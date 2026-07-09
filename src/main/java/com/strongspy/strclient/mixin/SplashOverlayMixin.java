package com.strongspy.strclient.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.client.texture.ResourceTexture;
import net.minecraft.resource.ResourceReload;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.function.Consumer;

@Mixin(SplashOverlay.class)
public class SplashOverlayMixin {

    @Unique
    private static final Identifier CUSTOM_BACKGROUND = Identifier.of("strclient", "textures/gui/brand_bg.png");

    /**
     * 在构造方法尾部注入，将自定义背景图片强行登记到游戏的纹理管理器中
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void preloadTexture(MinecraftClient client, ResourceReload monitor, Consumer<Optional<Throwable>> exceptionHandler, boolean reloading, CallbackInfo ci) {
        try {
            client.getTextureManager().registerTexture(CUSTOM_BACKGROUND, new ResourceTexture(CUSTOM_BACKGROUND));
        } catch (Exception ignored) {
            // 静默忽略初期可能的异常
        }
    }

    /**
     * 完美的“第一版”原封不动全屏绘制逻辑
     */
    @Inject(
            method = "render",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/gui/screen/SplashOverlay;LOGO:Lnet/minecraft/util/Identifier;",
                    ordinal = 0
            )
    )
    private void drawCustomBackground(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();

        // 确保开启混合模式，防止大图由于特殊通道变黑
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 绘制全屏大图
        context.drawTexture(CUSTOM_BACKGROUND, 0, 0, 0, 0, width, height, width, height);

        RenderSystem.disableBlend();
    }
}