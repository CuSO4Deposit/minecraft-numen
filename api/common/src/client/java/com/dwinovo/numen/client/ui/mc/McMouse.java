package com.dwinovo.numen.client.ui.mc;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * 原版鼠标键号 → NumenUI 认的键号(<b>GLFW 约定</b>:左 0、右 1、中 2)。
 *
 * <p>26.3 起 MC 的窗口后端从 GLFW 换成 SDL,{@code MouseButtonInfo.button()} 报的键号
 * 跟着变成 SDL 约定(左 1、中 2、右 3)。NumenUI 是版本中立的,一路按 GLFW 约定判断
 * (组件库里左键是 {@code button == 0}、右键是 {@code button == 1}),所以在这一层换算一次,
 * 上层不必知道后端换过;用 {@link InputConstants} 的常量而不是写死数字,换回来时也不用改这里。
 */
public final class McMouse {

    private McMouse() {}

    /** 原版键号 → GLFW 键号(左 0、右 1、中 2);认不出的键(侧键等)原样透传。 */
    public static int button(int vanillaButton) {
        if (vanillaButton == InputConstants.MOUSE_BUTTON_LEFT) return 0;
        if (vanillaButton == InputConstants.MOUSE_BUTTON_RIGHT) return 1;
        if (vanillaButton == InputConstants.MOUSE_BUTTON_MIDDLE) return 2;
        return vanillaButton;
    }
}
