package com.holynight.client;

import com.holynight.HolyKnightEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

public class HolyKnightItemLayer extends BlockAndItemGeoLayer<HolyKnightEntity> {

    public HolyKnightItemLayer(GeoEntityRenderer<HolyKnightEntity> renderer) {
        super(renderer);
    }

    @Override
    protected ItemStack getStackForBone(GeoBone bone, HolyKnightEntity entity) {
        if ("right_hand".equals(bone.getName())) {
            return entity.getItemBySlot(EquipmentSlot.MAINHAND);
        }
        return ItemStack.EMPTY;
    }

    @Override
    protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack,
                                      HolyKnightEntity entity, MultiBufferSource bufferSource,
                                      float partialTick, int packedLight, int packedOverlay) {
        poseStack.translate(0.0, 0.0, -0.1);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90));
        super.renderStackForBone(poseStack, bone, stack, entity, bufferSource, partialTick, packedLight, packedOverlay);
    }
}
