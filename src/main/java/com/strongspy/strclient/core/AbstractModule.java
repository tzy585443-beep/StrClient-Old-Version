package com.strongspy.strclient.core;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class AbstractModule {

    public enum Category {
        COMBAT, MOVEMENT, VISUAL, UTILITY
    }

    private final String id;
    private final String displayName;
    private final Category category;
    private final String description;
    private boolean enabled = false;

    private KeyBinding keyBinding;
    private ModuleManager manager; // set via attachManager() during register()

    protected final Map<String, ModuleSetting<?>> settings = new LinkedHashMap<>();

    protected AbstractModule(String id, String displayName, Category category, String description) {
        this.id = id;
        this.displayName = displayName;
        this.category = category;
        this.description = description;
        registerSettings();
    }

    protected void registerSettings() {}

    public void onTick(MinecraftClient client) {}
    public void onEnable(MinecraftClient client) {}
    public void onDisable(MinecraftClient client) {}

    // ── Setting helpers ───────────────────────────────────────────────

    protected void registerSetting(ModuleSetting<?> setting) {
        settings.put(setting.getKey(), setting);
    }

    @SuppressWarnings("unchecked")
    public <T> ModuleSetting<T> getSetting(String key) {
        return (ModuleSetting<T>) settings.get(key);
    }

    public double getDouble(String key) {
        ModuleSetting<Double> s = getSetting(key);
        return s != null ? s.getValue() : 0.0;
    }

    public int getInt(String key) {
        ModuleSetting<Double> s = getSetting(key);
        return s != null ? (int) Math.round(s.getValue()) : 0;
    }

    public boolean getBoolean(String key) {
        ModuleSetting<Boolean> s = getSetting(key);
        return s != null && s.getValue();
    }

    public String getString(String key) {
        ModuleSetting<String> s = getSetting(key);
        return s != null ? s.getValue() : "";
    }

    // ── Getters ───────────────────────────────────────────────────────

    public String getId()          { return id; }
    public String getDisplayName() { return displayName; }
    public Category getCategory()  { return category; }
    public String getDescription() { return description; }
    public boolean isEnabled()     { return enabled; }
    public Map<String, ModuleSetting<?>> getSettings() { return settings; }

    // ── Keybind ──────────────────────────────────────────────────────

    public void setKeyBinding(KeyBinding keyBinding) { this.keyBinding = keyBinding; }
    public KeyBinding getKeyBinding() { return keyBinding; }

    // ── Manager link (used for conflict resolution) ────────────────────

    void attachManager(ModuleManager manager) { this.manager = manager; }

    // ── Enable / disable ──────────────────────────────────────────────

    public void setEnabled(boolean enabled, MinecraftClient client) {
        boolean was = this.enabled;
        if (was == enabled) return; // no-op

        this.enabled = enabled;
        if (client != null) {
            if (enabled) {
                onEnable(client);
                Notifications.moduleToggled(client, this, true);
                if (manager != null) manager.onModuleEnabled(this, client);
            } else {
                onDisable(client);
                Notifications.moduleToggled(client, this, false);
                if (manager != null) manager.onModuleDisabled(this);
            }
        }
    }

    /**
     * Used internally by ModuleManager's conflict resolution — disables this
     * module without re-triggering conflict checks (avoids infinite loops)
     * and without the normal "enabled/disabled" toast, since the conflict
     * system shows its own message.
     */
    void forceDisable(MinecraftClient client) {
        if (!enabled) return;
        enabled = false;
        if (client != null) onDisable(client);
        if (manager != null) manager.onModuleDisabled(this);
    }

    public void toggle(MinecraftClient client) {
        setEnabled(!enabled, client);
    }
}