package com.strongspy.strclient.modules.chestesp;

import com.strongspy.strclient.core.AbstractModule;
import com.strongspy.strclient.core.ModuleSetting;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;

import java.util.*;

public class ChestEspModule extends AbstractModule {

    // chunk -> positions of that type in that chunk
    private final Map<ChunkPos, List<BlockPos>> chestCache   = new HashMap<>();
    private final Map<ChunkPos, List<BlockPos>> spawnerCache = new HashMap<>();

    // Flattened render lists, only rebuilt when the scan actually changes something
    private List<BlockPos> flatChests   = Collections.emptyList();
    private List<BlockPos> flatSpawners = Collections.emptyList();
    private boolean listsDirty = true;

    private int scanCooldown = 0;
    private static final int SCAN_INTERVAL_TICKS = 20; // rescan once per second

    private static final double MAX_RENDER_DISTANCE = 256.0;
    private static final double MAX_RENDER_DISTANCE_SQ = MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE;

    public ChestEspModule() {
        super("chestesp", "Chest ESP", Category.VISUAL,
                "Highlights chests, barrels, shulker boxes and spawners through walls");
    }

    @Override
    protected void registerSettings() {
        registerSetting(ModuleSetting.ofBoolean("chests",      "Chests",        "", true));
        registerSetting(ModuleSetting.ofBoolean("barrels",     "Barrels",       "", true));
        registerSetting(ModuleSetting.ofBoolean("shulkers",    "Shulker Boxes", "", true));
        registerSetting(ModuleSetting.ofBoolean("spawners",    "Spawners",      "", true));
        registerSetting(ModuleSetting.ofBoolean("enderchests", "Ender Chests",  "", false));

        registerSetting(ModuleSetting.ofInt("chestR",   "Chest R",   "", 255, 0, 255));
        registerSetting(ModuleSetting.ofInt("chestG",   "Chest G",   "", 165, 0, 255));
        registerSetting(ModuleSetting.ofInt("chestB",   "Chest B",   "",   0, 0, 255));

        registerSetting(ModuleSetting.ofInt("spawnerR", "Spawner R", "", 255, 0, 255));
        registerSetting(ModuleSetting.ofInt("spawnerG", "Spawner G", "",   0, 0, 255));
        registerSetting(ModuleSetting.ofInt("spawnerB", "Spawner B", "",   0, 0, 255));

        registerSetting(ModuleSetting.ofInt("alpha", "Fill Alpha", "", 40, 0, 255));
    }

    @Override
    public void onEnable(MinecraftClient client) {
        chestCache.clear();
        spawnerCache.clear();
        flatChests   = Collections.emptyList();
        flatSpawners = Collections.emptyList();
        listsDirty   = true;
        scanCooldown = 0; // force an immediate scan on next tick

        WorldRenderEvents.AFTER_TRANSLUCENT.register(this::onRender);
    }

    @Override
    public void onDisable(MinecraftClient client) {
        chestCache.clear();
        spawnerCache.clear();
        flatChests   = Collections.emptyList();
        flatSpawners = Collections.emptyList();
    }

    /**
     * Called every client tick via AbstractModule's normal tick hook —
     * throttled internally so the actual chunk scan only runs once per
     * SCAN_INTERVAL_TICKS, not every tick.
     */
    @Override
    public void onTick(MinecraftClient client) {
        if (client.world == null || client.player == null) return;

        scanCooldown--;
        if (scanCooldown > 0) return;
        scanCooldown = SCAN_INTERVAL_TICKS;

        rescanNearbyChunks(client);
    }

    // ── Chunk scanning ───────────────────────────────────────────────

    private void rescanNearbyChunks(MinecraftClient client) {
        ChunkPos center = new ChunkPos(client.player.getBlockPos());
        int renderDist = client.options.getViewDistance().getValue();

        Set<ChunkPos> stillLoaded = new HashSet<>();
        boolean changed = false;

        for (int cx = center.x - renderDist; cx <= center.x + renderDist; cx++) {
            for (int cz = center.z - renderDist; cz <= center.z + renderDist; cz++) {
                ChunkPos cp = new ChunkPos(cx, cz);
                WorldChunk chunk = client.world.getChunkManager().getWorldChunk(cx, cz);
                if (chunk == null) continue;

                stillLoaded.add(cp);
                changed |= scanChunk(chunk, cp);
            }
        }

        // Drop cache entries for chunks that are no longer loaded
        changed |= chestCache.keySet().retainAll(stillLoaded);
        changed |= spawnerCache.keySet().retainAll(stillLoaded);

        if (changed) listsDirty = true;
    }

