package com.strongspy.strclient.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenBackgroundMixin {

    // 固定的资源路径：放入资源包对应的路径下（textures/background.png）即可自由替换
    private static final Identifier BG = Identifier.of("strclient", "textures/background.png");

    @Inject(method = "renderPanoramaBackground", at = @At("HEAD"), cancellable = true)
    private void replaceBackground(DrawContext ctx, float delta, CallbackInfo ci) {
        ci.cancel(); // 彻底跳过原版旋转方块全景图的计算和渲染

        MinecraftClient client = MinecraftClient.getInstance();
        int w = client.getWindow().getScaledWidth();
        int h = client.getWindow().getScaledHeight();

        // 确保开启混合，防止动态更换的图片带有透明或特殊通道时变黑
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 绑定并拉伸绘制全屏大图
        ctx.drawTexture(BG, 0, 0, 0, 0, w, h, w, h);

        RenderSystem.disableBlend();
    }
}