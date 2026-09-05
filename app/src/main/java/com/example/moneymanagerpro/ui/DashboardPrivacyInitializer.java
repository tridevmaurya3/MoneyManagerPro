package com.example.moneymanagerpro.ui;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.WindowCallbackWrapper;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.activities.DashboardActivity;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Adds a privacy switch beside the existing "Last 3 Months Overview" heading.
 * The switch masks financial values only; existing dashboard cards, labels,
 * database, sync, Family Hub and Smart SMS connection logic stay unchanged.
 */
public final class DashboardPrivacyInitializer extends ContentProvider {

    private final Map<Activity, DashboardPrivacyController> controllers =
            new WeakHashMap<>();

    @Override
    public boolean onCreate() {
        if (getContext() == null) {
            return false;
        }

        Application application =
                (Application) getContext().getApplicationContext();

        application.registerActivityLifecycleCallbacks(
                new Application.ActivityLifecycleCallbacks() {
                    @Override
                    public void onActivityResumed(@NonNull Activity activity) {
                        if (!(activity instanceof DashboardActivity)) {
                            return;
                        }

                        DashboardPrivacyController controller =
                                controllers.get(activity);

                        if (controller == null) {
                            controller =
                                    new DashboardPrivacyController(activity);
                            controllers.put(activity, controller);
                        }

                        controller.attachAndLock();
                    }

                    @Override
                    public void onActivityPaused(@NonNull Activity activity) {
                        DashboardPrivacyController controller =
                                controllers.get(activity);

                        if (controller != null) {
                            controller.lock();
                        }
                    }

                    @Override
                    public void onActivityDestroyed(@NonNull Activity activity) {
                        DashboardPrivacyController controller =
                                controllers.remove(activity);

                        if (controller != null) {
                            controller.detach();
                        }
                    }

                    @Override
                    public void onActivityCreated(
                            @NonNull Activity activity,
                            @Nullable Bundle bundle
                    ) {
                    }

                    @Override
                    public void onActivityStarted(@NonNull Activity activity) {
                    }

                    @Override
                    public void onActivityStopped(@NonNull Activity activity) {
                    }

                    @Override
                    public void onActivitySaveInstanceState(
                            @NonNull Activity activity,
                            @NonNull Bundle bundle
                    ) {
                    }
                }
        );

        return true;
    }

    @Nullable
    @Override
    public Cursor query(
            @NonNull Uri uri,
            @Nullable String[] projection,
            @Nullable String selection,
            @Nullable String[] selectionArgs,
            @Nullable String sortOrder
    ) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(
            @NonNull Uri uri,
            @Nullable ContentValues values
    ) {
        return null;
    }

    @Override
    public int delete(
            @NonNull Uri uri,
            @Nullable String selection,
            @Nullable String[] selectionArgs
    ) {
        return 0;
    }

    @Override
    public int update(
            @NonNull Uri uri,
            @Nullable ContentValues values,
            @Nullable String selection,
            @Nullable String[] selectionArgs
    ) {
        return 0;
    }

    private static final class DashboardPrivacyController {

        private static final long IDLE_TIMEOUT_MS = 60_000L;
        private static final long RUNTIME_DATA_LOCK_DELAY_MS = 350L;
        private static final String HEADING_TEXT =
                "Last 3 Months Overview";
        private static final String AMOUNT_MASK = "****";
        private static final String RUNTIME_MASK = "---";

        private static final int[] FINANCIAL_TEXT_VIEW_IDS = {
                R.id.txtBalance,
                R.id.txtIncome,
                R.id.txtExpense,
                R.id.txtCash,
                R.id.txtCardPayments,
                R.id.txtNetAvailableCash,
                R.id.txtMonth1Amount,
                R.id.txtMonth2Amount,
                R.id.txtMonth3Amount
        };

        private static final String[] RUNTIME_DASHBOARD_DATA_TAGS = {
                "dashboard_reconciliation_center_v1"
        };

        private SwitchMaterial privacySwitch;
        private boolean changingSwitchState;
        private boolean dataVisible;
        private boolean internalTextChange;

        private final Activity activity;
        private final Handler handler =
                new Handler(Looper.getMainLooper());
        private final Map<TextView, String> actualValues =
                new HashMap<>();
        private final Map<TextView, String> maskTokens =
                new HashMap<>();
        private final Map<TextView, TextWatcher> textWatchers =
                new HashMap<>();

        private final Runnable idleLock = this::lock;
        private final Runnable runtimeDataLock = () -> {
            if (!dataVisible) {
                registerRuntimeDashboardDataViews();
                applyFinancialMask(false);
            }
        };

        private Window.Callback originalWindowCallback;
        private Window.Callback privacyWindowCallback;

        DashboardPrivacyController(Activity activity) {
            this.activity = activity;
        }

        void attachAndLock() {
            ensurePrivacySwitchBesideHeading();
            registerKnownFinancialViews();
            ensureInteractionTracking();
            lock();
        }

