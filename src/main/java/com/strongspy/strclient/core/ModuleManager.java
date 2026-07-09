package com.strongspy.strclient.core;

import net.minecraft.client.MinecraftClient;
import java.util.*;

/**
 * 模块注册表 + tick 分发器。
 * 在 StrClientMod.java 里调用 register() 即可完成模块接入。
 */
public class ModuleManager {

    private final SettingsManager settingsManager;
    private final Map<String, AbstractModule> modules = new LinkedHashMap<>();

    /** Tracks enable order so conflict resolution knows which module was enabled first. */
    private final LinkedHashSet<String> enabledOrder = new LinkedHashSet<>();

    public ModuleManager(SettingsManager settingsManager) {
        this.settingsManager = settingsManager;
    }

    public void register(AbstractModule module) {
        modules.put(module.getId(), module);
        module.attachManager(this);
        settingsManager.registerModule(module);
    }

    public void tickAll(MinecraftClient client) {
        for (AbstractModule module : modules.values()) {
            if (module.isEnabled()) {
                try {
                    module.onTick(client);
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Called by AbstractModule right after it transitions to enabled=true.
     * Checks the conflict table and force-disables any other currently
     * enabled module that conflicts with the one just turned on.
     */
    void onModuleEnabled(AbstractModule justEnabled, MinecraftClient client) {
        enabledOrder.add(justEnabled.getId());

        Set<String> conflicts = ModuleConflicts.getConflicts(justEnabled.getId());
        if (conflicts.isEmpty()) return;

        for (String conflictId : conflicts) {
            AbstractModule other = modules.get(conflictId);
            if (other == null || !other.isEnabled()) continue;

            // The one enabled first stays off; the new one wins.
            // (enabledOrder records insertion order, so "other" was enabled earlier
            //  since it's still in the set from before justEnabled was added)
            other.forceDisable(client);
            enabledOrder.remove(other.getId());

            HudOverlay.pushToast(other.getDisplayName() + " was turned off to prevent errors", false);
            settingsManager.save(this);
        }
    }

    void onModuleDisabled(AbstractModule justDisabled) {
        enabledOrder.remove(justDisabled.getId());
    }

    public AbstractModule get(String id) { return modules.get(id); }
    public Collection<AbstractModule> getAll() { return modules.values(); }
    public Map<String, AbstractModule> getMap() { return Collections.unmodifiableMap(modules); }
}