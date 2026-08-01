package com.example.moneymanagerpro.backup;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.moneymanagerpro.database.AppDatabase;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Account;
import com.example.moneymanagerpro.model.Budget;
import com.example.moneymanagerpro.model.Category;
import com.example.moneymanagerpro.model.CreditCard;
import com.example.moneymanagerpro.model.CreditCardPayment;
import com.example.moneymanagerpro.model.ExpenseItem;
import com.example.moneymanagerpro.model.Goal;
import com.example.moneymanagerpro.model.InvestmentItem;
import com.example.moneymanagerpro.model.Loan;
import com.example.moneymanagerpro.model.LoanPayment;
import com.example.moneymanagerpro.model.RecurringTransaction;
import com.example.moneymanagerpro.model.Subscription;
import com.example.moneymanagerpro.model.Transaction;
import com.example.moneymanagerpro.utils.BackupStorageManager;
import com.example.moneymanagerpro.utils.InvestmentStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Creates a complete verified offline Money Manager Pro backup.
 *
 * Processing flow:
 *
 * 1. Read every Room table inside one Room transaction.
 * 2. Read investments from InvestmentStore.
 * 3. Build the complete version-5 JSON backup.
 * 4. Calculate SHA-256 integrity checksum.
 * 5. Write the backup to a temporary document.
 * 6. Read the temporary document again.
 * 7. Verify structure, counts, IDs, references and SHA-256.
 * 8. Replace the previous latest backup safely.
 *
 * This class performs blocking database and storage work.
 * Always call createVerifiedBackup() from a background thread.
 */
public final class OfflineBackupEngine {

    public static final String APP_NAME =
            "Money Manager Pro";

    public static final String BACKUP_TYPE =
            "MONEY_MANAGER_PRO_FULL_OFFLINE_BACKUP";

    public static final int BACKUP_VERSION =
            5;

    public static final String INTEGRITY_ALGORITHM =
            "SHA-256";

    public static final int MAX_BACKUP_BYTES =
            25 * 1024 * 1024;

    private static final String[] REQUIRED_ARRAYS = {
            "transactions",
            "expenseItems",
            "categories",
            "accounts",
            "goals",
            "recurringTransactions",
            "budgets",
            "loans",
            "loanPayments",
            "subscriptions",
            "creditCards",
            "creditCardPayments",
            "investments"
    };

    private static final String[] INTEGER_ID_ARRAYS = {
            "transactions",
            "expenseItems",
            "categories",
            "accounts",
            "goals",
            "recurringTransactions",
            "budgets",
            "loans",
            "loanPayments",
            "subscriptions",
            "creditCards",
            "creditCardPayments"
    };

    private final Context applicationContext;

    private final BackupStorageManager backupStorageManager;

    public OfflineBackupEngine(
            @NonNull Context context
    ) {
        applicationContext =
                context.getApplicationContext();

        backupStorageManager =
                new BackupStorageManager(
                        applicationContext
                );
    }

    /**
     * Creates, verifies and commits the latest offline backup.
     *
     * Requirements:
     *
     * - The user must already have selected a usable backup folder.
     * - This method must run on a background thread.
     */
    @NonNull
    public BackupResult createVerifiedBackup()
            throws OfflineBackupException {

        String currentStage =
                "Checking backup folder";

        Uri temporaryBackupUri =
                null;

        try {
            if (!backupStorageManager
                    .hasUsableBackupFolder()) {

                throw new BackupFolderUnavailableException(
                        "A usable offline backup folder "
                                + "has not been selected."
                );
            }

            currentStage =
                    "Reading finance data";

            JSONObject backupRoot =
                    buildBackupJson();

            currentStage =
                    "Creating temporary backup file";

            temporaryBackupUri =
                    backupStorageManager
                            .createTemporaryBackupUri();

            currentStage =
                    "Writing temporary backup file";

            int writtenByteCount =
                    writeBackupJson(
                            temporaryBackupUri,
                            backupRoot
                    );

            currentStage =
                    "Reading temporary backup file";

            JSONObject verificationRoot =
                    readBackupJson(
                            temporaryBackupUri
                    );

            currentStage =
                    "Verifying backup integrity";

            ValidationResult validationResult =
                    validateBackupRoot(
                            verificationRoot
                    );

            currentStage =
                    "Replacing latest backup safely";

            Uri latestBackupUri =
                    backupStorageManager
                            .commitTemporaryBackup(
                                    temporaryBackupUri
                            );

            temporaryBackupUri =
                    null;

            currentStage =
                    "Reading final backup information";

            long documentSize =
                    backupStorageManager
                            .getDocumentSize(
                                    latestBackupUri
                            );

            if (documentSize <= 0L) {
                documentSize =
                        writtenByteCount;
            }

            return new BackupResult(
                    validationResult.backupId,
                    validationResult.createdAtMillis,
                    validationResult.createdAtText,
                    validationResult.databaseVersion,
                    validationResult.appVersionName,
                    validationResult.appVersionCode,
                    validationResult.recordCounts,
                    documentSize,
                    latestBackupUri,
                    backupStorageManager
                            .getBackupLocationLabel(),
                    validationResult.integritySha256
            );

        } catch (OfflineBackupException exception) {
            throw exception;

        } catch (Exception exception) {
            throw new OfflineBackupException(
                    currentStage,
                    usefulMessage(
                            exception,
                            "Offline backup could not be completed."
                    ),
                    exception
            );
        }
    }

