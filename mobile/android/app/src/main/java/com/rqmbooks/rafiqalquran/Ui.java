package com.rqmbooks.rafiqalquran;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
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
    // Mutable palette: jewel tones, pastel light and a rich nocturnal dark theme.
    public static int INK = Color.parseColor("#182B3A");
    public static int PAPER = Color.parseColor("#F8F7FF");
    public static int CARD = Color.parseColor("#FFFFFF");
    public static int MUTED = Color.parseColor("#62748A");
    public static int LINE = Color.parseColor("#DCE6F2");
    public static int SILVER = Color.parseColor("#B8C6D9");
    // The legacy token names are retained so older screens inherit the new accents.
    public static int GOLD = Color.parseColor("#E76F51");
    public static int GOLD_BRIGHT = Color.parseColor("#FFD166");
    public static int GOLD_DEEP = Color.parseColor("#C94F3D");
    public static int GOLD_SOFT = Color.parseColor("#FDEAE4");
    public static int GREEN = Color.parseColor("#0E7C86");
    public static int GREEN_SOFT = Color.parseColor("#DDF4F2");
    public static int DANGER = Color.parseColor("#D94A4A");
    public static int BAR = Color.parseColor("#0B5568");
    public static int BUBBLE = Color.parseColor("#E9F4FF");
    public static int ON_BAR = Color.parseColor("#FFFFFF");
    public static int TEAL = Color.parseColor("#0E7C86");
    public static int CORAL = Color.parseColor("#E76F51");
    public static int PURPLE = Color.parseColor("#7B5EA7");
    public static int AMBER = Color.parseColor("#F4A261");
    public static int PINK = Color.parseColor("#E5989B");
    public static int SKY = Color.parseColor("#5B8DEF");
    public static int MINT = Color.parseColor("#3DC9B0");
    public static int LAVENDER = Color.parseColor("#B79CFF");
    public static int SUN = Color.parseColor("#FFD166");

    private static boolean dark;
    private static Typeface amiri;

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
        window.setBackgroundDrawable(new ColorDrawable(PAPER));
        int flags = decor.getSystemUiVisibility();
        if (dark) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        else flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        if (!dark) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        decor.setSystemUiVisibility(flags);
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
            window.setStatusBarColor(BAR);
            window.setNavigationBarColor(dark ? BAR : PAPER);
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
            INK = Color.parseColor("#F6F8FF");
            PAPER = Color.parseColor("#0B1520");
            CARD = Color.parseColor("#142234");
            MUTED = Color.parseColor("#9CB0C8");
            LINE = Color.parseColor("#283A50");
            SILVER = Color.parseColor("#C4D2E6");
            GOLD = Color.parseColor("#FF8A6B");
            GOLD_BRIGHT = Color.parseColor("#FFD166");
            GOLD_DEEP = Color.parseColor("#FFB09B");
            GOLD_SOFT = Color.parseColor("#35232D");
            GREEN = Color.parseColor("#4ED6C7");
            GREEN_SOFT = Color.parseColor("#123B3C");
            DANGER = Color.parseColor("#FF8E8E");
            BAR = Color.parseColor("#082C3A");
            BUBBLE = Color.parseColor("#1A2C40");
            ON_BAR = Color.parseColor("#FFFFFF");
            TEAL = Color.parseColor("#4ED6C7");
            CORAL = Color.parseColor("#FF8A6B");
            PURPLE = Color.parseColor("#B79CFF");
            AMBER = Color.parseColor("#FFB86B");
            PINK = Color.parseColor("#F5A9B8");
            SKY = Color.parseColor("#7FB2FF");
            MINT = Color.parseColor("#63E6D6");
            LAVENDER = Color.parseColor("#CDB4FF");
            SUN = Color.parseColor("#FFD166");
        } else {
            INK = Color.parseColor("#182B3A");
            PAPER = Color.parseColor("#F8F7FF");
            CARD = Color.parseColor("#FFFFFF");
            MUTED = Color.parseColor("#62748A");
            LINE = Color.parseColor("#DCE6F2");
            SILVER = Color.parseColor("#B8C6D9");
            GOLD = Color.parseColor("#E76F51");
            GOLD_BRIGHT = Color.parseColor("#FFD166");
            GOLD_DEEP = Color.parseColor("#C94F3D");
            GOLD_SOFT = Color.parseColor("#FDEAE4");
            GREEN = Color.parseColor("#0E7C86");
            GREEN_SOFT = Color.parseColor("#DDF4F2");
            DANGER = Color.parseColor("#D94A4A");
            BAR = Color.parseColor("#0B5568");
            BUBBLE = Color.parseColor("#E9F4FF");
            ON_BAR = Color.parseColor("#FFFFFF");
            TEAL = Color.parseColor("#0E7C86");
            CORAL = Color.parseColor("#E76F51");
            PURPLE = Color.parseColor("#7B5EA7");
            AMBER = Color.parseColor("#F4A261");
            PINK = Color.parseColor("#E5989B");
            SKY = Color.parseColor("#5B8DEF");
            MINT = Color.parseColor("#3DC9B0");
            LAVENDER = Color.parseColor("#B79CFF");
            SUN = Color.parseColor("#FFD166");
        }
    }

    public static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics()));
    }

    /** Returns the same colour with a new alpha channel. */
    public static int alpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    public static TextView text(Context context, String value, float sizeSp, int color) {
        TextView view = new TextView(context);
        view.setText(value == null ? "" : value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color);
        view.setLineSpacing(dp(context, 3), 1.45f);
        view.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        return view;
    }

    /** Editorial display face used for screen titles and hero headings. */
    public static TextView display(Context context, String value, float sizeSp) {
        TextView view = text(context, value, sizeSp, INK);
        view.setTypeface(Typeface.create("serif", Typeface.BOLD));
        view.setLineSpacing(dp(context, 2), 1.05f);
        view.setLetterSpacing(-0.025f);
        return view;
    }

    /** Amiri gives the Arabic text a proper mushaf voice instead of a UI sans. */
    public static TextView arabic(Context context, String value, float sizeSp, int color) {
        TextView view = text(context, value, sizeSp, color);
        if (amiri == null) {
            try {
                amiri = Typeface.createFromAsset(context.getAssets(), "fonts/Amiri-Regular.ttf");
            } catch (Exception ignored) {
                amiri = Typeface.create("serif", Typeface.NORMAL);
            }
        }
        view.setTypeface(amiri);
        view.setLineSpacing(dp(context, 8), 1.55f);
        return view;
    }

    public static TextView label(Context context, String value) {
        TextView view = text(context, value, 12.5f, MUTED);
        view.setLetterSpacing(0.08f);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
    }

    public static TextView sectionLabel(Context context, String value) {
        TextView view = text(context, value, 14.5f, INK);
        view.setLetterSpacing(0.01f);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
    }

    /** Silver hairline card with a paper/card fill — the frame used across the app. */
    public static GradientDrawable card(Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(CARD);
        drawable.setCornerRadius(dp(context, 22));
        drawable.setStroke(dp(context, 1), LINE);
        return drawable;
    }

    public static LinearLayout cardBox(Context context) {
        LinearLayout box = column(context);
        box.setBackground(card(context));
        box.setElevation(dp(context, 1.5f));
        box.setPadding(dp(context, 20), dp(context, 18), dp(context, 20), dp(context, 18));
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
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        button.setAllCaps(false);
        button.setLetterSpacing(0.01f);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setPadding(dp(context, 22), dp(context, 12), dp(context, 22), dp(context, 12));
        button.setMinHeight(dp(context, 54));
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

    /** Thin multicolour rule used under the header and between sections. */
    public static View goldRule(Context context) {
        View view = new View(context);
        view.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{CORAL, AMBER, MINT, SKY, LAVENDER}));
        view.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 3)));
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
        TextView view = text(context, label, 14f, GOLD_DEEP);
        view.setLetterSpacing(0.01f);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setPadding(dp(context, 10), dp(context, 10), dp(context, 10), dp(context, 10));
        view.setGravity(Gravity.CENTER);
        if (onClick != null) view.setOnClickListener(v -> onClick.run());
        return view;
    }

    /** Plain green text action used under answers (Copy / Listen / More verses). */
    public static TextView textAction(Context context, String label, Runnable onClick) {
        TextView view = text(context, label, 13.5f, GREEN);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setPadding(dp(context, 8), dp(context, 9), dp(context, 8), dp(context, 9));
        if (onClick != null) view.setOnClickListener(v -> onClick.run());
        return view;
    }

    /** Round glyph button (composer send, header actions). */
    public static TextView circle(Context context, String glyph, int fill, int glyphColor, int sizeDp, Runnable onClick) {
        TextView view = text(context, glyph, 20f, glyphColor);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        view.setGravity(Gravity.CENTER);
        int size = dp(context, sizeDp);
        view.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        view.setBackground(rounded(fill, 0, sizeDp, context));
        if (onClick != null) view.setOnClickListener(v -> onClick.run());
        return view;
    }

    /** Small glyph shown inside the composer (attach / voice). */
    public static TextView glyph(Context context, String value, Runnable onClick) {
        TextView view = text(context, value, 24f, MUTED);
        view.setPadding(dp(context, 3), dp(context, 9), dp(context, 3), dp(context, 9));
        view.setGravity(Gravity.CENTER);
        if (onClick != null) view.setOnClickListener(v -> onClick.run());
        return view;
    }

    /** Moon/sun control that flips the light/dark theme. */
    public static TextView themeToggle(Activity activity) {
        TextView view = text(activity, dark ? "\u2600" : "\u263E", 26f, SUN);
        view.setPadding(dp(activity, 11), dp(activity, 4), dp(activity, 11), dp(activity, 4));
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
        bar.setBackground(headerBackground(activity));
        bar.setPadding(dp(activity, 10), dp(activity, 12), dp(activity, 8), dp(activity, 12));
        if (onBack != null) {
            TextView back = text(activity, "\u2190", 20f, SUN);
            back.setPadding(dp(activity, 8), 0, dp(activity, 12), 0);
            back.setContentDescription("Back");
            back.setOnClickListener(v -> onBack.run());
            bar.addView(back);
        }
        LinearLayout titleBox = column(activity);
        TextView heading = text(activity, title == null ? "" : title, 20f, ON_BAR);
        heading.setTypeface(Typeface.create("serif", Typeface.BOLD));
        heading.setLetterSpacing(-0.01f);
        heading.setSingleLine(true);
        heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleBox.addView(heading);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = text(activity, subtitle, 11.5f, SILVER);
            sub.setLetterSpacing(0.03f);
            sub.setSingleLine(true);
            sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
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

    /** Root container for a screen with the illustrated themed background. */
    public static LinearLayout screen(Activity activity) {
        LinearLayout root = column(activity);
        root.setBackground(pageBackground(activity));
        return root;
    }

    /** Illustrated page background: colour blooms and an Islamic star lattice. */
    public static Drawable pageBackground(Context context) {
        return context.getResources().getDrawable(
                dark ? R.drawable.bg_page_dark : R.drawable.bg_page_light, context.getTheme());
    }

    /** Rich teal → indigo → violet header, replacing the old black bar. */
    public static Drawable headerBackground(Context context) {
        return new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.parseColor("#0B6E7A"), Color.parseColor("#3A5FCD"),
                        Color.parseColor("#6C4AA8")});
    }

    /** A soft colour wash used behind the hero and listening banners. */
    public static Drawable heroBackground(Context context) {
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#0B6E7A"), Color.parseColor("#2C5BC4"),
                        Color.parseColor("#6C4AA8"), Color.parseColor("#D95D4E")});
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
