package com.example.moneymanagerpro.pro;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.moneymanagerpro.activities.DashboardActivity;
import com.example.moneymanagerpro.activities.SplashActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputLayout;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * UI-only compact form pass for MoneyManagerPro.
 *
 * This class deliberately does not read or write finance data, databases,
 * repositories, listeners or calculations. It only tightens the already
 * inflated Android views so every data-entry screen follows the same compact
 * Fluent form language as the Loan screen.
 */
public final class CompactFormPolish {

    private static final int FIELD_HEIGHT_DP = 48;
    private static final int BUTTON_HEIGHT_DP = 46;

    private CompactFormPolish() {
    }

    public static void apply(@NonNull Activity activity) {
        if (activity.isFinishing()
                || activity.isDestroyed()
                || activity instanceof DashboardActivity
                || activity instanceof SplashActivity) {
            return;
        }

        View decor = activity.getWindow().getDecorView();
        if (!(decor instanceof ViewGroup)) {
            return;
        }

        ViewGroup root = (ViewGroup) decor;
        if (countFormControls(root, 1) == 0) {
            return;
        }

        compactTree(activity, root);

        // Some existing controllers add fields shortly after onResume().
        decor.postDelayed(() -> {
            if (!activity.isFinishing() && !activity.isDestroyed()) {
                View latest = activity.getWindow().getDecorView();
                if (latest instanceof ViewGroup
                        && countFormControls(latest, 1) > 0) {
                    compactTree(activity, (ViewGroup) latest);
                }
            }
        }, 320L);
    }

    private static void compactTree(
            @NonNull Activity activity,
            @NonNull ViewGroup group
    ) {
        int controlsInGroup = countFormControls(group, 6);
        if (controlsInGroup >= 2) {
            compactContainer(activity, group);
        }

        for (int index = 0; index < group.getChildCount(); index++) {
            View child = group.getChildAt(index);

            if (child instanceof TextInputLayout) {
                TextInputLayout input = (TextInputLayout) child;
                compactInputLayout(activity, input);
                hideRedundantFieldText(activity, input);
            }

            if (child instanceof EditText) {
                compactEditText(activity, (EditText) child);
            }

            if (child instanceof MaterialButton) {
                compactButton(activity, (MaterialButton) child);
            }

            if (child instanceof MaterialCardView
                    && countFormControls(child, 2) >= 2) {
                MaterialCardView card = (MaterialCardView) child;
                card.setCardElevation(0f);
                card.setUseCompatPadding(false);
            }

            if (child instanceof TextView
                    && !(child instanceof EditText)
                    && isFieldSideText(group, index)) {
                capVerticalMargins(activity, child, 10, 2);
            }

            compactSpacer(activity, group, child);

            if (child instanceof ViewGroup) {
                compactTree(activity, (ViewGroup) child);
            }
        }
    }

    private static void compactInputLayout(
            @NonNull Activity activity,
            @NonNull TextInputLayout input
    ) {
        capVerticalMargins(activity, input, 7, 3);

        if (input.getBoxBackgroundMode()
                != TextInputLayout.BOX_BACKGROUND_NONE) {
            float radius = dp(activity, 12);
            input.setBoxCornerRadii(radius, radius, radius, radius);
        }
    }

    private static void compactEditText(
            @NonNull Activity activity,
            @NonNull EditText editText
    ) {
        if (editText.getMaxLines() > 1) {
            capVerticalMargins(activity, editText, 7, 3);
            return;
        }

        editText.setMinHeight(dp(activity, FIELD_HEIGHT_DP));
        editText.setMinimumHeight(dp(activity, FIELD_HEIGHT_DP));

        ViewGroup.LayoutParams params = editText.getLayoutParams();
        if (params != null
                && params.height >= dp(activity, 50)
                && params.height <= dp(activity, 80)) {
            params.height = dp(activity, FIELD_HEIGHT_DP);
            editText.setLayoutParams(params);
        }

        int top = Math.min(editText.getPaddingTop(), dp(activity, 9));
        int bottom = Math.min(editText.getPaddingBottom(), dp(activity, 9));
        editText.setPaddingRelative(
                editText.getPaddingStart(),
                top,
                editText.getPaddingEnd(),
                bottom
        );

        capVerticalMargins(activity, editText, 7, 3);
    }

