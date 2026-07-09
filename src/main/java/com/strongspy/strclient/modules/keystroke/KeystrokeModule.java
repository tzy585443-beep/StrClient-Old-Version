package com.strongspy.strclient.modules.keystroke;

import com.strongspy.strclient.core.AbstractModule;
import com.strongspy.strclient.core.ModuleSetting;
import net.minecraft.client.MinecraftClient;

/**
 * Keystroke overlay — shows WASD + LMB/RMB CPS display on screen.
 * Toggling this module shows/hides the widget entirely.
 */
public class KeystrokeModule extends AbstractModule {

    public KeystrokeModule() {
        super("keystrokes", "Keystrokes", Category.VISUAL,
                "Shows WASD keys and LMB/RMB CPS on screen");
    }

    @Override
    protected void registerSettings() {
        registerSetting(ModuleSetting.ofString(
                "shape", "Shape", "Key box shape",
                "SQUARE", "SQUARE", "WIDE"));

        registerSetting(ModuleSetting.ofInt(
                "keySize", "Key Size", "Size of each key box in pixels",
                16, 10, 32));

        registerSetting(ModuleSetting.ofBoolean(
                "showWasd", "Show WASD", "Show movement keys",
                true));

        registerSetting(ModuleSetting.ofBoolean(
                "showMouse", "Show Mouse Buttons", "Show LMB/RMB CPS counters",
                true));
    }

    // No onTick needed — rendering is done directly in HudOverlay
    // which reads isEnabled() to decide whether to draw the widget.
}