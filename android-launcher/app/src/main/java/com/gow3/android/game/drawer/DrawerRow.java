package com.gow3.android.game.drawer;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/** 抽屉菜单行：icon + 标题 + 可选副标题（摘要），可整体置灰禁用，支持手柄焦点/按下金边高亮。
 *  行为与旧 GameActivity.menuItem()/setDetail()/setRowEnabled() 一致，标题与摘要统一走字符串资源。 */
public final class DrawerRow {
    private static final int TITLE_COLOR = 0xffe8dfc9;
    private static final int DETAIL_COLOR = 0xff8e9097;

    private final Context ctx;
    private final LinearLayout row;
    private final LinearLayout texts;
    private TextView detail;

    public DrawerRow(Context ctx, @DrawableRes int iconRes, @StringRes int titleRes,
                     @Nullable String detailText, Runnable action) {
        this.ctx = ctx;
        row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setFocusable(true);
        row.setClickable(true);
        row.setOnClickListener(v -> { if (action != null) action.run(); });
        row.setMinimumHeight(dp(48));
        row.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        ((LinearLayout.LayoutParams) row.getLayoutParams()).bottomMargin = dp(2);
        row.setBackground(rowBackground());

        if (iconRes != 0) {
            ImageView image = new ImageView(ctx);
            image.setImageResource(iconRes);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(24), dp(24));
            ip.rightMargin = dp(14);
            row.addView(image, ip);
        }

        texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(ctx);
        name.setText(titleRes);
        name.setTextSize(15);
        name.setTextColor(TITLE_COLOR);
        texts.addView(name);
        if (detailText != null) detail = appendDetail(detailText);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
    }

    /** 行视图（交给父容器摆放）。 */
    public View view() { return row; }

    /** 手柄/触摸聚焦与执行的目标视图。 */
    public View focusTarget() { return row; }

    /** 更新副标题（摘要）；构造时 detailText 为 null 的行在这里补上副标题。 */
    public void setDetail(String value) {
        if (detail == null) detail = appendDetail(value);
        else detail.setText(value);
    }

    /** 行不可用时：不可点击、不可获焦（手柄导航跳过），并整体变灰。 */
    public void setEnabled(boolean on) {
        row.setClickable(on);
        row.setFocusable(on);
        row.setAlpha(on ? 1.0f : 0.4f);
    }

    private TextView appendDetail(String value) {
        TextView second = new TextView(ctx);
        second.setText(value);
        second.setTextSize(12);
        second.setTextColor(DETAIL_COLOR);
        texts.addView(second);
        return second;
    }

    private android.graphics.drawable.Drawable rowBackground() {
        StateListDrawable states = new StateListDrawable();
        GradientDrawable lit = new GradientDrawable();
        lit.setColor(0x33c6ad76); lit.setCornerRadius(dp(10)); lit.setStroke(dp(1), 0x99c6ad76);
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(0x44c6ad76); pressed.setCornerRadius(dp(10));
        states.addState(new int[] { android.R.attr.state_pressed }, pressed);
        states.addState(new int[] { android.R.attr.state_focused }, lit);
        states.addState(new int[] {}, new ColorDrawable(0));
        return states;
    }

    private int dp(float n) { return Math.round(n * ctx.getResources().getDisplayMetrics().density); }
}
