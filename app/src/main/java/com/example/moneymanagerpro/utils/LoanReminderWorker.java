package com.example.moneymanagerpro.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.activities.LoanActivity;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Loan;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class LoanReminderWorker extends Worker {

    private static final String CHANNEL_ID = "loan_reminder_channel";
    private static final String CHANNEL_NAME = "Loan EMI Reminders";

    public LoanReminderWorker(
            @NonNull Context context,
            @NonNull WorkerParameters workerParams
    ) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            List<Loan> loans = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .loanDao()
                    .getActiveLoans();

            for (Loan loan : loans) {
                showReminderIfNeeded(loan);
            }

            return Result.success();

        } catch (Exception exception) {
            return Result.retry();
        }
    }

    private void showReminderIfNeeded(Loan loan) {
        Calendar dueCalendar = parseDate(loan.getDueDate());

        if (dueCalendar == null) {
            return;
        }

        Calendar today = Calendar.getInstance();
        clearTime(today);
        clearTime(dueCalendar);

        long difference = dueCalendar.getTimeInMillis()
                - today.getTimeInMillis();

        long daysLeft = difference / (24 * 60 * 60 * 1000);

        String title;
        String message;

        if (daysLeft < 0) {
            title = "Loan EMI Overdue";
            message = loan.getPersonName()
                    + ": EMI is overdue by "
                    + Math.abs(daysLeft)
                    + " day(s). Remaining: "
                    + formatAmount(loan.getOutstandingAmount());

        } else if (daysLeft == 0) {
            title = "Loan EMI Due Today";
            message = loan.getPersonName()
                    + ": Pay EMI today. Remaining: "
                    + formatAmount(loan.getOutstandingAmount());

        } else if (daysLeft <= 3) {
            title = "Upcoming Loan EMI";
            message = loan.getPersonName()
                    + ": EMI is due in "
                    + daysLeft
                    + " day(s). Remaining: "
                    + formatAmount(loan.getOutstandingAmount());

        } else {
            return;
        }

        createNotificationChannel();

        Intent openLoan = new Intent(getApplicationContext(), LoanActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openLoanIntent = PendingIntent.getActivity(
                getApplicationContext(),
                5000 + loan.getId(),
                openLoan,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(
                        getApplicationContext(),
                        CHANNEL_ID
                )
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setStyle(new NotificationCompat.BigTextStyle()
                                .bigText(message))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(openLoanIntent)
                        .setAutoCancel(true);

        NotificationManager notificationManager =
                (NotificationManager) getApplicationContext()
                        .getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            notificationManager.notify(
                    5000 + loan.getId(),
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
                    "Reminders for loan EMI due dates"
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

    private Calendar parseDate(String date) {
        try {
            Calendar calendar = Calendar.getInstance();

            calendar.setTime(
                    new SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale.US
                    ).parse(date)
            );

            return calendar;

        } catch (Exception exception) {
            return null;
        }
    }

    private void clearTime(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private String formatAmount(double amount) {
        NumberFormat numberFormat = NumberFormat.getCurrencyInstance(
                new Locale("en", "IN")
        );

        return numberFormat.format(amount);
    }
}
