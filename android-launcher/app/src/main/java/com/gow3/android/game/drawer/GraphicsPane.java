package com.gow3.android.game.drawer;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.gow3.android.R;
import com.gow3.android.core.settings.FrameGenSettings;

/** Graphics tab：帧生成（内联开关 + 自定义倍数/质量两行横排选项，就地调整即时生效，不弹二级对话框）、
 *  Performance HUD 开关。缺 Lossless.dll 时帧生成组置灰并显示导入指引。 */
final class GraphicsPane implements DrawerPane {
    /** 自定义倍数：与 run-gow3.sh 的 framegen_multiplier 取值一致。 */
    private static final String[] MULT_OPTIONS = { "2×", "3×", "4×" };
    private static final int[] MULT_VALUES = { 2, 3, 4 };
    /** 质量档位 → flow scale（都在 LSFG_FLOW_VALUES 白名单内，0.25–1.00）。 */
    private static final float[] QUALITY_FLOW = { 1.0f, 0.8f, 0.5f, 0.25f };
    private static final int[] QUALITY_LABELS = {
        R.string.drawer_quality_best, R.string.drawer_quality_high,
        R.string.drawer_quality_balanced, R.string.drawer_quality_fastest,
    };

    private final Context ctx;
    private final GameDrawerCallbacks cb;
    private final ScrollView scroll;
    private final DrawerRow frameGenRow;
    private final DrawerOptionsRow multRow;
    private final DrawerOptionsRow qualityRow;
    private final TextView note;
    private final DrawerRow perfHudRow;

    GraphicsPane(Context ctx, GameDrawerCallbacks cb) {
        this.ctx = ctx;
        this.cb = cb;
        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(ctx,14), dp(ctx,10), dp(ctx,14), dp(ctx,16));

        frameGenRow = new DrawerRow(ctx, R.drawable.ic_frames, R.string.drawer_title_frame_gen, null, () -> {
            if (!cb.isFrameGenDllPresent()) return;
            cb.setFrameGenEnabled(!cb.frameGenConfig().enabled);
            refresh();
        });
        content.addView(frameGenRow.view());

        multRow = new DrawerOptionsRow(ctx, R.string.drawer_title_frame_gen_multiplier,
            MULT_OPTIONS, 0, i -> {
                cb.setFrameGenMultiplier(MULT_VALUES[i]);
                refresh();
            });
        content.addView(multRow.view());

        String[] qualityOptions = new String[QUALITY_LABELS.length];
        for (int i = 0; i < QUALITY_LABELS.length; i++) qualityOptions[i] = ctx.getString(QUALITY_LABELS[i]);
        qualityRow = new DrawerOptionsRow(ctx, R.string.drawer_title_frame_gen_quality,
            qualityOptions, 0, i -> {
                cb.setFrameGenFlowScale(QUALITY_FLOW[i]);
                refresh();
            });
        content.addView(qualityRow.view());

        note = new TextView(ctx);
        note.setText(ctx.getString(R.string.drawer_note_framegen));
        note.setTextSize(12);
        note.setTextColor(0xff8e9097);
        note.setPadding(dp(ctx,14), dp(ctx,4), dp(ctx,14), dp(ctx,10));
        content.addView(note);

        perfHudRow = new DrawerRow(ctx, R.drawable.ic_tune, R.string.drawer_title_perf_hud, null, cb::onTogglePerfHud);
        content.addView(perfHudRow.view());

        scroll = new ScrollView(ctx);
        scroll.setFillViewport(false);
        scroll.addView(content, new FrameLayout.LayoutParams(-1, -2));
    }

    @Override public View content() { return scroll; }

    @Override public View firstFocusTarget() { return frameGenRow.focusTarget(); }

    @Override public void refresh() {
        FrameGenSettings.Config fg = cb.frameGenConfig();
        boolean dll = cb.isFrameGenDllPresent();
        frameGenRow.setEnabled(dll);
        frameGenRow.setDetail(dll
            ? (fg.enabled ? ctx.getString(R.string.drawer_detail_on) : ctx.getString(R.string.drawer_detail_off))
            : ctx.getString(R.string.drawer_detail_lossless_import));
        // 缺 DLL 时隐藏倍数/质量/说明；DLL 在但帧生成关闭时保留并置灰（布局高度稳定）。
        boolean showCtl = dll;
        multRow.view().setVisibility(showCtl ? View.VISIBLE : View.GONE);
        qualityRow.view().setVisibility(showCtl ? View.VISIBLE : View.GONE);
        note.setVisibility(showCtl ? View.VISIBLE : View.GONE);
        boolean live = dll && fg.enabled;
        multRow.setEnabled(live);
        qualityRow.setEnabled(live);
        multRow.setSelectedIndex(indexOf(MULT_VALUES, fg.multiplier, 0));
        qualityRow.setSelectedIndex(indexOfNearest(QUALITY_FLOW, fg.flowScale, 1));
        perfHudRow.setDetail(cb.isPerfHudEnabled()
            ? ctx.getString(R.string.drawer_detail_on) : ctx.getString(R.string.drawer_detail_off));
    }

    private static int indexOf(int[] values, int v, int fallback) {
        for (int i = 0; i < values.length; i++) if (values[i] == v) return i;
        return fallback;
    }

    /** flow scale 可能为白名单外的遗留值（如 0.75），就近收敛到最近的质量档。 */
    private static int indexOfNearest(float[] values, float v, int fallback) {
        int best = fallback;
        float bestDelta = Float.MAX_VALUE;
        for (int i = 0; i < values.length; i++) {
            float delta = Math.abs(values[i] - v);
            if (delta < bestDelta) { bestDelta = delta; best = i; }
        }
        return best;
    }

    private static int dp(Context ctx, float n) {
        return Math.round(n * ctx.getResources().getDisplayMetrics().density);
    }
}
