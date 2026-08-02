package com.example.moneymanagerpro.planner;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.activities.DashboardActivity;
import com.example.moneymanagerpro.activities.SmartGoalDebtPlannerActivity;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

public final class SmartGoalDebtDashboardController {

    private static final String TAG = "smart_goal_debt_dashboard_card";
    private final Activity activity;

    public SmartGoalDebtDashboardController(@NonNull Activity activity) {
        this.activity = activity;
    }

    public void attach() {
        if (!(activity instanceof DashboardActivity)
                || activity.isFinishing()
                || activity.isDestroyed()) {
            return;
        }

        View root = activity.findViewById(android.R.id.content);
        if (!(root instanceof ViewGroup)) return;
        if (((ViewGroup) root).findViewWithTag(TAG) != null) return;

        LinearLayout column = findDashboardColumn((ViewGroup) root);
        if (column == null) {
            root.postDelayed(this::attach, 140L);
            return;
        }

        MaterialCardView card = new MaterialCardView(activity);
        card.setTag(TAG);
        card.setCardBackgroundColor(color(R.color.warning_surface));
        card.setStrokeColor(color(R.color.warning_outline));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(18));
        card.setCardElevation(dp(1));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(dp(16), dp(8), dp(16), dp(12));
        card.setLayoutParams(cardParams);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(15), dp(14), dp(15), dp(14));

        TextView title = text("Smart Goal & Debt Payoff Planner", 16, R.color.warning, true);
        content.addView(title);
        TextView detail = text(
                "Compare Snowball and Avalanche, estimate your debt-free date, and see the monthly contribution needed for every savings goal.",
                11,
                R.color.app_text_secondary,
                false
        );
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        detailParams.setMargins(0, dp(4), 0, dp(10));
        detail.setLayoutParams(detailParams);
        content.addView(detail);

        MaterialButton button = new MaterialButton(activity);
        button.setText("Open Smart Planner");
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setCornerRadius(dp(14));
        button.setGravity(Gravity.CENTER);
        button.setOnClickListener(view -> activity.startActivity(
                new Intent(activity, SmartGoalDebtPlannerActivity.class)
        ));
        BubbleTouchAnimator.apply(button);
        content.addView(button, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
        ));
        card.addView(content);

        column.addView(card);
        card.setAlpha(0f);
        card.setTranslationY(dp(14));
        card.animate().alpha(1f).translationY(0f).setDuration(320L).start();
    }

    private LinearLayout findDashboardColumn(@NonNull ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout layout = (LinearLayout) child;
                if (layout.getOrientation() == LinearLayout.VERTICAL
                        && layout.findViewWithTag("money_manager_smart_dashboard_2_panel") != null) {
                    return layout;
                }
            }
            if (child instanceof ViewGroup) {
                LinearLayout found = findDashboardColumn((ViewGroup) child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private TextView text(String value, int size, int colorRes, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color(colorRes));
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private int color(int resource) {
        return ContextCompat.getColor(activity, resource);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
