package com.strongspy.strclient.modules.killaura;

import com.strongspy.strclient.core.AbstractModule;
import com.strongspy.strclient.core.ModuleSetting;
import com.strongspy.strclient.modules.targethud.TargetHUDModule; // 💡 导入 TargetHUD 模块
import com.strongspy.strclient.core.ModuleManager; // 如果你有全局管理器的化，也可以通过单例获取。这里假设可以直接静态交互或者通过 ModuleManager
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

public class KillAuraModule extends AbstractModule {

    private final Random random = new Random();
    private LivingEntity currentTarget = null;

    public KillAuraModule() {
        super("killaura", "Kill Aura", Category.COMBAT,
                "Ultimate fast attack – no cooldown, pure damage, optional swing");
    }

    @Override
    protected void registerSettings() {
        registerSetting(ModuleSetting.ofDouble("range", "Attack Range", "", 4.0, 1.0, 6.0));
        registerSetting(ModuleSetting.ofDouble("fov", "Field of View", "0-360", 360.0, 0.0, 360.0));
        registerSetting(ModuleSetting.ofBoolean("multiTarget", "Multi-Target", "", true));
        registerSetting(ModuleSetting.ofString("targetMode", "Target Mode", "", "ALL", "ALL", "HOSTILE", "PLAYERS"));
        registerSetting(ModuleSetting.ofBoolean("onlyOnGround", "Only On Ground", "", false));
        registerSetting(ModuleSetting.ofBoolean("whileMoving", "While Moving", "", true));
        registerSetting(ModuleSetting.ofBoolean("pauseOnUse", "Pause on Use", "", true));
        registerSetting(ModuleSetting.ofBoolean("autoRotate", "Auto Rotate", "Face target", true));
        registerSetting(ModuleSetting.ofDouble("maxTurnSpeed", "Max Turn Speed", "", 50.0, 10.0, 90.0));
        registerSetting(ModuleSetting.ofBoolean("hitSelector", "Hit Selector", "Only attack when hittable", false));
        registerSetting(ModuleSetting.ofBoolean("showSwing", "Show Swing Animation",
                "Display hand swing when attacking. Disable for 'blocking' look", false));
    }

    @Override
    public void onTick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) return;
        if (shouldPause(client)) return;

        double range = getDouble("range");
        double fov = getDouble("fov");
        String targetMode = getString("targetMode");
        List<LivingEntity> candidates = getTargetsInRange(client, range, targetMode);
        if (candidates.isEmpty()) {
            currentTarget = null;
            return;
        }
        candidates = filterByFOV(client, candidates, fov);
        if (candidates.isEmpty()) return;
        candidates.sort(Comparator.comparingDouble(e -> player.squaredDistanceTo(e)));

        boolean multiTarget = getBoolean("multiTarget");
        List<LivingEntity> targetsToAttack = multiTarget ? candidates : Collections.singletonList(candidates.get(0));

        LivingEntity primaryTarget = targetsToAttack.isEmpty() ? null : targetsToAttack.get(0);
        if (primaryTarget != null) {
            currentTarget = primaryTarget;
            if (getBoolean("autoRotate")) {
                applyRotation(client, primaryTarget);
            }
        }

        for (LivingEntity target : targetsToAttack) {
            if (getBoolean("hitSelector") && !isHittable(target)) continue;
            performAttack(client, target);
        }
    }

    private void performAttack(MinecraftClient client, LivingEntity target) {
        ClientPlayerEntity player = client.player;
        double distance = player.distanceTo(target);
        double range = getDouble("range");
        if (distance > range + 0.2) return;

        try {
            Field hurtTimeField = LivingEntity.class.getDeclaredField("hurtTime");
            hurtTimeField.setAccessible(true);
            hurtTimeField.setInt(target, 0);
        } catch (Exception ignored) {}

        // ⚔️ 1. 执行对实体的发包/交互攻击
        client.interactionManager.attackEntity(player, target);

        // 🌟 2. 【核心修复注入】强行打破隔离，将受击目标直接推送给 TargetHUD 静态锁
        if (target instanceof PlayerEntity targetPlayer) {
            com.strongspy.strclient.modules.targethud.TargetHUDModule.lockedTarget = targetPlayer;
            com.strongspy.strclient.modules.targethud.TargetHUDModule.lockTime = System.currentTimeMillis();
        }

        if (getBoolean("showSwing")) {
            player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        }
    }

    private void applyRotation(MinecraftClient client, LivingEntity target) {
        ClientPlayerEntity player = client.player;
        if (player == null || target == null) return;
        Vec3d direction = target.getBoundingBox().getCenter().subtract(player.getEyePos()).normalize();
        float targetYaw = (float) Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0F;
        float targetPitch = (float) -Math.toDegrees(Math.asin(direction.y));
        float currentYaw = player.getYaw();
        float currentPitch = player.getPitch();
        float yawDiff = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDiff = targetPitch - currentPitch;
        float maxTurn = (float) getDouble("maxTurnSpeed");
        float newYaw = currentYaw + MathHelper.clamp(yawDiff, -maxTurn, maxTurn);
        float newPitch = currentPitch + MathHelper.clamp(pitchDiff, -maxTurn, maxTurn);
        newPitch = MathHelper.clamp(newPitch, -90.0F, 90.0F);
        player.setYaw(newYaw);
        player.setPitch(newPitch);
    }

    private boolean shouldPause(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) return true;
        if (getBoolean("onlyOnGround") && !player.isOnGround()) return true;
        if (!getBoolean("whileMoving") && (Math.abs(player.getVelocity().x) > 0.01 || Math.abs(player.getVelocity().z) > 0.01))
            return true;
        if (getBoolean("pauseOnUse") && player.isUsingItem()) return true;
        return false;
    }

    private List<LivingEntity> getTargetsInRange(MinecraftClient client, double range, String mode) {
        Box box = client.player.getBoundingBox().expand(range);
        return client.world.getEntitiesByClass(LivingEntity.class, box,
                e -> isValidTarget(e, client.player, mode));
    }

    private List<LivingEntity> filterByFOV(MinecraftClient client, List<LivingEntity> targets, double fov) {
        if (fov >= 360.0) return targets;
        Vec3d lookVec = client.player.getRotationVec(1.0F);
        return targets.stream().filter(t -> {
            Vec3d toTarget = t.getPos().subtract(client.player.getPos()).normalize();
            double angle = Math.toDegrees(Math.acos(lookVec.dotProduct(toTarget)));
            return Math.abs(angle) <= fov / 2.0;
        }).collect(Collectors.toList());
    }

    private boolean isHittable(LivingEntity target) {
        try {
            Field hurtTimeField = LivingEntity.class.getDeclaredField("hurtTime");
            hurtTimeField.setAccessible(true);
            int hurtTime = hurtTimeField.getInt(target);
            return hurtTime == 0;
        } catch (Exception e) {
            return true;
        }
    }

    private boolean isTargetDead(LivingEntity target) {
        return target.isDead() || target.getHealth() <= 0;
    }

    private boolean isValidTarget(LivingEntity target, PlayerEntity player, String mode) {
        if (target == player) return false;
        if (target.isDead() || target.getHealth() <= 0) return false;
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
}