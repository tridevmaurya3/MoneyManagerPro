package com.example.moneymanagerpro.security;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

/**
 * Adds inactivity auto-lock controls to SettingsActivity without changing the
 * existing PIN-management behaviour.
 */
public final class AutoLockSettingsController {

    private static final String CARD_TAG =
            "money_manager_auto_lock_settings_card";

    private static final String HEADING_TAG =
            "money_manager_auto_lock_settings_heading";

    private static final String SUBTITLE_TAG =
            "money_manager_auto_lock_settings_subtitle";

    private static final String KEY_PIN =
            "app_pin";

    private static final String KEY_PIN_ENABLED =
            "pin_enabled";

    private final Activity activity;

    private final MaterialButton[] timeoutButtons =
            new MaterialButton[4];

    private final int[] timeoutMinutes = {
            1,
            2,
            5,
            10
    };

    private TextView txtAutoLockStatus;

    public AutoLockSettingsController(
            @NonNull Activity activity
    ) {
        this.activity = activity;
    }

    public void attach() {
        LinearLayout mainContainer =
                findMainContainer();

        if (mainContainer == null) {
            return;
        }

        View existingCard =
                mainContainer.findViewWithTag(
                        CARD_TAG
                );

        if (existingCard == null) {
            insertAutoLockSection(
                    mainContainer
            );
        }

        refresh();
    }

    public void refresh() {
        if (txtAutoLockStatus == null) {
            return;
        }

        int selectedMinutes =
                AppInactivityLockManager
                        .getTimeoutMinutes(
                                activity
                        );

        boolean pinEnabled =
                isPinEnabled();

        if (pinEnabled) {
            txtAutoLockStatus.setText(
                    "Active: app locks after "
                            + selectedMinutes
                            + (selectedMinutes == 1
                            ? " minute"
                            : " minutes")
                            + " without touch or keyboard activity."
            );

            txtAutoLockStatus.setTextColor(
                    color(
                            R.color.success
                    )
            );

        } else {
            txtAutoLockStatus.setText(
                    "Saved: "
                            + selectedMinutes
                            + (selectedMinutes == 1
                            ? " minute. Enable PIN Lock to activate inactivity locking."
                            : " minutes. Enable PIN Lock to activate inactivity locking.")
            );

            txtAutoLockStatus.setTextColor(
                    color(
                            R.color.app_text_secondary
                    )
            );
        }

        for (int index = 0;
             index < timeoutButtons.length;
             index++) {

            styleTimeoutButton(
                    timeoutButtons[index],
                    timeoutMinutes[index]
                            == selectedMinutes
            );
        }
    }

    private void insertAutoLockSection(
            @NonNull LinearLayout mainContainer
    ) {
        int insertionIndex =
                findBackupHeadingIndex(
                        mainContainer
                );

        if (insertionIndex < 0) {
            insertionIndex =
                    mainContainer.getChildCount();
        }

        TextView heading =
                new TextView(activity);

        heading.setTag(HEADING_TAG);
        heading.setText("Inactivity Auto-Lock");
        heading.setTextSize(18);
        heading.setTextColor(
                color(
                        R.color.app_text_primary
                )
        );
        heading.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        LinearLayout.LayoutParams headingParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        headingParams.setMargins(
                0,
                dp(25),
                0,
                0
        );

        heading.setLayoutParams(
                headingParams
        );

        TextView subtitle =
                new TextView(activity);

        subtitle.setTag(SUBTITLE_TAG);
        subtitle.setText(
                "Choose how long the app may remain untouched before the PIN screen opens"
        );
        subtitle.setTextSize(11);
        subtitle.setTextColor(
                color(
                        R.color.app_text_secondary
                )
        );

        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        subtitleParams.setMargins(
                0,
                dp(3),
                0,
                0
        );

        subtitle.setLayoutParams(
                subtitleParams
        );

        MaterialCardView card =
                createAutoLockCard();

        mainContainer.addView(
                heading,
                insertionIndex
        );

        mainContainer.addView(
                subtitle,
                insertionIndex + 1
        );

        mainContainer.addView(
                card,
                insertionIndex + 2
        );
    }