    /**
     * Creates the complete JSON backup in memory.
     */
    @NonNull
    public JSONObject buildBackupJson()
            throws OfflineBackupException {

        try {
            AppDatabase database =
                    DatabaseClient
                            .getInstance(
                                    applicationContext
                            )
                            .getAppDatabase();

            int databaseVersion =
                    database
                            .getOpenHelper()
                            .getReadableDatabase()
                            .getVersion();

            AppVersionInformation appVersion =
                    readAppVersionInformation();

            long createdAtMillis =
                    System.currentTimeMillis();

            String backupId =
                    UUID.randomUUID()
                            .toString()
                            .replace(
                                    "-",
                                    ""
                            );

            BackupSnapshot snapshot =
                    new BackupSnapshot();

            database.runInTransaction(
                    () -> {
                        snapshot.transactions =
                                database
                                        .transactionDao()
                                        .getAllTransactions();

                        snapshot.expenseItems =
                                database
                                        .expenseItemDao()
                                        .getAllExpenseItems();

                        snapshot.categories =
                                database
                                        .categoryDao()
                                        .getAllCategories();

                        snapshot.accounts =
                                database
                                        .accountDao()
                                        .getAllAccounts();

                        snapshot.goals =
                                database
                                        .goalDao()
                                        .getAllGoals();

                        snapshot.recurringTransactions =
                                database
                                        .recurringTransactionDao()
                                        .getAllRecurringTransactions();

                        snapshot.budgets =
                                database
                                        .budgetDao()
                                        .getAllBudgets();

                        snapshot.loans =
                                database
                                        .loanDao()
                                        .getAllLoans();

                        snapshot.loanPayments =
                                database
                                        .loanPaymentDao()
                                        .getAllLoanPayments();

                        snapshot.subscriptions =
                                database
                                        .subscriptionDao()
                                        .getAllSubscriptions();

                        snapshot.creditCards =
                                database
                                        .creditCardDao()
                                        .getAllCreditCards();

                        snapshot.creditCardPayments =
                                database
                                        .creditCardPaymentDao()
                                        .getAllPayments();
                    }
            );

            snapshot.investments =
                    InvestmentStore.getAll(
                            applicationContext
                    );

            normalizeSnapshotLists(
                    snapshot
            );

            JSONArray transactions =
                    createTransactionArray(
                            snapshot.transactions
                    );

            JSONArray expenseItems =
                    createExpenseItemArray(
                            snapshot.expenseItems
                    );

            JSONArray categories =
                    createCategoryArray(
                            snapshot.categories
                    );

            JSONArray accounts =
                    createAccountArray(
                            snapshot.accounts
                    );

            JSONArray goals =
                    createGoalArray(
                            snapshot.goals
                    );

            JSONArray recurringTransactions =
                    createRecurringTransactionArray(
                            snapshot.recurringTransactions
                    );

            JSONArray budgets =
                    createBudgetArray(
                            snapshot.budgets
                    );

            JSONArray loans =
                    createLoanArray(
                            snapshot.loans
                    );

            JSONArray loanPayments =
                    createLoanPaymentArray(
                            snapshot.loanPayments
                    );

            JSONArray subscriptions =
                    createSubscriptionArray(
                            snapshot.subscriptions
                    );

            JSONArray creditCards =
                    createCreditCardArray(
                            snapshot.creditCards
                    );

            JSONArray creditCardPayments =
                    createCreditCardPaymentArray(
                            snapshot.creditCardPayments
                    );

            JSONArray investments =
                    createInvestmentArray(
                            snapshot.investments
                    );

            JSONObject root =
                    new JSONObject();

            root.put(
                    "appName",
                    APP_NAME
            );

            root.put(
                    "backupType",
                    BACKUP_TYPE
            );

            root.put(
                    "backupVersion",
                    BACKUP_VERSION
            );

            root.put(
                    "backupId",
                    backupId
            );

            root.put(
                    "databaseVersion",
                    databaseVersion
            );

            root.put(
                    "appVersion",
                    appVersion.versionName
            );

            root.put(
                    "appVersionCode",
                    appVersion.versionCode
            );

            root.put(
                    "createdAt",
                    formatDateTime(
                            createdAtMillis
                    )
            );

            root.put(
                    "createdAtMillis",
                    createdAtMillis
            );

            root.put(
                    "transactions",
                    transactions
            );

            root.put(
                    "expenseItems",
                    expenseItems
            );

            root.put(
                    "categories",
                    categories
            );

            root.put(
                    "accounts",
                    accounts
            );

            root.put(
                    "goals",
                    goals
            );

            root.put(
                    "recurringTransactions",
                    recurringTransactions
            );

            root.put(
                    "budgets",
                    budgets
            );

            root.put(
                    "loans",
                    loans
            );

            root.put(
                    "loanPayments",
                    loanPayments
            );

            root.put(
                    "subscriptions",
                    subscriptions
            );

            root.put(
                    "creditCards",
                    creditCards
            );

            root.put(
                    "creditCardPayments",
                    creditCardPayments
            );

            root.put(
                    "investments",
                    investments
            );

            RecordCounts recordCounts =
                    new RecordCounts(
                            transactions.length(),
                            expenseItems.length(),
                            categories.length(),
                            accounts.length(),
                            goals.length(),
                            recurringTransactions.length(),
                            budgets.length(),
                            loans.length(),
                            loanPayments.length(),
                            subscriptions.length(),
                            creditCards.length(),
                            creditCardPayments.length(),
                            investments.length()
                    );

            root.put(
                    "recordCounts",
                    recordCounts.toJson()
            );

            root.put(
                    "integrityAlgorithm",
                    INTEGRITY_ALGORITHM
            );

            root.put(
                    "integritySha256",
                    BackupIntegrity.calculateSha256(
                            root
                    )
            );

            byte[] estimatedBytes =
                    root.toString(2)
                            .getBytes(
                                    StandardCharsets.UTF_8
                            );

            if (estimatedBytes.length
                    > MAX_BACKUP_BYTES) {

                throw new OfflineBackupException(
                        "Building backup data",
                        "Offline backup exceeds the supported "
                                + "25 MB size limit."
                );
            }

            return root;

        } catch (OfflineBackupException exception) {
            throw exception;

        } catch (Exception exception) {
            throw new OfflineBackupException(
                    "Building backup data",
                    usefulMessage(
                            exception,
                            "Finance data could not be prepared "
                                    + "for offline backup."
                    ),
                    exception
            );
        }
    }

    /**
     * Reads and validates an existing version-5 offline backup.
     *
     * This method is used later by the manual restore screen and
     * automatic-backup verification.
     */
    @NonNull
    public ValidationResult inspectAndValidateBackup(
            @NonNull Uri backupUri
    ) throws OfflineBackupException {

        try {
            JSONObject root =
                    readBackupJson(
                            backupUri
                    );

            return validateBackupRoot(
                    root
            );

        } catch (OfflineBackupException exception) {
            throw exception;

        } catch (Exception exception) {
            throw new OfflineBackupException(
                    "Inspecting offline backup",
                    usefulMessage(
                            exception,
                            "Offline backup could not be inspected."
                    ),
                    exception
            );
        }
    }

