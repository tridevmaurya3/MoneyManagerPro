package com.example.moneymanagerpro.pro;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.activities.DashboardActivity;
import com.example.moneymanagerpro.activities.SplashActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Final app-wide Fluent polish.
 *
 * Dashboard is the visual source of truth. Non-dashboard screens are normalized
 * to the same compact typography, low-elevation cards, rounded controls and
 * soft semantic button surfaces without changing finance colors or business
 * logic. The pass is theme-aware and therefore does not force light system bars
 * when the app is using dark mode.
 */
public final class FluentProfessionalPolish {

    private static final String DATA_CENTER_BUTTON_TAG =
            "credit_card_data_center_fab";

    private FluentProfessionalPolish() {
    }

    public static void apply(@NonNull Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        applySystemSurfaces(activity);

        View decor = activity.getWindow().getDecorView();

        if (!(activity instanceof DashboardActivity)
                && !(activity instanceof SplashActivity)
                && decor instanceof ViewGroup) {

            applyContentBackground(activity);
            polishTree(activity, (ViewGroup) decor);

            /*
             * Several existing controllers add cards shortly after onResume().
             * One delayed pass catches those dynamic views without installing
             * a permanent layout observer on every screen.
             */
            decor.postDelayed(
                    () -> {
                        if (activity.isFinishing() || activity.isDestroyed()) {
                            return;
                        }

                        View latestDecor = activity.getWindow().getDecorView();
                        if (latestDecor instanceof ViewGroup) {
                            polishTree(activity, (ViewGroup) latestDecor);
                        }
                        styleDataCenterButton(activity);
                    },
                    260L
            );
        }

        styleDataCenterButton(activity);
    }

    private static void applySystemSurfaces(@NonNull Activity activity) {
        boolean night = isNightMode(activity);

        int appBackground = ContextCompat.getColor(
                activity,
                R.color.app_background
        );
        int surface = ContextCompat.getColor(
                activity,
                R.color.app_surface
        );

        Window window = activity.getWindow();
        window.setStatusBarColor(appBackground);
        window.setNavigationBarColor(surface);

        View decor = window.getDecorView();
        int flags = decor.getSystemUiVisibility();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (night) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (night) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            } else {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        }

