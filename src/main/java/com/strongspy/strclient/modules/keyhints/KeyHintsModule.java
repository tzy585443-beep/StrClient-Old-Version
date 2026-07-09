package com.strongspy.strclient.modules.keyhints;

import com.strongspy.strclient.core.AbstractModule;
import com.strongspy.strclient.core.ModuleSetting;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;

import java.util.ArrayList;
import java.util.List;

/**
 * Key Hints — shows a HUD overlay listing all StrClient keybinds
 * and their currently bound keys. Only shows binds that are actually bound.
 */
public class KeyHintsModule extends AbstractModule {

    private static final int BG      = 0x70000000;
    private static final int TEXT    = 0xFFE8EAF0;
    private static final int DIM     = 0xFF9AA0B5;
    private static final int ACCENT  = 0xFF5C7CFA;
    private static final int PADDING = 5;
    private static final int LINE_H  = 11;
    private static final float SCALE = 0.8f;

    public KeyHintsModule() {
        super("keyhints", "Key Hints", Category.VISUAL,
                "Shows all StrClient keybinds on screen");
    }

    @Override
    protected void registerSettings() {
        registerSetting(ModuleSetting.ofString(
                "position", "Position", "",
                "BOTTOM_LEFT", "TOP_LEFT", "TOP_RIGHT", "BOTTOM_LEFT", "BOTTOM_RIGHT"));

        registerSetting(ModuleSetting.ofBoolean(
                "onlyBound", "Only Bound", "",
                true));

        registerSetting(ModuleSetting.ofBoolean(
                "showCategory", "Show Category", "",
                false));
    }

    @Override
    public void onEnable(MinecraftClient client) {
        HudRenderCallback.EVENT.register(this::render);
    }

    private void render(DrawContext context, RenderTickCounter tickCounter) {
        if (!isEnabled()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) return;
        if (client.currentScreen != null) return;

        boolean onlyBound    = getBoolean("onlyBound");
        boolean showCategory = getBoolean("showCategory");
        String  position     = getString("position");

        // Collect all StrClient keybinds from the Controls screen
        List<String[]> rows = new ArrayList<>(); // [0]=label, [1]=key

        for (KeyBinding kb : client.options.allKeys) {
            String category = kb.getCategory();
            if (!category.equals("StrClient Modules")) continue;
            if (onlyBound && kb.isUnbound()) continue;

            String keyName = kb.isUnbound() ? "---" : kb.getBoundKeyLocalizedText().getString();
            String label   = showCategory
                    ? kb.getTranslationKey()
                    : kb.getTranslationKey(); // display name = translation key (falls back to itself)

            rows.add(new String[]{ label, keyName });
        }

        if (rows.isEmpty()) return;

        // Measure dimensions
        int maxLabelW = 0;
        int maxKeyW   = 0;
        for (String[] row : rows) {
            maxLabelW = Math.max(maxLabelW, client.textRenderer.getWidth(row[0]));
            maxKeyW   = Math.max(maxKeyW,   client.textRenderer.getWidth("[" + row[1] + "]"));
        }

        int scaledLabelW = Math.round(maxLabelW * SCALE);
        int scaledKeyW   = Math.round(maxKeyW   * SCALE);
        int scaledLineH  = Math.round(LINE_H    * SCALE);
        int gapW         = Math.round(8         * SCALE);

        int boxW = scaledLabelW + gapW + scaledKeyW + PADDING * 2;
        int boxH = rows.size() * scaledLineH + PADDING * 2;

        int sw = context.getScaledWindowWidth();
        int sh = context.getScaledWindowHeight();
        int margin = 6;

        int x, y;
        switch (position) {
            case "TOP_LEFT"     -> { x = margin;           y = margin; }
            case "TOP_RIGHT"    -> { x = sw - boxW - margin; y = margin; }
            case "BOTTOM_RIGHT" -> { x = sw - boxW - margin; y = sh - boxH - margin; }
            default             -> { x = margin;           y = sh - boxH - margin; } // BOTTOM_LEFT
        }

        // Background
        context.fill(x, y, x + boxW, y + boxH, BG);

        // Accent left bar
        context.fill(x, y, x + 2, y + boxH, ACCENT);

        // Rows
        int rowY = y + PADDING;
        for (String[] row : rows) {
            String label  = row[0];
            String keyStr = "[" + row[1] + "]";

            // Label (left-aligned)
            context.getMatrices().push();
            context.getMatrices().translate(x + PADDING + 2, rowY, 0);
            context.getMatrices().scale(SCALE, SCALE, 1f);
            context.drawTextWithShadow(client.textRenderer, label, 0, 0, TEXT);
            context.getMatrices().pop();

            // Key badge (right-aligned)
            int keyW = Math.round(client.textRenderer.getWidth(keyStr) * SCALE);
            context.getMatrices().push();
            context.getMatrices().translate(x + boxW - PADDING - keyW, rowY, 0);
            context.getMatrices().scale(SCALE, SCALE, 1f);
            context.drawTextWithShadow(client.textRenderer, keyStr, 0, 0,
                    row[1].equals("---") ? DIM : ACCENT);
            context.getMatrices().pop();

            rowY += scaledLineH;
        }
    }
}