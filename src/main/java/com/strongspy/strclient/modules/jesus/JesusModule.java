package com.strongspy.strclient.modules.jesus;

import com.strongspy.strclient.core.AbstractModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Jesus 模块 - 完美固体行走修复版
 * 允许玩家像踩在实体方块上一样在水/岩浆上行走和跳跃，绝不卡空。
 */
public class JesusModule extends AbstractModule {

    public JesusModule() {
        super("jesus", "Jesus", Category.MOVEMENT,
                "Walk on water and lava as if they were solid ground.");
    }

    @Override
    public void onTick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        BlockPos feetPos = player.getBlockPos();
        Fluid fluidAtFeet = player.getWorld().getFluidState(feetPos).getFluid();
        Fluid fluidBelow = player.getWorld().getFluidState(feetPos.down()).getFluid();

        // 只有脚下有液体，或者脚已经陷入液体时才激活逻辑
        boolean onLiquid = isLiquid(fluidAtFeet) || isLiquid(fluidBelow);
        if (!onLiquid) return;

        // 计算精确的液面固体 Y 轴高度
        double liquidY;
        if (isLiquid(fluidAtFeet)) {
            liquidY = feetPos.getY() + 1.0;
        } else {
            liquidY = feetPos.down().getY() + 1.0;
        }

        // 真实踩实表面的判定：
        // 只有当玩家当前的 Y 轴已经低于或等于液面（或者稍微下陷时），我们才将他接住
        if (player.getY() <= liquidY + 0.05) {
            // 稳稳托住玩家在液面上
            player.setPosition(player.getX(), liquidY, player.getZ());

            // 只有当玩家想往下沉（vel.y < 0）时才阻止，如果玩家按跳跃键（vel.y > 0）则放行
            Vec3d vel = player.getVelocity();
            if (vel.y < 0) {
                player.setVelocity(vel.x, 0, vel.z);
                // 踩在“固体”上，重置在地面状态，允许玩家在水面上直接按空格键跳跃！
                player.setOnGround(true);
            }
            player.fallDistance = 0;
        }
        // 如果 player.getY() 明显大于 liquidY（说明玩家刚刚在液体前跳了一下，正处于半空中），
        // 此时代码什么都不做，让游戏原版的重力自然带他下落，直到落回水面触发上面的 if 条件。
    }

    private boolean isLiquid(Fluid fluid) {
        return fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER ||
                fluid == Fluids.LAVA || fluid == Fluids.FLOWING_LAVA;
    }
}