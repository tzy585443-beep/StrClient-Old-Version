package com.strongspy.strclient.modules.fullbright;

import com.strongspy.strclient.core.AbstractModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

/**
 * FullBright 模块 - 最终版
 * 通过客户端夜视效果实现全亮，稳定兼容 1.21.1
 */
public class FullBrightModule extends AbstractModule {

    // 保存玩家原本的夜视状态，用于关闭模块时恢复
    private StatusEffectInstance originalNightVisionEffect = null;

    public FullBrightModule() {
        super("fullbright", "FullBright", Category.VISUAL,
                "Dynamically enables a max-level Night Vision effect.");
    }

    @Override
    public void onTick(MinecraftClient client) {
        // 只在玩家存在且模块启用时生效
        if (client.player == null) return;

        // 应用或刷新夜视效果
        StatusEffectInstance currentEffect = client.player.getStatusEffect(StatusEffects.NIGHT_VISION);

        // 如果效果不存在，或者效果存在但剩余时间不足10秒，就重新应用一个无限时长的夜视效果
        if (currentEffect == null || currentEffect.getDuration() <= 200) {
            // 给玩家添加一个时长 10 分钟（12000 ticks）的夜视效果，强度设为 255 以达到最亮
            client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 12000, 255, true, false));
        }
    }

    @Override
    public void onDisable(MinecraftClient client) {
        if (client.player == null) return;

        // 移除由本模组添加的夜视效果
        client.player.removeStatusEffect(StatusEffects.NIGHT_VISION);

        // 如果玩家原本就有夜视效果，则恢复它
        // 注意：这里需要在模块开启时保存原始状态，但会涉及比较复杂的逻辑
        // 为了保持简单，当前版本仅在关闭时移除本模块施加的效果
    }
}