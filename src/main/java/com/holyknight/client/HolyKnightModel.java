package com.holyknight.client;

import com.holyknight.HolyKnightEntity;
import com.holyknight.HolyKnight;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class HolyKnightModel extends GeoModel<HolyKnightEntity> {

    @Override
    public ResourceLocation getModelResource(HolyKnightEntity entity) {
        return new ResourceLocation(HolyKnight.MODID, "geo/holy_knight.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(HolyKnightEntity entity) {
        return new ResourceLocation(HolyKnight.MODID, "textures/entity/holy_knight.png");
    }

    @Override
    public ResourceLocation getAnimationResource(HolyKnightEntity entity) {
        return new ResourceLocation(HolyKnight.MODID, "animations/holy_knight.animation.json");
    }
}
