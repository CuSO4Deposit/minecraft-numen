package com.dwinovo.numen.client.ui.mc;

/**
 * 原版按键事件 → NumenUI 认的键码(<b>GLFW 约定</b>)。与 {@link McMouse} 同类:26.3 起 MC 的
 * 窗口后端从 GLFW 换成 SDL,{@code KeyEvent.key()} 报的是 <b>SDL scancode</b>(回车是 40),
 * {@code modifiers()} 是 SDL KMOD;而 NumenUI 版本中立、一路按 GLFW 判断(回车 257、Esc 256、
 * 修饰位 SHIFT=1/CTRL=2/ALT=4)。在这一层换算一次。
 *
 * <p>只映射 NumenUI 真正会用到的键(导航/编辑/字母快捷键);用不到的键原样透传——组件库
 * 不会跟它们比较,透传值是多少都无所谓。
 */
public final class McKeys {

    private McKeys() {}

    /** SDL scancode → GLFW 键码。 */
    public static int key(int scancode) {
        // 字母 A..Z:SDL scancode 4..29 → GLFW 65..90
        if (scancode >= 4 && scancode <= 29) return 'A' + (scancode - 4);
        // 数字 1..9:30..38,0:39 → GLFW 49..57 / 48
        if (scancode >= 30 && scancode <= 38) return '1' + (scancode - 30);
        if (scancode == 39) return '0';
        return switch (scancode) {
            case 40, 88 -> 257;   // RETURN / KP_ENTER → GLFW ENTER
            case 41 -> 256;       // ESCAPE
            case 42 -> 259;       // BACKSPACE
            case 43 -> 258;       // TAB
            case 44 -> 32;        // SPACE
            case 74 -> 268;       // HOME
            case 76 -> 261;       // DELETE
            case 77 -> 269;       // END
            case 79 -> 262;       // RIGHT
            case 80 -> 263;       // LEFT
            case 81 -> 264;       // DOWN
            case 82 -> 265;       // UP
            default -> scancode;
        };
    }

    /** SDL KMOD → GLFW 修饰位(SHIFT=0x1、CTRL=0x2、ALT=0x4)。 */
    public static int modifiers(int sdlMods) {
        int m = 0;
        if ((sdlMods & 0x0003) != 0) m |= 0x1;   // KMOD_SHIFT
        if ((sdlMods & 0x00C0) != 0) m |= 0x2;   // KMOD_CTRL
        if ((sdlMods & 0x0300) != 0) m |= 0x4;   // KMOD_ALT
        return m;
    }
}
