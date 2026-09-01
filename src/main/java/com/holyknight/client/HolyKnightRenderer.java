package com.holyknight.client;

import com.holyknight.HolyKnightEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class HolyKnightRenderer extends GeoEntityRenderer<HolyKnightEntity> {

    public HolyKnightRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HolyKnightModel());
        this.shadowRadius = 1.0F;
        this.scaleWidth = 2.0F;
        this.scaleHeight = 2.0F;
    }
}
