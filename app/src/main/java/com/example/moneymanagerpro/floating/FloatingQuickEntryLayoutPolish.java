package com.example.moneymanagerpro.floating;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.reflect.Field;

/**
 * Presentation-only polish for the floating expense form.
 *
 * Keeps the existing save/data logic untouched while moving the existing
 * + Item action under the item list so it naturally follows newly added rows.
 */
final class FloatingQuickEntryLayoutPolish {

    private static final String FOOTER_TAG = "money_manager_item_footer";

    private FloatingQuickEntryLayoutPolish() {
    }

    static void apply(FloatingQuickEntryFormOverlay overlay) {
        if (overlay == null) {
            return;
        }

        try {
            ScrollView bodyScroll = (ScrollView) readField(overlay, "bodyScroll");
            LinearLayout itemsContainer = (LinearLayout) readField(overlay, "itemsContainer");
            TextView addItemButton = (TextView) readField(overlay, "addItemButton");
            TextView itemsTotalView = (TextView) readField(overlay, "itemsTotalView");

            if (bodyScroll == null || itemsContainer == null
                    || addItemButton == null || itemsTotalView == null) {
                return;
            }

            ViewParent itemParent = itemsContainer.getParent();
            if (!(itemParent instanceof LinearLayout)) {
                return;
            }

            LinearLayout body = (LinearLayout) itemParent;
            if (FOOTER_TAG.equals(body.getTag())) {
                return;
            }
            body.setTag(FOOTER_TAG);

            Context context = body.getContext();

            // Remove + Item from the top heading row.
            ViewParent oldButtonParent = addItemButton.getParent();
            if (oldButtonParent instanceof ViewGroup) {
                ((ViewGroup) oldButtonParent).removeView(addItemButton);
            }
            if (oldButtonParent instanceof View) {
                View headingRow = (View) oldButtonParent;
                ViewGroup.LayoutParams headingParams = headingRow.getLayoutParams();
                if (headingParams != null) {
                    headingParams.height = dp(context, 28);
                    headingRow.setLayoutParams(headingParams);
                }
            }

            // Reuse the existing total and button in a compact footer below the
            // current last item. New item cards are inserted before this footer,
            // so the button automatically keeps moving down with the list.
            ViewParent oldTotalParent = itemsTotalView.getParent();
            if (oldTotalParent instanceof ViewGroup) {
                ((ViewGroup) oldTotalParent).removeView(itemsTotalView);
            }

            LinearLayout footer = new LinearLayout(context);
            footer.setOrientation(LinearLayout.HORIZONTAL);
            footer.setGravity(Gravity.CENTER_VERTICAL);
            footer.setPadding(0, dp(context, 3), 0, dp(context, 3));

            itemsTotalView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams totalParams = new LinearLayout.LayoutParams(
                    0,
                    dp(context, 30),
                    1f
            );
            footer.addView(itemsTotalView, totalParams);

            LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                    dp(context, 76),
                    dp(context, 32)
            );
            buttonParams.leftMargin = dp(context, 6);
            footer.addView(addItemButton, buttonParams);

            int itemIndex = body.indexOfChild(itemsContainer);
            LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            footerParams.topMargin = dp(context, 2);
            footerParams.bottomMargin = dp(context, 5);
            body.addView(footer, Math.max(0, itemIndex + 1), footerParams);

            // Prevent the lower edge of the last item/footer from being clipped
            // inside the compact scrolling viewport.
            body.setClipChildren(false);
            body.setClipToPadding(false);
            body.setPadding(
                    body.getPaddingLeft(),
                    body.getPaddingTop(),
                    body.getPaddingRight(),
                    dp(context, 14)
            );
            itemsContainer.setClipChildren(false);
            itemsContainer.setClipToPadding(false);
            bodyScroll.setClipToPadding(false);
            bodyScroll.setPadding(
                    bodyScroll.getPaddingLeft(),
                    bodyScroll.getPaddingTop(),
                    bodyScroll.getPaddingRight(),
                    dp(context, 6)
            );

            itemsContainer.setOnHierarchyChangeListener(
                    new ViewGroup.OnHierarchyChangeListener() {
                        @Override
                        public void onChildViewAdded(View parent, View child) {
                            footer.post(() -> {
                                body.requestLayout();
                                int target = Math.max(
                                        0,
                                        footer.getBottom()
                                                - bodyScroll.getHeight()
                                                + dp(context, 18)
                                );
                                bodyScroll.smoothScrollTo(0, target);
                            });
                        }

                        @Override
                        public void onChildViewRemoved(View parent, View child) {
                            footer.post(body::requestLayout);
                        }
                    }
            );

            body.requestLayout();
            bodyScroll.requestLayout();
        } catch (ReflectiveOperationException ignored) {
            // Presentation polish must never affect transaction entry/saving.
        }
    }

    private static Object readField(
            FloatingQuickEntryFormOverlay overlay,
            String name
    ) throws ReflectiveOperationException {
        Field field = FloatingQuickEntryFormOverlay.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(overlay);
    }

    private static int dp(Context context, int value) {
        return Math.round(
                value
                        * context.getResources()
                        .getDisplayMetrics()
                        .density
        );
    }
}
