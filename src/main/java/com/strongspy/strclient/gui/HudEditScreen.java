package com.strongspy.strclient.gui;

import com.strongspy.strclient.core.HudOverlay;
import com.strongspy.strclient.core.HudPosition;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

/**
 * A transparent overlay screen that lets the player drag every registered
 * HUD element around. World keeps rendering underneath (isPauseScreen=false),
 * and the actual HUD elements are still drawn by HudOverlay's normal
 * HudRenderCallback — this screen just adds draggable hitboxes on top and
 * a thin highlight border so it's clear what's draggable.
 */
public class HudEditScreen extends Screen {

    private String draggingId = null;
    private int dragOffX, dragOffY;

    public HudEditScreen() {
        super(Text.literal("StrClient HUD Editor"));
    }

    public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {}

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Draw a hint banner at the top
        String hint = "HUD Edit Mode — drag elements to move them — ESC to finish";
        int w = textRenderer.getWidth(hint);
        int x = (this.width - w) / 2 - 6;
        ctx.fill(x, 4, x + w + 12, 18, 0xA0000000);
        ctx.drawCenteredTextWithShadow(textRenderer, hint, this.width / 2, 7, 0xFF5C7CFA);

        // Draw highlight boxes around every draggable element using its current bounds
        for (HudOverlay.Element el : HudOverlay.getElements()) {
            int[] b = el.bounds();
            if (b == null) continue;
            boolean hovered = mouseX >= b[0] && mouseX <= b[2] && mouseY >= b[1] && mouseY <= b[3];
            int color = hovered || el.id().equals(draggingId) ? 0xFF5C7CFA : 0x80FFFFFF;
            drawBoxOutline(ctx, b[0] - 2, b[1] - 2, b[2] + 2, b[3] + 2, color);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawBoxOutline(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        ctx.fill(x1, y1, x2, y1 + 1, color);
        ctx.fill(x1, y2 - 1, x2, y2, color);
        ctx.fill(x1, y1, x1 + 1, y2, color);
        ctx.fill(x2 - 1, y1, x2, y2, color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            List<HudOverlay.Element> elements = HudOverlay.getElements();
            // Iterate in reverse so topmost/last-drawn element wins on overlap
            for (int i = elements.size() - 1; i >= 0; i--) {
                HudOverlay.Element el = elements.get(i);
                int[] b = el.bounds();
                if (b == null) continue;
                if (mouseX >= b[0] && mouseX <= b[2] && mouseY >= b[1] && mouseY <= b[3]) {
                    draggingId = el.id();
                    dragOffX = (int) mouseX - b[0];
                    dragOffY = (int) mouseY - b[1];
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (draggingId != null) {
            int newX = (int) mouseX - dragOffX;
            int newY = (int) mouseY - dragOffY;
            HudPosition.set(draggingId, newX, newY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingId != null) {
            HudPosition.save();
            draggingId = null;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            HudPosition.save();
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}