package com.strongspy.strclient.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

    @Shadow @Final private MinecraftClient client;

    @Inject(method = "drawBlockOutline", at = @At("HEAD"), cancellable = true)
    private void onDrawBlockOutline(MatrixStack matrices, VertexConsumer vertexConsumer, Entity entity,
                                    double cameraX, double cameraY, double cameraZ,
                                    BlockPos pos, BlockState state, CallbackInfo ci) {

        VoxelShape shape = state.getOutlineShape(this.client.world, pos);
        if (shape.isEmpty()) return;

        // 1. 拦截原版黑色细线
        ci.cancel();

        // 2. 配置纯白高亮混色状态
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Tessellator tessellator = Tessellator.getInstance();
        matrices.push();
        matrices.translate((double)pos.getX() - cameraX, (double)pos.getY() - cameraY, (double)pos.getZ() - cameraZ);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        // 💡 极高亮设定：RGB 全部拉满到 1.0f 纯白，Alpha 直接拉到 1.0f 达到完全不透明
        float r = 1.0f;
        float g = 1.0f;
        float b = 1.0f;
        float alphaLine = 1.0f;

        // 3. 构建纯白外框边缘线
        BufferBuilder lineBuffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        shape.getBoundingBoxes().forEach(box -> {
            float x1 = (float) box.minX, y1 = (float) box.minY, z1 = (float) box.minZ;
            float x2 = (float) box.maxX, y2 = (float) box.maxY, z2 = (float) box.maxZ;

            // 底部 4 条边
            lineBuffer.vertex(matrix, x1, y1, z1).color(r, g, b, alphaLine); lineBuffer.vertex(matrix, x2, y1, z1).color(r, g, b, alphaLine);
            lineBuffer.vertex(matrix, x2, y1, z1).color(r, g, b, alphaLine); lineBuffer.vertex(matrix, x2, y1, z2).color(r, g, b, alphaLine);
            lineBuffer.vertex(matrix, x2, y1, z2).color(r, g, b, alphaLine); lineBuffer.vertex(matrix, x1, y1, z2).color(r, g, b, alphaLine);
            lineBuffer.vertex(matrix, x1, y1, z2).color(r, g, b, alphaLine); lineBuffer.vertex(matrix, x1, y1, z1).color(r, g, b, alphaLine);

            // 顶部 4 条边
            lineBuffer.vertex(matrix, x1, y2, z1).color(r, g, b, alphaLine); lineBuffer.vertex(matrix, x2, y2, z1).color(r, g, b, alphaLine);
            lineBuffer.vertex(matrix, x2, y2, z1).color(r, g, b, alphaLine); lineBuffer.vertex(matrix, x2, y2, z2).color(r, g, b, alphaLine);
            lineBuffer.vertex(matrix, x2, y2, z2).color(r, g, b, alphaLine); lineBuffer.vertex(matrix, x1, y2, z2).color(r, g, b, alphaLine);
            lineBuffer.vertex(matrix, x1, y2, z2).color(r, g, b, alphaLine); lineBuffer.vertex(matrix, x1, y2, z1).color(r, g, b, alphaLine);

            // 垂直 4 条边
            lineBuffer.vertex(matrix, x1, y1, z1).color(r, g, b, alphaLine); lineBuffer.vertex(matrix, x1, y2, z1).color(r, g, b, alphaLine);
            lineBuffer.vertex(matrix, x2, y1, z1).color(r, g, b, alphaLine); lineBuffer.vertex(matrix, x2, y2, z1).color(r, g, b, alphaLine);
            lineBuffer.vertex(matrix, x2, y1, z2).color(r, g, b, alphaLine); lineBuffer.vertex(matrix, x2, y2, z2).color(r, g, b, alphaLine);
            lineBuffer.vertex(matrix, x1, y1, z2).color(r, g, b, alphaLine); lineBuffer.vertex(matrix, x1, y2, z2).color(r, g, b, alphaLine);
        });

        // 4. 提交绘制并还原栈状态
        BufferRenderer.drawWithGlobalProgram(lineBuffer.end());
        matrices.pop();

        RenderSystem.disableBlend();
    }
}