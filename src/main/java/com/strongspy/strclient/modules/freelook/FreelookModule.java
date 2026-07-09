package com.strongspy.strclient.modules.freelook;

import com.strongspy.strclient.core.AbstractModule;
import com.strongspy.strclient.core.ModuleSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

import java.lang.reflect.Field;

public class FreelookModule extends AbstractModule {

    private float bodyYaw;
    private Field bodyYawField;
    private Field headYawField;

    public FreelookModule() {
        super("freelook", "Free Look", Category.MOVEMENT,
                "Lock body rotation while allowing free camera movement.");
        try {
            bodyYawField = net.minecraft.entity.LivingEntity.class.getDeclaredField("bodyYaw");
            bodyYawField.setAccessible(true);
            headYawField = net.minecraft.entity.LivingEntity.class.getDeclaredField("headYaw");
            headYawField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            // 1.21 混淆名回退
            try {
                bodyYawField = net.minecraft.entity.LivingEntity.class.getDeclaredField("field_6036");
                bodyYawField.setAccessible(true);
                headYawField = net.minecraft.entity.LivingEntity.class.getDeclaredField("field_6035");
                headYawField.setAccessible(true);
            } catch (NoSuchFieldException ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    protected void registerSettings() {
        registerSetting(ModuleSetting.ofBoolean("lockBody", "Lock Body Rotation",
                "Keep body facing fixed direction while looking around", true));
    }

    @Override
    public void onEnable(MinecraftClient client) {
        if (client.player != null) {
            bodyYaw = client.player.getYaw();
        }
    }

    @Override
    public void onTick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.getNetworkHandler() == null) return;
        if (bodyYawField == null || headYawField == null) return;

        if (!getBoolean("lockBody")) return;

        float viewPitch = player.getPitch();

        // 设置身体和头部朝向为固定值
        try {
            bodyYawField.setFloat(player, bodyYaw);
            headYawField.setFloat(player, bodyYaw);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        // 发送移动数据包，使用 bodyYaw 作为旋转，pitch 使用当前视角
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        PlayerMoveC2SPacket packet = new PlayerMoveC2SPacket.Full(x, y, z, bodyYaw, viewPitch, player.isOnGround());
        player.networkHandler.sendPacket(packet);
    }

    @Override
    public void onDisable(MinecraftClient client) {
        if (client.player != null && bodyYawField != null && headYawField != null) {
            try {
                float currentYaw = client.player.getYaw();
                bodyYawField.setFloat(client.player, currentYaw);
                headYawField.setFloat(client.player, currentYaw);
            } catch (IllegalAccessException ignored) {}
        }
    }
}