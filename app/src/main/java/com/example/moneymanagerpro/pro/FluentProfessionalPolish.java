package com.example.moneymanagerpro.pro;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Safe Fluent-style polish that preserves each screen's semantic colors while
 * standardising shape, elevation, controls and light system surfaces.
 */
public final class FluentProfessionalPolish {

    private static final int SYSTEM_SURFACE =
            Color.rgb(247, 249, 252);

    private static final String DATA_CENTER_BUTTON_TAG =
            "credit_card_data_center_fab";

    /*
     * Translucent Fluent surface used only by the floating Data Center action.
     * The alpha keeps underlying content softly visible without reducing
     * readability of the blue label and icon.
     */
    private static final int DATA_CENTER_GLASS_SURFACE =
            Color.argb(218, 238, 247, 255);

    private static final int DATA_CENTER_ACCENT =
            Color.rgb(15, 108, 189);

    private static final int DATA_CENTER_OUTLINE =
            Color.rgb(137, 190, 232);

    private FluentProfessionalPolish() {
    }

    public static void apply(@NonNull Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        Window window = activity.getWindow();
        window.setStatusBarColor(SYSTEM_SURFACE);
        window.setNavigationBarColor(SYSTEM_SURFACE);

        View decor = window.getDecorView();
        int flags = decor.getSystemUiVisibility();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }

        decor.setSystemUiVisibility(flags);

        if (decor instanceof ViewGroup) {
            polishTree(
                    activity,
                    (ViewGroup) decor
            );
        }

        /*
         * CreditCardActivity creates its floating Data Center button from a
         * posted onResume callback. Try immediately and once again shortly
         * afterwards so the Fluent glass treatment is reliable regardless of
         * lifecycle callback ordering.
         */
        styleDataCenterButton(activity);

        decor.postDelayed(
                () -> styleDataCenterButton(activity),
                100L
        );
    }

    private static void polishTree(
            @NonNull Activity activity,
            @NonNull ViewGroup group
    ) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);

            if (child instanceof MaterialCardView) {
                MaterialCardView card =
                        (MaterialCardView) child;

                if (card.getRadius() < dp(activity, 12)) {
                    card.setRadius(
                            dp(activity, 12)
                    );
                }

                if (card.getCardElevation() > dp(activity, 2)) {
                    card.setCardElevation(
                            dp(activity, 1)
                    );
                }

                card.setUseCompatPadding(false);
            }

            if (child instanceof MaterialButton) {
                MaterialButton button =
                        (MaterialButton) child;

                button.setAllCaps(false);
                button.setInsetTop(0);
                button.setInsetBottom(0);
                button.setMinHeight(
                        dp(activity, 40)
                );

                if (button.getCornerRadius()
                        < dp(activity, 11)) {

                    button.setCornerRadius(
                            dp(activity, 11)
                    );
                }
            }

            if (child instanceof TextInputLayout) {
                TextInputLayout input =
                        (TextInputLayout) child;

                if (input.getBoxBackgroundMode()
                        != TextInputLayout.BOX_BACKGROUND_NONE) {

                    float radius =
                            dp(activity, 12);

                    input.setBoxCornerRadii(
                            radius,
                            radius,
                            radius,
                            radius
                    );
                }
            }

            if (child instanceof RecyclerView) {
                RecyclerView recyclerView =
                        (RecyclerView) child;

                recyclerView.setClipToPadding(false);
                recyclerView.setOverScrollMode(
                        View.OVER_SCROLL_NEVER
                );
            }

            if (child instanceof NestedScrollView
                    || child instanceof ScrollView) {

                child.setOverScrollMode(
                        View.OVER_SCROLL_NEVER
                );
            }

            if (child instanceof ViewGroup) {
                polishTree(
                        activity,
                        (ViewGroup) child
                );
            }
        }
    }

    private static void styleDataCenterButton(
            @NonNull Activity activity
    ) {
        if (activity.isFinishing()
                || activity.isDestroyed()) {
            return;
        }

        View decor =
                activity.getWindow()
                        .getDecorView();

        View taggedView =
                decor.findViewWithTag(
                        DATA_CENTER_BUTTON_TAG
                );

        if (!(taggedView instanceof MaterialButton)) {
            return;
        }

        MaterialButton button =
                (MaterialButton) taggedView;

        button.setBackgroundTintList(
                ColorStateList.valueOf(
                        DATA_CENTER_GLASS_SURFACE
                )
        );

        button.setTextColor(
                DATA_CENTER_ACCENT
        );

        button.setIconTint(
                ColorStateList.valueOf(
                        DATA_CENTER_ACCENT
                )
        );

        button.setStrokeColor(
                ColorStateList.valueOf(
                        DATA_CENTER_OUTLINE
                )
        );

        button.setStrokeWidth(
                dp(activity, 1)
        );

        button.setCornerRadius(
                dp(activity, 20)
        );

        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setElevation(
                dp(activity, 1)
        );

        button.setAllCaps(false);
        button.bringToFront();
    }

    private static int dp(
            @NonNull Activity activity,
            int value
    ) {
        return Math.round(
                value
                        * activity
                        .getResources()
                        .getDisplayMetrics()
                        .density
        );
    }
}
