package com.gow3.android.game.drawer;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.gow3.android.R;
import java.util.HashMap;
import java.util.Map;

/** 游戏内抽屉外壳：抽屉 View 树、顶部 tab 栏、内容区切换、左滑动画与 scrim。
 *  GameActivity 只负责把 handle()/scrim()/view() 挂进屏幕并委托 open/close，不接触抽屉内部结构。 */
public final class GameDrawer {
    /** 关闭动画结束、抽屉已隐藏：宿主借此恢复游戏画面焦点。 */
    public interface Listener {
        void onDrawerClosed();
    }

    private static final int GOLD = 0xffc6ad76;
    private static final int LABEL_COLOR = 0xff8e9097;
    private static final int DRAWER_WIDTH_DP = 320;

    private final Context ctx;
    private final GameDrawerCallbacks callbacks;
    private final Listener listener;
    private final View handle;
    private final View scrim;
    private final LinearLayout drawer;
    private LinearLayout tabStrip;
    private FrameLayout contentHost;
    private final Map<DrawerTab, DrawerPane> panes = new HashMap<>();
    private final Map<DrawerTab, View> tabButtons = new HashMap<>();
    private DrawerTab activeTab = DrawerTab.GAME;
    private boolean open;

    public GameDrawer(Context ctx, GameDrawerCallbacks callbacks, Listener listener) {
        this.ctx = ctx;
        this.callbacks = callbacks;
        this.listener = listener;
        this.handle = buildHandle();
        this.scrim = buildScrim();
        this.drawer = buildDrawer();
        registerPane(new GamePane(ctx, callbacks));
        registerPane(new GraphicsPane(ctx, callbacks));
        registerPane(new ControlsPane(ctx, callbacks));
        selectTab(DrawerTab.GAME, false);
    }

    /** 左缘拉手（游戏侧），GameActivity 挂入屏幕并摆放。 */
    public View handle() { return handle; }

    /** 全屏遮罩，GameActivity 挂入屏幕并摆放。 */
    public View scrim() { return scrim; }

    /** 抽屉面板根视图，GameActivity 挂入屏幕并摆放。 */
    public View view() { return drawer; }

    public boolean isOpen() { return open; }

    private void registerPane(DrawerPane pane) {
        DrawerTab tab = tabOf(pane);
        panes.put(tab, pane);
    }

    private DrawerTab tabOf(DrawerPane pane) {
        if (pane instanceof GamePane) return DrawerTab.GAME;
        if (pane instanceof GraphicsPane) return DrawerTab.GRAPHICS;
        if (pane instanceof ControlsPane) return DrawerTab.CONTROLS;
        throw new IllegalArgumentException("unknown pane " + pane);
    }

    private View buildHandle() {
        View h = new View(ctx);
        GradientDrawable grip = new GradientDrawable();
        grip.setColor(0x80c6ad76); grip.setCornerRadius(dp(4));
        h.setBackground(grip);
        h.setContentDescription(ctx.getString(R.string.drawer_handle_desc));
        h.setOnClickListener(v -> open());
        return h;
    }

    private View buildScrim() {
        View s = new View(ctx);
        s.setBackgroundColor(0x99000000);
        s.setVisibility(View.GONE);
        s.setOnClickListener(v -> close());
        return s;
    }