    private static void compactButton(
            @NonNull Activity activity,
            @NonNull MaterialButton button
    ) {
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setMinHeight(dp(activity, 44));
        button.setMinimumHeight(dp(activity, 44));

        ViewGroup.LayoutParams params = button.getLayoutParams();
        if (params != null
                && params.height >= dp(activity, 50)
                && params.height <= dp(activity, 72)) {
            params.height = dp(activity, BUTTON_HEIGHT_DP);
            button.setLayoutParams(params);
        }

        capVerticalMargins(activity, button, 10, 4);
    }

    private static void compactContainer(
            @NonNull Activity activity,
            @NonNull ViewGroup group
    ) {
        if (group instanceof TextInputLayout
                || group instanceof RecyclerView
                || group instanceof ScrollView
                || group instanceof NestedScrollView) {
            return;
        }

        int maxHorizontal = dp(activity, 14);
        int maxTop = dp(activity, 12);
        int maxBottom = dp(activity, 20);

        int start = Math.min(group.getPaddingStart(), maxHorizontal);
        int end = Math.min(group.getPaddingEnd(), maxHorizontal);
        int top = Math.min(group.getPaddingTop(), maxTop);
        int bottom = Math.min(group.getPaddingBottom(), maxBottom);

        if (start != group.getPaddingStart()
                || end != group.getPaddingEnd()
                || top != group.getPaddingTop()
                || bottom != group.getPaddingBottom()) {
            group.setPaddingRelative(start, top, end, bottom);
        }
    }

    /**
     * Removes only genuinely duplicate field-side text. A TextView with an id
     * is never hidden because it may be controlled by existing screen logic.
     * The TextInputLayout hint remains as the accessible field label.
     */
    private static void hideRedundantFieldText(
            @NonNull Activity activity,
            @NonNull TextInputLayout input
    ) {
        CharSequence hintValue = input.getHint();
        String hint = hintValue == null ? "" : hintValue.toString().trim();
        if (hint.isEmpty()) {
            return;
        }

        ViewParent parent = input.getParent();
        if (!(parent instanceof ViewGroup)) {
            return;
        }

        ViewGroup container = (ViewGroup) parent;
        int inputIndex = container.indexOfChild(input);
        if (inputIndex <= 0) {
            return;
        }

        View previous = container.getChildAt(inputIndex - 1);

        // Common pattern: Label -> helper sentence -> TextInputLayout.
        if (previous instanceof TextView
                && !(previous instanceof EditText)
                && inputIndex >= 2) {
            View possibleLabel = container.getChildAt(inputIndex - 2);
            if (possibleLabel instanceof TextView
                    && !(possibleLabel instanceof EditText)) {
                TextView helper = (TextView) previous;
                TextView label = (TextView) possibleLabel;
                if (canHide(label)
                        && canHide(helper)
                        && isDuplicateLabel(label.getText(), hint)
                        && isGenericHelper(activity, helper)) {
                    label.setVisibility(View.GONE);
                    helper.setVisibility(View.GONE);
                    return;
                }
            }
        }

        // Simpler pattern: Label -> TextInputLayout.
        if (previous instanceof TextView
                && !(previous instanceof EditText)) {
            TextView label = (TextView) previous;
            if (canHide(label)
                    && isDuplicateLabel(label.getText(), hint)) {
                label.setVisibility(View.GONE);
            }
        }
    }

    private static boolean canHide(@NonNull TextView view) {
        if (view.getId() != View.NO_ID) {
            return false;
        }
        String value = safeText(view).toLowerCase(Locale.ROOT);
        return !containsAny(
                value,
                "warning",
                "important",
                "required",
                "error",
                "caution"
        );
    }

    private static boolean isGenericHelper(
            @NonNull Activity activity,
            @NonNull TextView view
    ) {
        String text = safeText(view);
        if (text.isEmpty() || text.length() > 170) {
            return false;
        }

        float sp = view.getTextSize()
                / activity.getResources().getDisplayMetrics().scaledDensity;
        Typeface typeface = view.getTypeface();
        boolean bold = typeface != null
                && (typeface.getStyle() & Typeface.BOLD) != 0;

        return sp <= 12.5f && !bold;
    }

