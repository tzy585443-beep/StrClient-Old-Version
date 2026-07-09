package com.strongspy.strclient.mixin;

import com.strongspy.strclient.modules.capepatch.CapePatchModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public class CapePatchMixin {

    @Inject(
            method = "getSkinTextures",
            at = @At("RETURN"),
            cancellable = true
    )
    private void injectCustomCape(CallbackInfoReturnable<SkinTextures> cir) {
        // 1. 检查模块是否开启，以及贴图是否准备好
        if (!CapePatchModule.ACTIVE) return;
        if (CapePatchModule.CAPE_TEXTURE == null) return;

        // 2. 获取当前的玩家实体
        AbstractClientPlayerEntity player = (AbstractClientPlayerEntity) (Object) this;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        // 3. 这里的 UUID 判断决定了【谁能看到这件披风】：
        // 如果保留这行：只有【你自己】的身后会挂上这件专属玻璃披风。
        // 如果删掉这行：所有装了这个模组的玩家，只要进服，互相之间看对方都会挂着这件披风。
        if (!player.getUuid().equals(client.player.getUuid())) return;

        // 4. 获取原版的皮肤数据
        SkinTextures original = cir.getReturnValue();
        if (original != null) {
            // 5. 核心：因为 SkinTextures 是不可变的 Record，我们需要新建一个，把原版参数抄过去，只替换披风项
            SkinTextures customTextures = new SkinTextures(
                    original.texture(),       // 保持原版皮肤不变
                    original.textureUrl(),    // 保持皮肤URL不变
                    CapePatchModule.CAPE_TEXTURE, // ✨ 强行塞入你的 StrClient 专属披风贴图！
                    original.elytraTexture(), // 保持原版鞘翅不变
                    original.model(),         // 保持玩家模型（胖/瘦手臂）不变
                    original.secure()         // 保持安全检查状态
            );

            // 6. 覆写返回值
            cir.setReturnValue(customTextures);
        }
    }
}