    private LinearLayout buildDrawer() {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable panel = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] { 0xff1b1d24, 0xff121318 });
        root.setBackground(panel);
        root.setElevation(dp(16));
        root.setVisibility(View.GONE);

        // 顶部直接是 tab 栏（按用户要求与 WinNative 形态一致），操作提示移到抽屉底部。
        tabStrip = new LinearLayout(ctx);
        tabStrip.setOrientation(LinearLayout.HORIZONTAL);
        tabStrip.setPadding(dp(8), dp(6), dp(8), dp(6));
        root.addView(tabStrip, new LinearLayout.LayoutParams(-1, -2));
        buildTabStrip();

        View divider = new View(ctx);
        divider.setBackgroundColor(0x55c6ad76);
        root.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));

        contentHost = new FrameLayout(ctx);
        root.addView(contentHost, new LinearLayout.LayoutParams(-1, 0, 1));

        // 底部提示（原版本号位置）。
        TextView hint = new TextView(ctx);
        hint.setText(ctx.getString(R.string.drawer_hint_swipe));
        hint.setTextSize(12);
        hint.setTextColor(LABEL_COLOR);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(14), dp(10), dp(14), dp(14));
        root.addView(hint, new LinearLayout.LayoutParams(-1, -2));
        return root;
    }

    private void buildTabStrip() {
        for (DrawerTab tab : DrawerTab.values()) {
            DrawerTabSpec spec = DrawerTabSpec.of(tab);
            LinearLayout btn = new LinearLayout(ctx);
            btn.setOrientation(LinearLayout.HORIZONTAL);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(dp(8), dp(9), dp(8), dp(9));
            btn.setFocusable(true);
            btn.setClickable(true);
            btn.setBackground(tabButtonBackground());

            ImageView icon = new ImageView(ctx);
            icon.setImageResource(spec.iconRes);
            icon.setScaleType(ImageView.ScaleType.CENTER);
            btn.addView(icon, new LinearLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));

            TextView label = new TextView(ctx);
            label.setText(spec.labelRes);
            label.setTextSize(11);
            label.setTextColor(LABEL_COLOR);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setGravity(Gravity.CENTER);
            btn.addView(label, new LinearLayout.LayoutParams(-2, -2, Gravity.CENTER));

            btn.setOnClickListener(v -> selectTab(tab, true));
            tabButtons.put(tab, btn);
            tabStrip.addView(btn, new LinearLayout.LayoutParams(0, -2, 1f));
        }
    }

    private StateListDrawable tabButtonBackground() {
        StateListDrawable states = new StateListDrawable();
        GradientDrawable selected = new GradientDrawable();
        selected.setColor(0x26c6ad76); selected.setCornerRadius(dp(10));
        GradientDrawable lit = new GradientDrawable();
        lit.setColor(0x33c6ad76); lit.setCornerRadius(dp(10)); lit.setStroke(dp(1), 0x99c6ad76);
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(0x44c6ad76); pressed.setCornerRadius(dp(10));
        states.addState(new int[] { android.R.attr.state_pressed }, pressed);
        states.addState(new int[] { android.R.attr.state_selected }, selected);
        states.addState(new int[] { android.R.attr.state_focused }, lit);
        states.addState(new int[] {}, new ColorDrawable(0));
        return states;
    }

    /** 切换当前 tab：刷新 tab 选中态、换内容区、可选地把焦点移入新 tab 首行。 */
    private void selectTab(DrawerTab tab, boolean focus) {
        activeTab = tab;
        for (Map.Entry<DrawerTab, View> e : tabButtons.entrySet()) {
            boolean active = e.getKey() == tab;
            android.view.ViewGroup b = (android.view.ViewGroup) e.getValue();
            b.setSelected(active);
            if (b.getChildCount() > 1) {
                ((TextView) b.getChildAt(1)).setTextColor(active ? GOLD : LABEL_COLOR);
            }
        }
        DrawerPane pane = panes.get(tab);
        pane.refresh();
        contentHost.removeAllViews();
        contentHost.addView(pane.content());
        if (focus) {
            final DrawerPane p = pane;
            contentHost.post(() -> p.firstFocusTarget().requestFocus());
        }
    }

    /** 打开抽屉：释放由宿主完成，这里负责 scrim/拉手/面板动画与内容刷新。 */
    public void open() {
        if (open) return;
        open = true;
        for (DrawerPane pane : panes.values()) pane.refresh();
        handle.setVisibility(View.GONE);
        scrim.animate().cancel(); drawer.animate().cancel();
        scrim.setAlpha(0); scrim.setVisibility(View.VISIBLE);
        scrim.animate().alpha(1).setDuration(200).start();
        drawer.setTranslationX(-dp(DRAWER_WIDTH_DP));
        drawer.setVisibility(View.VISIBLE);
        drawer.animate().translationX(0).setDuration(220).start();
        // 动画结束后把焦点交给当前 tab 首行（手柄 A 确认语义沿用现有 dispatchKeyEvent）。
        drawer.postDelayed(() -> {
            if (open) panes.get(activeTab).firstFocusTarget().requestFocus();
        }, 240);
    }

    /** 关闭抽屉：scrim 淡出、面板滑出，结束后回调 Listener 供宿主恢复游戏画面焦点。 */
    public void close() {
        if (!open) return;
        open = false;
        scrim.animate().cancel(); drawer.animate().cancel();
        scrim.animate().alpha(0).setDuration(180)
            .withEndAction(() -> scrim.setVisibility(View.GONE)).start();
        drawer.animate().translationX(-dp(DRAWER_WIDTH_DP)).setDuration(200)
            .withEndAction(() -> {
                drawer.setVisibility(View.GONE);
                handle.setVisibility(View.VISIBLE);
                if (listener != null) listener.onDrawerClosed();
            }).start();
    }

    private int dp(float n) { return Math.round(n * ctx.getResources().getDisplayMetrics().density); }
}
