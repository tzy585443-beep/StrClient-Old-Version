package com.strongspy.strclient.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {

    /**
     * 👑 严格屏幕二维平面向左旋转 90 度修复版
     */
    @Inject(
            method = "renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void injectAbsoluteBlockhit(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {

        // 判定：左键攻击且主手持剑
        if (player == MinecraftClient.getInstance().player && hand == Hand.MAIN_HAND && item.getItem() instanceof SwordItem && player.handSwinging) {
            boolean isRightArm = player.getMainArm() == net.minecraft.util.Arm.RIGHT;

            matrices.push();

            // =========================================================================
            // 🛠️ 空间基本位移面板
            // =========================================================================
            float SWORD_SCALE  = 1.50F;  // 🔍 放大倍率
            float SWORD_CLOSE  = -0.38F; // ↩️ 前后距离（挪远一点）
            float SWORD_UP     = 0.16F;  // 🔼 上下高度
            float SWORD_LEFT   = -0.12F; // ↔️ 左右位置
            // =========================================================================

            // 1.8 经典连砍平滑波动曲线
            float hitAnim = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);

            if (isRightArm) {
                // 1. 基础安全坐标定位
                matrices.translate(0.56F + SWORD_LEFT, -0.52F + SWORD_UP, -0.62F + SWORD_CLOSE);
                matrices.translate(0.0F, equipProgress * -0.6F, 0.0F);

                // 2. 缩放
                matrices.scale(SWORD_SCALE, SWORD_SCALE, SWORD_SCALE);

                // 3. 经典连砍颤动（上下高频抽击）
                matrices.translate(-hitAnim * 0.08F, hitAnim * 0.12F, 0.0F);
                matrices.multiply(new Quaternionf().rotationX(hitAnim * (float) Math.toRadians(-10.0)));
                matrices.multiply(new Quaternionf().rotationZ(hitAnim * (float) Math.toRadians(-15.0)));

                // 4. ✨【核心修复：正规屏幕平面向左旋转 90 度】
                // 在做任何空间扭曲之前，先让剑在绝对垂直于屏幕的面上逆时针转 90 度
                matrices.multiply(new Quaternionf().rotationZ((float) Math.toRadians(90.0)));

                // 5. 🛡️ 【后续 3D 姿态塑造】
                // 此时再做 X 轴和 Y 轴的微调，让转完 90 度的剑朝玩家扣过来
                matrices.multiply(new Quaternionf().rotationX((float) Math.toRadians(-35.0)));
                matrices.multiply(new Quaternionf().rotationY((float) Math.toRadians(65.0)));

            } else {
                // ==================== 左手镜像 ====================
                matrices.translate(-0.56F - SWORD_LEFT, -0.52F + SWORD_UP, -0.62F + SWORD_CLOSE);
                matrices.translate(0.0F, equipProgress * -0.6F, 0.0F);

                matrices.scale(SWORD_SCALE, SWORD_SCALE, SWORD_SCALE);

                matrices.translate(hitAnim * 0.08F, hitAnim * 0.12F, 0.0F);
                matrices.multiply(new Quaternionf().rotationX(hitAnim * (float) Math.toRadians(-10.0)));
                matrices.multiply(new Quaternionf().rotationZ(hitAnim * (float) Math.toRadians(15.0)));

                // 左手镜像：向右平面旋转 90 度
                matrices.multiply(new Quaternionf().rotationZ((float) Math.toRadians(-90.0)));
                matrices.multiply(new Quaternionf().rotationX((float) Math.toRadians(-35.0)));
                matrices.multiply(new Quaternionf().rotationY((float) Math.toRadians(-65.0)));
            }

            // 6. 底层强行渲染
            HeldItemRenderer renderer = (HeldItemRenderer) (Object) this;
            var mode = isRightArm ?
                    net.minecraft.client.render.model.json.ModelTransformationMode.FIRST_PERSON_RIGHT_HAND :
                    net.minecraft.client.render.model.json.ModelTransformationMode.FIRST_PERSON_LEFT_HAND;

            renderer.renderItem(player, item, mode, !isRightArm, matrices, vertexConsumers, light);

            matrices.pop();
            ci.cancel();
        }
    }
}