    @NonNull
    private ValidationResult validateBackupRoot(
            @NonNull JSONObject root
    ) throws OfflineBackupException {

        try {
            String appName =
                    root.optString(
                            "appName",
                            ""
                    ).trim();

            if (!APP_NAME.equals(
                    appName
            )) {
                throw new BackupValidationException(
                        "Backup belongs to another application."
                );
            }

            String backupType =
                    root.optString(
                            "backupType",
                            ""
                    ).trim();

            if (!BACKUP_TYPE.equals(
                    backupType
            )) {
                throw new BackupValidationException(
                        "Unsupported offline backup type."
                );
            }

            int backupVersion =
                    root.optInt(
                            "backupVersion",
                            0
                    );

            if (backupVersion
                    != BACKUP_VERSION) {

                throw new BackupValidationException(
                        "Unsupported offline backup version."
                );
            }

            String backupId =
                    root.optString(
                            "backupId",
                            ""
                    ).trim();

            if (backupId.isEmpty()
                    || backupId.length() > 180
                    || !backupId.matches(
                    "[A-Za-z0-9_-]+"
            )) {

                throw new BackupValidationException(
                        "Offline backup ID is invalid."
                );
            }

            int backupDatabaseVersion =
                    root.optInt(
                            "databaseVersion",
                            0
                    );

            int currentDatabaseVersion =
                    DatabaseClient
                            .getInstance(
                                    applicationContext
                            )
                            .getAppDatabase()
                            .getOpenHelper()
                            .getReadableDatabase()
                            .getVersion();

            if (backupDatabaseVersion <= 0) {
                throw new BackupValidationException(
                        "Offline backup database version is invalid."
                );
            }

            if (backupDatabaseVersion
                    > currentDatabaseVersion) {

                throw new BackupValidationException(
                        "This offline backup was created by a "
                                + "newer database version. Update "
                                + "the app before restoring it."
                );
            }

            for (String arrayName :
                    REQUIRED_ARRAYS) {

                if (root.optJSONArray(
                        arrayName
                ) == null) {

                    throw new BackupValidationException(
                            "Backup section is missing: "
                                    + arrayName
                    );
                }
            }

            JSONObject countObject =
                    root.optJSONObject(
                            "recordCounts"
                    );

            if (countObject == null) {
                throw new BackupValidationException(
                        "Offline backup record counts are missing."
                );
            }

            RecordCounts recordCounts =
                    RecordCounts.fromJson(
                            countObject
                    );

            verifyArrayCounts(
                    root,
                    recordCounts
            );

            validateUniqueIntegerIds(
                    root
            );

            validateInvestmentIds(
                    root.getJSONArray(
                            "investments"
                    )
            );

            validateExpenseItemReferences(
                    root
            );

            validateLoanPaymentReferences(
                    root
            );

            validateCreditCardPaymentReferences(
                    root
            );

            validateCreditCardAccountReferences(
                    root
            );

            String integrityAlgorithm =
                    root.optString(
                            "integrityAlgorithm",
                            ""
                    ).trim();

            if (!INTEGRITY_ALGORITHM.equalsIgnoreCase(
                    integrityAlgorithm
            )) {
                throw new BackupValidationException(
                        "Unsupported offline backup "
                                + "integrity algorithm."
                );
            }

            String storedChecksum =
                    root.optString(
                                    "integritySha256",
                                    ""
                            )
                            .trim()
                            .toLowerCase(
                                    Locale.US
                            );

            if (!storedChecksum.matches(
                    "[0-9a-f]{64}"
            )) {
                throw new BackupValidationException(
                        "Offline backup checksum is invalid."
                );
            }

            if (!BackupIntegrity.verify(
                    root,
                    storedChecksum
            )) {
                throw new BackupValidationException(
                        "Offline backup SHA-256 verification failed."
                );
            }

            long createdAtMillis =
                    root.optLong(
                            "createdAtMillis",
                            0L
                    );

            if (createdAtMillis <= 0L) {
                throw new BackupValidationException(
                        "Offline backup creation time is invalid."
                );
            }

            String createdAtText =
                    root.optString(
                            "createdAt",
                            ""
                    ).trim();

            if (createdAtText.isEmpty()) {
                createdAtText =
                        formatDateTime(
                                createdAtMillis
                        );
            }

            String appVersionName =
                    root.optString(
                            "appVersion",
                            ""
                    );

            long appVersionCode =
                    root.optLong(
                            "appVersionCode",
                            0L
                    );

            return new ValidationResult(
                    backupId,
                    createdAtMillis,
                    createdAtText,
                    backupDatabaseVersion,
                    appVersionName,
                    appVersionCode,
                    recordCounts,
                    storedChecksum
            );

        } catch (OfflineBackupException exception) {
            throw exception;

        } catch (Exception exception) {
            throw new BackupValidationException(
                    usefulMessage(
                            exception,
                            "Offline backup validation failed."
                    ),
                    exception
            );
        }
    }

    private void verifyArrayCounts(
            @NonNull JSONObject root,
            @NonNull RecordCounts counts
    ) throws Exception {

        verifyArrayCount(
                root,
                "transactions",
                counts.transactions
        );

        verifyArrayCount(
                root,
                "expenseItems",
                counts.expenseItems
        );

        verifyArrayCount(
                root,
                "categories",
                counts.categories
        );

        verifyArrayCount(
                root,
                "accounts",
                counts.accounts
        );

        verifyArrayCount(
                root,
                "goals",
                counts.goals
        );

        verifyArrayCount(
                root,
                "recurringTransactions",
                counts.recurringTransactions
        );

        verifyArrayCount(
                root,
                "budgets",
                counts.budgets
        );

        verifyArrayCount(
                root,
                "loans",
                counts.loans
        );

        verifyArrayCount(
                root,
                "loanPayments",
                counts.loanPayments
        );

        verifyArrayCount(
                root,
                "subscriptions",
                counts.subscriptions
        );

        verifyArrayCount(
                root,
                "creditCards",
                counts.creditCards
        );

        verifyArrayCount(
                root,
                "creditCardPayments",
                counts.creditCardPayments
        );

        verifyArrayCount(
                root,
                "investments",
                counts.investments
        );

        int calculatedTotal =
                counts.transactions
                        + counts.expenseItems
                        + counts.categories
                        + counts.accounts
                        + counts.goals
                        + counts.recurringTransactions
                        + counts.budgets
                        + counts.loans
                        + counts.loanPayments
                        + counts.subscriptions
                        + counts.creditCards
                        + counts.creditCardPayments
                        + counts.investments;

        if (calculatedTotal
                != counts.totalRecords) {

            throw new BackupValidationException(
                    "Offline backup total record count "
                            + "verification failed."
            );
        }
    }

