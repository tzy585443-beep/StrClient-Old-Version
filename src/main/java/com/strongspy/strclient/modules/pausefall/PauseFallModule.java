package com.strongspy.strclient.modules.pausefall;

import com.strongspy.strclient.core.AbstractModule;
import com.strongspy.strclient.core.ModuleSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

public class PauseFallModule extends AbstractModule {

    private boolean isPaused = false;
    private long pauseStartTime = 0;
    private double pauseY = 0;

    public PauseFallModule() {
        super("pausefall", "Pause Fall", Category.MOVEMENT,
                "Automatically pause in mid-air when falling fast, giving you time to react.");
    }

    @Override
    protected void registerSettings() {
        registerSetting(ModuleSetting.ofDouble("triggerSpeed", "Trigger Speed",
                "Falling speed to trigger pause (negative value threshold)", -2.0, -10.0, -0.5));
        registerSetting(ModuleSetting.ofInt("pauseDuration", "Pause Duration (s)",
                "How long to stay paused before resuming", 5, 1, 30));
        registerSetting(ModuleSetting.ofBoolean("autoResumeOnJump", "Resume on Jump",
                "Press jump to resume falling immediately", true));
        registerSetting(ModuleSetting.ofBoolean("autoResumeOnSneak", "Resume on Sneak",
                "Press sneak to resume falling immediately", false));
    }

    @Override
    public void onEnable(MinecraftClient client) {
        isPaused = false;
        pauseStartTime = 0;
        if (client.player != null) {
            client.player.sendMessage(Text.literal("§aPause Fall enabled. Falling fast triggers hover."), false);
        }
    }

    @Override
    public void onDisable(MinecraftClient client) {
        if (isPaused) {
            releasePause(client);
        }
        isPaused = false;
    }

    @Override
    public void onTick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.getNetworkHandler() == null) return;

        if (isPaused) {
            // 检查是否应该恢复
            boolean shouldResume = false;
            long elapsed = System.currentTimeMillis() - pauseStartTime;
            int durationMs = getInt("pauseDuration") * 1000;
            if (elapsed > durationMs) {
                shouldResume = true;
            }
            if (getBoolean("autoResumeOnJump") && client.options.jumpKey.isPressed()) {
                shouldResume = true;
            }
            if (getBoolean("autoResumeOnSneak") && client.options.sneakKey.isPressed()) {
                shouldResume = true;
            }

            if (shouldResume) {
                releasePause(client);
                return;
            }

            // 保持悬停：发送位置包，锁定Y坐标
            double x = player.getX();
            double y = pauseY;
            double z = player.getZ();
            // 确保位置包发送，防止玩家被服务器拉回
            client.getNetworkHandler().sendPacket(
                    new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, true)
            );
            // 本地玩家也锁定位置
            player.setPosition(x, y, z);
            // 速度归零
            player.setVelocity(Vec3d.ZERO);
            // 重置摔落距离
            player.fallDistance = 0;
            return;
        }

        // 未悬停，检测掉落速度
        double triggerSpeed = getDouble("triggerSpeed");
        if (player.getVelocity().y < triggerSpeed && !player.isOnGround()) {
            // 触发悬停
            isPaused = true;
            pauseY = player.getY();
            pauseStartTime = System.currentTimeMillis();
            // 锁定位置
            player.setPosition(player.getX(), pauseY, player.getZ());
            player.setVelocity(Vec3d.ZERO);
            player.fallDistance = 0;
            // 立即发送一个位置包，确保服务器同步
            client.getNetworkHandler().sendPacket(
                    new PlayerMoveC2SPacket.PositionAndOnGround(player.getX(), pauseY, player.getZ(), true)
            );
            // 提示
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§e§l⏸ Paused falling! You have " + getInt("pauseDuration") + "s to react."), false);
            }
        }
    }

    private void releasePause(MinecraftClient client) {
        if (!isPaused) return;
        isPaused = false;
        pauseStartTime = 0;
        if (client.player != null) {
            client.player.sendMessage(Text.literal("§aResumed falling."), false);
            // 允许继续下落
            // 注意：服务器可能需要接收到位置变化，我们发送一个更新包
            // 但玩家已经下落，速度已被重置，我们只需让客户端自然下落即可。
            // 但为了防止服务器卡位，发送一个 onGround=false 的包，表示玩家在掉落中
            client.getNetworkHandler().sendPacket(
                    new PlayerMoveC2SPacket.OnGroundOnly(false)
            );
        }
    }
}