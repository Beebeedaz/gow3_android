package com.gow3.android.game.drawer;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import com.gow3.android.R;

/** Controls tab：屏幕控制器开关、编辑布局、重置布局。 */
final class ControlsPane implements DrawerPane {
    private final Context ctx;
    private final GameDrawerCallbacks cb;
    private final ScrollView scroll;
    private final DrawerRow controllerRow;

    ControlsPane(Context ctx, GameDrawerCallbacks cb) {
        this.ctx = ctx;
        this.cb = cb;
        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(ctx,14), dp(ctx,10), dp(ctx,14), dp(ctx,16));

        controllerRow = new DrawerRow(ctx, R.drawable.ic_gamepad, R.string.drawer_title_controller, null, cb::onToggleController);
        content.addView(controllerRow.view());

        content.addView(new DrawerRow(ctx, R.drawable.ic_edit, R.string.drawer_title_edit_layout, null,
            cb::onEditControllerLayout).view());
        content.addView(new DrawerRow(ctx, R.drawable.ic_restore, R.string.drawer_title_reset_layout, null,
            cb::onResetControllerLayout).view());

        scroll = new ScrollView(ctx);
        scroll.setFillViewport(false);
        scroll.addView(content, new FrameLayout.LayoutParams(-1, -2));
    }

    @Override public View content() { return scroll; }

    @Override public View firstFocusTarget() { return controllerRow.focusTarget(); }

    @Override public void refresh() {
        controllerRow.setDetail(cb.isControllerEnabled()
            ? ctx.getString(R.string.drawer_detail_on) : ctx.getString(R.string.drawer_detail_off));
    }

    private static int dp(Context ctx, float n) {
        return Math.round(n * ctx.getResources().getDisplayMetrics().density);
    }
}
