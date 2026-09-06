package com.example.moneymanagerpro.floating;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.example.moneymanagerpro.R;

/**
 * Presentation-only tightening for the full floating Add Expense activity.
 *
 * The activity still uses AddExpenseActivity's original UPI, QR, receipt,
 * item, validation and Room save logic. This class only makes those existing
 * controls fit the compact floating-window design.
 */
final class FloatingExpenseCompactPolish {

    private FloatingExpenseCompactPolish() {
    }

    static void apply(Activity activity) {
        if (activity == null) {
            return;
        }

        compactWindow(activity);

        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) {
            return;
        }

        tightenTree(activity, content);
        compactKnownControls(activity, content);
    }

    private static void compactWindow(Activity activity) {
        Window window = activity.getWindow();
        if (window == null) {
            return;
        }

        int screenWidth = activity.getResources()
                .getDisplayMetrics().widthPixels;
        int screenHeight = activity.getResources()
                .getDisplayMetrics().heightPixels;

        WindowManager.LayoutParams attrs = window.getAttributes();
        attrs.width = Math.min(
                screenWidth - dp(activity, 18),
                Math.max(dp(activity, 318), Math.round(screenWidth * 0.88f))
        );
        attrs.height = Math.min(
                screenHeight - dp(activity, 82),
                Math.max(dp(activity, 430), Math.round(screenHeight * 0.70f))
        );
        attrs.gravity = Gravity.CENTER;
        attrs.dimAmount = 0.06f;
        attrs.alpha = 1f;
        window.setAttributes(attrs);
    }

    private static void compactKnownControls(
            Activity activity,
            View root
    ) {
        setHeight(activity, root, R.id.etAmount, 40);
        setHeight(activity, root, R.id.dropdownCategory, 38);
        setHeight(activity, root, R.id.dropdownAccount, 38);
        setHeight(activity, root, R.id.etDate, 38);
        setHeight(activity, root, R.id.etNote, 54);

        // Full original UPI flow, only presented more compactly.
        setHeight(activity, root, R.id.dropdownUpiEntryMode, 38);
        setHeight(activity, root, R.id.etUpiPayeeId, 38);
        setHeight(activity, root, R.id.etUpiPayeeName, 38);
        setHeight(activity, root, R.id.btnPayWithUpi, 38);
        setHeight(activity, root, R.id.btnClearUpiPaymentResult, 34);

        // Original receipt picker/preview/remove flow, compact presentation.
        setHeight(activity, root, R.id.btnAttachReceipt, 38);
        setHeight(activity, root, R.id.btnRemoveReceipt, 30);
        capHeight(activity, root, R.id.receiptPreviewContainer, 112);

        setHeight(activity, root, R.id.btnMoreItem, 36);
        setHeight(activity, root, R.id.btnSaveExpense, 42);

        View status = root.findViewById(R.id.txtUpiPaymentStatus);
        if (status instanceof TextView) {
            ((TextView) status).setTextSize(11f);
        }

        View itemsTotal = root.findViewById(R.id.txtItemsTotal);
        if (itemsTotal instanceof TextView) {
            ((TextView) itemsTotal).setTextSize(11f);
        }
    }

    private static void tightenTree(Activity activity, View view) {
        if (view == null) {
            return;
        }

        int id = view.getId();
        if (id == R.id.etItemName) {
            applyCompactField(activity, view, 36);
        } else if (id == R.id.etItemQuantity
                || id == R.id.etItemUnit
                || id == R.id.etItemPrice
                || id == R.id.etItemTotal) {
            applyCompactField(activity, view, 38);
        } else if (id == R.id.btnRemoveItem) {
            setExactHeight(activity, view, 30);
            if (view instanceof TextView) {
                TextView text = (TextView) view;
                text.setMinWidth(0);
                text.setMinimumWidth(0);
                text.setTextSize(10.5f);
                text.setPadding(dp(activity, 8), 0, dp(activity, 8), 0);
            }
        }

        ViewGroup.LayoutParams raw = view.getLayoutParams();
        if (raw instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins =
                    (ViewGroup.MarginLayoutParams) raw;
            margins.topMargin = Math.min(margins.topMargin, dp(activity, 6));
            margins.bottomMargin = Math.min(margins.bottomMargin, dp(activity, 4));
            margins.leftMargin = Math.min(margins.leftMargin, dp(activity, 6));
            margins.rightMargin = Math.min(margins.rightMargin, dp(activity, 6));
            view.setLayoutParams(margins);
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;

            if (!(group.getClass().getName().contains("TextInputLayout"))) {
                int left = Math.min(group.getPaddingLeft(), dp(activity, 9));
                int top = Math.min(group.getPaddingTop(), dp(activity, 9));
                int right = Math.min(group.getPaddingRight(), dp(activity, 9));
                int bottom = Math.min(group.getPaddingBottom(), dp(activity, 9));
                group.setPadding(left, top, right, bottom);
            }

            for (int index = 0; index < group.getChildCount(); index++) {
                tightenTree(activity, group.getChildAt(index));
            }
        }
    }

    private static void applyCompactField(
            Activity activity,
            View view,
            int heightDp
    ) {
        setExactHeight(activity, view, heightDp);
        view.setMinimumHeight(dp(activity, heightDp));
        view.setPadding(
                Math.min(view.getPaddingLeft(), dp(activity, 8)),
                dp(activity, 1),
                Math.min(view.getPaddingRight(), dp(activity, 8)),
                dp(activity, 1)
        );
        if (view instanceof TextView) {
            ((TextView) view).setTextSize(11.5f);
            ((TextView) view).setIncludeFontPadding(false);
        }
    }

    private static void setHeight(
            Activity activity,
            View root,
            int id,
            int heightDp
    ) {
        View view = root.findViewById(id);
        if (view == null) {
            return;
        }
        setExactHeight(activity, view, heightDp);
        view.setMinimumHeight(dp(activity, heightDp));
        if (view instanceof TextView) {
            ((TextView) view).setIncludeFontPadding(false);
        }
    }

    private static void capHeight(
            Activity activity,
            View root,
            int id,
            int heightDp
    ) {
        View view = root.findViewById(id);
        if (view == null) {
            return;
        }
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null) {
            params.height = dp(activity, heightDp);
            view.setLayoutParams(params);
        }
        if (view instanceof FrameLayout) {
            ((FrameLayout) view).setClipToPadding(false);
        }
    }

    private static void setExactHeight(
            Activity activity,
            View view,
            int heightDp
    ) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null) {
            params.height = dp(activity, heightDp);
            view.setLayoutParams(params);
        }
    }

    private static int dp(Activity activity, int value) {
        return Math.round(
                value
                        * activity.getResources()
                        .getDisplayMetrics()
                        .density
        );
    }
}
