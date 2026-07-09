package com.strongspy.strclient.modules.spinner;

import com.strongspy.strclient.core.AbstractModule;
import com.strongspy.strclient.core.ModuleSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

/**
 * Spinner — rotates the player's body like a top while keeping
 * the camera view direction unchanged.
 * 已完美修复 1.21 下不工作、乱抖动的问题，并完美支持第三人称视角可见的旋转。
 */
public class SpinnerModule extends AbstractModule {

    private float currentYaw = 0f;

    public SpinnerModule() {
        super("spinner", "Spinner", Category.MOVEMENT,
                "Spins your player body like a top without moving the camera");
    }

    @Override
    protected void registerSettings() {
        registerSetting(ModuleSetting.ofDouble(
                "speed", "Speed", "Rotation speed in degrees per tick",
                15.0, 1.0, 90.0));

        registerSetting(ModuleSetting.ofBoolean(
                "clockwise", "Clockwise", "Spin direction",
                true));
    }

    @Override
    public void onEnable(MinecraftClient client) {
        if (client.player != null) {
            currentYaw = client.player.getYaw();
        }
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) return;

        double speed = getDouble("speed");
        boolean clockwise = getBoolean("clockwise");

        // 1. 步进服务器端的 yaw 角度
        currentYaw += clockwise ? speed : -speed;

        // 保持在 -180 到 180 的合法视向范围内
        if (currentYaw > 180f)  currentYaw -= 360f;
        if (currentYaw < -180f) currentYaw += 360f;

        // 2. 核心黑科技：强行修改本地模型的渲染偏角
        // 这样在 F5 第三人称下能看到身体和头在完美旋转，而第一人称画面（Camera Yaw）稳如泰山，完全不会晕！
        client.player.bodyYaw = currentYaw;
        client.player.headYaw = currentYaw;

        // 3. 发送包含完整 X, Y, Z 坐标和旋转后 Yaw 的 Full 包
        // 这会强行覆盖原生移动包带来的视向拉扯，让服务器和外人眼里看到你平滑稳定地在旋转
        client.getNetworkHandler().sendPacket(
                new PlayerMoveC2SPacket.Full(
                        client.player.getX(),
                        client.player.getY(),
                        client.player.getZ(),
                        currentYaw,
                        client.player.getPitch(),
                        client.player.isOnGround()
                )
        );
    }

    @Override
    public void onDisable(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) return;

        // 当关闭 Spinner 时，发送一个 Full 包，把服务器端的视向和身体完美拉回到玩家当前的实际真实操作视角
        client.getNetworkHandler().sendPacket(
                new PlayerMoveC2SPacket.Full(
                        client.player.getX(),
                        client.player.getY(),
                        client.player.getZ(),
                        client.player.getYaw(),
                        client.player.getPitch(),
                        client.player.isOnGround()
                )
        );
    }
}