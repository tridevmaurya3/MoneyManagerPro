package com.example.moneymanagerpro.utils;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.R;
import com.google.android.material.card.MaterialCardView;

/** UI-only header normalization. Existing back views and click listeners stay intact. */
public final class UnifiedPageHeader {
    private UnifiedPageHeader() { }

    public static void mergeExisting(Activity activity, int backId) {
        View back = activity.findViewById(backId);
        if (back == null || !(back.getParent() instanceof LinearLayout)) return;
        LinearLayout page = (LinearLayout) back.getParent();
        int backIndex = page.indexOfChild(back);
        MaterialCardView headerCard = null;
        int cardIndex = -1;
        for (int i = backIndex + 1; i < Math.min(page.getChildCount(), backIndex + 7); i++) {
            if (page.getChildAt(i) instanceof MaterialCardView) {
                headerCard = (MaterialCardView) page.getChildAt(i);
                cardIndex = i;
                break;
            }
        }
        if (headerCard == null || headerCard.getChildCount() == 0
                || !(headerCard.getChildAt(0) instanceof LinearLayout)) {
            styleBack(activity, back);
            return;
        }
        LinearLayout headerContent = (LinearLayout) headerCard.getChildAt(0);
        if (headerContent.getOrientation() != LinearLayout.HORIZONTAL) {
            styleBack(activity, back);
            return;
        }
        for (int i = backIndex + 1; i < cardIndex; i++) {
            View duplicateHeading = page.getChildAt(i);
            if (duplicateHeading instanceof TextView) duplicateHeading.setVisibility(View.GONE);
        }
        page.removeView(back);
        styleBack(activity, back);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(activity, 46), dp(activity, 46));
        params.setMarginEnd(dp(activity, 10));
        back.setLayoutParams(params);
        headerContent.addView(back, 0);
        ViewGroup.LayoutParams rawCardParams = headerCard.getLayoutParams();
        if (rawCardParams instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) rawCardParams).topMargin = 0;
            headerCard.setLayoutParams(rawCardParams);
        }
    }

    public static void add(Activity activity, String title, String subtitle,
                           @ColorRes int surface, @ColorRes int outline, @ColorRes int accent) {
        LinearLayout page = findPage(activity.findViewById(android.R.id.content));
        if (page == null) return;
        int hidden = 0;
        for (int i = 0; i < Math.min(4, page.getChildCount()) && hidden < 3; i++) {
            if (page.getChildAt(i) instanceof TextView) {
                page.getChildAt(i).setVisibility(View.GONE);
                hidden++;
            }
        }
        MaterialCardView card = new MaterialCardView(activity);
        card.setCardBackgroundColor(ContextCompat.getColor(activity, surface));
        card.setStrokeColor(ContextCompat.getColor(activity, outline));
        card.setStrokeWidth(dp(activity, 1));
        card.setRadius(dp(activity, 18));
        card.setCardElevation(dp(activity, 1));
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
        TextView back = new TextView(activity);
        back.setText("←");
        back.setContentDescription("Back");
        back.setOnClickListener(v -> activity.finish());
        styleBack(activity, back);
        row.addView(back, new LinearLayout.LayoutParams(dp(activity, 46), dp(activity, 46)));
        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.setMarginStart(dp(activity, 11));
        TextView heading = new TextView(activity);
        heading.setText(title);
        heading.setTextColor(ContextCompat.getColor(activity, accent));
        heading.setTextSize(18);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        TextView subheading = new TextView(activity);
        subheading.setText(subtitle);
        subheading.setTextColor(ContextCompat.getColor(activity, R.color.app_text_secondary));
        subheading.setTextSize(11);
        subheading.setMaxLines(2);
        copy.addView(heading);
        copy.addView(subheading);
        row.addView(copy, copyParams);
        card.addView(row);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, dp(activity, 12));
        page.addView(card, 0, cardParams);
    }

    private static void styleBack(Activity activity, View view) {
        if (!(view instanceof TextView)) return;
        TextView back = (TextView) view;
        back.setText("←");
        back.setTextSize(22);
        back.setGravity(Gravity.CENTER);
        back.setMinWidth(0);
        back.setMinimumWidth(0);
        back.setPadding(0, 0, 0, 0);
        back.setTextColor(ContextCompat.getColor(activity, R.color.secondary));
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(ContextCompat.getColor(activity, R.color.info_surface));
        background.setStroke(dp(activity, 1), ContextCompat.getColor(activity, R.color.info_outline));
        back.setBackground(background);
    }

    private static LinearLayout findPage(View view) {
        if (view instanceof LinearLayout && ((LinearLayout) view).getOrientation() == LinearLayout.VERTICAL) {
            return (LinearLayout) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                LinearLayout found = findPage(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