    private void verifyArrayCount(
            @NonNull JSONObject root,
            @NonNull String arrayName,
            int declaredCount
    ) throws Exception {

        int actualCount =
                root.getJSONArray(
                        arrayName
                ).length();

        if (declaredCount
                != actualCount) {

            throw new BackupValidationException(
                    "Offline backup record count mismatch: "
                            + arrayName
            );
        }
    }

    private void validateUniqueIntegerIds(
            @NonNull JSONObject root
    ) throws Exception {

        for (String arrayName :
                INTEGER_ID_ARRAYS) {

            JSONArray array =
                    root.getJSONArray(
                            arrayName
                    );

            Set<Integer> usedIds =
                    new HashSet<>();

            for (int index = 0;
                 index < array.length();
                 index++) {

                JSONObject object =
                        array.optJSONObject(
                                index
                        );

                if (object == null) {
                    throw new BackupValidationException(
                            "Invalid record in "
                                    + arrayName
                    );
                }

                int id =
                        object.optInt(
                                "id",
                                0
                        );

                if (id <= 0
                        || !usedIds.add(
                        id
                )) {

                    throw new BackupValidationException(
                            "Invalid or duplicate ID in "
                                    + arrayName
                    );
                }
            }
        }
    }

    private void validateInvestmentIds(
            @NonNull JSONArray investments
    ) throws Exception {

        Set<String> usedIds =
                new HashSet<>();

        for (int index = 0;
             index < investments.length();
             index++) {

            JSONObject object =
                    investments.optJSONObject(
                            index
                    );

            if (object == null) {
                throw new BackupValidationException(
                        "Invalid investment record."
                );
            }

            String id =
                    object.optString(
                            "id",
                            ""
                    ).trim();

            if (id.isEmpty()
                    || id.length() > 200
                    || !usedIds.add(
                    id
            )) {

                throw new BackupValidationException(
                        "Invalid or duplicate investment ID."
                );
            }
        }
    }

    private void validateExpenseItemReferences(
            @NonNull JSONObject root
    ) throws Exception {

        Set<Integer> transactionIds =
                collectIntegerIds(
                        root.getJSONArray(
                                "transactions"
                        )
                );

        JSONArray expenseItems =
                root.getJSONArray(
                        "expenseItems"
                );

        for (int index = 0;
             index < expenseItems.length();
             index++) {

            int transactionId =
                    expenseItems
                            .getJSONObject(
                                    index
                            )
                            .optInt(
                                    "transactionId",
                                    0
                            );

            if (!transactionIds.contains(
                    transactionId
            )) {
                throw new BackupValidationException(
                        "Expense item references a "
                                + "missing transaction."
                );
            }
        }
    }

    private void validateLoanPaymentReferences(
            @NonNull JSONObject root
    ) throws Exception {

        Set<Integer> loanIds =
                collectIntegerIds(
                        root.getJSONArray(
                                "loans"
                        )
                );

        JSONArray payments =
                root.getJSONArray(
                        "loanPayments"
                );

        for (int index = 0;
             index < payments.length();
             index++) {

            int loanId =
                    payments
                            .getJSONObject(
                                    index
                            )
                            .optInt(
                                    "loanId",
                                    0
                            );

            if (!loanIds.contains(
                    loanId
            )) {
                throw new BackupValidationException(
                        "Loan payment references a missing loan."
                );
            }
        }
    }

    private void validateCreditCardPaymentReferences(
            @NonNull JSONObject root
    ) throws Exception {

        Set<Integer> creditCardIds =
                collectIntegerIds(
                        root.getJSONArray(
                                "creditCards"
                        )
                );

        JSONArray payments =
                root.getJSONArray(
                        "creditCardPayments"
                );

        for (int index = 0;
             index < payments.length();
             index++) {

            int creditCardId =
                    payments
                            .getJSONObject(
                                    index
                            )
                            .optInt(
                                    "creditCardId",
                                    0
                            );

            if (!creditCardIds.contains(
                    creditCardId
            )) {
                throw new BackupValidationException(
                        "Credit-card payment references "
                                + "a missing credit card."
                );
            }
        }
    }

    private void validateCreditCardAccountReferences(
            @NonNull JSONObject root
    ) throws Exception {

        JSONArray accounts =
                root.getJSONArray(
                        "accounts"
                );

        Set<String> accountNames =
                new HashSet<>();

        for (int index = 0;
             index < accounts.length();
             index++) {

            accountNames.add(
                    accounts
                            .getJSONObject(
                                    index
                            )
                            .optString(
                                    "name",
                                    ""
                            )
            );
        }

        JSONArray creditCards =
                root.getJSONArray(
                        "creditCards"
                );

        for (int index = 0;
             index < creditCards.length();
             index++) {

            String accountName =
                    creditCards
                            .getJSONObject(
                                    index
                            )
                            .optString(
                                    "accountName",
                                    ""
                            );

            if (!accountNames.contains(
                    accountName
            )) {
                throw new BackupValidationException(
                        "Credit-card account is missing "
                                + "from the offline backup."
                );
            }
        }
    }

    @NonNull
    private Set<Integer> collectIntegerIds(
            @NonNull JSONArray array
    ) throws Exception {

        Set<Integer> ids =
                new HashSet<>();

        for (int index = 0;
             index < array.length();
             index++) {

            ids.add(
                    array
                            .getJSONObject(
                                    index
                            )
                            .getInt(
                                    "id"
                            )
            );
        }

        return ids;
    }

    private int writeBackupJson(
            @NonNull Uri destinationUri,
            @NonNull JSONObject backupRoot
    ) throws OfflineBackupException {

        byte[] backupBytes =
                null;

        try {
            backupBytes =
                    backupRoot
                            .toString(2)
                            .getBytes(
                                    StandardCharsets.UTF_8
                            );

            if (backupBytes.length <= 0) {
                throw new OfflineBackupException(
                        "Writing offline backup",
                        "Offline backup content is empty."
                );
            }

            if (backupBytes.length
                    > MAX_BACKUP_BYTES) {

                throw new OfflineBackupException(
                        "Writing offline backup",
                        "Offline backup exceeds the supported "
                                + "25 MB size limit."
                );
            }

            try (
                    OutputStream outputStream =
                            backupStorageManager
                                    .openBackupOutputStream(
                                            destinationUri
                                    )
            ) {
                outputStream.write(
                        backupBytes
                );

                outputStream.flush();
            }

            return backupBytes.length;

        } catch (OfflineBackupException exception) {
            throw exception;

        } catch (Exception exception) {
            throw new OfflineBackupException(
                    "Writing offline backup",
                    usefulMessage(
                            exception,
                            "Offline backup file could not be written."
                    ),
                    exception
            );
        }
    }

