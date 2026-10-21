package com.gow3.android.game;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** PadBridge.physicalToken 纯逻辑的宿主单测（task 2.1）：物理输入链的 token 修正，
 *  解析为 touchpad 的物理输入一律以 touchpad_right 呈现（右侧点击开暂停菜单），
 *  其余 16 个 PS 按钮名与 touchpad_left/right 原样返回。无 Android 依赖。 */
public class PadBridgePhysicalTokenTest {

    @Test public void touchpadTranslatesToRightClick() {
        assertEquals("touchpad_right", PadBridge.physicalToken("touchpad"));
    }

    @Test public void otherButtonsPassThrough() {
        for (String button : PadMapping.PS_BUTTONS) {
            if (button.equals("touchpad")) continue;
            assertEquals("按钮 " + button + " 应原样返回", button, PadBridge.physicalToken(button));
        }
    }

    @Test public void touchpadLeftRightTokensPassThrough() {
        assertEquals("touchpad_left", PadBridge.physicalToken("touchpad_left"));
        assertEquals("touchpad_right", PadBridge.physicalToken("touchpad_right"));
    }

    @Test public void nullAndEmptyAreTolerated() {
        assertEquals(null, PadBridge.physicalToken(null));
        assertEquals("", PadBridge.physicalToken(""));
    }
}
