package com.strongspy.strclient.core;

import com.strongspy.strclient.modules.island.DynamicIslandModule;
import com.strongspy.strclient.modules.keystroke.KeystrokeModule;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class HudOverlay {

    // ════════════════════════════════════════════════════════════════
    // ── SETTINGS — all tunable constants live here ────────────────────
    // ════════════════════════════════════════════════════════════════

    // Colors
    private static final int PANEL_BG   = 0x70000000;
    private static final int TEXT_COLOR = 0xFFE8EAF0;
    private static final int DIM_COLOR  = 0xFF9AA0B5;
    private static final int GREEN      = 0xFF34C759;
    private static final int RED        = 0xFFFF3B30;
    private static final int ACCENT     = 0xFF5C7CFA;

    // Layout
    private static final int MARGIN    = 6;
    private static final int PADDING   = 4;   // also controls dynamic island height
    private static final int BOX_GAP   = 3;
    private static final int TOAST_GAP = 4;

    // Text scale
    private static final float LIST_TEXT_SCALE   = 0.7f;
    private static final float ISLAND_TEXT_SCALE = 0.8f; // 1.0 = normal, change to resize island text

    // Toast animation
    private static final long TOAST_DURATION_MS = 2000;
    private static final long TOAST_ANIMATE_MS  = 200;

    // Dynamic island
    private static final int ISLAND_RADIUS    = 4;
    private static final int ISLAND_H_PADDING = 9;
    private static final String SEP = "  |  ";
    private static final String MOD_VERSION = "Release-1.6.0";
    private static final DateTimeFormatter CLOCK_FMT = DateTimeFormatter.ofPattern("HH:mm");

    // ════════════════════════════════════════════════════════════════

    private record Toast(String text, int color, long createdAt, long expiresAt) {}
    private static final List<Toast> toasts = new ArrayList<>();

    // ── CPS tracking ──────────────────────────────────────────────────
    private static final Deque<Long> leftClickTimes  = new ArrayDeque<>();
    private static final Deque<Long> rightClickTimes = new ArrayDeque<>();
    private static boolean wasLeftDown  = false;
    private static boolean wasRightDown = false;

    private static void updateCps(MinecraftClient client) {
        long now = System.currentTimeMillis();
        boolean leftDown  = client.options.attackKey.isPressed();
        boolean rightDown = client.options.useKey.isPressed();
        if (leftDown  && !wasLeftDown)  leftClickTimes.addLast(now);
        if (rightDown && !wasRightDown) rightClickTimes.addLast(now);
        wasLeftDown  = leftDown;
        wasRightDown = rightDown;
        while (!leftClickTimes.isEmpty()  && now - leftClickTimes.peekFirst()  > 1000) leftClickTimes.pollFirst();
        while (!rightClickTimes.isEmpty() && now - rightClickTimes.peekFirst() > 1000) rightClickTimes.pollFirst();
    }

    private static int getLeftCps()  { return leftClickTimes.size(); }
    private static int getRightCps() { return rightClickTimes.size(); }

    // ── Draggable element registry ────────────────────────────────────

    public interface Element {
        String id();
        int[] bounds();
    }

    private record SimpleElement(String id, int[] bounds) implements Element {}

    private static final List<Element> elements = new ArrayList<>();
    public static List<Element> getElements() { return elements; }

    // ── Module references (resolved lazily) ───────────────────────────

    private final ModuleManager moduleManager;
    private DynamicIslandModule islandModule;
    private KeystrokeModule keystrokeModule;

    public HudOverlay(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
        HudPosition.load();
    }

    public void register() {
        HudRenderCallback.EVENT.register(this::render);
    }

    public static void pushToast(String text, boolean positive) {
        long now = System.currentTimeMillis();
        toasts.add(new Toast(text, positive ? GREEN : RED, now, now + TOAST_DURATION_MS));
    }

    // ── Lazy module resolution ────────────────────────────────────────

    private DynamicIslandModule islandMod() {
        if (islandModule == null) {
            AbstractModule m = moduleManager.get("island");
            if (m instanceof DynamicIslandModule d) islandModule = d;
        }
        return islandModule;
    }

    private KeystrokeModule keystrokeMod() {
        if (keystrokeModule == null) {
            AbstractModule m = moduleManager.get("keystrokes");
            if (m instanceof KeystrokeModule k) keystrokeModule = k;
        }
        return keystrokeModule;
    }

    // ── Render ────────────────────────────────────────────────────────

    private void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) return;

        updateCps(client);
        elements.clear();

        boolean editing = client.currentScreen
                instanceof com.strongspy.strclient.gui.HudEditScreen;
        if (client.currentScreen != null && !editing) return;

        int screenWidth  = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();

        // Dynamic island — always rendered (hides itself when module is disabled)
        DynamicIslandModule im = islandMod();
        if (im == null || im.isEnabled()) {
            renderDynamicIsland(context, client, screenWidth, im);
        }

        // Enabled modules list — always rendered
        renderEnabledList(context, client, screenWidth, screenHeight);

        // Keystrokes — only when module is enabled
        KeystrokeModule km = keystrokeMod();
        if (km != null && km.isEnabled()) {
            renderKeyDisplay(context, client, screenWidth, screenHeight, km);
        }

        renderToasts(context, client, screenWidth, screenHeight);
    }

    // ── Dynamic Island ────────────────────────────────────────────────

    private void renderDynamicIsland(DrawContext context, MinecraftClient client,
                                     int screenWidth, DynamicIslandModule mod) {
        List<String> parts   = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        // Always: brand
        parts.add("StrClient"); colors.add(ACCENT);

        // Optional: player name
        if (mod != null && mod.getBoolean("showPlayer")) {
            parts.add(client.player.getGameProfile().getName());
            colors.add(TEXT_COLOR);
        }

        // Optional: FPS (on by default)
        if (mod == null || mod.getBoolean("showFps")) {
            int fps = client.getCurrentFps();
            int fpsColor = fps >= 60 ? GREEN : (fps >= 30 ? TEXT_COLOR : RED);
            parts.add(fps + " FPS"); colors.add(fpsColor);
        }

        // Optional: Ping
        if (mod != null && mod.getBoolean("showPing")) {
            int ping = -1;
            PlayerListEntry entry = client.player.networkHandler
                    .getPlayerListEntry(client.player.getUuid());
            if (entry != null) ping = entry.getLatency();
            int pingColor = ping < 0 ? DIM_COLOR : (ping < 80 ? GREEN : ping < 200 ? TEXT_COLOR : RED);
            parts.add(ping < 0 ? "? ms" : ping + " ms"); colors.add(pingColor);
        }

        // Optional: Coordinates
        if (mod != null && mod.getBoolean("showCoords")) {
            int x = (int) client.player.getX();
            int y = (int) client.player.getY();
            int z = (int) client.player.getZ();
            parts.add(x + " " + y + " " + z); colors.add(DIM_COLOR);
        }

        // Optional: MC version
        if (mod != null && mod.getBoolean("showMcVersion")) {
            parts.add(SharedConstants.getGameVersion().getName()); colors.add(DIM_COLOR);
        }

        // Optional: Mod version
        if (mod != null && mod.getBoolean("showModVersion")) {
            parts.add(MOD_VERSION); colors.add(DIM_COLOR);
        }

        // Always: clock
        parts.add(LocalTime.now().format(CLOCK_FMT)); colors.add(TEXT_COLOR);

        // Measure total width (scaled)
        int sepWidth = Math.round(client.textRenderer.getWidth(SEP) * ISLAND_TEXT_SCALE);
        int contentWidth = 0;
        for (String s : parts) contentWidth += Math.round(client.textRenderer.getWidth(s) * ISLAND_TEXT_SCALE);
        contentWidth += sepWidth * (parts.size() - 1);

        int boxW = contentWidth + ISLAND_H_PADDING * 2;
        int boxH = Math.round(client.textRenderer.fontHeight * ISLAND_TEXT_SCALE) + PADDING * 2;

        // ── Centering fix ──────────────────────────────────────────────
        int defaultX = (screenWidth - boxW) / 2;
        int defaultY = 6;

        int x1, y1;
        if (HudPosition.has("island")) {
            // User manually dragged it — respect the saved position
            HudPosition.Pos pos = HudPosition.get("island", defaultX, defaultY);
            x1 = clamp(pos.x(), 0, Math.max(0, screenWidth - boxW));
            y1 = clamp(pos.y(), 0, context.getScaledWindowHeight() - boxH);
        } else {
            // No custom position — re-center every frame based on current content width
            x1 = defaultX;
            y1 = defaultY;
        }
        // ──────────────────────────────────────────────────────────────

        elements.add(new SimpleElement("island", new int[]{x1, y1, x1 + boxW, y1 + boxH}));
        fillRounded(context, x1, y1, x1 + boxW, y1 + boxH, PANEL_BG, ISLAND_RADIUS);

        // Draw text inside a scale matrix so ISLAND_TEXT_SCALE affects font size
        context.getMatrices().push();
        context.getMatrices().translate(x1 + ISLAND_H_PADDING, y1 + PADDING, 0);
        context.getMatrices().scale(ISLAND_TEXT_SCALE, ISLAND_TEXT_SCALE, 1f);

        int x = 0;
        for (int i = 0; i < parts.size(); i++) {
            context.drawTextWithShadow(client.textRenderer, parts.get(i), x, 0, colors.get(i));
            x += client.textRenderer.getWidth(parts.get(i));
            if (i < parts.size() - 1) {
                context.drawTextWithShadow(client.textRenderer, SEP, x, 0, DIM_COLOR);
                x += client.textRenderer.getWidth(SEP);
            }
        }

        context.getMatrices().pop();
    }

    // ── Enabled modules list ──────────────────────────────────────────

    private void renderEnabledList(DrawContext context, MinecraftClient client,
                                   int screenWidth, int screenHeight) {
        List<AbstractModule> enabled = new ArrayList<>();
        for (AbstractModule m : moduleManager.getAll())
            if (m.isEnabled()) enabled.add(m);
        if (enabled.isEmpty()) return;

        int scaledFontH = Math.round(client.textRenderer.fontHeight * LIST_TEXT_SCALE);
        int boxH = scaledFontH + PADDING * 2;

        // Calculate each box width individually
        List<Integer> boxWidths = new ArrayList<>();
        int maxBoxW = 0;
        for (AbstractModule m : enabled) {
            int w = Math.round(client.textRenderer.getWidth(m.getDisplayName()) * LIST_TEXT_SCALE) + PADDING * 2;
            boxWidths.add(w);
            maxBoxW = Math.max(maxBoxW, w);
        }

        int totalH = enabled.size() * boxH + (enabled.size() - 1) * BOX_GAP;

        HudPosition.Pos pos = HudPosition.get("enabledList", MARGIN, MARGIN);
        int groupX = clamp(pos.x(), 0, screenWidth - maxBoxW);
        int groupY = clamp(pos.y(), 0, screenHeight - totalH);

        elements.add(new SimpleElement("enabledList",
                new int[]{groupX, groupY, groupX + maxBoxW, groupY + totalH}));

        // Determine which corner/side the group is anchored to
        boolean anchorRight  = groupX > screenWidth / 2;
        boolean anchorBottom = groupY > screenHeight / 2;

        int y = anchorBottom
                ? groupY + totalH - boxH   // start from bottom, draw upward
                : groupY;                   // start from top, draw downward

        List<AbstractModule> drawOrder = new ArrayList<>(enabled);
        if (anchorBottom) java.util.Collections.reverse(drawOrder);

        for (int i = 0; i < drawOrder.size(); i++) {
            AbstractModule m = drawOrder.get(i);
            String name = m.getDisplayName();
            int idx = enabled.indexOf(m);
            int boxW = boxWidths.get(idx);

            // Align box to the nearest horizontal edge
            int x = anchorRight ? groupX + maxBoxW - boxW : groupX;

            context.fill(x, y, x + boxW, y + boxH, PANEL_BG);

            context.getMatrices().push();
            context.getMatrices().translate(x + PADDING, y + PADDING, 0);
            context.getMatrices().scale(LIST_TEXT_SCALE, LIST_TEXT_SCALE, 1f);
            context.drawTextWithShadow(client.textRenderer, name, 0, 0, TEXT_COLOR);
            context.getMatrices().pop();

            if (anchorBottom) y -= boxH + BOX_GAP;
            else              y += boxH + BOX_GAP;
        }
    }

    // ── Keystroke display ─────────────────────────────────────────────

    private void renderKeyDisplay(DrawContext context, MinecraftClient client,
                                  int screenWidth, int screenHeight, KeystrokeModule mod) {
        String shape = mod.getString("shape");
        int keySize  = mod.getInt("keySize");
        boolean wide = "WIDE".equals(shape);
        int keyW = wide ? (int)(keySize * 1.6f) : keySize;
        int gap  = 2;

        boolean showWasd  = mod.getBoolean("showWasd");
        boolean showMouse = mod.getBoolean("showMouse");

        if (!showWasd && !showMouse) return;

        int wasdBlockW  = showWasd  ? keyW * 3 + gap * 2 : 0;
        int mouseBlockW = showMouse ? keyW * 2 + gap : 0;
        int spacer = (showWasd && showMouse) ? 10 : 0;
        int groupW = wasdBlockW + spacer + mouseBlockW;
        int groupH = keySize * 2 + gap;

        int defaultX = MARGIN;
        int defaultY = screenHeight - groupH - MARGIN - 10;
        HudPosition.Pos pos = HudPosition.get("keys", defaultX, defaultY);
        int gx = clamp(pos.x(), 0, screenWidth - groupW);
        int gy = clamp(pos.y(), 0, screenHeight - groupH);

        elements.add(new SimpleElement("keys", new int[]{gx, gy, gx + groupW, gy + groupH}));

        if (showWasd) {
            drawKey(context, client, gx + keyW + gap, gy, keyW, keySize,
                    "W", client.options.forwardKey.isPressed());
            drawKey(context, client, gx, gy + keySize + gap, keyW, keySize,
                    "A", client.options.leftKey.isPressed());
            drawKey(context, client, gx + keyW + gap, gy + keySize + gap, keyW, keySize,
                    "S", client.options.backKey.isPressed());
            drawKey(context, client, gx + (keyW + gap) * 2, gy + keySize + gap, keyW, keySize,
                    "D", client.options.rightKey.isPressed());
        }

        if (showMouse) {
            int mx0 = gx + wasdBlockW + spacer;
            drawCpsBox(context, client, mx0, gy, keyW, groupH,
                    "L", getLeftCps(),  client.options.attackKey.isPressed());
            drawCpsBox(context, client, mx0 + keyW + gap, gy, keyW, groupH,
                    "R", getRightCps(), client.options.useKey.isPressed());
        }
    }

    private void drawKey(DrawContext ctx, MinecraftClient client,
                         int x, int y, int w, int h, String label, boolean pressed) {
        int bg = pressed ? (0xB0 << 24 | (ACCENT & 0x00FFFFFF)) : PANEL_BG;
        int tc = pressed ? 0xFFFFFFFF : DIM_COLOR;
        fillRounded(ctx, x, y, x + w, y + h, bg, 3);
        int tw = client.textRenderer.getWidth(label);
        ctx.drawTextWithShadow(client.textRenderer, label,
                x + (w - tw) / 2, y + (h - client.textRenderer.fontHeight) / 2, tc);
    }

    private void drawCpsBox(DrawContext ctx, MinecraftClient client,
                            int x, int y, int w, int h, String label, int cps, boolean pressed) {
        int bg = pressed ? (0xB0 << 24 | (ACCENT & 0x00FFFFFF)) : PANEL_BG;
        fillRounded(ctx, x, y, x + w, y + h, bg, 3);
        int labelColor = pressed ? 0xFFFFFFFF : DIM_COLOR;
        int cpsColor   = cps > 0 ? (pressed ? 0xFFFFFFFF : ACCENT) : DIM_COLOR;
        int lw = client.textRenderer.getWidth(label);
        int cw = client.textRenderer.getWidth(String.valueOf(cps));
        int lineH = client.textRenderer.fontHeight;
        ctx.drawTextWithShadow(client.textRenderer, label,
                x + (w - lw) / 2, y + (h / 2 - lineH) / 2 + 1, labelColor);
        ctx.drawTextWithShadow(client.textRenderer, String.valueOf(cps),
                x + (w - cw) / 2, y + h / 2 + (h / 2 - lineH) / 2 + 1, cpsColor);
    }

    // ── Toasts ────────────────────────────────────────────────────────

    private void renderToasts(DrawContext context, MinecraftClient client,
                              int screenWidth, int screenHeight) {
        if (toasts.isEmpty()) return;
        long now = System.currentTimeMillis();
        toasts.removeIf(t -> now > t.expiresAt() + TOAST_ANIMATE_MS);
        if (toasts.isEmpty()) return;

        int boxH  = client.textRenderer.fontHeight + PADDING * 2;
        int totalH = toasts.size() * boxH + (toasts.size() - 1) * TOAST_GAP;
        int baseY  = screenHeight - MARGIN - totalH;

        for (Toast toast : toasts) {
            long age = now - toast.createdAt();
            long remaining = toast.expiresAt() - now;
            float progress;
            if (age < TOAST_ANIMATE_MS) progress = (float) age / TOAST_ANIMATE_MS;
            else if (remaining > 0) progress = 1f;
            else progress = Math.max(0f, 1f - (float)(-remaining) / TOAST_ANIMATE_MS);
            progress = progress < 0.5f
                    ? 2f * progress * progress
                    : 1f - (float) Math.pow(-2f * progress + 2f, 2) / 2f;

            int width = client.textRenderer.getWidth(toast.text());
            int boxW  = width + PADDING * 2;
            int targetX = screenWidth - MARGIN - boxW;
            int x1 = targetX + (int)((1f - progress) * (boxW + MARGIN));

            context.fill(x1, baseY, x1 + boxW, baseY + boxH,
                    ((int)(0x70 * progress) << 24));
            context.drawTextWithShadow(client.textRenderer, toast.text(),
                    x1 + PADDING, baseY + PADDING,
                    ((int)(0xFF * progress) << 24) | (toast.color() & 0x00FFFFFF));

            baseY += boxH + TOAST_GAP;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private void fillRounded(DrawContext ctx, int x1, int y1, int x2, int y2, int color, int r) {
        if (r <= 0) { ctx.fill(x1, y1, x2, y2, color); return; }
        ctx.fill(x1 + r, y1, x2 - r, y2, color);
        ctx.fill(x1, y1 + r, x1 + r, y2 - r, color);
        ctx.fill(x2 - r, y1 + r, x2, y2 - r, color);
    }

    private static int clamp(int v, int min, int max) {
        if (max < min) return min;
        return Math.max(min, Math.min(max, v));
    }
}