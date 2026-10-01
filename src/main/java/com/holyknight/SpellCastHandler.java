package com.holyknight;

import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = HolyKnight.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SpellCastHandler {

    private static final double DETECTION_RANGE = 32.0;

    // Divine Smite cast by the Holy Knight is scaled down.
    private static final float SPELL_DAMAGE_SCALE = 0.421F; // 66.5 -> 28.0 at spell level 1

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        DamageSource source = event.getSource();
        if (source instanceof SpellDamageSource spellSource && source.getEntity() instanceof HolyKnightEntity
                && spellSource.spell() == SpellRegistry.DIVINE_SMITE_SPELL.get()) {
            event.setAmount(event.getAmount() * SPELL_DAMAGE_SCALE);
        }
    }

    @SubscribeEvent
    public static void onSpellCast(SpellOnCastEvent event) {
        Player caster = event.getEntity();
        if (caster.level().isClientSide()) return;

        AABB searchBox = caster.getBoundingBox().inflate(DETECTION_RANGE);
        List<HolyKnightEntity> knights = caster.level().getEntitiesOfClass(
                HolyKnightEntity.class, searchBox
        );

        for (HolyKnightEntity knight : knights) {
            knight.onPlayerCastSpellNearby(caster);
        }
    }
}
