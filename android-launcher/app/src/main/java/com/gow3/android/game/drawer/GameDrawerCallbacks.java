package com.gow3.android.game.drawer;

import com.gow3.android.core.settings.FrameGenSettings;

/** GameDrawer 与 GameActivity 的解耦接口：抽屉面板只经此接口触发动作与读取当前值，
 *  GameActivity 不再被抽屉内部结构引用。 */
public interface GameDrawerCallbacks {
    void resumeGame();
    void requestRestart();
    void quitGame();
    void setFrameGenEnabled(boolean enabled);
    void setFrameGenMultiplier(int multiplier);
    void setFrameGenFlowScale(float flowScale);
    void onTogglePerfHud();
    void onToggleController();
    void onEditControllerLayout();
    void onResetControllerLayout();

    boolean isFrameGenDllPresent();
    FrameGenSettings.Config frameGenConfig();
    boolean isPerfHudEnabled();
    boolean isControllerEnabled();
}
