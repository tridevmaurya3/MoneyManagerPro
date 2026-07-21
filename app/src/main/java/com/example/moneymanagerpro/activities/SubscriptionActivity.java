package com.example.moneymanagerpro.activities;

import android.app.DatePickerDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.utils.ReminderScheduler;
import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Account;
import com.example.moneymanagerpro.model.Subscription;
import com.example.moneymanagerpro.model.Transaction;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class SubscriptionActivity extends AppCompatActivity {

    private TextInputLayout inputName;
    private TextInputLayout inputAmount;

    private TextInputEditText etName;
    private TextInputEditText etAmount;
    private TextInputEditText etDueDate;
    private TextInputEditText etNote;

    private MaterialAutoCompleteTextView dropdownBillingCycle;
    private MaterialAutoCompleteTextView dropdownAccount;
    private MaterialAutoCompleteTextView dropdownReminder;

    private MaterialButton btnSaveSubscription;
    private LinearLayout subscriptionContainer;
    private TextView txtEmptySubscriptions;

    private Calendar selectedCalendar;
    private String selectedDueDate;

    private final String[] billingCycles = {
            "Weekly", "Monthly", "Yearly"
    };

    private final String[] reminderOptions = {
            "On Due Date",
            "1 Day Before",
            "3 Days Before",
            "7 Days Before"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subscription);

        inputName = findViewById(R.id.inputName);
        inputAmount = findViewById(R.id.inputAmount);

        etName = findViewById(R.id.etName);
        etAmount = findViewById(R.id.etAmount);
        etDueDate = findViewById(R.id.etDueDate);
        etNote = findViewById(R.id.etNote);

        dropdownBillingCycle = findViewById(R.id.dropdownBillingCycle);
        dropdownAccount = findViewById(R.id.dropdownAccount);
        dropdownReminder = findViewById(R.id.dropdownReminder);

        btnSaveSubscription = findViewById(R.id.btnSaveSubscription);
        subscriptionContainer = findViewById(R.id.subscriptionContainer);
        txtEmptySubscriptions = findViewById(R.id.txtEmptySubscriptions);

        TextView btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());

        selectedCalendar = Calendar.getInstance();
        updateDueDateField();

        setupDropdowns();

        etDueDate.setOnClickListener(v -> showDatePicker());

        BubbleTouchAnimator.apply(btnSaveSubscription);

        btnSaveSubscription.setOnClickListener(v -> saveSubscription());
        ReminderScheduler.scheduleDaily(getApplicationContext());
        requestNotificationPermission();
    }
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    501
            );
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        loadAccounts();
        loadSubscriptions();
    }

    private void setupDropdowns() {
        ArrayAdapter<String> cycleAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                billingCycles
        );

        ArrayAdapter<String> reminderAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                reminderOptions
        );

        dropdownBillingCycle.setAdapter(cycleAdapter);
        dropdownReminder.setAdapter(reminderAdapter);

        dropdownBillingCycle.setText("Monthly", false);
        dropdownReminder.setText("3 Days Before", false);
    }

    private void loadAccounts() {
        new Thread(() -> {
            List<Account> accounts = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .accountDao()
                    .getAllAccounts();

            List<String> accountNames = new ArrayList<>();

            for (Account account : accounts) {
                accountNames.add(account.getName());
            }

            if (accountNames.isEmpty()) {
                accountNames.add("Cash");
            }

            runOnUiThread(() -> {
                ArrayAdapter<String> accountAdapter = new ArrayAdapter<>(
                        SubscriptionActivity.this,
                        android.R.layout.simple_list_item_1,
                        accountNames
                );

                dropdownAccount.setAdapter(accountAdapter);

                String selectedAccount = accountNames.get(0);

                for (String accountName : accountNames) {
                    if (accountName.equalsIgnoreCase("Cash")) {
                        selectedAccount = accountName;
                        break;
                    }
                }

                dropdownAccount.setText(selectedAccount, false);
            });
        }).start();
    }

    private void showDatePicker() {
        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    selectedCalendar.set(Calendar.YEAR, year);
                    selectedCalendar.set(Calendar.MONTH, month);
                    selectedCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                    updateDueDateField();
                },
                selectedCalendar.get(Calendar.YEAR),
                selectedCalendar.get(Calendar.MONTH),
                selectedCalendar.get(Calendar.DAY_OF_MONTH)
        );

        dialog.show();
    }

    private void updateDueDateField() {
        selectedDueDate = new SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.US
        ).format(selectedCalendar.getTime());

        String visibleDate = new SimpleDateFormat(
                "dd MMMM yyyy",
                Locale.ENGLISH
        ).format(selectedCalendar.getTime());

        etDueDate.setText(visibleDate);
    }

    private void saveSubscription() {
        String name = etName.getText() == null
                ? ""
                : etName.getText().toString().trim();

        String amountText = etAmount.getText() == null
                ? ""
                : etAmount.getText().toString().trim();

        if (name.isEmpty()) {
            inputName.setError("Please enter bill or subscription name");
            return;
        }

        double amount;

        try {
            amount = Double.parseDouble(amountText);
        } catch (Exception exception) {
            inputAmount.setError("Enter a valid amount");
            return;
        }

        if (amount <= 0) {
            inputAmount.setError("Amount must be greater than zero");
            return;
        }

        inputName.setError(null);
        inputAmount.setError(null);

        Subscription subscription = new Subscription();
        subscription.setName(name);
        subscription.setAmount(amount);
        subscription.setBillingCycle(
                dropdownBillingCycle.getText().toString().trim()
        );
        subscription.setNextDueDate(selectedDueDate);
        subscription.setAccount(
                dropdownAccount.getText().toString().trim()
        );
        subscription.setCategory("Subscriptions");
        subscription.setRemindDays(
                getReminderDays(
                        dropdownReminder.getText().toString().trim()
                )
        );
        subscription.setNote(
                etNote.getText() == null
                        ? ""
                        : etNote.getText().toString().trim()
        );
        subscription.setActive(true);

        btnSaveSubscription.setEnabled(false);
        btnSaveSubscription.setText("Saving Bill...");

        new Thread(() -> {
            DatabaseClient.getInstance(getApplicationContext())
                    .getAppDatabase()
                    .subscriptionDao()
                    .insert(subscription);

            runOnUiThread(() -> {
                etName.setText("");
                etAmount.setText("");
                etNote.setText("");
                dropdownBillingCycle.setText("Monthly", false);
                dropdownReminder.setText("3 Days Before", false);

                btnSaveSubscription.setEnabled(true);
                btnSaveSubscription.setText("Save Bill / Subscription");

                Toast.makeText(
                        SubscriptionActivity.this,
                        "Bill saved successfully",
                        Toast.LENGTH_SHORT
                ).show();

                loadSubscriptions();
            });
        }).start();
    }

    private void loadSubscriptions() {
        new Thread(() -> {
            List<Subscription> subscriptions = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .subscriptionDao()
                    .getAllSubscriptions();

            runOnUiThread(() -> showSubscriptions(subscriptions));
        }).start();
    }

    private void showSubscriptions(List<Subscription> subscriptions) {
        subscriptionContainer.removeAllViews();

        txtEmptySubscriptions.setVisibility(
                subscriptions.isEmpty() ? View.VISIBLE : View.GONE
        );

        for (Subscription subscription : subscriptions) {
            addSubscriptionCard(subscription);
        }
    }

    private void addSubscriptionCard(Subscription subscription) {
        int statusColor = getDueColor(subscription);

        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(Color.WHITE);
        card.setRadius(dpToPx(22));
        card.setCardElevation(dpToPx(5));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dpToPx(12));
        card.setLayoutParams(cardParams);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));

        TextView txtName = new TextView(this);
        txtName.setText(subscription.getName());
        txtName.setTextSize(20);
        txtName.setTextColor(Color.parseColor("#172033"));
        txtName.setGravity(Gravity.CENTER);
        txtName.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        TextView txtAmount = new TextView(this);
        txtAmount.setText(formatAmount(subscription.getAmount()));
        txtAmount.setTextSize(22);
        txtAmount.setTextColor(Color.parseColor("#7B1FA2"));
        txtAmount.setGravity(Gravity.CENTER);
        txtAmount.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        TextView txtDetails = new TextView(this);
        txtDetails.setText(
                subscription.getBillingCycle()
                        + " • "
                        + subscription.getAccount()
                        + "\nDue Date: "
                        + subscription.getNextDueDate()
        );
        txtDetails.setTextSize(13);
        txtDetails.setTextColor(Color.parseColor("#64748B"));
        txtDetails.setGravity(Gravity.CENTER);

        TextView txtStatus = new TextView(this);
        txtStatus.setText(
                subscription.isActive()
                        ? getDueText(subscription)
                        : "Paused Bill"
        );
        txtStatus.setTextSize(14);
        txtStatus.setGravity(Gravity.CENTER);
        txtStatus.setTextColor(
                subscription.isActive()
                        ? statusColor
                        : Color.parseColor("#64748B")
        );
        txtStatus.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams actionRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(48)
        );
        actionRowParams.setMargins(0, dpToPx(14), 0, 0);
        actionRow.setLayoutParams(actionRowParams);

        MaterialButton btnMarkPaid = new MaterialButton(this);
        btnMarkPaid.setText("Mark Paid");
        btnMarkPaid.setTextColor(Color.WHITE);
        btnMarkPaid.setTextSize(11);
        btnMarkPaid.setAllCaps(false);
        btnMarkPaid.setCornerRadius(dpToPx(20));
        btnMarkPaid.setEnabled(subscription.isActive());
        btnMarkPaid.setBackgroundTintList(
                ColorStateList.valueOf(Color.parseColor("#2E7D32"))
        );

        MaterialButton btnPause = new MaterialButton(this);
        btnPause.setText(subscription.isActive() ? "Pause" : "Resume");
        btnPause.setTextColor(Color.WHITE);
        btnPause.setTextSize(11);
        btnPause.setAllCaps(false);
        btnPause.setCornerRadius(dpToPx(20));
        btnPause.setBackgroundTintList(
                ColorStateList.valueOf(
                        subscription.isActive()
                                ? Color.parseColor("#EF6C00")
                                : Color.parseColor("#1565C0")
                )
        );

        MaterialButton btnDelete = new MaterialButton(this);
        btnDelete.setText("Delete");
        btnDelete.setTextColor(Color.WHITE);
        btnDelete.setTextSize(11);
        btnDelete.setAllCaps(false);
        btnDelete.setCornerRadius(dpToPx(20));
        btnDelete.setBackgroundTintList(
                ColorStateList.valueOf(Color.parseColor("#455A64"))
        );

        LinearLayout.LayoutParams paidParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
        );
        paidParams.setMargins(0, 0, dpToPx(4), 0);

        LinearLayout.LayoutParams pauseParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
        );
        pauseParams.setMargins(dpToPx(2), 0, dpToPx(2), 0);

        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
        );
        deleteParams.setMargins(dpToPx(4), 0, 0, 0);

        btnMarkPaid.setLayoutParams(paidParams);
        btnPause.setLayoutParams(pauseParams);
        btnDelete.setLayoutParams(deleteParams);

        BubbleTouchAnimator.apply(card);
        BubbleTouchAnimator.apply(btnMarkPaid);
        BubbleTouchAnimator.apply(btnPause);
        BubbleTouchAnimator.apply(btnDelete);

        btnMarkPaid.setOnClickListener(v -> markAsPaid(subscription));
        btnPause.setOnClickListener(v -> toggleSubscription(subscription));
        btnDelete.setOnClickListener(v -> confirmDelete(subscription));

        actionRow.addView(btnMarkPaid);
        actionRow.addView(btnPause);
        actionRow.addView(btnDelete);

        content.addView(txtName);
        content.addView(txtAmount);
        content.addView(txtDetails);
        content.addView(txtStatus);
        content.addView(actionRow);

        card.addView(content);
        subscriptionContainer.addView(card);
    }

    private void markAsPaid(Subscription subscription) {
        Transaction transaction = new Transaction();
        transaction.setType("EXPENSE");
        transaction.setAmount(subscription.getAmount());
        transaction.setCategory(
                subscription.getCategory().isEmpty()
                        ? "Subscriptions"
                        : subscription.getCategory()
        );
        transaction.setAccount(subscription.getAccount());
        transaction.setNote("Subscription payment - " + subscription.getName());
        transaction.setDate(
                new SimpleDateFormat(
                        "yyyy-MM-dd HH:mm",
                        Locale.US
                ).format(Calendar.getInstance().getTime())
        );

        subscription.setNextDueDate(
                getNextFutureDueDate(
                        subscription.getNextDueDate(),
                        subscription.getBillingCycle()
                )
        );

        new Thread(() -> {
            DatabaseClient.getInstance(getApplicationContext())
                    .getAppDatabase()
                    .runInTransaction(() -> {
                        DatabaseClient.getInstance(getApplicationContext())
                                .getAppDatabase()
                                .transactionDao()
                                .insert(transaction);

                        DatabaseClient.getInstance(getApplicationContext())
                                .getAppDatabase()
                                .subscriptionDao()
                                .update(subscription);
                    });

            runOnUiThread(() -> {
                Toast.makeText(
                        SubscriptionActivity.this,
                        "Expense added and next due date updated",
                        Toast.LENGTH_SHORT
                ).show();

                loadSubscriptions();
            });
        }).start();
    }

    private void toggleSubscription(Subscription subscription) {
        subscription.setActive(!subscription.isActive());

        new Thread(() -> {
            DatabaseClient.getInstance(getApplicationContext())
                    .getAppDatabase()
                    .subscriptionDao()
                    .update(subscription);

            runOnUiThread(() -> {
                Toast.makeText(
                        SubscriptionActivity.this,
                        subscription.isActive()
                                ? "Bill resumed"
                                : "Bill paused",
                        Toast.LENGTH_SHORT
                ).show();

                loadSubscriptions();
            });
        }).start();
    }

    private void confirmDelete(Subscription subscription) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Bill")
                .setMessage(
                        "Do you want to delete \""
                                + subscription.getName()
                                + "\"?"
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    new Thread(() -> {
                        DatabaseClient.getInstance(getApplicationContext())
                                .getAppDatabase()
                                .subscriptionDao()
                                .delete(subscription);

                        runOnUiThread(() -> {
                            Toast.makeText(
                                    SubscriptionActivity.this,
                                    "Bill deleted",
                                    Toast.LENGTH_SHORT
                            ).show();

                            loadSubscriptions();
                        });
                    }).start();
                })
                .show();
    }

    private int getReminderDays(String reminderOption) {
        if (reminderOption.equalsIgnoreCase("1 Day Before")) {
            return 1;
        }

        if (reminderOption.equalsIgnoreCase("3 Days Before")) {
            return 3;
        }

        if (reminderOption.equalsIgnoreCase("7 Days Before")) {
            return 7;
        }

        return 0;
    }

    private String getDueText(Subscription subscription) {
        int days = getDaysUntilDue(subscription.getNextDueDate());

        if (days < 0) {
            return "Overdue by " + Math.abs(days) + " day(s)";
        }

        if (days == 0) {
            return "Due Today";
        }

        return "Due in " + days + " day(s)";
    }

    private int getDueColor(Subscription subscription) {
        int days = getDaysUntilDue(subscription.getNextDueDate());

        if (days <= 0) {
            return Color.parseColor("#D32F2F");
        }

        if (days <= subscription.getRemindDays()) {
            return Color.parseColor("#EF6C00");
        }

        return Color.parseColor("#2E7D32");
    }

    private int getDaysUntilDue(String dueDate) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat(
                    "yyyy-MM-dd",
                    Locale.US
            );

            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);

            Calendar due = Calendar.getInstance();
            due.setTime(dateFormat.parse(dueDate));
            due.set(Calendar.HOUR_OF_DAY, 0);
            due.set(Calendar.MINUTE, 0);
            due.set(Calendar.SECOND, 0);
            due.set(Calendar.MILLISECOND, 0);

            long difference = due.getTimeInMillis() - today.getTimeInMillis();

            return (int) (difference / (24 * 60 * 60 * 1000));

        } catch (Exception exception) {
            return 0;
        }
    }

    private String getNextFutureDueDate(String dueDate, String billingCycle) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat(
                    "yyyy-MM-dd",
                    Locale.US
            );

            Calendar calendar = Calendar.getInstance();
            calendar.setTime(dateFormat.parse(dueDate));

            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);

            int safetyCounter = 0;

            while (calendar.getTimeInMillis() <= today.getTimeInMillis()
                    && safetyCounter < 120) {

                if (billingCycle.equalsIgnoreCase("Weekly")) {
                    calendar.add(Calendar.DAY_OF_MONTH, 7);
                } else if (billingCycle.equalsIgnoreCase("Yearly")) {
                    calendar.add(Calendar.YEAR, 1);
                } else {
                    calendar.add(Calendar.MONTH, 1);
                }

                safetyCounter++;
            }

            return dateFormat.format(calendar.getTime());

        } catch (Exception exception) {
            return dueDate;
        }
    }

    private String formatAmount(double amount) {
        NumberFormat numberFormat = NumberFormat.getCurrencyInstance(
                new Locale("en", "IN")
        );

        return numberFormat.format(amount);
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density);
    }
}