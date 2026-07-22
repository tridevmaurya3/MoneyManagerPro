package com.example.moneymanagerpro.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.database.AppDatabase;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Account;
import com.example.moneymanagerpro.model.Budget;
import com.example.moneymanagerpro.model.Category;
import com.example.moneymanagerpro.model.Goal;
import com.example.moneymanagerpro.model.Loan;
import com.example.moneymanagerpro.model.RecurringTransaction;
import com.example.moneymanagerpro.model.Transaction;
import com.example.moneymanagerpro.utils.BackupStorageManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BackupActivity extends AppCompatActivity {

    private static final String TAG = "BackupActivity";

    private static final int REQUEST_PICK_BACKUP_FOLDER = 401;

    private static final int PENDING_ACTION_NONE = 0;
    private static final int PENDING_ACTION_CREATE_BACKUP = 1;
    private static final int PENDING_ACTION_RESTORE_BACKUP = 2;

    private static final String STATE_PENDING_ACTION =
            "backup_pending_action";

    private TextView txtBackupStatus;
    private Button btnCreateBackup;
    private Button btnRestoreBackup;

    private BackupStorageManager backupStorageManager;

    private int pendingFolderAction = PENDING_ACTION_NONE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_backup);

        txtBackupStatus = findViewById(R.id.txtBackupStatus);
        btnCreateBackup = findViewById(R.id.btnCreateBackup);
        btnRestoreBackup = findViewById(R.id.btnRestoreBackup);

        backupStorageManager =
                new BackupStorageManager(getApplicationContext());

        if (savedInstanceState != null) {
            pendingFolderAction = savedInstanceState.getInt(
                    STATE_PENDING_ACTION,
                    PENDING_ACTION_NONE
            );
        }

        btnCreateBackup.setOnClickListener(
                view -> startCreateBackupFlow()
        );

        btnRestoreBackup.setOnClickListener(
                view -> startRestoreBackupFlow()
        );

        updateBackupStatus();
    }

    @Override
    protected void onSaveInstanceState(
            Bundle outState
    ) {
        outState.putInt(
                STATE_PENDING_ACTION,
                pendingFolderAction
        );

        super.onSaveInstanceState(outState);
    }

    private void startCreateBackupFlow() {
        if (!backupStorageManager.hasUsableBackupFolder()) {
            requestBackupFolder(
                    PENDING_ACTION_CREATE_BACKUP
            );

            return;
        }

        createBackup();
    }

    private void startRestoreBackupFlow() {
        if (!backupStorageManager.hasUsableBackupFolder()) {
            requestBackupFolder(
                    PENDING_ACTION_RESTORE_BACKUP
            );

            return;
        }

        checkBackupAndConfirmRestore();
    }

    private void requestBackupFolder(
            int requestedAction
    ) {
        if (backupStorageManager.getSavedTreeUri() != null
                && !backupStorageManager.isSavedPermissionValid()) {

            backupStorageManager.clearSavedFolder();
        }

        pendingFolderAction = requestedAction;

        String title;
        String message;

        if (requestedAction ==
                PENDING_ACTION_RESTORE_BACKUP) {

            title = "Backup Folder चुनें";

            message =
                    "Backup restore करने के लिए उस folder को चुनें " +
                            "जहाँ पुराना MoneyManagerPro backup मौजूद है।\n\n" +
                            "सही विकल्प:\n" +
                            "• Documents folder\n" +
                            "या\n" +
                            "• पहले से मौजूद MoneyManagerPro folder\n\n" +
                            "Backup नाम वाला अंदर का subfolder सीधे न चुनें।\n\n" +
                            "यह चयन केवल एक बार करना होगा।";

        } else {
            title = "Backup Folder चुनें";

            message =
                    "पहली बार backup location चुनना आवश्यक है।\n\n" +
                            "Documents folder चुनना सबसे अच्छा रहेगा। " +
                            "App उसके अंदर स्वयं यह folder बनाएगा:\n\n" +
                            "MoneyManagerPro/Backup\n\n" +
                            "यह चयन केवल एक बार करना होगा। " +
                            "इसके बाद backup location दोबारा नहीं पूछी जाएगी।";
        }

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(
                        "Choose Folder",
                        (dialog, which) ->
                                openBackupFolderPicker()
                )
                .setNegativeButton(
                        "Cancel",
                        (dialog, which) ->
                                pendingFolderAction =
                                        PENDING_ACTION_NONE
                )
                .show();
    }

    private void openBackupFolderPicker() {
        try {
            Intent folderPickerIntent =
                    backupStorageManager
                            .createFolderPickerIntent();

            startActivityForResult(
                    folderPickerIntent,
                    REQUEST_PICK_BACKUP_FOLDER
            );

        } catch (Exception exception) {
            Log.e(
                    TAG,
                    "Unable to open folder picker",
                    exception
            );

            pendingFolderAction =
                    PENDING_ACTION_NONE;

            Toast.makeText(
                    this,
                    "Folder picker नहीं खुल सका",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            @Nullable Intent data
    ) {
        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode !=
                REQUEST_PICK_BACKUP_FOLDER) {
            return;
        }

        if (resultCode != RESULT_OK
                || data == null
                || data.getData() == null) {

            pendingFolderAction =
                    PENDING_ACTION_NONE;

            txtBackupStatus.setText(
                    "Backup folder नहीं चुना गया।"
            );

            return;
        }

        Uri selectedFolderUri =
                data.getData();

        int returnedFlags =
                data.getFlags();

        try {
            backupStorageManager.saveSelectedFolder(
                    selectedFolderUri,
                    returnedFlags
            );

            txtBackupStatus.setText(
                    "Backup folder तैयार है\n" +
                            backupStorageManager
                                    .getBackupLocationLabel()
            );

            int actionToContinue =
                    pendingFolderAction;

            pendingFolderAction =
                    PENDING_ACTION_NONE;

            if (actionToContinue ==
                    PENDING_ACTION_CREATE_BACKUP) {

                createBackup();

            } else if (actionToContinue ==
                    PENDING_ACTION_RESTORE_BACKUP) {

                checkBackupAndConfirmRestore();

            } else {
                updateBackupStatus();
            }

        } catch (Exception exception) {
            Log.e(
                    TAG,
                    "Unable to save backup folder",
                    exception
            );

            pendingFolderAction =
                    PENDING_ACTION_NONE;

            txtBackupStatus.setText(
                    "चुना गया folder इस्तेमाल नहीं किया जा सका।"
            );

            new AlertDialog.Builder(this)
                    .setTitle("Folder Access Failed")
                    .setMessage(
                            "चुने गए folder की permanent read/write " +
                                    "permission save नहीं हो सकी।\n\n" +
                                    "Documents folder चुनकर दोबारा कोशिश करें।"
                    )
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private void createBackup() {
        setBackupButtonsEnabled(false);

        txtBackupStatus.setText(
                "नया backup बनाया जा रहा है..."
        );

        new Thread(() -> {
            try {
                JSONObject backupData =
                        buildBackupJson();

                Uri temporaryBackupUri =
                        backupStorageManager
                                .createTemporaryBackupUri();

                writeBackupJson(
                        temporaryBackupUri,
                        backupData
                );

                JSONObject verificationData =
                        readBackupJson(
                                temporaryBackupUri
                        );

                validateBackupFile(
                        verificationData
                );

                Uri latestBackupUri =
                        backupStorageManager
                                .commitTemporaryBackup(
                                        temporaryBackupUri
                                );

                long backupSize =
                        backupStorageManager
                                .getDocumentSize(
                                        latestBackupUri
                                );

                String createdAt =
                        getBackupDateTime(
                                backupData,
                                latestBackupUri
                        );

                String status =
                        "Latest backup सफलतापूर्वक बन गया\n" +
                                createdAt +
                                "\n" +
                                formatFileSize(backupSize) +
                                "\n" +
                                backupStorageManager
                                        .getBackupLocationLabel();

                runOnUiThread(() -> {
                    setBackupButtonsEnabled(true);

                    txtBackupStatus.setText(status);

                    Toast.makeText(
                            BackupActivity.this,
                            "पुराना backup replace करके नया backup बन गया",
                            Toast.LENGTH_LONG
                    ).show();
                });

            } catch (Exception exception) {
                Log.e(
                        TAG,
                        "Backup creation failed",
                        exception
                );

                runOnUiThread(() -> {
                    setBackupButtonsEnabled(true);

                    txtBackupStatus.setText(
                            "Backup नहीं बन सका। फिर से कोशिश करें।"
                    );

                    new AlertDialog.Builder(this)
                            .setTitle("Backup Failed")
                            .setMessage(
                                    "Backup बनाते समय समस्या आई।\n\n" +
                                            "कृपया storage उपलब्ध होने और " +
                                            "folder permission सही होने की जाँच करें।"
                            )
                            .setPositiveButton("OK", null)
                            .show();
                });
            }
        }).start();
    }

    private void checkBackupAndConfirmRestore() {
        setBackupButtonsEnabled(false);

        txtBackupStatus.setText(
                "Backup की जानकारी जाँची जा रही है..."
        );

        new Thread(() -> {
            try {
                Uri latestBackupUri =
                        backupStorageManager
                                .findLatestBackupUri();

                if (latestBackupUri == null) {
                    runOnUiThread(() -> {
                        setBackupButtonsEnabled(true);

                        txtBackupStatus.setText(
                                "चुने हुए folder में कोई backup नहीं मिला।"
                        );

                        new AlertDialog.Builder(this)
                                .setTitle("No Backup Found")
                                .setMessage(
                                        "MoneyManagerPro_Latest.mmpbackup " +
                                                "file नहीं मिली।\n\n" +
                                                "सही Documents या MoneyManagerPro " +
                                                "folder चुना गया है या नहीं, जाँच करें।"
                                )
                                .setPositiveButton("OK", null)
                                .show();
                    });

                    return;
                }

                JSONObject backupRoot =
                        readBackupJson(
                                latestBackupUri
                        );

                validateBackupFile(
                        backupRoot
                );

                BackupSummary backupSummary =
                        createBackupSummary(
                                backupRoot,
                                latestBackupUri
                        );

                runOnUiThread(() -> {
                    setBackupButtonsEnabled(true);

                    showRestoreConfirmation(
                            latestBackupUri,
                            backupSummary
                    );
                });

            } catch (Exception exception) {
                Log.e(
                        TAG,
                        "Unable to inspect backup",
                        exception
                );

                runOnUiThread(() -> {
                    setBackupButtonsEnabled(true);

                    txtBackupStatus.setText(
                            "Backup file मौजूद है, लेकिन पढ़ी नहीं जा सकी।"
                    );

                    new AlertDialog.Builder(this)
                            .setTitle("Invalid Backup")
                            .setMessage(
                                    "Backup file खराब है या यह सही " +
                                            "Money Manager Pro backup नहीं है।"
                            )
                            .setPositiveButton("OK", null)
                            .show();
                });
            }
        }).start();
    }

    private void showRestoreConfirmation(
            Uri backupUri,
            BackupSummary summary
    ) {
        String message =
                "Backup मौजूद है\n\n" +
                        "दिनांक एवं समय:\n" +
                        summary.createdAt +
                        "\n\n" +
                        "File Size: " +
                        summary.fileSize +
                        "\n" +
                        "Backup Version: " +
                        summary.backupVersion +
                        "\n" +
                        "App Version: " +
                        summary.appVersion +
                        "\n\n" +
                        "Transactions: " +
                        summary.transactionCount +
                        "\n" +
                        "Accounts: " +
                        summary.accountCount +
                        "\n" +
                        "Categories: " +
                        summary.categoryCount +
                        "\n" +
                        "Budgets: " +
                        summary.budgetCount +
                        "\n" +
                        "Goals: " +
                        summary.goalCount +
                        "\n" +
                        "Loans: " +
                        summary.loanCount +
                        "\n" +
                        "Recurring Transactions: " +
                        summary.recurringCount +
                        "\n\n" +
                        "Restore करने पर वर्तमान app data हट जाएगा " +
                        "और इस backup का data वापस आ जाएगा।";

        new AlertDialog.Builder(this)
                .setTitle("Restore Backup")
                .setMessage(message)
                .setPositiveButton(
                        "OK, Restore",
                        (dialog, which) ->
                                restoreBackup(backupUri)
                )
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void restoreBackup(
            Uri backupUri
    ) {
        setBackupButtonsEnabled(false);

        txtBackupStatus.setText(
                "Backup restore किया जा रहा है..."
        );

        new Thread(() -> {
            try {
                JSONObject root =
                        readBackupJson(
                                backupUri
                        );

                validateBackupFile(root);

                BackupContent backupContent =
                        parseBackup(root);

                AppDatabase database =
                        DatabaseClient
                                .getInstance(
                                        getApplicationContext()
                                )
                                .getAppDatabase();

                database.runInTransaction(() -> {
                    database.clearAllTables();

                    for (Category category :
                            backupContent.categories) {

                        database
                                .categoryDao()
                                .insert(category);
                    }

                    if (backupContent.accounts.isEmpty()) {
                        Account cashAccount =
                                new Account();

                        cashAccount.setName("Cash");
                        cashAccount.setType("Cash");
                        cashAccount.setOpeningBalance(0);
                        cashAccount.setColor("#2E7D32");

                        database
                                .accountDao()
                                .insert(cashAccount);

                    } else {
                        for (Account account :
                                backupContent.accounts) {

                            database
                                    .accountDao()
                                    .insert(account);
                        }
                    }

                    for (Goal goal :
                            backupContent.goals) {

                        database
                                .goalDao()
                                .insert(goal);
                    }

                    for (RecurringTransaction recurring :
                            backupContent
                                    .recurringTransactions) {

                        database
                                .recurringTransactionDao()
                                .insert(recurring);
                    }

                    for (Budget budget :
                            backupContent.budgets) {

                        database
                                .budgetDao()
                                .insert(budget);
                    }

                    for (Loan loan :
                            backupContent.loans) {

                        database
                                .loanDao()
                                .insert(loan);
                    }

                    for (Transaction transaction :
                            backupContent.transactions) {

                        database
                                .transactionDao()
                                .insert(transaction);
                    }
                });

                String restoredDate =
                        getBackupDateTime(
                                root,
                                backupUri
                        );

                runOnUiThread(() -> {
                    setBackupButtonsEnabled(true);

                    txtBackupStatus.setText(
                            "Backup सफलतापूर्वक restore हो गया\n" +
                                    restoredDate
                    );

                    new AlertDialog.Builder(this)
                            .setTitle("Restore Complete")
                            .setMessage(
                                    "Backup का data सफलतापूर्वक restore हो गया है।\n\n" +
                                            "Dashboard पर वापस जाने के बाद नया data दिखाई देगा।"
                            )
                            .setPositiveButton(
                                    "OK",
                                    (dialog, which) -> finish()
                            )
                            .setCancelable(false)
                            .show();
                });

            } catch (Exception exception) {
                Log.e(
                        TAG,
                        "Backup restore failed",
                        exception
                );

                runOnUiThread(() -> {
                    setBackupButtonsEnabled(true);

                    txtBackupStatus.setText(
                            "Restore नहीं हो सका।"
                    );

                    new AlertDialog.Builder(this)
                            .setTitle("Restore Failed")
                            .setMessage(
                                    "Backup restore नहीं हो सका।\n\n" +
                                            "Backup file खराब हो सकती है या " +
                                            "उसका format इस app version के साथ compatible नहीं है।"
                            )
                            .setPositiveButton("OK", null)
                            .show();
                });
            }
        }).start();
    }

    private void updateBackupStatus() {
        if (!backupStorageManager
                .hasUsableBackupFolder()) {

            if (backupStorageManager
                    .getSavedTreeUri() == null) {

                txtBackupStatus.setText(
                        "Backup folder अभी चुना नहीं गया है।\n" +
                                "Create Backup दबाने पर पहली बार folder चुनें।"
                );

            } else {
                txtBackupStatus.setText(
                        "Backup folder की permission उपलब्ध नहीं है।\n" +
                                "Create Backup या Restore Backup दबाकर folder दोबारा चुनें।"
                );
            }

            return;
        }

        txtBackupStatus.setText(
                "Backup की स्थिति जाँची जा रही है..."
        );

        new Thread(() -> {
            String statusText;

            try {
                Uri latestBackupUri =
                        backupStorageManager
                                .findLatestBackupUri();

                if (latestBackupUri == null) {
                    statusText =
                            "Backup folder तैयार है\n" +
                                    backupStorageManager
                                            .getBackupLocationLabel() +
                                    "\nअभी कोई backup उपलब्ध नहीं है।";

                } else {
                    JSONObject root =
                            readBackupJson(
                                    latestBackupUri
                            );

                    validateBackupFile(root);

                    String createdAt =
                            getBackupDateTime(
                                    root,
                                    latestBackupUri
                            );

                    long size =
                            backupStorageManager
                                    .getDocumentSize(
                                            latestBackupUri
                                    );

                    statusText =
                            "Latest backup उपलब्ध है\n" +
                                    createdAt +
                                    "\n" +
                                    formatFileSize(size) +
                                    "\n" +
                                    backupStorageManager
                                            .getBackupLocationLabel();
                }

            } catch (Exception exception) {
                statusText =
                        "Backup file मौजूद है, लेकिन इसकी जानकारी " +
                                "पढ़ी नहीं जा सकी।";
            }

            String finalStatusText =
                    statusText;

            runOnUiThread(() ->
                    txtBackupStatus.setText(
                            finalStatusText
                    )
            );
        }).start();
    }

    private void writeBackupJson(
            Uri destinationUri,
            JSONObject backupData
    ) throws Exception {

        try (
                OutputStream outputStream =
                        backupStorageManager
                                .openBackupOutputStream(
                                        destinationUri
                                )
        ) {
            byte[] backupBytes =
                    backupData
                            .toString(2)
                            .getBytes(
                                    StandardCharsets.UTF_8
                            );

            outputStream.write(backupBytes);
            outputStream.flush();
        }
    }

    private JSONObject readBackupJson(
            Uri backupUri
    ) throws Exception {

        try (
                InputStream inputStream =
                        backupStorageManager
                                .openBackupInputStream(
                                        backupUri
                                );

                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        inputStream,
                                        StandardCharsets.UTF_8
                                )
                        )
        ) {
            StringBuilder textBuilder =
                    new StringBuilder();

            String line;

            while ((line = reader.readLine()) != null) {
                textBuilder.append(line);
            }

            return new JSONObject(
                    textBuilder.toString()
            );
        }
    }

    private void validateBackupFile(
            JSONObject root
    ) throws Exception {

        String appName =
                root.optString(
                        "appName",
                        ""
                );

        if (!"Money Manager Pro".equals(appName)) {
            throw new Exception(
                    "Invalid backup app name"
            );
        }

        int backupVersion =
                root.optInt(
                        "backupVersion",
                        0
                );

        if (backupVersion != 1) {
            throw new Exception(
                    "Unsupported backup version"
            );
        }
    }

    private BackupSummary createBackupSummary(
            JSONObject root,
            Uri backupUri
    ) {
        BackupSummary summary =
                new BackupSummary();

        summary.createdAt =
                getBackupDateTime(
                        root,
                        backupUri
                );

        summary.fileSize =
                formatFileSize(
                        backupStorageManager
                                .getDocumentSize(
                                        backupUri
                                )
                );

        summary.backupVersion =
                root.optInt(
                        "backupVersion",
                        1
                );

        summary.appVersion =
                root.optString(
                        "appVersion",
                        "Unknown"
                );

        summary.transactionCount =
                getArrayLength(
                        root.optJSONArray(
                                "transactions"
                        )
                );

        summary.categoryCount =
                getArrayLength(
                        root.optJSONArray(
                                "categories"
                        )
                );

        summary.accountCount =
                getArrayLength(
                        root.optJSONArray(
                                "accounts"
                        )
                );

        summary.goalCount =
                getArrayLength(
                        root.optJSONArray(
                                "goals"
                        )
                );

        summary.recurringCount =
                getArrayLength(
                        root.optJSONArray(
                                "recurringTransactions"
                        )
                );

        summary.budgetCount =
                getArrayLength(
                        root.optJSONArray(
                                "budgets"
                        )
                );

        summary.loanCount =
                getArrayLength(
                        root.optJSONArray(
                                "loans"
                        )
                );

        return summary;
    }

    private int getArrayLength(
            JSONArray array
    ) {
        return array == null
                ? 0
                : array.length();
    }

    private String getBackupDateTime(
            JSONObject root,
            Uri backupUri
    ) {
        String createdAt =
                root.optString(
                        "createdAt",
                        ""
                );

        if (!createdAt.trim().isEmpty()) {
            return createdAt;
        }

        long createdAtMillis =
                root.optLong(
                        "createdAtMillis",
                        0L
                );

        if (createdAtMillis > 0L) {
            return formatDateTime(
                    createdAtMillis
            );
        }

        long modifiedTime =
                backupStorageManager
                        .getDocumentLastModified(
                                backupUri
                        );

        if (modifiedTime > 0L) {
            return formatDateTime(
                    modifiedTime
            );
        }

        return "दिनांक एवं समय उपलब्ध नहीं";
    }

    private JSONObject buildBackupJson()
            throws Exception {

        JSONObject root =
                new JSONObject();

        root.put(
                "appName",
                "Money Manager Pro"
        );

        root.put(
                "backupVersion",
                1
        );

        root.put(
                "appVersion",
                getAppVersionName()
        );

        root.put(
                "createdAt",
                getCurrentDateTime()
        );

        root.put(
                "createdAtMillis",
                System.currentTimeMillis()
        );

        AppDatabase database =
                DatabaseClient
                        .getInstance(
                                getApplicationContext()
                        )
                        .getAppDatabase();

        List<Transaction> transactions =
                database
                        .transactionDao()
                        .getAllTransactions();

        List<Category> categories =
                database
                        .categoryDao()
                        .getAllCategories();

        List<Account> accounts =
                database
                        .accountDao()
                        .getAllAccounts();

        List<Goal> goals =
                database
                        .goalDao()
                        .getAllGoals();

        List<RecurringTransaction> recurringTransactions =
                database
                        .recurringTransactionDao()
                        .getAllRecurringTransactions();

        List<Budget> budgets =
                database
                        .budgetDao()
                        .getAllBudgets();

        List<Loan> loans =
                database
                        .loanDao()
                        .getAllLoans();

        JSONArray transactionArray =
                createTransactionArray(
                        transactions
                );

        JSONArray categoryArray =
                createCategoryArray(
                        categories
                );

        JSONArray accountArray =
                createAccountArray(
                        accounts
                );

        JSONArray goalArray =
                createGoalArray(
                        goals
                );

        JSONArray recurringArray =
                createRecurringArray(
                        recurringTransactions
                );

        JSONArray budgetArray =
                createBudgetArray(
                        budgets
                );

        JSONArray loanArray =
                createLoanArray(
                        loans
                );

        root.put(
                "transactions",
                transactionArray
        );

        root.put(
                "categories",
                categoryArray
        );

        root.put(
                "accounts",
                accountArray
        );

        root.put(
                "goals",
                goalArray
        );

        root.put(
                "recurringTransactions",
                recurringArray
        );

        root.put(
                "budgets",
                budgetArray
        );

        root.put(
                "loans",
                loanArray
        );

        JSONObject recordCounts =
                new JSONObject();

        recordCounts.put(
                "transactions",
                transactionArray.length()
        );

        recordCounts.put(
                "categories",
                categoryArray.length()
        );

        recordCounts.put(
                "accounts",
                accountArray.length()
        );

        recordCounts.put(
                "goals",
                goalArray.length()
        );

        recordCounts.put(
                "recurringTransactions",
                recurringArray.length()
        );

        recordCounts.put(
                "budgets",
                budgetArray.length()
        );

        recordCounts.put(
                "loans",
                loanArray.length()
        );

        root.put(
                "recordCounts",
                recordCounts
        );

        return root;
    }

    private BackupContent parseBackup(
            JSONObject root
    ) throws Exception {

        validateBackupFile(root);

        BackupContent content =
                new BackupContent();

        content.transactions =
                parseTransactions(
                        root.optJSONArray(
                                "transactions"
                        )
                );

        content.categories =
                parseCategories(
                        root.optJSONArray(
                                "categories"
                        )
                );

        content.accounts =
                parseAccounts(
                        root.optJSONArray(
                                "accounts"
                        )
                );

        content.goals =
                parseGoals(
                        root.optJSONArray(
                                "goals"
                        )
                );

        content.recurringTransactions =
                parseRecurringTransactions(
                        root.optJSONArray(
                                "recurringTransactions"
                        )
                );

        content.budgets =
                parseBudgets(
                        root.optJSONArray(
                                "budgets"
                        )
                );

        content.loans =
                parseLoans(
                        root.optJSONArray(
                                "loans"
                        )
                );

        return content;
    }

    private JSONArray createTransactionArray(
            List<Transaction> transactions
    ) throws Exception {

        JSONArray array =
                new JSONArray();

        for (Transaction transaction :
                transactions) {

            JSONObject object =
                    new JSONObject();

            object.put(
                    "type",
                    transaction.getType()
            );

            object.put(
                    "amount",
                    transaction.getAmount()
            );

            object.put(
                    "category",
                    transaction.getCategory()
            );

            object.put(
                    "account",
                    transaction.getAccount()
            );

            object.put(
                    "note",
                    transaction.getNote()
            );

            object.put(
                    "date",
                    transaction.getDate()
            );

            array.put(object);
        }

        return array;
    }

    private JSONArray createCategoryArray(
            List<Category> categories
    ) throws Exception {

        JSONArray array =
                new JSONArray();

        for (Category category :
                categories) {

            JSONObject object =
                    new JSONObject();

            object.put(
                    "name",
                    category.getName()
            );

            object.put(
                    "type",
                    category.getType()
            );

            object.put(
                    "color",
                    category.getColor()
            );

            array.put(object);
        }

        return array;
    }

    private JSONArray createAccountArray(
            List<Account> accounts
    ) throws Exception {

        JSONArray array =
                new JSONArray();

        for (Account account :
                accounts) {

            JSONObject object =
                    new JSONObject();

            object.put(
                    "name",
                    account.getName()
            );

            object.put(
                    "type",
                    account.getType()
            );

            object.put(
                    "openingBalance",
                    account.getOpeningBalance()
            );

            object.put(
                    "color",
                    account.getColor()
            );

            array.put(object);
        }

        return array;
    }

    private JSONArray createGoalArray(
            List<Goal> goals
    ) throws Exception {

        JSONArray array =
                new JSONArray();

        for (Goal goal :
                goals) {

            JSONObject object =
                    new JSONObject();

            object.put(
                    "name",
                    goal.getName()
            );

            object.put(
                    "targetAmount",
                    goal.getTargetAmount()
            );

            object.put(
                    "savedAmount",
                    goal.getSavedAmount()
            );

            object.put(
                    "targetDate",
                    goal.getTargetDate()
            );

            object.put(
                    "color",
                    goal.getColor()
            );

            array.put(object);
        }

        return array;
    }

    private JSONArray createRecurringArray(
            List<RecurringTransaction> recurringTransactions
    ) throws Exception {

        JSONArray array =
                new JSONArray();

        for (RecurringTransaction recurring :
                recurringTransactions) {

            JSONObject object =
                    new JSONObject();

            object.put(
                    "type",
                    recurring.getType()
            );

            object.put(
                    "amount",
                    recurring.getAmount()
            );

            object.put(
                    "category",
                    recurring.getCategory()
            );

            object.put(
                    "account",
                    recurring.getAccount()
            );

            object.put(
                    "note",
                    recurring.getNote()
            );

            object.put(
                    "frequency",
                    recurring.getFrequency()
            );

            object.put(
                    "startDate",
                    recurring.getStartDate()
            );

            object.put(
                    "nextRunDate",
                    recurring.getNextRunDate()
            );

            object.put(
                    "active",
                    recurring.isActive()
            );

            array.put(object);
        }

        return array;
    }

    private JSONArray createBudgetArray(
            List<Budget> budgets
    ) throws Exception {

        JSONArray array =
                new JSONArray();

        for (Budget budget :
                budgets) {

            JSONObject object =
                    new JSONObject();

            object.put(
                    "category",
                    budget.getCategory()
            );

            object.put(
                    "period",
                    budget.getPeriod()
            );

            object.put(
                    "limitAmount",
                    budget.getLimitAmount()
            );

            array.put(object);
        }

        return array;
    }

    private JSONArray createLoanArray(
            List<Loan> loans
    ) throws Exception {

        JSONArray array =
                new JSONArray();

        for (Loan loan :
                loans) {

            JSONObject object =
                    new JSONObject();

            object.put(
                    "personName",
                    loan.getPersonName()
            );

            object.put(
                    "loanType",
                    loan.getLoanType()
            );

            object.put(
                    "totalAmount",
                    loan.getTotalAmount()
            );

            object.put(
                    "outstandingAmount",
                    loan.getOutstandingAmount()
            );

            object.put(
                    "interestRate",
                    loan.getInterestRate()
            );

            object.put(
                    "emiAmount",
                    loan.getEmiAmount()
            );

            object.put(
                    "dueDate",
                    loan.getDueDate()
            );

            object.put(
                    "note",
                    loan.getNote()
            );

            object.put(
                    "active",
                    loan.isActive()
            );

            array.put(object);
        }

        return array;
    }

    private List<Transaction> parseTransactions(
            JSONArray array
    ) {
        List<Transaction> transactions =
                new ArrayList<>();

        if (array == null) {
            return transactions;
        }

        for (int index = 0;
             index < array.length();
             index++) {

            JSONObject object =
                    array.optJSONObject(index);

            if (object == null) {
                continue;
            }

            Transaction transaction =
                    new Transaction();

            transaction.setType(
                    object.optString(
                            "type",
                            ""
                    )
            );

            transaction.setAmount(
                    object.optDouble(
                            "amount",
                            0
                    )
            );

            transaction.setCategory(
                    object.optString(
                            "category",
                            ""
                    )
            );

            transaction.setAccount(
                    object.optString(
                            "account",
                            "Cash"
                    )
            );

            transaction.setNote(
                    object.optString(
                            "note",
                            ""
                    )
            );

            transaction.setDate(
                    object.optString(
                            "date",
                            ""
                    )
            );

            transactions.add(
                    transaction
            );
        }

        return transactions;
    }

    private List<Category> parseCategories(
            JSONArray array
    ) {
        List<Category> categories =
                new ArrayList<>();

        if (array == null) {
            return categories;
        }

        for (int index = 0;
             index < array.length();
             index++) {

            JSONObject object =
                    array.optJSONObject(index);

            if (object == null) {
                continue;
            }

            Category category =
                    new Category();

            category.setName(
                    object.optString(
                            "name",
                            ""
                    )
            );

            category.setType(
                    object.optString(
                            "type",
                            ""
                    )
            );

            category.setColor(
                    object.optString(
                            "color",
                            "#1565C0"
                    )
            );

            categories.add(category);
        }

        return categories;
    }

    private List<Account> parseAccounts(
            JSONArray array
    ) {
        List<Account> accounts =
                new ArrayList<>();

        if (array == null) {
            return accounts;
        }

        for (int index = 0;
             index < array.length();
             index++) {

            JSONObject object =
                    array.optJSONObject(index);

            if (object == null) {
                continue;
            }

            Account account =
                    new Account();

            account.setName(
                    object.optString(
                            "name",
                            "Cash"
                    )
            );

            account.setType(
                    object.optString(
                            "type",
                            "Cash"
                    )
            );

            account.setOpeningBalance(
                    object.optDouble(
                            "openingBalance",
                            0
                    )
            );

            account.setColor(
                    object.optString(
                            "color",
                            "#2E7D32"
                    )
            );

            accounts.add(account);
        }

        return accounts;
    }

    private List<Goal> parseGoals(
            JSONArray array
    ) {
        List<Goal> goals =
                new ArrayList<>();

        if (array == null) {
            return goals;
        }

        for (int index = 0;
             index < array.length();
             index++) {

            JSONObject object =
                    array.optJSONObject(index);

            if (object == null) {
                continue;
            }

            Goal goal =
                    new Goal();

            goal.setName(
                    object.optString(
                            "name",
                            ""
                    )
            );

            goal.setTargetAmount(
                    object.optDouble(
                            "targetAmount",
                            0
                    )
            );

            goal.setSavedAmount(
                    object.optDouble(
                            "savedAmount",
                            0
                    )
            );

            goal.setTargetDate(
                    object.optString(
                            "targetDate",
                            ""
                    )
            );

            goal.setColor(
                    object.optString(
                            "color",
                            "#6C63FF"
                    )
            );

            goals.add(goal);
        }

        return goals;
    }

    private List<RecurringTransaction>
    parseRecurringTransactions(
            JSONArray array
    ) {
        List<RecurringTransaction>
                recurringTransactions =
                new ArrayList<>();

        if (array == null) {
            return recurringTransactions;
        }

        for (int index = 0;
             index < array.length();
             index++) {

            JSONObject object =
                    array.optJSONObject(index);

            if (object == null) {
                continue;
            }

            RecurringTransaction recurring =
                    new RecurringTransaction();

            recurring.setType(
                    object.optString(
                            "type",
                            ""
                    )
            );

            recurring.setAmount(
                    object.optDouble(
                            "amount",
                            0
                    )
            );

            recurring.setCategory(
                    object.optString(
                            "category",
                            ""
                    )
            );

            recurring.setAccount(
                    object.optString(
                            "account",
                            "Cash"
                    )
            );

            recurring.setNote(
                    object.optString(
                            "note",
                            ""
                    )
            );

            recurring.setFrequency(
                    object.optString(
                            "frequency",
                            "Monthly"
                    )
            );

            recurring.setStartDate(
                    object.optString(
                            "startDate",
                            ""
                    )
            );

            recurring.setNextRunDate(
                    object.optString(
                            "nextRunDate",
                            ""
                    )
            );

            recurring.setActive(
                    object.optBoolean(
                            "active",
                            true
                    )
            );

            recurringTransactions.add(
                    recurring
            );
        }

        return recurringTransactions;
    }

    private List<Budget> parseBudgets(
            JSONArray array
    ) {
        List<Budget> budgets =
                new ArrayList<>();

        if (array == null) {
            return budgets;
        }

        for (int index = 0;
             index < array.length();
             index++) {

            JSONObject object =
                    array.optJSONObject(index);

            if (object == null) {
                continue;
            }

            Budget budget =
                    new Budget();

            budget.setCategory(
                    object.optString(
                            "category",
                            ""
                    )
            );

            budget.setPeriod(
                    object.optString(
                            "period",
                            "Monthly"
                    )
            );

            budget.setLimitAmount(
                    object.optDouble(
                            "limitAmount",
                            0
                    )
            );

            budgets.add(budget);
        }

        return budgets;
    }

    private List<Loan> parseLoans(
            JSONArray array
    ) {
        List<Loan> loans =
                new ArrayList<>();

        if (array == null) {
            return loans;
        }

        for (int index = 0;
             index < array.length();
             index++) {

            JSONObject object =
                    array.optJSONObject(index);

            if (object == null) {
                continue;
            }

            Loan loan =
                    new Loan();

            loan.setPersonName(
                    object.optString(
                            "personName",
                            ""
                    )
            );

            loan.setLoanType(
                    object.optString(
                            "loanType",
                            "Loan Taken"
                    )
            );

            loan.setTotalAmount(
                    object.optDouble(
                            "totalAmount",
                            0
                    )
            );

            loan.setOutstandingAmount(
                    object.optDouble(
                            "outstandingAmount",
                            0
                    )
            );

            loan.setInterestRate(
                    object.optDouble(
                            "interestRate",
                            0
                    )
            );

            loan.setEmiAmount(
                    object.optDouble(
                            "emiAmount",
                            0
                    )
            );

            loan.setDueDate(
                    object.optString(
                            "dueDate",
                            ""
                    )
            );

            loan.setNote(
                    object.optString(
                            "note",
                            ""
                    )
            );

            loan.setActive(
                    object.optBoolean(
                            "active",
                            true
                    )
            );

            loans.add(loan);
        }

        return loans;
    }

    private void setBackupButtonsEnabled(
            boolean enabled
    ) {
        btnCreateBackup.setEnabled(enabled);
        btnRestoreBackup.setEnabled(enabled);
    }

    private String getCurrentDateTime() {
        return formatDateTime(
                System.currentTimeMillis()
        );
    }

    private String formatDateTime(
            long timestamp
    ) {
        return new SimpleDateFormat(
                "dd MMM yyyy, hh:mm a",
                Locale.getDefault()
        ).format(
                new Date(timestamp)
        );
    }

    private String formatFileSize(
            long sizeInBytes
    ) {
        if (sizeInBytes < 0) {
            return "File size उपलब्ध नहीं";
        }

        if (sizeInBytes < 1024) {
            return sizeInBytes + " Bytes";
        }

        double sizeInKb =
                sizeInBytes / 1024.0;

        if (sizeInKb < 1024) {
            return String.format(
                    Locale.getDefault(),
                    "%.2f KB",
                    sizeInKb
            );
        }

        double sizeInMb =
                sizeInKb / 1024.0;

        return String.format(
                Locale.getDefault(),
                "%.2f MB",
                sizeInMb
        );
    }

    private String getAppVersionName() {
        try {
            return getPackageManager()
                    .getPackageInfo(
                            getPackageName(),
                            0
                    )
                    .versionName;

        } catch (Exception exception) {
            return "Unknown";
        }
    }

    private static class BackupContent {
        List<Transaction> transactions;
        List<Category> categories;
        List<Account> accounts;
        List<Goal> goals;

        List<RecurringTransaction>
                recurringTransactions;

        List<Budget> budgets;
        List<Loan> loans;
    }

    private static class BackupSummary {
        String createdAt;
        String fileSize;
        String appVersion;

        int backupVersion;
        int transactionCount;
        int categoryCount;
        int accountCount;
        int goalCount;
        int recurringCount;
        int budgetCount;
        int loanCount;
    }
}