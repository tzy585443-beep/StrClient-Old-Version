package com.strongspy.strclient.modules.speed;

import com.strongspy.strclient.core.AbstractModule;
import com.strongspy.strclient.core.ModuleSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Speed 模块（修改基础移动速度属性）
 * 不通过药水效果，直接修改玩家的 MOVEMENT_SPEED 属性值
 */
public class SpeedModule extends AbstractModule {

    private double originalSpeed = 0.1; // 原版默认值 0.1

    public SpeedModule() {
        super("speed", "Speed", Category.MOVEMENT,
                "Increase player movement speed by modifying base attribute (not potion effect).");
    }

    @Override
    protected void registerSettings() {
        // 速度倍数 (原版速度为 1.0，2.0 表示两倍速度)
        registerSetting(ModuleSetting.ofDouble(
                "multiplier", "Speed Multiplier",
                "Multiplier for base movement speed (1.0 = vanilla)",
                1.5, 1.0, 5.0
        ));
    }

    @Override
    public void onEnable(MinecraftClient client) {
        if (client.player != null) {
            EntityAttributeInstance attribute = client.player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
            if (attribute != null) {
                originalSpeed = attribute.getBaseValue();
                applySpeed(client.player);
            }
        }
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        // 每 tick 都重新应用速度，防止被其他插件或游戏机制重置
        applySpeed(client.player);
    }

    @Override
    public void onDisable(MinecraftClient client) {
        if (client.player != null) {
            EntityAttributeInstance attribute = client.player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
            if (attribute != null) {
                attribute.setBaseValue(originalSpeed);
            }
        }
    }

    private void applySpeed(PlayerEntity player) {
        double multiplier = getDouble("multiplier");
        EntityAttributeInstance attribute = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (attribute != null) {
            // 原版基础速度为 0.1，乘以倍数
            double newSpeed = 0.1 * multiplier;
            attribute.setBaseValue(newSpeed);
        }
    }
}