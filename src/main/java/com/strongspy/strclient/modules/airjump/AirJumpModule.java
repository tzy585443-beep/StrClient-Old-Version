package com.strongspy.strclient.modules.airjump;

import com.strongspy.strclient.core.AbstractModule;
import com.strongspy.strclient.core.ModuleSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

public class AirJumpModule extends AbstractModule {

    public AirJumpModule() {
        super("airjump", "Air Jump", Category.MOVEMENT,
                "Allows jumping in mid-air.");
    }

    @Override
    protected void registerSettings() {
        registerSetting(ModuleSetting.ofDouble("multiplier", "Jump Height Multiplier",
                "Multiplier for jump height", 1.0, 0.5, 2.0));
    }

    @Override
    public void onTick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        // 检测是否按下跳跃键且不在地面
        if (client.options.jumpKey.isPressed() && !player.isOnGround()) {
            double multiplier = getDouble("multiplier");
            // 原版跳跃速度 ≈ 0.42，乘以倍率
            double jumpSpeed = 0.42 * multiplier;
            // 仅当当前垂直速度低于跳跃速度时才应用，避免降低速度
            if (player.getVelocity().y < jumpSpeed) {
                player.setVelocity(player.getVelocity().x, jumpSpeed, player.getVelocity().z);
                // 重置摔落距离，防止摔落伤害
                player.fallDistance = 0;
            }
        }
    }
}