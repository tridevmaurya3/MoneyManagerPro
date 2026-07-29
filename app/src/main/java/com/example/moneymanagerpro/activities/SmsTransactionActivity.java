package com.example.moneymanagerpro.activities;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Account;
import com.example.moneymanagerpro.model.Category;
import com.example.moneymanagerpro.utils.SmsImportStore;
import com.example.moneymanagerpro.utils.SmsTransactionProcessor;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SmsTransactionActivity
        extends AppCompatActivity {

    private MaterialSwitch switchSmsSync;
    private MaterialSwitch switchSmsAutoAdd;
    private TextView txtPermissionStatus;
    private TextView txtQueueCount;
    private TextView txtDetails;
    private MaterialCardView reviewCard;
    private MaterialAutoCompleteTextView dropdownType;
    private MaterialAutoCompleteTextView dropdownCategory;
    private MaterialAutoCompleteTextView dropdownAccount;
    private MaterialButton btnScan;

    private final List<Category> categories =
            new ArrayList<>();
    private final List<Account> accounts =
            new ArrayList<>();
    private List<SmsImportStore.PendingTransaction>
            pendingTransactions =
            new ArrayList<>();
    private SmsImportStore.PendingTransaction current;
    private boolean bindingSwitches;

    private final ActivityResultLauncher<String[]>
            permissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts
                            .RequestMultiplePermissions(),
                    result -> {
                        boolean granted =
                                hasSmsPermissions();

                        SmsImportStore.setEnabled(
                                this,
                                granted
                        );
                        bindSwitchState();

                        if (!granted) {
                            Toast.makeText(
                                    this,
                                    "SMS permission is required for transaction sync",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(
                R.layout.activity_sms_transaction
        );

        bindViews();
        setupActions();
        loadOptionsAndQueue();
        bindSwitchState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadQueue();
        bindSwitchState();
    }

    private void bindViews() {
        findViewById(R.id.btnSmsBack)
                .setOnClickListener(view -> finish());
        switchSmsSync =
                findViewById(R.id.switchSmsSync);
        switchSmsAutoAdd =
                findViewById(R.id.switchSmsAutoAdd);
        txtPermissionStatus =
                findViewById(
                        R.id.txtSmsPermissionStatus
                );
        txtQueueCount =
                findViewById(R.id.txtSmsQueueCount);
        txtDetails =
                findViewById(
                        R.id.txtSmsDetectedDetails
                );
        reviewCard =
                findViewById(R.id.smsReviewCard);
        dropdownType =
                findViewById(R.id.dropdownSmsType);
        dropdownCategory =
                findViewById(
                        R.id.dropdownSmsCategory
                );
        dropdownAccount =
                findViewById(R.id.dropdownSmsAccount);
        btnScan =
                findViewById(R.id.btnScanRecentSms);
    }

    private void setupActions() {
        switchSmsSync.setOnCheckedChangeListener(
                (button, checked) -> {
                    if (bindingSwitches) {
                        return;
                    }

                    if (checked
                            && !hasSmsPermissions()) {
                        requestSmsPermissions();
                        return;
                    }

                    SmsImportStore.setEnabled(
                            this,
                            checked
                    );
                    bindSwitchState();
                }
        );

        switchSmsAutoAdd
                .setOnCheckedChangeListener(
                        (button, checked) -> {
                            if (bindingSwitches) {
                                return;
                            }

                            SmsImportStore
                                    .setAutoAddEnabled(
                                            this,
                                            checked
                                    );
                        }
                );

        btnScan.setOnClickListener(
                view -> scanRecentSms()
        );

        dropdownType.setOnItemClickListener(
                (parent, view, position, id) ->
                        updateCategoryOptions()
        );

        findViewById(R.id.btnSaveSmsTransaction)
                .setOnClickListener(
                        view -> saveCurrent()
                );

        findViewById(
                R.id.btnIgnoreSmsTransaction
        ).setOnClickListener(
                view -> ignoreCurrent()
        );
    }

    private void bindSwitchState() {
        bindingSwitches = true;

        boolean permissions =
                hasSmsPermissions();
        boolean enabled =
                permissions
                        && SmsImportStore.isEnabled(
                        this
                );

        switchSmsSync.setChecked(enabled);
        switchSmsAutoAdd.setChecked(
                SmsImportStore.isAutoAddEnabled(
                        this
                )
        );
        switchSmsAutoAdd.setEnabled(enabled);
        btnScan.setEnabled(
                ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_SMS
                ) == PackageManager.PERMISSION_GRANTED
        );

        txtPermissionStatus.setText(
                permissions
                        ? enabled
                        ? "SMS sync is active"
                        : "SMS permission granted; sync is off"
                        : "SMS access is disabled"
        );

        bindingSwitches = false;
    }

    private boolean hasSmsPermissions() {
        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestSmsPermissions() {
        List<String> permissions =
                new ArrayList<>();
        permissions.add(
                Manifest.permission.RECEIVE_SMS
        );
        permissions.add(
                Manifest.permission.READ_SMS
        );

        if (Build.VERSION.SDK_INT >= 33) {
            permissions.add(
                    Manifest.permission
                            .POST_NOTIFICATIONS
            );
        }

        permissionLauncher.launch(
                permissions.toArray(
                        new String[0]
                )
        );
    }

    private void loadOptionsAndQueue() {
        new Thread(() -> {
            List<Category> loadedCategories =
                    DatabaseClient
                            .getInstance(
                                    getApplicationContext()
                            )
                            .getAppDatabase()
                            .categoryDao()
                            .getAllCategories();
            List<Account> loadedAccounts =
                    DatabaseClient
                            .getInstance(
                                    getApplicationContext()
                            )
                            .getAppDatabase()
                            .accountDao()
                            .getAllAccounts();

            runOnUiThread(() -> {
                categories.clear();
                categories.addAll(loadedCategories);
                accounts.clear();
                accounts.addAll(loadedAccounts);
                loadQueue();
            });
        }).start();
    }

    private void loadQueue() {
        pendingTransactions =
                SmsImportStore.getPending(this);
        current = pendingTransactions.isEmpty()
                ? null
                : pendingTransactions.get(0);

        txtQueueCount.setText(
                pendingTransactions.isEmpty()
                        ? "No transaction is waiting for review"
                        : pendingTransactions.size()
                        + " transaction(s) waiting for review"
        );
        reviewCard.setVisibility(
                current == null
                        ? View.GONE
                        : View.VISIBLE
        );

        if (current != null) {
            bindCurrent();
        }
    }

    private void bindCurrent() {
        txtDetails.setText(
                ("INCOME".equals(current.type)
                        ? "Income"
                        : "Expense")
                        + ": ₹"
                        + String.format(
                        Locale.US,
                        "%.2f",
                        current.amount
                )
                        + "\nSender: "
                        + value(current.sender)
                        + "\nBank: "
                        + value(current.bank)
                        + "\nMerchant: "
                        + value(current.merchant)
                        + "\nReference: "
                        + value(current.reference)
                        + "\nConfidence: "
                        + current.confidence
                        + "%"
        );

        setDropdown(
                dropdownType,
                new String[]{
                        "Expense",
                        "Income"
                },
                "INCOME".equals(current.type)
                        ? "Income"
                        : "Expense"
        );
        updateCategoryOptions();

        List<String> accountNames =
                new ArrayList<>();
        for (Account account : accounts) {
            accountNames.add(account.getName());
        }

        String matchedAccount =
                SmsTransactionProcessor
                        .findMatchingAccount(
                                accounts,
                                current.bank,
                                current.sender
                        );
        setDropdown(
                dropdownAccount,
                accountNames.toArray(
                        new String[0]
                ),
                matchedAccount
        );
    }

    private void updateCategoryOptions() {
        boolean income =
                "Income".equalsIgnoreCase(
                        dropdownType.getText()
                                .toString()
                );
        List<String> names = new ArrayList<>();

        for (Category category : categories) {
            if ((income ? "Income" : "Expense")
                    .equalsIgnoreCase(
                            category.getType()
                    )) {
                names.add(category.getName());
            }
        }

        setDropdown(
                dropdownCategory,
                names.toArray(new String[0]),
                current == null
                        ? ""
                        : current.category
        );
    }

    private void setDropdown(
            MaterialAutoCompleteTextView dropdown,
            String[] values,
            String preferred
    ) {
        dropdown.setAdapter(
                new ArrayAdapter<>(
                        this,
                        android.R.layout
                                .simple_list_item_1,
                        values
                )
        );

        String selected =
                values.length == 0
                        ? ""
                        : values[0];

        for (String value : values) {
            if (value.equalsIgnoreCase(
                    preferred
            )) {
                selected = value;
                break;
            }
        }

        dropdown.setText(selected, false);
    }

    private void saveCurrent() {
        if (current == null) {
            return;
        }

        String category =
                dropdownCategory.getText()
                        .toString()
                        .trim();
        String account =
                dropdownAccount.getText()
                        .toString()
                        .trim();

        if (category.isEmpty()
                || account.isEmpty()) {
            Toast.makeText(
                    this,
                    "Select a category and account",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        current.type =
                "Income".equalsIgnoreCase(
                        dropdownType.getText()
                                .toString()
                )
                        ? "INCOME"
                        : "EXPENSE";

        new Thread(() -> {
            boolean saved =
                    SmsTransactionProcessor
                            .savePending(
                                    getApplicationContext(),
                                    current,
                                    category,
                                    account
                            );

            runOnUiThread(() -> {
                Toast.makeText(
                        this,
                        saved
                                ? "SMS transaction saved"
                                : "Transaction could not be saved",
                        Toast.LENGTH_SHORT
                ).show();

                if (saved) {
                    loadQueue();
                }
            });
        }).start();
    }

    private void ignoreCurrent() {
        if (current == null) {
            return;
        }

        SmsImportStore.markProcessed(
                this,
                current.fingerprint
        );
        SmsImportStore.removePending(
                this,
                current.fingerprint
        );
        loadQueue();
    }

    private void scanRecentSms() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_SMS
        ) != PackageManager.PERMISSION_GRANTED) {
            requestSmsPermissions();
            return;
        }

        btnScan.setEnabled(false);
        btnScan.setText("Scanning...");

        new Thread(() -> {
            int scanned = 0;

            try (Cursor cursor =
                         getContentResolver().query(
                                 Telephony.Sms.Inbox
                                         .CONTENT_URI,
                                 new String[]{
                                         Telephony.Sms
                                                 .ADDRESS,
                                         Telephony.Sms.BODY,
                                         Telephony.Sms.DATE
                                 },
                                 null,
                                 null,
                                 Telephony.Sms.DATE
                                         + " DESC"
                         )) {
                if (cursor != null) {
                    int addressIndex =
                            cursor.getColumnIndex(
                                    Telephony.Sms.ADDRESS
                            );
                    int bodyIndex =
                            cursor.getColumnIndex(
                                    Telephony.Sms.BODY
                            );
                    int dateIndex =
                            cursor.getColumnIndex(
                                    Telephony.Sms.DATE
                            );

                    while (cursor.moveToNext()
                            && scanned < 100) {
                        SmsTransactionProcessor
                                .processAsync(
                                        this,
                                        cursor.getString(
                                                addressIndex
                                        ),
                                        cursor.getString(
                                                bodyIndex
                                        ),
                                        cursor.getLong(
                                                dateIndex
                                        )
                                );
                        scanned++;
                    }
                }
            } catch (Exception ignored) {
            }

            int finalScanned = scanned;

            runOnUiThread(() -> {
                btnScan.postDelayed(
                        () -> {
                            btnScan.setText(
                                    "Scan Recent Bank SMS"
                            );
                            btnScan.setEnabled(true);
                            loadQueue();
                            Toast.makeText(
                                    this,
                                    finalScanned
                                            + " recent SMS checked",
                                    Toast.LENGTH_SHORT
                            ).show();
                        },
                        1200
                );
            });
        }).start();
    }

    private String value(String value) {
        return value == null
                || value.trim().isEmpty()
                ? "Not detected"
                : value.trim();
    }
}
