package com.strongspy.strclient.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class ScreenMixin {

    // Reuse your main menu custom background image path
    private static final Identifier BG = Identifier.of("strclient", "textures/background.png");

    /**
     * Intercepts base background rendering for all standard UI screens.
     * Handles separate behaviors for main-menu submenus and in-game menus.
     */
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void replaceSubMenuBackground(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();

        // 1. If the current active screen is TitleScreen itself, skip this hook
        // and hand over control to TitleScreenBackgroundMixin.
        if (client.currentScreen instanceof TitleScreen) {
            return;
        }

        // 🎯 2. IN-GAME ABSOLUTE TRANSPARENCY FIX:
        // If the player is inside a world (Singleplayer or Multiplayer), we cancel the vanilla
        // background rendering completely and draw NOTHING. This removes the vanilla translucent
        // black overlay entirely, making the ESC/Options menus perfectly floating and transparent.
        if (client.world != null) {
            ci.cancel();
            return;
        }

        // 3. For menus accessed from the Main Menu (NOT in-game), cancel vanilla dirt rendering
        // and display the client's unified custom theme background image.
        ci.cancel();

        int w = client.getWindow().getScaledWidth();
        int h = client.getWindow().getScaledHeight();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // Render custom full-screen background image for main menu sub-screens
        ctx.drawTexture(BG, 0, 0, 0, 0, w, h, w, h);

        // Draw a 50% opacity black layer on top to keep texts and buttons legible on the main menu
        ctx.fill(0, 0, w, h, 0x80000000);

        RenderSystem.disableBlend();
    }
}