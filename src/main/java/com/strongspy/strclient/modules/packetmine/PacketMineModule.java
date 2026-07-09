package com.strongspy.strclient.modules.packetmine;

import com.strongspy.strclient.core.AbstractModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Packet Mine 模块 - 手动触发版
 * 必须用左键点击一次方块才开始持续挖掘，不会自动开始。
 * 可远离目标，挖掘不会中断，直到方块破坏或关闭模块。
 */
public class PacketMineModule extends AbstractModule {

    private BlockPos targetPos = null;
    private Direction targetSide = null;
    private boolean isMining = false;          // 是否正在挖掘中
    private boolean wasAttackKeyPressed = false; // 用于检测左键按下瞬间

    public PacketMineModule() {
        super("packetmine", "Packet Mine", Category.UTILITY,
                "Left-click a block once to start auto-mining it. You can then walk away.");
    }

    @Override
    public void onEnable(MinecraftClient client) {
        // 模块开启时重置状态，不自动锁定任何方块
        resetState(client);
    }

    @Override
    public void onDisable(MinecraftClient client) {
        stopMining(client);
        resetState(client);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null || client.interactionManager == null) return;

        // 1. 检测玩家是否刚刚按下了左键（手动点击）
        KeyBinding attackKey = client.options.attackKey;
        boolean isAttackKeyPressed = attackKey.isPressed();
        boolean justClicked = isAttackKeyPressed && !wasAttackKeyPressed;
        wasAttackKeyPressed = isAttackKeyPressed;

        if (justClicked) {
            // 玩家点击了左键
            if (client.crosshairTarget instanceof BlockHitResult hit) {
                BlockPos pos = hit.getBlockPos();
                Direction side = hit.getSide();
                // 如果已经在挖掘同一个方块，不做变动；否则切换到新目标
                if (targetPos == null || !targetPos.equals(pos)) {
                    startMining(client, pos, side);
                }
            } else {
                // 点击了空气，停止所有挖掘（可选）
                if (isMining) {
                    stopMining(client);
                }
            }
        }

        // 2. 如果正在挖掘，持续发送挖掘进度包
        if (isMining && targetPos != null) {
            // 检查目标方块是否已被破坏
            if (client.world.getBlockState(targetPos).isAir()) {
                stopMining(client);
                return;
            }

            // 每 tick 发送破坏进度更新（核心：让服务器持续挖掘）
            client.interactionManager.updateBlockBreakingProgress(targetPos, targetSide != null ? targetSide : Direction.UP);

            // 可选：偶尔挥手（视觉效果）
            if (client.player.age % 6 == 0) {
                client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            }
        }
    }

    /**
     * 开始挖掘指定的方块
     */
    private void startMining(MinecraftClient client, BlockPos pos, Direction side) {
        // 停止当前挖掘（如果有）
        if (isMining) {
            stopMining(client);
        }

        // 发送开始破坏包
        client.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
                pos,
                side
        ));

        targetPos = pos;
        targetSide = side;
        isMining = true;
    }

    /**
     * 停止挖掘
     */
    private void stopMining(MinecraftClient client) {
        if (client.player == null || client.interactionManager == null) return;
        if (targetPos != null && isMining) {
            // 发送中止破坏包
            client.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK,
                    targetPos,
                    targetSide != null ? targetSide : Direction.UP
            ));
        }
        isMining = false;
        client.interactionManager.cancelBlockBreaking();
    }

    private void resetState(MinecraftClient client) {
        targetPos = null;
        targetSide = null;
        isMining = false;
        wasAttackKeyPressed = false;
        if (client.player != null && client.interactionManager != null) {
            client.interactionManager.cancelBlockBreaking();
        }
    }

    public void clearTarget() {
        if (MinecraftClient.getInstance().player != null) {
            stopMining(MinecraftClient.getInstance());
        }
        targetPos = null;
        targetSide = null;
        isMining = false;
    }
}