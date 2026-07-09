package com.strongspy.strclient.gui;

import com.strongspy.strclient.core.AbstractModule;
import com.strongspy.strclient.core.ModuleManager;
import com.strongspy.strclient.core.ModuleSetting;
import com.strongspy.strclient.core.SettingsManager;
import com.strongspy.strclient.core.ProfileManager; // 📝 1. 导入 ProfileManager
import net.minecraft.client.MinecraftClient;       // 📝 2. 导入 MinecraftClient
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.*;

public class StrClientScreen extends Screen {

    // ── Colors ──────────────────────────────────────────────────────
    private static final int BG           = 0x70000000;
    private static final int BG_HEADER    = 0x90000000;
    private static final int TEXT         = 0xFFE8EAF0;
    private static final int TEXT_SUB     = 0xFF9AA0B5;
    private static final int ENABLED_BAR  = 0xFF34C759;
    private static final int DISABLED_BAR = 0xFF555566;
    private static final int ACCENT       = 0xFF5C7CFA;
    private static final int ACCENT_DIM   = 0x555C7CFA;
    private static final int SEPARATOR    = 0x30FFFFFF;

    // ── Layout ──────────────────────────────────────────────────────
    private static final int PANEL_W       = 160;
    private static final int HEADER_H      = 14;
    private static final int ROW_H         = 13;
    private static final int PADDING       = 5;
    private static final int SETTING_INDENT = 8;

    private final ModuleManager moduleManager;
    private final SettingsManager settingsManager;

    private final List<CategoryPanel> panels = new ArrayList<>();
    private AbstractModule openSettings = null;

    // ── Drag state ───────────────────────────────────────────────────
    private CategoryPanel dragging = null;
    private int dragOffX, dragOffY;

    // ── Slider drag state ────────────────────────────────────────────
    private AbstractModule sliderModule = null;
    private String sliderKey = null;
    private double sliderMin, sliderMax;
    private int sliderX1, sliderX2;

    // ── Animation state ──────────────────────────────────────────────
    private float openProgress  = 0f;
    private boolean closing     = false;
    private float closeProgress = 1f;

    // ── Profile state ──────────────────────────────────────────────── 📝 3. 配置文件状态字段
    private List<String> profileList = new ArrayList<>();
    private String exportName = "myprofile";
    private String profileFeedback = ""; // 成功/错误提示信息
    private long feedbackExpiry = 0;

    private static class CategoryPanel {
        final String name;
        final AbstractModule.Category category;
        int x, y;
        boolean collapsed = false;

        CategoryPanel(String name, AbstractModule.Category category, int x, int y) {
            this.name = name;
            this.category = category;
            this.x = x;
            this.y = y;
        }
    }

    public StrClientScreen(ModuleManager moduleManager, SettingsManager settingsManager) {
        super(Text.literal("StrClient"));
        this.moduleManager  = moduleManager;
        this.settingsManager = settingsManager;
    }

    @Override
    public void init() {
        panels.clear();
        openProgress  = 0f;
        closing       = false;
        closeProgress = 1f;

        int x = 10, y = 10, gap = 8;
        for (AbstractModule.Category cat : AbstractModule.Category.values()) {
            panels.add(new CategoryPanel(catName(cat), cat, x, y));
            x += PANEL_W + gap;
        }

        // 📝 4. 初始化时加载本地已有的 Profile 列表
        profileList = ProfileManager.listProfiles();
    }

    public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {}

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (!closing && openProgress < 1f) {
            openProgress = Math.min(1f, openProgress + delta * 0.13f);
        }
        if (closing) {
            closeProgress = Math.max(0f, closeProgress - delta * 0.16f);
            if (closeProgress <= 0f) { super.close(); return; }
        }

        float progress = closing ? closeProgress : openProgress;
        float eased = 1f - (float) Math.pow(1f - progress, 3);

        // 渲染原有的四大类面板
        for (int i = 0; i < panels.size(); i++) {
            CategoryPanel panel = panels.get(i);
            float panelProgress = Math.min(1f, eased * 1.2f - i * 0.08f);
            panelProgress = Math.max(0f, panelProgress);
            renderPanel(ctx, panel, mouseX, mouseY, panelProgress);
        }

