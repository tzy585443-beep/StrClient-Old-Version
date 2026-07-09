package com.strongspy.strclient.modules.autof5;

import com.strongspy.strclient.core.AbstractModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.option.Perspective;

public class AutoF5Module extends AbstractModule {

    private Perspective originalPerspective = Perspective.FIRST_PERSON;
    private boolean wasContainerOpen = false;

    public AutoF5Module() {
        super("autof5", "Auto F5", Category.UTILITY,
                "Switch to third person when opening containers.");
    }

    @Override
    public void onEnable(MinecraftClient client) {
        if (client.player != null) {
            originalPerspective = client.options.getPerspective();
            wasContainerOpen = false;
        }
    }

    @Override
    public void onDisable(MinecraftClient client) {
        if (client.player != null) {
            client.options.setPerspective(originalPerspective);
        }
        wasContainerOpen = false;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        boolean containerOpen = isContainerOpen(client);

        if (containerOpen && !wasContainerOpen) {
            // 打开容器时保存当前视角，切换到第三人称背面
            originalPerspective = client.options.getPerspective();
            client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        } else if (!containerOpen && wasContainerOpen) {
            // 关闭容器时恢复之前保存的视角
            client.options.setPerspective(originalPerspective);
        }

        wasContainerOpen = containerOpen;
    }

    private boolean isContainerOpen(MinecraftClient client) {
        Screen screen = client.currentScreen;
        if (screen == null) return false;

        // 排除玩家物品栏（按 E 键）和创造模式物品栏
        if (screen instanceof InventoryScreen) return false;
        if (screen instanceof CreativeInventoryScreen) return false;

        // 所有 HandledScreen 子类均为容器界面（箱子、熔炉、附魔台、潜影盒等）
        return screen instanceof HandledScreen;
    }
}