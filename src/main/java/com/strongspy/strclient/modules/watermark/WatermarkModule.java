package com.strongspy.strclient.modules.watermark;

import com.strongspy.strclient.core.AbstractModule;
import com.strongspy.strclient.core.ModuleSetting;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/**
 * Watermark — draws a persistent text overlay on screen.
 * Configurable text, font scale, opacity, and position.
 * No mixin required — uses the standard HudRenderCallback like the rest of the HUD.
 */
public class WatermarkModule extends AbstractModule {

    public WatermarkModule() {
        super("watermark", "Watermark", Category.VISUAL,
                "Displays a custom text overlay on screen");
    }

    @Override
    protected void registerSettings() {
        registerSetting(ModuleSetting.ofString(
                "text", "Text", "Watermark text content",
                "StrClient"));

        registerSetting(ModuleSetting.ofInt(
                "size", "Font Size", "Text scale, 100 = normal size",
                150, 50, 400));

        registerSetting(ModuleSetting.ofInt(
                "alpha", "Opacity", "0 = invisible, 255 = fully solid",
                160, 0, 255));

        registerSetting(ModuleSetting.ofString(
                "position", "Position", "Where on screen to anchor the watermark",
                "BOTTOM_RIGHT", "TOP_LEFT", "TOP_RIGHT", "BOTTOM_LEFT", "BOTTOM_RIGHT", "CENTER"));

        registerSetting(ModuleSetting.ofBoolean(
                "shadow", "Text Shadow", "Draw a drop shadow behind the text",
                true));
    }

    @Override
    public void onEnable(MinecraftClient client) {
        HudRenderCallback.EVENT.register(this::render);
    }

    private void render(DrawContext context, RenderTickCounter tickCounter) {
        if (!isEnabled()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        String text = getString("text");
        if (text.isEmpty()) return;

        float scale = getInt("size") / 100f;
        int alpha = getInt("alpha");
        String position = getString("position");
        boolean shadow = getBoolean("shadow");

        int color = (alpha << 24) | 0xFFFFFF; // white text, alpha-controlled

        int screenWidth  = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();

        int textWidth  = client.textRenderer.getWidth(text);
        int textHeight = client.textRenderer.fontHeight;
        int scaledWidth  = Math.round(textWidth * scale);
        int scaledHeight = Math.round(textHeight * scale);

        int margin = 6;
        int x, y;

        switch (position) {
            case "TOP_LEFT" -> { x = margin; y = margin; }
            case "TOP_RIGHT" -> { x = screenWidth - scaledWidth - margin; y = margin; }
            case "BOTTOM_LEFT" -> { x = margin; y = screenHeight - scaledHeight - margin; }
            case "CENTER" -> { x = (screenWidth - scaledWidth) / 2; y = (screenHeight - scaledHeight) / 2; }
            default -> { // BOTTOM_RIGHT
                x = screenWidth - scaledWidth - margin;
                y = screenHeight - scaledHeight - margin;
            }
        }

        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0);
        context.getMatrices().scale(scale, scale, 1f);

        if (shadow) {
            context.drawTextWithShadow(client.textRenderer, text, 0, 0, color);
        } else {
            context.drawText(client.textRenderer, text, 0, 0, color, false);
        }

        context.getMatrices().pop();
    }
}