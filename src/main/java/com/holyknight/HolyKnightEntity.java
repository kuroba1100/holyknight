package com.holyknight;

import com.holyknight.spell.HolyRaySpell;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;

public class HolyKnightEntity extends Monster implements GeoEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final EntityDataAccessor<Boolean> DATA_CASTING =
            SynchedEntityData.defineId(HolyKnightEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_LUNGING =
            SynchedEntityData.defineId(HolyKnightEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_TECH =
            SynchedEntityData.defineId(HolyKnightEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TECH_SEQ =
            SynchedEntityData.defineId(HolyKnightEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_WAKI =
            SynchedEntityData.defineId(HolyKnightEntity.class, EntityDataSerializers.BOOLEAN);

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.holyknight.holy_knight"),
            BossEvent.BossBarColor.YELLOW,
            BossEvent.BossBarOverlay.PROGRESS
    );

    private int healCooldown = 0;
    private int smiteCooldown = 0;
    private int rayCooldown = 0;
    private Vec3 rayAim = null;
    private int castingTicks = 0;
    private boolean isCastingHeal = false;
    private boolean pendingHeal = false;
    private boolean phase2 = false;
    private int lungeTick = -1;
    private boolean lungeHit = false;
    private float lungeYaw = 0;
    private double lungePrevX, lungePrevZ, lungePredX, lungePredZ;

    // tick単位。slashの長さ0.9秒、振り始め0.4秒
    private static final int LUNGE_LENGTH = 18;
    private static final int LUNGE_CHARGE = 8;
    private static final int LUNGE_LOOK = 6;
    private static final float LUNGE_TURN_RATE = 5.0F;
    private static final float LUNGE_SENSING = 12.0F;
    private static final double LUNGE_MOTION = 0.3;
    private static final double LUNGE_MAX_SPEED = 1.5;
    private int lastCombatTick = -200;

    private int healCastTime = 0;

    private static final int HEAL_COOLDOWN = 6000; // 5 minutes
    private static final int SMITE_COOLDOWN = 400; // 20 seconds
    private static final double SMITE_RANGE = 3.0;
    private static final float SMITE_CHANCE = 0.0143F; // per tick, about 25% over one second
    private static final int SMITE_CAST_TICK = 12;
    private static final int SMITE_LEVEL = 1;
    private static final int HEAL_LEVEL = 1;
    private static final int RAY_LEVEL = 1;
    private static final int RAY_AIM_TICK = 1;
    private static final int RAY_CAST_TICK = 3;
    // 突進が届く距離（速度上限1.5×地上の減衰2.2＋命中半径2.5）
    private static final double LUNGE_REACH = 6.0;
    private static final double RAY_MIN_RANGE = 12.0;
    private static final double LUNGE_HIT_RANGE = 2.5;
    private static final int COUNTER_CHANCE = 30; // percent

    // 以下は仮の値
    private static final float ATTACK_CHANCE = 0.2F; // per tick
    private static final float START_FRONT = 60.0F;
    private static final float TURN_RATE = 12.0F;
    private static final int LOCK_BEFORE = 2;
    private static final int RECOVERY_BASE = 20;
    private static final int RECOVERY_PER_HIT = 10;
    private static final float HIT_HEIGHT_MARGIN = 0.6F;
    private static final float GUARD_FRONT = 45.0F;
    private static final float GUARD_REDUCTION = 0.5F;
    private static final int GUARD_CHANCE = 50; // percent。突進が届く距離で突進の代わりに構える割合

    private enum Pose { LOW, WAKI, JODAN, SLASH_L, SLASH_R }

    // hits: {tick, 左端, 右端, 届く距離, 下端, 上端}。角度は正面0°・＋が右手側、距離と高さはブロック。
    // .bbmodelのキーフレームから刃の通る範囲をtickごとに計算した値
    private enum Tech {
        RISING_DIAGONAL_SLASH("rising_diagonal_slash", 16, Pose.LOW, Pose.LOW, 14, new float[][]{
                {8, 38.7F, 81.5F, 4.3F, 0.2F, 1.9F}, {9, 15.0F, 61.4F, 4.4F, 1.7F, 3.3F},
                {10, -10.5F, 38.0F, 4.3F, 2.3F, 4.7F}, {11, -30.0F, 15.4F, 3.6F, 2.8F, 5.8F}}),
        RISING_COMBO("rising_combo", 12, null, Pose.LOW, -1, new float[][]{
                {2, -27.7F, 20.9F, 3.8F, 2.7F, 6.2F}, {3, -6.6F, 44.0F, 4.3F, 2.0F, 4.2F}, {4, 15.6F, 73.6F, 4.3F, 0.6F, 2.1F}}),
        THRUST("thrust", 16, Pose.LOW, Pose.LOW, -1, new float[][]{
                {8, -13.5F, 12.7F, 3.9F, 1.9F, 2.2F}, {9, -16.3F, 10.4F, 4.4F, 2.0F, 2.6F}, {10, -16.3F, 4.7F, 4.4F, 2.1F, 2.6F}}),
        SHIN_SWEEP("shin_sweep", 16, Pose.LOW, Pose.LOW, -1, new float[][]{
                {7, 35.5F, 99.5F, 4.2F, 0.0F, 1.6F}, {8, -19.0F, 48.2F, 4.1F, 0.0F, 1.8F}, {9, -38.0F, -8.8F, 4.4F, 1.0F, 3.8F}}),
        NUKI_DOU("nuki_dou", 22, Pose.LOW, Pose.WAKI, -1, new float[][]{
                {4, -99.8F, -46.9F, 3.8F, 2.0F, 3.4F}, {5, -48.6F, -18.9F, 4.1F, 2.1F, 3.4F}, {6, -21.1F, 21.5F, 4.1F, 1.3F, 2.3F},
                {7, 20.3F, 67.7F, 4.3F, 1.3F, 1.8F}, {8, 58.7F, 90.1F, 4.3F, 0.7F, 1.8F}, {9, 85.5F, 119.1F, 4.1F, 0.5F, 1.6F},
                {10, 115.2F, 149.7F, 4.2F, 0.6F, 1.8F}}),
        LOW_TO_SLASH1("low_to_combo_slash1", 4, Pose.LOW, Pose.SLASH_L, -1, new float[0][]),
        LOW_TO_SLASH2("low_to_combo_slash2", 4, Pose.LOW, Pose.SLASH_R, -1, new float[0][]),
        COMBO_SLASH1("combo_slash1", 10, Pose.SLASH_L, Pose.SLASH_R, 6, new float[][]{
                {3, -53.1F, 13.2F, 3.8F, 0.6F, 2.1F}, {4, -17.6F, 56.9F, 4.5F, 0.6F, 2.4F}, {5, 36.4F, 85.2F, 4.8F, 1.8F, 4.4F}}),
        COMBO_SLASH2("combo_slash2", 10, Pose.SLASH_R, Pose.SLASH_L, 6, new float[][]{
                {3, 28.7F, 68.1F, 5.1F, 0.4F, 2.2F}, {4, -35.9F, 30.5F, 4.3F, 0.4F, 2.3F}, {5, -94.4F, -23.6F, 4.3F, 1.6F, 4.2F}}),
        SLASH1_TO_LOW("combo_slash1_to_low", 9, null, Pose.LOW, -1, new float[0][]),
        SLASH2_TO_LOW("combo_slash2_to_low", 9, null, Pose.LOW, -1, new float[0][]),
        WAKI_RISING("waki_rising", 16, Pose.WAKI, Pose.WAKI, -1, new float[][]{
                {6, 126.7F, 178.0F, 4.2F, 1.1F, 3.1F}, {7, 79.7F, 132.9F, 4.2F, 1.8F, 3.2F},
                {8, 12.1F, 91.7F, 4.2F, 1.8F, 2.6F}, {9, -30.0F, 31.9F, 4.0F, 2.0F, 4.2F}}),
        WAKI_MAKIUCHI("waki_makiuchi", 20, Pose.WAKI, Pose.LOW, -1, new float[][]{
                {8, -11.3F, 39.8F, 3.9F, 3.5F, 6.0F}, {9, -12.0F, 30.1F, 4.3F, 2.3F, 4.6F}}),
        SLASH2("slash2", 16, Pose.WAKI, Pose.LOW, -1, new float[][]{
                {2, 92.1F, 133.9F, 5.0F, 0.9F, 2.3F}, {3, 33.1F, 99.5F, 4.8F, 0.0F, 2.1F}, {4, -34.7F, 52.3F, 4.1F, 0.0F, 2.2F}}),
        WAKI_RISING_RIGHT("waki_rising_right", 12, Pose.WAKI, Pose.JODAN, -1, new float[][]{
                {3, 133.7F, 182.7F, 3.9F, 0.0F, 1.5F}, {4, 109.5F, 146.1F, 4.1F, 0.0F, 1.5F}, {5, 86.5F, 113.5F, 4.7F, 0.0F, 1.9F},
                {6, 23.1F, 87.7F, 5.1F, 0.6F, 3.3F}, {7, -14.0F, 33.1F, 5.0F, 2.9F, 6.1F}}),
        COMBO_DIAGONAL_DOWN("combo_diagonal_down", 15, Pose.JODAN, Pose.LOW, -1, new float[][]{
                {3, 14.3F, 90.3F, 2.8F, 3.4F, 6.7F}, {4, -5.2F, 21.4F, 4.2F, 2.8F, 6.1F}, {5, -17.1F, 6.2F, 4.4F, 1.1F, 3.8F}}),
        WAKI_TO_LOW("waki_to_low", 10, null, Pose.LOW, -1, new float[0][]),
        PARRY("parry", 10, null, Pose.LOW, -1, new float[0][]),
        CAST_SMITE("cast_smite", 18, null, Pose.LOW, -1, new float[0][]),
        CAST_RAY("cast_ray", 7, null, Pose.LOW, -1, new float[0][]),
        GUARD("guard", 10, null, Pose.LOW, -1, new float[0][]);

        final RawAnimation raw;
        final int length;
        final Pose from; // nullは連撃の候補に入らない
        final Pose to;
        final int altTick;
        final float[][] hits;
        final int lockTick;

        Tech(String anim, int length, Pose from, Pose to, int altTick, float[][] hits) {
            this.raw = RawAnimation.begin().thenPlayAndHold(anim);
            this.length = length;
            this.from = from;
            this.to = to;
            this.altTick = altTick;
            this.hits = hits;
            this.lockTick = hits.length > 0 ? Math.max(0, (int) hits[0][0] - LOCK_BEFORE) : length;
        }
    }

    private static final Tech[] TECHS = Tech.values();
    private static final RawAnimation HEAL_ANIM = RawAnimation.begin().thenPlayAndHold("greater_heal");
    private static final RawAnimation LUNGE_ANIM = RawAnimation.begin().thenPlay("slash");
    private static final RawAnimation WAKI_ANIM = RawAnimation.begin().thenLoop("idle_waki");

    private Tech tech = null;
    private Tech lastTech = null;
    private int techTick = 0;
    private float techYaw = 0;
    private Pose stance = Pose.LOW;
    private int chainLeft = 0;
    private int chainHits = 0;
    private int recovery = 0;
    private boolean riposte = false;
    private final Set<UUID> struck = new HashSet<>();
    private int clientTechSeq = -1;

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
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_CASTING, false);
        this.entityData.define(DATA_LUNGING, false);
        this.entityData.define(DATA_TECH, 0);
        this.entityData.define(DATA_TECH_SEQ, 0);
        this.entityData.define(DATA_WAKI, false);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("HealCooldown", healCooldown);
        tag.putInt("SmiteCooldown", smiteCooldown);
        tag.putBoolean("Phase2", phase2);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        healCooldown = tag.getInt("HealCooldown");
        smiteCooldown = tag.getInt("SmiteCooldown");
        phase2 = tag.getBoolean("Phase2");
    }

    // 攻撃を仕掛けてよい相手: サバイバル/アドベンチャーで視線が通るプレイヤー
    private boolean isValidFoe(Entity e) {
        return e instanceof Player p && p.isAlive() && !p.isCreative() && !p.isSpectator() && this.hasLineOfSight(p);
    }

    private boolean busy() {
        return isCastingHeal || lungeTick >= 0 || tech != null;
    }

    @Override
    public boolean isImmobile() {
        return super.isImmobile() || busy() || recovery > 0;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, false));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return HolyKnightEntity.this.getLastHurtByMob() instanceof Player && super.canUse();
            }
        });
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide()) return;

        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());

        if (healCooldown > 0) healCooldown--;
        if (smiteCooldown > 0) smiteCooldown--;
        if (rayCooldown > 0) rayCooldown--;

        if (isCastingHeal) {
            castingTicks++;
            if (castingTicks >= healCastTime) {
                castSpell(SpellRegistry.GREATER_HEAL_SPELL.get(), HEAL_LEVEL);
                isCastingHeal = false;
                castingTicks = 0;
                healCooldown = HEAL_COOLDOWN;
                phase2 = true;
            }
        } else if (!pendingHeal && healCooldown <= 0 && this.getHealth() < this.getMaxHealth() * 0.5) {
            float hpRatio = this.getHealth() / this.getMaxHealth();
            float castChance = 0.5F + 0.5F * (1.0F - hpRatio / 0.5F);
            if (this.getRandom().nextFloat() < castChance * 0.02F) {
                pendingHeal = true;
            }
        }

        boolean inCombat = this.getTarget() != null || (this.tickCount - lastCombatTick) < 200;
        if (!inCombat && this.getHealth() < this.getMaxHealth()) {
            this.heal(1.0F);
        }

        if (isCastingHeal) {
            this.getNavigation().stop();
        }

        if (lungeTick >= 0) {
            tickLunge();
        }

        if (tech != null) {
            tickTech();
        } else if (recovery > 0) {
            recovery--;
        }

        if (!busy()) {
            decide();
        }

        this.entityData.set(DATA_CASTING, isCastingHeal);
        this.entityData.set(DATA_LUNGING, lungeTick >= 0);
        this.entityData.set(DATA_TECH, tech == null ? 0 : tech.ordinal() + 1);
        this.entityData.set(DATA_WAKI, stance == Pose.WAKI);
    }

    private void decide() {
        if (pendingHeal) {
            if (stance == Pose.WAKI) {
                start(Tech.WAKI_TO_LOW);
                return;
            }
            pendingHeal = false;
            isCastingHeal = true;
            castingTicks = 0;
            healCastTime = SpellRegistry.GREATER_HEAL_SPELL.get().getEffectiveCastTime(HEAL_LEVEL, this);
            this.playSound(SoundEvents.BEACON_POWER_SELECT, 1.0F, 0.8F);
            return;
        }
        LivingEntity target = this.getTarget();
        if (target == null || !isValidFoe(target)) {
            if (stance == Pose.WAKI && (this.tickCount - lastCombatTick) >= 200) {
                start(Tech.WAKI_TO_LOW);
            }
            return;
        }
        if (recovery > 0) return;
        if (stance == Pose.LOW && smiteCooldown <= 0 && this.distanceTo(target) < SMITE_RANGE
                && this.getRandom().nextFloat() < SMITE_CHANCE) {
            start(Tech.CAST_SMITE);
            smiteCooldown = SMITE_COOLDOWN;
            return;
        }
        if (stance == Pose.LOW && rayCooldown <= 0 && rayRange(target)
                && this.distanceTo(target) < HolyRaySpell.getRange()) {
            start(Tech.CAST_RAY);
            rayCooldown = HolyKnight.HOLY_RAY.get().getSpellCooldown();
            return;
        }
        if (Math.abs(relYaw(target.position())) <= START_FRONT && this.getRandom().nextFloat() < ATTACK_CHANCE) {
            startChain(target);
        }
    }

    private double horizontalTo(Entity e) {
        double dx = e.getX() - this.getX();
        double dz = e.getZ() - this.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private boolean lungeReaches(Entity e) {
        return horizontalTo(e) <= LUNGE_REACH && Math.abs(e.getY() - this.getY()) <= LUNGE_HIT_RANGE;
    }

    private boolean rayRange(Entity e) {
        return horizontalTo(e) > RAY_MIN_RANGE || Math.abs(e.getY() - this.getY()) > LUNGE_HIT_RANGE;
    }

    // 連撃

    private void startChain(LivingEntity target) {
        chainLeft = phase2 ? 2 + this.getRandom().nextInt(4) : 2 + this.getRandom().nextInt(2);
        chainHits = 0;
        Tech first = pick(stance, target);
        if (first != null) {
            start(first);
        } else {
            chainLeft = 0;
        }
    }

    private void start(Tech t) {
        tech = t;
        techTick = 0;
        techYaw = this.yBodyRot;
        struck.clear();
        if (t.hits.length > 0) {
            chainLeft--;
            chainHits++;
            lastTech = t;
        }
        this.getNavigation().stop();
        this.entityData.set(DATA_TECH, t.ordinal() + 1);
        this.entityData.set(DATA_TECH_SEQ, this.entityData.get(DATA_TECH_SEQ) + 1);
    }

    private boolean canContinue(LivingEntity target) {
        return chainLeft > 0 && target != null && isValidFoe(target);
    }

    private Tech pick(Pose pose, LivingEntity target) {
        List<Tech> options = new ArrayList<>();
        for (Tech t : TECHS) {
            if (t.from != pose || t == lastTech) continue;
            if (t == Tech.WAKI_RISING_RIGHT && chainLeft < 2) continue;
            if (covers(t, target)) options.add(t);
        }
        return options.isEmpty() ? null : options.get(this.getRandom().nextInt(options.size()));
    }

    // 正面に捉えた時に、その技の刃が相手に届くか
    private boolean covers(Tech t, LivingEntity target) {
        return switch (t) {
            case LOW_TO_SLASH1 -> lastTech != Tech.COMBO_SLASH1 && covers(Tech.COMBO_SLASH1, target);
            case LOW_TO_SLASH2 -> lastTech != Tech.COMBO_SLASH2 && covers(Tech.COMBO_SLASH2, target);
            case WAKI_RISING_RIGHT -> hitsReach(t, target) || hitsReach(Tech.COMBO_DIAGONAL_DOWN, target);
            default -> hitsReach(t, target);
        };
    }

    private boolean hitsReach(Tech t, LivingEntity target) {
        for (float[] row : t.hits) {
            if (reaches(row, target, 0.0F)) return true;
        }
        return false;
    }

    private void tickTech() {
        LivingEntity target = this.getTarget();
        techTick++;
        if (target != null && techTick <= tech.lockTick && !(tech == Tech.CAST_RAY && techTick > RAY_AIM_TICK)) {
            techYaw = Mth.approachDegrees(techYaw, yawTo(target.position()), TURN_RATE);
        }
        this.setYRot(techYaw);
        this.yBodyRot = techYaw;
        this.yHeadRot = techYaw;

        for (float[] row : tech.hits) {
            if ((int) row[0] + 1 == techTick) sweep(row);
        }
        if (tech == Tech.CAST_SMITE && techTick == SMITE_CAST_TICK) {
            castSpell(SpellRegistry.DIVINE_SMITE_SPELL.get(), SMITE_LEVEL);
        }
        if (tech == Tech.CAST_RAY) {
            if (techTick == RAY_AIM_TICK) {
                rayAim = target != null ? target.getBoundingBox().getCenter() : null;
            }
            if (techTick == RAY_CAST_TICK && rayAim != null) {
                this.lookAt(EntityAnchorArgument.Anchor.EYES, rayAim);
                techYaw = this.getYRot();
                castSpell(HolyKnight.HOLY_RAY.get(), RAY_LEVEL);
            }
        }

        if (techTick == tech.altTick) {
            Tech next = switch (tech) {
                case RISING_DIAGONAL_SLASH -> canContinue(target) && hitsReach(Tech.RISING_COMBO, target)
                        && this.getRandom().nextBoolean() ? Tech.RISING_COMBO : null;
                case COMBO_SLASH1 -> canContinue(target) && hitsReach(Tech.COMBO_SLASH2, target) ? null : Tech.SLASH1_TO_LOW;
                case COMBO_SLASH2 -> canContinue(target) && hitsReach(Tech.COMBO_SLASH1, target) ? null : Tech.SLASH2_TO_LOW;
                default -> null;
            };
            if (next != null) {
                start(next);
                return;
            }
        }
        if (techTick >= tech.length) {
            finish(target);
        }
    }

    private void finish(LivingEntity target) {
        Pose pose = tech.to;
        Tech next = switch (pose) {
            case JODAN -> Tech.COMBO_DIAGONAL_DOWN;
            case SLASH_L -> Tech.COMBO_SLASH1;
            case SLASH_R -> Tech.COMBO_SLASH2;
            default -> null;
        };
        if (next == null) {
            stance = pose;
            if (riposte) {
                riposte = false;
                tech = null;
                if (target != null && isValidFoe(target)) startChain(target);
                return;
            }
            if (canContinue(target)) next = pick(pose, target);
        }
        if (next != null) {
            start(next);
        } else {
            tech = null;
            recovery = chainHits > 0 ? RECOVERY_BASE + RECOVERY_PER_HIT * chainHits : 0;
            chainLeft = 0;
            chainHits = 0;
        }
    }

    private void sweep(float[] row) {
        double r = row[3] + 1.0;
        for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(r, row[5], r))) {
            if (!isValidFoe(e) || !reaches(row, e, relYaw(e.position()))) continue;
            if (struck.add(e.getUUID())) {
                strike(e);
            }
        }
    }

    private boolean reaches(float[] row, Entity e, float rel) {
        double dx = e.getX() - this.getX();
        double dz = e.getZ() - this.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz) - e.getBbWidth() / 2;
        if (dist > row[3]) return false;
        double bottom = e.getY() - this.getY();
        if (bottom > row[5] + HIT_HEIGHT_MARGIN || bottom + e.getBbHeight() < row[4] - HIT_HEIGHT_MARGIN) return false;
        float pad = (float) Math.toDegrees(Math.atan2(e.getBbWidth() / 2, Math.max(dist, 0.5)));
        for (int turn = -360; turn <= 360; turn += 360) {
            if (rel + turn >= row[1] - pad && rel + turn <= row[2] + pad) return true;
        }
        return false;
    }

    private float yawTo(Vec3 pos) {
        return (float) (Math.toDegrees(Math.atan2(pos.z - this.getZ(), pos.x - this.getX())) - 90.0);
    }

    // 正面0°、＋が右手側
    private float relYaw(Vec3 pos) {
        return Mth.wrapDegrees(yawTo(pos) - this.yBodyRot);
    }

    private boolean strike(Entity target) {
        return super.doHurtTarget(target);
    }

    // 接近はMeleeAttackGoal、攻撃はdecide()から始める
    @Override
    public boolean doHurtTarget(Entity target) {
        return false;
    }

    // CataclysmのIgnis（PredictiveChargeAttackAnimationGoal）と同じ形: 溜め中に着地点を先読みし、突進のtickで距離比例の速度を1回与える
    private void tickLunge() {
        LivingEntity t = this.getTarget();
        if (t != null && lungeTick < LUNGE_LOOK) {
            lungeYaw = Mth.approachDegrees(lungeYaw, yawTo(t.position()), LUNGE_TURN_RATE);
        }
        if (t != null && lungeTick < LUNGE_CHARGE) {
            lungePredX = t.getX() + (t.getX() - lungePrevX) / LUNGE_CHARGE * LUNGE_SENSING;
            lungePredZ = t.getZ() + (t.getZ() - lungePrevZ) / LUNGE_CHARGE * LUNGE_SENSING;
        }
        if (lungeTick == LUNGE_CHARGE) {
            double vx = (lungePredX - this.getX()) * LUNGE_MOTION;
            double vz = (lungePredZ - this.getZ()) * LUNGE_MOTION;
            double speed = Math.sqrt(vx * vx + vz * vz);
            if (speed > LUNGE_MAX_SPEED) {
                vx *= LUNGE_MAX_SPEED / speed;
                vz *= LUNGE_MAX_SPEED / speed;
            }
            this.setDeltaMovement(vx, this.getDeltaMovement().y, vz);
            this.hasImpulse = true;
            this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.8F);
        }
        if (lungeTick >= LUNGE_CHARGE && !lungeHit && t != null && isValidFoe(t) && this.distanceTo(t) < 2.5) {
            this.strike(t);
            lungeHit = true;
        }
        this.setYRot(lungeYaw);
        this.yBodyRot = lungeYaw;
        this.yHeadRot = lungeYaw;
        if (++lungeTick > LUNGE_LENGTH) {
            lungeTick = -1;
        }
    }

    private void castSpell(AbstractSpell spell, int spellLevel) {
        spell.onCast(this.level(), spellLevel, this, CastSource.MOB, MagicData.getPlayerMagicData(this));
    }

    public void onPlayerCastSpellNearby(Player caster) {
        if (busy() || pendingHeal || recovery > 0 || stance != Pose.LOW || !isValidFoe(caster)) return;
        if (this.distanceTo(caster) > 3.0) {
            this.setTarget(caster);
            if (!lungeReaches(caster) || this.getRandom().nextInt(100) < GUARD_CHANCE) {
                start(Tech.GUARD);
                return;
            }
            lungeTick = 0;
            lungeHit = false;
            lungeYaw = this.yBodyRot;
            lungePrevX = lungePredX = caster.getX();
            lungePrevZ = lungePredZ = caster.getZ();
        }
    }

    // GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5, this::movementAnim));
        controllers.add(new AnimationController<>(this, "action", 0, this::actionAnim));
    }

    private PlayState movementAnim(AnimationState<HolyKnightEntity> state) {
        if (state.isMoving()) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("walk"));
        } else {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("idle"));
        }
        return PlayState.CONTINUE;
    }

    private PlayState actionAnim(AnimationState<HolyKnightEntity> state) {
        AnimationController<HolyKnightEntity> controller = state.getController();
        if (this.entityData.get(DATA_CASTING)) {
            controller.setAnimation(HEAL_ANIM);
            return PlayState.CONTINUE;
        }
        if (this.entityData.get(DATA_LUNGING)) {
            controller.setAnimation(LUNGE_ANIM);
            return PlayState.CONTINUE;
        }
        int id = this.entityData.get(DATA_TECH);
        if (id > 0) {
            int seq = this.entityData.get(DATA_TECH_SEQ);
            if (seq != clientTechSeq) {
                clientTechSeq = seq;
                controller.forceAnimationReset();
            }
            controller.setAnimation(TECHS[id - 1].raw);
            return PlayState.CONTINUE;
        }
        if (this.entityData.get(DATA_WAKI)) {
            controller.setAnimation(WAKI_ANIM);
            return PlayState.CONTINUE;
        }
        return PlayState.STOP;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    private boolean fromFront(DamageSource source, float halfAngle) {
        Vec3 pos = source.getSourcePosition();
        return pos != null && Math.abs(relYaw(pos)) <= halfAngle;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // 落下・酸欠は無効。戦闘扱いにもしないので非戦闘時の回復が止まらない
        if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.DROWN)) {
            return false;
        }

        boolean counter = false;
        if (!this.level().isClientSide()) {
            // 摺り上げ: 振っている間は前方からの攻撃を受けない
            if (tech == Tech.WAKI_RISING_RIGHT && techTick > tech.hits[0][0]
                    && techTick <= tech.hits[tech.hits.length - 1][0] + 1 && fromFront(source, GUARD_FRONT)) {
                return false;
            }
            counter = !busy() && recovery <= 0 && !pendingHeal
                    && source.getEntity() instanceof LivingEntity attacker && isValidFoe(attacker)
                    && this.getRandom().nextInt(100) < COUNTER_CHANCE;
            // 下段: 正面からの近接を打ち払って切り返す
            if (counter && stance == Pose.LOW && source.getDirectEntity() == source.getEntity()
                    && fromFront(source, GUARD_FRONT)) {
                lastCombatTick = this.tickCount;
                this.setTarget((LivingEntity) source.getEntity());
                riposte = true;
                start(Tech.PARRY);
                return false;
            }
        }

        if (tech == Tech.GUARD && fromFront(source, GUARD_FRONT)) {
            amount *= 1.0F - GUARD_REDUCTION;
        }

        lastCombatTick = this.tickCount;
        boolean hurt = super.hurt(source, amount);
        if (hurt && counter && tech == null && source.getEntity() instanceof LivingEntity attacker) {
            startChain(attacker);
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
        return this.entityData.get(DATA_CASTING);
    }

    public boolean isLunging() {
        return this.entityData.get(DATA_LUNGING);
    }
}