    @NonNull
    private MaterialCardView createAutoLockCard() {
        MaterialCardView card =
                new MaterialCardView(activity);

        card.setTag(CARD_TAG);
        card.setCardBackgroundColor(
                color(
                        R.color.info_surface
                )
        );
        card.setRadius(dp(20));
        card.setCardElevation(dp(1));
        card.setStrokeColor(
                color(
                        R.color.info_outline
                )
        );
        card.setStrokeWidth(dp(1));

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        cardParams.setMargins(
                0,
                dp(12),
                0,
                0
        );

        card.setLayoutParams(cardParams);

        LinearLayout content =
                new LinearLayout(activity);

        content.setOrientation(
                LinearLayout.VERTICAL
        );
        content.setPadding(
                dp(16),
                dp(16),
                dp(16),
                dp(16)
        );

        LinearLayout header =
                new LinearLayout(activity);

        header.setOrientation(
                LinearLayout.HORIZONTAL
        );
        header.setGravity(
                Gravity.CENTER_VERTICAL
        );

        MaterialCardView iconCard =
                new MaterialCardView(activity);

        iconCard.setCardBackgroundColor(
                color(
                        R.color.app_surface
                )
        );
        iconCard.setRadius(dp(14));
        iconCard.setCardElevation(0f);
        iconCard.setStrokeColor(
                color(
                        R.color.info_outline
                )
        );
        iconCard.setStrokeWidth(dp(1));

        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(
                        dp(46),
                        dp(46)
                );

        iconCard.setLayoutParams(iconParams);

        TextView icon =
                new TextView(activity);

        icon.setText("◷");
        icon.setGravity(Gravity.CENTER);
        icon.setTextSize(21);
        icon.setTextColor(
                color(
                        R.color.secondary
                )
        );
        icon.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        iconCard.addView(icon);
        header.addView(iconCard);

        LinearLayout headerText =
                new LinearLayout(activity);

        headerText.setOrientation(
                LinearLayout.VERTICAL
        );

        LinearLayout.LayoutParams headerTextParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        headerTextParams.setMargins(
                dp(12),
                0,
                0,
                0
        );

        headerText.setLayoutParams(
                headerTextParams
        );

        TextView title =
                new TextView(activity);

        title.setText("Lock after no activity");
        title.setTextSize(15);
        title.setTextColor(
                color(
                        R.color.app_text_primary
                )
        );
        title.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        headerText.addView(title);

        TextView detail =
                new TextView(activity);

        detail.setText(
                "Touch, typing and scrolling restart the timer. Background time also counts."
        );
        detail.setTextSize(10);
        detail.setTextColor(
                color(
                        R.color.app_text_secondary
                )
        );
        detail.setLineSpacing(
                dp(2),
                1f
        );

        LinearLayout.LayoutParams detailParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        detailParams.setMargins(
                0,
                dp(3),
                0,
                0
        );

        detail.setLayoutParams(
                detailParams
        );

        headerText.addView(detail);
        header.addView(headerText);
        content.addView(header);

        txtAutoLockStatus =
                new TextView(activity);

        txtAutoLockStatus.setTextSize(11);
        txtAutoLockStatus.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );
        txtAutoLockStatus.setLineSpacing(
                dp(2),
                1f
        );

        LinearLayout.LayoutParams statusParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        statusParams.setMargins(
                0,
                dp(14),
                0,
                0
        );

        txtAutoLockStatus.setLayoutParams(
                statusParams
        );

        content.addView(
                txtAutoLockStatus
        );

        LinearLayout firstRow =
                createButtonRow();

        LinearLayout secondRow =
                createButtonRow();

        LinearLayout.LayoutParams firstRowParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        firstRowParams.setMargins(
                0,
                dp(13),
                0,
                0
        );

        firstRow.setLayoutParams(
                firstRowParams
        );

        LinearLayout.LayoutParams secondRowParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        secondRowParams.setMargins(
                0,
                dp(8),
                0,
                0
        );

        secondRow.setLayoutParams(
                secondRowParams
        );

        for (int index = 0;
             index < timeoutMinutes.length;
             index++) {

            int minutes =
                    timeoutMinutes[index];

            MaterialButton button =
                    createTimeoutButton(
                            minutes
                    );

            timeoutButtons[index] = button;

            if (index < 2) {
                firstRow.addView(button);
            } else {
                secondRow.addView(button);
            }
        }

        content.addView(firstRow);
        content.addView(secondRow);

        TextView note =
                new TextView(activity);

        note.setText(
                "For security, the timer works only with a valid four-digit PIN. Unlock remains available through PIN, biometric or verified email flow."
        );
        note.setTextSize(9);
        note.setTextColor(
                color(
                        R.color.app_text_secondary
                )
        );
        note.setLineSpacing(
                dp(2),
                1f
        );

        LinearLayout.LayoutParams noteParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        noteParams.setMargins(
                0,
                dp(11),
                0,
                0
        );

        note.setLayoutParams(noteParams);

        content.addView(note);
        card.addView(content);

