package com.strongspy.strclient.modules.autorespawn;

import com.strongspy.strclient.core.AbstractModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.entity.player.PlayerEntity;

/**
 * 自动重生模块 - 稳定可靠版
 * 玩家死亡后，自动向服务器发送重生请求，无需点击按钮。
 */
public class AutoRespawnModule extends AbstractModule {

    public AutoRespawnModule() {
        super("autorespawn", "Auto Respawn", Category.UTILITY,
                "Automatically respawn when you die.");
    }

    @Override
    public void onTick(MinecraftClient client) {
        // 1. 检查当前屏幕是否为死亡屏幕
        if (client.currentScreen instanceof DeathScreen) {

            // 2. 通过 client.player 获取玩家对象
            //    注意：在死亡屏幕上，client.player 可能为 null。
            //    需要先做空值检查，避免空指针异常。
            PlayerEntity player = client.player;
            if (player != null) {

                // 3. 调用原生的重生方法
                //    requestRespawn() 会处理向服务器发送重生请求的所有底层逻辑。
                player.requestRespawn();

                // 4. 可选：关闭死亡屏幕，让重生界面消失得更快
                //    但这行代码不是必须的，因为请求重生后，屏幕通常会自动关闭。
                client.currentScreen = null;
            }
        }
    }
}