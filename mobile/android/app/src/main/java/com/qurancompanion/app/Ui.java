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

/**
 * Mushaf-inspired palette: Kaaba black, paper white, gold calligraphy, silver
 * hairlines and a restrained green accent. No imagery that could imply religious
 * authority.
 */
public final class Ui {
    public static final int INK = Color.parseColor("#0B0B0B");        // Kaaba black
    public static final int PAPER = Color.parseColor("#FFFFFF");      // mushaf paper
    public static final int MUTED = Color.parseColor("#6E6A5E");      // warm grey
    public static final int LINE = Color.parseColor("#E8E2D2");       // ivory/silver hairline
    public static final int SILVER = Color.parseColor("#C9C9C4");     // silver
    public static final int GOLD = Color.parseColor("#B8912F");       // calligraphy gold
    public static final int GOLD_DEEP = Color.parseColor("#8C6D1F");
    public static final int GOLD_SOFT = Color.parseColor("#F7F1E1");
    public static final int GREEN = Color.parseColor("#0E5A3A");      // restrained Kaaba green
    public static final int GREEN_SOFT = Color.parseColor("#EAF2EC");
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
        TextView view = text(context, value.toUpperCase(), 10f, GOLD_DEEP);
        view.setLetterSpacing(0.16f);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    /** Silver hairline with a gold inner stroke — the card frame used across the app. */
    public static GradientDrawable card(Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(PAPER);
        drawable.setCornerRadius(dp(context, 10));
        drawable.setStroke(dp(context, 1), LINE);
        return drawable;
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

    /** Primary = gold on black (calligraphic); secondary = black on paper. */
    public static Button button(Context context, String label, boolean primary) {
        Button button = new Button(context);
        button.setText(label);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        button.setAllCaps(false);
        button.setLetterSpacing(0.04f);
        button.setPadding(dp(context, 14), dp(context, 6), dp(context, 14), dp(context, 6));
        button.setMinHeight(dp(context, 40));
        if (primary) {
            button.setBackground(rounded(INK, GOLD, 4, context));
            button.setTextColor(GOLD);
        } else {
            button.setBackground(rounded(PAPER, LINE, 4, context));
            button.setTextColor(INK);
        }
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

    /** Thin gold rule used under the header and between sections. */
    public static View goldRule(Context context) {
        View view = new View(context);
        view.setBackgroundColor(GOLD);
        view.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 2)));
        return view;
    }

    public static LinearLayout.LayoutParams withTop(Context context, int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(context, topDp);
        return params;
    }
}
