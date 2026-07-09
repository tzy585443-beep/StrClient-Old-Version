package com.strongspy.strclient.mixin;

import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin extends Screen {

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    /**
     * 在主菜单初始化完毕后拦截
     */
    @Inject(method = "init", at = @At("TAIL"))
    private void onInitTail(CallbackInfo ci) {
        // 🎯 终极修复：改用绝对公开且通用的 children() 方法获取所有元素
        for (Element element : this.children()) {
            // 筛选出 ClickableWidget 类型的控件（即所有按钮）
            if (element instanceof ClickableWidget widget) {
                Text message = widget.getMessage();

                if (message != null) {
                    // 🔍 匹配 Realms 按钮的语言 Key ("menu.online")
                    if (message.getString().equals(Text.translatable("menu.online").getString())) {
                        // 🪄 隐藏并禁用该按钮
                        widget.visible = false;
                        widget.active = false;

                        // 🪄 扔出屏幕双重保险
                        widget.setX(-9999);
                    }
                }
            }
        }
    }
}