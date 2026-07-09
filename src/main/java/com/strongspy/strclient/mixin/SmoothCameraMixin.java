package com.strongspy.strclient.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public class SmoothCameraMixin {

    @Shadow private float yaw;
    @Shadow private float pitch;
    @Shadow private Vec3d pos;
    @Shadow private boolean ready;

    // 记录平滑的轨道环绕角度
    @Unique private float smoothOrbitYaw;
    @Unique private float smoothOrbitPitch;
    @Unique private boolean initialized = false;

    // 【可调参数 1】镜头的追踪丝滑度（数值越小越丝滑，大范围转弯时的电影感、漂移感越强。建议 0.05 ~ 0.12）
    @Unique private static final float SMOOTH_SPEED = 0.12f;

    // 【可调参数 2】严格锁定的相机与玩家头部的物理距离（单位：格）
    // 放在球面上后，相距永远死锁在这一数值，绝对不会贴脸
    @Unique private static final double CAMERA_DISTANCE = 4;

    @Inject(method = "update", at = @At("TAIL"))
    private void onUpdateCamera(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (!this.ready) return;

        if (thirdPerson) {
            // 1. 获取玩家眼睛/头部的精确当前帧渲染坐标（作为完美的球体中心点）
            double targetX = MathHelper.lerp((double)tickDelta, focusedEntity.prevX, focusedEntity.getX());
            double targetY = MathHelper.lerp((double)tickDelta, focusedEntity.prevY, focusedEntity.getY()) + (double)focusedEntity.getStandingEyeHeight();
            double targetZ = MathHelper.lerp((double)tickDelta, focusedEntity.prevZ, focusedEntity.getZ());

            // 2. 获取玩家当前的实际看方向角度
            float targetYaw = MathHelper.lerpAngleDegrees(tickDelta, focusedEntity.prevYaw, focusedEntity.getYaw());
            float targetPitch = MathHelper.lerp(tickDelta, focusedEntity.prevPitch, focusedEntity.getPitch());

            // 如果是第二人称正脸视角（F5按两次），将目标轨道角度反转到前方
            if (inverseView) {
                targetYaw += 180.0f;
                targetPitch = -targetPitch;
            }

            // 首次进入第三人称时初始化，防止镜头从极远处瞬移过来
            if (!initialized) {
                smoothOrbitYaw = targetYaw;
                smoothOrbitPitch = targetPitch;
                initialized = true;
            }

            // 3. 角度环绕平滑内插值（完美处理 0~360 度跨界突变问题）
            float yawDiff = targetYaw - smoothOrbitYaw;
            while (yawDiff < -180.0f) yawDiff += 360.0f;
            while (yawDiff >= 180.0f) yawDiff -= 360.0f;

            smoothOrbitYaw += yawDiff * SMOOTH_SPEED;
            smoothOrbitPitch += (targetPitch - smoothOrbitPitch) * SMOOTH_SPEED;

            // 4. 【核心物理修复】准确计算出在当前轨道角度下，相机在玩家*正后方*的球面坐标
            float f = smoothOrbitPitch * 0.017453292F;
            float g = -smoothOrbitYaw * 0.017453292F;
            float h = MathHelper.cos(g);
            float i = MathHelper.sin(g);
            float j = MathHelper.cos(f);
            float k = MathHelper.sin(f);

            // 得到该旋转角度下的标准向前方向向量
            double forwardX = i * j;
            double forwardY = -k;
            double forwardZ = h * j;

            // 关键修复：用头部坐标【减去】向前向量，让相机精确来到玩家正后方的球面上，实现绝对防贴脸
            double camX = targetX - forwardX * CAMERA_DISTANCE;
            double camY = targetY - forwardY * CAMERA_DISTANCE;
            double camZ = targetZ - forwardZ * CAMERA_DISTANCE;
            Vec3d newCameraPos = new Vec3d(camX, camY, camZ);

            // 5. 【强行定点注视】重新精准计算从 当前相机位置 指向 玩家头部 的绝对三维夹角
            // 这一步彻底解决了由于平滑位移滞后导致的“不指向后脑勺”或“镜头看歪”的问题
            double xDiff = targetX - camX;
            double yDiff = targetY - camY;
            double zDiff = targetZ - camZ;
            double diffXZ = Math.sqrt(xDiff * xDiff + zDiff * zDiff);

            float lookYaw = (float)(MathHelper.atan2(zDiff, xDiff) * 57.2957763671875) - 90.0F;
            float lookPitch = (float)(-(MathHelper.atan2(yDiff, diffXZ) * 57.2957763671875));

            // 6. 将最终计算出的完美球面坐标与强注视角度覆写回原版相机
            this.yaw = lookYaw;
            this.pitch = lookPitch;
            this.pos = newCameraPos;

        } else {
            // 第一人称时关闭初始化状态
            initialized = false;
        }
    }
}