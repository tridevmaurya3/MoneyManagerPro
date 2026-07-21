package com.example.moneymanagerpro.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.activities.AddExpenseActivity;
import com.example.moneymanagerpro.activities.AddIncomeActivity;
import com.example.moneymanagerpro.activities.DashboardActivity;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.AccountBalance;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class FinanceWidgetProvider extends AppWidgetProvider {

    private static final String ACTION_REFRESH_WIDGET =
            "com.example.moneymanagerpro.ACTION_REFRESH_WIDGET";

    @Override
    public void onUpdate(
            Context context,
            AppWidgetManager appWidgetManager,
            int[] appWidgetIds
    ) {
        updateWidgets(context, appWidgetManager, appWidgetIds);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        if (ACTION_REFRESH_WIDGET.equals(intent.getAction())) {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);

            ComponentName componentName = new ComponentName(
                    context,
                    FinanceWidgetProvider.class
            );

            int[] widgetIds = manager.getAppWidgetIds(componentName);

            updateWidgets(context, manager, widgetIds);
        }
    }

    private void updateWidgets(
            Context context,
            AppWidgetManager appWidgetManager,
            int[] appWidgetIds
    ) {
        final PendingResult pendingResult = goAsync();

        new Thread(() -> {
            try {
                double totalBalance = 0;

                List<AccountBalance> accountBalances = DatabaseClient
                        .getInstance(context.getApplicationContext())
                        .getAppDatabase()
                        .accountDao()
                        .getAccountBalances();

                for (AccountBalance accountBalance : accountBalances) {
                    totalBalance += accountBalance.currentBalance;
                }

                Double incomeResult = DatabaseClient
                        .getInstance(context.getApplicationContext())
                        .getAppDatabase()
                        .transactionDao()
                        .getTotalAmountByType("INCOME");

                Double expenseResult = DatabaseClient
                        .getInstance(context.getApplicationContext())
                        .getAppDatabase()
                        .transactionDao()
                        .getTotalAmountByType("EXPENSE");

                double totalIncome = incomeResult == null ? 0 : incomeResult;
                double totalExpense = expenseResult == null ? 0 : expenseResult;

                for (int widgetId : appWidgetIds) {
                    RemoteViews views = new RemoteViews(
                            context.getPackageName(),
                            R.layout.widget_finance
                    );

                    views.setTextViewText(
                            R.id.widgetBalance,
                            formatMoney(totalBalance)
                    );

                    views.setTextViewText(
                            R.id.widgetIncome,
                            formatMoney(totalIncome)
                    );

                    views.setTextViewText(
                            R.id.widgetExpense,
                            formatMoney(totalExpense)
                    );

                    attachActions(context, views, widgetId);

                    appWidgetManager.updateAppWidget(widgetId, views);
                }

            } catch (Exception ignored) {
            } finally {
                pendingResult.finish();
            }
        }).start();
    }

    private void attachActions(
            Context context,
            RemoteViews views,
            int widgetId
    ) {
        Intent dashboardIntent = new Intent(context, DashboardActivity.class);
        dashboardIntent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
        );

        PendingIntent dashboardPendingIntent = PendingIntent.getActivity(
                context,
                widgetId + 10,
                dashboardIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        views.setOnClickPendingIntent(
                R.id.widgetRoot,
                dashboardPendingIntent
        );

        Intent incomeIntent = new Intent(context, AddIncomeActivity.class);
        incomeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent incomePendingIntent = PendingIntent.getActivity(
                context,
                widgetId + 20,
                incomeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        views.setOnClickPendingIntent(
                R.id.widgetAddIncome,
                incomePendingIntent
        );

        Intent expenseIntent = new Intent(context, AddExpenseActivity.class);
        expenseIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent expensePendingIntent = PendingIntent.getActivity(
                context,
                widgetId + 30,
                expenseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        views.setOnClickPendingIntent(
                R.id.widgetAddExpense,
                expensePendingIntent
        );

        Intent refreshIntent = new Intent(context, FinanceWidgetProvider.class);
        refreshIntent.setAction(ACTION_REFRESH_WIDGET);

        PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                widgetId + 40,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        views.setOnClickPendingIntent(
                R.id.widgetRefresh,
                refreshPendingIntent
        );
    }

    private String formatMoney(double amount) {
        NumberFormat formatter = NumberFormat.getNumberInstance(new Locale("en", "IN"));
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);

        return "₹" + formatter.format(amount);
    }
}