    @NonNull
    private JSONObject readBackupJson(
            @NonNull Uri backupUri
    ) throws OfflineBackupException {

        try (
                InputStream inputStream =
                        backupStorageManager
                                .openBackupInputStream(
                                        backupUri
                                );

                ByteArrayOutputStream outputStream =
                        new ByteArrayOutputStream()
        ) {
            byte[] buffer =
                    new byte[8192];

            int totalBytes =
                    0;

            int bytesRead;

            while ((bytesRead =
                    inputStream.read(
                            buffer
                    )) != -1) {

                totalBytes +=
                        bytesRead;

                if (totalBytes
                        > MAX_BACKUP_BYTES) {

                    throw new OfflineBackupException(
                            "Reading offline backup",
                            "Offline backup exceeds the supported "
                                    + "25 MB size limit."
                    );
                }

                outputStream.write(
                        buffer,
                        0,
                        bytesRead
                );
            }

            if (totalBytes <= 0) {
                throw new OfflineBackupException(
                        "Reading offline backup",
                        "Offline backup file is empty."
                );
            }

            return new JSONObject(
                    outputStream.toString(
                            StandardCharsets.UTF_8.name()
                    )
            );

        } catch (OfflineBackupException exception) {
            throw exception;

        } catch (Exception exception) {
            throw new OfflineBackupException(
                    "Reading offline backup",
                    usefulMessage(
                            exception,
                            "Offline backup file could not be read."
                    ),
                    exception
            );
        }
    }

    @NonNull
    private JSONArray createTransactionArray(
            @NonNull List<Transaction> transactions
    ) throws Exception {

        JSONArray array =
                new JSONArray();

        for (Transaction transaction :
                transactions) {

            JSONObject object =
                    new JSONObject();

            object.put(
                    "id",
                    transaction.getId()
            );

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

            array.put(
                    object
            );
        }

        return array;
    }

    @NonNull
    private JSONArray createExpenseItemArray(
            @NonNull List<ExpenseItem> expenseItems
    ) throws Exception {

        JSONArray array =
                new JSONArray();

        for (ExpenseItem item :
                expenseItems) {

            JSONObject object =
                    new JSONObject();

            object.put(
                    "id",
                    item.getId()
            );

            object.put(
                    "transactionId",
                    item.getTransactionId()
            );

            object.put(
                    "itemName",
                    item.getItemName()
            );

            object.put(
                    "quantity",
                    item.getQuantity()
            );

            object.put(
                    "unit",
                    item.getUnit()
            );

            object.put(
                    "price",
                    item.getPrice()
            );

            object.put(
                    "total",
                    item.getTotal()
            );

            object.put(
                    "sortOrder",
                    item.getSortOrder()
            );

            array.put(
                    object
            );
        }

        return array;
    }

    @NonNull
    private JSONArray createCategoryArray(
            @NonNull List<Category> categories
    ) throws Exception {

        JSONArray array =
                new JSONArray();

        for (Category category :
                categories) {

            JSONObject object =
                    new JSONObject();

            object.put(
                    "id",
                    category.getId()
            );

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

            array.put(
                    object
            );
        }

        return array;
    }

    @NonNull
    private JSONArray createAccountArray(
            @NonNull List<Account> accounts
    ) throws Exception {

        JSONArray array =
                new JSONArray();

        for (Account account :
                accounts) {

            JSONObject object =
                    new JSONObject();

            object.put(
                    "id",
                    account.getId()
            );

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

            array.put(
                    object
            );
        }

        return array;
    }

    @NonNull
    private JSONArray createGoalArray(
            @NonNull List<Goal> goals
    ) throws Exception {

        JSONArray array =
                new JSONArray();

        for (Goal goal :
                goals) {

            JSONObject object =
                    new JSONObject();

            object.put(
                    "id",
                    goal.getId()
            );

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

            array.put(
                    object
            );
        }

        return array;
    }

    @NonNull
    private JSONArray createRecurringTransactionArray(
            @NonNull List<RecurringTransaction> recurringTransactions
    ) throws Exception {

        JSONArray array =
                new JSONArray();

        for (RecurringTransaction recurring :
                recurringTransactions) {

            JSONObject object =
                    new JSONObject();

            object.put(
                    "id",
                    recurring.getId()
            );

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

            array.put(
                    object
            );
        }

        return array;
    }

    @NonNull
    private JSONArray createBudgetArray(
            @NonNull List<Budget> budgets
    ) throws Exception {

        JSONArray array =
                new JSONArray();

        for (Budget budget :
                budgets) {

            JSONObject object =
                    new JSONObject();

            object.put(
                    "id",
                    budget.getId()
            );

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

            array.put(
                    object
            );
        }

        return array;
    }

    @NonNull
    private JSONArray createLoanArray(
            @NonNull List<Loan> loans
    ) throws Exception {

        JSONArray array =
                new JSONArray();

        for (Loan loan :
                loans) {

            JSONObject object =
                    new JSONObject();

            object.put(
                    "id",
                    loan.getId()
            );

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

            object.put(
                    "startDate",
                    loan.getStartDate()
            );

            object.put(
                    "tenureMonths",
                    loan.getTenureMonths()
            );

            object.put(
                    "historicalPaidAmount",
                    loan.getHistoricalPaidAmount()
            );

            object.put(
                    "historicalInstallments",
                    loan.getHistoricalInstallments()
            );

            array.put(
                    object
            );
        }

        return array;
    }

    @NonNull
    private JSONArray createLoanPaymentArray(
            @NonNull List<LoanPayment> loanPayments
    ) throws Exception {

        JSONArray array =
                new JSONArray();

        for (LoanPayment payment :
                loanPayments) {

            JSONObject object =
                    new JSONObject();

            object.put(
                    "id",
                    payment.getId()
            );

            object.put(
                    "loanId",
                    payment.getLoanId()
            );

            object.put(
                    "amount",
                    payment.getAmount()
            );

            object.put(
                    "paymentType",
                    payment.getPaymentType()
            );

            object.put(
                    "account",
                    payment.getAccount()
            );

            object.put(
                    "paymentDate",
                    payment.getPaymentDate()
            );

            object.put(
                    "note",
                    payment.getNote()
            );

            array.put(
                    object
            );
        }

        return array;
    }

