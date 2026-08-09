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
import com.example.moneymanagerpro.model.Transaction;
import com.example.moneymanagerpro.model.CreditCardPayment;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.text.ParsePosition;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FinanceWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_REFRESH_WIDGET =
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

                Calendar start = Calendar.getInstance();
                start.set(Calendar.DAY_OF_MONTH, 1);
                start.set(Calendar.HOUR_OF_DAY, 0); start.set(Calendar.MINUTE, 0); start.set(Calendar.SECOND, 0);
                Calendar end = (Calendar) start.clone();
                end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH));
                end.set(Calendar.HOUR_OF_DAY, 23); end.set(Calendar.MINUTE, 59); end.set(Calendar.SECOND, 59);
                SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

                double totalIncome = 0d;
                double totalExpense = 0d;
                List<Transaction> transactions = DatabaseClient.getInstance(context.getApplicationContext())
                        .getAppDatabase().transactionDao().getAllTransactions();
                if (transactions != null) for (Transaction transaction : transactions) {
                    if (transaction == null || !inRange(transaction.getDate(), start, end)) continue;
                    if ("INCOME".equalsIgnoreCase(transaction.getType())) totalIncome += Math.abs(transaction.getAmount());
                    if ("EXPENSE".equalsIgnoreCase(transaction.getType())) totalExpense += Math.abs(transaction.getAmount());
                }
                double cardPayments = 0d;
                List<CreditCardPayment> payments = DatabaseClient.getInstance(context.getApplicationContext())
                        .getAppDatabase().creditCardPaymentDao().getAllPayments();
                if (payments != null) for (CreditCardPayment payment : payments) {
                    if (payment != null && inRange(payment.getPaymentDate(), start, end)) cardPayments += Math.abs(payment.getAmount());
                }
                double availableCash = totalIncome - totalExpense - cardPayments;
                String monthLabel = new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(start.getTime());

                for (int widgetId : appWidgetIds) {
                    RemoteViews views = new RemoteViews(
                            context.getPackageName(),
                            R.layout.widget_finance
                    );

                    views.setTextViewText(
                            R.id.widgetBalance,
                            formatMoney(totalBalance)
                    );
                    views.setTextViewText(R.id.widgetMonth, monthLabel);
                    views.setTextViewText(R.id.widgetAvailableCash, formatSignedMoney(availableCash));
                    views.setTextViewText(R.id.widgetCardPayments, "−" + formatMoney(cardPayments));
                    views.setTextViewText(R.id.widgetUpdated, "Updated " + new SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(Calendar.getInstance().getTime()));

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

    private String formatSignedMoney(double amount) {
        return (amount > 0.005d ? "+" : amount < -0.005d ? "−" : "") + formatMoney(Math.abs(amount));
    }

    private boolean inRange(String value, Calendar start, Calendar end) {
        Date date = parseDate(value);
        return date != null && !date.before(start.getTime()) && !date.after(end.getTime());
    }

    private Date parseDate(String value) {
        String clean = value == null ? "" : value.trim();
        String[] patterns = {"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd", "dd-MM-yyyy HH:mm", "dd-MM-yyyy", "dd/MM/yyyy HH:mm", "dd/MM/yyyy"};
        for (String pattern : patterns) {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US); format.setLenient(false);
            ParsePosition position = new ParsePosition(0); Date date = format.parse(clean, position);
            if (date != null && position.getIndex() == clean.length()) return date;
        }
        return null;
    }
}
