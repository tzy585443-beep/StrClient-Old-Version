package com.strongspy.strclient.modules.autotaunt;

import com.strongspy.strclient.core.AbstractModule;
import com.strongspy.strclient.core.ModuleSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * AutoTaunt 自动嘲讽模块（修复无限发送bug）
 * 仅在实体从存活变为死亡的瞬间触发一次嘲讽
 */
public class AutoTauntModule extends AbstractModule {

    // 记录每个实体的上一帧存活状态
    private final Map<UUID, Boolean> lastAliveStatus = new HashMap<>();
    private long lastTauntTime = 0;
    private final Random random = new Random();

    // 纯文本嘲讽消息库
    private static final String[] TAUNT_MESSAGES = {
            "Get rekt!",
            "Too easy!",
            "You're a bot!",
            "Sit down!",
            "L bozo",
            "Nice try!",
            "Go back to lobby",
            "ez clap",
            "Bye bye!",
            "You died lol"
    };

    public AutoTauntModule() {
        super("autotaunt", "Auto Taunt", Category.UTILITY,
                "Automatically send a random taunt message when you kill an entity.");
    }

    @Override
    protected void registerSettings() {
        registerSetting(ModuleSetting.ofString(
                "triggerTarget", "Trigger Target",
                "Which entities trigger the taunt: PLAYERS, MONSTERS, ALL",
                "PLAYERS", "PLAYERS", "MONSTERS", "ALL"
        ));
        registerSetting(ModuleSetting.ofInt(
                "cooldown", "Cooldown (seconds)",
                "Minimum time between taunts",
                3, 0, 30
        ));
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.world == null || client.player == null) return;

        String targetMode = getString("triggerTarget");
        int cooldownSec = getInt("cooldown");

        long now = System.currentTimeMillis();
        if (now - lastTauntTime < cooldownSec * 1000L) {
            return;
        }

        // 遍历所有实体
        for (Entity entity : client.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (living == client.player) continue;

            UUID uuid = living.getUuid();
            boolean currentlyAlive = living.isAlive();
            Boolean wasAlive = lastAliveStatus.get(uuid);

            // 首次遇到该实体时，记录状态并跳过
            if (wasAlive == null) {
                lastAliveStatus.put(uuid, currentlyAlive);
                continue;
            }

            // 关键修复：仅在上一帧存活且当前帧死亡时触发（由活到死的瞬间）
            if (wasAlive && !currentlyAlive) {
                if (isTargetMatch(living, targetMode)) {
                    String message = TAUNT_MESSAGES[random.nextInt(TAUNT_MESSAGES.length)];
                    client.getNetworkHandler().sendChatMessage(message);
                    lastTauntTime = now;
                    // 发送后立即更新冷却，避免同一 tick 多次触发
                    break; // 一次只嘲讽一个实体
                }
            }

            // 更新状态
            lastAliveStatus.put(uuid, currentlyAlive);
        }
    }

    private boolean isTargetMatch(LivingEntity entity, String mode) {
        switch (mode) {
            case "PLAYERS":
                return entity instanceof PlayerEntity;
            case "MONSTERS":
                return entity instanceof Monster;
            case "ALL":
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onDisable(MinecraftClient client) {
        lastAliveStatus.clear();
        lastTauntTime = 0;
    }
}