        // 📝 5. 在功能面板循环之后，渲染独立的左下角 Profile 面板
        renderProfilePanel(ctx, mouseX, mouseY, eased);

        super.render(ctx, mouseX, mouseY, delta);
    }

    // 📝 6. 新增 Profile 面板渲染具体实现方法
    private void renderProfilePanel(DrawContext ctx, int mouseX, int mouseY, float progress) {
        // 固定位置：左下角
        int pw  = PANEL_W + 20;
        int px  = 10;
        int py  = this.height - 10;

        int rowCount = 3 + profileList.size(); // header + export row + separator + profile rows
        int totalH = HEADER_H + rowCount * ROW_H + 4;
        py -= totalH;

        int bgAlpha     = (int)(0x70 * progress);
        int headerAlpha = (int)(0x90 * progress);
        int textAlpha   = (int)(0xFF * progress);
        int subAlpha    = (int)(0xFF * progress);
        int accentAlpha = (int)(0xFF * progress);
        int sepAlpha    = (int)(0x30 * progress);

        int bg          = (bgAlpha     << 24);
        int bgHeader    = (headerAlpha << 24);
        int textColor   = (textAlpha   << 24) | (TEXT        & 0x00FFFFFF);
        int subColor    = (subAlpha    << 24) | (TEXT_SUB    & 0x00FFFFFF);
        int accentColor = (accentAlpha << 24) | (ACCENT      & 0x00FFFFFF);
        int sepColor    = (sepAlpha    << 24) | (SEPARATOR   & 0x00FFFFFF);

        ctx.fill(px, py, px + pw, py + totalH, bg);
        ctx.fill(px, py, px + pw, py + HEADER_H, bgHeader);
        ctx.fill(px, py, px + 2,  py + HEADER_H, accentColor);
        ctx.drawText(textRenderer, "Profiles", px + 5, py + (HEADER_H - 8) / 2, textColor, false);

        int y = py + HEADER_H;

        // 导出名称行（点击切换后缀）
        ctx.drawText(textRenderer, "Export as:", px + SETTING_INDENT, y + (ROW_H - 8) / 2, subColor, false);
        String exportLabel = "[" + exportName + "]";
        int elw = textRenderer.getWidth(exportLabel);
        ctx.drawText(textRenderer, exportLabel, px + pw - elw - PADDING, y + (ROW_H - 8) / 2, accentColor, false);
        y += ROW_H;

        // 确认导出按钮
        ctx.drawText(textRenderer, ">> Export current settings", px + SETTING_INDENT, y + (ROW_H - 8) / 2, accentColor, false);
        ctx.fill(px + SETTING_INDENT, y + ROW_H - 1, px + pw, y + ROW_H, sepColor);
        y += ROW_H;

        // 操作反馈提示文字
        if (!profileFeedback.isEmpty() && System.currentTimeMillis() < feedbackExpiry) {
            ctx.drawText(textRenderer, profileFeedback, px + SETTING_INDENT, y + (ROW_H - 8) / 2, textColor, false);
            y += ROW_H;
        }

        // 分割线
        ctx.fill(px + 2, y, px + pw, y + 1, sepColor);
        y += 4;

        // 已经存在的配置列表加载行
        if (profileList.isEmpty()) {
            ctx.drawText(textRenderer, "No profiles yet", px + SETTING_INDENT, y + (ROW_H - 8) / 2, subColor, false);
        } else {
            for (String profile : profileList) {
                boolean hover = mouseX >= px && mouseX <= px + pw && mouseY >= y && mouseY <= y + ROW_H;
                if (hover) ctx.fill(px, y, px + pw, y + ROW_H, (int)(0x20 * progress) << 24);

                ctx.fill(px, y, px + 2, y + ROW_H, (accentAlpha << 24) | (ENABLED_BAR & 0x00FFFFFF));
                ctx.drawText(textRenderer, profile, px + SETTING_INDENT, y + (ROW_H - 8) / 2, textColor, false);

                String importBtn = "Load";
                int ibw = textRenderer.getWidth(importBtn);
                ctx.drawText(textRenderer, importBtn, px + pw - ibw - PADDING, y + (ROW_H - 8) / 2, accentColor, false);
                ctx.fill(px + SETTING_INDENT, y + ROW_H - 1, px + pw, y + ROW_H, sepColor);
                y += ROW_H;
            }
        }
    }

    private void renderPanel(DrawContext ctx, CategoryPanel panel,
                             int mouseX, int mouseY, float progress) {
        List<AbstractModule> modules = modulesFor(panel.category);

        int contentH = 0;
        if (!panel.collapsed) {
            for (AbstractModule m : modules) {
                contentH += ROW_H;
                if (m == openSettings) contentH += settingsHeight(m);
            }
        }
        int totalH = HEADER_H + contentH;
        int pw = PANEL_W;
        int px = panel.x;
        int py = panel.y;

        int slideOffset = (int) ((1f - progress) * -24f);
        int renderY = py + slideOffset;

        int bgAlpha      = (int) (0x70 * progress);
        int headerAlpha  = (int) (0x90 * progress);
        int textAlpha    = (int) (0xFF * progress);
        int subAlpha     = (int) (0xFF * progress);
        int accentAlpha  = (int) (0xFF * progress);
        int sepAlpha     = (int) (0x30 * progress);

        int bg         = (bgAlpha     << 24);
        int bgHeader   = (headerAlpha << 24);
        int textColor  = (textAlpha   << 24) | (TEXT         & 0x00FFFFFF);
        int subColor   = (subAlpha    << 24) | (TEXT_SUB     & 0x00FFFFFF);
        int accentColor= (accentAlpha << 24) | (ACCENT       & 0x00FFFFFF);
        int sepColor   = (sepAlpha    << 24) | (SEPARATOR    & 0x00FFFFFF);

        ctx.fill(px, renderY, px + pw, renderY + totalH, bg);
        ctx.fill(px, renderY, px + pw, renderY + HEADER_H, bgHeader);
        ctx.fill(px, renderY, px + 2, renderY + HEADER_H, accentColor);

        ctx.drawText(textRenderer, panel.name, px + 5, renderY + (HEADER_H - 8) / 2, textColor, false);

        String arrow = panel.collapsed ? "+" : "-";
        ctx.drawText(textRenderer, arrow, px + pw - 10, renderY + (HEADER_H - 8) / 2, subColor, false);

        if (!panel.collapsed) {
            int rowY = renderY + HEADER_H;
            for (AbstractModule m : modules) {
                renderModuleRow(ctx, m, px, rowY, pw, mouseX, mouseY, textColor, subColor, sepColor, progress);
                rowY += ROW_H;
                if (m == openSettings) {
                    rowY = renderSettings(ctx, m, px, rowY, pw, mouseX, mouseY, textColor, subColor, accentColor, sepColor, progress);
                }
            }
        }
    }

    private void renderModuleRow(DrawContext ctx, AbstractModule m,
                                 int px, int py, int pw, int mouseX, int mouseY,
                                 int textColor, int subColor, int sepColor, float progress) {
        boolean hover = mouseX >= px && mouseX <= px + pw && mouseY >= py && mouseY <= py + ROW_H;
        if (hover) ctx.fill(px, py, px + pw, py + ROW_H, (int)(0x20 * progress) << 24);

        int enabledColor  = ((int)(0xFF * progress) << 24) | (ENABLED_BAR  & 0x00FFFFFF);
        int disabledColor = ((int)(0xFF * progress) << 24) | (DISABLED_BAR & 0x00FFFFFF);

        ctx.fill(px, py, px + 2, py + ROW_H, m.isEnabled() ? enabledColor : disabledColor);

        int nameColor = m.isEnabled() ? textColor : subColor;
        ctx.drawText(textRenderer, m.getDisplayName(), px + 5, py + (ROW_H - 8) / 2, nameColor, false);

        String stateLabel = m.isEnabled() ? "ON" : "OFF";
        int labelColor = m.isEnabled() ? enabledColor : disabledColor;
        int labelW = textRenderer.getWidth(stateLabel);
        ctx.drawText(textRenderer, stateLabel, px + pw - labelW - PADDING, py + (ROW_H - 8) / 2, labelColor, false);

        ctx.fill(px + 2, py + ROW_H - 1, px + pw, py + ROW_H, sepColor);
    }

    private int renderSettings(DrawContext ctx, AbstractModule m,
                               int px, int startY, int pw,
                               int mouseX, int mouseY,
                               int textColor, int subColor, int accentColor, int sepColor,
                               float progress) {
        int y = startY;
        int settH = settingsHeight(m);

        int settBgAlpha   = (int)(0x40 * progress);
        int accentDimAlpha = (int)(0x55 * progress);
        ctx.fill(px, y, px + pw, y + settH, settBgAlpha << 24);
        ctx.fill(px, y, px + 2, y + settH, (accentDimAlpha << 24) | (ACCENT_DIM & 0x00FFFFFF));

        for (ModuleSetting<?> s : m.getSettings().values()) {
            int rowBottom = y + ROW_H;
            ctx.drawText(textRenderer, s.getDisplayName(), px + SETTING_INDENT, y + (ROW_H - 8) / 2, subColor, false);

            switch (s.getType()) {
                case BOOLEAN -> {
                    boolean val = (Boolean) s.getValue();
                    int lc = val ? ((int)(0xFF * progress) << 24) | (ENABLED_BAR  & 0x00FFFFFF)
                            : ((int)(0xFF * progress) << 24) | (DISABLED_BAR & 0x00FFFFFF);
                    int lw = textRenderer.getWidth(val ? "ON" : "OFF");
                    ctx.drawText(textRenderer, val ? "ON" : "OFF", px + pw - lw - PADDING, y + (ROW_H - 8) / 2, lc, false);
                }
                case DOUBLE, INT -> {
                    double val = ((Number) s.getValue()).doubleValue();
                    String vt = s.getType() == ModuleSetting.Type.INT ? String.valueOf((int) Math.round(val)) : String.format("%.1f", val);
                    int vtW = textRenderer.getWidth(vt);
                    ctx.drawText(textRenderer, vt, px + pw - vtW - PADDING, y + (ROW_H - 8) / 2, accentColor, false);

                    int barX1 = px + SETTING_INDENT + textRenderer.getWidth(s.getDisplayName()) + 4;
                    int barX2 = px + pw - vtW - PADDING - 4;
                    int barY  = y + ROW_H / 2;
                    if (barX2 > barX1 + 10) {
                        ctx.fill(barX1, barY - 1, barX2, barY + 1, ((int)(0x40 * progress) << 24) | 0xFFFFFF);
                        double t = s.getMax() > s.getMin() ? (val - s.getMin()) / (s.getMax() - s.getMin()) : 0;
                        int filled = barX1 + (int)((barX2 - barX1) * Math.max(0, Math.min(1, t)));
                        ctx.fill(barX1, barY - 1, filled, barY + 1, accentColor);
                        ctx.fill(filled - 1, barY - 2, filled + 1, barY + 2, textColor);
                    }
                }
                case STRING -> {
                    String val = (String) s.getValue();
                    int vw = textRenderer.getWidth(val);
                    ctx.drawText(textRenderer, val, px + pw - vw - PADDING, y + (ROW_H - 8) / 2, accentColor, false);
                }
            }
            ctx.fill(px + SETTING_INDENT, rowBottom - 1, px + pw, rowBottom, sepColor);
            y += ROW_H;
        }

        // ── Integrated CapePatch Custom Rendering ──
        if (m.getId().equals("capepatch") && m instanceof com.strongspy.strclient.modules.capepatch.CapePatchModule cm) {
            List<String> capes = cm.getAvailableCapes();

            if (capes.isEmpty()) {
                ctx.drawText(textRenderer, "No PNGs found in JAR assets!", px + SETTING_INDENT, y + (ROW_H - 8) / 2, DISABLED_BAR, false);
                y += ROW_H;
            } else {
                ctx.drawText(textRenderer, "── Capes ──", px + SETTING_INDENT, y + (ROW_H - 8) / 2, TEXT_SUB, false);
                y += ROW_H;

                for (String cape : capes) {
                    boolean on = cm.isCapeEnabled(cape);

                    if (inRow(px, y, pw, ROW_H, mouseX, mouseY))
                        ctx.fill(px, y, px + pw, y + ROW_H, 0x20FFFFFF);

                    ctx.fill(px, y, px + 2, y + ROW_H, on ? ENABLED_BAR : DISABLED_BAR);

                    String display = textRenderer.trimToWidth(cape, pw - SETTING_INDENT - 30);
                    ctx.drawText(textRenderer, display, px + SETTING_INDENT, y + (ROW_H - 8) / 2, on ? TEXT : TEXT_SUB, false);

                    String label = on ? "ON" : "OFF";
                    int lw = textRenderer.getWidth(label);
                    ctx.drawText(textRenderer, label, px + pw - lw - PADDING, y + (ROW_H - 8) / 2, on ? ENABLED_BAR : DISABLED_BAR, false);

                    ctx.fill(px + SETTING_INDENT, y + ROW_H - 1, px + pw, y + ROW_H, SEPARATOR);
                    y += ROW_H;
                }

                ctx.drawText(textRenderer, "Re-roll Cape", px + SETTING_INDENT, y + (ROW_H - 8) / 2, ACCENT, false);
                y += ROW_H;
            }
        }

        return y;
    }

    private int settingsHeight(AbstractModule m) {
        int height = m.getSettings().size() * ROW_H;
        if (m.getId().equals("capepatch") && m instanceof com.strongspy.strclient.modules.capepatch.CapePatchModule cm) {
            List<String> capes = cm.getAvailableCapes();
            if (capes.isEmpty()) {
                height += ROW_H;
            } else {
                height += ROW_H; // Header
                height += capes.size() * ROW_H; // Items
                height += ROW_H; // Re-roll button
            }
        }
        return height;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        for (CategoryPanel panel : panels) {
            if (inHeader(panel, mx, my)) {
                if (btn == 0) {
                    if (mx >= panel.x + PANEL_W - 14) {
                        panel.collapsed = !panel.collapsed;
                    } else {
                        dragging = panel;
                        dragOffX = (int) mx - panel.x;
                        dragOffY = (int) my - panel.y;
                    }
                }
                return true;
            }

            if (panel.collapsed) continue;

            List<AbstractModule> modules = modulesFor(panel.category);
            int rowY = panel.y + HEADER_H;

            for (AbstractModule m : modules) {
                if (inRow(panel.x, rowY, PANEL_W, ROW_H, mx, my)) {
                    if (btn == 0) {
                        m.toggle(this.client);
                        settingsManager.save(moduleManager);
                        if (openSettings == m && !m.isEnabled()) openSettings = null;
                    } else if (btn == 1) {
                        openSettings = (openSettings == m) ? null : m;
                    }
                    return true;
                }
                rowY += ROW_H;

                if (m == openSettings) {
                    for (ModuleSetting<?> s : m.getSettings().values()) {
                        if (inRow(panel.x, rowY, PANEL_W, ROW_H, mx, my)) {
                            handleSettingClick(m, s, panel.x, rowY, PANEL_W, mx, btn);
                            return true;
                        }
                        rowY += ROW_H;
                    }

                    // ── Integrated CapePatch Click Handling ──
                    if (m.getId().equals("capepatch") && m instanceof com.strongspy.strclient.modules.capepatch.CapePatchModule cm) {
                        MinecraftClient client = MinecraftClient.getInstance();
                        List<String> capes = cm.getAvailableCapes();
                        int capeRowY = rowY;

                        capeRowY += ROW_H; // skip header

                        for (String cape : capes) {
                            if (inRow(panel.x, capeRowY, PANEL_W, ROW_H, mx, my)) {
                                cm.toggleCape(client, cape);
                                return true;
                            }
                            capeRowY += ROW_H;
                        }

                        if (!capes.isEmpty() && inRow(panel.x, capeRowY, PANEL_W, ROW_H, mx, my)) {
                            cm.pickCape();
                            return true;
                        }
                    }
                }
            }
        }

        // 📝 7. 在原有组件都没有拦截点击事件时，进行 Profile 面板的点击拦截处理
        {
            int pw  = PANEL_W + 20;
            int px  = 10;
            int totalH = HEADER_H + (3 + profileList.size()) * ROW_H + 4;
            int py  = this.height - 10 - totalH;

            if (mx >= px && mx <= px + pw && my >= py && my <= py + totalH) {
                int y = py + HEADER_H;

                // 拦截：点击导出名称行（动态生成随机时间戳后缀防止文件名冲突）
                if (inRow(px, y, pw, ROW_H, mx, my) && btn == 0) {
                    exportName = "profile_" + System.currentTimeMillis() % 100000;
                    return true;
                }
                y += ROW_H;

                // 拦截：点击导出配置按钮
                if (inRow(px, y, pw, ROW_H, mx, my) && btn == 0) {
                    ProfileManager.export(moduleManager, exportName);
                    profileList = ProfileManager.listProfiles(); // 导出后刷新一次列表
                    profileFeedback = "Exported: " + exportName;
                    feedbackExpiry = System.currentTimeMillis() + 2500;
                    return true;
                }
                y += ROW_H;

                // 跳过提示行计算
                if (!profileFeedback.isEmpty() && System.currentTimeMillis() < feedbackExpiry) {
                    y += ROW_H;
                }

                y += 4; // 隔离带

                // 拦截：点击任意一个已存在 Profile 的 Load（导入）按钮
                for (String profile : profileList) {
                    if (inRow(px, y, pw, ROW_H, mx, my) && btn == 0) {
                        boolean ok = ProfileManager.importProfile(
                                moduleManager, settingsManager, this.client, profile);
                        profileFeedback = ok ? "Loaded: " + profile : "Failed to load: " + profile;
                        feedbackExpiry = System.currentTimeMillis() + 2500;
                        return true;
                    }
                    y += ROW_H;
                }
                return true;
            }
        }

        return super.mouseClicked(mx, my, btn);
    }

    private void handleSettingClick(AbstractModule m, ModuleSetting<?> s,
                                    int px, int rowY, int pw, double mx, int btn) {
        switch (s.getType()) {
            case BOOLEAN -> {
                s.setFromObject(!(Boolean) s.getValue());
                settingsManager.save(moduleManager);
            }
            case DOUBLE, INT -> {
                String vt = s.getType() == ModuleSetting.Type.INT ? String.valueOf((int) Math.round(((Number) s.getValue()).doubleValue())) : String.format("%.1f", ((Number) s.getValue()).doubleValue());
                int vtW  = textRenderer.getWidth(vt);
                int barX1 = px + SETTING_INDENT + textRenderer.getWidth(s.getDisplayName()) + 4;
                int barX2 = px + pw - vtW - PADDING - 4;

                sliderModule = m;
                sliderKey    = s.getKey();
                sliderMin    = s.getMin();
                sliderMax    = s.getMax();
                sliderX1     = barX1;
                sliderX2     = barX2;
                applySlider(mx);
            }
            case STRING -> {
                String[] opts = s.getOptions();
                if (opts != null && opts.length > 0) {
                    String cur = (String) s.getValue();
                    int idx = 0;
                    for (int i = 0; i < opts.length; i++) if (opts[i].equals(cur)) idx = i;
                    s.setFromObject(opts[(idx + 1) % opts.length]);
                    settingsManager.save(moduleManager);
                }
            }
        }
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (dragging != null) {
            dragging.x = (int) mx - dragOffX;
            dragging.y = (int) my - dragOffY;
            return true;
        }
        if (sliderModule != null) { applySlider(mx); return true; }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        dragging = null;
        if (sliderModule != null) {
            settingsManager.save(moduleManager);
            sliderModule = null;
            sliderKey    = null;
        }
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { this.close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        if (!closing) {
            closing       = true;
            closeProgress = openProgress;
        }
    }

    private void applySlider(double mx) {
        double t = sliderX2 > sliderX1 ? Math.max(0, Math.min(1, (mx - sliderX1) / (double)(sliderX2 - sliderX1))) : 0;
        sliderModule.getSetting(sliderKey).setFromObject(sliderMin + t * (sliderMax - sliderMin));
    }

    private List<AbstractModule> modulesFor(AbstractModule.Category cat) {
        List<AbstractModule> list = new ArrayList<>();
        for (AbstractModule m : moduleManager.getAll()) if (m.getCategory() == cat) list.add(m);
        return list;
    }

    private boolean inHeader(CategoryPanel p, double mx, double my) {
        return mx >= p.x && mx <= p.x + PANEL_W && my >= p.y && my <= p.y + HEADER_H;
    }

    private boolean inRow(int px, int py, int pw, int rh, double mx, double my) {
        return mx >= px && mx <= px + pw && my >= py && my <= py + rh;
    }

    private String catName(AbstractModule.Category cat) {
        return switch (cat) {
            case COMBAT   -> "Combat";
            case MOVEMENT -> "Movement";
            case VISUAL   -> "Visual";
            case UTILITY  -> "Utility";
        };
    }
}