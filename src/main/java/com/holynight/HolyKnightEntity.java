package com.holynight;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class HolyKnightEntity extends Monster implements GeoEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.holynight.holy_knight"),
            BossEvent.BossBarColor.YELLOW,
            BossEvent.BossBarOverlay.PROGRESS
    );

    private int healCooldown = 0;
    private int smiteCooldown = 0;
    private int castingTicks = 0;
    private boolean isCastingHeal = false;
    private int healUsesLeft = 3;
    private int lungeTicksLeft = 0;

    private static final int HEAL_CAST_TIME = 200;
    private static final int HEAL_COOLDOWN = 1800;

    public HolyKnightEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 50;
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 400.0)
                .add(Attributes.MOVEMENT_SPEED, 0.35)
                .add(Attributes.ATTACK_DAMAGE, 18.0)
                .add(Attributes.ARMOR, 12.0)
                .add(Attributes.ARMOR_TOUGHNESS, 6.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
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
            if (castingTicks >= HEAL_CAST_TIME) {
                this.setHealth(this.getMaxHealth());
                this.level().broadcastEntityEvent(this, (byte) 35);
                this.playSound(SoundEvents.BEACON_ACTIVATE, 1.5F, 1.2F);
                isCastingHeal = false;
                castingTicks = 0;
                healCooldown = HEAL_COOLDOWN;
                healUsesLeft--;
            }
        } else if (healUsesLeft > 0 && healCooldown <= 0 && this.getHealth() < this.getMaxHealth() * 0.5) {
            float hpRatio = this.getHealth() / this.getMaxHealth();
            float castChance = 1.0F - (hpRatio / 0.5F);
            castChance = castChance * castChance;
            if (hpRatio <= 0.1F) castChance = 0.9F + this.getRandom().nextFloat() * 0.1F;
            if (this.getRandom().nextFloat() < castChance * 0.05F) {
                isCastingHeal = true;
                castingTicks = 0;
                this.playSound(SoundEvents.BEACON_POWER_SELECT, 1.0F, 0.8F);
            }
        }

        if (this.getTarget() == null && this.getHealth() < this.getMaxHealth()) {
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
        if (target != null && !isCastingHeal && smiteCooldown <= 0 && this.distanceTo(target) < 8.0) {
            target.hurt(this.damageSources().magic(), 14.0F);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            this.playSound(SoundEvents.LIGHTNING_BOLT_IMPACT, 1.0F, 1.5F);
            smiteCooldown = 80;
        }
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
    public void customServerAiStep() {
        super.customServerAiStep();
        this.bossEvent.setVisible(this.isAlive());
        for (ServerPlayer player : this.level().getServer().getPlayerList().getPlayers()) {
            if (this.distanceTo(player) < 64.0) {
                this.bossEvent.addPlayer(player);
            } else {
                this.bossEvent.removePlayer(player);
            }
        }
    }

    // Sounds

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.VINDICATOR_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.VINDICATOR_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VINDICATOR_DEATH;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    public boolean isCastingHeal() {
        return isCastingHeal;
    }

    public boolean isLunging() {
        return lungeTicksLeft > 0;
    }
}
