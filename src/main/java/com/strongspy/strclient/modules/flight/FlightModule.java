package com.strongspy.strclient.modules.flight;

import com.strongspy.strclient.core.AbstractModule;
import com.strongspy.strclient.core.ModuleSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public class FlightModule extends AbstractModule {

    public FlightModule() {
        super("flight", "Flight", Category.MOVEMENT,
                "Survival flight with speed control and fall damage protection.");
    }

    @Override
    protected void registerSettings() {
        registerSetting(ModuleSetting.ofDouble("speed", "Speed", "Movement speed (blocks/sec)", 1.2, 0.5, 200.0));
        registerSetting(ModuleSetting.ofBoolean("allowJump", "Allow Jump", "Jump to ascend", true));
        registerSetting(ModuleSetting.ofDouble("verticalSpeed", "Vertical Speed", "Up/down speed multiplier", 1.0, 0.5, 40.0));
    }

    @Override
    public void onTick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        double speed = getDouble("speed");
        boolean allowJump = getBoolean("allowJump");
        double vertical = getDouble("verticalSpeed");

        boolean forward = player.input.pressingForward;
        boolean back = player.input.pressingBack;
        boolean left = player.input.pressingLeft;
        boolean right = player.input.pressingRight;
        boolean jump = player.input.jumping;
        boolean sneak = player.input.sneaking;

        float moveForward = 0;
        if (forward && !back) moveForward = 1;
        else if (!forward && back) moveForward = -1;

        float moveStrafe = 0;
        if (right && !left) moveStrafe = 1;
        else if (!right && left) moveStrafe = -1;

        double spd = speed / 20.0;
        double yawRad = Math.toRadians(player.getYaw());

        double velX = 0, velZ = 0;
        if (moveForward != 0) {
            velX += Math.sin(-yawRad) * moveForward;
            velZ += Math.cos(yawRad) * moveForward;
        }
        if (moveStrafe != 0) {
            velX += Math.sin(-yawRad + Math.PI / 2) * moveStrafe;
            velZ += Math.cos(yawRad + Math.PI / 2) * moveStrafe;
        }
        double len = Math.hypot(velX, velZ);
        if (len != 0) {
            velX /= len;
            velZ /= len;
        }
        velX *= spd;
        velZ *= spd;

        double velY = 0;
        if (allowJump && jump) {
            velY = spd * vertical;
        } else if (sneak) {
            // 下降速度限制，避免太快导致摔落
            velY = -Math.min(spd * vertical, 1.0); // 限制最大下降速度为1格/秒
        }

        // 应用速度（客户端移动）
        player.setVelocity(velX, velY, velZ);

        // ★★★ 关键：每 tick 发送 onGround=true 包，防止摔落 ★★★
        if (player.networkHandler != null) {
            player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));
        }

        // 重置摔落高度
        if (player.fallDistance > 0) {
            player.fallDistance = 0;
        }
    }

    @Override
    public void onDisable(MinecraftClient client) {
        if (client.player != null) {
            client.player.fallDistance = 0;
        }
    }
}