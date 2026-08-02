package com.example.moneymanagerpro.cloud;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.moneymanagerpro.activities.BackupActivity;
import com.example.moneymanagerpro.activities.CreditCardActivity;
import com.example.moneymanagerpro.activities.DashboardActivity;
import com.example.moneymanagerpro.activities.SettingsActivity;
import com.example.moneymanagerpro.credit.AdvancedCreditCardManagerController;
import com.example.moneymanagerpro.dashboard.AdvancedDashboardInsightsController;
import com.example.moneymanagerpro.dashboard.DashboardObligationsController;
import com.example.moneymanagerpro.dashboard.SmartDashboard2Controller;
import com.example.moneymanagerpro.planner.SmartGoalDebtDashboardController;
import com.example.moneymanagerpro.security.AppInactivityLockManager;
import com.example.moneymanagerpro.security.AutoLockSettingsController;
import com.example.moneymanagerpro.ui.DashboardMotionPolish;
import com.example.moneymanagerpro.ui.DashboardVisualEnhancer;

import java.util.Map;
import java.util.WeakHashMap;

public final class CloudDeleteActionsInitializer extends ContentProvider {

    private final Map<Activity, CloudDeleteActionsController> cloudDeleteControllers = new WeakHashMap<>();
    private final Map<Activity, AutoLockSettingsController> autoLockSettingsControllers = new WeakHashMap<>();
    private final Map<Activity, SmartDashboard2Controller> smartDashboardControllers = new WeakHashMap<>();
    private final Map<Activity, AdvancedDashboardInsightsController> advancedDashboardControllers = new WeakHashMap<>();
    private final Map<Activity, DashboardObligationsController> obligationControllers = new WeakHashMap<>();
    private final Map<Activity, SmartGoalDebtDashboardController> smartPlannerControllers = new WeakHashMap<>();
    private final Map<Activity, AdvancedCreditCardManagerController> creditCardControllers = new WeakHashMap<>();

    @Nullable
    private AppInactivityLockManager inactivityLockManager;

    @Override
    public boolean onCreate() {
        if (getContext() == null) return false;

        Application application = (Application) getContext().getApplicationContext();
        inactivityLockManager = new AppInactivityLockManager(application);

        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                // Controllers attach after Activity.onResume().
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                // No action required.
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                if (inactivityLockManager != null) inactivityLockManager.onActivityResumed(activity);
                DashboardVisualEnhancer.apply(activity);

                if (activity instanceof DashboardActivity) {
                    attachSmartDashboardController(activity);
                    attachAdvancedDashboardController(activity);
                    attachObligationsController(activity);
                    attachSmartPlannerController(activity);
                    activity.getWindow().getDecorView().postDelayed(
                            () -> DashboardMotionPolish.apply(activity),
                            140L
                    );
                }

                if (activity instanceof CreditCardActivity) attachCreditCardController(activity);
                if (activity instanceof BackupActivity) attachCloudDeleteController(activity);
                if (activity instanceof SettingsActivity) attachAutoLockSettingsController(activity);
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
                if (inactivityLockManager != null) inactivityLockManager.onActivityPaused(activity);
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
                // Background duration is evaluated at next resume.
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
                // No action required.
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
                cloudDeleteControllers.remove(activity);
                autoLockSettingsControllers.remove(activity);
                smartDashboardControllers.remove(activity);
                advancedDashboardControllers.remove(activity);
                obligationControllers.remove(activity);
                smartPlannerControllers.remove(activity);
                creditCardControllers.remove(activity);
                DashboardVisualEnhancer.remove(activity);
                DashboardMotionPolish.remove(activity);
                if (inactivityLockManager != null) inactivityLockManager.onActivityDestroyed(activity);
            }
        });

        return true;
    }

    private void attachCloudDeleteController(@NonNull Activity activity) {
        CloudDeleteActionsController controller = cloudDeleteControllers.get(activity);
        if (controller == null) {
            controller = new CloudDeleteActionsController(activity);
            cloudDeleteControllers.put(activity, controller);
        }
        controller.attach();
    }

    private void attachAutoLockSettingsController(@NonNull Activity activity) {
        AutoLockSettingsController controller = autoLockSettingsControllers.get(activity);
        if (controller == null) {
            controller = new AutoLockSettingsController(activity);
            autoLockSettingsControllers.put(activity, controller);
        }
        controller.attach();
    }

    private void attachSmartDashboardController(@NonNull Activity activity) {
        SmartDashboard2Controller controller = smartDashboardControllers.get(activity);
        if (controller == null) {
            controller = new SmartDashboard2Controller(activity);
            smartDashboardControllers.put(activity, controller);
        }
        controller.attach();
    }

    private void attachAdvancedDashboardController(@NonNull Activity activity) {
        AdvancedDashboardInsightsController controller = advancedDashboardControllers.get(activity);
        if (controller == null) {
            controller = new AdvancedDashboardInsightsController(activity);
            advancedDashboardControllers.put(activity, controller);
        }
        controller.attach();
    }

    private void attachObligationsController(@NonNull Activity activity) {
        DashboardObligationsController controller = obligationControllers.get(activity);
        if (controller == null) {
            controller = new DashboardObligationsController(activity);
            obligationControllers.put(activity, controller);
        }
        controller.attach();
    }

    private void attachSmartPlannerController(@NonNull Activity activity) {
        SmartGoalDebtDashboardController controller = smartPlannerControllers.get(activity);
        if (controller == null) {
            controller = new SmartGoalDebtDashboardController(activity);
            smartPlannerControllers.put(activity, controller);
        }
        controller.attach();
    }

    private void attachCreditCardController(@NonNull Activity activity) {
        AdvancedCreditCardManagerController controller = creditCardControllers.get(activity);
        if (controller == null) {
            controller = new AdvancedCreditCardManagerController(activity);
            creditCardControllers.put(activity, controller);
        }
        controller.attach();
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values,
                      @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }
}
