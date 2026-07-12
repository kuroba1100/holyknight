package com.holynight.client;

import com.holynight.HolyKnightEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

public class HolyKnightItemLayer extends GeoRenderLayer<HolyKnightEntity> {

    public HolyKnightItemLayer(GeoEntityRenderer<HolyKnightEntity> renderer) {
        super(renderer);
    }

    @Override
    public void renderForBone(PoseStack poseStack, HolyKnightEntity entity, GeoBone bone,
                              net.minecraft.client.renderer.RenderType renderType,
                              MultiBufferSource bufferSource, com.mojang.blaze3d.vertex.VertexConsumer buffer,
                              float partialTick, int packedLight, int packedOverlay) {
        if ("right_arm_lower".equals(bone.getName()) || "right_arm".equals(bone.getName())) {
            ItemStack weapon = entity.getItemBySlot(EquipmentSlot.MAINHAND);
            if (!weapon.isEmpty()) {
                poseStack.pushPose();
                poseStack.translate(0.0, -0.4, -0.1);
                poseStack.mulPose(Axis.XP.rotationDegrees(-90));
                ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
                itemRenderer.renderStatic(weapon, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                        packedLight, packedOverlay, poseStack, bufferSource, entity.level(), entity.getId());
                poseStack.popPose();
            }
        }
    }
}