    @NonNull
    private JSONArray createSubscriptionArray(
            @NonNull List<Subscription> subscriptions
    ) throws Exception {

        JSONArray array =
                new JSONArray();

        for (Subscription subscription :
                subscriptions) {

            JSONObject object =
                    new JSONObject();

            object.put(
                    "id",
                    subscription.getId()
            );

            object.put(
                    "name",
                    subscription.getName()
            );

            object.put(
                    "amount",
                    subscription.getAmount()
            );

            object.put(
                    "billingCycle",
                    subscription.getBillingCycle()
            );

            object.put(
                    "nextDueDate",
                    subscription.getNextDueDate()
            );

            object.put(
                    "account",
                    subscription.getAccount()
            );

            object.put(
                    "category",
                    subscription.getCategory()
            );

            object.put(
                    "remindDays",
                    subscription.getRemindDays()
            );

            object.put(
                    "note",
                    subscription.getNote()
            );

            object.put(
                    "active",
                    subscription.isActive()
            );

            array.put(
                    object
            );
        }

        return array;
    }

    @NonNull
    private JSONArray createCreditCardArray(
            @NonNull List<CreditCard> creditCards
    ) throws Exception {

        JSONArray array =
                new JSONArray();

        for (CreditCard card :
                creditCards) {

            JSONObject object =
                    new JSONObject();

            object.put(
                    "id",
                    card.getId()
            );

            object.put(
                    "name",
                    card.getName()
            );

            object.put(
                    "lastFour",
                    card.getLastFour()
            );

            object.put(
                    "accountName",
                    card.getAccountName()
            );

            object.put(
                    "creditLimit",
                    card.getCreditLimit()
            );

            object.put(
                    "billingDay",
                    card.getBillingDay()
            );

            object.put(
                    "dueDay",
                    card.getDueDay()
            );

            object.put(
                    "paymentAccount",
                    card.getPaymentAccount()
            );

            object.put(
                    "reminderDays",
                    card.getReminderDays()
            );

            object.put(
                    "active",
                    card.isActive()
            );

            array.put(
                    object
            );
        }

        return array;
    }

    @NonNull
    private JSONArray createCreditCardPaymentArray(
            @NonNull List<CreditCardPayment> creditCardPayments
    ) throws Exception {

        JSONArray array =
                new JSONArray();

        for (CreditCardPayment payment :
                creditCardPayments) {

            JSONObject object =
                    new JSONObject();

            object.put(
                    "id",
                    payment.getId()
            );

            object.put(
                    "creditCardId",
                    payment.getCreditCardId()
            );

            object.put(
                    "statementEndDate",
                    payment.getStatementEndDate()
            );

            object.put(
                    "amount",
                    payment.getAmount()
            );

            object.put(
                    "paymentDate",
                    payment.getPaymentDate()
            );

            object.put(
                    "sourceAccount",
                    payment.getSourceAccount()
            );

            object.put(
                    "note",
                    payment.getNote()
            );

            array.put(
                    object
            );
        }

        return array;
    }

    @NonNull
    private JSONArray createInvestmentArray(
            @NonNull List<InvestmentItem> investments
    ) throws Exception {

        JSONArray array =
                new JSONArray();

        Set<String> usedIds =
                new HashSet<>();

        for (InvestmentItem investment :
                investments) {

            String investmentId =
                    safeString(
                            investment.getId()
                    ).trim();

            if (investmentId.isEmpty()) {
                investmentId =
                        UUID.randomUUID()
                                .toString();
            }

            if (!usedIds.add(
                    investmentId
            )) {
                throw new BackupValidationException(
                        "Duplicate investment ID was found "
                                + "while building the backup."
                );
            }

            JSONObject object =
                    new JSONObject();

            object.put(
                    "id",
                    investmentId
            );

            object.put(
                    "name",
                    investment.getName()
            );

            object.put(
                    "type",
                    investment.getType()
            );

            object.put(
                    "startDate",
                    investment.getStartDate()
            );

            object.put(
                    "note",
                    investment.getNote()
            );

            object.put(
                    "investedAmount",
                    investment.getInvestedAmount()
            );

            object.put(
                    "currentValue",
                    investment.getCurrentValue()
            );

            object.put(
                    "monthlyContribution",
                    investment.getMonthlyContribution()
            );

            object.put(
                    "createdAt",
                    investment.getCreatedAt()
            );

            array.put(
                    object
            );
        }

        return array;
    }

    private void normalizeSnapshotLists(
            @NonNull BackupSnapshot snapshot
    ) {
        if (snapshot.transactions == null) {
            snapshot.transactions =
                    new ArrayList<>();
        }

        if (snapshot.expenseItems == null) {
            snapshot.expenseItems =
                    new ArrayList<>();
        }

        if (snapshot.categories == null) {
            snapshot.categories =
                    new ArrayList<>();
        }

        if (snapshot.accounts == null) {
            snapshot.accounts =
                    new ArrayList<>();
        }

        if (snapshot.goals == null) {
            snapshot.goals =
                    new ArrayList<>();
        }

        if (snapshot.recurringTransactions == null) {
            snapshot.recurringTransactions =
                    new ArrayList<>();
        }

        if (snapshot.budgets == null) {
            snapshot.budgets =
                    new ArrayList<>();
        }

        if (snapshot.loans == null) {
            snapshot.loans =
                    new ArrayList<>();
        }

        if (snapshot.loanPayments == null) {
            snapshot.loanPayments =
                    new ArrayList<>();
        }

        if (snapshot.subscriptions == null) {
            snapshot.subscriptions =
                    new ArrayList<>();
        }

        if (snapshot.creditCards == null) {
            snapshot.creditCards =
                    new ArrayList<>();
        }

        if (snapshot.creditCardPayments == null) {
            snapshot.creditCardPayments =
                    new ArrayList<>();
        }

        if (snapshot.investments == null) {
            snapshot.investments =
                    new ArrayList<>();
        }
    }

    @NonNull
    private AppVersionInformation readAppVersionInformation() {
        try {
            PackageInfo packageInfo =
                    applicationContext
                            .getPackageManager()
                            .getPackageInfo(
                                    applicationContext
                                            .getPackageName(),
                                    0
                            );

            long versionCode;

            if (android.os.Build.VERSION.SDK_INT
                    >= android.os.Build.VERSION_CODES.P) {

                versionCode =
                        packageInfo
                                .getLongVersionCode();

            } else {
                versionCode =
                        packageInfo.versionCode;
            }

            String versionName =
                    packageInfo.versionName;

            if (versionName == null
                    || versionName.trim().isEmpty()) {

                versionName =
                        "Unknown";
            }

            return new AppVersionInformation(
                    versionName,
                    versionCode
            );

        } catch (Exception exception) {
            return new AppVersionInformation(
                    "Unknown",
                    0L
            );
        }
    }

