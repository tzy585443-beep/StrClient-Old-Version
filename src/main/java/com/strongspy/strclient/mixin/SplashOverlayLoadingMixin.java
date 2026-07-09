package com.strongspy.strclient.mixin;

import com.strongspy.strclient.core.LicenseManager;
import com.strongspy.strclient.gui.CustomLoadingScreen;
import com.strongspy.strclient.gui.LicenseScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.client.texture.ResourceTexture;
import net.minecraft.resource.ResourceReload;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.function.Consumer;

@Mixin(SplashOverlay.class)
public class SplashOverlayLoadingMixin {

    @Unique
    private static final Identifier CUSTOM_BACKGROUND = Identifier.of("strclient", "textures/gui/brand_bg.png");

    // 💡 新增独占静态全局变量：用于记录客户端自启动以来是否已经完成过首次验证拦截
    @Unique
    private static boolean hasInitialized = false;

    @Shadow @Final private MinecraftClient client;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void preloadTexture(MinecraftClient client, ResourceReload monitor, Consumer<Optional<Throwable>> exceptionHandler, boolean reloading, CallbackInfo ci) {
        try {
            client.getTextureManager().registerTexture(CUSTOM_BACKGROUND, new ResourceTexture(CUSTOM_BACKGROUND));
        } catch (Exception ignored) {}
    }

    /**
     * 🏁 【核心调度链】：当 Mojang 进度条加载到 100% 准备关闭大红屏（Overlay）的瞬间进行精准拦截
     */
    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/MinecraftClient;setOverlay(Lnet/minecraft/client/gui/screen/Overlay;)V"
            )
    )
    private void onMojangLoadComplete(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        // 🔒 关键核心修复：如果游戏已经启动过并初始化完成了（比如切换语言、换材质包触发的重载），直接放行，不再弹出界面
        if (hasInitialized) {
            return;
        }

        // 标记为已初始化，确保这段逻辑在游戏生命周期内有且仅执行一次
        hasInitialized = true;

        // 1. 静默检查本地的加密设备锁证书文件是否有效
        if (!LicenseManager.isUnlocked()) {
            LicenseManager.checkSaved(); //
        }

        // 2. 根据当前的解锁状态，严丝合缝地规划接下来的客户端画面
        if (!LicenseManager.isUnlocked()) {
            // 🛡️ 【路线 A — 未授权】：强制将下一个画面设置为卡密验证界面
            this.client.setScreen(new LicenseScreen(() -> {
                // 当玩家在界面里成功输入正确卡密后，触发此回调：
                // 验证成功后，顺理成章、无缝衔接切入你的 5 秒华丽圆圈旋转加载动画！
                this.client.setScreen(new CustomLoadingScreen()); //
            }));
        } else {
            // 🚀 【路线 B — 已授权/自动登录】：本地凭证完好，直接无缝切入你的 5 秒华丽圆圈旋转加载动画！
            this.client.setScreen(new CustomLoadingScreen()); //
        }
    }
}