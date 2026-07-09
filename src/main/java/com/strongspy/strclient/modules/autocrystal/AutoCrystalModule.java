package com.strongspy.strclient.modules.autocrystal;

import com.strongspy.strclient.core.AbstractModule;
import com.strongspy.strclient.core.ModuleSetting;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Ultimate Auto Crystal Module with integrated Auto-Obsidian logic.
 * Scans for vanilla obsidian/bedrock to place crystals. If none exist and enabled,
 * it will automatically place an obsidian block first, then place and break the crystal.
 */
public class AutoCrystalModule extends AbstractModule {

    private int placeCooldown = 0;
    private int breakCooldown = 0;

    public AutoCrystalModule() {
        super("autocrystal", "Auto Crystal", Category.COMBAT,
                "Automatically scans, places, and detonates crystals on nearby obsidian/bedrock");
    }

    @Override
    protected void registerSettings() {
        registerSetting(ModuleSetting.ofDouble("range", "Range (blocks)",
                "Max radius around player to scan and interact", 5.0, 2.0, 10.0));
        registerSetting(ModuleSetting.ofInt("placeDelay", "Place Delay (ticks)",
                "Delay ticks between placing crystals", 2, 1, 20));
        registerSetting(ModuleSetting.ofInt("breakDelay", "Break Delay (ticks)",
                "Delay ticks between breaking crystals", 2, 1, 20));

        // 新增核心设置项：如果附近没有黑曜石，是否全自动生成黑曜石方块
        registerSetting(ModuleSetting.ofBoolean("autoPlaceObsidian", "Auto Place Obsidian",
                "Automatically places an Obsidian block if no valid placements are found nearby", true));

        registerSetting(ModuleSetting.ofBoolean("onlyOnGround", "Only On Ground",
                "Only execute crystal actions when player is standing on the ground", false));
        registerSetting(ModuleSetting.ofBoolean("pauseOnUse", "Pause on Item Use",
                "Pause crystal automation while holding or consuming items", true));
        registerSetting(ModuleSetting.ofBoolean("autoSwitch", "Auto Switch",
                "Automatically switch to crystals/obsidian in your hotbar when needed", true));
        registerSetting(ModuleSetting.ofBoolean("debug", "Debug Messages",
                "Show crystal placement debug status in chat", false));
    }

    @Override
    public void onEnable(MinecraftClient client) {
        placeCooldown = 0;
        breakCooldown = 0;
    }

