package com.strongspy.strclient.modules.creativeflight;

import com.strongspy.strclient.core.AbstractModule;
import com.strongspy.strclient.core.ModuleSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

public class CreativeFlightModule extends AbstractModule {

    private boolean wasAllowFlying = false;

    public CreativeFlightModule() {
        super("creativeflight", "Creative Flight", Category.MOVEMENT,
                "Fly like in Creative mode (survival/ adventure). Press space to toggle flight.");
    }

    @Override
    protected void registerSettings() {
        registerSetting(ModuleSetting.ofDouble("speed", "Fly Speed",
                "Flight speed multiplier (1.0 = creative speed)", 1.0, 0.5, 3.0));
    }

    @Override
    public void onEnable(MinecraftClient client) {
        if (client.player == null) return;
        ClientPlayerEntity player = client.player;
        wasAllowFlying = player.getAbilities().allowFlying;

        // 允许飞行，但不强制开启飞行状态（让玩家按空格触发）
        player.getAbilities().allowFlying = true;
        // 设置飞行速度
        float speed = (float) (0.05 * getDouble("speed"));
        player.getAbilities().setFlySpeed(speed);
    }

    @Override
    public void onDisable(MinecraftClient client) {
        if (client.player == null) return;
        ClientPlayerEntity player = client.player;
        // 如果玩家当前正在飞行，取消飞行
        if (player.getAbilities().flying) {
            player.getAbilities().flying = false;
        }
        // 恢复原始 allowFlying 状态
        player.getAbilities().allowFlying = wasAllowFlying;
        // 恢复默认飞行速度
        player.getAbilities().setFlySpeed(0.05f);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        if (!isEnabled()) return;

        ClientPlayerEntity player = client.player;
        // 确保 allowFlying 始终为 true（防止被游戏重置）
        if (!player.getAbilities().allowFlying) {
            player.getAbilities().allowFlying = true;
        }
        // 保持飞行速度
        float targetSpeed = (float) (0.05 * getDouble("speed"));
        if (player.getAbilities().getFlySpeed() != targetSpeed) {
            player.getAbilities().setFlySpeed(targetSpeed);
        }

        // 注意：我们不强制设置 flying，让玩家通过空格键自由切换
        // 原版创造模式下，按空格切换飞行状态，我们保持这个行为。
    }
}