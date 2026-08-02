package com.example.moneymanagerpro.ui;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.moneymanagerpro.activities.DashboardActivity;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

/**
 * Removes the redundant introductory title/subtitle shown above a page's
 * primary hero/manager card. Back navigation remains visible and pages without
 * a hero card are left unchanged.
 */
public final class DuplicatePageHeadingController {

    private static final String PROCESSED_TAG = "duplicate_page_heading_processed";

    private final Activity activity;
    private int attempts;

    public DuplicatePageHeadingController(@NonNull Activity activity) {
        this.activity = activity;
    }

    public void attach() {
        if (activity instanceof DashboardActivity
                || activity.isFinishing()
                || activity.isDestroyed()) {
            return;
        }

        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) return;

        ViewGroup root = (ViewGroup) content;
        Object processed = root.getTag();
        if (PROCESSED_TAG.equals(processed)) return;

        if (!removeDuplicateHeading(root) && attempts++ < 12) {
            root.postDelayed(this::attach, 140L);
        }
    }

    private boolean removeDuplicateHeading(@NonNull ViewGroup root) {
        LinearLayout pageColumn = findPageColumn(root);
        if (pageColumn == null) return false;

        int firstHeroIndex = findFirstHeroCardIndex(pageColumn);
        if (firstHeroIndex < 0) {
            root.setTag(PROCESSED_TAG);
            return true;
        }

        List<TextView> candidates = new ArrayList<>();
        for (int index = 0; index < firstHeroIndex; index++) {
            collectTopTextViews(pageColumn.getChildAt(index), candidates);
        }

        TextView heading = null;
        for (TextView candidate : candidates) {
            String value = safeText(candidate);
            if (isBackNavigation(value)) continue;
            if (candidate.getTextSize() / activity.getResources().getDisplayMetrics().scaledDensity >= 21f
                    && value.length() >= 2) {
                heading = candidate;
                break;
            }
        }

        if (heading == null || !heroCardHasHeading((MaterialCardView) pageColumn.getChildAt(firstHeroIndex))) {
            root.setTag(PROCESSED_TAG);
            return true;
        }

        int headingPosition = candidates.indexOf(heading);
        heading.setVisibility(View.GONE);
        collapseMargins(heading);

        if (headingPosition >= 0 && headingPosition + 1 < candidates.size()) {
            TextView subtitle = candidates.get(headingPosition + 1);
            String subtitleText = safeText(subtitle);
            float subtitleSp = subtitle.getTextSize()
                    / activity.getResources().getDisplayMetrics().scaledDensity;
            if (!isBackNavigation(subtitleText)
                    && subtitleSp <= 18f
                    && subtitleText.length() > 3) {
                subtitle.setVisibility(View.GONE);
                collapseMargins(subtitle);
            }
        }

        root.setTag(PROCESSED_TAG);
        return true;
    }

    private LinearLayout findPageColumn(@NonNull ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout layout = (LinearLayout) child;
                if (layout.getOrientation() == LinearLayout.VERTICAL
                        && findFirstHeroCardIndex(layout) >= 0) {
                    return layout;
                }
            }
            if (child instanceof ViewGroup) {
                LinearLayout nested = findPageColumn((ViewGroup) child);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private int findFirstHeroCardIndex(@NonNull LinearLayout column) {
        int limit = Math.min(column.getChildCount(), 10);
        for (int i = 0; i < limit; i++) {
            View child = column.getChildAt(i);
            if (child instanceof MaterialCardView
                    && heroCardHasHeading((MaterialCardView) child)) {
                return i;
            }
        }
        return -1;
    }

    private boolean heroCardHasHeading(@NonNull MaterialCardView card) {
        List<TextView> textViews = new ArrayList<>();
        collectTopTextViews(card, textViews);
        for (TextView textView : textViews) {
            String value = safeText(textView);
            float sizeSp = textView.getTextSize()
                    / activity.getResources().getDisplayMetrics().scaledDensity;
            if (sizeSp >= 16f && value.length() >= 3 && !isBackNavigation(value)) {
                return true;
            }
        }
        return false;
    }

    private void collectTopTextViews(@NonNull View view, @NonNull List<TextView> out) {
        if (view instanceof TextView) {
            out.add((TextView) view);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectTopTextViews(group.getChildAt(i), out);
            }
        }
    }

    private void collapseMargins(@NonNull View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
            margins.topMargin = 0;
            margins.bottomMargin = 0;
            view.setLayoutParams(margins);
        }
    }

    @NonNull
    private String safeText(@NonNull TextView view) {
        CharSequence value = view.getText();
        return value == null ? "" : value.toString().trim();
    }

    private boolean isBackNavigation(@NonNull String value) {
        String normalized = value.toLowerCase();
        return normalized.equals("back")
                || normalized.contains("←")
                || normalized.startsWith("‹")
                || normalized.startsWith("< back");
    }
}