        return card;
    }

    @NonNull
    private LinearLayout createButtonRow() {
        LinearLayout row =
                new LinearLayout(activity);

        row.setOrientation(
                LinearLayout.HORIZONTAL
        );

        return row;
    }

    @NonNull
    private MaterialButton createTimeoutButton(
            int minutes
    ) {
        MaterialButton button =
                new MaterialButton(activity);

        button.setText(
                minutes
                        + (minutes == 1
                        ? " minute"
                        : " minutes")
        );
        button.setTextSize(12);
        button.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );
        button.setCornerRadius(dp(15));
        button.setStrokeWidth(dp(1));
        button.setMinHeight(dp(50));
        button.setMinimumHeight(dp(50));
        button.setGravity(Gravity.CENTER);
        button.setInsetTop(0);
        button.setInsetBottom(0);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        0,
                        dp(50),
                        1f
                );

        params.setMargins(
                dp(4),
                0,
                dp(4),
                0
        );

        button.setLayoutParams(params);

        button.setOnClickListener(
                view -> saveTimeout(
                        minutes
                )
        );

        BubbleTouchAnimator.apply(button);

        return button;
    }

    private void saveTimeout(
            int minutes
    ) {
        boolean saved =
                AppInactivityLockManager
                        .saveTimeoutMinutes(
                                activity,
                                minutes
                        );

        if (!saved) {
            Toast.makeText(
                    activity,
                    "Auto-lock time could not be saved",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        refresh();

        Toast.makeText(
                activity,
                "Auto-lock set to "
                        + minutes
                        + (minutes == 1
                        ? " minute"
                        : " minutes"),
                Toast.LENGTH_SHORT
        ).show();
    }

    private void styleTimeoutButton(
            @Nullable MaterialButton button,
            boolean selected
    ) {
        if (button == null) {
            return;
        }

        int backgroundColor =
                color(
                        selected
                                ? R.color.secondary
                                : R.color.app_surface
                );

        int textColor =
                color(
                        selected
                                ? R.color.white
                                : R.color.secondary
                );

        int strokeColor =
                color(
                        selected
                                ? R.color.secondary
                                : R.color.info_outline
                );

        button.setBackgroundTintList(
                ColorStateList.valueOf(
                        backgroundColor
                )
        );

        button.setTextColor(textColor);

        button.setStrokeColor(
                ColorStateList.valueOf(
                        strokeColor
                )
        );

        button.setAlpha(
                selected
                        ? 1f
                        : 0.94f
        );
    }

    private boolean isPinEnabled() {
        SharedPreferences preferences =
                activity.getSharedPreferences(
                        AppInactivityLockManager.SECURITY_PREFERENCES,
                        Context.MODE_PRIVATE
                );

        boolean enabled =
                preferences.getBoolean(
                        KEY_PIN_ENABLED,
                        false
                );

        String pin =
                preferences.getString(
                        KEY_PIN,
                        ""
                );

        return enabled
                && pin != null
                && pin.matches("\\d{4}");
    }

    private int findBackupHeadingIndex(
            @NonNull LinearLayout mainContainer
    ) {
        for (int index = 0;
             index < mainContainer.getChildCount();
             index++) {

            View child =
                    mainContainer.getChildAt(index);

            if (!(child instanceof TextView)) {
                continue;
            }

            CharSequence text =
                    ((TextView) child).getText();

            if (text != null
                    && "Backup & Restore"
                    .contentEquals(text)) {

                return index;
            }
        }

        return -1;
    }

    @Nullable
    private LinearLayout findMainContainer() {
        View content =
                activity.findViewById(
                        android.R.id.content
                );

        return findNestedScrollLinearLayout(
                content
        );
    }

    @Nullable
    private LinearLayout findNestedScrollLinearLayout(
            @Nullable View view
    ) {
        if (view == null) {
            return null;
        }

        if (view instanceof NestedScrollView) {
            NestedScrollView scrollView =
                    (NestedScrollView) view;

            if (scrollView.getChildCount() > 0
                    && scrollView.getChildAt(0)
                    instanceof LinearLayout) {

                return (LinearLayout)
                        scrollView.getChildAt(0);
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup group =
                    (ViewGroup) view;

            for (int index = 0;
                 index < group.getChildCount();
                 index++) {

                LinearLayout result =
                        findNestedScrollLinearLayout(
                                group.getChildAt(index)
                        );

                if (result != null) {
                    return result;
                }
            }
        }

        return null;
    }

    private int color(
            @ColorRes int colorResource
    ) {
        return ContextCompat.getColor(
                activity,
                colorResource
        );
    }

    private int dp(
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
