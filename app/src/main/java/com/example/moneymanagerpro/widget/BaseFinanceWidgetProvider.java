package com.example.moneymanagerpro.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver.PendingResult;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;

/** Shared async lifecycle for every widget in the Money Manager Pro widget pack. */
public abstract class BaseFinanceWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_REFRESH_WIDGET_PACK =
            "com.example.moneymanagerpro.ACTION_REFRESH_WIDGET_PACK";

    @Override
    public void onUpdate(
            Context context,
            AppWidgetManager appWidgetManager,
            int[] appWidgetIds
    ) {
        updateAsync(context, appWidgetManager, appWidgetIds);
    }

    @Override
    public void onAppWidgetOptionsChanged(
            Context context,
            AppWidgetManager appWidgetManager,
            int appWidgetId,
            Bundle newOptions
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
        updateAsync(context, appWidgetManager, new int[]{appWidgetId});
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_REFRESH_WIDGET_PACK.equals(intent.getAction())) {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            int[] widgetIds = manager.getAppWidgetIds(new ComponentName(context, getClass()));
            updateAsync(context, manager, widgetIds);
        }
    }

    private void updateAsync(
            @NonNull Context context,
            @NonNull AppWidgetManager manager,
            int[] widgetIds
    ) {
        final PendingResult pendingResult = goAsync();
        final Context app = context.getApplicationContext();
        final int[] safeIds = widgetIds == null ? new int[0] : widgetIds;

        new Thread(() -> {
            try {
                WidgetFinanceSnapshot snapshot = WidgetFinanceSnapshot.load(app);
                for (int widgetId : safeIds) {
                    RemoteViews views = buildViews(app, snapshot, widgetId);
                    Bundle options = manager.getAppWidgetOptions(widgetId);
                    int widthDp = options == null ? 0 : options.getInt(
                            AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
                            0
                    );
                    int heightDp = options == null ? 0 : options.getInt(
                            AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
                            0
                    );
                    WidgetPackRenderer.applyResponsiveState(
                            views,
                            getClass(),
                            widthDp,
                            heightDp
                    );
                    manager.updateAppWidget(widgetId, views);
                }
            } catch (Exception ignored) {
                // Keep the last valid widget state if Room or launcher options are temporarily unavailable.
            } finally {
                pendingResult.finish();
            }
        }, "finance-widget-refresh").start();
    }

    @NonNull
    protected abstract RemoteViews buildViews(
            @NonNull Context context,
            @NonNull WidgetFinanceSnapshot snapshot,
            int widgetId
    );

    public static void requestRefreshAll(@NonNull Context context) {
        Class<?>[] providers = {
                FinanceWidgetProvider.class,
                QuickActionsWidgetProvider.class,
                DueReminderWidgetProvider.class,
                MonthlySnapshotWidgetProvider.class,
                CompactBalanceWidgetProvider.class
        };
        for (Class<?> provider : providers) {
            Intent refresh = new Intent(context, provider);
            refresh.setAction(ACTION_REFRESH_WIDGET_PACK);
            context.sendBroadcast(refresh);
        }
    }
}