        decor.setSystemUiVisibility(flags);
    }

    private static void applyContentBackground(@NonNull Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        if (content != null) {
            content.setBackgroundColor(
                    ContextCompat.getColor(
                            activity,
                            R.color.app_background
                    )
            );
        }
    }

    private static void polishTree(
            @NonNull Activity activity,
            @NonNull ViewGroup group
    ) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);

            if (child instanceof MaterialCardView) {
                normalizeCard(
                        activity,
                        (MaterialCardView) child
                );
            }

            if (child instanceof MaterialButton) {
                normalizeButton(
                        activity,
                        (MaterialButton) child
                );
            } else if (child instanceof EditText) {
                normalizeEditText(
                        activity,
                        (EditText) child
                );
            } else if (child instanceof TextView) {
                normalizeText(
                        activity,
                        (TextView) child
                );
            }

            if (child instanceof TextInputLayout) {
                normalizeInputLayout(
                        activity,
                        (TextInputLayout) child
                );
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

    private static void normalizeCard(
            @NonNull Activity activity,
            @NonNull MaterialCardView card
    ) {
        if (isSmallSquareView(activity, card)) {
            card.setCardElevation(0f);
            card.setUseCompatPadding(false);
            return;
        }

        float radius = card.getRadius();
        if (radius < dp(activity, 14)
                || radius > dp(activity, 22)) {
            card.setRadius(dp(activity, 16));
        }

        if (card.getCardElevation() > dp(activity, 1)) {
            card.setCardElevation(dp(activity, 1));
        }

        if (card.getStrokeWidth() <= 0) {
            card.setStrokeWidth(dp(activity, 1));
            card.setStrokeColor(
                    ContextCompat.getColor(
                            activity,
                            R.color.app_outline_soft
                    )
            );
        }

        card.setUseCompatPadding(false);
    }

    private static void normalizeButton(
            @NonNull Activity activity,
            @NonNull MaterialButton button
    ) {
        button.setAllCaps(false);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setElevation(0f);

        String label = safeText(button);
        boolean compactIcon = isSmallSquareView(activity, button)
                || isSymbolOnly(label);

        if (!compactIcon) {
            button.setMinHeight(dp(activity, 44));
            button.setMinimumHeight(dp(activity, 44));

            float currentSp = toSp(
                    activity,
                    button.getTextSize()
            );
            if (currentSp > 12.5f) {
                button.setTextSize(12.5f);
            }

            button.setTypeface(
                    Typeface.create(
                            "sans-serif-medium",
                            Typeface.NORMAL
                    )
            );

            if (button.getCornerRadius() < dp(activity, 12)
                    || button.getCornerRadius() > dp(activity, 18)) {
                button.setCornerRadius(dp(activity, 13));
            }

            ViewGroup.LayoutParams params = button.getLayoutParams();
            if (params != null
                    && params.height >= dp(activity, 52)
                    && params.height <= dp(activity, 66)) {
                params.height = dp(activity, 46);
                button.setLayoutParams(params);
            }

            if (!isThemeChoice(label)
                    && shouldSoftenButton(button)) {
                applyActionPalette(
                        activity,
                        button,
                        label
                );
            }
        }
    }

    private static boolean shouldSoftenButton(
            @NonNull MaterialButton button
    ) {
        ColorStateList tint = button.getBackgroundTintList();
        if (tint == null) return false;

        int color = tint.getDefaultColor();
        if (Color.alpha(color) < 220) return false;

        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);

        double luminance = ColorUtils.calculateLuminance(color);

        return hsv[1] >= 0.32f
                && luminance <= 0.58d;
    }

    private static void applyActionPalette(
            @NonNull Activity activity,
            @NonNull MaterialButton button,
            @NonNull String label
    ) {
        String normalized = label.toLowerCase();

        int backgroundRes;
        int foregroundRes;
        int outlineRes;

        if (containsAny(
                normalized,
                "delete",
                "remove",
                "disable",
                "permanent",
                "erase"
        )) {
            backgroundRes = R.color.error_surface;
            foregroundRes = R.color.error;
            outlineRes = R.color.error_outline;

        } else if (containsAny(
                normalized,
                "archive",
                "duplicate",
                "audit",
                "warning"
        )) {
            backgroundRes = R.color.warning_surface;
            foregroundRes = R.color.warning;
            outlineRes = R.color.warning_outline;

        } else if (containsAny(
                normalized,
                "merge",
                "rename",
                "planner",
                "smart",
                "advisor",
                "ai "
        )) {
            backgroundRes = R.color.purple_surface;
            foregroundRes = R.color.purple;
            outlineRes = R.color.purple_outline;

        } else if (containsAny(
                normalized,
                "save",
                "add",
                "create",
                "import",
                "restore",
                "record payment",
                "pay now",
                "backup"
        )) {
            backgroundRes = R.color.success_surface;
            foregroundRes = R.color.success;
            outlineRes = R.color.success_outline;

        } else if (containsAny(
                normalized,
                "cancel",
                "close",
                "back"
        )) {
            backgroundRes = R.color.app_surface;
            foregroundRes = R.color.app_text_primary;
            outlineRes = R.color.app_outline;

        } else {
            backgroundRes = R.color.info_surface;
            foregroundRes = R.color.info;
            outlineRes = R.color.info_outline;
        }

        int background = ContextCompat.getColor(
                activity,
                backgroundRes
        );
        int foreground = ContextCompat.getColor(
                activity,
                foregroundRes
        );
        int outline = ContextCompat.getColor(
                activity,
                outlineRes
        );

        button.setBackgroundTintList(
                ColorStateList.valueOf(background)
        );
        button.setTextColor(foreground);
        button.setIconTint(
                ColorStateList.valueOf(foreground)
        );
        button.setStrokeColor(
                ColorStateList.valueOf(outline)
        );
        button.setStrokeWidth(dp(activity, 1));
    }

    private static void normalizeInputLayout(
            @NonNull Activity activity,
            @NonNull TextInputLayout input
    ) {
        if (input.getBoxBackgroundMode()
                == TextInputLayout.BOX_BACKGROUND_NONE) {
            return;
        }

        float radius = dp(activity, 13);
        input.setBoxCornerRadii(
                radius,
                radius,
                radius,
                radius
        );
        input.setBoxBackgroundColor(
                ContextCompat.getColor(
                        activity,
                        R.color.app_surface
                )
        );
    }

    private static void normalizeEditText(
            @NonNull Activity activity,
            @NonNull EditText editText
    ) {
        float currentSp = toSp(
                activity,
                editText.getTextSize()
        );

        if (currentSp > 14f) {
            editText.setTextSize(14f);
        }

        editText.setTypeface(
                Typeface.create(
                        "sans-serif",
                        Typeface.NORMAL
                )
        );

        if (editText.getMaxLines() <= 1) {
            editText.setMinHeight(dp(activity, 48));
            editText.setMinimumHeight(dp(activity, 48));
        }
    }

    private static void normalizeText(
            @NonNull Activity activity,
            @NonNull TextView textView
    ) {
        String value = safeText(textView);
        if (value.isEmpty() || isSymbolOnly(value)) {
            return;
        }

        Typeface currentTypeface = textView.getTypeface();
        boolean bold = currentTypeface != null
                && (currentTypeface.getStyle() & Typeface.BOLD) != 0;

        textView.setTypeface(
                Typeface.create(
                        bold
                                ? "sans-serif-medium"
                                : "sans-serif",
                        Typeface.NORMAL
                )
        );

        if (isFinancialValue(value)) {
            return;
        }

        float sp = toSp(
                activity,
                textView.getTextSize()
        );

        if (sp >= 26f) {
            textView.setTextSize(26f);
        } else if (sp >= 21f) {
            textView.setTextSize(18f);
        } else if (sp >= 18f) {
            textView.setTextSize(17f);
        } else if (sp >= 16f) {
            textView.setTextSize(
                    bold ? 15.5f : 14f
            );
        } else if (sp >= 14f) {
            textView.setTextSize(13f);
        } else if (sp >= 12f) {
            textView.setTextSize(11.5f);
        }
    }

    private static void styleDataCenterButton(
            @NonNull Activity activity
    ) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        View decor = activity.getWindow().getDecorView();
        View taggedView = decor.findViewWithTag(
                DATA_CENTER_BUTTON_TAG
        );

        if (!(taggedView instanceof MaterialButton)) {
            return;
        }

        MaterialButton button = (MaterialButton) taggedView;

        int backgroundBase = ContextCompat.getColor(
                activity,
                R.color.info_surface
        );
        int background = ColorUtils.setAlphaComponent(
                backgroundBase,
                isNightMode(activity) ? 242 : 222
        );
        int accent = ContextCompat.getColor(
                activity,
                R.color.secondary
        );
        int outline = ContextCompat.getColor(
                activity,
                R.color.info_outline
        );

        button.setBackgroundTintList(
                ColorStateList.valueOf(background)
        );
        button.setTextColor(accent);
        button.setIconTint(
                ColorStateList.valueOf(accent)
        );
        button.setStrokeColor(
                ColorStateList.valueOf(outline)
        );
        button.setStrokeWidth(dp(activity, 1));
        button.setCornerRadius(dp(activity, 20));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setElevation(dp(activity, 1));
        button.setAllCaps(false);
        button.setTextSize(11f);
        button.setTypeface(
                Typeface.create(
                        "sans-serif-medium",
                        Typeface.NORMAL
                )
        );
        button.bringToFront();
    }

    private static boolean isSmallSquareView(
            @NonNull Activity activity,
            @NonNull View view
    ) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params == null
                || params.width <= 0
                || params.height <= 0) {
            return false;
        }

        int max = Math.max(params.width, params.height);
        int difference = Math.abs(params.width - params.height);

        return max <= dp(activity, 72)
                && difference <= dp(activity, 8);
    }

    private static boolean isNightMode(@NonNull Activity activity) {
        int mask = activity.getResources()
                .getConfiguration()
                .uiMode
                & Configuration.UI_MODE_NIGHT_MASK;

        return mask == Configuration.UI_MODE_NIGHT_YES;
    }

    private static boolean isFinancialValue(@NonNull String value) {
        return value.contains("₹")
                || value.contains("$")
                || value.contains("€")
                || value.contains("£")
                || value.matches(".*\\d+(?:[.,]\\d+)?%.*");
    }

    private static boolean isThemeChoice(@NonNull String value) {
        String normalized = value.trim().toLowerCase();

        return normalized.equals("system")
                || normalized.equals("light")
                || normalized.equals("dark")
                || normalized.equals("system theme")
                || normalized.equals("light theme")
                || normalized.equals("dark theme");
    }

    private static boolean isSymbolOnly(@NonNull String value) {
        String normalized = value.trim();
        if (normalized.isEmpty()) return true;
        if (normalized.length() > 3) return false;

        return normalized.matches("[←→‹›<>+−–—⋮☰✓×✕•]+")
                || normalized.equals("₹");
    }

    private static boolean containsAny(
            @NonNull String value,
            @NonNull String... needles
    ) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private static String safeText(@NonNull TextView view) {
        CharSequence value = view.getText();
        return value == null ? "" : value.toString().trim();
    }

    private static float toSp(
            @NonNull Activity activity,
            float pixels
    ) {
        return pixels
                / activity.getResources()
                .getDisplayMetrics()
                .scaledDensity;
    }

    private static int dp(
            @NonNull Activity activity,
            int value
    ) {
        return Math.round(
                value
                        * activity.getResources()
                        .getDisplayMetrics()
                        .density
        );
    }
}
