package com.strongspy.strclient.mixin;

import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BiomeColors.class)
public class TotalColorTransformerMixin {

    // 1. 草方块和各种草丛的专属颜色：例如亮绿色 0x00FF00
    private static final int GRASS_COLOR = 0x71C439;

    // 2. 树叶（普通树叶，如橡木、丛林）的专属颜色：例如纯红色 0xFF0000
    private static final int LEAVES_COLOR = 0xFF9900;

    // 3. 水体的专属颜色：例如亮黄色 0xFFFF00
    private static final int WATER_COLOR = 0x00B7FF;

    /**
     * 拦截草方块、草丛的生物群系颜色出口
     */
    @Inject(method = "getGrassColor", at = @At("HEAD"), cancellable = true)
    private static void onGetGrassColor(BlockRenderView world, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        // 返回草方块专属颜色
        cir.setReturnValue(GRASS_COLOR);
    }

    /**
     * 拦截大部分普通树叶的生物群系颜色出口（橡木、丛林、金合欢等）
     */
    @Inject(method = "getFoliageColor", at = @At("HEAD"), cancellable = true)
    private static void onGetFoliageColor(BlockRenderView world, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        // 返回普通树叶专属颜色
        cir.setReturnValue(LEAVES_COLOR);
    }

    /**
     * 拦截水体的颜色出口
     */
    @Inject(method = "getWaterColor", at = @At("HEAD"), cancellable = true)
    private static void onGetWaterColor(BlockRenderView world, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        // 返回水体专属颜色
        cir.setReturnValue(WATER_COLOR);
    }
}