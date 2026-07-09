package com.strongspy.strclient.modules.island;

import com.strongspy.strclient.core.AbstractModule;
import com.strongspy.strclient.core.ModuleSetting;
import net.minecraft.client.MinecraftClient;

/**
 * Dynamic Island — the persistent HUD pill at the top of the screen.
 * Each info item can be individually toggled.
 * Always shows "StrClient" brand and clock by default.
 */
public class DynamicIslandModule extends AbstractModule {

    public DynamicIslandModule() {
        super("island", "Dynamic Island", Category.VISUAL,
                "Top-center HUD pill showing client info");
    }

    @Override
    protected void registerSettings() {
        // "StrClient" brand and clock are always shown, not toggleable

        registerSetting(ModuleSetting.ofBoolean(
                "showFps", "Show FPS", "Display current framerate",
                true));

        registerSetting(ModuleSetting.ofBoolean(
                "showPlayer", "Show Player Name", "Display your in-game name",
                false));

        registerSetting(ModuleSetting.ofBoolean(
                "showPing", "Show Ping", "Display server latency in ms",
                false));

        registerSetting(ModuleSetting.ofBoolean(
                "showCoords", "Show Coordinates", "Display X Y Z position",
                false));

        registerSetting(ModuleSetting.ofBoolean(
                "showMcVersion", "Show MC Version", "Display Minecraft version",
                false));

        registerSetting(ModuleSetting.ofBoolean(
                "showModVersion", "Show Mod Version", "Display StrClient version",
                false));


    }
}