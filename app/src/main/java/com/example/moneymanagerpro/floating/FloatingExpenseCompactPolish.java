package com.example.moneymanagerpro.floating;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.moneymanagerpro.R;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Floating-only presentation tightening for AddExpenseActivity.
 *
 * No expense, UPI, QR, receipt or database behavior is implemented here.
 * This class only rearranges the existing AddExpenseActivity controls into a
 * dense two-column floating layout so the original logic can be reused with
 * much less vertical scrolling.
 */
final class FloatingExpenseCompactPolish {

    private static final String ITEM_COMPACT_TAG =
            "floating_expense_item_compact_v2";

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

        hideFloatingOnlyText(content);
        tightenTree(activity, content);
        compactKnownControls(activity, content);
        arrangePrimaryRows(activity, content);
        compactItems(activity, content);
        compactReceipt(activity, content);
        compactUpiResult(activity, content);
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
                screenWidth - dp(activity, 14),
                Math.max(dp(activity, 320), Math.round(screenWidth * 0.92f))
        );
        attrs.height = Math.min(
                screenHeight - dp(activity, 58),
                Math.max(dp(activity, 470), Math.round(screenHeight * 0.84f))
        );
        attrs.gravity = Gravity.CENTER;
        attrs.dimAmount = 0.04f;
        attrs.alpha = 1f;
        window.setAttributes(attrs);
    }

    private static void hideFloatingOnlyText(View root) {
        String[] hidden = {
                "Add Expense",
                "Expense Category",
                "Select what this money was spent on",
                "Pay From Account",
                "Choose the account used to make this payment",
                "Pay with UPI App",
                "Enter a UPI ID manually or scan a payment QR code to fill the receiver details automatically.",
                "Expense Date",
                "Tap the field to choose a different date",
                "Note",
                "Optional information about this expense",
                "Bill Photo",
                "Optionally attach the shop bill or payment receipt",
                "Attach Receipt",
                "Select an image from your device"
        };

        for (String text : hidden) {
            TextView view = findTextViewExact(root, text);
            if (view != null) {
                view.setVisibility(View.GONE);
            }
        }
    }

    private static void compactKnownControls(Activity activity, View root) {
        setHeight(activity, root, R.id.etAmount, 34);
        setWrapperHeight(activity, root, R.id.etAmount, 39);

        setHeight(activity, root, R.id.dropdownCategory, 32);
        setWrapperHeight(activity, root, R.id.dropdownCategory, 38);
        setHeight(activity, root, R.id.dropdownAccount, 32);
        setWrapperHeight(activity, root, R.id.dropdownAccount, 38);

        setHeight(activity, root, R.id.dropdownUpiEntryMode, 31);
        setWrapperHeight(activity, root, R.id.dropdownUpiEntryMode, 37);
        setHeight(activity, root, R.id.etUpiPayeeId, 31);
        setWrapperHeight(activity, root, R.id.etUpiPayeeId, 37);
        setHeight(activity, root, R.id.etUpiPayeeName, 31);
        setWrapperHeight(activity, root, R.id.etUpiPayeeName, 37);
        setHeight(activity, root, R.id.btnPayWithUpi, 36);
        setHeight(activity, root, R.id.btnClearUpiPaymentResult, 28);

        setHeight(activity, root, R.id.etDate, 32);
        setWrapperHeight(activity, root, R.id.etDate, 38);
        setHeight(activity, root, R.id.etNote, 38);
        setWrapperHeight(activity, root, R.id.etNote, 44);
        disableCounterForChild(root, R.id.etNote);

        setHeight(activity, root, R.id.btnAttachReceipt, 36);
        setHeight(activity, root, R.id.btnRemoveReceipt, 26);
        capHeight(activity, root, R.id.receiptPreviewContainer, 78);

        setHeight(activity, root, R.id.btnMoreItem, 32);
        setHeight(activity, root, R.id.btnSaveExpense, 38);

        shrinkText(root, R.id.dropdownCategory, 11.5f);
        shrinkText(root, R.id.dropdownAccount, 11.5f);
        shrinkText(root, R.id.dropdownUpiEntryMode, 10.5f);
        shrinkText(root, R.id.etUpiPayeeId, 10.5f);
        shrinkText(root, R.id.etUpiPayeeName, 10.5f);
        shrinkText(root, R.id.etDate, 11f);
        shrinkText(root, R.id.etNote, 10.5f);
        shrinkText(root, R.id.btnPayWithUpi, 10.5f);
        shrinkText(root, R.id.btnAttachReceipt, 10.5f);
        shrinkText(root, R.id.btnSaveExpense, 11.5f);

        View status = root.findViewById(R.id.txtUpiPaymentStatus);
        if (status instanceof TextView) {
            ((TextView) status).setTextSize(9.5f);
        }
        View total = root.findViewById(R.id.txtItemsTotal);
        if (total instanceof TextView) {
            ((TextView) total).setTextSize(9.5f);
        }
    }

    private static void arrangePrimaryRows(Activity activity, View root) {
        View category = wrapperFor(root.findViewById(R.id.dropdownCategory));
        View account = wrapperFor(root.findViewById(R.id.dropdownAccount));
        pairInSameParent(activity, category, account, 1f, 1.18f, 4);

        View upiMode = wrapperFor(root.findViewById(R.id.dropdownUpiEntryMode));
        View upiId = wrapperFor(root.findViewById(R.id.etUpiPayeeId));
        pairInSameParent(activity, upiMode, upiId, 0.92f, 1.18f, 4);

        View upiName = wrapperFor(root.findViewById(R.id.etUpiPayeeName));
        View payButton = root.findViewById(R.id.btnPayWithUpi);
        pairInSameParent(activity, upiName, payButton, 1.12f, 0.88f, 4);

        View date = wrapperFor(root.findViewById(R.id.etDate));
        View billButton = root.findViewById(R.id.btnAttachReceipt);
        if (date != null && billButton != null) {
            moveAcrossParentsIntoRow(
                    activity,
                    date,
                    billButton,
                    1.04f,
                    0.96f,
                    4
            );
        }
    }

    private static void compactItems(Activity activity, View root) {
        View containerView = root.findViewById(R.id.itemDetailsContainer);
        if (!(containerView instanceof ViewGroup)) {
            return;
        }

        ViewGroup container = (ViewGroup) containerView;
        for (int i = 0; i < container.getChildCount(); i++) {
            compactSingleItem(activity, container.getChildAt(i));
        }

        container.setOnHierarchyChangeListener(
                new ViewGroup.OnHierarchyChangeListener() {
                    @Override
                    public void onChildViewAdded(View parent, View child) {
                        compactSingleItem(activity, child);
                    }

                    @Override
                    public void onChildViewRemoved(View parent, View child) {
                    }
                }
        );

        View total = root.findViewById(R.id.txtItemsTotal);
        View more = root.findViewById(R.id.btnMoreItem);
        pairInSameParent(activity, total, more, 1f, 0.42f, 4);
    }

    private static void compactSingleItem(Activity activity, View itemRoot) {
        if (!(itemRoot instanceof ViewGroup)
                || ITEM_COMPACT_TAG.equals(itemRoot.getTag())) {
            return;
        }
        itemRoot.setTag(ITEM_COMPACT_TAG);

        View nameField = itemRoot.findViewById(R.id.etItemName);
        View qtyField = itemRoot.findViewById(R.id.etItemQuantity);
        View unitField = itemRoot.findViewById(R.id.etItemUnit);
        View priceField = itemRoot.findViewById(R.id.etItemPrice);
        View totalField = itemRoot.findViewById(R.id.etItemTotal);
        View remove = itemRoot.findViewById(R.id.btnRemoveItem);
        View number = itemRoot.findViewById(R.id.txtItemNumber);

        View name = wrapperFor(nameField);
        View qty = wrapperFor(qtyField);
        View unit = wrapperFor(unitField);
        View price = wrapperFor(priceField);
        View total = wrapperFor(totalField);

        ViewGroup body = findItemBody(itemRoot);
        if (body == null || name == null || qty == null || unit == null
                || price == null || total == null || remove == null) {
            return;
        }

        if (number != null) {
            ViewParent headerParent = number.getParent();
            number.setVisibility(View.GONE);
            if (headerParent instanceof View) {
                ((View) headerParent).setVisibility(View.GONE);
            }
        }

        detach(name);
        detach(qty);
        detach(unit);
        detach(price);
        detach(total);
        detach(remove);

        LinearLayout first = horizontal(activity);
        first.addView(name, weighted(1f, 0));
        LinearLayout.LayoutParams removeParams =
                new LinearLayout.LayoutParams(dp(activity, 30), dp(activity, 34));
        removeParams.leftMargin = dp(activity, 3);
        first.addView(remove, removeParams);

        LinearLayout second = horizontal(activity);
        second.addView(qty, weighted(0.68f, 0));
        second.addView(unit, weightedWithGap(activity, 0.86f, 3));
        second.addView(price, weightedWithGap(activity, 0.90f, 3));
        second.addView(total, weightedWithGap(activity, 0.90f, 3));

        body.addView(first, fullWidthWithTop(activity, 1));
        body.addView(second, fullWidthWithTop(activity, 3));

        setExactHeight(activity, nameField, 30);
        setExactHeight(activity, qtyField, 30);
        setExactHeight(activity, unitField, 30);
        setExactHeight(activity, priceField, 30);
        setExactHeight(activity, totalField, 30);

        setExactHeight(activity, name, 35);
        setExactHeight(activity, qty, 35);
        setExactHeight(activity, unit, 35);
        setExactHeight(activity, price, 35);
        setExactHeight(activity, total, 35);
        setExactHeight(activity, remove, 30);

        shrinkText(itemRoot, R.id.etItemName, 10.5f);
        shrinkText(itemRoot, R.id.etItemQuantity, 10f);
        shrinkText(itemRoot, R.id.etItemUnit, 10f);
        shrinkText(itemRoot, R.id.etItemPrice, 10f);
        shrinkText(itemRoot, R.id.etItemTotal, 10f);
        shrinkText(itemRoot, R.id.btnRemoveItem, 9.5f);

        body.setPadding(
                Math.min(body.getPaddingLeft(), dp(activity, 6)),
                dp(activity, 4),
                Math.min(body.getPaddingRight(), dp(activity, 6)),
                dp(activity, 4)
        );
    }

    private static void compactReceipt(Activity activity, View root) {
        View attach = root.findViewById(R.id.btnAttachReceipt);
        View preview = root.findViewById(R.id.receiptPreviewContainer);
        if (attach == null || preview == null) {
            return;
        }

        View card = nearestClassContaining(attach, "MaterialCardView");
        if (!(card instanceof ViewGroup)) {
            return;
        }

        ViewGroup cardGroup = (ViewGroup) card;
        if (cardGroup.getChildCount() > 0
                && cardGroup.getChildAt(0) instanceof ViewGroup) {
            ViewGroup inside = (ViewGroup) cardGroup.getChildAt(0);
            inside.setPadding(0, 0, 0, 0);

            for (int i = 0; i < inside.getChildCount(); i++) {
                View child = inside.getChildAt(i);
                if (child != preview && child != attach) {
                    TextView marker = findTextViewExact(child, "Attach Receipt");
                    if (marker != null) {
                        child.setVisibility(View.GONE);
                    }
                }
            }
        }

        ViewGroup.LayoutParams params = card.getLayoutParams();
        if (params instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins =
                    (ViewGroup.MarginLayoutParams) params;
            margins.topMargin = dp(activity, 2);
            margins.bottomMargin = 0;
            card.setLayoutParams(margins);
        }
    }

    private static void compactUpiResult(Activity activity, View root) {
        View resultCard = root.findViewById(R.id.upiPaymentResultCard);
        if (!(resultCard instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) resultCard;
        if (group.getChildCount() > 0
                && group.getChildAt(0) instanceof ViewGroup) {
            ViewGroup inside = (ViewGroup) group.getChildAt(0);
            inside.setPadding(
                    dp(activity, 6),
                    dp(activity, 4),
                    dp(activity, 6),
                    dp(activity, 4)
            );
        }
    }

    private static void pairInSameParent(
            Activity activity,
            View first,
            View second,
            float firstWeight,
            float secondWeight,
            int gapDp
    ) {
        if (first == null || second == null) {
            return;
        }
        ViewParent firstParent = first.getParent();
        ViewParent secondParent = second.getParent();
        if (!(firstParent instanceof ViewGroup)
                || firstParent != secondParent) {
            return;
        }

        ViewGroup parent = (ViewGroup) firstParent;
        int firstIndex = parent.indexOfChild(first);
        int secondIndex = parent.indexOfChild(second);
        int insertAt = Math.min(firstIndex, secondIndex);

        parent.removeView(first);
        parent.removeView(second);

        LinearLayout row = horizontal(activity);
        row.addView(first, weighted(firstWeight, 0));
        row.addView(second, weightedWithGap(activity, secondWeight, gapDp));
        parent.addView(row, insertAt, fullWidthWithTop(activity, 3));
    }

    private static void moveAcrossParentsIntoRow(
            Activity activity,
            View first,
            View second,
            float firstWeight,
            float secondWeight,
            int gapDp
    ) {
        if (first == null || second == null) {
            return;
        }
        ViewParent firstParent = first.getParent();
        if (!(firstParent instanceof ViewGroup)) {
            return;
        }
        ViewGroup targetParent = (ViewGroup) firstParent;
        int insertAt = targetParent.indexOfChild(first);

        detach(first);
        detach(second);

        LinearLayout row = horizontal(activity);
        row.addView(first, weighted(firstWeight, 0));
        row.addView(second, weightedWithGap(activity, secondWeight, gapDp));
        targetParent.addView(row, insertAt, fullWidthWithTop(activity, 3));
    }

    private static LinearLayout horizontal(Activity activity) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBaselineAligned(false);
        return row;
    }

    private static LinearLayout.LayoutParams weighted(float weight, int gap) {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        params.leftMargin = gap;
        return params;
    }

    private static LinearLayout.LayoutParams weightedWithGap(
            Activity activity,
            float weight,
            int gapDp
    ) {
        return weighted(weight, dp(activity, gapDp));
    }

    private static LinearLayout.LayoutParams fullWidthWithTop(
            Activity activity,
            int topDp
    ) {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        params.topMargin = dp(activity, topDp);
        return params;
    }

    private static ViewGroup findItemBody(View root) {
        if (!(root instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) root;
        if (group.getChildCount() == 1
                && group.getChildAt(0) instanceof ViewGroup) {
            return (ViewGroup) group.getChildAt(0);
        }
        return group;
    }

    private static View wrapperFor(View child) {
        if (child == null) {
            return null;
        }
        ViewParent parent = child.getParent();
        return parent instanceof View ? (View) parent : child;
    }

    private static void detach(View view) {
        if (view == null) {
            return;
        }
        ViewParent parent = view.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(view);
        }
    }

    private static View nearestClassContaining(View view, String classPart) {
        View current = view;
        while (current != null) {
            if (current.getClass().getName().contains(classPart)) {
                return current;
            }
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    private static TextView findTextViewExact(View root, String text) {
        if (root instanceof TextView) {
            CharSequence value = ((TextView) root).getText();
            if (value != null && value.toString().trim().equals(text)) {
                return (TextView) root;
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findTextViewExact(group.getChildAt(i), text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void tightenTree(Activity activity, View view) {
        if (view == null) {
            return;
        }

        ViewGroup.LayoutParams raw = view.getLayoutParams();
        if (raw instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins =
                    (ViewGroup.MarginLayoutParams) raw;
            margins.topMargin = Math.min(margins.topMargin, dp(activity, 4));
            margins.bottomMargin = Math.min(margins.bottomMargin, dp(activity, 3));
            margins.leftMargin = Math.min(margins.leftMargin, dp(activity, 4));
            margins.rightMargin = Math.min(margins.rightMargin, dp(activity, 4));
            view.setLayoutParams(margins);
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            if (!(group instanceof TextInputLayout)) {
                group.setPadding(
                        Math.min(group.getPaddingLeft(), dp(activity, 7)),
                        Math.min(group.getPaddingTop(), dp(activity, 7)),
                        Math.min(group.getPaddingRight(), dp(activity, 7)),
                        Math.min(group.getPaddingBottom(), dp(activity, 7))
                );
            }
            for (int i = 0; i < group.getChildCount(); i++) {
                tightenTree(activity, group.getChildAt(i));
            }
        }
    }

    private static void setWrapperHeight(
            Activity activity,
            View root,
            int childId,
            int heightDp
    ) {
        View child = root.findViewById(childId);
        View wrapper = wrapperFor(child);
        if (wrapper != null && wrapper != child) {
            setExactHeight(activity, wrapper, heightDp);
            wrapper.setMinimumHeight(0);
        }
    }

    private static void disableCounterForChild(View root, int childId) {
        View child = root.findViewById(childId);
        ViewParent parent = child == null ? null : child.getParent();
        if (parent instanceof TextInputLayout) {
            ((TextInputLayout) parent).setCounterEnabled(false);
        }
    }

    private static void shrinkText(View root, int id, float sizeSp) {
        View view = root.findViewById(id);
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            text.setTextSize(sizeSp);
            text.setIncludeFontPadding(false);
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
        view.setMinimumHeight(0);
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
        setExactHeight(activity, view, heightDp);
        view.setMinimumHeight(0);
        if (view instanceof FrameLayout) {
            ((FrameLayout) view).setClipToPadding(false);
        }
    }

    private static void setExactHeight(
            Activity activity,
            View view,
            int heightDp
    ) {
        if (view == null) {
            return;
        }
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
                        .getDisplayMetrics().density
        );
    }
}
