package com.strongspy.strclient.modules.aimassist;

import com.strongspy.strclient.core.AbstractModule;
import com.strongspy.strclient.core.ModuleSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class AimAssistModule extends AbstractModule {

    public AimAssistModule() {
        super("aimassist", "Aim Assist", Category.COMBAT,
                "Smoothly pull your crosshair towards nearby targets.");
    }

    @Override
    protected void registerSettings() {
        registerSetting(ModuleSetting.ofDouble("fov", "FOV (degrees)",
                "Field of view to search for targets", 30.0, 5.0, 90.0));
        registerSetting(ModuleSetting.ofDouble("range", "Range (blocks)",
                "Maximum distance to target", 4.5, 2.0, 8.0));
        registerSetting(ModuleSetting.ofDouble("smoothness", "Smoothness",
                "Lower = faster snap, higher = smoother", 5.0, 1.0, 15.0));
        registerSetting(ModuleSetting.ofString("targetMode", "Target Mode",
                "Entity type to target", "ALL", "ALL", "HOSTILE", "PLAYERS"));
        registerSetting(ModuleSetting.ofBoolean("aimAtHead", "Aim at Head",
                "Target the head instead of center", false));
        registerSetting(ModuleSetting.ofBoolean("onlyWhileAttacking", "Only While Attacking",
                "Only assist when attacking", true));
    }

    @Override
    public void onTick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) return;

        // 如果设置为仅攻击时生效，检查是否按住左键
        if (getBoolean("onlyWhileAttacking") && !client.options.attackKey.isPressed()) {
            return;
        }

        double range = getDouble("range");
        double fov = getDouble("fov");
        String targetMode = getString("targetMode");
        double smoothness = getDouble("smoothness");
        boolean aimHead = getBoolean("aimAtHead");

        // 搜索范围内所有候选目标
        Box box = player.getBoundingBox().expand(range);
        List<LivingEntity> candidates = client.world.getEntitiesByClass(LivingEntity.class, box,
                e -> isValidTarget(e, player, targetMode));

        if (candidates.isEmpty()) return;

        // 按距离排序，取最近的一个
        candidates.sort(Comparator.comparingDouble(e -> player.squaredDistanceTo(e)));
        LivingEntity target = candidates.get(0);

        // FOV 过滤：计算准星到目标的角度
        Vec3d lookVec = player.getRotationVec(1.0F);
        Vec3d toTarget = target.getPos().subtract(player.getEyePos()).normalize();
        double angle = Math.toDegrees(Math.acos(lookVec.dotProduct(toTarget)));

        if (Math.abs(angle) > fov) return;

        // 计算目标瞄准点（中心或头部）
        Vec3d targetPoint = aimHead ?
                target.getPos().add(0, target.getHeight() * 0.85, 0) :
                target.getPos().add(0, target.getHeight() * 0.5, 0);

        Vec3d direction = targetPoint.subtract(player.getEyePos()).normalize();

        // 计算需要的 Yaw 和 Pitch
        float targetYaw = (float) Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0F;
        float targetPitch = (float) -Math.toDegrees(Math.asin(direction.y));

        // 当前角度
        float currentYaw = player.getYaw();
        float currentPitch = player.getPitch();

        // 计算差值
        float yawDiff = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDiff = targetPitch - currentPitch;

        // 平滑度：限制每 tick 最大旋转量
        float maxTurn = (float) (10.0 / smoothness); // smoothness=5 -> 2° per tick
        float newYaw = currentYaw + MathHelper.clamp(yawDiff, -maxTurn, maxTurn);
        float newPitch = currentPitch + MathHelper.clamp(pitchDiff, -maxTurn, maxTurn);
        newPitch = MathHelper.clamp(newPitch, -90.0F, 90.0F);

        // 应用旋转（视角会平滑移动）
        player.setYaw(newYaw);
        player.setPitch(newPitch);
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
}