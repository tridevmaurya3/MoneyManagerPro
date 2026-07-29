package com.example.moneymanagerpro.activities;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.utils.ThemeManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.CircularProgressIndicator;

public class SplashActivity extends AppCompatActivity {

    private static final String PREFS_NAME =
            "MoneyManagerSecurity";

    private static final String KEY_PIN =
            "app_pin";

    private static final String KEY_PIN_ENABLED =
            "pin_enabled";

    private static final long SPLASH_DELAY_MS =
            1200L;

    private MaterialCardView splashSecurityBadge;
    private MaterialCardView splashIconOuterCard;
    private MaterialCardView splashFooterCard;

    private TextView txtSplashTitle;
    private TextView txtSplashSubtitle;
    private TextView txtSplashLoading;

    private CircularProgressIndicator splashProgress;

    private final Handler navigationHandler =
            new Handler(Looper.getMainLooper());

    private boolean navigationScheduled = false;
    private boolean hasNavigated = false;

    private AnimatorSet pulseAnimator;

    private final Runnable navigationRunnable =
            this::navigateToNextScreen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applySavedTheme(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        bindViews();
        updateLoadingMessage();
        prepareInitialAnimationState();
        startEntranceAnimations();
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (!hasNavigated) {
            startPulseAnimation();
            scheduleNavigation();
        }
    }

    @Override
    protected void onStop() {
        cancelScheduledNavigation();
        stopPulseAnimation();

        super.onStop();
    }

    @Override
    protected void onDestroy() {
        cancelScheduledNavigation();
        stopPulseAnimation();
        cancelViewAnimations();

        super.onDestroy();
    }

    private void bindViews() {
        splashSecurityBadge =
                findViewById(R.id.splashSecurityBadge);

        splashIconOuterCard =
                findViewById(R.id.splashIconOuterCard);

        splashFooterCard =
                findViewById(R.id.splashFooterCard);

        txtSplashTitle =
                findViewById(R.id.txtSplashTitle);

        txtSplashSubtitle =
                findViewById(R.id.txtSplashSubtitle);

        txtSplashLoading =
                findViewById(R.id.txtSplashLoading);

        splashProgress =
                findViewById(R.id.splashProgress);
    }

    private void updateLoadingMessage() {
        SharedPreferences preferences =
                getSharedPreferences(
                        PREFS_NAME,
                        MODE_PRIVATE
                );

        boolean pinEnabled =
                preferences.getBoolean(
                        KEY_PIN_ENABLED,
                        false
                );

        String savedPin =
                preferences.getString(
                        KEY_PIN,
                        ""
                );

        boolean validPin =
                savedPin != null
                        && savedPin.matches("\\d{4}");

        if (pinEnabled && validPin) {
            txtSplashLoading.setText(
                    "Preparing secure access..."
            );
        } else {
            txtSplashLoading.setText(
                    "Preparing your financial workspace..."
            );
        }
    }

    private void prepareInitialAnimationState() {
        splashSecurityBadge.setAlpha(0f);
        splashSecurityBadge.setTranslationY(-dp(16));

        splashIconOuterCard.setAlpha(0f);
        splashIconOuterCard.setScaleX(0.72f);
        splashIconOuterCard.setScaleY(0.72f);

        txtSplashTitle.setAlpha(0f);
        txtSplashTitle.setTranslationY(dp(14));

        txtSplashSubtitle.setAlpha(0f);
        txtSplashSubtitle.setTranslationY(dp(12));

        splashProgress.setAlpha(0f);
        splashProgress.setScaleX(0.75f);
        splashProgress.setScaleY(0.75f);

        txtSplashLoading.setAlpha(0f);
        txtSplashLoading.setTranslationY(dp(8));

        splashFooterCard.setAlpha(0f);
        splashFooterCard.setTranslationY(dp(20));
    }

    private void startEntranceAnimations() {
        splashSecurityBadge.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(30)
                .setDuration(360)
                .setInterpolator(
                        new DecelerateInterpolator()
                )
                .start();

        splashIconOuterCard.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(100)
                .setDuration(500)
                .setInterpolator(
                        new OvershootInterpolator(0.85f)
                )
                .start();

        txtSplashTitle.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(250)
                .setDuration(360)
                .setInterpolator(
                        new DecelerateInterpolator()
                )
                .start();

        txtSplashSubtitle.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(340)
                .setDuration(340)
                .setInterpolator(
                        new DecelerateInterpolator()
                )
                .start();

        splashProgress.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(460)
                .setDuration(300)
                .setInterpolator(
                        new DecelerateInterpolator()
                )
                .start();

        txtSplashLoading.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(520)
                .setDuration(300)
                .setInterpolator(
                        new DecelerateInterpolator()
                )
                .start();

        splashFooterCard.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(600)
                .setDuration(380)
                .setInterpolator(
                        new DecelerateInterpolator()
                )
                .start();
    }