    @NonNull
    private String formatDateTime(
            long timestamp
    ) {
        return new SimpleDateFormat(
                "dd MMM yyyy, hh:mm a",
                Locale.getDefault()
        ).format(
                new Date(
                        timestamp
                )
        );
    }

    @NonNull
    private static String safeString(
            @Nullable String value
    ) {
        return value == null
                ? ""
                : value;
    }

    @NonNull
    private static String usefulMessage(
            @NonNull Throwable throwable,
            @NonNull String fallback
    ) {
        Throwable current =
                throwable;

        String useful =
                "";

        int inspectedCauses =
                0;

        while (current != null
                && inspectedCauses < 12) {

            String message =
                    current.getMessage();

            if (message != null
                    && !message.trim().isEmpty()) {

                useful =
                        message.trim();
            }

            current =
                    current.getCause();

            inspectedCauses++;
        }

        if (useful.isEmpty()) {
            return fallback;
        }

        return useful;
    }

    private static final class AppVersionInformation {

        private final String versionName;

        private final long versionCode;

        private AppVersionInformation(
                @NonNull String versionName,
                long versionCode
        ) {
            this.versionName =
                    versionName;

            this.versionCode =
                    versionCode;
        }
    }

    private static final class BackupSnapshot {

        private List<Transaction> transactions =
                new ArrayList<>();

        private List<ExpenseItem> expenseItems =
                new ArrayList<>();

        private List<Category> categories =
                new ArrayList<>();

        private List<Account> accounts =
                new ArrayList<>();

        private List<Goal> goals =
                new ArrayList<>();

        private List<RecurringTransaction> recurringTransactions =
                new ArrayList<>();

        private List<Budget> budgets =
                new ArrayList<>();

        private List<Loan> loans =
                new ArrayList<>();

        private List<LoanPayment> loanPayments =
                new ArrayList<>();

        private List<Subscription> subscriptions =
                new ArrayList<>();

        private List<CreditCard> creditCards =
                new ArrayList<>();

        private List<CreditCardPayment> creditCardPayments =
                new ArrayList<>();

        private List<InvestmentItem> investments =
                new ArrayList<>();
    }

    /**
     * Immutable record-count information.
     */
    public static final class RecordCounts {

        private final int transactions;
        private final int expenseItems;
        private final int categories;
        private final int accounts;
        private final int goals;
        private final int recurringTransactions;
        private final int budgets;
        private final int loans;
        private final int loanPayments;
        private final int subscriptions;
        private final int creditCards;
        private final int creditCardPayments;
        private final int investments;
        private final int totalRecords;

        private RecordCounts(
                int transactions,
                int expenseItems,
                int categories,
                int accounts,
                int goals,
                int recurringTransactions,
                int budgets,
                int loans,
                int loanPayments,
                int subscriptions,
                int creditCards,
                int creditCardPayments,
                int investments
        ) {
            this.transactions =
                    transactions;

            this.expenseItems =
                    expenseItems;

            this.categories =
                    categories;

            this.accounts =
                    accounts;

            this.goals =
                    goals;

            this.recurringTransactions =
                    recurringTransactions;

            this.budgets =
                    budgets;

            this.loans =
                    loans;

            this.loanPayments =
                    loanPayments;

            this.subscriptions =
                    subscriptions;

            this.creditCards =
                    creditCards;

            this.creditCardPayments =
                    creditCardPayments;

            this.investments =
                    investments;

            this.totalRecords =
                    transactions
                            + expenseItems
                            + categories
                            + accounts
                            + goals
                            + recurringTransactions
                            + budgets
                            + loans
                            + loanPayments
                            + subscriptions
                            + creditCards
                            + creditCardPayments
                            + investments;
        }

        @NonNull
        private JSONObject toJson()
                throws Exception {

            JSONObject object =
                    new JSONObject();

            object.put(
                    "transactions",
                    transactions
            );

            object.put(
                    "expenseItems",
                    expenseItems
            );

            object.put(
                    "categories",
                    categories
            );

            object.put(
                    "accounts",
                    accounts
            );

            object.put(
                    "goals",
                    goals
            );

            object.put(
                    "recurringTransactions",
                    recurringTransactions
            );

            object.put(
                    "budgets",
                    budgets
            );

            object.put(
                    "loans",
                    loans
            );

            object.put(
                    "loanPayments",
                    loanPayments
            );

            object.put(
                    "subscriptions",
                    subscriptions
            );

            object.put(
                    "creditCards",
                    creditCards
            );

            object.put(
                    "creditCardPayments",
                    creditCardPayments
            );

            object.put(
                    "investments",
                    investments
            );

            object.put(
                    "totalRecords",
                    totalRecords
            );

            return object;
        }

        @NonNull
        private static RecordCounts fromJson(
                @NonNull JSONObject object
        ) throws BackupValidationException {

            int transactions =
                    requireCount(
                            object,
                            "transactions"
                    );

            int expenseItems =
                    requireCount(
                            object,
                            "expenseItems"
                    );

            int categories =
                    requireCount(
                            object,
                            "categories"
                    );

            int accounts =
                    requireCount(
                            object,
                            "accounts"
                    );

            int goals =
                    requireCount(
                            object,
                            "goals"
                    );

            int recurringTransactions =
                    requireCount(
                            object,
                            "recurringTransactions"
                    );

            int budgets =
                    requireCount(
                            object,
                            "budgets"
                    );

            int loans =
                    requireCount(
                            object,
                            "loans"
                    );

            int loanPayments =
                    requireCount(
                            object,
                            "loanPayments"
                    );

            int subscriptions =
                    requireCount(
                            object,
                            "subscriptions"
                    );

            int creditCards =
                    requireCount(
                            object,
                            "creditCards"
                    );

            int creditCardPayments =
                    requireCount(
                            object,
                            "creditCardPayments"
                    );

            int investments =
                    requireCount(
                            object,
                            "investments"
                    );

            RecordCounts counts =
                    new RecordCounts(
                            transactions,
                            expenseItems,
                            categories,
                            accounts,
                            goals,
                            recurringTransactions,
                            budgets,
                            loans,
                            loanPayments,
                            subscriptions,
                            creditCards,
                            creditCardPayments,
                            investments
                    );

            int declaredTotal =
                    requireCount(
                            object,
                            "totalRecords"
                    );

            if (counts.totalRecords
                    != declaredTotal) {

                throw new BackupValidationException(
                        "Offline backup total record count is invalid."
                );
            }

            return counts;
        }

