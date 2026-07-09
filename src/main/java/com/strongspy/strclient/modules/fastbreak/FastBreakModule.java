package com.strongspy.strclient.modules.fastbreak;

import com.strongspy.strclient.core.AbstractModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * FastBreak 模块 - 修复版
 * 加速方块挖掘速度，无设置项。
 */
public class FastBreakModule extends AbstractModule {

    private BlockPos currentTarget = null;
    private int breakProgress = 0;

    public FastBreakModule() {
        super("fastbreak", "Fast Break", Category.UTILITY,
                "Increases block breaking speed.");
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null || client.interactionManager == null) return;

        // 检查玩家是否正在挖掘（通过 client.interactionManager.isBreakingBlock()）
        // 注意：isBreakingBlock() 在 1.21 中可能不存在，改用反射获取
        boolean isBreaking = false;
        BlockPos target = null;
        try {
            java.lang.reflect.Field field = client.interactionManager.getClass().getDeclaredField("currentBreakingBlockPos");
            field.setAccessible(true);
            target = (BlockPos) field.get(client.interactionManager);
            isBreaking = (target != null);
        } catch (Exception e) {
            // 反射失败时，通过瞄准的方块和鼠标左键状态推断
            isBreaking = client.options.attackKey.isPressed() && client.crosshairTarget instanceof BlockHitResult;
            if (isBreaking && client.crosshairTarget instanceof BlockHitResult hit) {
                target = hit.getBlockPos();
            }
        }

        if (isBreaking && target != null) {
            if (currentTarget == null || !currentTarget.equals(target)) {
                currentTarget = target;
                breakProgress = 0;
            }

            // 加速挖掘进度（每 tick 增加 5 点进度）
            breakProgress += 5;

            if (breakProgress >= 10) {
                // 发送完成挖掘的数据包（STOP_DESTROY_BLOCK）
                client.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
                        currentTarget,
                        Direction.UP
                ));
                currentTarget = null;
                breakProgress = 0;
            } else {
                // 发送进度更新包（ABORT_DESTROY_BLOCK 也可用作进度刷新）
                client.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK,
                        currentTarget,
                        Direction.UP
                ));
            }
        } else {
            currentTarget = null;
            breakProgress = 0;
        }
    }
}