    private static boolean isDuplicateLabel(
            CharSequence labelValue,
            @NonNull String hint
    ) {
        String label = canonical(labelValue == null ? "" : labelValue.toString());
        String fieldHint = canonical(hint);
        if (label.isEmpty() || fieldHint.isEmpty()) {
            return false;
        }

        if (label.equals(fieldHint)
                || (label.length() >= 4 && fieldHint.contains(label))
                || (fieldHint.length() >= 4 && label.contains(fieldHint))) {
            return true;
        }

        Set<String> labelTokens = tokens(label);
        Set<String> hintTokens = tokens(fieldHint);
        if (labelTokens.isEmpty() || hintTokens.isEmpty()) {
            return false;
        }

        int common = 0;
        for (String token : labelTokens) {
            if (hintTokens.contains(token)) {
                common++;
            }
        }

        int smaller = Math.min(labelTokens.size(), hintTokens.size());
        return smaller == 1
                ? common == 1
                : common >= 2 && common * 2 >= smaller;
    }

    @NonNull
    private static String canonical(@NonNull String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\b(enter|select|choose|pick|add|provide|please|your|the|a|an|new|current|type)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    @NonNull
    private static Set<String> tokens(@NonNull String value) {
        Set<String> result = new HashSet<>();
        for (String token : value.split(" ")) {
            String clean = token.trim();
            if (clean.length() >= 2) {
                result.add(clean);
            }
        }
        return result;
    }

    private static boolean isFieldSideText(
            @NonNull ViewGroup parent,
            int index
    ) {
        if (index + 1 < parent.getChildCount()) {
            View next = parent.getChildAt(index + 1);
            if (next instanceof TextInputLayout || next instanceof EditText) {
                return true;
            }
        }
        if (index + 2 < parent.getChildCount()) {
            View next = parent.getChildAt(index + 1);
            View afterNext = parent.getChildAt(index + 2);
            return next instanceof TextView
                    && afterNext instanceof TextInputLayout;
        }
        return false;
    }

    private static void compactSpacer(
            @NonNull Activity activity,
            @NonNull ViewGroup parent,
            @NonNull View child
    ) {
        if (child.getClass() != View.class
                || child.getId() != View.NO_ID
                || child.getBackground() != null
                || countFormControls(parent, 2) < 2) {
            return;
        }

        ViewGroup.LayoutParams params = child.getLayoutParams();
        if (params != null
                && params.height >= dp(activity, 12)
                && params.height <= dp(activity, 40)) {
            params.height = dp(activity, 6);
            child.setLayoutParams(params);
        }
    }

    private static void capVerticalMargins(
            @NonNull Activity activity,
            @NonNull View view,
            int maxTopDp,
            int maxBottomDp
    ) {
        ViewGroup.LayoutParams rawParams = view.getLayoutParams();
        if (!(rawParams instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }

        ViewGroup.MarginLayoutParams params =
                (ViewGroup.MarginLayoutParams) rawParams;
        int topLimit = dp(activity, maxTopDp);
        int bottomLimit = dp(activity, maxBottomDp);
        boolean changed = false;

        if (params.topMargin > topLimit) {
            params.topMargin = topLimit;
            changed = true;
        }
        if (params.bottomMargin > bottomLimit) {
            params.bottomMargin = bottomLimit;
            changed = true;
        }

        if (changed) {
            view.setLayoutParams(params);
        }
    }

    private static int countFormControls(
            @NonNull View view,
            int limit
    ) {
        if (view instanceof TextInputLayout) {
            return 1;
        }
        if (view instanceof EditText) {
            return 1;
        }
        if (!(view instanceof ViewGroup)) {
            return 0;
        }

        ViewGroup group = (ViewGroup) view;
        int count = 0;
        for (int index = 0; index < group.getChildCount(); index++) {
            count += countFormControls(group.getChildAt(index), limit - count);
            if (count >= limit) {
                return count;
            }
        }
        return count;
    }

    @NonNull
    private static String safeText(@NonNull TextView view) {
        CharSequence text = view.getText();
        return text == null ? "" : text.toString().trim();
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

    private static int dp(@NonNull Activity activity, int value) {
        return Math.round(
                value * activity.getResources().getDisplayMetrics().density
        );
    }
}
