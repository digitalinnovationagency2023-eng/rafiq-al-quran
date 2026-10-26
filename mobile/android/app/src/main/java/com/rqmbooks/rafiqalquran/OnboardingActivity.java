package com.rqmbooks.rafiqalquran;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Three short cards shown once, immediately after the first Bismillah screen. */
public final class OnboardingActivity extends Activity {
    private static final int GOLD = Color.rgb(212, 175, 90);
    private static final int WHITE = Color.rgb(245, 247, 245);
    private static final int BODY = Color.rgb(210, 224, 216);
    private static final int MUTED = Color.rgb(150, 170, 160);
    private static final int DARK = Color.rgb(3, 10, 7);

    private static final String[] ICONS = {"\u2726", "\u201C", "\u25C9"};
    private static final String[] HEADINGS = {"Ask naturally", "Quran-only answers", "Talk and listen"};
    private static final String[] BODIES = {
            "Ask in English, Roman Urdu or Arabic \u2014 even with typos. The app works out the question, then searches the Quran for the most relevant verses.",
            "Every answer is grounded in Quran verses with clear references. No tafsir, hadith or fatwa, and Quran text is never invented.",
            "Tap the voice-chat icon, speak your question, and hear the answer read aloud. It listens again for the next question, hands-free."
    };

    private Prefs prefs;
    private int page;
    private TextView icon;
    private TextView heading;
    private TextView body;
    private TextView step;
    private TextView back;
    private TextView next;
    private LinearLayout dots;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        getWindow().setStatusBarColor(DARK);
        getWindow().setNavigationBarColor(DARK);
        setContentView(build());
        render(false);
    }

    private View build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(DARK);
        root.addView(new IntroArt(this), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(22), dp(22), 0);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = text("Rafiq Al-Quran", 16f, WHITE);
        brand.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        header.addView(brand, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView skip = text("Skip", 13f, MUTED);
        skip.setPadding(dp(12), dp(10), dp(4), dp(10));
        skip.setOnClickListener(view -> finishIntro());
        header.addView(skip);
        content.addView(header);

        View spacer = new View(this);
        content.addView(spacer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(22), dp(20), dp(20));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.argb(216, 5, 24, 17));
        cardBg.setCornerRadius(dp(24));
        cardBg.setStroke(dp(1), Color.argb(80, 212, 175, 90));
        card.setBackground(cardBg);

        icon = text(ICONS[0], 38f, GOLD);
        icon.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        card.addView(icon);

        heading = text(HEADINGS[0], 22f, WHITE);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.setPadding(0, dp(8), 0, 0);
        card.addView(heading);

        body = text(BODIES[0], 14f, BODY);
        body.setLineSpacing(dp(2), 1.3f);
        body.setPadding(0, dp(8), 0, 0);
        card.addView(body);

        step = text("1 of 3", 11f, MUTED);
        step.setLetterSpacing(0.08f);
        step.setPadding(0, dp(12), 0, 0);
        card.addView(step);

        content.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        contentParams.bottomMargin = dp(168);
        root.addView(content, contentParams);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setGravity(Gravity.CENTER_HORIZONTAL);
        bottom.setPadding(dp(22), dp(8), dp(22), dp(22));

        dots = new LinearLayout(this);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        dots.setGravity(Gravity.CENTER);
        dots.setPadding(0, 0, 0, dp(16));
        bottom.addView(dots);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        back = text("Back", 14f, MUTED);
        back.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        back.setPadding(dp(8), dp(12), dp(18), dp(12));
        back.setOnClickListener(view -> previous());
        actions.addView(back);
        TextView actionSpacer = new TextView(this);
        actions.addView(actionSpacer, new LinearLayout.LayoutParams(0, 1, 1f));
        next = text("Next", 15f, Color.WHITE);
        next.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        next.setGravity(Gravity.CENTER);
        next.setPadding(dp(34), dp(14), dp(34), dp(14));
        GradientDrawable nextBg = new GradientDrawable();
        nextBg.setColor(Color.rgb(16, 122, 87));
        nextBg.setCornerRadius(dp(24));
        next.setBackground(nextBg);
        next.setOnClickListener(view -> {
            if (page < 2) { page++; render(true); } else finishIntro();
        });
        actions.addView(next);
        bottom.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bottomParams.gravity = Gravity.BOTTOM;
        root.addView(bottom, bottomParams);
        return root;
    }

    private void render(boolean animate) {
        icon.setText(ICONS[page]);
        heading.setText(HEADINGS[page]);
        body.setText(BODIES[page]);
        step.setText((page + 1) + " of 3");
        back.setVisibility(page == 0 ? View.INVISIBLE : View.VISIBLE);
        next.setText(page == 2 ? "Start" : "Next");
        rebuildDots();
        if (animate) {
            View[] views = {icon, heading, body, step};
            for (View view : views) {
                view.setAlpha(0f);
                view.setTranslationX(dp(14));
                view.animate().alpha(1f).translationX(0f).setDuration(260).start();
            }
        }
    }

    private void rebuildDots() {
        dots.removeAllViews();
        for (int i = 0; i < 3; i++) {
            View dot = new View(this);
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(i == page ? GOLD : Color.argb(80, 220, 230, 224));
            dot.setBackground(shape);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(7), dp(7));
            params.leftMargin = dp(4);
            params.rightMargin = dp(4);
            dots.addView(dot, params);
        }
    }

    private void previous() {
        if (page > 0) { page--; render(true); }
    }

    private void finishIntro() {
        prefs.setOnboardingSeen(true);
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    public void onBackPressed() {
        if (page > 0) previous(); else finishIntro();
    }

    private TextView text(String value, float sizeSp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color);
        return view;
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics()));
    }
}
