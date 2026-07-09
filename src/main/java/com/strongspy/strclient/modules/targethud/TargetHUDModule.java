package com.strongspy.strclient.modules.targethud;

import com.strongspy.strclient.core.AbstractModule;
import com.strongspy.strclient.core.ModuleSetting;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

public class TargetHUDModule extends AbstractModule {

    private boolean registered = false;

    // 🌟 将锁定目标设为全局公有静态变量，方便 KillAura 直接强行赋值
    public static PlayerEntity lockedTarget = null;
    public static long lockTime = 0;
    private static final long LOCK_DURATION = 3500; // 锁定持续3.5秒

    public TargetHUDModule() {
        super("targethud", "Target HUD", Category.VISUAL,
                "Display name, health bar, and avatar of target players.");
    }

    @Override
    protected void registerSettings() {
        registerSetting(ModuleSetting.ofBoolean("showAvatar", "Show Avatar", "Display player skin avatar", true));
        registerSetting(ModuleSetting.ofInt("avatarSize", "Avatar Size (pixels)", "Size of the avatar", 32, 16, 64));
        registerSetting(ModuleSetting.ofString("position", "Position", "Screen position Base",
                "CROSSHAIR_RIGHT",
                "CROSSHAIR_RIGHT", "CROSSHAIR_LEFT", "CROSSHAIR_BOTTOM", "CROSSHAIR_TOP",
                "TOP_LEFT", "TOP_CENTER", "TOP_RIGHT",
                "BOTTOM_LEFT", "BOTTOM_CENTER", "BOTTOM_RIGHT",
                "CENTER"));
        registerSetting(ModuleSetting.ofBoolean("showHealthNumber", "Show Health Number", "Display health as numerical values", true));
        registerSetting(ModuleSetting.ofInt("barWidth", "Bar Width (pixels)", "Width of the health bar", 100, 50, 200));
        registerSetting(ModuleSetting.ofInt("offsetX", "X Offset", "Horizontal offset from position", 10, -100, 100));
        registerSetting(ModuleSetting.ofInt("offsetY", "Y Offset", "Vertical offset from position", 0, -100, 100));
    }

    @Override
    public void onEnable(MinecraftClient client) {
        if (!registered) {
            HudRenderCallback.EVENT.register(this::render);
            registered = true;
        }
    }

    @Override
    public void onDisable(MinecraftClient client) {
        lockedTarget = null;
        lockTime = 0;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        // 🛡️ 兜底方案：如果手动左键攻击或没有开启 KillAura 时，原版准星指着人也能锁定
        HitResult hit = client.crosshairTarget;
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof PlayerEntity player) {
            if (player != client.player && !player.isDead()) {
                lockedTarget = player;
                lockTime = System.currentTimeMillis();
            }
        }

