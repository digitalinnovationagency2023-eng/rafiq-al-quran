package com.rqmbooks.rafiqalquran;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Emerald-and-gold Bismillah opening. Shown on every cold start for about
 * 2.6 seconds; tapping anywhere skips it. The first run then shows a short
 * three-card introduction.
 */
public final class SplashActivity extends Activity {
    private static final int GOLD = Color.rgb(212, 175, 90);
    private static final int SILVER = Color.rgb(214, 222, 217);
    private static final int MUTED = Color.rgb(150, 170, 160);
    private static final int DARK = Color.rgb(3, 10, 7);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable advance = this::advance;
    private Prefs prefs;
    private boolean launched;
    private long shownAt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        getWindow().setStatusBarColor(DARK);
        getWindow().setNavigationBarColor(DARK);
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        setContentView(build());
        shownAt = SystemClock.uptimeMillis();
        handler.postDelayed(advance, 2600);
    }

    private View build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(DARK);
        root.addView(new IntroArt(this), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(24), dp(20), dp(24), dp(20));

        TextView bismillah = text("\u0628\u0650\u0633\u0652\u0645\u0650 \u0627\u0644\u0644\u0647\u0650 \u0627\u0644\u0631\u064e\u0651\u062d\u0652\u0645\u064e\u0670\u0646\u0650 \u0627\u0644\u0631\u064e\u0651\u062d\u0650\u064a\u0645\u0650", 23.5f, GOLD);
        bismillah.setTypeface(amiri());
        bismillah.setSingleLine(true);
        bismillah.setGravity(Gravity.CENTER);
        bismillah.setLineSpacing(0, 1.35f);
        bismillah.setShadowLayer(18, 0, 3, Color.argb(150, 212, 175, 90));
        bismillah.setAlpha(0f);
        bismillah.setScaleX(0.94f);
        bismillah.setScaleY(0.94f);
        bismillah.setTranslationY(dp(14));
        content.addView(bismillah, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView meaning = text("In the name of Allah, the Most Gracious, the Most Merciful", 12f, SILVER);
        meaning.setGravity(Gravity.CENTER);
        meaning.setAlpha(0f);
        meaning.setPadding(0, dp(10), 0, 0);
        content.addView(meaning);

        View divider = new View(this);
        GradientDrawable goldLine = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.TRANSPARENT, GOLD, Color.TRANSPARENT});
        divider.setBackground(goldLine);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(dp(150), dp(2));
        dividerParams.topMargin = dp(24);
        dividerParams.bottomMargin = dp(20);
        divider.setLayoutParams(dividerParams);
        divider.setScaleX(0f);
        content.addView(divider);

        TextView title = text("Rafiq Al-Quran", 25f, Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setGravity(Gravity.CENTER);
        title.setAlpha(0f);
        title.setTranslationY(dp(8));
        content.addView(title);

        TextView subtitle = text("Quran-only assistant", 12f, MUTED);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(4), 0, 0);
        subtitle.setAlpha(0f);
        content.addView(subtitle);

        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        contentParams.gravity = Gravity.CENTER;
        root.addView(content, contentParams);

        TextView skip = text("Tap to skip", 11f, Color.argb(170, 220, 230, 224));
        skip.setPadding(dp(16), dp(12), dp(16), dp(16));
        skip.setAlpha(0f);
        skip.setOnClickListener(view -> advance());
        FrameLayout.LayoutParams skipParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        skipParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        skipParams.bottomMargin = dp(26);
        root.addView(skip, skipParams);

        root.setOnClickListener(view -> advance());
        bismillah.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                .setStartDelay(260).setDuration(900)
                .setInterpolator(new DecelerateInterpolator()).start();
        meaning.animate().alpha(0.86f).setStartDelay(560).setDuration(700).start();
        divider.animate().scaleX(1f).setStartDelay(760).setDuration(760).start();
        title.animate().alpha(1f).translationY(0f).setStartDelay(1050).setDuration(600).start();
        subtitle.animate().alpha(1f).setStartDelay(1200).setDuration(600).start();
        skip.animate().alpha(1f).setStartDelay(1450).setDuration(500).start();
        return root;
    }

    private TextView text(String value, float sizeSp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private Typeface amiri() {
        try {
            return Typeface.createFromAsset(getAssets(), "fonts/Amiri-Regular.ttf");
        } catch (Exception ignored) {
            return Typeface.create("serif", Typeface.NORMAL);
        }
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics()));
    }

    private void advance() {
        if (launched) return;
        launched = true;
        handler.removeCallbacks(advance);
        Class<?> target = prefs.onboardingSeen() ? MainActivity.class : OnboardingActivity.class;
        Intent intent = new Intent(this, target);
        if (target == MainActivity.class) intent.putExtras(getIntent());
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!launched && shownAt > 0) {
            handler.removeCallbacks(advance);
            long remaining = 2600 - (SystemClock.uptimeMillis() - shownAt);
            handler.postDelayed(advance, Math.max(250, remaining));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!launched) handler.removeCallbacks(advance);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(advance);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        advance();
    }
}
