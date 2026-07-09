package com.strongspy.strclient.modules.jumpmodify;

import com.strongspy.strclient.core.AbstractModule;
import com.strongspy.strclient.core.ModuleSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

public class JumpModifyModule extends AbstractModule {

    private boolean wasOnGround = false;

    public JumpModifyModule() {
        super("jumpmodify", "Jump Modify", Category.MOVEMENT,
                "Boost horizontal speed when jumping.");
    }

    @Override
    protected void registerSettings() {
        registerSetting(ModuleSetting.ofDouble("horizontalMultiplier", "Horizontal Multiplier",
                "Multiply horizontal speed on jump", 1.5, 1.0, 5.0));
        registerSetting(ModuleSetting.ofDouble("verticalMultiplier", "Vertical Multiplier",
                "Multiply vertical speed on jump", 1.0, 1.0, 3.0));
    }

    @Override
    public void onTick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        boolean onGround = player.isOnGround();
        // 检测起跳瞬间（从地面变为空中）
        if (wasOnGround && !onGround) {
            // 确认是主动跳跃（按跳跃键或垂直速度上升）
            if (client.options.jumpKey.isPressed() || player.getVelocity().y > 0.1) {
                double hMulti = getDouble("horizontalMultiplier");
                double vMulti = getDouble("verticalMultiplier");
                Vec3d vel = player.getVelocity();
                // 应用倍率
                player.setVelocity(vel.x * hMulti, vel.y * vMulti, vel.z * hMulti);
                // 重置摔落距离，防止落地伤害
                player.fallDistance = 0;
            }
        }
        wasOnGround = onGround;
    }
}