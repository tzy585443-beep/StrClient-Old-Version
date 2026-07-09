package com.strongspy.strclient.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.registry.tag.FluidTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BackgroundRenderer.class)
public class StrClientUnderwaterMixin {

    /**
     * Intercepts fog distance calculation to ensure complete clarity when submerged under water.
     * Rewritten to bypass specific mapping dependencies.
     */
    @Inject(method = "applyFog", at = @At("HEAD"), cancellable = true)
    private static void removeUnderwaterFog(Camera camera, BackgroundRenderer.FogType fogType, float viewDistance, boolean thickFog, float tickDelta, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();

        // 🎯 Safe alternative: Use FluidTags to detect if the player is under water,
        // which completely avoids using the unresolvable 'CameraSubmersionType' class.
        if (client.player != null && client.player.isSubmergedIn(FluidTags.WATER)) {
            ci.cancel(); // Cancel vanilla underwater thick fog rendering

            // Manually push fog bounds to maximum view distance to achieve perfect clarity
            RenderSystem.setShaderFogStart(viewDistance * 0.75F);
            RenderSystem.setShaderFogEnd(viewDistance);

            // Note: RenderSystem.setShaderFogShape line is removed to avoid 'com.mojang.blaze3d.shaders'
            // compilation issues across different mapping environments. It is not required for anti-fog.
        }
    }
}