        void lock() {
            handler.removeCallbacks(idleLock);
            handler.removeCallbacks(runtimeDataLock);

            dataVisible = false;
            registerKnownFinancialViews();
            registerRuntimeDashboardDataViews();
            applyFinancialMask(false);

            if (privacySwitch != null && privacySwitch.isChecked()) {
                changingSwitchState = true;
                privacySwitch.setChecked(false);
                changingSwitchState = false;
            }

            // Some existing dashboard extensions inject their values just after
            // onResume(). Re-apply the mask after those runtime views appear.
            handler.postDelayed(
                    runtimeDataLock,
                    RUNTIME_DATA_LOCK_DELAY_MS
            );
        }

        void detach() {
            handler.removeCallbacks(idleLock);
            handler.removeCallbacks(runtimeDataLock);

            for (Map.Entry<TextView, TextWatcher> entry
                    : textWatchers.entrySet()) {
                entry.getKey().removeTextChangedListener(
                        entry.getValue()
                );
            }

            textWatchers.clear();
            actualValues.clear();
            maskTokens.clear();

            Window window = activity.getWindow();

            if (window != null
                    && privacyWindowCallback != null
                    && window.getCallback() == privacyWindowCallback
                    && originalWindowCallback != null) {
                window.setCallback(originalWindowCallback);
            }

            originalWindowCallback = null;
            privacyWindowCallback = null;
        }

        private void ensurePrivacySwitchBesideHeading() {
            if (privacySwitch != null) {
                return;
            }

            View root = activity.findViewById(android.R.id.content);
            TextView heading = findTextViewByText(
                    root,
                    HEADING_TEXT
            );

            if (heading == null) {
                return;
            }

            ViewParent parent = heading.getParent();

            if (!(parent instanceof LinearLayout)) {
                return;
            }

            LinearLayout dashboardContainer =
                    (LinearLayout) parent;
            int headingIndex =
                    dashboardContainer.indexOfChild(heading);

            if (headingIndex < 0) {
                return;
            }

            ViewGroup.LayoutParams oldParams =
                    heading.getLayoutParams();

            LinearLayout headingRow =
                    new LinearLayout(activity);
            headingRow.setOrientation(LinearLayout.HORIZONTAL);
            headingRow.setGravity(
                    android.view.Gravity.CENTER_VERTICAL
            );
            headingRow.setTag(
                    "dashboard_privacy_heading_row_v2"
            );

            LinearLayout.LayoutParams rowParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );

            if (oldParams instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams margins =
                        (ViewGroup.MarginLayoutParams) oldParams;
                rowParams.setMargins(
                        margins.leftMargin,
                        margins.topMargin,
                        margins.rightMargin,
                        margins.bottomMargin
                );
            }

            dashboardContainer.removeViewAt(headingIndex);

            LinearLayout.LayoutParams headingParams =
                    new LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f
                    );
            heading.setLayoutParams(headingParams);

            privacySwitch = new SwitchMaterial(activity);
            privacySwitch.setTag(
                    "dashboard_privacy_switch_v2"
            );
            privacySwitch.setContentDescription(
                    "Show or mask dashboard financial data"
            );
            privacySwitch.setChecked(false);
            privacySwitch.setUseMaterialThemeColors(true);

