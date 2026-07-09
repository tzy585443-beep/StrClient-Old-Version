package com.strongspy.strclient.modules.antipowdersnow;

import com.strongspy.strclient.core.AbstractModule;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Anti Powder Snow 模块 - 伪固体终极版
 * 彻底修复跳跃拉回、短暂陷落和冰冻伤害的 Bug。
 */
public class AntiPowderSnowModule extends AbstractModule {

    public AntiPowderSnowModule() {
        super("antipowdersnow", "Anti Powder Snow", Category.MOVEMENT,
                "Walk and jump on powder snow smoothly like a solid block.");
    }

    @Override
    public void onTick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        // 1. 强制清空冷冻刻，防止任何意外的客户端冰冻减速
        if (player.getFrozenTicks() > 0) {
            player.setFrozenTicks(0);
        }

        BlockPos feetPos = player.getBlockPos();
        // 扫描玩家脚下及周围的细雪方块
        boolean hasSnowBelow = player.getWorld().getBlockState(feetPos.down()).isOf(Blocks.POWDER_SNOW)
                || player.getWorld().getBlockState(feetPos).isOf(Blocks.POWDER_SNOW);

        if (!hasSnowBelow) return;

        // 计算细雪完整的方块顶部 Y 轴（即 feetPos.getY() + 1.0，完全脱离 0.875 的减速区）
        double solidY = player.getWorld().getBlockState(feetPos).isOf(Blocks.POWDER_SNOW)
                ? feetPos.getY() + 1.0
                : feetPos.down().getY() + 1.0;

        // 2. 核心拦截：当玩家下落、或者踩在表面时
        if (player.getY() <= solidY + 0.05) {

            // 稳稳将玩家放在完整方块高度上
            player.setPosition(player.getX(), solidY, player.getZ());

            Vec3d vel = player.getVelocity();
            if (vel.y < 0) {
                // 停止下落速度
                player.setVelocity(vel.x, 0, vel.z);

                // 3. 告诉游戏我们踩在真正的固体方块上！
                // 这允许玩家直接在细雪上向服务器发送完美的、正常的地面跳跃数据包，彻底消除跳跃被拉回的 Bug！
                player.setOnGround(true);
            }
            player.fallDistance = 0;
        }
    }
}