package com.qurancompanion.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small programmatic-UI helpers. Black/white palette with restrained gold. */
public final class Ui {
    public static final int INK = Color.parseColor("#0C0C0C");
    public static final int PAPER = Color.parseColor("#FFFFFF");
    public static final int MUTED = Color.parseColor("#6A6A6A");
    public static final int LINE = Color.parseColor("#E7E7E7");
    public static final int GOLD = Color.parseColor("#A97C22");
    public static final int GOLD_SOFT = Color.parseColor("#F7EFDD");
    public static final int DANGER = Color.parseColor("#8C2B2B");

    private Ui() {}

    public static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics()));
    }

    public static TextView text(Context context, String value, float sizeSp, int color) {
        TextView view = new TextView(context);
        view.setText(value == null ? "" : value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.25f);
        return view;
    }

    public static TextView label(Context context, String value) {
        TextView view = text(context, value.toUpperCase(), 10f, MUTED);
        view.setLetterSpacing(0.12f);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    public static GradientDrawable rounded(int fill, int stroke, int radiusDp, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(context, radiusDp));
        if (stroke != 0) drawable.setStroke(dp(context, 1), stroke);
        return drawable;
    }

    public static LinearLayout column(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    public static LinearLayout row(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    public static Button button(Context context, String label, boolean primary) {
        Button button = new Button(context);
        button.setText(label);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        button.setAllCaps(false);
        button.setPadding(dp(context, 14), dp(context, 6), dp(context, 14), dp(context, 6));
        button.setMinHeight(dp(context, 40));
        button.setBackground(rounded(primary ? INK : PAPER, primary ? INK : LINE, 20, context));
        button.setTextColor(primary ? PAPER : INK);
        return button;
    }

    public static View divider(Context context) {
        View view = new View(context);
        view.setBackgroundColor(LINE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 1));
        params.topMargin = dp(context, 10);
        params.bottomMargin = dp(context, 10);
        view.setLayoutParams(params);
        return view;
    }

    public static LinearLayout.LayoutParams withTop(Context context, int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(context, topDp);
        return params;
    }
}
