package com.example.moneymanagerpro.widget;

import android.content.Context;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;

/** Compact 2x2-style finance quick actions widget. */
public final class QuickActionsWidgetProvider extends BaseFinanceWidgetProvider {
    @NonNull
    @Override
    protected RemoteViews buildViews(
            @NonNull Context context,
            @NonNull WidgetFinanceSnapshot snapshot,
            int widgetId
    ) {
        return WidgetPackRenderer.quickActions(
                context,
                snapshot,
                widgetId,
                QuickActionsWidgetProvider.class
        );
    }
}