            LinearLayout.LayoutParams switchParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );
            switchParams.setMarginStart(dp(8));
            privacySwitch.setLayoutParams(switchParams);

            privacySwitch.setOnCheckedChangeListener(
                    (buttonView, isChecked) -> {
                        if (changingSwitchState) {
                            return;
                        }

                        handler.removeCallbacks(runtimeDataLock);
                        setFinancialDataVisible(isChecked);

                        if (isChecked) {
                            scheduleIdleLock();
                        } else {
                            handler.removeCallbacks(idleLock);
                            handler.postDelayed(
                                    runtimeDataLock,
                                    RUNTIME_DATA_LOCK_DELAY_MS
                            );
                        }
                    }
            );

            headingRow.addView(heading);
            headingRow.addView(privacySwitch);

            dashboardContainer.addView(
                    headingRow,
                    headingIndex,
                    rowParams
            );
        }

        private TextView findTextViewByText(
                View view,
                String targetText
        ) {
            if (view == null) {
                return null;
            }

            if (view instanceof TextView) {
                TextView textView = (TextView) view;

                if (targetText.contentEquals(textView.getText())) {
                    return textView;
                }
            }

            if (!(view instanceof ViewGroup)) {
                return null;
            }

            ViewGroup group = (ViewGroup) view;

            for (int index = 0;
                 index < group.getChildCount();
                 index++) {
                TextView match = findTextViewByText(
                        group.getChildAt(index),
                        targetText
                );

                if (match != null) {
                    return match;
                }
            }

            return null;
        }

        private void registerKnownFinancialViews() {
            for (int viewId : FINANCIAL_TEXT_VIEW_IDS) {
                View view = activity.findViewById(viewId);

                if (view instanceof TextView) {
                    registerMaskedTextView(
                            (TextView) view,
                            AMOUNT_MASK
                    );
                }
            }
        }

        private void registerRuntimeDashboardDataViews() {
            View root = activity.findViewById(android.R.id.content);

            if (root == null) {
                return;
            }

            for (String tag : RUNTIME_DASHBOARD_DATA_TAGS) {
                View taggedView = root.findViewWithTag(tag);

                if (taggedView != null) {
                    registerSensitiveRuntimeTextViews(
                            taggedView
                    );
                }
            }
        }

        private void registerSensitiveRuntimeTextViews(View view) {
            if (view instanceof TextView) {
                TextView textView = (TextView) view;
                String value = String.valueOf(
                        textView.getText()
                );

                if (looksSensitive(value)) {
                    registerMaskedTextView(
                            textView,
                            RUNTIME_MASK
                    );
                }

                return;
            }

            if (!(view instanceof ViewGroup)) {
                return;
            }

            ViewGroup group = (ViewGroup) view;

            for (int index = 0;
                 index < group.getChildCount();
                 index++) {
                registerSensitiveRuntimeTextViews(
                        group.getChildAt(index)
                );
            }
        }

        private boolean looksSensitive(String value) {
            if (value == null || value.trim().isEmpty()) {
                return false;
            }

            for (int index = 0;
                 index < value.length();
                 index++) {
                char character = value.charAt(index);

                if (Character.isDigit(character)
                        || character == '₹'
                        || character == '$'
                        || character == '€'
                        || character == '£') {
                    return true;
                }
            }

            return false;
        }

        private void registerMaskedTextView(
                TextView textView,
                String mask
        ) {
            if (maskTokens.containsKey(textView)) {
                return;
            }

            actualValues.put(
                    textView,
                    String.valueOf(textView.getText())
            );
            maskTokens.put(textView, mask);

            TextWatcher watcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(
                        CharSequence sequence,
                        int start,
                        int count,
                        int after
                ) {
                }

                @Override
                public void onTextChanged(
                        CharSequence sequence,
                        int start,
                        int before,
                        int count
                ) {
                }

                @Override
                public void afterTextChanged(Editable editable) {
                    if (internalTextChange) {
                        return;
                    }

                    String currentText =
                            editable == null
                                    ? ""
                                    : editable.toString();
                    String currentMask =
                            maskTokens.get(textView);

                    if (dataVisible) {
                        actualValues.put(
                                textView,
                                currentText
                        );
                        return;
                    }

                    if (currentMask == null
                            || currentMask.equals(currentText)) {
                        return;
                    }

                    // Dashboard data can refresh asynchronously while privacy is
                    // OFF. Keep the newest real value privately, then re-mask it.
                    actualValues.put(
                            textView,
                            currentText
                    );
                    setTextInternally(
                            textView,
                            currentMask
                    );
                }
            };

            textView.addTextChangedListener(watcher);
            textWatchers.put(textView, watcher);

            if (!dataVisible) {
                setTextInternally(textView, mask);
            }
        }

        private void setFinancialDataVisible(boolean visible) {
            dataVisible = visible;
            registerKnownFinancialViews();
            registerRuntimeDashboardDataViews();
            applyFinancialMask(visible);
        }

        private void applyFinancialMask(boolean visible) {
            for (Map.Entry<TextView, String> entry
                    : maskTokens.entrySet()) {
                TextView textView = entry.getKey();
                String mask = entry.getValue();

                if (visible) {
                    String actualValue =
                            actualValues.get(textView);

                    if (actualValue != null) {
                        setTextInternally(
                                textView,
                                actualValue
                        );
                    }

                    continue;
                }

                String currentText =
                        String.valueOf(textView.getText());

                if (!mask.equals(currentText)) {
                    actualValues.put(
                            textView,
                            currentText
                    );
                }

                setTextInternally(textView, mask);
            }
        }

        private void setTextInternally(
                TextView textView,
                String value
        ) {
            if (value.contentEquals(textView.getText())) {
                return;
            }

            internalTextChange = true;

            try {
                textView.setText(value);
            } finally {
                internalTextChange = false;
            }
        }

        private void ensureInteractionTracking() {
            Window window = activity.getWindow();

            if (window == null || privacyWindowCallback != null) {
                return;
            }

            originalWindowCallback = window.getCallback();

            if (originalWindowCallback == null) {
                return;
            }

            privacyWindowCallback =
                    new WindowCallbackWrapper(
                            originalWindowCallback
                    ) {
                        @Override
                        public boolean dispatchTouchEvent(
                                MotionEvent event
                        ) {
                            if (event != null
                                    && event.getActionMasked()
                                    == MotionEvent.ACTION_DOWN) {
                                onUserInteraction();
                            }

                            return super.dispatchTouchEvent(event);
                        }
                    };

            window.setCallback(privacyWindowCallback);
        }

        private void onUserInteraction() {
            if (privacySwitch != null
                    && privacySwitch.isChecked()) {
                scheduleIdleLock();
            }
        }

        private void scheduleIdleLock() {
            handler.removeCallbacks(idleLock);
            handler.postDelayed(
                    idleLock,
                    IDLE_TIMEOUT_MS
            );
        }

        private int dp(int value) {
            return Math.round(
                    value
                            * activity
                            .getResources()
                            .getDisplayMetrics()
                            .density
            );
        }
    }
}
