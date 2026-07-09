package com.strongspy.strclient.core;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Profile system — export/import all module states and settings.
 * Profiles are saved as JSON files in .minecraft/config/strclient/profiles/
 */
public class ProfileManager {

    private static final Path PROFILE_DIR = FabricLoader.getInstance()
            .getConfigDir().resolve("strclient").resolve("profiles");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static {
        try { Files.createDirectories(PROFILE_DIR); } catch (IOException ignored) {}
    }

    // ── Export ────────────────────────────────────────────────────────

    /**
     * Exports current module states + settings to a named profile file.
     * File will be saved as profiles/<name>.json
     */
    public static void export(ModuleManager moduleManager, String name) {
        if (name == null || name.isBlank()) name = "profile";
        // Sanitize filename
        name = name.replaceAll("[^a-zA-Z0-9_\\-]", "_");

        JsonObject root = new JsonObject();
        root.addProperty("_version", 1);
        root.addProperty("_name", name);

        JsonObject modules = new JsonObject();
        for (AbstractModule module : moduleManager.getAll()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("enabled", module.isEnabled());

            JsonObject settings = new JsonObject();
            for (ModuleSetting<?> s : module.getSettings().values()) {
                Object val = s.getValue();
                if      (val instanceof Double d)  settings.addProperty(s.getKey(), d);
                else if (val instanceof Boolean b) settings.addProperty(s.getKey(), b);
                else                               settings.addProperty(s.getKey(), val.toString());
            }
            entry.add("settings", settings);
            modules.add(module.getId(), entry);
        }
        root.add("modules", modules);

        Path file = PROFILE_DIR.resolve(name + ".json");
        try {
            Files.writeString(file, GSON.toJson(root));
        } catch (IOException e) {
            System.err.println("[StrClient] Failed to export profile: " + e.getMessage());
        }
    }

    // ── Import ────────────────────────────────────────────────────────

    /**
     * Imports a profile by filename (without .json) and applies it immediately.
     * Returns true on success.
     */
    public static boolean importProfile(ModuleManager moduleManager,
                                        SettingsManager settingsManager,
                                        net.minecraft.client.MinecraftClient client,
                                        String name) {
        Path file = PROFILE_DIR.resolve(name + ".json");
        if (!Files.exists(file)) {
            System.err.println("[StrClient] Profile not found: " + file);
            return false;
        }

        try {
            String json = Files.readString(file);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!root.has("modules")) return false;

            JsonObject modules = root.getAsJsonObject("modules");

            for (String id : modules.keySet()) {
                AbstractModule module = moduleManager.get(id);
                if (module == null) continue;

                JsonObject entry = modules.getAsJsonObject(id);

                // Apply enabled state
                if (entry.has("enabled")) {
                    boolean shouldEnable = entry.get("enabled").getAsBoolean();
                    if (module.isEnabled() != shouldEnable) {
                        module.setEnabled(shouldEnable, client);
                    }
                }

                // Apply settings
                if (entry.has("settings")) {
                    JsonObject settings = entry.getAsJsonObject("settings");
                    for (String key : settings.keySet()) {
                        ModuleSetting<?> setting = module.getSetting(key);
                        if (setting == null) continue;
                        JsonPrimitive p = settings.get(key).getAsJsonPrimitive();
                        if      (p.isNumber())  setting.setFromObject(p.getAsDouble());
                        else if (p.isBoolean()) setting.setFromObject(p.getAsBoolean());
                        else                    setting.setFromObject(p.getAsString());
                    }
                }
            }

            settingsManager.save(moduleManager);
            return true;

        } catch (Exception e) {
            System.err.println("[StrClient] Failed to import profile: " + e.getMessage());
            return false;
        }
    }

    // ── List available profiles ───────────────────────────────────────

    public static List<String> listProfiles() {
        List<String> result = new ArrayList<>();
        try (Stream<Path> files = Files.list(PROFILE_DIR)) {
            files.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .map(p -> p.getFileName().toString().replace(".json", ""))
                    .sorted()
                    .forEach(result::add);
        } catch (IOException ignored) {}
        return result;
    }

    public static Path getProfileDir() { return PROFILE_DIR; }
}