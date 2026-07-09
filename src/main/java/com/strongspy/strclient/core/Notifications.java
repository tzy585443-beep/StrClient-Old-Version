package com.strongspy.strclient.core;

import net.minecraft.client.MinecraftClient;

/**
 * In-game notifications — shows a semi-transparent HUD toast when a module
 * is toggled. Called from AbstractModule.setEnabled() after the state has
 * actually changed, so the displayed state always matches reality.
 */
public final class Notifications {

    private Notifications() {}

    public static void moduleToggled(MinecraftClient client, AbstractModule module, boolean enabled) {
        if (client == null || client.player == null) return;

        String text = module.getDisplayName() + (enabled ? " enabled" : " disabled");
        HudOverlay.pushToast(text, enabled);
    }
}
