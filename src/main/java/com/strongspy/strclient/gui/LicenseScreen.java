package com.strongspy.strclient.gui;

import com.strongspy.strclient.core.LicenseManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class LicenseScreen extends Screen {

    private static final Identifier BG_TEXTURE = Identifier.of("strclient", "textures/gui/license_bg.png");

    private TextFieldWidget inputField;
    private String feedback = "";
    private String expireInfo = "";
    private final Runnable onSuccess;

    public LicenseScreen(Runnable onSuccess) {
        super(Text.literal("StrClient — License"));
        this.onSuccess = onSuccess;
    }

    @Override
    public boolean shouldPause() { return true; }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    protected void init() {
        int fieldW = 220;
        int fieldX = (this.width - fieldW) / 2;
        int fieldY = this.height / 2 - 10;

        inputField = new TextFieldWidget(
                this.textRenderer,
                fieldX, fieldY, fieldW, 20,
                Text.literal("Enter license key"));
        inputField.setMaxLength(29);
        inputField.setPlaceholder(Text.literal("Enter your 5x5 dynamic key..."));

        this.addDrawableChild(inputField);
        inputField.setFocused(true);

        // Display pre-existing authorization details if already unlocked
        if (LicenseManager.isUnlocked()) {
            expireInfo = "§7Authorized Device: §a" + LicenseManager.getExpireTimeStr();
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.drawTexture(BG_TEXTURE, 0, 0, 0, 0, this.width, this.height, this.width, this.height);
        ctx.fill(0, 0, this.width, this.height, 0x60000000);

        super.render(ctx, mouseX, mouseY, delta);

        int cx = this.width / 2;
        int cy = this.height / 2;

        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("§l§fStrClient §f— Dynamic Authentication"), cx, cy - 40, 0xFFFFFFFF);

        // 1. Render operation status feedback
        if (!feedback.isEmpty()) {
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal(feedback), cx, cy + 34, 0xFFFFFFFF);
        }

        // 2. Render localized license expiration info permanently at the bottom
        if (!expireInfo.isEmpty()) {
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal(expireInfo), cx, cy + 54, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            String input = inputField.getText().trim();
            if (input.isEmpty()) return true;

            if (LicenseManager.submit(input)) {
                String timeStr = LicenseManager.getExpireTimeStr();
                feedback = "§a✓ Verification successful! Loading game...";
                expireInfo = "§7[Expiration Time]: §e" + timeStr;

                inputField.setEditable(false); // Lock the input field upon success

                new Thread(() -> {
                    try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                    if (client != null) client.execute(onSuccess);
                }).start();
            } else {
                feedback = "§c✗ Invalid or Expired key (10 min expiry)! Try again.";
                inputField.setText("");
            }
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}