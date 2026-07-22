package com.example.moneymanagerpro.activities;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.database.AppDatabase;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Account;
import com.example.moneymanagerpro.model.Subscription;
import com.example.moneymanagerpro.model.Transaction;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
import com.example.moneymanagerpro.utils.ReminderScheduler;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.NumberFormat;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SubscriptionActivity extends AppCompatActivity {

    private static final int NOTIFICATION_PERMISSION_REQUEST = 501;

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
    private String selectedDueDate = "";

    private int subscriptionLoadVersion = 0;
    private int accountLoadVersion = 0;

    private boolean saveInProgress = false;
    private boolean actionInProgress = false;

    private final String[] billingCycles = {
            "Weekly",
            "Monthly",
            "Yearly"
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

        bindViews();
        prepareScreen();

        scheduleRemindersSafely();
        requestNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();

        loadAccounts();
        loadSubscriptions();
    }

    @Override
    protected void onDestroy() {
        subscriptionLoadVersion++;
        accountLoadVersion++;

        super.onDestroy();
    }

    private void bindViews() {
        TextView btnBack =
                findViewById(R.id.btnBack);

        inputName =
                findViewById(R.id.inputName);

        inputAmount =
                findViewById(R.id.inputAmount);

        etName =
                findViewById(R.id.etName);

        etAmount =
                findViewById(R.id.etAmount);

        etDueDate =
                findViewById(R.id.etDueDate);

        etNote =
                findViewById(R.id.etNote);

        dropdownBillingCycle =
                findViewById(R.id.dropdownBillingCycle);

        dropdownAccount =
                findViewById(R.id.dropdownAccount);

        dropdownReminder =
                findViewById(R.id.dropdownReminder);

        btnSaveSubscription =
                findViewById(R.id.btnSaveSubscription);

        subscriptionContainer =
                findViewById(R.id.subscriptionContainer);

        txtEmptySubscriptions =
                findViewById(R.id.txtEmptySubscriptions);

        btnBack.setOnClickListener(
                view -> finish()
        );

        BubbleTouchAnimator.apply(btnBack);
    }

    private void prepareScreen() {
        selectedCalendar =
                Calendar.getInstance();

        clearTime(selectedCalendar);
        updateDueDateField();

        setupDropdowns();

        etDueDate.setOnClickListener(
                view -> showDatePicker()
        );

        btnSaveSubscription.setOnClickListener(
                view -> saveSubscription()
        );

        BubbleTouchAnimator.apply(
                btnSaveSubscription
        );

        showSubscriptionsLoading();
    }

    private void setupDropdowns() {
        ArrayAdapter<String> cycleAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_list_item_1,
                        billingCycles
                );

        ArrayAdapter<String> reminderAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_list_item_1,
                        reminderOptions
                );

        dropdownBillingCycle.setAdapter(
                cycleAdapter
        );

        dropdownReminder.setAdapter(
                reminderAdapter
        );

        dropdownBillingCycle.setText(
                "Monthly",
                false
        );

        dropdownReminder.setText(
                "3 Days Before",
                false
        );
    }

    private void loadAccounts() {
        int currentRequest =
                ++accountLoadVersion;

        String currentSelection =
                safeText(
                        dropdownAccount.getText() == null
                                ? ""
                                : dropdownAccount.getText().toString(),
                        ""
                );

        new Thread(() -> {
            try {
                List<Account> accounts =
                        DatabaseClient
                                .getInstance(
                                        getApplicationContext()
                                )
                                .getAppDatabase()
                                .accountDao()
                                .getAllAccounts();

                List<String> accountNames =
                        new ArrayList<>();

                if (accounts != null) {
                    for (Account account : accounts) {
                        if (account == null) {
                            continue;
                        }

                        String accountName =
                                safeText(
                                        account.getName(),
                                        ""
                                );

                        if (!accountName.isEmpty()
                                && !containsIgnoreCase(
                                accountNames,
                                accountName
                        )) {

                            accountNames.add(
                                    accountName
                            );
                        }
                    }
                }

                if (accountNames.isEmpty()) {
                    accountNames.add("Cash");
                }

                runOnUiThread(() -> {
                    if (currentRequest != accountLoadVersion
                            || isUiUnavailable()) {

                        return;
                    }

                    ArrayAdapter<String> accountAdapter =
                            new ArrayAdapter<>(
                                    SubscriptionActivity.this,
                                    android.R.layout.simple_list_item_1,
                                    accountNames
                            );

                    dropdownAccount.setAdapter(
                            accountAdapter
                    );

                    String selectedAccount =
                            findMatchingValue(
                                    accountNames,
                                    currentSelection
                            );

                    if (selectedAccount.isEmpty()) {
                        selectedAccount =
                                findMatchingValue(
                                        accountNames,
                                        "Cash"
                                );
                    }

                    if (selectedAccount.isEmpty()) {
                        selectedAccount =
                                accountNames.get(0);
                    }

                    dropdownAccount.setText(
                            selectedAccount,
                            false
                    );
                });

            } catch (Exception exception) {
                runOnUiThread(() -> {
                    if (currentRequest != accountLoadVersion
                            || isUiUnavailable()) {

                        return;
                    }

                    List<String> fallbackAccounts =
                            new ArrayList<>();

                    fallbackAccounts.add("Cash");

                    ArrayAdapter<String> accountAdapter =
                            new ArrayAdapter<>(
                                    SubscriptionActivity.this,
                                    android.R.layout.simple_list_item_1,
                                    fallbackAccounts
                            );

                    dropdownAccount.setAdapter(
                            accountAdapter
                    );

                    dropdownAccount.setText(
                            "Cash",
                            false
                    );
                });
            }
        }).start();
    }

    private void showDatePicker() {
        DatePickerDialog dialog =
                new DatePickerDialog(
                        this,
                        (view, year, month, dayOfMonth) -> {
                            selectedCalendar.set(
                                    Calendar.YEAR,
                                    year
                            );

                            selectedCalendar.set(
                                    Calendar.MONTH,
                                    month
                            );

                            selectedCalendar.set(
                                    Calendar.DAY_OF_MONTH,
                                    dayOfMonth
                            );

                            clearTime(
                                    selectedCalendar
                            );

                            updateDueDateField();
                        },
                        selectedCalendar.get(
                                Calendar.YEAR
                        ),
                        selectedCalendar.get(
                                Calendar.MONTH
                        ),
                        selectedCalendar.get(
                                Calendar.DAY_OF_MONTH
                        )
                );

        dialog.show();
    }

    private void updateDueDateField() {
        selectedDueDate =
                new SimpleDateFormat(
                        "yyyy-MM-dd",
                        Locale.US
                ).format(
                        selectedCalendar.getTime()
                );

        String visibleDate =
                new SimpleDateFormat(
                        "dd MMMM yyyy",
                        Locale.ENGLISH
                ).format(
                        selectedCalendar.getTime()
                );

        etDueDate.setText(
                visibleDate
        );
    }

    private void saveSubscription() {
        if (saveInProgress) {
            return;
        }

        inputName.setError(null);
        inputAmount.setError(null);

        String name =
                getInputText(etName);

        String amountText =
                getInputText(etAmount);

        String billingCycle =
                safeText(
                        dropdownBillingCycle.getText() == null
                                ? ""
                                : dropdownBillingCycle.getText().toString(),
                        ""
                );

        String account =
                safeText(
                        dropdownAccount.getText() == null
                                ? ""
                                : dropdownAccount.getText().toString(),
                        ""
                );

        String reminderOption =
                safeText(
                        dropdownReminder.getText() == null
                                ? ""
                                : dropdownReminder.getText().toString(),
                        ""
                );

        String note =
                getInputText(etNote);

        if (name.isEmpty()) {
            inputName.setError(
                    "Please enter bill or subscription name"
            );

            etName.requestFocus();
            return;
        }

        Double amount =
                parseAmount(
                        amountText
                );

        if (amount == null) {
            inputAmount.setError(
                    "Enter a valid amount"
            );

            etAmount.requestFocus();
            return;
        }

        if (amount <= 0
                || Double.isNaN(amount)
                || Double.isInfinite(amount)) {

            inputAmount.setError(
                    "Amount must be greater than zero"
            );

            etAmount.requestFocus();
            return;
        }

        if (!isSupportedBillingCycle(
                billingCycle
        )) {
            Toast.makeText(
                    this,
                    "Select a valid billing cycle",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (account.isEmpty()) {
            account = "Cash";
        }

        if (!isSupportedReminder(
                reminderOption
        )) {
            Toast.makeText(
                    this,
                    "Select a valid reminder option",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (selectedDueDate.isEmpty()
                || parseStoredDate(
                selectedDueDate
        ) == null) {

            Toast.makeText(
                    this,
                    "Select a valid due date",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        Subscription subscription =
                new Subscription();

        subscription.setName(name);
        subscription.setAmount(amount);
        subscription.setBillingCycle(billingCycle);
        subscription.setNextDueDate(selectedDueDate);
        subscription.setAccount(account);
        subscription.setCategory("Subscriptions");
        subscription.setRemindDays(
                getReminderDays(
                        reminderOption
                )
        );
        subscription.setNote(note);
        subscription.setActive(true);

        setSaveState(true);

        new Thread(() -> {
            try {
                DatabaseClient
                        .getInstance(
                                getApplicationContext()
                        )
                        .getAppDatabase()
                        .subscriptionDao()
                        .insert(subscription);

                runOnUiThread(() -> {
                    if (isUiUnavailable()) {
                        return;
                    }

                    setSaveState(false);
                    clearSubscriptionForm();

                    scheduleRemindersSafely();

                    Toast.makeText(
                            SubscriptionActivity.this,
                            "Bill saved successfully",
                            Toast.LENGTH_SHORT
                    ).show();

                    loadSubscriptions();
                });

            } catch (Exception exception) {
                runOnUiThread(() -> {
                    if (isUiUnavailable()) {
                        return;
                    }

                    setSaveState(false);

                    Toast.makeText(
                            SubscriptionActivity.this,
                            "Unable to save this bill",
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }
        }).start();
    }

    private void setSaveState(
            boolean saving
    ) {
        saveInProgress = saving;

        btnSaveSubscription.setEnabled(
                !saving
        );

        btnSaveSubscription.setAlpha(
                saving
                        ? 0.58f
                        : 1f
        );

        btnSaveSubscription.setText(
                saving
                        ? "Saving Bill..."
                        : "Save Bill / Subscription"
        );
    }

    private void clearSubscriptionForm() {
        etName.setText("");
        etAmount.setText("");
        etNote.setText("");

        dropdownBillingCycle.setText(
                "Monthly",
                false
        );

        dropdownReminder.setText(
                "3 Days Before",
                false
        );

        selectedCalendar =
                Calendar.getInstance();

        clearTime(
                selectedCalendar
        );

        updateDueDateField();

        inputName.setError(null);
        inputAmount.setError(null);
    }

    private void loadSubscriptions() {
        int currentRequest =
                ++subscriptionLoadVersion;

        showSubscriptionsLoading();

        new Thread(() -> {
            try {
                List<Subscription> subscriptions =
                        DatabaseClient
                                .getInstance(
                                        getApplicationContext()
                                )
                                .getAppDatabase()
                                .subscriptionDao()
                                .getAllSubscriptions();

                List<Subscription> safeSubscriptions =
                        subscriptions == null
                                ? new ArrayList<>()
                                : new ArrayList<>(
                                subscriptions
                        );

                sortSubscriptions(
                        safeSubscriptions
                );

                runOnUiThread(() -> {
                    if (currentRequest
                            != subscriptionLoadVersion
                            || isUiUnavailable()) {

                        return;
                    }

                    actionInProgress = false;

                    showSubscriptions(
                            safeSubscriptions
                    );
                });

            } catch (Exception exception) {
                runOnUiThread(() -> {
                    if (currentRequest
                            != subscriptionLoadVersion
                            || isUiUnavailable()) {

                        return;
                    }

                    actionInProgress = false;
                    showSubscriptionsError();
                });
            }
        }).start();
    }

    private void sortSubscriptions(
            List<Subscription> subscriptions
    ) {
        Collections.sort(
                subscriptions,
                (first, second) -> {
                    if (first == null && second == null) {
                        return 0;
                    }

                    if (first == null) {
                        return 1;
                    }

                    if (second == null) {
                        return -1;
                    }

                    if (first.isActive()
                            != second.isActive()) {

                        return first.isActive()
                                ? -1
                                : 1;
                    }

                    Date firstDate =
                            parseStoredDate(
                                    first.getNextDueDate()
                            );

                    Date secondDate =
                            parseStoredDate(
                                    second.getNextDueDate()
                            );

                    if (firstDate == null
                            && secondDate == null) {

                        return safeText(
                                first.getName(),
                                ""
                        ).compareToIgnoreCase(
                                safeText(
                                        second.getName(),
                                        ""
                                )
                        );
                    }

                    if (firstDate == null) {
                        return 1;
                    }

                    if (secondDate == null) {
                        return -1;
                    }

                    return firstDate.compareTo(
                            secondDate
                    );
                }
        );
    }

    private void showSubscriptionsLoading() {
        txtEmptySubscriptions.setVisibility(
                View.GONE
        );

        subscriptionContainer.removeAllViews();

        addContainerStatusCard(
                "Loading saved bills",
                "Checking upcoming payments and reminder status.",
                "↻",
                StatusTone.PURPLE
        );
    }

    private void showSubscriptions(
            List<Subscription> subscriptions
    ) {
        subscriptionContainer.removeAllViews();

        boolean empty =
                subscriptions == null
                        || subscriptions.isEmpty();

        txtEmptySubscriptions.setVisibility(
                empty
                        ? View.VISIBLE
                        : View.GONE
        );

        if (empty) {
            return;
        }

        for (Subscription subscription : subscriptions) {
            if (subscription != null) {
                addSubscriptionCard(
                        subscription
                );
            }
        }
    }

    private void showSubscriptionsError() {
        txtEmptySubscriptions.setVisibility(
                View.GONE
        );

        subscriptionContainer.removeAllViews();

        addContainerStatusCard(
                "Bills could not be loaded",
                "Reopen this screen to try reading the saved subscriptions again.",
                "!",
                StatusTone.EXPENSE
        );

        Toast.makeText(
                this,
                "Unable to load saved bills",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void addSubscriptionCard(
            Subscription subscription
    ) {
        DueStyle dueStyle =
                getDueStyle(
                        subscription
                );

        MaterialCardView card =
                new MaterialCardView(this);

        card.setCardBackgroundColor(
                getColorValue(
                        R.color.app_surface
                )
        );

        card.setRadius(
                dp(19)
        );

        card.setCardElevation(0);

        card.setStrokeWidth(
                dp(1)
        );

        card.setStrokeColor(
                dueStyle.outlineColor
        );

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        cardParams.setMargins(
                0,
                dp(4),
                0,
                dp(8)
        );

        card.setLayoutParams(
                cardParams
        );

        LinearLayout content =
                new LinearLayout(this);

        content.setOrientation(
                LinearLayout.VERTICAL
        );

        content.setPadding(
                dp(14),
                dp(14),
                dp(14),
                dp(14)
        );

        LinearLayout headerRow =
                new LinearLayout(this);

        headerRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        headerRow.setGravity(
                Gravity.CENTER_VERTICAL
        );

        TextView icon =
                createIcon(
                        dueStyle.symbol,
                        dueStyle.accentColor,
                        dueStyle.surfaceColor,
                        dueStyle.outlineColor
                );

        headerRow.addView(icon);

        LinearLayout titleContainer =
                new LinearLayout(this);

        titleContainer.setOrientation(
                LinearLayout.VERTICAL
        );

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        titleParams.setMargins(
                dp(11),
                0,
                dp(8),
                0
        );

        titleContainer.setLayoutParams(
                titleParams
        );

        TextView name =
                createText(
                        safeText(
                                subscription.getName(),
                                "Unnamed Bill"
                        ),
                        16,
                        getColorValue(
                                R.color.app_text_primary
                        ),
                        true
                );

        TextView cycle =
                createText(
                        safeText(
                                subscription.getBillingCycle(),
                                "Monthly"
                        )
                                + " recurring payment",
                        10,
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        false
                );

        LinearLayout.LayoutParams cycleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        cycleParams.setMargins(
                0,
                dp(3),
                0,
                0
        );

        cycle.setLayoutParams(
                cycleParams
        );

        titleContainer.addView(name);
        titleContainer.addView(cycle);

        headerRow.addView(
                titleContainer
        );

        TextView amount =
                createAmountBadge(
                        formatAmount(
                                subscription.getAmount()
                        ),
                        dueStyle.accentColor,
                        dueStyle.surfaceColor,
                        dueStyle.outlineColor
                );

        headerRow.addView(amount);

        content.addView(
                headerRow
        );

        TextView status =
                createStatusBadge(
                        dueStyle.statusText,
                        dueStyle.accentColor,
                        dueStyle.surfaceColor,
                        dueStyle.outlineColor
                );

        LinearLayout.LayoutParams statusParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        dp(32)
                );

        statusParams.setMargins(
                0,
                dp(12),
                0,
                0
        );

        status.setLayoutParams(
                statusParams
        );

        content.addView(status);

        LinearLayout detailBox =
                new LinearLayout(this);

        detailBox.setOrientation(
                LinearLayout.VERTICAL
        );

        detailBox.setPadding(
                dp(11),
                dp(10),
                dp(11),
                dp(10)
        );

        detailBox.setBackground(
                createRoundedDrawable(
                        getColorValue(
                                R.color.app_surface_soft
                        ),
                        getColorValue(
                                R.color.app_outline_soft
                        ),
                        13
                )
        );

        LinearLayout.LayoutParams detailBoxParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        detailBoxParams.setMargins(
                0,
                dp(11),
                0,
                0
        );

        detailBox.setLayoutParams(
                detailBoxParams
        );

        addDetailRow(
                detailBox,
                "Next due date",
                formatVisibleDate(
                        subscription.getNextDueDate()
                )
        );

        addDetailRow(
                detailBox,
                "Pay from",
                safeText(
                        subscription.getAccount(),
                        "Cash"
                )
        );

        addDetailRow(
                detailBox,
                "Reminder",
                getReminderText(
                        subscription.getRemindDays()
                )
        );

        content.addView(
                detailBox
        );

        String note =
                safeText(
                        subscription.getNote(),
                        ""
                );

        if (!note.isEmpty()) {
            TextView noteView =
                    createText(
                            "Note: " + note,
                            11,
                            getColorValue(
                                    R.color.app_text_secondary
                            ),
                            false
                    );

            noteView.setLineSpacing(
                    dp(2),
                    1f
            );

            noteView.setPadding(
                    dp(11),
                    dp(9),
                    dp(11),
                    dp(9)
            );

            noteView.setBackground(
                    createRoundedDrawable(
                            dueStyle.surfaceColor,
                            dueStyle.outlineColor,
                            12
                    )
            );

            LinearLayout.LayoutParams noteParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );

            noteParams.setMargins(
                    0,
                    dp(9),
                    0,
                    0
            );

            noteView.setLayoutParams(
                    noteParams
            );

            content.addView(
                    noteView
            );
        }

        MaterialButton btnMarkPaid =
                createFilledButton(
                        "Mark Paid",
                        getColorValue(
                                R.color.success
                        )
                );

        btnMarkPaid.setEnabled(
                subscription.isActive()
        );

        btnMarkPaid.setAlpha(
                subscription.isActive()
                        ? 1f
                        : 0.48f
        );

        LinearLayout.LayoutParams paidParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(49)
                );

        paidParams.setMargins(
                0,
                dp(13),
                0,
                0
        );

        btnMarkPaid.setLayoutParams(
                paidParams
        );

        content.addView(
                btnMarkPaid
        );

        LinearLayout secondaryActions =
                new LinearLayout(this);

        secondaryActions.setOrientation(
                LinearLayout.HORIZONTAL
        );

        LinearLayout.LayoutParams secondaryRowParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(48)
                );

        secondaryRowParams.setMargins(
                0,
                dp(8),
                0,
                0
        );

        secondaryActions.setLayoutParams(
                secondaryRowParams
        );

        MaterialButton btnPause =
                createFilledButton(
                        subscription.isActive()
                                ? "Pause"
                                : "Resume",
                        subscription.isActive()
                                ? getColorValue(
                                R.color.warning
                        )
                                : getColorValue(
                                R.color.secondary
                        )
                );

        MaterialButton btnDelete =
                createOutlinedButton(
                        "Delete",
                        getColorValue(
                                R.color.expense
                        )
                );

        LinearLayout.LayoutParams pauseParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        1f
                );

        pauseParams.setMargins(
                0,
                0,
                dp(4),
                0
        );

        btnPause.setLayoutParams(
                pauseParams
        );

        LinearLayout.LayoutParams deleteParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        1f
                );

        deleteParams.setMargins(
                dp(4),
                0,
                0,
                0
        );

        btnDelete.setLayoutParams(
                deleteParams
        );

        secondaryActions.addView(
                btnPause
        );

        secondaryActions.addView(
                btnDelete
        );

        content.addView(
                secondaryActions
        );

        btnMarkPaid.setOnClickListener(
                view -> confirmMarkAsPaid(
                        subscription
                )
        );

        btnPause.setOnClickListener(
                view -> toggleSubscription(
                        subscription
                )
        );

        btnDelete.setOnClickListener(
                view -> confirmDelete(
                        subscription
                )
        );

        BubbleTouchAnimator.apply(card);

        if (subscription.isActive()) {
            BubbleTouchAnimator.apply(
                    btnMarkPaid
            );
        }

        BubbleTouchAnimator.apply(
                btnPause
        );

        BubbleTouchAnimator.apply(
                btnDelete
        );

        card.addView(content);

        subscriptionContainer.addView(
                card
        );
    }

    private void confirmMarkAsPaid(
            Subscription subscription
    ) {
        if (actionInProgress) {
            showActionBusyMessage();
            return;
        }

        if (!subscription.isActive()) {
            Toast.makeText(
                    this,
                    "Resume this bill before recording payment",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        String nextDueDate =
                getNextFutureDueDate(
                        subscription.getNextDueDate(),
                        subscription.getBillingCycle()
                );

        if (nextDueDate == null) {
            Toast.makeText(
                    this,
                    "The next due date could not be calculated",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        String message =
                formatAmount(
                        subscription.getAmount()
                )
                        + " will be recorded as an expense from "
                        + safeText(
                        subscription.getAccount(),
                        "Cash"
                )
                        + ".\n\nNext due date: "
                        + formatVisibleDate(
                        nextDueDate
                );

        new AlertDialog.Builder(this)
                .setTitle(
                        "Mark Bill as Paid"
                )
                .setMessage(message)
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .setPositiveButton(
                        "Record Payment",
                        (dialog, which) ->
                                performMarkAsPaid(
                                        subscription,
                                        nextDueDate
                                )
                )
                .show();
    }

    private void performMarkAsPaid(
            Subscription subscription,
            String nextDueDate
    ) {
        if (actionInProgress) {
            return;
        }

        actionInProgress = true;

        Transaction transaction =
                new Transaction();

        transaction.setType("EXPENSE");
        transaction.setAmount(
                Math.abs(
                        subscription.getAmount()
                )
        );
        transaction.setCategory(
                safeText(
                        subscription.getCategory(),
                        "Subscriptions"
                )
        );
        transaction.setAccount(
                safeText(
                        subscription.getAccount(),
                        "Cash"
                )
        );
        transaction.setNote(
                "Subscription payment - "
                        + safeText(
                        subscription.getName(),
                        "Bill"
                )
        );
        transaction.setDate(
                new SimpleDateFormat(
                        "yyyy-MM-dd HH:mm",
                        Locale.US
                ).format(
                        Calendar.getInstance()
                                .getTime()
                )
        );

        String oldDueDate =
                subscription.getNextDueDate();

        subscription.setNextDueDate(
                nextDueDate
        );

        new Thread(() -> {
            try {
                AppDatabase database =
                        DatabaseClient
                                .getInstance(
                                        getApplicationContext()
                                )
                                .getAppDatabase();

                database.runInTransaction(() -> {
                    database
                            .transactionDao()
                            .insert(transaction);

                    database
                            .subscriptionDao()
                            .update(subscription);
                });

                runOnUiThread(() -> {
                    if (isUiUnavailable()) {
                        return;
                    }

                    actionInProgress = false;

                    scheduleRemindersSafely();

                    Toast.makeText(
                            SubscriptionActivity.this,
                            "Expense recorded and next due date updated",
                            Toast.LENGTH_SHORT
                    ).show();

                    loadSubscriptions();
                });

            } catch (Exception exception) {
                subscription.setNextDueDate(
                        oldDueDate
                );

                runOnUiThread(() -> {
                    if (isUiUnavailable()) {
                        return;
                    }

                    actionInProgress = false;

                    Toast.makeText(
                            SubscriptionActivity.this,
                            "Payment could not be recorded",
                            Toast.LENGTH_SHORT
                    ).show();

                    loadSubscriptions();
                });
            }
        }).start();
    }

    private void toggleSubscription(
            Subscription subscription
    ) {
        if (actionInProgress) {
            showActionBusyMessage();
            return;
        }

        actionInProgress = true;

        boolean previousState =
                subscription.isActive();

        boolean newState =
                !previousState;

        subscription.setActive(
                newState
        );

        new Thread(() -> {
            try {
                DatabaseClient
                        .getInstance(
                                getApplicationContext()
                        )
                        .getAppDatabase()
                        .subscriptionDao()
                        .update(subscription);

                runOnUiThread(() -> {
                    if (isUiUnavailable()) {
                        return;
                    }

                    actionInProgress = false;

                    scheduleRemindersSafely();

                    Toast.makeText(
                            SubscriptionActivity.this,
                            newState
                                    ? "Bill resumed"
                                    : "Bill paused",
                            Toast.LENGTH_SHORT
                    ).show();

                    loadSubscriptions();
                });

            } catch (Exception exception) {
                subscription.setActive(
                        previousState
                );

                runOnUiThread(() -> {
                    if (isUiUnavailable()) {
                        return;
                    }

                    actionInProgress = false;

                    Toast.makeText(
                            SubscriptionActivity.this,
                            "Bill status could not be changed",
                            Toast.LENGTH_SHORT
                    ).show();

                    loadSubscriptions();
                });
            }
        }).start();
    }

    private void confirmDelete(
            Subscription subscription
    ) {
        if (actionInProgress) {
            showActionBusyMessage();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(
                        "Delete Bill"
                )
                .setMessage(
                        "Delete \""
                                + safeText(
                                subscription.getName(),
                                "this bill"
                        )
                                + "\"?\n\nExisting expense transactions will not be deleted."
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .setPositiveButton(
                        "Delete",
                        (dialog, which) ->
                                performDelete(
                                        subscription
                                )
                )
                .show();
    }

    private void performDelete(
            Subscription subscription
    ) {
        if (actionInProgress) {
            return;
        }

        actionInProgress = true;

        new Thread(() -> {
            try {
                DatabaseClient
                        .getInstance(
                                getApplicationContext()
                        )
                        .getAppDatabase()
                        .subscriptionDao()
                        .delete(subscription);

                runOnUiThread(() -> {
                    if (isUiUnavailable()) {
                        return;
                    }

                    actionInProgress = false;

                    scheduleRemindersSafely();

                    Toast.makeText(
                            SubscriptionActivity.this,
                            "Bill deleted",
                            Toast.LENGTH_SHORT
                    ).show();

                    loadSubscriptions();
                });

            } catch (Exception exception) {
                runOnUiThread(() -> {
                    if (isUiUnavailable()) {
                        return;
                    }

                    actionInProgress = false;

                    Toast.makeText(
                            SubscriptionActivity.this,
                            "Bill could not be deleted",
                            Toast.LENGTH_SHORT
                    ).show();

                    loadSubscriptions();
                });
            }
        }).start();
    }

    private DueStyle getDueStyle(
            Subscription subscription
    ) {
        if (!subscription.isActive()) {
            return new DueStyle(
                    "Paused Bill",
                    "Ⅱ",
                    getColorValue(
                            R.color.app_text_secondary
                    ),
                    getColorValue(
                            R.color.app_surface_soft
                    ),
                    getColorValue(
                            R.color.app_outline_soft
                    )
            );
        }

        Integer days =
                getDaysUntilDue(
                        subscription.getNextDueDate()
                );

        if (days == null) {
            return new DueStyle(
                    "Due date unavailable",
                    "!",
                    getColorValue(
                            R.color.warning
                    ),
                    getColorValue(
                            R.color.warning_surface
                    ),
                    getColorValue(
                            R.color.warning_outline
                    )
            );
        }

        if (days < 0) {
            int overdueDays =
                    Math.abs(days);

            return new DueStyle(
                    "Overdue by "
                            + overdueDays
                            + (
                            overdueDays == 1
                                    ? " day"
                                    : " days"
                    ),
                    "!",
                    getColorValue(
                            R.color.expense
                    ),
                    getColorValue(
                            R.color.expense_surface
                    ),
                    getColorValue(
                            R.color.expense_outline
                    )
            );
        }

        if (days == 0) {
            return new DueStyle(
                    "Due Today",
                    "!",
                    getColorValue(
                            R.color.expense
                    ),
                    getColorValue(
                            R.color.expense_surface
                    ),
                    getColorValue(
                            R.color.expense_outline
                    )
            );
        }

        if (days <= Math.max(
                subscription.getRemindDays(),
                0
        )) {
            return new DueStyle(
                    "Due in "
                            + days
                            + (
                            days == 1
                                    ? " day"
                                    : " days"
                    ),
                    "⏰",
                    getColorValue(
                            R.color.warning
                    ),
                    getColorValue(
                            R.color.warning_surface
                    ),
                    getColorValue(
                            R.color.warning_outline
                    )
            );
        }

        return new DueStyle(
                "Due in "
                        + days
                        + (
                        days == 1
                                ? " day"
                                : " days"
                ),
                "↻",
                getColorValue(
                        R.color.success
                ),
                getColorValue(
                        R.color.success_surface
                ),
                getColorValue(
                        R.color.success_outline
                )
        );
    }

    private Integer getDaysUntilDue(
            String dueDate
    ) {
        Date parsedDate =
                parseStoredDate(
                        dueDate
                );

        if (parsedDate == null) {
            return null;
        }

        Calendar today =
                Calendar.getInstance();

        clearTime(today);

        Calendar due =
                Calendar.getInstance();

        due.setTime(parsedDate);
        clearTime(due);

        long difference =
                due.getTimeInMillis()
                        - today.getTimeInMillis();

        return (int) Math.round(
                difference
                        / 86400000d
        );
    }

    private String getNextFutureDueDate(
            String currentDueDate,
            String billingCycle
    ) {
        Date parsedDate =
                parseStoredDate(
                        currentDueDate
                );

        if (parsedDate == null) {
            return null;
        }

        Calendar dueCalendar =
                Calendar.getInstance();

        dueCalendar.setTime(
                parsedDate
        );

        clearTime(
                dueCalendar
        );

        Calendar today =
                Calendar.getInstance();

        clearTime(today);

        /*
         * Always advance at least one billing cycle.
         * This also handles bills paid before their due date.
         */
        advanceBillingCycle(
                dueCalendar,
                billingCycle
        );

        int safetyCounter = 0;

        while (!dueCalendar.after(today)
                && safetyCounter < 240) {

            advanceBillingCycle(
                    dueCalendar,
                    billingCycle
            );

            safetyCounter++;
        }

        if (!dueCalendar.after(today)) {
            return null;
        }

        return new SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.US
        ).format(
                dueCalendar.getTime()
        );
    }

    private void advanceBillingCycle(
            Calendar calendar,
            String billingCycle
    ) {
        if ("Weekly".equalsIgnoreCase(
                billingCycle
        )) {
            calendar.add(
                    Calendar.DAY_OF_MONTH,
                    7
            );

        } else if ("Yearly".equalsIgnoreCase(
                billingCycle
        )) {
            calendar.add(
                    Calendar.YEAR,
                    1
            );

        } else {
            calendar.add(
                    Calendar.MONTH,
                    1
            );
        }
    }

    private Date parseStoredDate(
            String dateText
    ) {
        if (dateText == null
                || dateText.trim().isEmpty()) {

            return null;
        }

        String cleanDate =
                dateText.trim();

        String[] patterns = {
                "yyyy-MM-dd",
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd HH:mm:ss",
                "dd/MM/yyyy",
                "dd-MM-yyyy",
                "dd MMM yyyy",
                "dd MMMM yyyy"
        };

        for (String pattern : patterns) {
            try {
                SimpleDateFormat formatter =
                        new SimpleDateFormat(
                                pattern,
                                Locale.ENGLISH
                        );

                formatter.setLenient(false);

                ParsePosition parsePosition =
                        new ParsePosition(0);

                Date parsedDate =
                        formatter.parse(
                                cleanDate,
                                parsePosition
                        );

                if (parsedDate != null
                        && parsePosition.getIndex()
                        == cleanDate.length()) {

                    return parsedDate;
                }

            } catch (Exception ignored) {
                // Try the next supported date format.
            }
        }

        return null;
    }

    private String formatVisibleDate(
            String storedDate
    ) {
        Date date =
                parseStoredDate(
                        storedDate
                );

        if (date == null) {
            return safeText(
                    storedDate,
                    "Date unavailable"
            );
        }

        return new SimpleDateFormat(
                "dd MMMM yyyy",
                Locale.ENGLISH
        ).format(date);
    }

    private int getReminderDays(
            String reminderOption
    ) {
        if ("1 Day Before".equalsIgnoreCase(
                reminderOption
        )) {
            return 1;
        }

        if ("3 Days Before".equalsIgnoreCase(
                reminderOption
        )) {
            return 3;
        }

        if ("7 Days Before".equalsIgnoreCase(
                reminderOption
        )) {
            return 7;
        }

        return 0;
    }

    private String getReminderText(
            int reminderDays
    ) {
        if (reminderDays <= 0) {
            return "On due date";
        }

        return reminderDays
                + (
                reminderDays == 1
                        ? " day before"
                        : " days before"
        );
    }

    private boolean isSupportedBillingCycle(
            String value
    ) {
        for (String billingCycle : billingCycles) {
            if (billingCycle.equalsIgnoreCase(
                    value
            )) {
                return true;
            }
        }

        return false;
    }

    private boolean isSupportedReminder(
            String value
    ) {
        for (String reminderOption : reminderOptions) {
            if (reminderOption.equalsIgnoreCase(
                    value
            )) {
                return true;
            }
        }

        return false;
    }

    private Double parseAmount(
            String amountText
    ) {
        if (amountText == null
                || amountText.trim().isEmpty()) {

            return null;
        }

        String cleanAmount =
                amountText
                        .trim()
                        .replace(",", "")
                        .replace("₹", "")
                        .replace(" ", "");

        try {
            return Double.parseDouble(
                    cleanAmount
            );

        } catch (Exception exception) {
            return null;
        }
    }

    private String formatAmount(
            double amount
    ) {
        NumberFormat numberFormat =
                NumberFormat.getCurrencyInstance(
                        new Locale(
                                "en",
                                "IN"
                        )
                );

        numberFormat.setMinimumFractionDigits(2);
        numberFormat.setMaximumFractionDigits(2);

        return numberFormat.format(
                Math.abs(amount)
        );
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT
                >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.POST_NOTIFICATIONS
                    },
                    NOTIFICATION_PERMISSION_REQUEST
            );
        }
    }

    private void scheduleRemindersSafely() {
        try {
            ReminderScheduler.scheduleDaily(
                    getApplicationContext()
            );

        } catch (Exception ignored) {
            // A scheduling error should not crash the screen.
        }
    }

    private MaterialButton createFilledButton(
            String text,
            int backgroundColor
    ) {
        MaterialButton button =
                new MaterialButton(this);

        button.setText(text);
        button.setTextSize(12);
        button.setTextColor(
                getColorValue(
                        R.color.white
                )
        );
        button.setAllCaps(false);
        button.setCornerRadius(
                dp(15)
        );
        button.setBackgroundTintList(
                ColorStateList.valueOf(
                        backgroundColor
                )
        );
        button.setMinimumHeight(0);
        button.setMinHeight(0);

        return button;
    }

    private MaterialButton createOutlinedButton(
            String text,
            int accentColor
    ) {
        MaterialButton button =
                new MaterialButton(this);

        button.setText(text);
        button.setTextSize(12);
        button.setTextColor(
                accentColor
        );
        button.setAllCaps(false);
        button.setCornerRadius(
                dp(15)
        );
        button.setBackgroundTintList(
                ColorStateList.valueOf(
                        getColorValue(
                                R.color.app_surface
                        )
                )
        );
        button.setStrokeWidth(
                dp(1)
        );
        button.setStrokeColor(
                ColorStateList.valueOf(
                        accentColor
                )
        );
        button.setMinimumHeight(0);
        button.setMinHeight(0);

        return button;
    }

    private TextView createIcon(
            String symbol,
            int textColor,
            int backgroundColor,
            int outlineColor
    ) {
        TextView icon =
                createText(
                        symbol,
                        symbol.length() > 1
                                ? 13
                                : 19,
                        textColor,
                        true
                );

        icon.setGravity(
                Gravity.CENTER
        );

        icon.setBackground(
                createRoundedDrawable(
                        backgroundColor,
                        outlineColor,
                        14
                )
        );

        icon.setLayoutParams(
                new LinearLayout.LayoutParams(
                        dp(46),
                        dp(46)
                )
        );

        return icon;
    }

    private TextView createAmountBadge(
            String amount,
            int textColor,
            int backgroundColor,
            int outlineColor
    ) {
        TextView badge =
                createText(
                        amount,
                        12,
                        textColor,
                        true
                );

        badge.setGravity(
                Gravity.CENTER
        );

        badge.setMaxLines(1);

        badge.setPadding(
                dp(9),
                0,
                dp(9),
                0
        );

        badge.setBackground(
                createRoundedDrawable(
                        backgroundColor,
                        outlineColor,
                        12
                )
        );

        badge.setLayoutParams(
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        dp(34)
                )
        );

        return badge;
    }

    private TextView createStatusBadge(
            String statusText,
            int textColor,
            int backgroundColor,
            int outlineColor
    ) {
        TextView badge =
                createText(
                        statusText,
                        11,
                        textColor,
                        true
                );

        badge.setGravity(
                Gravity.CENTER
        );

        badge.setPadding(
                dp(11),
                0,
                dp(11),
                0
        );

        badge.setBackground(
                createRoundedDrawable(
                        backgroundColor,
                        outlineColor,
                        11
                )
        );

        return badge;
    }

    private void addDetailRow(
            LinearLayout parent,
            String label,
            String value
    ) {
        LinearLayout row =
                new LinearLayout(this);

        row.setOrientation(
                LinearLayout.HORIZONTAL
        );

        row.setGravity(
                Gravity.TOP
        );

        LinearLayout.LayoutParams rowParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        if (parent.getChildCount() > 0) {
            rowParams.setMargins(
                    0,
                    dp(7),
                    0,
                    0
            );
        }

        row.setLayoutParams(
                rowParams
        );

        TextView labelView =
                createText(
                        label,
                        10,
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        false
                );

        labelView.setLayoutParams(
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        0.9f
                )
        );

        TextView valueView =
                createText(
                        safeText(
                                value,
                                "Not available"
                        ),
                        10,
                        getColorValue(
                                R.color.app_text_primary
                        ),
                        true
                );

        valueView.setGravity(
                Gravity.END
        );

        valueView.setLayoutParams(
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1.3f
                )
        );

        row.addView(labelView);
        row.addView(valueView);

        parent.addView(row);
    }

    private void addContainerStatusCard(
            String title,
            String description,
            String symbol,
            StatusTone tone
    ) {
        DueStyle style =
                getStatusStyle(
                        title,
                        symbol,
                        tone
                );

        MaterialCardView card =
                new MaterialCardView(this);

        card.setCardBackgroundColor(
                style.surfaceColor
        );

        card.setRadius(
                dp(17)
        );

        card.setCardElevation(0);

        card.setStrokeWidth(
                dp(1)
        );

        card.setStrokeColor(
                style.outlineColor
        );

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        cardParams.setMargins(
                0,
                dp(4),
                0,
                dp(6)
        );

        card.setLayoutParams(
                cardParams
        );

        LinearLayout content =
                new LinearLayout(this);

        content.setOrientation(
                LinearLayout.HORIZONTAL
        );

        content.setGravity(
                Gravity.CENTER_VERTICAL
        );

        content.setPadding(
                dp(13),
                dp(13),
                dp(13),
                dp(13)
        );

        content.addView(
                createIcon(
                        symbol,
                        style.accentColor,
                        getColorValue(
                                R.color.app_surface
                        ),
                        style.outlineColor
                )
        );

        LinearLayout textContainer =
                new LinearLayout(this);

        textContainer.setOrientation(
                LinearLayout.VERTICAL
        );

        LinearLayout.LayoutParams textParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        textParams.setMargins(
                dp(11),
                0,
                0,
                0
        );

        textContainer.setLayoutParams(
                textParams
        );

        TextView titleView =
                createText(
                        title,
                        13,
                        style.accentColor,
                        true
                );

        TextView descriptionView =
                createText(
                        description,
                        10,
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        false
                );

        descriptionView.setLineSpacing(
                dp(2),
                1f
        );

        LinearLayout.LayoutParams descriptionParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        descriptionParams.setMargins(
                0,
                dp(4),
                0,
                0
        );

        descriptionView.setLayoutParams(
                descriptionParams
        );

        textContainer.addView(
                titleView
        );

        textContainer.addView(
                descriptionView
        );

        content.addView(
                textContainer
        );

        card.addView(content);

        subscriptionContainer.addView(
                card
        );
    }

    private DueStyle getStatusStyle(
            String title,
            String symbol,
            StatusTone tone
    ) {
        if (tone == StatusTone.EXPENSE) {
            return new DueStyle(
                    title,
                    symbol,
                    getColorValue(
                            R.color.expense
                    ),
                    getColorValue(
                            R.color.expense_surface
                    ),
                    getColorValue(
                            R.color.expense_outline
                    )
            );
        }

        return new DueStyle(
                title,
                symbol,
                getColorValue(
                        R.color.purple
                ),
                getColorValue(
                        R.color.purple_surface
                ),
                getColorValue(
                        R.color.purple_outline
                )
        );
    }

    private TextView createText(
            String text,
            float textSize,
            int textColor,
            boolean bold
    ) {
        TextView textView =
                new TextView(this);

        textView.setText(text);
        textView.setTextSize(textSize);
        textView.setTextColor(textColor);

        if (bold) {
            textView.setTypeface(
                    Typeface.DEFAULT,
                    Typeface.BOLD
            );
        }

        return textView;
    }

    private GradientDrawable createRoundedDrawable(
            int backgroundColor,
            int outlineColor,
            int radiusDp
    ) {
        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(
                backgroundColor
        );

        drawable.setStroke(
                dp(1),
                outlineColor
        );

        drawable.setCornerRadius(
                dp(radiusDp)
        );

        return drawable;
    }

    private String getInputText(
            TextInputEditText editText
    ) {
        if (editText == null
                || editText.getText() == null) {

            return "";
        }

        return editText
                .getText()
                .toString()
                .trim();
    }

    private boolean containsIgnoreCase(
            List<String> values,
            String target
    ) {
        return !findMatchingValue(
                values,
                target
        ).isEmpty();
    }

    private String findMatchingValue(
            List<String> values,
            String target
    ) {
        if (values == null
                || target == null
                || target.trim().isEmpty()) {

            return "";
        }

        for (String value : values) {
            if (value != null
                    && value.equalsIgnoreCase(
                    target.trim()
            )) {
                return value;
            }
        }

        return "";
    }

    private void showActionBusyMessage() {
        Toast.makeText(
                this,
                "Please wait for the current bill action to finish",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void clearTime(
            Calendar calendar
    ) {
        calendar.set(
                Calendar.HOUR_OF_DAY,
                0
        );

        calendar.set(
                Calendar.MINUTE,
                0
        );

        calendar.set(
                Calendar.SECOND,
                0
        );

        calendar.set(
                Calendar.MILLISECOND,
                0
        );
    }

    private String safeText(
            String value,
            String fallback
    ) {
        if (value == null
                || value.trim().isEmpty()) {

            return fallback;
        }

        return value.trim();
    }

    private boolean isUiUnavailable() {
        return isFinishing()
                || isDestroyed();
    }

    private int getColorValue(
            int colorResource
    ) {
        return ContextCompat.getColor(
                this,
                colorResource
        );
    }

    private int dp(
            int value
    ) {
        return Math.round(
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    private enum StatusTone {
        PURPLE,
        EXPENSE
    }

    private static class DueStyle {

        private final String statusText;
        private final String symbol;

        private final int accentColor;
        private final int surfaceColor;
        private final int outlineColor;

        private DueStyle(
                String statusText,
                String symbol,
                int accentColor,
                int surfaceColor,
                int outlineColor
        ) {
            this.statusText =
                    statusText;

            this.symbol =
                    symbol;

            this.accentColor =
                    accentColor;

            this.surfaceColor =
                    surfaceColor;

            this.outlineColor =
                    outlineColor;
        }
    }
}