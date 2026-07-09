package com.strongspy.strclient.modules.nofall;

import com.strongspy.strclient.core.AbstractModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

/**
 * NoFall 模块
 * 防止玩家受到摔落伤害，无任何设置，开启即生效。
 */
public class NoFallModule extends AbstractModule {

    public NoFallModule() {
        super("nofall", "NoFall", Category.MOVEMENT,
                "Prevents fall damage. No settings required.");
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;

        // 当摔落距离超过危险阈值（3格开始有伤害）且玩家正在下落时
        if (client.player.fallDistance > 2.0F && !client.player.isOnGround()) {
            // 方法1：直接重置客户端的摔落距离（对于多数服务器有效）
            client.player.fallDistance = 0.0F;

            // 方法2：发送一个 onGround=true 的移动数据包，欺骗服务器认为玩家已经落地
            if (client.player.networkHandler != null) {
                PlayerMoveC2SPacket packet = new PlayerMoveC2SPacket.OnGroundOnly(true);
                client.player.networkHandler.sendPacket(packet);
            }
        }
    }
}