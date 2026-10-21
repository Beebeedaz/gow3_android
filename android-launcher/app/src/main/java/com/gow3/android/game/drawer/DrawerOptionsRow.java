package com.gow3.android.game.drawer;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.StringRes;

/** 抽屉内的横向选项行：标题 + 一行等宽可选 chips（选中金边高亮，可获焦、可触摸）。
 *  与 WinNative 的 chip 选项形态一致，就地选择即生效，不弹二级对话框。 */
public final class DrawerOptionsRow {
    /** 选项回调：携带选中索引。 */
    public interface OptionListener {
        void onSelect(int index);
    }

    private static final int GOLD = 0xffc6ad76;
    private static final int LABEL_COLOR = 0xff8e9097;
    private static final int TEXT_COLOR = 0xffe8dfc9;

    private final Context ctx;
    private final LinearLayout root;
    private final TextView[] chips;
    private final OptionListener listener;
    private int selected;

    public DrawerOptionsRow(Context ctx, @StringRes int labelRes, String[] options,
                            int selectedIndex, OptionListener listener) {
        this.ctx = ctx;
        this.listener = listener;
        this.selected = selectedIndex;
        chips = new TextView[options.length];

        root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(4), dp(10), dp(4));

        TextView label = new TextView(ctx);
        label.setText(labelRes);
        label.setTextSize(12);
        label.setTextColor(LABEL_COLOR);
        label.setPadding(0, dp(2), 0, dp(6));
        root.addView(label, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout chipsRow = new LinearLayout(ctx);
        chipsRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < options.length; i++) {
            final int index = i;
            TextView chip = new TextView(ctx);
            chip.setText(options[i]);
            chip.setTextSize(13);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(4), dp(8), dp(4), dp(8));
            chip.setFocusable(true);
            chip.setClickable(true);
            chip.setBackground(chipBackground());
            chip.setOnClickListener(v -> select(index));
            chips[i] = chip;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i > 0) lp.leftMargin = dp(6);
            chipsRow.addView(chip, lp);
        }
        root.addView(chipsRow, new LinearLayout.LayoutParams(-1, -2));
        render();
    }

    public View view() { return root; }

    /** 把当前选中项同步到某索引（越界收敛到两端）。 */
    public void setSelectedIndex(int i) {
        selected = Math.max(0, Math.min(i, chips.length - 1));
        render();
    }

    /** 行不可用时：chips 不可点击、不可获焦（手柄跳过），并整体变灰。 */
    public void setEnabled(boolean on) {
        for (TextView c : chips) {
            c.setClickable(on);
            c.setFocusable(on);
        }
        root.setAlpha(on ? 1.0f : 0.4f);
    }

    private void select(int i) {
        selected = i;
        render();
        if (listener != null) listener.onSelect(i);
    }

    private void render() {
        for (int i = 0; i < chips.length; i++) {
            boolean active = i == selected;
            chips[i].setSelected(active);
            chips[i].setTextColor(active ? GOLD : TEXT_COLOR);
        }
    }

    private android.graphics.drawable.Drawable chipBackground() {
        StateListDrawable states = new StateListDrawable();
        GradientDrawable selected = new GradientDrawable();
        selected.setColor(0x26c6ad76); selected.setCornerRadius(dp(8)); selected.setStroke(dp(1), 0x99c6ad76);
        GradientDrawable lit = new GradientDrawable();
        lit.setColor(0x14c6ad76); lit.setCornerRadius(dp(8)); lit.setStroke(dp(1), 0x55c6ad76);
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(0x44c6ad76); pressed.setCornerRadius(dp(8));
        states.addState(new int[] { android.R.attr.state_pressed }, pressed);
        states.addState(new int[] { android.R.attr.state_selected }, selected);
        states.addState(new int[] { android.R.attr.state_focused }, lit);
        states.addState(new int[] {}, new ColorDrawable(0x1415171d));
        return states;
    }

    private int dp(float n) { return Math.round(n * ctx.getResources().getDisplayMetrics().density); }
}
