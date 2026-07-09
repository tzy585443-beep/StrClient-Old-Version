package com.strongspy.strclient.modules.crash;

import com.strongspy.strclient.core.AbstractModule;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class CrashModule extends AbstractModule {

    private static boolean registered = false;

    public CrashModule() {
        super("crash", "Crash", Category.UTILITY,
                "Instantly crash the game with /crash (client-side only).");
    }

    @Override
    public void onEnable(MinecraftClient client) {
        if (!registered) {
            ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
                dispatcher.register(ClientCommandManager.literal("crash")
                        .executes(ctx -> {
                            MinecraftClient mc = ctx.getSource().getClient();
                            if (mc.player != null) {
                                mc.player.sendMessage(Text.literal("§c💥 Crashing game..."), false);
                            }
                            // 延迟 100ms 确保消息发送出去，然后强制终止 JVM
                            new Thread(() -> {
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException ignored) {}
                                Runtime.getRuntime().halt(1);
                            }).start();
                            return 1;
                        })
                );
            });
            registered = true;
        }
    }

    @Override
    public void onDisable(MinecraftClient client) {
        // nothing
    }
}