    /** Scans a single chunk's block entities and updates the cache. Returns true if anything changed. */
    private boolean scanChunk(WorldChunk chunk, ChunkPos cp) {
        boolean doChests   = getBoolean("chests");
        boolean doBarrels  = getBoolean("barrels");
        boolean doShulkers = getBoolean("shulkers");
        boolean doSpawners = getBoolean("spawners");
        boolean doEnder    = getBoolean("enderchests");

        List<BlockPos> chests   = null;
        List<BlockPos> spawners = null;

        // Only iterate block entities — much faster than scanning every block in the chunk
        for (Map.Entry<BlockPos, net.minecraft.block.entity.BlockEntity> entry
                : chunk.getBlockEntities().entrySet()) {

            BlockPos pos = entry.getKey();
            Block block = chunk.getBlockState(pos).getBlock();

            if (doChests && (block instanceof ChestBlock || block instanceof TrappedChestBlock)) {
                if (chests == null) chests = new ArrayList<>(4);
                chests.add(pos.toImmutable());
            } else if (doBarrels && block instanceof BarrelBlock) {
                if (chests == null) chests = new ArrayList<>(4);
                chests.add(pos.toImmutable());
            } else if (doShulkers && block instanceof ShulkerBoxBlock) {
                if (chests == null) chests = new ArrayList<>(4);
                chests.add(pos.toImmutable());
            } else if (doEnder && block instanceof EnderChestBlock) {
                if (chests == null) chests = new ArrayList<>(4);
                chests.add(pos.toImmutable());
            } else if (doSpawners && block instanceof SpawnerBlock) {
                if (spawners == null) spawners = new ArrayList<>(2);
                spawners.add(pos.toImmutable());
            }
        }

        boolean changed = false;

        if (chests != null) { changed |= !chests.equals(chestCache.put(cp, chests)); }
        else                { changed |= chestCache.remove(cp) != null; }

        if (spawners != null) { changed |= !spawners.equals(spawnerCache.put(cp, spawners)); }
        else                  { changed |= spawnerCache.remove(cp) != null; }

        return changed;
    }

    /** Flattens the per-chunk caches into render-ready lists, filtered by distance. Only runs when dirty. */
    private void refreshFlatLists(Vec3d playerPos) {
        List<BlockPos> chests = new ArrayList<>();
        for (List<BlockPos> l : chestCache.values()) chests.addAll(l);

        List<BlockPos> spawners = new ArrayList<>();
        for (List<BlockPos> l : spawnerCache.values()) spawners.addAll(l);

        chests.removeIf(p -> distSq(p, playerPos) > MAX_RENDER_DISTANCE_SQ);
        spawners.removeIf(p -> distSq(p, playerPos) > MAX_RENDER_DISTANCE_SQ);

        flatChests   = chests;
        flatSpawners = spawners;
        listsDirty   = false;
    }

    private static double distSq(BlockPos pos, Vec3d playerPos) {
        double dx = pos.getX() + 0.5 - playerPos.x;
        double dy = pos.getY() + 0.5 - playerPos.y;
        double dz = pos.getZ() + 0.5 - playerPos.z;
        return dx * dx + dy * dy + dz * dz;
    }

    // ── Render ───────────────────────────────────────────────────────