    @Override
    public void onTick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.interactionManager == null) return;
        if (getBoolean("pauseOnUse") && player.isUsingItem()) return;
        if (getBoolean("onlyOnGround") && !player.isOnGround()) return;

        if (placeCooldown > 0) placeCooldown--;
        if (breakCooldown > 0) breakCooldown--;

        double range = getDouble("range");

        // 1. 高速秒爆：自动检索获取范围内存在的水晶并秒杀
        EndCrystalEntity crystalToBreak = findCrystalInRange(client, range);
        if (crystalToBreak != null && breakCooldown == 0) {
            breakCrystal(client, crystalToBreak);
            breakCooldown = getInt("breakDelay");
            return;
        }

        // 2. 水晶放置与生成逻辑
        if (placeCooldown == 0) {
            // 首先寻找范围内现成的、合法的黑曜石或基岩位置
            BlockPos targetObsidian = findValidObsidianInRange(client, range);

            if (targetObsidian != null) {
                // 如果找到了现成方块，直接在上方放置末地水晶
                if (findItemSlot(client, Items.END_CRYSTAL) == -1) return;
                boolean placed = placeItem(client, targetObsidian, Items.END_CRYSTAL);
                if (placed) {
                    placeCooldown = getInt("placeDelay");
                }
            } else if (getBoolean("autoPlaceObsidian")) {
                // 如果范围内找不到任何黑曜石/基岩，且开启了“自动放置黑曜石”
                BlockPos obsidianPlaceTarget = findObsidianPlacementPosition(client, (int) Math.ceil(range));
                if (obsidianPlaceTarget != null) {
                    int obsidianSlot = findItemSlot(client, Items.OBSIDIAN);
                    if (obsidianSlot != -1) {
                        // 自动切到黑曜石，放置底座（传入的是底面依托方块，采用 UP 方向）
                        boolean placedObby = placeItem(client, obsidianPlaceTarget, Items.OBSIDIAN);
                        if (placedObby) {
                            // 放置成功后进入延迟，下一 tick 或者延迟结束后，代码会在新生成的黑曜石上放水晶
                            placeCooldown = getInt("placeDelay");
                        }
                    }
                }
            }
        }
    }

    private EndCrystalEntity findCrystalInRange(MinecraftClient client, double range) {
        Box box = client.player.getBoundingBox().expand(range);
        List<EndCrystalEntity> crystals = client.world.getEntitiesByClass(EndCrystalEntity.class, box,
                e -> client.player.squaredDistanceTo(e) <= range * range);

        if (crystals.isEmpty()) return null;

        EndCrystalEntity closest = null;
        double minDistance = Double.MAX_VALUE;
        for (EndCrystalEntity crystal : crystals) {
            double dist = client.player.squaredDistanceTo(crystal);
            if (dist < minDistance) {
                minDistance = dist;
                closest = crystal;
            }
        }
        return closest;
    }

    /**
     * 寻找范围内有效的、现成的黑曜石或基岩方块
     */
    private BlockPos findValidObsidianInRange(MinecraftClient client, double range) {
        ClientPlayerEntity player = client.player;
        BlockPos playerPos = player.getBlockPos();
        int radius = (int) Math.ceil(range);

        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = playerPos.add(dx, dy, dz);

                    if (player.squaredDistanceTo(Vec3d.ofCenter(pos)) > range * range) continue;

                    // 必须是黑曜石或基岩
                    if (client.world.getBlockState(pos).isOf(Blocks.OBSIDIAN) || client.world.getBlockState(pos).isOf(Blocks.BEDROCK)) {
                        BlockPos above = pos.up();

                        // 水晶放置条件：方块上方两格必须是空气
                        if (client.world.getBlockState(above).isAir() && client.world.getBlockState(above.up()).isAir()) {
                            Box entityCheck = new Box(above).expand(0.5);
                            if (client.world.getEntitiesByClass(EndCrystalEntity.class, entityCheck, e -> true).isEmpty()) {
                                return pos; // 返回当前可以放水晶的黑曜石坐标
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * 当地图上没黑曜石时，寻找一个能放下黑曜石（且放完后上方有2格空气可以堆水晶）的空位坐标
     */
    private BlockPos findObsidianPlacementPosition(MinecraftClient client, int range) {
        ClientPlayerEntity player = client.player;
        BlockPos playerPos = player.getBlockPos();

        for (int dy = -range; dy <= range; dy++) {
            for (int dx = -range; dx <= range; dx++) {
                for (int dz = -range; dz <= range; dz++) {
                    BlockPos pos = playerPos.add(dx, dy, dz);

                    // 距离校验
                    if (player.squaredDistanceTo(Vec3d.ofCenter(pos)) > range * range) continue;

                    // 目标位置（pos）是要生成的黑曜石的位置。所以它本身必须是空气，且上边一格、上边两格也必须是空气
                    if (client.world.getBlockState(pos).isAir() &&
                            client.world.getBlockState(pos.up()).isAir() &&
                            client.world.getBlockState(pos.up().up()).isAir()) {

                        // 放置方块必须要有一个邻近的实体方块表面来“右键点击”，这里检查底下一格是否是坚固方块
                        if (client.world.getBlockState(pos.down()).isSolidBlock(client.world, pos.down())) {
                            return pos.down(); // 返回右键要点击支撑的方块
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * 模块通用的右键交互逻辑（自动兼容切换水晶与黑曜石）
     * @param targetBlockPos 被点击的支撑方块（例如底下的实体方块，点击其 UP 顶面）
     */
    private boolean placeItem(MinecraftClient client, BlockPos targetBlockPos, net.minecraft.item.Item item) {
        ClientPlayerEntity player = client.player;
        int itemSlot = findItemSlot(client, item);
        if (itemSlot == -1) return false;

        int prevSlot = player.getInventory().selectedSlot;
        if (getBoolean("autoSwitch") && itemSlot != prevSlot) {
            player.getInventory().selectedSlot = itemSlot;
        }

        // 精确点击位置：方块顶面中心
        Vec3d hitVec = new Vec3d(targetBlockPos.getX() + 0.5, targetBlockPos.getY() + 1.0, targetBlockPos.getZ() + 0.5);
        BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, targetBlockPos, false);

        // 静默发包朝向（防身体剧烈抖动）
        double diffX = hitVec.x - player.getX();
        double diffY = hitVec.y - (player.getY() + player.getStandingEyeHeight());
        double diffZ = hitVec.z - player.getZ();
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) (Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(diffY, diffXZ)));

        float oldYaw = player.getYaw();
        float oldPitch = player.getPitch();
        player.setYaw(yaw);
        player.setPitch(pitch);

        var result = client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hitResult);

        player.setYaw(oldYaw);
        player.setPitch(oldPitch);
        player.swingHand(Hand.MAIN_HAND);

        if (getBoolean("autoSwitch") && itemSlot != prevSlot) {
            player.getInventory().selectedSlot = prevSlot;
        }

        return result == net.minecraft.util.ActionResult.SUCCESS || result == net.minecraft.util.ActionResult.CONSUME;
    }

    private void breakCrystal(MinecraftClient client, EndCrystalEntity crystal) {
        client.interactionManager.attackEntity(client.player, crystal);
        client.player.swingHand(Hand.MAIN_HAND);
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
        placeCooldown = 0;
        breakCooldown = 0;
    }
}