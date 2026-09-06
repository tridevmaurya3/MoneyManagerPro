package com.example.moneymanagerpro.floating;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import com.example.moneymanagerpro.R;

/**
 * Removes the decorative receipt chrome only in the floating expense window.
 * The existing receipt picker, preview, remove and ReceiptStore behavior remain
 * owned by AddExpenseActivity.
 */
final class FloatingExpenseReceiptPolish {

    private FloatingExpenseReceiptPolish() {
    }

    static void apply(Activity activity) {
        if (activity == null) {
            return;
        }

        View preview = activity.findViewById(R.id.receiptPreviewContainer);
        if (preview == null) {
            return;
        }

        View card = nearestCard(preview);
        if (!(card instanceof ViewGroup)) {
            return;
        }

        ViewGroup cardGroup = (ViewGroup) card;
        if (cardGroup.getChildCount() == 0
                || !(cardGroup.getChildAt(0) instanceof ViewGroup)) {
            return;
        }

        ViewGroup inside = (ViewGroup) cardGroup.getChildAt(0);
        inside.setPadding(0, 0, 0, 0);

        for (int i = 0; i < inside.getChildCount(); i++) {
            View child = inside.getChildAt(i);
            if (child != preview) {
                child.setVisibility(View.GONE);
            }
        }

        ViewGroup.LayoutParams previewParams = preview.getLayoutParams();
        if (previewParams != null) {
            previewParams.height = dp(activity, 76);
            preview.setLayoutParams(previewParams);
        }

        ViewGroup.LayoutParams cardParams = card.getLayoutParams();
        if (cardParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins =
                    (ViewGroup.MarginLayoutParams) cardParams;
            margins.topMargin = dp(activity, 2);
            margins.bottomMargin = 0;
            card.setLayoutParams(margins);
        }
    }

    private static View nearestCard(View start) {
        View current = start;
        while (current != null) {
            if (current.getClass().getName().contains("MaterialCardView")) {
                return current;
            }
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(
                value * activity.getResources().getDisplayMetrics().density
        );
    }
}
