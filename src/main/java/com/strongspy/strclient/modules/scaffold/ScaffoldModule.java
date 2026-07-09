package com.strongspy.strclient.modules.scaffold;

import com.strongspy.strclient.core.AbstractModule;
import com.strongspy.strclient.core.ModuleSetting;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class ScaffoldModule extends AbstractModule {

    // 用于自动转身的状态
    private boolean turnPending = false;
    private int turnCooldown = 0;

    public ScaffoldModule() {
        super("scaffold", "Scaffold", Category.MOVEMENT,
                "Automatically places blocks beneath you while moving");
    }

    @Override
    protected void registerSettings() {
        registerSetting(ModuleSetting.ofBoolean(
                "tower", "Tower", "Hold jump to tower up", false));
        registerSetting(ModuleSetting.ofBoolean(
                "autoSwitch", "Auto Switch", "Switch to a block in hotbar automatically", true));
        registerSetting(ModuleSetting.ofInt(
                "aggressiveness", "Aggressiveness",
                "Higher = faster bridging (1-10)", 5, 1, 10));
        registerSetting(ModuleSetting.ofBoolean(
                "autoSneak", "Auto Sneak on Edge",
                "Automatically sneak when approaching block edges", true));
        registerSetting(ModuleSetting.ofBoolean(
                "autoTurn", "Auto Turn at Edge",
                "Automatically turn 180° at edge (camera stays)", false));
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        ClientPlayerEntity player = client.player;

        // ===== Auto Sneak on Edge (改进版) =====
        if (getBoolean("autoSneak")) {
            boolean shouldSneak = isAtEdgeImproved(player);
            player.setSneaking(shouldSneak);
        }

        // ===== Auto Turn at Edge =====
        if (getBoolean("autoTurn")) {
            if (turnCooldown > 0) {
                turnCooldown--;
            }
            if (!turnPending && isAtEdgeImproved(player) && turnCooldown <= 0) {
                // 触发转身：发送180度旋转数据包，本地视角不变
                float currentYaw = player.getYaw();
                float newYaw = currentYaw + 180f;
                // 发送伪造旋转包
                if (player.networkHandler != null) {
                    player.networkHandler.sendPacket(
                            new PlayerMoveC2SPacket.LookAndOnGround(newYaw, player.getPitch(), player.isOnGround())
                    );
                    // 不修改本地视角，保持屏幕不动
                }
                // 标记冷却，防止连续触发
                turnCooldown = 10; // 10 tick 冷却
                turnPending = true;
            }
            // 如果玩家移动了一定距离，重置 pending 状态，允许下次触发
            if (turnPending && (Math.abs(player.getVelocity().x) > 0.1 || Math.abs(player.getVelocity().z) > 0.1)) {
                turnPending = false;
            }
        }

        // Tower 模式
        if (getBoolean("tower") && client.options.jumpKey.isPressed()) {
            placeAt(client, player.getBlockPos().down());
            return;
        }

        boolean moving = client.options.forwardKey.isPressed()
                || client.options.backKey.isPressed()
                || client.options.leftKey.isPressed()
                || client.options.rightKey.isPressed();

        if (!moving && player.isOnGround()) return;

        // 基础：脚下放块
        placeAt(client, player.getBlockPos().down());

        // 根据激进程度决定预判步数和距离
        int aggressiveness = getInt("aggressiveness");
        double baseSteps = Math.max(1, aggressiveness / 2); // 1~5
        double baseDistance = Math.max(0.3, aggressiveness * 0.1); // 0.3~1.0

        boolean airborne = !player.isOnGround();
        Vec3d pos = player.getPos();

        float yaw = player.getYaw();
        float yawRad = yaw * MathHelper.RADIANS_PER_DEGREE;
        double facingX = -MathHelper.sin(yawRad);
        double facingZ = MathHelper.cos(yawRad);

        Vec3d vel = player.getVelocity();
        double dx = airborne ? (facingX * 0.6 + vel.x * 0.4) : vel.x;
        double dz = airborne ? (facingZ * 0.6 + vel.z * 0.4) : vel.z;

        int stepCount = (int) baseSteps + (airborne ? 2 : 0);
        double maxDistance = baseDistance * (airborne ? 2.5 : 1.5);

        for (int i = 1; i <= stepCount; i++) {
            double scale = (i / (double) stepCount) * maxDistance;
            Vec3d lookahead = pos.add(dx * scale * 2, 0, dz * scale * 2);
            BlockPos target = BlockPos.ofFloored(lookahead).down();
            if (target.isWithinDistance(player.getPos(), 5.0)) {
                placeAt(client, target);
            }
        }
    }

    private void placeAt(MinecraftClient client, BlockPos below) {
        if (client.player == null || client.world == null) return;

        BlockState state = client.world.getBlockState(below);
        if (!state.isAir() && !state.isReplaceable()) return;

        int slot = findBlockSlot(client);
        if (slot == -1) return;

        int prevSlot = client.player.getInventory().selectedSlot;
        boolean switched = false;
        if (getBoolean("autoSwitch") && slot != prevSlot) {
            client.player.getInventory().selectedSlot = slot;
            switched = true;
        }

        boolean wasSneaking = client.player.isSneaking();
        client.player.setSneaking(true);

        BlockHitResult hit = getPlacementHit(client, below);
        if (hit != null) {
            client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hit);
            client.player.swingHand(Hand.MAIN_HAND);
        }

        client.player.setSneaking(wasSneaking);

        if (switched) client.player.getInventory().selectedSlot = prevSlot;
    }

    private BlockHitResult getPlacementHit(MinecraftClient client, BlockPos target) {
        BlockPos support = target.down();
        BlockState supportState = client.world.getBlockState(support);
        if (!supportState.isAir() && !supportState.isReplaceable()) {
            Vec3d hitVec = new Vec3d(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
            return new BlockHitResult(hitVec, Direction.UP, support, false);
        }

        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos neighbor = target.offset(dir);
            BlockState ns = client.world.getBlockState(neighbor);
            if (!ns.isAir() && !ns.isReplaceable()) {
                Vec3d hitVec = Vec3d.ofCenter(neighbor);
                return new BlockHitResult(hitVec, dir.getOpposite(), neighbor, false);
            }
        }

        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos sameLevel = target.offset(dir).up();
            BlockState ns = client.world.getBlockState(sameLevel);
            if (!ns.isAir() && !ns.isReplaceable()) {
                Vec3d hitVec = Vec3d.ofCenter(sameLevel);
                return new BlockHitResult(hitVec, dir.getOpposite(), sameLevel, false);
            }
        }

        return null;
    }

    private int findBlockSlot(MinecraftClient client) {
        if (client.player.getMainHandStack().getItem() instanceof BlockItem) {
            return client.player.getInventory().selectedSlot;
        }
        for (int i = 0; i < 9; i++) {
            if (client.player.getInventory().getStack(i).getItem() instanceof BlockItem) return i;
        }
        return -1;
    }

    // ===== 改进的边缘检测 =====
    private boolean isAtEdgeImproved(ClientPlayerEntity player) {
        // 获取玩家脚部所在方块
        BlockPos feetPos = player.getBlockPos().down();
        // 检查脚下是否为固体方块
        if (!player.getWorld().getBlockState(feetPos).isSolid()) {
            return false;
        }

        // 获取水平速度方向
        Vec3d velocity = player.getVelocity();
        double velX = velocity.x;
        double velZ = velocity.z;
        // 如果几乎静止，不触发
        if (Math.abs(velX) < 0.01 && Math.abs(velZ) < 0.01) {
            return false;
        }

        // 归一化方向向量
        double len = Math.sqrt(velX * velX + velZ * velZ);
        double dx = velX / len;
        double dz = velZ / len;

        // 检查前方 1~2 格是否为空（空气或可替换）
        for (int step = 1; step <= 2; step++) {
            BlockPos frontPos = new BlockPos(
                    feetPos.getX() + (int) Math.round(dx * step),
                    feetPos.getY(),
                    feetPos.getZ() + (int) Math.round(dz * step)
            );
            BlockState frontState = player.getWorld().getBlockState(frontPos);
            if (!frontState.isAir() && !frontState.isReplaceable()) {
                return false; // 有方块阻挡，不是边缘
            }
        }

        // 检查前下方是否有支撑（用于台阶等）
        BlockPos frontBelow = new BlockPos(
                feetPos.getX() + (int) Math.round(dx * 1),
                feetPos.getY() - 1,
                feetPos.getZ() + (int) Math.round(dz * 1)
        );
        if (!player.getWorld().getBlockState(frontBelow).isAir()) {
            // 前下方有方块，不是边缘
            return false;
        }

        // 如果前面是空的且前下方也是空的，则是边缘
        return true;
    }
}