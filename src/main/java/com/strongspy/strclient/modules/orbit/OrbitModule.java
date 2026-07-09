package com.strongspy.strclient.modules.orbit;

import com.strongspy.strclient.core.AbstractModule;
import com.strongspy.strclient.core.ModuleSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class OrbitModule extends AbstractModule {

    private double angle = 0;
    private LivingEntity currentTarget = null;
    private int targetSwitchCooldown = 0;

    public OrbitModule() {
        super("orbit", "Orbit", Category.COMBAT,
                "Automatically orbit around your target (position only, view locked).");
    }

    @Override
    protected void registerSettings() {
        registerSetting(ModuleSetting.ofDouble("radius", "Radius (blocks)",
                "Distance from target", 4.0, 1.0, 10.0));
        registerSetting(ModuleSetting.ofDouble("speed", "Speed (deg/tick)",
                "Rotation speed", 5.0, 0.5, 20.0));
        registerSetting(ModuleSetting.ofBoolean("autoSwitch", "Auto Switch Target",
                "Switch to nearest target", true));
        registerSetting(ModuleSetting.ofString("targetMode", "Target Mode",
                "Entity type to target", "ALL",
                "ALL", "HOSTILE", "PLAYERS"));
        registerSetting(ModuleSetting.ofDouble("range", "Target Search Range",
                "Max distance to find target", 20.0, 5.0, 40.0));
        registerSetting(ModuleSetting.ofDouble("fov", "Field of View",
                "Angle to look for target (for selection only)", 180.0, 30.0, 360.0));
        registerSetting(ModuleSetting.ofBoolean("followHeight", "Follow Height",
                "Follow target's Y position", true));
    }

    @Override
    public void onTick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) return;

        if (targetSwitchCooldown > 0) targetSwitchCooldown--;

        LivingEntity target = getTarget(client);
        if (target == null) {
            currentTarget = null;
            return;
        }
        currentTarget = target;

        // 保存当前视角
        float originalYaw = player.getYaw();
        float originalPitch = player.getPitch();

        Vec3d targetPos = target.getPos();
        double speedRad = Math.toRadians(getDouble("speed"));
        angle += speedRad;

        double radius = getDouble("radius");
        double targetX = targetPos.x + radius * Math.cos(angle);
        double targetZ = targetPos.z + radius * Math.sin(angle);
        double targetY = getBoolean("followHeight") ? targetPos.y : player.getY();

        // 移动位置
        player.setPosition(targetX, targetY, targetZ);

        // 重置速度和跌落距离，防止重力影响
        player.setVelocity(Vec3d.ZERO);
        player.fallDistance = 0;

        // 强制恢复视角（防止任何内部自动旋转）
        player.setYaw(originalYaw);
        player.setPitch(originalPitch);
    }

    private LivingEntity getTarget(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        String targetMode = getString("targetMode");
        double range = getDouble("range");
        double fov = getDouble("fov");
        boolean autoSwitch = getBoolean("autoSwitch");

        if (!autoSwitch && currentTarget != null && currentTarget.isAlive() && !currentTarget.isRemoved()) {
            if (player.distanceTo(currentTarget) <= range) return currentTarget;
        }

        Box box = player.getBoundingBox().expand(range);
        List<LivingEntity> candidates = client.world.getEntitiesByClass(LivingEntity.class, box,
                e -> isValidTarget(e, player, targetMode));
        if (candidates.isEmpty()) return null;
        candidates.sort(Comparator.comparingDouble(e -> player.squaredDistanceTo(e)));

        if (fov < 360.0) {
            Vec3d lookVec = player.getRotationVec(1.0F);
            candidates = candidates.stream()
                    .filter(e -> {
                        Vec3d toTarget = e.getPos().subtract(player.getPos()).normalize();
                        double angleDeg = Math.toDegrees(Math.acos(lookVec.dotProduct(toTarget)));
                        return angleDeg <= fov / 2.0;
                    })
                    .collect(Collectors.toList());
            if (candidates.isEmpty()) return null;
        }
        return candidates.get(0);
    }

    private boolean isValidTarget(LivingEntity target, PlayerEntity player, String mode) {
        if (target == player) return false;
        if (target.isDead() || target.isRemoved()) return false;
        if (!target.isAttackable()) return false;
        if (target.isInvisible()) return false;
        if (target instanceof ArmorStandEntity) return mode.equals("ALL");
        switch (mode) {
            case "HOSTILE":
                return target instanceof Monster || target instanceof HostileEntity;
            case "PLAYERS":
                return target instanceof PlayerEntity;
            default:
                return true;
        }
    }

    @Override
    public void onDisable(MinecraftClient client) {
        currentTarget = null;
    }
}