        private static int requireCount(
                @NonNull JSONObject object,
                @NonNull String key
        ) throws BackupValidationException {

            if (!object.has(
                    key
            )
                    || object.isNull(
                    key
            )) {

                throw new BackupValidationException(
                        "Backup record count is missing: "
                                + key
                );
            }

            long value =
                    object.optLong(
                            key,
                            -1L
                    );

            if (value < 0L
                    || value > Integer.MAX_VALUE) {

                throw new BackupValidationException(
                        "Backup record count is invalid: "
                                + key
                );
            }

            return (int) value;
        }

        public int getTransactions() {
            return transactions;
        }

        public int getExpenseItems() {
            return expenseItems;
        }

        public int getCategories() {
            return categories;
        }

        public int getAccounts() {
            return accounts;
        }

        public int getGoals() {
            return goals;
        }

        public int getRecurringTransactions() {
            return recurringTransactions;
        }

        public int getBudgets() {
            return budgets;
        }

        public int getLoans() {
            return loans;
        }

        public int getLoanPayments() {
            return loanPayments;
        }

        public int getSubscriptions() {
            return subscriptions;
        }

        public int getCreditCards() {
            return creditCards;
        }

        public int getCreditCardPayments() {
            return creditCardPayments;
        }

        public int getInvestments() {
            return investments;
        }

        public int getTotalRecords() {
            return totalRecords;
        }
    }

    /**
     * Immutable result returned after the final backup is committed.
     */
    public static final class BackupResult {

        private final String backupId;
        private final long createdAtMillis;
        private final String createdAtText;
        private final int databaseVersion;
        private final String appVersionName;
        private final long appVersionCode;
        private final RecordCounts recordCounts;
        private final long backupByteCount;
        private final Uri backupUri;
        private final String backupLocationLabel;
        private final String integritySha256;

        private BackupResult(
                @NonNull String backupId,
                long createdAtMillis,
                @NonNull String createdAtText,
                int databaseVersion,
                @NonNull String appVersionName,
                long appVersionCode,
                @NonNull RecordCounts recordCounts,
                long backupByteCount,
                @NonNull Uri backupUri,
                @NonNull String backupLocationLabel,
                @NonNull String integritySha256
        ) {
            this.backupId =
                    backupId;

            this.createdAtMillis =
                    createdAtMillis;

            this.createdAtText =
                    createdAtText;

            this.databaseVersion =
                    databaseVersion;

            this.appVersionName =
                    appVersionName;

            this.appVersionCode =
                    appVersionCode;

            this.recordCounts =
                    recordCounts;

            this.backupByteCount =
                    Math.max(
                            0L,
                            backupByteCount
                    );

            this.backupUri =
                    backupUri;

            this.backupLocationLabel =
                    backupLocationLabel;

            this.integritySha256 =
                    integritySha256;
        }

        @NonNull
        public String getBackupId() {
            return backupId;
        }

        public long getCreatedAtMillis() {
            return createdAtMillis;
        }

        @NonNull
        public String getCreatedAtText() {
            return createdAtText;
        }

        public int getDatabaseVersion() {
            return databaseVersion;
        }

        @NonNull
        public String getAppVersionName() {
            return appVersionName;
        }

        public long getAppVersionCode() {
            return appVersionCode;
        }

        @NonNull
        public RecordCounts getRecordCounts() {
            return recordCounts;
        }

        public long getBackupByteCount() {
            return backupByteCount;
        }

        @NonNull
        public Uri getBackupUri() {
            return backupUri;
        }

        @NonNull
        public String getBackupLocationLabel() {
            return backupLocationLabel;
        }

        @NonNull
        public String getIntegritySha256() {
            return integritySha256;
        }
    }

    /**
     * Immutable information returned when a backup is inspected.
     */
    public static final class ValidationResult {

        private final String backupId;
        private final long createdAtMillis;
        private final String createdAtText;
        private final int databaseVersion;
        private final String appVersionName;
        private final long appVersionCode;
        private final RecordCounts recordCounts;
        private final String integritySha256;

        private ValidationResult(
                @NonNull String backupId,
                long createdAtMillis,
                @NonNull String createdAtText,
                int databaseVersion,
                @NonNull String appVersionName,
                long appVersionCode,
                @NonNull RecordCounts recordCounts,
                @NonNull String integritySha256
        ) {
            this.backupId =
                    backupId;

            this.createdAtMillis =
                    createdAtMillis;

            this.createdAtText =
                    createdAtText;

            this.databaseVersion =
                    databaseVersion;

            this.appVersionName =
                    appVersionName;

            this.appVersionCode =
                    appVersionCode;

            this.recordCounts =
                    recordCounts;

            this.integritySha256 =
                    integritySha256;
        }

        @NonNull
        public String getBackupId() {
            return backupId;
        }

        public long getCreatedAtMillis() {
            return createdAtMillis;
        }

        @NonNull
        public String getCreatedAtText() {
            return createdAtText;
        }

        public int getDatabaseVersion() {
            return databaseVersion;
        }

        @NonNull
        public String getAppVersionName() {
            return appVersionName;
        }

        public long getAppVersionCode() {
            return appVersionCode;
        }

        @NonNull
        public RecordCounts getRecordCounts() {
            return recordCounts;
        }

        @NonNull
        public String getIntegritySha256() {
            return integritySha256;
        }
    }

    public static class OfflineBackupException
            extends Exception {

        private final String stage;

        public OfflineBackupException(
                @NonNull String stage,
                @NonNull String message
        ) {
            super(
                    message
            );

            this.stage =
                    stage;
        }

        public OfflineBackupException(
                @NonNull String stage,
                @NonNull String message,
                @NonNull Throwable cause
        ) {
            super(
                    message,
                    cause
            );

            this.stage =
                    stage;
        }

        @NonNull
        public String getStage() {
            return stage;
        }
    }

    public static final class BackupFolderUnavailableException
            extends OfflineBackupException {

        public BackupFolderUnavailableException(
                @NonNull String message
        ) {
            super(
                    "Checking backup folder",
                    message
            );
        }
    }

    public static final class BackupValidationException
            extends OfflineBackupException {

        public BackupValidationException(
                @NonNull String message
        ) {
            super(
                    "Validating offline backup",
                    message
            );
        }

        public BackupValidationException(
                @NonNull String message,
                @NonNull Throwable cause
        ) {
            super(
                    "Validating offline backup",
                    message,
                    cause
            );
        }
    }
}