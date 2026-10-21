package com.gow3.android.game.drawer;

import android.view.View;

/** 单个 tab 面板：构建自己的行集，打开抽屉时刷新摘要，并提供焦点入口。 */
public interface DrawerPane {
    /** 面板内容（ScrollView），交给 GameDrawer 的内容区摆放。 */
    View content();
    /** 该 tab 被选中后应获得焦点的首行。 */
    View firstFocusTarget();
    /** 打开抽屉/切到本 tab 时刷新摘要行。 */
    void refresh();
}
