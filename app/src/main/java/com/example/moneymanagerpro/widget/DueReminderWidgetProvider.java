package com.example.moneymanagerpro.widget;

import android.content.Context;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;

/** Nearest credit-card, EMI/loan and subscription due widget. */
public final class DueReminderWidgetProvider extends BaseFinanceWidgetProvider {
    @NonNull
    @Override
    protected RemoteViews buildViews(
            @NonNull Context context,
            @NonNull WidgetFinanceSnapshot snapshot,
            int widgetId
    ) {
        return WidgetPackRenderer.dueReminder(
                context,
                snapshot,
                widgetId,
                DueReminderWidgetProvider.class
        );
    }
}
