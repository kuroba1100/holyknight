package com.holynight;

import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = HolyNight.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SpellCastHandler {

    private static final double DETECTION_RANGE = 32.0;

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
