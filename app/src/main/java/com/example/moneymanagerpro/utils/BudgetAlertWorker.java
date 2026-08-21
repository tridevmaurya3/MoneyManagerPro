package com.example.moneymanagerpro.utils;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.activities.BudgetActivity;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Budget;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class BudgetAlertWorker extends Worker {

    private static final String CHANNEL_ID = "budget_alert_channel";
    private static final String CHANNEL_NAME = "Budget Alerts";

    public BudgetAlertWorker(
            @NonNull Context context,
            @NonNull WorkerParameters workerParameters
    ) {
        super(context, workerParameters);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            List<Budget> budgets = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .budgetDao()
                    .getAllBudgets();

            for (Budget budget : budgets) {
                checkBudget(budget);
            }

            return Result.success();

        } catch (Exception exception) {
            return Result.retry();
        }
    }

    private void checkBudget(Budget budget) {
        if (budget.getLimitAmount() <= 0) {
            return;
        }

        DateRange range = getDateRange(budget.getPeriod());

        double spentAmount = DatabaseClient
                .getInstance(getApplicationContext())
                .getAppDatabase()
                .transactionDao()
                .getExpenseTotalForCategoryPeriod(
                        budget.getCategory(),
                        range.startDate,
                        range.endDate
                );

        double percentage = (spentAmount / budget.getLimitAmount()) * 100;

        String alertLevel;

        if (percentage >= 100) {
            alertLevel = "LIMIT_CROSSED";
        } else if (percentage >= 80) {
            alertLevel = "WARNING_80";
        } else {
            return;
        }

        String preferenceKey = "budget_"
                + budget.getId()
                + "_"
                + getPeriodKey(budget.getPeriod())
                + "_"
                + alertLevel;

        SharedPreferences preferences = getApplicationContext()
                .getSharedPreferences(
                        "MoneyManagerBudgetAlerts",
                        Context.MODE_PRIVATE
                );

        if (preferences.getBoolean(preferenceKey, false)) {
            return;
        }

        showBudgetNotification(
                budget,
                spentAmount,
                percentage,
                alertLevel
        );

        preferences.edit()
                .putBoolean(preferenceKey, true)
                .apply();
    }

    private void showBudgetNotification(
            Budget budget,
            double spentAmount,
            double percentage,
            String alertLevel
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && getApplicationContext().checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        String title;
        String message;

        if (alertLevel.equals("LIMIT_CROSSED")) {
            title = "Budget Limit Crossed";
            message = budget.getCategory()
                    + " budget is "
                    + Math.round(percentage)
                    + "% used. Spent "
                    + formatAmount(spentAmount)
                    + " out of "
                    + formatAmount(budget.getLimitAmount());

        } else {
            title = "Budget Warning";
            message = budget.getCategory()
                    + " budget is already "
                    + Math.round(percentage)
                    + "% used. Remaining "
                    + formatAmount(
                    Math.max(
                            budget.getLimitAmount() - spentAmount,
                            0
                    )
            );
        }

        createNotificationChannel();

        Intent openBudget = new Intent(getApplicationContext(), BudgetActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openBudgetIntent = PendingIntent.getActivity(
                getApplicationContext(),
                7000 + budget.getId(),
                openBudget,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(
                        getApplicationContext(),
                        CHANNEL_ID
                )
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setStyle(
                                new NotificationCompat.BigTextStyle()
                                        .bigText(message)
                        )
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(openBudgetIntent)
                        .setAutoCancel(true);

        NotificationManager notificationManager =
                (NotificationManager) getApplicationContext()
                        .getSystemService(
                                Context.NOTIFICATION_SERVICE
                        );

        if (notificationManager != null) {
            int notificationId = 7000
                    + (budget.getId() * 10)
                    + (alertLevel.equals("LIMIT_CROSSED") ? 2 : 1);

            notificationManager.notify(
                    notificationId,
                    builder.build()
            );
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );

            channel.setDescription(
                    "Warnings when category budgets reach their limit"
            );

            NotificationManager notificationManager =
                    (NotificationManager) getApplicationContext()
                            .getSystemService(
                                    Context.NOTIFICATION_SERVICE
                            );

            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private DateRange getDateRange(String period) {
        Calendar startCalendar = Calendar.getInstance();
        Calendar endCalendar = Calendar.getInstance();

        clearTime(startCalendar);
        clearTime(endCalendar);

        if (period.equalsIgnoreCase("Weekly")) {
            int day = startCalendar.get(Calendar.DAY_OF_WEEK);
            int difference = day - Calendar.MONDAY;

            if (difference < 0) {
                difference += 7;
            }

            startCalendar.add(Calendar.DAY_OF_MONTH, -difference);

            endCalendar = (Calendar) startCalendar.clone();
            endCalendar.add(Calendar.DAY_OF_MONTH, 6);

        } else if (period.equalsIgnoreCase("Yearly")) {
            startCalendar.set(Calendar.MONTH, Calendar.JANUARY);
            startCalendar.set(Calendar.DAY_OF_MONTH, 1);

            endCalendar.set(Calendar.MONTH, Calendar.DECEMBER);
            endCalendar.set(Calendar.DAY_OF_MONTH, 31);

        } else {
            startCalendar.set(Calendar.DAY_OF_MONTH, 1);

            endCalendar.set(
                    Calendar.DAY_OF_MONTH,
                    endCalendar.getActualMaximum(
                            Calendar.DAY_OF_MONTH
                    )
            );
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.US
        );

        return new DateRange(
                dateFormat.format(startCalendar.getTime()),
                dateFormat.format(endCalendar.getTime()) + " 23:59"
        );
    }

    private String getPeriodKey(String period) {
        if (period.equalsIgnoreCase("Weekly")) {
            return new SimpleDateFormat(
                    "yyyy-MM-dd",
                    Locale.US
            ).format(Calendar.getInstance().getTime());
        }

        if (period.equalsIgnoreCase("Yearly")) {
            return new SimpleDateFormat(
                    "yyyy",
                    Locale.US
            ).format(Calendar.getInstance().getTime());
        }

        return new SimpleDateFormat(
                "yyyy-MM",
                Locale.US
        ).format(Calendar.getInstance().getTime());
    }

    private void clearTime(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private String formatAmount(double amount) {
        NumberFormat formatter = NumberFormat.getNumberInstance(
                new Locale("en", "IN")
        );

        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);

        return "₹" + formatter.format(amount);
    }

    private static class DateRange {
        String startDate;
        String endDate;

        DateRange(String startDate, String endDate) {
            this.startDate = startDate;
            this.endDate = endDate;
        }
    }
}