    private void startPulseAnimation() {
        if (splashIconOuterCard == null
                || pulseAnimator != null
                || hasNavigated) {

            return;
        }

        ObjectAnimator scaleXAnimator =
                ObjectAnimator.ofFloat(
                        splashIconOuterCard,
                        View.SCALE_X,
                        1f,
                        1.035f,
                        1f
                );

        ObjectAnimator scaleYAnimator =
                ObjectAnimator.ofFloat(
                        splashIconOuterCard,
                        View.SCALE_Y,
                        1f,
                        1.035f,
                        1f
                );

        scaleXAnimator.setRepeatCount(
                ObjectAnimator.INFINITE
        );

        scaleYAnimator.setRepeatCount(
                ObjectAnimator.INFINITE
        );

        pulseAnimator =
                new AnimatorSet();

        pulseAnimator.playTogether(
                scaleXAnimator,
                scaleYAnimator
        );

        pulseAnimator.setStartDelay(720);
        pulseAnimator.setDuration(1100);

        pulseAnimator.setInterpolator(
                new AccelerateDecelerateInterpolator()
        );

        pulseAnimator.start();
    }

    private void stopPulseAnimation() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
        }

        if (splashIconOuterCard != null) {
            splashIconOuterCard.setScaleX(1f);
            splashIconOuterCard.setScaleY(1f);
        }
    }

    private void scheduleNavigation() {
        if (navigationScheduled
                || hasNavigated
                || isFinishing()
                || isDestroyed()) {

            return;
        }

        navigationScheduled = true;

        navigationHandler.postDelayed(
                navigationRunnable,
                SPLASH_DELAY_MS
        );
    }

    private void cancelScheduledNavigation() {
        navigationHandler.removeCallbacks(
                navigationRunnable
        );

        navigationScheduled = false;
    }

    private void navigateToNextScreen() {
        navigationScheduled = false;

        if (hasNavigated
                || isFinishing()
                || isDestroyed()) {

            return;
        }

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            hasNavigated = true;

            Intent loginIntent = AuthenticationActivity.createLoginIntent(this);
            loginIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK
            );

            startActivity(loginIntent);
            finish();
            return;
        }

        SharedPreferences preferences =
                getSharedPreferences(
                        PREFS_NAME,
                        MODE_PRIVATE
                );

        boolean pinEnabled =
                preferences.getBoolean(
                        KEY_PIN_ENABLED,
                        false
                );

        String savedPin =
                preferences.getString(
                        KEY_PIN,
                        ""
                );

        boolean validPin =
                savedPin != null
                        && savedPin.matches("\\d{4}");

        /*
         * Prevents an invalid or incomplete PIN preference
         * from locking the user outside the application.
         */
        if (pinEnabled && !validPin) {
            preferences.edit()
                    .putBoolean(
                            KEY_PIN_ENABLED,
                            false
                    )
                    .apply();

            pinEnabled = false;
        }

        Class<?> nextActivity =
                pinEnabled
                        ? PinActivity.class
                        : DashboardActivity.class;

        hasNavigated = true;

        Intent intent =
                new Intent(
                        SplashActivity.this,
                        nextActivity
                );

        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        );

        try {
            startActivity(intent);

            overridePendingTransition(
                    android.R.anim.fade_in,
                    android.R.anim.fade_out
            );

            finish();

        } catch (Exception exception) {
            hasNavigated = false;

            Intent fallbackIntent =
                    new Intent(
                            SplashActivity.this,
                            DashboardActivity.class
                    );

            startActivity(fallbackIntent);
            finish();
        }
    }

    private void cancelViewAnimations() {
        if (splashSecurityBadge != null) {
            splashSecurityBadge.animate().cancel();
        }

        if (splashIconOuterCard != null) {
            splashIconOuterCard.animate().cancel();
        }

        if (txtSplashTitle != null) {
            txtSplashTitle.animate().cancel();
        }

        if (txtSplashSubtitle != null) {
            txtSplashSubtitle.animate().cancel();
        }

        if (splashProgress != null) {
            splashProgress.animate().cancel();
        }

        if (txtSplashLoading != null) {
            txtSplashLoading.animate().cancel();
        }

        if (splashFooterCard != null) {
            splashFooterCard.animate().cancel();
        }
    }

    private int dp(int value) {
        return Math.round(
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }
}
