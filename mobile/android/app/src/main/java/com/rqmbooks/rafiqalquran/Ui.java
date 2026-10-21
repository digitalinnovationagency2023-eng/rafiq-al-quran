package com.rqmbooks.rafiqalquran;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Mushaf-inspired design system: Kaaba black, mushaf paper, gold calligraphy,
 * silver hairlines and a restrained green accent.
 *
 * Two themes share the same tokens. {@link #init(Activity)} must be called before
 * any view is built so the palette matches the stored preference; switching theme
 * recreates the activity.
 */
public final class Ui {
    // Mutable palette: values are set by init()/useTheme() for the active theme.
    public static int INK = Color.parseColor("#0B0B0B");        // primary text (light) / deep black field
    public static int PAPER = Color.parseColor("#FFFFFF");      // screen background
    public static int CARD = Color.parseColor("#FFFFFF");       // card fill
    public static int MUTED = Color.parseColor("#6E6A5E");      // warm grey
    public static int LINE = Color.parseColor("#E8E2D2");       // ivory/silver hairline
    public static int SILVER = Color.parseColor("#C9C9C4");     // silver
    public static int GOLD = Color.parseColor("#B8912F");       // calligraphy gold
    public static int GOLD_BRIGHT = Color.parseColor("#D4AF5A"); // gold on black
    public static int GOLD_DEEP = Color.parseColor("#8C6D1F");
    public static int GOLD_SOFT = Color.parseColor("#F7F1E1");
    public static int GREEN = Color.parseColor("#0E5A3A");      // restrained Kaaba green
    public static int GREEN_SOFT = Color.parseColor("#EAF2EC");
    public static int DANGER = Color.parseColor("#8C2B2B");
    public static int BAR = Color.parseColor("#0B0B0B");        // header bar
    public static int BUBBLE = Color.parseColor("#F4F4F4");     // user message bubble
    public static int ON_BAR = Color.parseColor("#FFFFFF");     // title text on the bar

    private static boolean dark;

    private Ui() {}

    /**
     * Applies the stored theme and system bar styling. Call first in onCreate.
     *
     * <p>Apps targeting Android 15+ (API 35+) run edge-to-edge by default, so from
     * API 30 we draw the window background behind the system bars and inset the
     * content ourselves (top/bottom bars stay deep black in both themes). Older
     * releases keep the platform's default "decor fits system windows" behaviour.
     */
    public static boolean init(Activity activity) {
        useTheme(new Prefs(activity).isDark());
        Window window = activity.getWindow();
        View decor = window.getDecorView();
        window.setBackgroundDrawable(new ColorDrawable(BAR));
        int flags = decor.getSystemUiVisibility();
        flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        decor.setSystemUiVisibility(flags);
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
            window.setStatusBarColor(BAR);
            window.setNavigationBarColor(BAR);
            decor.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                View content = view.findViewById(android.R.id.content);
                if (content != null) {
                    content.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                }
                return WindowInsets.CONSUMED;
            });
            decor.requestApplyInsets();
        } else {
            // No edge-to-edge enforcement before API 30: keep the platform inset
            // behaviour and only recolour the bars.
            window.setStatusBarColor(BAR);
            window.setNavigationBarColor(PAPER);
            if (!dark) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            decor.setSystemUiVisibility(flags);
        }
        return dark;
    }

    public static boolean isDark() { return dark; }

    /** Switches theme and recreates the activity so every view picks up the palette. */
    public static void toggleTheme(Activity activity) {
        new Prefs(activity).setTheme(dark ? "light" : "dark");
        activity.recreate();
    }

    public static void useTheme(boolean darkTheme) {
        dark = darkTheme;
        if (darkTheme) {
            INK = Color.parseColor("#ECECEC");        // primary text
            PAPER = Color.parseColor("#212121");      // chat field
            CARD = Color.parseColor("#2F2F2F");       // raised card
            MUTED = Color.parseColor("#9B9B9B");
            LINE = Color.parseColor("#3C3C3C");
            SILVER = Color.parseColor("#C5C5D2");
            GOLD = Color.parseColor("#D4AF5A");
            GOLD_BRIGHT = Color.parseColor("#D4AF5A");
            GOLD_DEEP = Color.parseColor("#C9A24B");
            GOLD_SOFT = Color.parseColor("#2B2517");
            GREEN = Color.parseColor("#10A37F");
            GREEN_SOFT = Color.parseColor("#16352B");
            DANGER = Color.parseColor("#D06A6A");
            BAR = Color.parseColor("#171717");
            BUBBLE = Color.parseColor("#2F2F2F");
            ON_BAR = Color.parseColor("#FFFFFF");
        } else {
            INK = Color.parseColor("#0D0D0D");
            PAPER = Color.parseColor("#FFFFFF");
            CARD = Color.parseColor("#FFFFFF");
            MUTED = Color.parseColor("#6E6E80");
            LINE = Color.parseColor("#E5E5E5");
            SILVER = Color.parseColor("#C5C5D2");
            GOLD = Color.parseColor("#B8912F");
            GOLD_BRIGHT = Color.parseColor("#D4AF5A");
            GOLD_DEEP = Color.parseColor("#8C6D1F");
            GOLD_SOFT = Color.parseColor("#FAF6E9");
            GREEN = Color.parseColor("#10A37F");
            GREEN_SOFT = Color.parseColor("#ECFDF5");
            DANGER = Color.parseColor("#B42318");
            BAR = Color.parseColor("#0D0D0D");
            BUBBLE = Color.parseColor("#F4F4F4");
            ON_BAR = Color.parseColor("#FFFFFF");
        }
    }

    public static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics()));
    }

    public static TextView text(Context context, String value, float sizeSp, int color) {
        TextView view = new TextView(context);
        view.setText(value == null ? "" : value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.35f);
        view.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        return view;
    }

    public static TextView label(Context context, String value) {
        TextView view = text(context, value, 11f, MUTED);
        view.setLetterSpacing(0.02f);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
    }

    public static TextView sectionLabel(Context context, String value) {
        TextView view = text(context, value, 12f, INK);
        view.setLetterSpacing(0.01f);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
    }

    /** Silver hairline card with a paper/card fill — the frame used across the app. */
    public static GradientDrawable card(Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(CARD);
        drawable.setCornerRadius(dp(context, 16));
        drawable.setStroke(dp(context, 1), LINE);
        return drawable;
    }

    public static LinearLayout cardBox(Context context) {
        LinearLayout box = column(context);
        box.setBackground(card(context));
        box.setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14));
        return box;
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

    public static View spacer(Context context) {
        // android.widget.Space measures to zero under AT_MOST; a plain View would
        // take the full available height and break surrounding layouts.
        View view = new android.widget.Space(context);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                dp(context, 1), ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return view;
    }

    /** Primary = gold on black (calligraphic); secondary = black on paper. */
    public static Button button(Context context, String label, boolean primary) {
        Button button = new Button(context);
        button.setText(label);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        button.setAllCaps(false);
        button.setLetterSpacing(0.01f);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setPadding(dp(context, 18), dp(context, 8), dp(context, 18), dp(context, 8));
        button.setMinHeight(dp(context, 42));
        if (primary) {
            button.setBackground(rounded(GREEN, 0, 24, context));
            button.setTextColor(Color.WHITE);
        } else {
            button.setBackground(rounded(CARD, LINE, 24, context));
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

    // ------------------------------------------------------------------ chrome
    /** Small gold text action used in header bars and cards. */
    public static TextView action(Context context, String label, Runnable onClick) {
        TextView view = text(context, label, 12f, GREEN);
        view.setLetterSpacing(0.01f);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8));
        view.setGravity(Gravity.CENTER);
        if (onClick != null) view.setOnClickListener(v -> onClick.run());
        return view;
    }

    /** Plain green text action used under answers (Copy / Listen / More verses). */
    public static TextView textAction(Context context, String label, Runnable onClick) {
        TextView view = text(context, label, 12.5f, GREEN);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8));
        if (onClick != null) view.setOnClickListener(v -> onClick.run());
        return view;
    }

    /** Round glyph button (composer send, header actions). */
    public static TextView circle(Context context, String glyph, int fill, int glyphColor, int sizeDp, Runnable onClick) {
        TextView view = text(context, glyph, 18f, glyphColor);
        view.setGravity(Gravity.CENTER);
        int size = dp(context, sizeDp);
        view.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        view.setBackground(rounded(fill, 0, sizeDp, context));
        if (onClick != null) view.setOnClickListener(v -> onClick.run());
        return view;
    }

    /** Small glyph shown inside the composer (attach / voice). */
    public static TextView glyph(Context context, String value, Runnable onClick) {
        TextView view = text(context, value, 18f, MUTED);
        view.setPadding(dp(context, 10), dp(context, 10), dp(context, 10), dp(context, 10));
        view.setGravity(Gravity.CENTER);
        if (onClick != null) view.setOnClickListener(v -> onClick.run());
        return view;
    }

    /** Moon/sun control that flips the light/dark theme. */
    public static TextView themeToggle(Activity activity) {
        TextView view = text(activity, dark ? "\u2600" : "\u263E", 18f, GOLD_BRIGHT);
        view.setPadding(dp(activity, 10), dp(activity, 4), dp(activity, 10), dp(activity, 4));
        view.setContentDescription(dark ? "Switch to light theme" : "Switch to dark theme");
        view.setOnClickListener(v -> toggleTheme(activity));
        return view;
    }

    /**
     * Standard header: back arrow, serif gold title, small silver subtitle, a
     * flexible spacer, then whatever actions the caller appends to the returned
     * row. The caller is expected to add a gold rule below it.
     */
    public static LinearLayout bar(Activity activity, String title, String subtitle, Runnable onBack) {
        LinearLayout bar = row(activity);
        bar.setBackgroundColor(BAR);
        bar.setPadding(dp(activity, 10), dp(activity, 10), dp(activity, 8), dp(activity, 10));
        if (onBack != null) {
            TextView back = text(activity, "\u2190", 20f, GOLD_BRIGHT);
            back.setPadding(dp(activity, 8), 0, dp(activity, 12), 0);
            back.setContentDescription("Back");
            back.setOnClickListener(v -> onBack.run());
            bar.addView(back);
        }
        LinearLayout titleBox = column(activity);
        TextView heading = text(activity, title == null ? "" : title, 16f, ON_BAR);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.setLetterSpacing(0.01f);
        titleBox.addView(heading);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = text(activity, subtitle, 10f, SILVER);
            sub.setLetterSpacing(0.02f);
            sub.setPadding(0, dp(activity, 2), 0, 0);
            titleBox.addView(sub);
        }
        // The title column takes the leftover width so the caller's actions stay
        // right-aligned without a weighted spacer (a weighted empty view inside a
        // horizontal LinearLayout stretches its own height on some Android builds).
        LinearLayout.LayoutParams titleBoxParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bar.addView(titleBox, titleBoxParams);
        return bar;
    }

    /** Root container for a screen with the themed background. */
    public static LinearLayout screen(Activity activity) {
        LinearLayout root = column(activity);
        root.setBackgroundColor(PAPER);
        return root;
    }

    /** Small pill used for statuses and flags. */
    public static TextView chip(Context context, String text, int textColor, int fill, int stroke) {
        TextView view = text(context, text, 11f, textColor);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setPadding(dp(context, 10), dp(context, 4), dp(context, 10), dp(context, 4));
        view.setBackground(rounded(fill, stroke, 20, context));
        return view;
    }
}
