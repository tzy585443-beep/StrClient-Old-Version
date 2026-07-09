package com.strongspy.strclient.modules.autosprint;

import com.strongspy.strclient.core.AbstractModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

public class AutoSprintModule extends AbstractModule {

    public AutoSprintModule() {
        super("autosprint", "Auto Sprint", Category.MOVEMENT,
                "Automatically sprint when moving forward.");
    }

    @Override
    public void onTick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        // 检查是否按住了前进键（W）且没有按后退键（S）
        boolean forward = player.input.pressingForward;
        boolean back = player.input.pressingBack;
        boolean sneak = player.input.sneaking;
        boolean isMoving = forward && !back;

        // 疾跑条件：向前移动、不在潜行、有足够饥饿度（>=6）或处于创造模式
        if (isMoving && !sneak) {
            // 自动疾跑（不消耗饥饿度限制，因为在创造模式或某些服务器可能无限制）
            player.setSprinting(true);
        } else {
            // 如果玩家没有在移动或正在后退/潜行，关闭疾跑
            player.setSprinting(false);
        }
    }
}