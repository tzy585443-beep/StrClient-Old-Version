package com.strongspy.strclient.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.render.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.joml.Matrix4f;

public class CustomLoadingScreen extends Screen {

    private static final Identifier CUSTOM_BACKGROUND = Identifier.of("strclient", "textures/gui/brand_bg.png");
    private long startTime = -1L;

    public CustomLoadingScreen() {
        super(Text.literal("Loading"));
    }

    @Override
    public boolean shouldPause() { return true; }
    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long currentTime = Util.getMeasuringTimeMs();
        if (this.startTime == -1L) {
            this.startTime = currentTime;
        }

        long elapsed = currentTime - this.startTime;

        if (elapsed >= 5000L) {
            if (this.client != null) {
                this.client.setScreen(new TitleScreen(true));
            }
            return;
        }

        float alpha = 1.0F;
        if (elapsed > 4500L) {
            alpha = 1.0F - (float)(elapsed - 4500L) / 500.0F;
        }

        int width = this.width;
        int height = this.height;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);

        // 1. 绘制背景
        context.drawTexture(CUSTOM_BACKGROUND, 0, 0, 0, 0, width, height, width, height);
        // 强制刷新缓冲区，确保背景垫在最底下
        context.draw();

        int alphaBits = (int)(alpha * 255.0F) & 0xFF;
        int centerX = width / 2;
        int centerY = height / 2;

        // 2. 🌀 绘制旋转圆圈
        float radius = 24.0F;
        float thickness = 3.0F;
        drawSmoothSpinner(context, centerX, centerY, radius, thickness, 255, 255, 255, alphaBits);

        int whiteColor = (alphaBits << 24) | 0xFFFFFF;
        int grayColor = (alphaBits << 24) | 0xFFFFFF; // 稍微调暗了一点文本，更有质感
        int bgBarColor = (alphaBits << 24) | 0x444444; // 进度条底槽颜色

        if (this.client != null) {
            // 3. ✍️ 绘制标题
            context.drawCenteredTextWithShadow(this.client.textRenderer, "StrClient", centerX, centerY + 40, whiteColor);

            // 4. ✍️ 绘制百分比
            double progressPercent = Math.min(100.0, (elapsed / 5000.0) * 100.0);
            long snappedProgress = Math.round(progressPercent * 10) / 10 * 10;
            int displayPercent = (int) (snappedProgress / 10);
            String loadingText = "Loading... " + displayPercent + "%";
            context.drawCenteredTextWithShadow(this.client.textRenderer, loadingText, centerX, centerY + 54, grayColor);

            // 5. ➖ 绘制细长进度条 (🎯 修复点：去掉 RenderLayer，直接用绝对 Z 坐标的 fill 方法)
            int barWidth = 120;
            int barHeight = 2;
            int barX = centerX - (barWidth / 2);
            int barY = centerY + 70;
            int currentBarProgressWidth = (int) (barWidth * (progressPercent / 100.0));

            // 绘制底槽
            context.fill(barX, barY, barX + barWidth, barY + barHeight, bgBarColor);
            // 绘制当前进度
            context.fill(barX, barY, barX + currentBarProgressWidth, barY + barHeight, whiteColor);
        }

        // 确保后续文本和进度条被立刻画出来
        context.draw();

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    /**
     * 💎 动态几何圆圈渲染 (🎯 修复点：将 Z 轴降低至 0，加入强制混色开启)
     */
    private void drawSmoothSpinner(DrawContext context, float cx, float cy, float radius, float thickness, int r, int g, int b, int alpha) {
        long time = Util.getMeasuringTimeMs();
        double angleOffset = (time % 1200) / 1200.0 * Math.PI * 2;
        double arcLength = Math.PI * 2 * 0.70;
        int segments = 120;

        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();

        // 强制开启混色和取消剔除，保证绝对可见
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferBuilder = tessellator.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);

        for (int i = 0; i <= segments; i++) {
            double currentArcAngle = (i / (double) segments) * arcLength;
            double finalAngle = angleOffset + currentArcAngle;

            float cos = (float) Math.cos(finalAngle);
            float sin = (float) Math.sin(finalAngle);

            float innerX = cx + cos * (radius - thickness / 2f);
            float innerY = cy + sin * (radius - thickness / 2f);
            float outerX = cx + cos * (radius + thickness / 2f);
            float outerY = cy + sin * (radius + thickness / 2f);

            int segmentAlpha = (int) (alpha * (i / (float) segments));

            // 🎯 核心修复：把 400.0F 降回 0.0F，防止 1.21.1 的 GUI 相机把这部分图形“切”掉
            bufferBuilder.vertex(matrix, innerX, innerY, 0.0F).color(r, g, b, segmentAlpha);
            bufferBuilder.vertex(matrix, outerX, outerY, 0.0F).color(r, g, b, segmentAlpha);
        }

        BuiltBuffer builtBuffer = bufferBuilder.end();
        if (builtBuffer != null) {
            BufferRenderer.drawWithGlobalProgram(builtBuffer);
        }

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }
}