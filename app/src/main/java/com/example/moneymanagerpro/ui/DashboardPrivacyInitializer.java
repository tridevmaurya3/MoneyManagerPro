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
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.activities.DashboardActivity;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Adds a privacy switch to the Dashboard without changing any finance database,
 * sync, Family Hub or Smart SMS connection logic.
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

        private static final int[] DASHBOARD_DATA_VIEW_IDS = {
                R.id.txtBalance,
                R.id.txtIncome,
                R.id.txtExpense,
                R.id.txtCash,
                R.id.txtCardPayments,
                R.id.txtNetAvailableCash,
                R.id.txtSelectedPeriod,
                R.id.txtOverviewMonthLabel,
                R.id.txtSummarySubtitle,
                R.id.txtMonth1Title,
                R.id.txtMonth1Amount,
                R.id.txtMonth2Title,
                R.id.txtMonth2Amount,
                R.id.txtMonth3Title,
                R.id.txtMonth3Amount
        };

        private static final String[] RUNTIME_DASHBOARD_DATA_TAGS = {
                "dashboard_reconciliation_center_v1"
        };

        private SwitchMaterial privacySwitch;
        private TextView privacyStatus;
        private boolean changingSwitchState;

        private final Activity activity;
        private final Handler handler =
                new Handler(Looper.getMainLooper());
        private final Runnable idleLock = this::lock;
        private final Runnable runtimeDataLock = () -> {
            if (privacySwitch != null
                    && !privacySwitch.isChecked()) {
                setDashboardDataVisible(false);
            }
        };

        private Window.Callback originalWindowCallback;
        private Window.Callback privacyWindowCallback;

        DashboardPrivacyController(Activity activity) {
            this.activity = activity;
        }

        void attachAndLock() {
            ensurePrivacyCard();
            ensureInteractionTracking();
            lock();
        }

        void lock() {
            handler.removeCallbacks(idleLock);
            handler.removeCallbacks(runtimeDataLock);
            setDashboardDataVisible(false);

            if (privacySwitch != null && privacySwitch.isChecked()) {
                changingSwitchState = true;
                privacySwitch.setChecked(false);
                changingSwitchState = false;
            }

            updatePrivacyStatus(false);

            // Existing Dashboard extensions can inject summary cards just after
            // onResume(). Re-apply the OFF state once those runtime cards exist.
            handler.postDelayed(
                    runtimeDataLock,
                    RUNTIME_DATA_LOCK_DELAY_MS
            );
        }

        void detach() {
            handler.removeCallbacks(idleLock);
            handler.removeCallbacks(runtimeDataLock);

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

        private void ensurePrivacyCard() {
            if (privacySwitch != null) {
                return;
            }

            View thirdMonthCard =
                    activity.findViewById(R.id.cardMonth3);

            if (thirdMonthCard == null) {
                return;
            }

            ViewParent rowParent = thirdMonthCard.getParent();

            if (!(rowParent instanceof View)) {
                return;
            }

            View monthsRow = (View) rowParent;
            ViewParent containerParent = monthsRow.getParent();

            if (!(containerParent instanceof LinearLayout)) {
                return;
            }

            LinearLayout dashboardContainer =
                    (LinearLayout) containerParent;

            MaterialCardView privacyCard =
                    new MaterialCardView(activity);
            privacyCard.setCardBackgroundColor(
                    ContextCompat.getColor(
                            activity,
                            R.color.app_surface
                    )
            );
            privacyCard.setRadius(dp(16));
            privacyCard.setCardElevation(dp(1));
            privacyCard.setStrokeWidth(dp(1));
            privacyCard.setStrokeColor(
                    ContextCompat.getColor(
                            activity,
                            R.color.app_outline_soft
                    )
            );

            LinearLayout.LayoutParams cardParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );
            cardParams.setMargins(0, dp(10), 0, 0);
            privacyCard.setLayoutParams(cardParams);

            LinearLayout content = new LinearLayout(activity);
            content.setOrientation(LinearLayout.HORIZONTAL);
            content.setGravity(android.view.Gravity.CENTER_VERTICAL);
            content.setPadding(dp(14), dp(12), dp(12), dp(12));

            LinearLayout labels = new LinearLayout(activity);
            labels.setOrientation(LinearLayout.VERTICAL);

            LinearLayout.LayoutParams labelParams =
                    new LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f
                    );
            labels.setLayoutParams(labelParams);

            TextView title = new TextView(activity);
            title.setText("Dashboard Privacy");
            title.setTextColor(
                    ContextCompat.getColor(
                            activity,
                            R.color.app_text_primary
                    )
            );
            title.setTextSize(14);
            title.setTypeface(
                    title.getTypeface(),
                    android.graphics.Typeface.BOLD
            );

            privacyStatus = new TextView(activity);
            privacyStatus.setTextSize(10);
            privacyStatus.setPadding(0, dp(3), 0, 0);

            labels.addView(title);
            labels.addView(privacyStatus);

            privacySwitch = new SwitchMaterial(activity);
            privacySwitch.setContentDescription(
                    "Show or hide dashboard financial data"
            );
            privacySwitch.setChecked(false);
            privacySwitch.setUseMaterialThemeColors(true);
            privacySwitch.setOnCheckedChangeListener(
                    (buttonView, isChecked) -> {
                        if (changingSwitchState) {
                            return;
                        }

                        handler.removeCallbacks(runtimeDataLock);
                        setDashboardDataVisible(isChecked);
                        updatePrivacyStatus(isChecked);

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

            content.addView(labels);
            content.addView(privacySwitch);
            privacyCard.addView(content);

            int rowIndex = dashboardContainer.indexOfChild(monthsRow);

            if (rowIndex < 0) {
                return;
            }

            dashboardContainer.addView(
                    privacyCard,
                    rowIndex + 1
            );

            updatePrivacyStatus(false);
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

        private void setDashboardDataVisible(boolean visible) {
            int visibility = visible
                    ? View.VISIBLE
                    : View.INVISIBLE;

            for (int viewId : DASHBOARD_DATA_VIEW_IDS) {
                View dataView = activity.findViewById(viewId);

                if (dataView != null) {
                    dataView.setVisibility(visibility);
                }
            }

            View root = activity.findViewById(android.R.id.content);

            if (root != null) {
                for (String tag : RUNTIME_DASHBOARD_DATA_TAGS) {
                    View taggedDataView = root.findViewWithTag(tag);

                    if (taggedDataView != null) {
                        taggedDataView.setVisibility(visibility);
                    }
                }
            }
        }

        private void updatePrivacyStatus(boolean visible) {
            if (privacyStatus == null) {
                return;
            }

            privacyStatus.setText(
                    visible
                            ? "Visible • auto-locks after 1 minute idle"
                            : "Hidden • switch on to show dashboard data"
            );

            privacyStatus.setTextColor(
                    ContextCompat.getColor(
                            activity,
                            visible
                                    ? R.color.success
                                    : R.color.app_text_secondary
                    )
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
