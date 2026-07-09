package com.strongspy.strclient.modules.autosteal;

import com.strongspy.strclient.core.AbstractModule;
import com.strongspy.strclient.core.ModuleSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.inventory.Inventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.collection.DefaultedList;

public class AutoStealModule extends AbstractModule {

    private int stealIndex = 0;        // 当前要偷取的槽位索引
    private int stealDelay = 0;         // 延迟计数器

    public AutoStealModule() {
        super("autosteal", "Auto Steal", Category.UTILITY,
                "Automatically take all items from a container when you open it.");
    }

    @Override
    protected void registerSettings() {
        // 每个物品取出后的延迟 tick（0 = 无延迟，建议 1-2 避免被踢）
        registerSetting(ModuleSetting.ofInt(
                "itemDelay", "Item Delay (ticks)",
                "Delay between taking each item (0-10)",
                1, 0, 10
        ));
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.interactionManager == null) return;

        ScreenHandler handler = client.player.currentScreenHandler;
        // 必须是箱子、发射器等容器的界面（GenericContainerScreenHandler）
        if (!(handler instanceof GenericContainerScreenHandler container)) return;

        // 延迟处理
        if (stealDelay > 0) {
            stealDelay--;
            return;
        }

        int delaySetting = getInt("itemDelay");

        // 获取容器的库存（不包括玩家背包）
        Inventory containerInv = container.getInventory();
        int containerSlots = containerInv.size();   // 容器槽位数（例如箱子 27）

        // 如果已经偷完了所有容器槽位，则结束
        if (stealIndex >= containerSlots) {
            // 自动关闭界面（可选）
            closeScreenIfEmpty(client, container);
            reset();
            return;
        }

        // 遍历容器槽位，找到第一个非空物品
        for (int i = stealIndex; i < containerSlots; i++) {
            if (!containerInv.getStack(i).isEmpty()) {
                // 点击该槽位，取出物品（SlotActionType.QUICK_MOVE 相当于 Shift+左键）
                client.interactionManager.clickSlot(
                        handler.syncId,
                        i,                          // 槽位索引
                        0,                          // 按钮
                        SlotActionType.QUICK_MOVE,  // 快速移动至玩家背包
                        client.player
                );
                // 记录下一个要检查的槽位（注意：取出后物品可能变为空，但下一次 tick 再检查）
                stealIndex = i;
                stealDelay = delaySetting;
                return;
            }
        }

        // 所有槽位都为空，完成
        closeScreenIfEmpty(client, container);
        reset();
    }

    /**
     * 当所有物品都已取出时，关闭容器界面
     */
    private void closeScreenIfEmpty(MinecraftClient client, GenericContainerScreenHandler handler) {
        // 检查容器是否真的已空（可选）
        Inventory inv = handler.getInventory();
        boolean allEmpty = true;
        for (int i = 0; i < inv.size(); i++) {
            if (!inv.getStack(i).isEmpty()) {
                allEmpty = false;
                break;
            }
        }
        if (allEmpty && client.player != null) {
            client.player.closeHandledScreen();
        }
    }

    private void reset() {
        stealIndex = 0;
        stealDelay = 0;
    }

    @Override
    public void onDisable(MinecraftClient client) {
        reset();
    }
}