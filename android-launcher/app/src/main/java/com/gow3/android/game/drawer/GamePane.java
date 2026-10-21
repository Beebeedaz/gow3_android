package com.gow3.android.game.drawer;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import androidx.annotation.StringRes;
import com.gow3.android.R;

/** Game tab：继续游戏 / 重启游戏 / 退出游戏。重启与退出带确认对话框。 */
final class GamePane implements DrawerPane {
    private final ScrollView scroll;
    private final DrawerRow resumeRow;

    GamePane(Context ctx, GameDrawerCallbacks cb) {
        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(ctx,14), dp(ctx,10), dp(ctx,14), dp(ctx,16));

        resumeRow = new DrawerRow(ctx, R.drawable.ic_play, R.string.drawer_title_resume, null, cb::resumeGame);
        content.addView(resumeRow.view());

        content.addView(new DrawerRow(ctx, R.drawable.ic_refresh, R.string.drawer_title_restart,
            ctx.getString(R.string.drawer_detail_restart),
            () -> confirm(ctx, R.string.drawer_confirm_restart_title, R.string.drawer_confirm_restart_message,
                R.string.drawer_confirm_restart_ok, cb::requestRestart)).view());

        content.addView(new DrawerRow(ctx, R.drawable.ic_exit, R.string.drawer_title_quit, null,
            () -> confirm(ctx, R.string.drawer_confirm_quit_title, R.string.drawer_confirm_quit_message,
                R.string.drawer_confirm_quit_ok, cb::quitGame)).view());

        scroll = new ScrollView(ctx);
        scroll.setFillViewport(false);
        scroll.addView(content, new FrameLayout.LayoutParams(-1, -2));
    }

    @Override public View content() { return scroll; }

    @Override public View firstFocusTarget() { return resumeRow.focusTarget(); }

    @Override public void refresh() { /* Game tab 无动态摘要 */ }

    private static void confirm(Context ctx, @StringRes int title, @StringRes int message,
                                @StringRes int okLabel, Runnable action) {
        new AlertDialog.Builder(ctx)
            .setTitle(title).setMessage(message)
            .setNegativeButton(R.string.drawer_confirm_cancel, null)
            .setPositiveButton(okLabel, (d, w) -> action.run()).show();
    }

    private static int dp(Context ctx, float n) {
        return Math.round(n * ctx.getResources().getDisplayMetrics().density);
    }
}
