package com.strongspy.strclient.modules.autorespawnanchor;

import com.strongspy.strclient.core.AbstractModule;
import com.strongspy.strclient.core.ModuleSetting;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Ultimate Auto Respawn Anchor Module
 * Features: Optional auto-placement of anchors, single glowstone charging,
 * and instant weapon/tool detonation.
 */
public class AutoRespawnAnchorModule extends AbstractModule {

    private int cooldown = 0;

    public AutoRespawnAnchorModule() {
        super("autorespawnanchor", "Auto Respawn Anchor", Category.COMBAT,
                "Automatically places, charges with 1 glowstone, and detonates respawn anchors.");
    }

    @Override
    protected void registerSettings() {
        registerSetting(ModuleSetting.ofInt("range", "Range (blocks)",
                "Search and placement radius for respawn anchors", 5, 1, 10));
        registerSetting(ModuleSetting.ofInt("delay", "Delay (ticks)",
                "Delay ticks between interactions", 2, 1, 20));

        // 新增设置项：是否全自动放置重生锚方块
        registerSetting(ModuleSetting.ofBoolean("autoPlace", "Auto Place",
                "Automatically place a Respawn Anchor block if none are nearby", true));

        registerSetting(ModuleSetting.ofBoolean("autoSwitch", "Auto Switch",
                "Automatically switch to anchor/glowstone in hotbar", true));
        registerSetting(ModuleSetting.ofBoolean("debug", "Debug Messages",
                "Show debug messages in chat logs", false));
    }

    @Override
    public void onTick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        int range = getInt("range");
        BlockPos playerPos = player.getBlockPos();
        List<BlockPos> anchors = new ArrayList<>();

        // 1. 扫描范围内现有的重生锚
        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (client.world.getBlockState(pos).isOf(Blocks.RESPAWN_ANCHOR)) {
                        anchors.add(pos);
                    }
                }
            }
        }

        // 2. 如果没有找到现成的重生锚，且开启了自动放置
        if (anchors.isEmpty() && getBoolean("autoPlace")) {
            BlockPos targetPlacePos = findPlacePosition(client, range);
            if (targetPlacePos != null) {
                int anchorSlot = findItemSlot(client, Items.RESPAWN_ANCHOR);
                if (anchorSlot != -1) {
                    if (interactWithBlock(client, targetPlacePos, anchorSlot)) {
                        cooldown = getInt("delay");
                        return;
                    }
                }
            }
            return; // 找不到合适位置或没方块则等待下一Tick
        }

        // 3. 如果找到了现成的重生锚，开始执行 1 萤石引爆流
        if (!anchors.isEmpty()) {
            anchors.sort(Comparator.comparingDouble(p -> player.squaredDistanceTo(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5)));
            BlockPos targetAnchor = anchors.get(0);

            int charges = client.world.getBlockState(targetAnchor).get(RespawnAnchorBlock.CHARGES);

            if (charges == 0) {
                // 精准充能 1 个萤石
                int glowstoneSlot = findItemSlot(client, Items.GLOWSTONE);
                if (glowstoneSlot != -1) {
                    if (interactWithBlock(client, targetAnchor, glowstoneSlot)) {
                        cooldown = getInt("delay");
                    }
                }
            } else {
                // 已有充能，直接用当前手持物引爆
                if (detonateAnchor(client, targetAnchor)) {
                    cooldown = getInt("delay");
                }
            }
        }
    }

    /**
     * 在范围内寻找适合放置重生锚的地面（下方必须是实体方块，上方必须是空气）
     */
    private BlockPos findPlacePosition(MinecraftClient client, int range) {
        ClientPlayerEntity player = client.player;
        BlockPos playerPos = player.getBlockPos();

        for (int y = -range; y <= range; y++) {
            for (int x = -range; x <= range; x++) {
                for (int z = -range; z <= range; z++) {
                    BlockPos pos = playerPos.add(x, y, z);

                    // 确保目标位置是空气，且下方是坚固的实体方块可以承载物品
                    if (client.world.getBlockState(pos).isAir() &&
                            client.world.getBlockState(pos.down()).isSolidBlock(client.world, pos.down())) {

                        // 距离校验
                        if (player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= range * range) {
                            return pos.down(); // 返回要点击交互的底面方块位置
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * 基础方块/物品交互，完美贴合您原版架构的返回值校验
     */
    private boolean interactWithBlock(MinecraftClient client, BlockPos pos, int slot) {
        ClientPlayerEntity player = client.player;
        if (player == null) return false;

        int prevSlot = player.getInventory().selectedSlot;
        if (getBoolean("autoSwitch") && slot != prevSlot) {
            player.getInventory().selectedSlot = slot;
        }

        Vec3d hitVec = new Vec3d(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, pos, false);

        var result = client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hitResult);
        player.swingHand(Hand.MAIN_HAND);

        if (getBoolean("autoSwitch") && slot != prevSlot) {
            player.getInventory().selectedSlot = prevSlot;
        }

        return result == net.minecraft.util.ActionResult.SUCCESS || result == net.minecraft.util.ActionResult.CONSUME;
    }

    /**
     * 核心引爆：不切任何槽位，直接右键点爆
     */
    private boolean detonateAnchor(MinecraftClient client, BlockPos anchorPos) {
        ClientPlayerEntity player = client.player;
        if (player == null) return false;

        Vec3d hitVec = new Vec3d(anchorPos.getX() + 0.5, anchorPos.getY() + 0.5, anchorPos.getZ() + 0.5);
        BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, anchorPos, false);

        var result = client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hitResult);
        player.swingHand(Hand.MAIN_HAND);

        return result == net.minecraft.util.ActionResult.SUCCESS || result == net.minecraft.util.ActionResult.CONSUME;
    }

    private int findItemSlot(MinecraftClient client, net.minecraft.item.Item item) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (stack.getItem() == item) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void onDisable(MinecraftClient client) {
        cooldown = 0;
    }
}