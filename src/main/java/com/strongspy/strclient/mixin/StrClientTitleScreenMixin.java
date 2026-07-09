package com.strongspy.strclient.mixin;

import com.strongspy.strclient.core.LicenseManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class StrClientTitleScreenMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void renderLicenseExpiryOnTopRight(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.textRenderer == null) return;

        // 1. 获取当前格式化后的到期时间文本 (例如: "2026-xx-xx xx:xx:xx" 或 "永久授权 (无限制)")
        String expireTime = LicenseManager.getExpireTimeStr();

        // 如果未解锁或获取到的文本为空，安全起见显示未激活状态
        String displayText = "§fLicense: " + (expireTime.isEmpty() ? "§fUnactivated" : "§e" + expireTime);

        // 2. 动态计算右上角坐标
        int screenWidth = context.getScaledWindowWidth();
        int textWidth = client.textRenderer.getWidth(displayText);

        // 右上角边距：距离右边框 8 像素，距离顶边框 8 像素
        int x = screenWidth - textWidth - 8;
        int y = 8;

        // 3. 渲染带有文字阴影的授权文本
        context.drawCenteredTextWithShadow(
                client.textRenderer,
                Text.literal(displayText),
                x + (textWidth / 2),
                y,
                0xFFFFFFFF
        );
    }
}