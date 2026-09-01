package com.holyknight;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;

public class HolyKnightEntity extends Monster implements GeoEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.holyknight.holy_knight"),
            BossEvent.BossBarColor.YELLOW,
            BossEvent.BossBarOverlay.PROGRESS
    );

    private int healCooldown = 0;
    private int smiteCooldown = 0;
    private int castingTicks = 0;
    private boolean isCastingHeal = false;
    private int lungeTicksLeft = 0;
    private int lastCombatTick = -200;

    private int healCastTime = 0;

    private static final int HEAL_COOLDOWN = 6000; // 5 minutes
    private static final int SMITE_COOLDOWN = 400; // 20 seconds
    private static final double SMITE_RANGE = 3.0;
    private static final float SMITE_CHANCE = 0.0143F; // per tick, about 25% over one second
    private static final int SMITE_LEVEL = 1;
    private static final int HEAL_LEVEL = 1;
    private static final int COUNTER_CHANCE = 30; // percent

    public HolyKnightEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 50;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 400.0)
                .add(Attributes.MOVEMENT_SPEED, 0.35)
                .add(Attributes.ATTACK_DAMAGE, 18.0)
                .add(Attributes.ARMOR, 12.0)
                .add(Attributes.ARMOR_TOUGHNESS, 6.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, false));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide()) return;

        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());

        if (healCooldown > 0) healCooldown--;
        if (smiteCooldown > 0) smiteCooldown--;

        if (isCastingHeal) {
            castingTicks++;
            if (castingTicks >= healCastTime) {
                castSpell(SpellRegistry.GREATER_HEAL_SPELL.get(), HEAL_LEVEL);
                isCastingHeal = false;
                castingTicks = 0;
                healCooldown = HEAL_COOLDOWN;
            }
        } else if (healCooldown <= 0 && this.getHealth() < this.getMaxHealth() * 0.5) {
            float hpRatio = this.getHealth() / this.getMaxHealth();
            float castChance = 0.5F + 0.5F * (1.0F - hpRatio / 0.5F);
            if (this.getRandom().nextFloat() < castChance * 0.02F) {
                isCastingHeal = true;
                castingTicks = 0;
                healCastTime = SpellRegistry.GREATER_HEAL_SPELL.get().getEffectiveCastTime(HEAL_LEVEL, this);
                this.playSound(SoundEvents.BEACON_POWER_SELECT, 1.0F, 0.8F);
            }
        }

        boolean inCombat = this.getTarget() != null || (this.tickCount - lastCombatTick) < 200;
        if (!inCombat && this.getHealth() < this.getMaxHealth()) {
            this.heal(1.0F);
        }

        if (isCastingHeal) {
            this.getNavigation().stop();
        }

        if (lungeTicksLeft > 0) {
            lungeTicksLeft--;
            LivingEntity lungeTarget = this.getTarget();
            if (lungeTarget != null && this.distanceTo(lungeTarget) < 2.5) {
                this.doHurtTarget(lungeTarget);
                lungeTicksLeft = 0;
            }
        }

        LivingEntity target = this.getTarget();
        if (target != null && !isCastingHeal && smiteCooldown <= 0 && this.distanceTo(target) < SMITE_RANGE
                && this.getRandom().nextFloat() < SMITE_CHANCE) {
            castSpell(SpellRegistry.DIVINE_SMITE_SPELL.get(), SMITE_LEVEL);
            smiteCooldown = SMITE_COOLDOWN;
        }
    }

    private void castSpell(AbstractSpell spell, int spellLevel) {
        spell.onCast(this.level(), spellLevel, this, CastSource.MOB, MagicData.getPlayerMagicData(this));
    }

    public void onPlayerCastSpellNearby(Player caster) {
        if (this.distanceTo(caster) > 3.0 && lungeTicksLeft <= 0) {
            double dx = caster.getX() - this.getX();
            double dz = caster.getZ() - this.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            double speed = 1.5;
            this.setDeltaMovement(dx / len * speed, 0.1, dz / len * speed);
            this.setTarget(caster);
            lungeTicksLeft = 15;
            this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.8F);
        }
    }

    // GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5, this::movementAnim));
        controllers.add(new AnimationController<>(this, "action", 5, this::actionAnim));
    }

    private PlayState movementAnim(AnimationState<HolyKnightEntity> state) {
        if (state.isMoving()) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("walk"));
        } else {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("idle"));
        }
        return PlayState.CONTINUE;
    }

    private static final String[] ATTACK_ANIMS = {"attack_1", "attack_2", "attack_3"};

    private PlayState actionAnim(AnimationState<HolyKnightEntity> state) {
        if (isCastingHeal) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("cast_heal"));
            return PlayState.CONTINUE;
        }
        if (lungeTicksLeft > 0) {
            state.getController().setAnimation(RawAnimation.begin().thenPlay("slash"));
            return PlayState.CONTINUE;
        }
        if (this.swinging) {
            String anim = ATTACK_ANIMS[this.getRandom().nextInt(ATTACK_ANIMS.length)];
            state.getController().setAnimation(RawAnimation.begin().thenPlay(anim));
            return PlayState.CONTINUE;
        }
        return PlayState.STOP;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // 落下・酸欠は無効。戦闘扱いにもしないので非戦闘時の回復が止まらない
        if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.DROWN)) {
            return false;
        }

        lastCombatTick = this.tickCount;
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide() && !isCastingHeal
                && source.getEntity() instanceof LivingEntity attacker
                && this.getRandom().nextInt(100) < COUNTER_CHANCE) {
            this.doHurtTarget(attacker);
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        this.bossEvent.removeAllPlayers();
        this.bossEvent.setVisible(false);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public void remove(RemovalReason reason) {
        this.bossEvent.removeAllPlayers();
        super.remove(reason);
    }


    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSquared) {
        return false;
    }


    public boolean isCastingHeal() {
        return isCastingHeal;
    }

    public boolean isLunging() {
        return lungeTicksLeft > 0;
    }
}
