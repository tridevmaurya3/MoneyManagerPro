package com.example.moneymanagerpro.widget;

import android.content.Context;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;

/** Monthly income, expense and saving snapshot widget. */
public final class MonthlySnapshotWidgetProvider extends BaseFinanceWidgetProvider {
    @NonNull
    @Override
    protected RemoteViews buildViews(
            @NonNull Context context,
            @NonNull WidgetFinanceSnapshot snapshot,
            int widgetId
    ) {
        return WidgetPackRenderer.monthlySnapshot(
                context,
                snapshot,
                widgetId,
                MonthlySnapshotWidgetProvider.class
        );
    }
}
