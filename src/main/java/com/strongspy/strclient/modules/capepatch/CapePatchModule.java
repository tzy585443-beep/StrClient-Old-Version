package com.strongspy.strclient.modules.capepatch;

import com.strongspy.strclient.core.AbstractModule;
import com.strongspy.strclient.core.ModuleSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public class CapePatchModule extends AbstractModule {

    public static volatile Identifier CAPE_TEXTURE = null;
    public static volatile boolean ACTIVE = false;

    // cape name → loaded Identifier (null if not loaded yet)
    private final Map<String, Identifier> loadedTextures = new LinkedHashMap<>();

    // Which capes are currently "enabled" (checked on)
    private final Set<String> enabledCapes = new LinkedHashSet<>();

    // All available capes scanned from the JAR
    private final List<String> availableCapes = new ArrayList<>();

    private final Random random = new Random();

    // JAR 内的资源路径
    public static final String INTERNAL_CAPE_RES = "assets/strclient/capes";

    public CapePatchModule() {
        super("capepatch", "Cape Patch", Category.VISUAL,
                "Wear custom capes. Enable multiple to pick one randomly each session. " +
                        "Put PNGs inside the JAR at assets/strclient/capes/");
    }

    @Override
    public void onEnable(MinecraftClient client) {
        scanAndLoad(client);
        pickCape();
    }

    @Override
    public void onDisable(MinecraftClient client) {
        ACTIVE = false;
        CAPE_TEXTURE = null;
    }

    // ── Scanning Resources from JAR or Classpath ──────────────────────

    public void scanAndLoad(MinecraftClient client) {
        availableCapes.clear();
        loadedTextures.clear();

        URL url = CapePatchModule.class.getClassLoader().getResource(INTERNAL_CAPE_RES);
        if (url == null) {
            enabledCapes.clear();
            return;
        }

        try {
            String protocol = url.getProtocol();
            List<String> names = new ArrayList<>();

            if ("file".equals(protocol)) {
                // 开发环境 (IDE) - 直接读取文件系统中的类路径资源
                Path path = Paths.get(url.toURI());
                if (Files.exists(path)) {
                    try (Stream<Path> files = Files.list(path)) {
                        files.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png"))
                                .map(p -> p.getFileName().toString().replace(".png", ""))
                                .forEach(names::add);
                    }
                }
            } else if ("jar".equals(protocol)) {
                // 正式环境 - 扫描 JAR 压缩包内部的 Entry
                String jarPath = url.getPath().substring(5, url.getPath().indexOf("!"));
                try (JarFile jar = new JarFile(URLDecoder.decode(jarPath, StandardCharsets.UTF_8))) {
                    Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        String entryName = entry.getName();

                        // 匹配 assets/strclient/capes/ 文件夹下的 .png 文件
                        if (entryName.startsWith(INTERNAL_CAPE_RES + "/") && entryName.toLowerCase().endsWith(".png")) {
                            String filename = entryName.substring((INTERNAL_CAPE_RES + "/").length());
                            // 确保不包含子目录的文件
                            if (!filename.contains("/") && !filename.isEmpty()) {
                                names.add(filename.replace(".png", ""));
                            }
                        }
                    }
                }
            }

            // 排序并写入可用的披风列表
            Collections.sort(names);
            for (String name : names) {
                availableCapes.add(name);
                loadedTextures.put(name, null); // 保持懒加载
            }

        } catch (Exception e) {
            System.err.println("[StrClient] Failed to scan internal capes: " + e.getMessage());
        }

        // 过滤掉不再存在于包内的已启用历史记录
        enabledCapes.retainAll(availableCapes);
    }

    // ── Enable/disable individual capes ──────────────────────────────

    public void toggleCape(MinecraftClient client, String name) {
        if (enabledCapes.contains(name)) {
            enabledCapes.remove(name);
        } else {
            enabledCapes.add(name);
        }
        if (isEnabled()) pickCape();
    }

    public boolean isCapeEnabled(String name) {
        return enabledCapes.contains(name);
    }

    public List<String> getAvailableCapes() { return availableCapes; }

    // ── Pick and activate a cape ──────────────────────────────────────

    public void pickCape() {
        List<String> active = new ArrayList<>(enabledCapes);
        if (active.isEmpty()) {
            if (!availableCapes.isEmpty()) active.add(availableCapes.get(0));
            else { ACTIVE = false; CAPE_TEXTURE = null; return; }
        }

        String chosen = active.get(random.nextInt(active.size()));
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        ensureLoaded(client, chosen, id -> {
            CAPE_TEXTURE = id;
            ACTIVE = true;
        });
    }

    // ── Lazy texture loading from Resource Stream ─────────────────────

    private void ensureLoaded(MinecraftClient client, String name, java.util.function.Consumer<Identifier> callback) {
        Identifier existing = loadedTextures.get(name);
        if (existing != null) { callback.accept(existing); return; }

        String resourcePath = INTERNAL_CAPE_RES + "/" + name + ".png";
        try (InputStream is = CapePatchModule.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new java.io.FileNotFoundException("Resource missing inside JAR: " + resourcePath);
            }

            NativeImage img = NativeImage.read(is);
            NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
            Identifier id = Identifier.of("strclient", "cape_" + name.toLowerCase()
                    .replaceAll("[^a-z0-9_.-]", "_"));

            client.execute(() -> {
                client.getTextureManager().registerTexture(id, tex);
                loadedTextures.put(name, id);
                callback.accept(id);
            });
        } catch (Exception e) {
            System.err.println("[StrClient] Failed to load internal cape '" + name + "': " + e.getMessage());
            ACTIVE = false;
        }
    }
}