        // 自动检查锁定的目标是否超时失效（超过 3.5 秒没被打或者已经死掉）
        if (lockedTarget != null) {
            if (lockedTarget.isDead() || !lockedTarget.isAlive() || (System.currentTimeMillis() - lockTime > LOCK_DURATION)) {
                lockedTarget = null;
            }
        }
    }

    private void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || !isEnabled()) return;

        // 🌟 1. 核心：如果 KillAura 强行上报了攻击目标，无视一切准星，绝对优先渲染它
        if (lockedTarget != null) {
            renderSinglePlayerHUD(context, client, lockedTarget, Formatting.RED + " [TARGET]");
            return;
        }

        // 🌟 2. 备用：如果没有受到 KillAura 攻击的目标，才渲染目前准星看着的人
        HitResult crosshairHit = client.crosshairTarget;
        if (crosshairHit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof PlayerEntity player) {
            if (player != client.player && !player.isDead()) {
                renderSinglePlayerHUD(context, client, player, Formatting.GREEN + " [LOOKING]");
            }
        }
    }

    private void renderSinglePlayerHUD(DrawContext context, MinecraftClient client, PlayerEntity target, String statusTag) {
        boolean showAvatar = getBoolean("showAvatar");
        String name = getTargetName(target) + statusTag;
        float health = target.getHealth();
        float maxHealth = target.getMaxHealth();
        float healthPercent = Math.max(0.0f, Math.min(1.0f, health / maxHealth));
        int barWidth = getInt("barWidth");
        int barHeight = 8;
        boolean showNumber = getBoolean("showHealthNumber");
        int avatarSize = getInt("avatarSize");

        TextRenderer textRenderer = client.textRenderer;
        String healthText = showNumber ? String.format(" %.1f/%.1f", health, maxHealth) : "";
        String displayText = name + healthText;
        int textWidth = textRenderer.getWidth(displayText);
        int textHeight = textRenderer.fontHeight;

        int padding = 4;
        int avatarSpacing = 6;
        int contentX, contentY;
        int totalWidth, totalHeight;

        if (showAvatar) {
            totalWidth = avatarSize + avatarSpacing + Math.max(textWidth, barWidth) + padding * 2;
            totalHeight = Math.max(avatarSize, textHeight + 2 + barHeight) + padding * 2;
            contentX = avatarSize + avatarSpacing + padding;
            contentY = padding;
        } else {
            totalWidth = Math.max(textWidth, barWidth) + padding * 2;
            totalHeight = textHeight + 2 + barHeight + padding * 2;
            contentX = padding;
            contentY = padding;
        }

        int[] pos = calculatePosition(client, totalWidth, totalHeight);
        int x = pos[0];
        int y = pos[1];

        // 背景
        context.fill(x, y, x + totalWidth, y + totalHeight, 0xAA000000);

        // 头像
        if (showAvatar) {
            int avatarX = x + padding;
            int avatarY = y + (totalHeight - avatarSize) / 2;
            Identifier skinTexture = getSkinTexture(target);
            if (skinTexture != null) {
                context.drawTexture(skinTexture, avatarX, avatarY, avatarSize, avatarSize, 8.0f, 8.0f, 8, 8, 64, 64);
                context.drawTexture(skinTexture, avatarX, avatarY, avatarSize, avatarSize, 40.0f, 8.0f, 8, 8, 64, 64);
            }
        }

        // 文字与血条
        int textX = x + contentX;
        int textY = y + contentY;
        context.drawTextWithShadow(textRenderer, Text.literal(displayText), textX, textY, 0xFFFFFF);

        int barY = textY + textHeight + 2;
        int barX = textX;
        context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF333333);
        int barFill = (int) (barWidth * healthPercent);
        context.fill(barX, barY, barX + barFill, barY + barHeight, getHealthColor(healthPercent));
        context.drawBorder(barX, barY, barWidth, barHeight, 0xFFAAAAAA);
    }

    private String getTargetName(PlayerEntity player) {
        String name = player.getName().getString();
        var team = player.getScoreboardTeam();
        if (team != null && team.getColor() != null) name = team.getColor() + name;
        return name;
    }

    private Identifier getSkinTexture(PlayerEntity player) {
        try { return MinecraftClient.getInstance().getSkinProvider().getSkinTextures(player.getGameProfile()).texture(); }
        catch (Exception e) { return null; }
    }

    private int[] calculatePosition(MinecraftClient client, int width, int height) {
        int windowWidth = client.getWindow().getScaledWidth();
        int windowHeight = client.getWindow().getScaledHeight();
        int offsetX = getInt("offsetX");
        int offsetY = getInt("offsetY");
        String pos = getString("position");

        int x, y;
        switch (pos) {
            case "CROSSHAIR_RIGHT" -> { x = windowWidth / 2 + 20 + offsetX; y = windowHeight / 2 - height / 2 + offsetY; }
            case "CROSSHAIR_LEFT" -> { x = windowWidth / 2 - width - 20 + offsetX; y = windowHeight / 2 - height / 2 + offsetY; }
            case "CROSSHAIR_BOTTOM" -> { x = windowWidth / 2 - width / 2 + offsetX; y = windowHeight / 2 + 20 + offsetY; }
            case "CROSSHAIR_TOP" -> { x = windowWidth / 2 - width / 2 + offsetX; y = windowHeight / 2 - height - 20 + offsetY; }
            case "TOP_LEFT" -> { x = 10 + offsetX; y = 10 + offsetY; }
            case "TOP_CENTER" -> { x = (windowWidth - width) / 2 + offsetX; y = 10 + offsetY; }
            case "TOP_RIGHT" -> { x = windowWidth - width - 10 + offsetX; y = 10 + offsetY; }
            case "BOTTOM_LEFT" -> { x = 10 + offsetX; y = windowHeight - height - 10 + offsetY; }
            case "BOTTOM_CENTER" -> { x = (windowWidth - width) / 2 + offsetX; y = windowHeight - height - 15 + offsetY; }
            case "BOTTOM_RIGHT" -> { x = windowWidth - width - 10 + offsetX; y = windowHeight - height - 10 + offsetY; }
            default -> { x = (windowWidth - width) / 2 + offsetX; y = (windowHeight - height) / 2 + offsetY; }
        }
        return new int[]{Math.max(0, Math.min(windowWidth - width, x)), Math.max(0, Math.min(windowHeight - height, y))};
    }

    private int getHealthColor(float percent) {
        if (percent <= 0.25f) return 0xFFFF3333;
        else if (percent <= 0.5f) return 0xFFFFAA00;
        return 0xFF33FF33;
    }
}