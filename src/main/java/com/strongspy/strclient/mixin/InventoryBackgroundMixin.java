package com.strongspy.strclient.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class InventoryBackgroundMixin {

    /**
     * 💡 核心修正：1.21.1 的容器界面直接调用此方法来绘制全屏黑色暗化层
     */
    @Inject(
            method = "renderInGameBackground(Lnet/minecraft/client/gui/DrawContext;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void removeInventoryDarkOverlay(DrawContext context, CallbackInfo ci) {
        // 🔒 精准过滤：如果是物品栏或箱子等容器界面，直接取消渲染
        if ((Object) this instanceof HandledScreen) {
            ci.cancel(); // 掐断原版的黑色半透明渲染，使其变回 100% 全透明
        }
    }
}