    private void onRender(WorldRenderContext wrc) {
        if (!isEnabled()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        if (listsDirty) refreshFlatLists(client.player.getPos());

        List<BlockPos> chests   = flatChests;
        List<BlockPos> spawners = flatSpawners;
        if (chests.isEmpty() && spawners.isEmpty()) return;

        int cr = getInt("chestR"),   cg = getInt("chestG"),   cb = getInt("chestB");
        int sr = getInt("spawnerR"), sg = getInt("spawnerG"), sb = getInt("spawnerB");
        int alpha = getInt("alpha");

        Vec3d cam = wrc.camera().getPos();
        MatrixStack matrices = wrc.matrixStack();
        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        Tessellator tess = Tessellator.getInstance();

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (BlockPos p : chests)   drawFilled(buf, mat, p, cr, cg, cb, alpha);
        for (BlockPos p : spawners) drawFilled(buf, mat, p, sr, sg, sb, alpha);
        BufferRenderer.drawWithGlobalProgram(buf.end());

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder ol = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        for (BlockPos p : chests)   drawOutline(ol, mat, p, cr, cg, cb, 255);
        for (BlockPos p : spawners) drawOutline(ol, mat, p, sr, sg, sb, 255);
        BufferRenderer.drawWithGlobalProgram(ol.end());

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        matrices.pop();
    }

    // ── Geometry ─────────────────────────────────────────────────────

    private void drawFilled(BufferBuilder b, Matrix4f m, BlockPos p,
                            int r, int g, int bl, int a) {
        float x1 = p.getX(), y1 = p.getY(), z1 = p.getZ();
        float x2 = x1+1, y2 = y1+1, z2 = z1+1;
        float fr = r/255f, fg = g/255f, fb = bl/255f, fa = a/255f;
        b.vertex(m,x1,y1,z1).color(fr,fg,fb,fa); b.vertex(m,x2,y1,z1).color(fr,fg,fb,fa);
        b.vertex(m,x2,y1,z2).color(fr,fg,fb,fa); b.vertex(m,x1,y1,z2).color(fr,fg,fb,fa);
        b.vertex(m,x1,y2,z1).color(fr,fg,fb,fa); b.vertex(m,x1,y2,z2).color(fr,fg,fb,fa);
        b.vertex(m,x2,y2,z2).color(fr,fg,fb,fa); b.vertex(m,x2,y2,z1).color(fr,fg,fb,fa);
        b.vertex(m,x1,y1,z1).color(fr,fg,fb,fa); b.vertex(m,x1,y2,z1).color(fr,fg,fb,fa);
        b.vertex(m,x2,y2,z1).color(fr,fg,fb,fa); b.vertex(m,x2,y1,z1).color(fr,fg,fb,fa);
        b.vertex(m,x1,y1,z2).color(fr,fg,fb,fa); b.vertex(m,x2,y1,z2).color(fr,fg,fb,fa);
        b.vertex(m,x2,y2,z2).color(fr,fg,fb,fa); b.vertex(m,x1,y2,z2).color(fr,fg,fb,fa);
        b.vertex(m,x1,y1,z1).color(fr,fg,fb,fa); b.vertex(m,x1,y1,z2).color(fr,fg,fb,fa);
        b.vertex(m,x1,y2,z2).color(fr,fg,fb,fa); b.vertex(m,x1,y2,z1).color(fr,fg,fb,fa);
        b.vertex(m,x2,y1,z1).color(fr,fg,fb,fa); b.vertex(m,x2,y2,z1).color(fr,fg,fb,fa);
        b.vertex(m,x2,y2,z2).color(fr,fg,fb,fa); b.vertex(m,x2,y1,z2).color(fr,fg,fb,fa);
    }

    private void drawOutline(BufferBuilder b, Matrix4f m, BlockPos p,
                             int r, int g, int bl, int a) {
        float x1 = p.getX(), y1 = p.getY(), z1 = p.getZ();
        float x2 = x1+1, y2 = y1+1, z2 = z1+1;
        float fr = r/255f, fg = g/255f, fb = bl/255f, fa = a/255f;
        ln(b,m,x1,y1,z1,x2,y1,z1,fr,fg,fb,fa); ln(b,m,x2,y1,z1,x2,y1,z2,fr,fg,fb,fa);
        ln(b,m,x2,y1,z2,x1,y1,z2,fr,fg,fb,fa); ln(b,m,x1,y1,z2,x1,y1,z1,fr,fg,fb,fa);
        ln(b,m,x1,y2,z1,x2,y2,z1,fr,fg,fb,fa); ln(b,m,x2,y2,z1,x2,y2,z2,fr,fg,fb,fa);
        ln(b,m,x2,y2,z2,x1,y2,z2,fr,fg,fb,fa); ln(b,m,x1,y2,z2,x1,y2,z1,fr,fg,fb,fa);
        ln(b,m,x1,y1,z1,x1,y2,z1,fr,fg,fb,fa); ln(b,m,x2,y1,z1,x2,y2,z1,fr,fg,fb,fa);
        ln(b,m,x2,y1,z2,x2,y2,z2,fr,fg,fb,fa); ln(b,m,x1,y1,z2,x1,y2,z2,fr,fg,fb,fa);
    }

    private void ln(BufferBuilder b, Matrix4f m,
                    float x1,float y1,float z1,float x2,float y2,float z2,
                    float r,float g,float fb,float a) {
        b.vertex(m,x1,y1,z1).color(r,g,fb,a);
        b.vertex(m,x2,y2,z2).color(r,g,fb,a);
    }
}