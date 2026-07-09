package com.strongspy.strclient.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.SwordItem;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BipedEntityModel.class)
public class BipedEntityModelMixin<T extends LivingEntity> {

    @Shadow public net.minecraft.client.model.ModelPart rightArm;
    @Shadow public net.minecraft.client.model.ModelPart leftArm;

    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void overrideToPure18Blocking(T entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        if (entity != MinecraftClient.getInstance().player) return;

        PlayerEntity player = (PlayerEntity) entity;

        // 判定手里拿的是剑，且手在挥动（KillAura 正在触发或在手动砍击）
        if (player.handSwinging && player.handSwingProgress > 0 && player.getMainHandStack().getItem() instanceof SwordItem) {

            boolean isRightHand = player.getMainArm() == Arm.RIGHT;

            // 1.8 砍击格挡的正弦波计算
            float swingModifier = net.minecraft.util.math.MathHelper.sin(player.getHandSwingProgress(animationProgress) * (float)Math.PI);
            float swingOffset = net.minecraft.util.math.MathHelper.sin(net.minecraft.util.math.MathHelper.sqrt(player.getHandSwingProgress(animationProgress)) * (float)Math.PI);

            if (isRightHand) {
                // 强行把手臂固定在胸前横置
                this.rightArm.pitch = -0.7F;
                this.rightArm.yaw = -0.4F;
                this.rightArm.roll = 0.0F;

                // 注入格挡抽搐动画
                this.rightArm.pitch += swingModifier * 0.3F - swingOffset * 0.1F;
                this.rightArm.yaw += swingOffset * 0.4F;
            } else {
                this.leftArm.pitch = -0.7F;
                this.leftArm.yaw = 0.4F;
                this.leftArm.roll = 0.0F;

                this.leftArm.pitch += swingModifier * 0.3F - swingOffset * 0.1F;
                this.leftArm.yaw -= swingOffset * 0.4F;
            }
        }
    }
}