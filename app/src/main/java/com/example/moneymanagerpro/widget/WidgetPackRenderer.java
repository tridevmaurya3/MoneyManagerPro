package com.example.moneymanagerpro.widget;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.activities.AddExpenseActivity;
import com.example.moneymanagerpro.activities.AddIncomeActivity;
import com.example.moneymanagerpro.activities.AnalyticsActivity;
import com.example.moneymanagerpro.activities.CalendarActivity;
import com.example.moneymanagerpro.activities.CreditCardActivity;
import com.example.moneymanagerpro.activities.DashboardActivity;
import com.example.moneymanagerpro.activities.LoanActivity;
import com.example.moneymanagerpro.activities.ReportActivity;
import com.example.moneymanagerpro.activities.SubscriptionActivity;
import com.example.moneymanagerpro.activities.TransferActivity;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** RemoteViews renderer for the complete Dashboard-style widget pack. */
final class WidgetPackRenderer {

    private WidgetPackRenderer() {
    }

    @NonNull
    static RemoteViews summary(
            @NonNull Context context,
            @NonNull WidgetFinanceSnapshot data,
            int widgetId,
            @NonNull Class<?> providerClass
    ) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_finance);
        views.setTextViewText(R.id.widgetMonth, data.monthLabel);
        views.setTextViewText(R.id.widgetBalance, plainSignedMoney(data.totalBalance));
        views.setTextViewText(R.id.widgetIncome, formatMoney(data.income));
        views.setTextViewText(R.id.widgetExpense, formatMoney(data.expense));
        views.setTextViewText(R.id.widgetSaving, formatSignedMoney(data.monthlySaving));
        views.setTextViewText(R.id.widgetAvailableCash, "Available " + formatSignedMoney(data.availableCash));
        views.setTextViewText(R.id.widgetUpdated, updatedNow());

        views.setOnClickPendingIntent(R.id.widgetRoot, activity(context, DashboardActivity.class, widgetId, 1));
        views.setOnClickPendingIntent(R.id.widgetAddIncome, activity(context, AddIncomeActivity.class, widgetId, 2));
        views.setOnClickPendingIntent(R.id.widgetAddExpense, activity(context, AddExpenseActivity.class, widgetId, 3));
        views.setOnClickPendingIntent(R.id.widgetRefresh, refresh(context, providerClass, widgetId, 4));
        return views;
    }

    @NonNull
    static RemoteViews quickActions(
            @NonNull Context context,
            @NonNull WidgetFinanceSnapshot data,
            int widgetId,
            @NonNull Class<?> providerClass
    ) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_quick_actions);
        views.setTextViewText(R.id.widgetQuickBalance, compactMoney(data.totalBalance));
        views.setTextViewText(R.id.widgetQuickMonth, data.monthLabel);
        views.setTextViewText(R.id.widgetQuickUpdated, updatedNow());
        views.setOnClickPendingIntent(R.id.widgetQuickRoot, activity(context, DashboardActivity.class, widgetId, 10));
        views.setOnClickPendingIntent(R.id.widgetQuickIncome, activity(context, AddIncomeActivity.class, widgetId, 11));
        views.setOnClickPendingIntent(R.id.widgetQuickExpense, activity(context, AddExpenseActivity.class, widgetId, 12));
        views.setOnClickPendingIntent(R.id.widgetQuickTransfer, activity(context, TransferActivity.class, widgetId, 13));
        views.setOnClickPendingIntent(R.id.widgetQuickReports, activity(context, ReportActivity.class, widgetId, 14));
        views.setOnClickPendingIntent(R.id.widgetQuickRefresh, refresh(context, providerClass, widgetId, 15));
        return views;
    }

    @NonNull
    static RemoteViews dueReminder(
            @NonNull Context context,
            @NonNull WidgetFinanceSnapshot data,
            int widgetId,
            @NonNull Class<?> providerClass
    ) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_due_reminders);
        views.setTextViewText(R.id.widgetDueCount, data.dueCount + (data.dueCount == 1 ? " item" : " items"));
        views.setTextViewText(R.id.widgetDueUpdated, updatedNow());

        Class<?> openClass = CalendarActivity.class;
        if (data.nearestDue == null) {
            views.setTextViewText(R.id.widgetDueStatus, "ALL CLEAR");
            views.setTextViewText(R.id.widgetDueType, "No active payment due");
            views.setTextViewText(R.id.widgetDueDetail, "Cards, EMIs and subscriptions look clear.");
            views.setTextViewText(R.id.widgetDueAmount, "₹0.00");
            views.setTextViewText(R.id.widgetDueDate, "Nothing pending");
            views.setTextColor(R.id.widgetDueStatus, Color.parseColor("#107C10"));
            views.setInt(R.id.widgetDueStatus, "setBackgroundResource", R.drawable.widget_surface_green);
        } else {
            WidgetFinanceSnapshot.DueItem due = data.nearestDue;
            long days = WidgetFinanceSnapshot.daysUntil(due.dueAt);
            String status;
            int statusColor;
            int statusBackground;
            if (days < 0) {
                status = "OVERDUE " + Math.abs(days) + "D";
                statusColor = Color.parseColor("#C42B1C");
                statusBackground = R.drawable.widget_surface_red;
            } else if (days == 0) {
                status = "DUE TODAY";
                statusColor = Color.parseColor("#C42B1C");
                statusBackground = R.drawable.widget_surface_red;
            } else if (days <= 3) {
                status = "DUE IN " + days + "D";
                statusColor = Color.parseColor("#9A6700");
                statusBackground = R.drawable.widget_surface_warning;
            } else {
                status = "UPCOMING";
                statusColor = Color.parseColor("#0F6CBD");
                statusBackground = R.drawable.widget_surface_blue;
            }
            views.setTextViewText(R.id.widgetDueStatus, status);
            views.setTextViewText(R.id.widgetDueType, due.type);
            views.setTextViewText(R.id.widgetDueDetail, due.detail);
            views.setTextViewText(R.id.widgetDueAmount, formatMoney(due.amount));
            views.setTextViewText(R.id.widgetDueDate, new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(new Date(due.dueAt)));
            views.setTextColor(R.id.widgetDueStatus, statusColor);
            views.setInt(R.id.widgetDueStatus, "setBackgroundResource", statusBackground);

            if ("Credit Card".equals(due.type)) openClass = CreditCardActivity.class;
            else if ("Loan / EMI".equals(due.type)) openClass = LoanActivity.class;
            else if ("Subscription".equals(due.type)) openClass = SubscriptionActivity.class;
        }

        views.setOnClickPendingIntent(R.id.widgetDueRoot, activity(context, openClass, widgetId, 20));
        views.setOnClickPendingIntent(R.id.widgetDueOpen, activity(context, openClass, widgetId, 21));
        views.setOnClickPendingIntent(R.id.widgetDueCalendar, activity(context, CalendarActivity.class, widgetId, 22));
        views.setOnClickPendingIntent(R.id.widgetDueRefresh, refresh(context, providerClass, widgetId, 23));
        return views;
    }

    @NonNull
    static RemoteViews monthlySnapshot(
            @NonNull Context context,
            @NonNull WidgetFinanceSnapshot data,
            int widgetId,
            @NonNull Class<?> providerClass
    ) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_monthly_snapshot);
        views.setTextViewText(R.id.widgetMonthlyMonth, data.monthLabel);
        views.setTextViewText(R.id.widgetMonthlyIncome, compactMoney(data.income));
        views.setTextViewText(R.id.widgetMonthlyExpense, compactMoney(data.expense));
        views.setTextViewText(R.id.widgetMonthlySaving, compactSignedMoney(data.monthlySaving));
        views.setTextViewText(R.id.widgetMonthlyCash, "Available cash " + compactSignedMoney(data.availableCash));
        views.setTextViewText(
                R.id.widgetMonthlyStatus,
                data.monthlySaving >= -0.005d ? "Saving is on track" : "Expense is above income"
        );
        views.setTextColor(
                R.id.widgetMonthlyStatus,
                Color.parseColor(data.monthlySaving >= -0.005d ? "#107C10" : "#C42B1C")
        );
        views.setTextViewText(R.id.widgetMonthlyUpdated, updatedNow());
        views.setOnClickPendingIntent(R.id.widgetMonthlyRoot, activity(context, DashboardActivity.class, widgetId, 30));
        views.setOnClickPendingIntent(R.id.widgetMonthlyAnalytics, activity(context, AnalyticsActivity.class, widgetId, 31));
        views.setOnClickPendingIntent(R.id.widgetMonthlyRefresh, refresh(context, providerClass, widgetId, 32));
        return views;
    }

    @NonNull
    static RemoteViews compactBalance(
            @NonNull Context context,
            @NonNull WidgetFinanceSnapshot data,
            int widgetId,
            @NonNull Class<?> providerClass
    ) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_compact_balance);
        views.setTextViewText(R.id.widgetCompactBalance, compactMoney(data.totalBalance));
        views.setTextViewText(R.id.widgetCompactMonth, data.monthLabel);
        views.setTextViewText(R.id.widgetCompactSaving, "Saving " + compactSignedMoney(data.monthlySaving));
        views.setOnClickPendingIntent(R.id.widgetCompactRoot, activity(context, DashboardActivity.class, widgetId, 40));
        views.setOnClickPendingIntent(R.id.widgetCompactIncome, activity(context, AddIncomeActivity.class, widgetId, 41));
        views.setOnClickPendingIntent(R.id.widgetCompactExpense, activity(context, AddExpenseActivity.class, widgetId, 42));
        views.setOnClickPendingIntent(R.id.widgetCompactRefresh, refresh(context, providerClass, widgetId, 43));
        return views;
    }

    @NonNull
    private static PendingIntent activity(
            @NonNull Context context,
            @NonNull Class<?> activityClass,
            int widgetId,
            int action
    ) {
        Intent intent = new Intent(context, activityClass);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
                context,
                requestCode(widgetId, action),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    @NonNull
    private static PendingIntent refresh(
            @NonNull Context context,
            @NonNull Class<?> providerClass,
            int widgetId,
            int action
    ) {
        Intent intent = new Intent(context, providerClass);
        intent.setAction(BaseFinanceWidgetProvider.ACTION_REFRESH_WIDGET_PACK);
        return PendingIntent.getBroadcast(
                context,
                requestCode(widgetId, action),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static int requestCode(int widgetId, int action) {
        return (widgetId * 100) + action;
    }

    @NonNull
    private static String updatedNow() {
        return "Updated " + new SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(new Date());
    }

    @NonNull
    static String formatMoney(double amount) {
        NumberFormat formatter = NumberFormat.getNumberInstance(new Locale("en", "IN"));
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);
        return "₹" + formatter.format(Math.abs(amount));
    }

    @NonNull
    private static String plainSignedMoney(double amount) {
        return (amount < -0.005d ? "−" : "") + formatMoney(amount);
    }

    @NonNull
    private static String formatSignedMoney(double amount) {
        String sign = amount > 0.005d ? "+" : amount < -0.005d ? "−" : "";
        return sign + formatMoney(amount);
    }

    @NonNull
    private static String compactMoney(double amount) {
        double value = Math.abs(amount);
        String sign = amount < -0.005d ? "−" : "";
        if (value >= 10000000d) return sign + "₹" + oneOrTwo(value / 10000000d) + "Cr";
        if (value >= 100000d) return sign + "₹" + oneOrTwo(value / 100000d) + "L";
        if (value >= 1000d) return sign + "₹" + oneOrTwo(value / 1000d) + "K";
        return sign + formatMoney(value);
    }

    @NonNull
    private static String compactSignedMoney(double amount) {
        if (Math.abs(amount) <= 0.005d) return "₹0.00";
        return (amount > 0d ? "+" : "−") + compactMoney(Math.abs(amount));
    }

    @NonNull
    private static String oneOrTwo(double value) {
        if (value >= 100d) return String.format(Locale.ENGLISH, "%.0f", value);
        if (value >= 10d) return String.format(Locale.ENGLISH, "%.1f", value);
        return String.format(Locale.ENGLISH, "%.2f", value);
    }
}
