package com.strongspy.strclient.mixin;

// ！！！注意看这里：修正为了 1.21.1 的正确路径 ！！！
import net.minecraft.world.biome.FoliageColors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FoliageColors.class)
public class FoliageColorsMixin {

    // 统一的目标颜色：纯红 0xFF0000
    private static final int TARGET_COLOR = 0xFF6200;

    /**
     * 强行截断并修改白桦树叶的硬编码颜色出口
     */
    @Inject(method = "getBirchColor", at = @At("HEAD"), cancellable = true)
    private static void onGetBirchColor(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(TARGET_COLOR);
    }

    /**
     * 强行截断并修改云杉树叶的硬编码颜色出口
     */
    @Inject(method = "getSpruceColor", at = @At("HEAD"), cancellable = true)
    private static void onGetSpruceColor(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(TARGET_COLOR);
    }

    /**
     * 强行截断并修改默认树叶的硬编码颜色出口
     */
    @Inject(method = "getDefaultColor", at = @At("HEAD"), cancellable = true)
    private static void onGetDefaultColor(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(TARGET_COLOR);
    }

    /**
     * 强行截断并修改红树林树叶的硬编码颜色出口（1.21.1 独有硬编码）
     */
    @Inject(method = "getMangroveColor", at = @At("HEAD"), cancellable = true)
    private static void onGetMangroveColor(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(TARGET_COLOR);
    }
}