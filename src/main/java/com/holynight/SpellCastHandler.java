package com.holynight;

import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = HolyNight.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SpellCastHandler {

    private static final double DETECTION_RANGE = 32.0;

    // Spell damage dealt by the Holy Knight is scaled down. Melee is left untouched.
    private static final float SPELL_DAMAGE_SCALE = 0.421F; // 66.5 -> 28.0 at spell level 1

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        DamageSource source = event.getSource();
        if (source.getEntity() instanceof HolyKnightEntity && !source.is(DamageTypes.MOB_ATTACK)) {
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
