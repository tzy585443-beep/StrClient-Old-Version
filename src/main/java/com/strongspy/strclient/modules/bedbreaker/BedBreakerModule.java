package com.strongspy.strclient.modules.bedbreaker;

import com.strongspy.strclient.core.AbstractModule;
import com.strongspy.strclient.core.ModuleSetting;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.*;
import java.util.stream.Collectors;

public class BedBreakerModule extends AbstractModule {

    private BlockPos currentTarget = null;
    private long lastBreakTime = 0;
    private List<BlockPos> allBeds = new ArrayList<>();
    private List<BlockPos> espBeds = new ArrayList<>();
    private int scanCooldown = 0;
    private static final int SCAN_INTERVAL_TICKS = 20;

    public BedBreakerModule() {
        super("bedbreaker", "Bed Breaker", Category.COMBAT,
                "Automatically breaks beds within range, with ESP.");
        WorldRenderEvents.AFTER_TRANSLUCENT.register(this::onRender);
    }

    @Override
    protected void registerSettings() {
        registerSetting(ModuleSetting.ofInt("range", "Break Range (blocks)",
                "Search radius for breaking beds", 5, 1, 15));
        registerSetting(ModuleSetting.ofInt("espRange", "ESP Range (blocks)",
                "Search radius for ESP highlighting", 16, 4, 50));
        registerSetting(ModuleSetting.ofInt("delay", "Break Delay (ticks)",
                "Delay between breaking each bed", 2, 0, 10));

        // ESP settings
        registerSetting(ModuleSetting.ofBoolean("showESP", "Show ESP",
                "Highlight beds with colored outline and fill", true));
        registerSetting(ModuleSetting.ofInt("bedR", "Bed R", "", 255, 0, 255));
        registerSetting(ModuleSetting.ofInt("bedG", "Bed G", "", 0, 0, 255));
        registerSetting(ModuleSetting.ofInt("bedB", "Bed B", "", 0, 0, 255));
        registerSetting(ModuleSetting.ofInt("alpha", "Fill Alpha", "", 40, 0, 255));
    }

