package com.strongspy.strclient.core;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Stores draggable HUD element positions (x, y as fractions of screen size
 * isn't used here — we store raw pixel anchors at a reference resolution
 * and let each element clamp itself on screen at render time).
 *
 * Saved to .minecraft/config/strclient/hud_positions.json
 */
public class HudPosition {

    public record Pos(int x, int y) {}

    private static final Path FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("strclient").resolve("hud_positions.json");

    private static final Map<String, Pos> positions = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void load() {
        if (!Files.exists(FILE)) return;
        try {
            String json = Files.readString(FILE);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            for (String key : root.keySet()) {
                JsonObject p = root.getAsJsonObject(key);
                positions.put(key, new Pos(p.get("x").getAsInt(), p.get("y").getAsInt()));
            }
        } catch (Exception ignored) {}
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            JsonObject root = new JsonObject();
            for (var entry : positions.entrySet()) {
                JsonObject p = new JsonObject();
                p.addProperty("x", entry.getValue().x());
                p.addProperty("y", entry.getValue().y());
                root.add(entry.getKey(), p);
            }
            Files.writeString(FILE, GSON.toJson(root));
        } catch (IOException ignored) {}
    }

    /** Returns the stored position, or the given default if none is saved yet. */
    public static Pos get(String id, int defaultX, int defaultY) {
        return positions.getOrDefault(id, new Pos(defaultX, defaultY));
    }

    public static void set(String id, int x, int y) {
        positions.put(id, new Pos(x, y));
    }
    public static boolean has(String id) {
        return positions.containsKey(id);
    }
}