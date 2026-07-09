package com.strongspy.strclient.core;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * 持久化设置管理器。
 * 设置存储在 .minecraft/config/strclient/settings.json
 */
public class SettingsManager {

    private final Path configPath;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private JsonObject root = new JsonObject();
    private final Map<String, Map<String, ModuleSetting<?>>> moduleSettings = new LinkedHashMap<>();

    public SettingsManager() {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("strclient");
        try { Files.createDirectories(configDir); } catch (IOException ignored) {}
        configPath = configDir.resolve("settings.json");
    }

    public void registerModule(AbstractModule module) {
        moduleSettings.put(module.getId(), module.getSettings());
    }

    public void load(ModuleManager moduleManager) {
        if (!Files.exists(configPath)) return;
        try {
            root = JsonParser.parseString(Files.readString(configPath)).getAsJsonObject();
            for (var entry : moduleSettings.entrySet()) {
                String id = entry.getKey();
                if (!root.has(id)) continue;
                JsonObject mObj = root.getAsJsonObject(id);
                AbstractModule module = moduleManager.get(id);

                if (mObj.has("__enabled")) {
                    module.setEnabled(mObj.get("__enabled").getAsBoolean(), null);
                }

                JsonObject sObj = mObj.has("settings")
                        ? mObj.getAsJsonObject("settings") : new JsonObject();

                for (var sEntry : entry.getValue().entrySet()) {
                    String key = sEntry.getKey();
                    ModuleSetting<?> setting = sEntry.getValue();
                    if (!sObj.has(key)) continue;
                    JsonPrimitive p = sObj.get(key).getAsJsonPrimitive();
                    if (p.isNumber())       setting.setFromObject(p.getAsDouble());
                    else if (p.isBoolean()) setting.setFromObject(p.getAsBoolean());
                    else                    setting.setFromObject(p.getAsString());
                }
            }
        } catch (Exception e) {
            System.err.println("[StrClient] Failed to load settings: " + e.getMessage());
        }
    }

    public void save(ModuleManager moduleManager) {
        root = new JsonObject();
        for (var entry : moduleSettings.entrySet()) {
            String id = entry.getKey();
            AbstractModule module = moduleManager.get(id);
            JsonObject mObj = new JsonObject();
            mObj.addProperty("__enabled", module.isEnabled());
            JsonObject sObj = new JsonObject();
            for (var sEntry : entry.getValue().entrySet()) {
                Object val = sEntry.getValue().getValue();
                if (val instanceof Double d)  sObj.addProperty(sEntry.getKey(), d);
                else if (val instanceof Boolean b) sObj.addProperty(sEntry.getKey(), b);
                else sObj.addProperty(sEntry.getKey(), val.toString());
            }
            mObj.add("settings", sObj);
            root.add(id, mObj);
        }
        try { Files.writeString(configPath, gson.toJson(root)); }
        catch (IOException e) { System.err.println("[StrClient] Failed to save: " + e.getMessage()); }
    }
}
