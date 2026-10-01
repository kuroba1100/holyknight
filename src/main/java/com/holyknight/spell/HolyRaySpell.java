package com.holyknight.spell;

import com.holyknight.HolyKnight;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Optional;

public class HolyRaySpell extends AbstractSpell {
    private static final float UNDEAD_MULTIPLIER = 1.5F;
    private static final ResourceLocation CAST_SOUND = new ResourceLocation("irons_spellbooks", "spell.guiding_bolt.cast");

    private final ResourceLocation spellId = new ResourceLocation(HolyKnight.MODID, "holy_ray");
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.COMMON)
            .setSchoolResource(SchoolRegistry.HOLY_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(15)
            .build();

    public HolyRaySpell() {
        this.manaCostPerLevel = 15;
        this.baseSpellPower = 6;
        this.spellPowerPerLevel = 1;
        this.castTime = 0;
        this.baseManaCost = 25;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("ui.irons_spellbooks.damage", Utils.stringTruncation(getDamage(spellLevel, caster), 2)),
                Component.translatable("ui.irons_spellbooks.distance", Utils.stringTruncation(getRange(), 1))
        );
    }

    @Override
    public CastType getCastType() {
        return CastType.INSTANT;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return defaultConfig;
    }

    @Override
    public ResourceLocation getSpellResource() {
        return spellId;
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.empty();
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.ofNullable(ForgeRegistries.SOUND_EVENTS.getValue(CAST_SOUND));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        HitResult hit = Utils.raycastForEntity(level, entity, getRange(), true, 0.15F);
        Vec3 end = hit.getLocation();
        level.addFreshEntity(new HolyRayVisualEntity(level, entity.getEyePosition(), end, entity));
        if (hit.getType() == HitResult.Type.ENTITY) {
            Entity target = ((EntityHitResult) hit).getEntity();
            float damage = getDamage(spellLevel, entity);
            if (target instanceof LivingEntity living && living.getMobType() == MobType.UNDEAD) {
                damage *= UNDEAD_MULTIPLIER;
            }
            DamageSources.applyDamage(target, damage, getDamageSource(entity));
        }
        MagicManager.spawnParticles(level, ParticleHelper.WISP, end.x, end.y, end.z, 20, 0, 0, 0, 0.2, false);
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    public static float getRange() {
        return 30;
    }

    private float getDamage(int spellLevel, LivingEntity caster) {
        return 3 + getSpellPower(spellLevel, caster) * 1.5F;
    }
}
