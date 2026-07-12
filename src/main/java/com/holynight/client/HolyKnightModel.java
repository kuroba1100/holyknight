package com.holynight.client;

import com.holynight.HolyKnightEntity;
import com.holynight.HolyNight;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class HolyKnightModel extends GeoModel<HolyKnightEntity> {

    @Override
    public ResourceLocation getModelResource(HolyKnightEntity entity) {
        return new ResourceLocation(HolyNight.MODID, "geo/holy_knight.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(HolyKnightEntity entity) {
        return new ResourceLocation(HolyNight.MODID, "textures/entity/holy_knight.png");
    }

    @Override
    public ResourceLocation getAnimationResource(HolyKnightEntity entity) {
        return new ResourceLocation(HolyNight.MODID, "animations/holy_knight.animation.json");
    }
}
