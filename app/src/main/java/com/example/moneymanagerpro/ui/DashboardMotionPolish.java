package com.example.moneymanagerpro.ui;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.example.moneymanagerpro.activities.DashboardActivity;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Applies restrained Fluent-style entrance motion to dashboard cards.
 * Animations run once per Activity instance and do not alter saved data.
 */
public final class DashboardMotionPolish {

    private static final Map<Activity, Boolean> APPLIED = new WeakHashMap<>();

    private DashboardMotionPolish() {
    }

    public static void apply(@NonNull Activity activity) {
        if (!(activity instanceof DashboardActivity) || APPLIED.containsKey(activity)) {
            return;
        }

        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) {
            return;
        }

        List<MaterialCardView> cards = new ArrayList<>();
        collectCards((ViewGroup) content, cards);

        int maximum = Math.min(cards.size(), 18);
        for (int index = 0; index < maximum; index++) {
            MaterialCardView card = cards.get(index);
            card.setAlpha(0f);
            card.setTranslationY(dp(activity, 18));
            card.setScaleX(0.985f);
            card.setScaleY(0.985f);

            AnimatorSet set = new AnimatorSet();
            set.playTogether(
                    ObjectAnimator.ofFloat(card, View.ALPHA, 0f, 1f),
                    ObjectAnimator.ofFloat(card, View.TRANSLATION_Y, dp(activity, 18), 0f),
                    ObjectAnimator.ofFloat(card, View.SCALE_X, 0.985f, 1f),
                    ObjectAnimator.ofFloat(card, View.SCALE_Y, 0.985f, 1f)
            );
            set.setStartDelay(Math.min(420L, index * 38L));
            set.setDuration(300L);
            set.start();
        }

        APPLIED.put(activity, true);
    }

    public static void remove(@NonNull Activity activity) {
        APPLIED.remove(activity);
    }

    private static void collectCards(ViewGroup group, List<MaterialCardView> output) {
        for (int index = 0; index < group.getChildCount(); index++) {
            View child = group.getChildAt(index);
            if (child instanceof MaterialCardView) {
                output.add((MaterialCardView) child);
            }
            if (child instanceof ViewGroup) {
                collectCards((ViewGroup) child, output);
            }
        }
    }

    private static float dp(Activity activity, int value) {
        return value * activity.getResources().getDisplayMetrics().density;
    }
}
