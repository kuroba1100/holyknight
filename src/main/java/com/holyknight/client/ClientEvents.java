package com.holyknight.client;

import com.holyknight.HolyKnight;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HolyKnight.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(HolyKnight.HOLY_KNIGHT.get(), HolyKnightRenderer::new);
        event.registerEntityRenderer(HolyKnight.HOLY_RAY_VISUAL.get(), HolyRayRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(HolyRayRenderer.MODEL_LAYER_LOCATION, HolyRayRenderer::createBodyLayer);
    }
}