    @Override
    public void onTick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) return;

        scanCooldown--;
        if (scanCooldown <= 0) {
            scanBeds(client);
            scanCooldown = SCAN_INTERVAL_TICKS;
        }

        // 破坏逻辑
        int breakRange = getInt("range");
        int delay = getInt("delay");

        if (currentTarget != null) {
            BlockState state = client.world.getBlockState(currentTarget);
            if (!(state.getBlock() instanceof BedBlock)) {
                currentTarget = null;
                return;
            }
            if (System.currentTimeMillis() - lastBreakTime >= delay * 50L) {
                client.interactionManager.updateBlockBreakingProgress(currentTarget, net.minecraft.util.math.Direction.UP);
                lastBreakTime = System.currentTimeMillis();
            }
            return;
        }

        // 筛选可用于破坏的床（在 breakRange 内）
        BlockPos center = player.getBlockPos();
        List<BlockPos> breakable = allBeds.stream()
                .filter(p -> p.getSquaredDistance(center) <= breakRange * breakRange)
                .sorted(Comparator.comparingDouble(p -> p.getSquaredDistance(center)))
                .collect(Collectors.toList());

        if (!breakable.isEmpty()) {
            currentTarget = breakable.get(0);
            lastBreakTime = System.currentTimeMillis() - (delay * 50L);
        }
    }

    private void scanBeds(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        int maxRange = Math.max(getInt("range"), getInt("espRange"));
        BlockPos center = player.getBlockPos();
        List<BlockPos> beds = new ArrayList<>();
        for (int x = -maxRange; x <= maxRange; x++) {
            for (int y = -maxRange; y <= maxRange; y++) {
                for (int z = -maxRange; z <= maxRange; z++) {
                    BlockPos pos = center.add(x, y, z);
                    BlockState state = client.world.getBlockState(pos);
                    if (state.getBlock() instanceof BedBlock) {
                        beds.add(pos);
                    }
                }
            }
        }
        this.allBeds = beds;

        // 筛选用于 ESP 的床（在 espRange 内）
        int espRange = getInt("espRange");
        this.espBeds = beds.stream()
                .filter(p -> p.getSquaredDistance(center) <= espRange * espRange)
                .collect(Collectors.toList());
    }

    @Override
    public void onDisable(MinecraftClient client) {
        currentTarget = null;
        allBeds.clear();
        espBeds.clear();
    }

    // ===== ESP Rendering =====
    private void onRender(WorldRenderContext wrc) {
        if (!isEnabled() || !getBoolean("showESP") || espBeds.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        int r = getInt("bedR"), g = getInt("bedG"), b = getInt("bedB"), alpha = getInt("alpha");

        Vec3d cam = wrc.camera().getPos();
        MatrixStack matrices = wrc.matrixStack();
        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        Tessellator tess = Tessellator.getInstance();

        // Filled faces
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (BlockPos p : espBeds) {
            drawFilled(buf, mat, p, r, g, b, alpha);
        }
        BufferRenderer.drawWithGlobalProgram(buf.end());

        // Outlines
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder ol = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        for (BlockPos p : espBeds) {
            drawOutline(ol, mat, p, r, g, b, 255);
        }
        BufferRenderer.drawWithGlobalProgram(ol.end());

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        matrices.pop();
    }

    // Geometry helpers (copied from ChestEsp)
    private void drawFilled(BufferBuilder b, Matrix4f m, BlockPos p,
                            int r, int g, int bl, int a) {
        float x1 = p.getX(), y1 = p.getY(), z1 = p.getZ();
        float x2 = x1+1, y2 = y1+1, z2 = z1+1;
        float fr = r/255f, fg = g/255f, fb = bl/255f, fa = a/255f;
        b.vertex(m,x1,y1,z1).color(fr,fg,fb,fa);
        b.vertex(m,x2,y1,z1).color(fr,fg,fb,fa);
        b.vertex(m,x2,y1,z2).color(fr,fg,fb,fa);
        b.vertex(m,x1,y1,z2).color(fr,fg,fb,fa);
        b.vertex(m,x1,y2,z1).color(fr,fg,fb,fa);
        b.vertex(m,x1,y2,z2).color(fr,fg,fb,fa);
        b.vertex(m,x2,y2,z2).color(fr,fg,fb,fa);
        b.vertex(m,x2,y2,z1).color(fr,fg,fb,fa);
        b.vertex(m,x1,y1,z1).color(fr,fg,fb,fa);
        b.vertex(m,x1,y2,z1).color(fr,fg,fb,fa);
        b.vertex(m,x2,y2,z1).color(fr,fg,fb,fa);
        b.vertex(m,x2,y1,z1).color(fr,fg,fb,fa);
        b.vertex(m,x1,y1,z2).color(fr,fg,fb,fa);
        b.vertex(m,x2,y1,z2).color(fr,fg,fb,fa);
        b.vertex(m,x2,y2,z2).color(fr,fg,fb,fa);
        b.vertex(m,x1,y2,z2).color(fr,fg,fb,fa);
        b.vertex(m,x1,y1,z1).color(fr,fg,fb,fa);
        b.vertex(m,x1,y1,z2).color(fr,fg,fb,fa);
        b.vertex(m,x1,y2,z2).color(fr,fg,fb,fa);
        b.vertex(m,x1,y2,z1).color(fr,fg,fb,fa);
        b.vertex(m,x2,y1,z1).color(fr,fg,fb,fa);
        b.vertex(m,x2,y2,z1).color(fr,fg,fb,fa);
        b.vertex(m,x2,y2,z2).color(fr,fg,fb,fa);
        b.vertex(m,x2,y1,z2).color(fr,fg,fb,fa);
    }

    private void drawOutline(BufferBuilder b, Matrix4f m, BlockPos p,
                             int r, int g, int bl, int a) {
        float x1 = p.getX(), y1 = p.getY(), z1 = p.getZ();
        float x2 = x1+1, y2 = y1+1, z2 = z1+1;
        float fr = r/255f, fg = g/255f, fb = bl/255f, fa = a/255f;
        ln(b,m,x1,y1,z1,x2,y1,z1,fr,fg,fb,fa);
        ln(b,m,x2,y1,z1,x2,y1,z2,fr,fg,fb,fa);
        ln(b,m,x2,y1,z2,x1,y1,z2,fr,fg,fb,fa);
        ln(b,m,x1,y1,z2,x1,y1,z1,fr,fg,fb,fa);
        ln(b,m,x1,y2,z1,x2,y2,z1,fr,fg,fb,fa);
        ln(b,m,x2,y2,z1,x2,y2,z2,fr,fg,fb,fa);
        ln(b,m,x2,y2,z2,x1,y2,z2,fr,fg,fb,fa);
        ln(b,m,x1,y2,z2,x1,y2,z1,fr,fg,fb,fa);
        ln(b,m,x1,y1,z1,x1,y2,z1,fr,fg,fb,fa);
        ln(b,m,x2,y1,z1,x2,y2,z1,fr,fg,fb,fa);
        ln(b,m,x2,y1,z2,x2,y2,z2,fr,fg,fb,fa);
        ln(b,m,x1,y1,z2,x1,y2,z2,fr,fg,fb,fa);
    }

    private void ln(BufferBuilder b, Matrix4f m,
                    float x1,float y1,float z1,float x2,float y2,float z2,
                    float r,float g,float fb,float a) {
        b.vertex(m,x1,y1,z1).color(r,g,fb,a);
        b.vertex(m,x2,y2,z2).color(r,g,fb,a);
    }
}