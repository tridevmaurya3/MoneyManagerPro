package com.example.moneymanagerpro.floating;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.activities.DashboardActivity;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Adds only the dashboard control for the floating quick-entry feature.
 * No finance provider, sync provider, DAO, schema or existing transaction
 * integration is changed here.
 */
public final class FloatingQuickEntryInitializer extends ContentProvider {

    private static final String HEADING = "Financial Summary";

    private final Map<Activity, Controller> controllers =
            new WeakHashMap<>();

    @Override
    public boolean onCreate() {
        if (getContext() == null) {
            return false;
        }

        Application application = (Application)
                getContext().getApplicationContext();

        application.registerActivityLifecycleCallbacks(
                new Application.ActivityLifecycleCallbacks() {
                    @Override
                    public void onActivityResumed(@NonNull Activity activity) {
                        if (!(activity instanceof DashboardActivity)) {
                            return;
                        }

                        Controller controller = controllers.get(activity);

                        if (controller == null) {
                            controller = new Controller(activity);
                            controllers.put(activity, controller);
                        }

                        controller.attachAndSync();
                    }

                    @Override
                    public void onActivityDestroyed(@NonNull Activity activity) {
                        controllers.remove(activity);
                    }

                    @Override public void onActivityCreated(@NonNull Activity a, @Nullable Bundle b) {}
                    @Override public void onActivityStarted(@NonNull Activity a) {}
                    @Override public void onActivityPaused(@NonNull Activity a) {}
                    @Override public void onActivityStopped(@NonNull Activity a) {}
                    @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle b) {}
                }
        );

        return true;
    }

    private static final class Controller {
        private final Activity activity;
        private SwitchMaterial toggle;
        private boolean internalChange;

        Controller(Activity activity) {
            this.activity = activity;
        }

        void attachAndSync() {
            ensureToggle();

            boolean permissionGranted =
                    Settings.canDrawOverlays(activity);

            if (FloatingQuickEntrySettings
                    .isPermissionPending(activity)) {
                FloatingQuickEntrySettings
                        .setPermissionPending(activity, false);

                if (permissionGranted) {
                    FloatingQuickEntrySettings
                            .setEnabled(activity, true);
                }
            }

            boolean enabled = FloatingQuickEntrySettings
                    .isEnabled(activity)
                    && permissionGranted;

            if (!permissionGranted
                    && FloatingQuickEntrySettings.isEnabled(activity)) {
                FloatingQuickEntrySettings
                        .setEnabled(activity, false);
            }

            setToggleChecked(enabled);

            if (enabled) {
                startFloatingService();
            } else {
                activity.stopService(
                        new Intent(
                                activity,
                                FloatingQuickEntryService.class
                        )
                );
            }
        }

        private void ensureToggle() {
            if (toggle != null) {
                return;
            }

            View root = activity.findViewById(android.R.id.content);
            TextView heading = findTextView(root, HEADING);

            if (heading == null) {
                return;
            }

            ViewParent parent = heading.getParent();

            if (!(parent instanceof LinearLayout)) {
                return;
            }

            LinearLayout dashboardContainer = (LinearLayout) parent;
            int index = dashboardContainer.indexOfChild(heading);

            if (index < 0) {
                return;
            }

            ViewGroup.LayoutParams oldParams = heading.getLayoutParams();
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setTag("floating_quick_entry_dashboard_row_v1");

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

            dashboardContainer.removeViewAt(index);

            heading.setLayoutParams(
                    new LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f
                    )
            );

            TextView label = new TextView(activity);
            label.setText("Floating Strip");
            label.setTextSize(11);
            label.setTextColor(0xFF5F6F66);
            label.setPadding(dp(8), 0, dp(5), 0);

            toggle = new SwitchMaterial(activity);
            toggle.setContentDescription(
                    "Enable floating quick income and expense entry"
            );
            toggle.setUseMaterialThemeColors(true);

            toggle.setOnCheckedChangeListener(
                    (buttonView, isChecked) -> {
                        if (internalChange) {
                            return;
                        }

                        if (isChecked) {
                            enableFloatingStrip();
                        } else {
                            FloatingQuickEntrySettings
                                    .setPermissionPending(activity, false);
                            FloatingQuickEntrySettings
                                    .setEnabled(activity, false);
                            activity.stopService(
                                    new Intent(
                                            activity,
                                            FloatingQuickEntryService.class
                                    )
                            );
                        }
                    }
            );

            row.addView(heading);
            row.addView(label);
            row.addView(toggle);
            dashboardContainer.addView(row, index, rowParams);
        }

        private void enableFloatingStrip() {
            if (!Settings.canDrawOverlays(activity)) {
                setToggleChecked(false);
                FloatingQuickEntrySettings
                        .setPermissionPending(activity, true);

                Intent intent = new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse(
                                "package:" + activity.getPackageName()
                        )
                );
                activity.startActivity(intent);
                return;
            }

            FloatingQuickEntrySettings
                    .setPermissionPending(activity, false);
            FloatingQuickEntrySettings
                    .setEnabled(activity, true);
            startFloatingService();
        }

        private void startFloatingService() {
            ContextCompat.startForegroundService(
                    activity,
                    new Intent(
                            activity,
                            FloatingQuickEntryService.class
                    )
            );
        }

        private void setToggleChecked(boolean checked) {
            if (toggle == null || toggle.isChecked() == checked) {
                return;
            }

            internalChange = true;
            toggle.setChecked(checked);
            internalChange = false;
        }

        private static TextView findTextView(
                View view,
                String expectedText
        ) {
            if (view instanceof TextView) {
                CharSequence value = ((TextView) view).getText();

                if (value != null
                        && expectedText.contentEquals(value)) {
                    return (TextView) view;
                }
            }

            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;

                for (int i = 0; i < group.getChildCount(); i++) {
                    TextView match = findTextView(
                            group.getChildAt(i),
                            expectedText
                    );

                    if (match != null) {
                        return match;
                    }
                }
            }

            return null;
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

    @Nullable @Override public Cursor query(@NonNull Uri u, @Nullable String[] p, @Nullable String s, @Nullable String[] a, @Nullable String o) { return null; }
    @Nullable @Override public String getType(@NonNull Uri uri) { return null; }
    @Nullable @Override public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) { return null; }
    @Override public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) { return 0; }
    @Override public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) { return 0; }
}
