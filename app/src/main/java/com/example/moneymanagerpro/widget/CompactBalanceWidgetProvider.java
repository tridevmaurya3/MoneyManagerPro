package com.example.moneymanagerpro.widget;

import android.content.Context;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;

/** Small balance-first widget for narrow home-screen spaces. */
public final class CompactBalanceWidgetProvider extends BaseFinanceWidgetProvider {
    @NonNull
    @Override
    protected RemoteViews buildViews(
            @NonNull Context context,
            @NonNull WidgetFinanceSnapshot snapshot,
            int widgetId
    ) {
        return WidgetPackRenderer.compactBalance(
                context,
                snapshot,
                widgetId,
                CompactBalanceWidgetProvider.class
        );
    }
}
