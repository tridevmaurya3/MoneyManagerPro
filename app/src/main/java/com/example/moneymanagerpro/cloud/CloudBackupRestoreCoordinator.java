package com.example.moneymanagerpro.cloud;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.moneymanagerpro.database.AppDatabase;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Validates and restores a verified decrypted Money Manager Pro
 * encrypted cloud backup.
 *
 * Supported payload structures:
 *
 * Current format:
 *
 * {
 *   "backup_format": "...",
 *   "backup_format_version": 1,
 *   "backup_type": "...",
 *   "package_name": "...",
 *   "database_version": 11,
 *   "backup_id": "...",
 *   "firebase_user_id": "...",
 *   "record_counts": {...},
 *   "data": {
 *       "transactions": [...],
 *       "expense_items": [...],
 *       "categories": [...],
 *       "accounts": [...],
 *       "goals": [...],
 *       "recurring_transactions": [...],
 *       "budgets": [...],
 *       "loans": [...],
 *       "loan_payments": [...],
 *       "subscriptions": [...],
 *       "credit_cards": [...],
 *       "credit_card_payments": [...],
 *       "investments": [...]
 *   }
 * }
 *
 * Legacy format:
 *
 * - Root-level arrays
 * - camelCase field names
 *
 * Restore safety:
 *
 * 1. Firebase account ownership is checked.
 * 2. Backup metadata and database version are checked.
 * 3. Record counts are checked.
 * 4. Duplicate primary keys are rejected.
 * 5. Child-to-parent references are verified.
 * 6. Room records are replaced inside one transaction.
 * 7. Investment SharedPreferences are rolled back if Room fails.
 * 8. Malformed data never reaches the database.
 */
public final class CloudBackupRestoreCoordinator {

    public static final String RESTORE_MODE_REPLACE_ALL =
            "REPLACE_ALL";

    private static final String APP_PACKAGE =
            "com.example.moneymanagerpro";

    private static final int MAX_TOTAL_RECORDS =
            250_000;

    private static final int MAX_TEXT_LENGTH =
            20_000;

    private static final int MAX_INVESTMENT_ID_LENGTH =
            200;

    private static final String INVESTMENT_PREFERENCES_NAME =
            "investment_tracker_storage";

    private static final String INVESTMENT_PREFERENCES_KEY =
            "saved_investments";

    private static final ExecutorService RESTORE_EXECUTOR =
            Executors.newSingleThreadExecutor();

    private static final Handler MAIN_HANDLER =
            new Handler(
                    Looper.getMainLooper()
            );

    private final Context applicationContext;

    public CloudBackupRestoreCoordinator(
            @NonNull Context context
    ) {
        applicationContext =
                context.getApplicationContext();
    }

    /**
     * Inspects and validates a decrypted cloud backup without changing
     * local Room data.
     */
    public void inspectBackup(
            @NonNull FirebaseUser firebaseUser,
            @NonNull EncryptedCloudBackupDownloader
                    .DecryptedCloudBackup backup,
            @NonNull PreviewCallback callback
    ) {
        Exception accountError =
                validateFirebaseUser(
                        firebaseUser,
                        backup
                );

        if (accountError != null) {
            callback.onError(
                    accountError
            );

            return;
        }

        byte[] jsonBytes =
                backup.getJsonBytes();

        try {
            RESTORE_EXECUTOR.execute(
                    () -> {
                        try {
                            PreparedRestore preparedRestore =
                                    prepareRestore(
                                            firebaseUser,
                                            backup,
                                            jsonBytes
                                    );

                            RestorePreview preview =
                                    createPreview(
                                            backup,
                                            preparedRestore
                                    );

                            MAIN_HANDLER.post(
                                    () -> callback.onLoaded(
                                            preview
                                    )
                            );

                        } catch (Exception exception) {
                            MAIN_HANDLER.post(
                                    () -> callback.onError(
                                            asException(
                                                    exception
                                            )
                                    )
                            );

                        } finally {
                            clearBytes(
                                    jsonBytes
                            );
                        }
                    }
            );

        } catch (RejectedExecutionException exception) {
            clearBytes(
                    jsonBytes
            );

            callback.onError(
                    new RestoreException(
                            "Cloud backup inspection could not be started.",
                            exception
                    )
            );
        }
    }

    /**
     * Replaces all supported local finance records with records from
     * the already downloaded, decrypted and verified cloud backup.
     */
    public void restoreReplaceAll(
            @NonNull FirebaseUser firebaseUser,
            @NonNull EncryptedCloudBackupDownloader
                    .DecryptedCloudBackup backup,
            @NonNull RestoreCallback callback
    ) {
        Exception accountError =
                validateFirebaseUser(
                        firebaseUser,
                        backup
                );

        if (accountError != null) {
            callback.onError(
                    accountError
            );

            return;
        }

        byte[] jsonBytes =
                backup.getJsonBytes();

        try {
            RESTORE_EXECUTOR.execute(
                    () -> {
                        try {
                            PreparedRestore preparedRestore =
                                    prepareRestore(
                                            firebaseUser,
                                            backup,
                                            jsonBytes
                                    );

                            performRestore(
                                    preparedRestore
                            );

                            RestoreResult result =
                                    new RestoreResult(
                                            preparedRestore.backupId,
                                            preparedRestore.backupCreatedAt,
                                            System.currentTimeMillis(),
                                            preparedRestore.totalRecordCount,
                                            preparedRestore.transactions.size(),
                                            preparedRestore.expenseItems.size(),
                                            preparedRestore.accounts.size(),
                                            preparedRestore.categories.size(),
                                            preparedRestore.investments.length()
                                    );

                            MAIN_HANDLER.post(
                                    () -> callback.onSuccess(
                                            result
                                    )
                            );

                        } catch (Exception exception) {
                            MAIN_HANDLER.post(
                                    () -> callback.onError(
                                            asException(
                                                    exception
                                            )
                                    )
                            );

                        } finally {
                            clearBytes(
                                    jsonBytes
                            );
                        }
                    }
            );

        } catch (RejectedExecutionException exception) {
            clearBytes(
                    jsonBytes
            );

            callback.onError(
                    new RestoreException(
                            "Cloud backup restore could not be started.",
                            exception
                    )
            );
        }
    }

    @NonNull
    private PreparedRestore prepareRestore(
            @NonNull FirebaseUser firebaseUser,
            @NonNull EncryptedCloudBackupDownloader
                    .DecryptedCloudBackup backup,
            @NonNull byte[] jsonBytes
    ) throws RestoreException {

        if (jsonBytes.length <= 0) {
            throw new RestoreValidationException(
                    "Decrypted cloud backup is empty."
            );
        }

        try {
            String jsonText =
                    new String(
                            jsonBytes,
                            StandardCharsets.UTF_8
                    );

            JSONObject root =
                    new JSONObject(
                            jsonText
                    );

            validateBackupRoot(
                    firebaseUser,
                    backup,
                    root
            );

            JSONObject recordCounts =
                    requireObject(
                            root,
                            "record_counts",
                            "recordCounts"
                    );

            /*
             * New payload keeps all table arrays inside data.
             * Legacy payload keeps them at the root.
             */
            JSONObject dataObject =
                    root.optJSONObject(
                            "data"
                    );

            JSONObject tableContainer =
                    dataObject == null
                            ? root
                            : dataObject;

            JSONArray transactionsArray =
                    requireArray(
                            tableContainer,
                            "transactions"
                    );

            JSONArray expenseItemsArray =
                    requireArray(
                            tableContainer,
                            "expense_items",
                            "expenseItems"
                    );

            JSONArray categoriesArray =
                    requireArray(
                            tableContainer,
                            "categories"
                    );

            JSONArray accountsArray =
                    requireArray(
                            tableContainer,
                            "accounts"
                    );

            JSONArray goalsArray =
                    requireArray(
                            tableContainer,
                            "goals"
                    );

            JSONArray recurringArray =
                    requireArray(
                            tableContainer,
                            "recurring_transactions",
                            "recurringTransactions"
                    );

            JSONArray budgetsArray =
                    requireArray(
                            tableContainer,
                            "budgets"
                    );

            JSONArray loansArray =
                    requireArray(
                            tableContainer,
                            "loans"
                    );

            JSONArray loanPaymentsArray =
                    requireArray(
                            tableContainer,
                            "loan_payments",
                            "loanPayments"
                    );

            JSONArray subscriptionsArray =
                    requireArray(
                            tableContainer,
                            "subscriptions"
                    );

            JSONArray creditCardsArray =
                    requireArray(
                            tableContainer,
                            "credit_cards",
                            "creditCards"
                    );

            JSONArray creditCardPaymentsArray =
                    requireArray(
                            tableContainer,
                            "credit_card_payments",
                            "creditCardPayments"
                    );

            JSONArray investmentsArray =
                    requireArray(
                            tableContainer,
                            "investments"
                    );

            verifyDeclaredCount(
                    recordCounts,
                    transactionsArray.length(),
                    "transactions"
            );

            verifyDeclaredCount(
                    recordCounts,
                    expenseItemsArray.length(),
                    "expense_items",
                    "expenseItems"
            );

            verifyDeclaredCount(
                    recordCounts,
                    categoriesArray.length(),
                    "categories"
            );

            verifyDeclaredCount(
                    recordCounts,
                    accountsArray.length(),
                    "accounts"
            );

            verifyDeclaredCount(
                    recordCounts,
                    goalsArray.length(),
                    "goals"
            );

            verifyDeclaredCount(
                    recordCounts,
                    recurringArray.length(),
                    "recurring_transactions",
                    "recurringTransactions"
            );

            verifyDeclaredCount(
                    recordCounts,
                    budgetsArray.length(),
                    "budgets"
            );

            verifyDeclaredCount(
                    recordCounts,
                    loansArray.length(),
                    "loans"
            );

            verifyDeclaredCount(
                    recordCounts,
                    loanPaymentsArray.length(),
                    "loan_payments",
                    "loanPayments"
            );

            verifyDeclaredCount(
                    recordCounts,
                    subscriptionsArray.length(),
                    "subscriptions"
            );

            verifyDeclaredCount(
                    recordCounts,
                    creditCardsArray.length(),
                    "credit_cards",
                    "creditCards"
            );

            verifyDeclaredCount(
                    recordCounts,
                    creditCardPaymentsArray.length(),
                    "credit_card_payments",
                    "creditCardPayments"
            );

            verifyDeclaredCount(
                    recordCounts,
                    investmentsArray.length(),
                    "investments"
            );

            int calculatedTotal =
                    transactionsArray.length()
                            + expenseItemsArray.length()
                            + categoriesArray.length()
                            + accountsArray.length()
                            + goalsArray.length()
                            + recurringArray.length()
                            + budgetsArray.length()
                            + loansArray.length()
                            + loanPaymentsArray.length()
                            + subscriptionsArray.length()
                            + creditCardsArray.length()
                            + creditCardPaymentsArray.length()
                            + investmentsArray.length();

            int declaredTotal =
                    requireNonNegativeInt(
                            recordCounts,
                            "total_records",
                            "totalRecords"
                    );

            if (calculatedTotal != declaredTotal) {
                throw new RestoreValidationException(
                        "Cloud backup total record count verification failed."
                );
            }

            if (calculatedTotal
                    != backup.getVerifiedRecordCount()) {

                throw new RestoreValidationException(
                        "Cloud backup verified record count "
                                + "does not match its decrypted payload."
                );
            }

            if (calculatedTotal > MAX_TOTAL_RECORDS) {
                throw new RestoreValidationException(
                        "Cloud backup contains more records than supported."
                );
            }

            PreparedRestore preparedRestore =
                    new PreparedRestore();

            preparedRestore.backupId =
                    backup.getBackupId();

            preparedRestore.backupCreatedAt =
                    backup.getBackupCreatedAt();

            preparedRestore.databaseVersion =
                    requirePositiveInt(
                            root,
                            "database_version",
                            "databaseVersion"
                    );

            preparedRestore.appVersionName =
                    optionalString(
                            root,
                            "app_version_name",
                            "appVersionName"
                    );

            preparedRestore.appVersionCode =
                    optionalLong(
                            root,
                            0L,
                            "app_version_code",
                            "appVersionCode"
                    );

            preparedRestore.totalRecordCount =
                    calculatedTotal;

            Set<Integer> transactionIds =
                    new HashSet<>();

            Set<Integer> categoryIds =
                    new HashSet<>();

            Set<Integer> accountIds =
                    new HashSet<>();

            Set<Integer> goalIds =
                    new HashSet<>();

            Set<Integer> recurringIds =
                    new HashSet<>();

            Set<Integer> budgetIds =
                    new HashSet<>();

            Set<Integer> loanIds =
                    new HashSet<>();

            Set<Integer> loanPaymentIds =
                    new HashSet<>();

            Set<Integer> subscriptionIds =
                    new HashSet<>();

            Set<Integer> creditCardIds =
                    new HashSet<>();

            Set<Integer> creditCardPaymentIds =
                    new HashSet<>();

            Set<Integer> expenseItemIds =
                    new HashSet<>();

            preparedRestore.transactions =
                    prepareTransactions(
                            transactionsArray,
                            transactionIds
                    );

            preparedRestore.categories =
                    prepareCategories(
                            categoriesArray,
                            categoryIds
                    );

            preparedRestore.accounts =
                    prepareAccounts(
                            accountsArray,
                            accountIds
                    );

            preparedRestore.goals =
                    prepareGoals(
                            goalsArray,
                            goalIds
                    );

            preparedRestore.recurringTransactions =
                    prepareRecurringTransactions(
                            recurringArray,
                            recurringIds
                    );

            preparedRestore.budgets =
                    prepareBudgets(
                            budgetsArray,
                            budgetIds
                    );

            preparedRestore.loans =
                    prepareLoans(
                            loansArray,
                            loanIds
                    );

            preparedRestore.loanPayments =
                    prepareLoanPayments(
                            loanPaymentsArray,
                            loanPaymentIds,
                            loanIds
                    );

            preparedRestore.subscriptions =
                    prepareSubscriptions(
                            subscriptionsArray,
                            subscriptionIds
                    );

            preparedRestore.creditCards =
                    prepareCreditCards(
                            creditCardsArray,
                            creditCardIds
                    );

            preparedRestore.creditCardPayments =
                    prepareCreditCardPayments(
                            creditCardPaymentsArray,
                            creditCardPaymentIds,
                            creditCardIds
                    );

            preparedRestore.expenseItems =
                    prepareExpenseItems(
                            expenseItemsArray,
                            expenseItemIds,
                            transactionIds
                    );

            preparedRestore.investments =
                    prepareInvestments(
                            investmentsArray
                    );

            validateCurrentDatabaseCompatibility(
                    preparedRestore.databaseVersion
            );

            return preparedRestore;

        } catch (RestoreException exception) {
            throw exception;

        } catch (Exception exception) {
            throw new RestoreValidationException(
                    "Cloud backup restore data is invalid.",
                    exception
            );
        }
    }

    private void validateBackupRoot(
            @NonNull FirebaseUser firebaseUser,
            @NonNull EncryptedCloudBackupDownloader
                    .DecryptedCloudBackup backup,
            @NonNull JSONObject root
    ) throws RestoreException {

        String backupType =
                optionalString(
                        root,
                        "backup_type",
                        "payloadType"
                );

        String backupFormat =
                optionalString(
                        root,
                        "backup_format"
                );

        boolean supportedType =
                CloudBackupPayloadBuilder
                        .PAYLOAD_TYPE
                        .equals(
                                backupType
                        )
                        || CloudBackupPayloadBuilder
                        .PAYLOAD_TYPE
                        .equals(
                                backupFormat
                        );

        if (!supportedType) {
            throw new RestoreValidationException(
                    "Unsupported Money Manager Pro backup type."
            );
        }

        int payloadVersion =
                requirePositiveInt(
                        root,
                        "backup_format_version",
                        "payloadVersion"
                );

        if (payloadVersion
                != CloudBackupPayloadBuilder.PAYLOAD_VERSION) {

            throw new RestoreValidationException(
                    "Unsupported cloud backup payload version."
            );
        }

        String packageName =
                requireString(
                        root,
                        "package_name",
                        "packageName"
                );

        if (!APP_PACKAGE.equals(
                packageName
        )) {
            throw new RestoreValidationException(
                    "Cloud backup belongs to another application."
            );
        }

        String cloudOwnerUserId =
                requireString(
                        root,
                        "firebase_user_id",
                        "cloudOwnerUid"
                );

        if (!firebaseUser
                .getUid()
                .equals(
                        cloudOwnerUserId
                )) {

            throw new RestoreValidationException(
                    "Cloud backup belongs to another Firebase account."
            );
        }

        if (!backup
                .getFirebaseUserId()
                .equals(
                        cloudOwnerUserId
                )) {

            throw new RestoreValidationException(
                    "Cloud backup account verification failed."
            );
        }

        String backupId =
                requireString(
                        root,
                        "backup_id",
                        "backupId"
                );

        if (!backup
                .getBackupId()
                .equals(
                        backupId
                )) {

            throw new RestoreValidationException(
                    "Cloud backup ID verification failed."
            );
        }

        String compression =
                requireString(
                        root,
                        "compression"
                );

        if (!CloudBackupPayloadBuilder
                .COMPRESSION_TYPE
                .equalsIgnoreCase(
                        compression
                )) {

            throw new RestoreValidationException(
                    "Unsupported cloud backup compression format."
            );
        }

        String hashAlgorithm =
                optionalString(
                        root,
                        "hash_algorithm",
                        "hashAlgorithm"
                );

        if (!hashAlgorithm.isEmpty()) {
            String normalizedAlgorithm =
                    hashAlgorithm
                            .replace(
                                    "-",
                                    ""
                            )
                            .replace(
                                    "_",
                                    ""
                            )
                            .toUpperCase(
                                    Locale.US
                            );

            if (!"SHA256".equals(
                    normalizedAlgorithm
            )) {
                throw new RestoreValidationException(
                        "Unsupported cloud backup hash algorithm."
                );
            }
        }

        int databaseVersion =
                requirePositiveInt(
                        root,
                        "database_version",
                        "databaseVersion"
                );

        if (databaseVersion
                != backup.getDatabaseVersion()) {

            throw new RestoreValidationException(
                    "Cloud backup database version verification failed."
            );
        }
    }

    @NonNull
    private List<Object[]> prepareTransactions(
            @NonNull JSONArray array,
            @NonNull Set<Integer> ids
    ) throws RestoreException {

        List<Object[]> rows =
                new ArrayList<>(
                        array.length()
                );

        for (int index = 0;
             index < array.length();
             index++) {

            JSONObject object =
                    requireArrayObject(
                            array,
                            index,
                            "transaction"
                    );

            rows.add(
                    new Object[]{
                            requireUniquePositiveId(
                                    object,
                                    ids,
                                    "transaction",
                                    "id"
                            ),
                            limitedString(
                                    object,
                                    "type"
                            ),
                            requireFiniteNumber(
                                    object,
                                    "amount"
                            ),
                            limitedString(
                                    object,
                                    "category"
                            ),
                            limitedString(
                                    object,
                                    "account"
                            ),
                            limitedString(
                                    object,
                                    "note"
                            ),
                            limitedString(
                                    object,
                                    "date"
                            )
                    }
            );
        }

        return rows;
    }

    @NonNull
    private List<Object[]> prepareExpenseItems(
            @NonNull JSONArray array,
            @NonNull Set<Integer> ids,
            @NonNull Set<Integer> transactionIds
    ) throws RestoreException {

        List<Object[]> rows =
                new ArrayList<>(
                        array.length()
                );

        for (int index = 0;
             index < array.length();
             index++) {

            JSONObject object =
                    requireArrayObject(
                            array,
                            index,
                            "expense item"
                    );

            int id =
                    requireUniquePositiveId(
                            object,
                            ids,
                            "expense item",
                            "id"
                    );

            int transactionId =
                    requirePositiveInt(
                            object,
                            "transaction_id",
                            "transactionId"
                    );

            if (!transactionIds.contains(
                    transactionId
            )) {
                throw new RestoreValidationException(
                        "Expense item references a missing transaction."
                );
            }

            rows.add(
                    new Object[]{
                            id,
                            transactionId,
                            limitedString(
                                    object,
                                    "item_name",
                                    "itemName"
                            ),
                            requireFiniteNumber(
                                    object,
                                    "quantity"
                            ),
                            limitedString(
                                    object,
                                    "unit"
                            ),
                            requireFiniteNumber(
                                    object,
                                    "price"
                            ),
                            requireFiniteNumber(
                                    object,
                                    "total"
                            ),
                            optionalInt(
                                    object,
                                    0,
                                    "sort_order",
                                    "sortOrder"
                            )
                    }
            );
        }

        return rows;
    }

    @NonNull
    private List<Object[]> prepareCategories(
            @NonNull JSONArray array,
            @NonNull Set<Integer> ids
    ) throws RestoreException {

        List<Object[]> rows =
                new ArrayList<>(
                        array.length()
                );

        for (int index = 0;
             index < array.length();
             index++) {

            JSONObject object =
                    requireArrayObject(
                            array,
                            index,
                            "category"
                    );

            rows.add(
                    new Object[]{
                            requireUniquePositiveId(
                                    object,
                                    ids,
                                    "category",
                                    "id"
                            ),
                            limitedString(
                                    object,
                                    "name"
                            ),
                            limitedString(
                                    object,
                                    "type"
                            ),
                            limitedString(
                                    object,
                                    "color"
                            )
                    }
            );
        }

        return rows;
    }

    @NonNull
    private List<Object[]> prepareAccounts(
            @NonNull JSONArray array,
            @NonNull Set<Integer> ids
    ) throws RestoreException {

        List<Object[]> rows =
                new ArrayList<>(
                        array.length()
                );

        for (int index = 0;
             index < array.length();
             index++) {

            JSONObject object =
                    requireArrayObject(
                            array,
                            index,
                            "account"
                    );

            rows.add(
                    new Object[]{
                            requireUniquePositiveId(
                                    object,
                                    ids,
                                    "account",
                                    "id"
                            ),
                            limitedString(
                                    object,
                                    "name"
                            ),
                            limitedString(
                                    object,
                                    "type"
                            ),
                            requireFiniteNumber(
                                    object,
                                    "opening_balance",
                                    "openingBalance"
                            ),
                            limitedString(
                                    object,
                                    "color"
                            )
                    }
            );
        }

        return rows;
    }

    @NonNull
    private List<Object[]> prepareGoals(
            @NonNull JSONArray array,
            @NonNull Set<Integer> ids
    ) throws RestoreException {

        List<Object[]> rows =
                new ArrayList<>(
                        array.length()
                );

        for (int index = 0;
             index < array.length();
             index++) {

            JSONObject object =
                    requireArrayObject(
                            array,
                            index,
                            "goal"
                    );

            rows.add(
                    new Object[]{
                            requireUniquePositiveId(
                                    object,
                                    ids,
                                    "goal",
                                    "id"
                            ),
                            limitedString(
                                    object,
                                    "name"
                            ),
                            requireFiniteNumber(
                                    object,
                                    "target_amount",
                                    "targetAmount"
                            ),
                            requireFiniteNumber(
                                    object,
                                    "saved_amount",
                                    "savedAmount"
                            ),
                            limitedString(
                                    object,
                                    "target_date",
                                    "targetDate"
                            ),
                            limitedString(
                                    object,
                                    "color"
                            )
                    }
            );
        }

        return rows;
    }

    @NonNull
    private List<Object[]> prepareRecurringTransactions(
            @NonNull JSONArray array,
            @NonNull Set<Integer> ids
    ) throws RestoreException {

        List<Object[]> rows =
                new ArrayList<>(
                        array.length()
                );

        for (int index = 0;
             index < array.length();
             index++) {

            JSONObject object =
                    requireArrayObject(
                            array,
                            index,
                            "recurring transaction"
                    );

            rows.add(
                    new Object[]{
                            requireUniquePositiveId(
                                    object,
                                    ids,
                                    "recurring transaction",
                                    "id"
                            ),
                            limitedString(
                                    object,
                                    "type"
                            ),
                            requireFiniteNumber(
                                    object,
                                    "amount"
                            ),
                            limitedString(
                                    object,
                                    "category"
                            ),
                            limitedString(
                                    object,
                                    "account"
                            ),
                            limitedString(
                                    object,
                                    "note"
                            ),
                            limitedString(
                                    object,
                                    "frequency"
                            ),
                            limitedString(
                                    object,
                                    "start_date",
                                    "startDate"
                            ),
                            limitedString(
                                    object,
                                    "next_run_date",
                                    "nextRunDate"
                            ),
                            optionalBooleanInteger(
                                    object,
                                    false,
                                    "active"
                            )
                    }
            );
        }

        return rows;
    }

    @NonNull
    private List<Object[]> prepareBudgets(
            @NonNull JSONArray array,
            @NonNull Set<Integer> ids
    ) throws RestoreException {

        List<Object[]> rows =
                new ArrayList<>(
                        array.length()
                );

        for (int index = 0;
             index < array.length();
             index++) {

            JSONObject object =
                    requireArrayObject(
                            array,
                            index,
                            "budget"
                    );

            rows.add(
                    new Object[]{
                            requireUniquePositiveId(
                                    object,
                                    ids,
                                    "budget",
                                    "id"
                            ),
                            limitedString(
                                    object,
                                    "category"
                            ),
                            limitedString(
                                    object,
                                    "period"
                            ),
                            requireFiniteNumber(
                                    object,
                                    "limit_amount",
                                    "limitAmount"
                            )
                    }
            );
        }

        return rows;
    }

    @NonNull
    private List<Object[]> prepareLoans(
            @NonNull JSONArray array,
            @NonNull Set<Integer> ids
    ) throws RestoreException {

        List<Object[]> rows =
                new ArrayList<>(
                        array.length()
                );

        for (int index = 0;
             index < array.length();
             index++) {

            JSONObject object =
                    requireArrayObject(
                            array,
                            index,
                            "loan"
                    );

            rows.add(
                    new Object[]{
                            requireUniquePositiveId(
                                    object,
                                    ids,
                                    "loan",
                                    "id"
                            ),
                            limitedString(
                                    object,
                                    "person_name",
                                    "personName"
                            ),
                            limitedString(
                                    object,
                                    "loan_type",
                                    "loanType"
                            ),
                            requireFiniteNumber(
                                    object,
                                    "total_amount",
                                    "totalAmount"
                            ),
                            requireFiniteNumber(
                                    object,
                                    "outstanding_amount",
                                    "outstandingAmount"
                            ),
                            requireFiniteNumber(
                                    object,
                                    "interest_rate",
                                    "interestRate"
                            ),
                            requireFiniteNumber(
                                    object,
                                    "emi_amount",
                                    "emiAmount"
                            ),
                            limitedString(
                                    object,
                                    "due_date",
                                    "dueDate"
                            ),
                            limitedString(
                                    object,
                                    "note"
                            ),
                            optionalBooleanInteger(
                                    object,
                                    false,
                                    "active"
                            ),
                            limitedString(
                                    object,
                                    "start_date",
                                    "startDate"
                            ),
                            optionalInt(
                                    object,
                                    0,
                                    "tenure_months",
                                    "tenureMonths"
                            ),
                            requireFiniteNumber(
                                    object,
                                    "historical_paid_amount",
                                    "historicalPaidAmount"
                            ),
                            optionalInt(
                                    object,
                                    0,
                                    "historical_installments",
                                    "historicalInstallments"
                            )
                    }
            );
        }

        return rows;
    }

    @NonNull
    private List<Object[]> prepareLoanPayments(
            @NonNull JSONArray array,
            @NonNull Set<Integer> ids,
            @NonNull Set<Integer> loanIds
    ) throws RestoreException {

        List<Object[]> rows =
                new ArrayList<>(
                        array.length()
                );

        for (int index = 0;
             index < array.length();
             index++) {

            JSONObject object =
                    requireArrayObject(
                            array,
                            index,
                            "loan payment"
                    );

            int loanId =
                    requirePositiveInt(
                            object,
                            "loan_id",
                            "loanId"
                    );

            if (!loanIds.contains(
                    loanId
            )) {
                throw new RestoreValidationException(
                        "Loan payment references a missing loan."
                );
            }

            rows.add(
                    new Object[]{
                            requireUniquePositiveId(
                                    object,
                                    ids,
                                    "loan payment",
                                    "id"
                            ),
                            loanId,
                            requireFiniteNumber(
                                    object,
                                    "amount"
                            ),
                            limitedString(
                                    object,
                                    "payment_type",
                                    "paymentType"
                            ),
                            limitedString(
                                    object,
                                    "account"
                            ),
                            limitedString(
                                    object,
                                    "payment_date",
                                    "paymentDate"
                            ),
                            limitedString(
                                    object,
                                    "note"
                            )
                    }
            );
        }

        return rows;
    }

    @NonNull
    private List<Object[]> prepareSubscriptions(
            @NonNull JSONArray array,
            @NonNull Set<Integer> ids
    ) throws RestoreException {

        List<Object[]> rows =
                new ArrayList<>(
                        array.length()
                );

        for (int index = 0;
             index < array.length();
             index++) {

            JSONObject object =
                    requireArrayObject(
                            array,
                            index,
                            "subscription"
                    );

            rows.add(
                    new Object[]{
                            requireUniquePositiveId(
                                    object,
                                    ids,
                                    "subscription",
                                    "id"
                            ),
                            limitedString(
                                    object,
                                    "name"
                            ),
                            requireFiniteNumber(
                                    object,
                                    "amount"
                            ),
                            limitedString(
                                    object,
                                    "billing_cycle",
                                    "billingCycle"
                            ),
                            limitedString(
                                    object,
                                    "next_due_date",
                                    "nextDueDate"
                            ),
                            limitedString(
                                    object,
                                    "account"
                            ),
                            limitedString(
                                    object,
                                    "category"
                            ),
                            optionalInt(
                                    object,
                                    0,
                                    "remind_days",
                                    "remindDays"
                            ),
                            limitedString(
                                    object,
                                    "note"
                            ),
                            optionalBooleanInteger(
                                    object,
                                    false,
                                    "active"
                            )
                    }
            );
        }

        return rows;
    }

    @NonNull
    private List<Object[]> prepareCreditCards(
            @NonNull JSONArray array,
            @NonNull Set<Integer> ids
    ) throws RestoreException {

        List<Object[]> rows =
                new ArrayList<>(
                        array.length()
                );

        for (int index = 0;
             index < array.length();
             index++) {

            JSONObject object =
                    requireArrayObject(
                            array,
                            index,
                            "credit card"
                    );

            rows.add(
                    new Object[]{
                            requireUniquePositiveId(
                                    object,
                                    ids,
                                    "credit card",
                                    "id"
                            ),
                            limitedString(
                                    object,
                                    "name"
                            ),
                            limitedString(
                                    object,
                                    "last_four",
                                    "lastFour"
                            ),
                            limitedString(
                                    object,
                                    "account_name",
                                    "accountName"
                            ),
                            requireFiniteNumber(
                                    object,
                                    "credit_limit",
                                    "creditLimit"
                            ),
                            optionalInt(
                                    object,
                                    1,
                                    "billing_day",
                                    "billingDay"
                            ),
                            optionalInt(
                                    object,
                                    1,
                                    "due_day",
                                    "dueDay"
                            ),
                            limitedString(
                                    object,
                                    "payment_account",
                                    "paymentAccount"
                            ),
                            optionalInt(
                                    object,
                                    0,
                                    "reminder_days",
                                    "reminderDays"
                            ),
                            optionalBooleanInteger(
                                    object,
                                    false,
                                    "active"
                            )
                    }
            );
        }

        return rows;
    }

    @NonNull
    private List<Object[]> prepareCreditCardPayments(
            @NonNull JSONArray array,
            @NonNull Set<Integer> ids,
            @NonNull Set<Integer> creditCardIds
    ) throws RestoreException {

        List<Object[]> rows =
                new ArrayList<>(
                        array.length()
                );

        for (int index = 0;
             index < array.length();
             index++) {

            JSONObject object =
                    requireArrayObject(
                            array,
                            index,
                            "credit card payment"
                    );

            int creditCardId =
                    requirePositiveInt(
                            object,
                            "credit_card_id",
                            "creditCardId"
                    );

            if (!creditCardIds.contains(
                    creditCardId
            )) {
                throw new RestoreValidationException(
                        "Credit-card payment references "
                                + "a missing credit card."
                );
            }

            rows.add(
                    new Object[]{
                            requireUniquePositiveId(
                                    object,
                                    ids,
                                    "credit card payment",
                                    "id"
                            ),
                            creditCardId,
                            limitedString(
                                    object,
                                    "statement_end_date",
                                    "statementEndDate"
                            ),
                            requireFiniteNumber(
                                    object,
                                    "amount"
                            ),
                            limitedString(
                                    object,
                                    "payment_date",
                                    "paymentDate"
                            ),
                            limitedString(
                                    object,
                                    "source_account",
                                    "sourceAccount"
                            ),
                            limitedString(
                                    object,
                                    "note"
                            )
                    }
            );
        }

        return rows;
    }

    @NonNull
    private JSONArray prepareInvestments(
            @NonNull JSONArray source
    ) throws RestoreException {

        JSONArray restoredInvestments =
                new JSONArray();

        Set<String> investmentIds =
                new HashSet<>();

        for (int index = 0;
             index < source.length();
             index++) {

            JSONObject sourceObject =
                    requireArrayObject(
                            source,
                            index,
                            "investment"
                    );

            String investmentId =
                    optionalString(
                            sourceObject,
                            "id"
                    );

            if (investmentId.isEmpty()) {
                investmentId =
                        UUID.randomUUID()
                                .toString();
            }

            if (investmentId.length()
                    > MAX_INVESTMENT_ID_LENGTH) {

                throw new RestoreValidationException(
                        "Investment ID exceeds the supported length."
                );
            }

            if (!investmentIds.add(
                    investmentId
            )) {
                throw new RestoreValidationException(
                        "Cloud backup contains a duplicate investment ID."
                );
            }

            JSONObject restoredObject =
                    new JSONObject();

            try {
                restoredObject.put(
                        "id",
                        investmentId
                );

                restoredObject.put(
                        "name",
                        limitedString(
                                sourceObject,
                                "name"
                        )
                );

                restoredObject.put(
                        "type",
                        limitedString(
                                sourceObject,
                                "type"
                        )
                );

                restoredObject.put(
                        "startDate",
                        limitedString(
                                sourceObject,
                                "start_date",
                                "startDate"
                        )
                );

                restoredObject.put(
                        "note",
                        limitedString(
                                sourceObject,
                                "note"
                        )
                );

                restoredObject.put(
                        "investedAmount",
                        requireFiniteNumber(
                                sourceObject,
                                "invested_amount",
                                "investedAmount"
                        )
                );

                restoredObject.put(
                        "currentValue",
                        requireFiniteNumber(
                                sourceObject,
                                "current_value",
                                "currentValue"
                        )
                );

                restoredObject.put(
                        "monthlyContribution",
                        requireFiniteNumber(
                                sourceObject,
                                "monthly_contribution",
                                "monthlyContribution"
                        )
                );

                long createdAt =
                        optionalLong(
                                sourceObject,
                                0L,
                                "created_at",
                                "createdAt"
                        );

                if (createdAt <= 0L) {
                    createdAt =
                            System.currentTimeMillis();
                }

                restoredObject.put(
                        "createdAt",
                        createdAt
                );

                restoredInvestments.put(
                        restoredObject
                );

            } catch (RestoreException exception) {
                throw exception;

            } catch (Exception exception) {
                throw new RestoreValidationException(
                        "Investment backup data is invalid.",
                        exception
                );
            }
        }

        return restoredInvestments;
    }

    private void validateCurrentDatabaseCompatibility(
            int backupDatabaseVersion
    ) throws RestoreException {

        AppDatabase database =
                DatabaseClient
                        .getInstance(
                                applicationContext
                        )
                        .getAppDatabase();

        int currentDatabaseVersion =
                database
                        .getOpenHelper()
                        .getReadableDatabase()
                        .getVersion();

        if (backupDatabaseVersion
                > currentDatabaseVersion) {

            throw new BackupFromNewerAppException(
                    "This cloud backup was created by a newer "
                            + "Money Manager Pro database version. "
                            + "Update the app before restoring."
            );
        }
    }

    private void performRestore(
            @NonNull PreparedRestore preparedRestore
    ) throws RestoreException {

        AppDatabase database =
                DatabaseClient
                        .getInstance(
                                applicationContext
                        )
                        .getAppDatabase();

        SharedPreferences investmentPreferences =
                applicationContext
                        .getSharedPreferences(
                                INVESTMENT_PREFERENCES_NAME,
                                Context.MODE_PRIVATE
                        );

        String previousInvestmentData =
                investmentPreferences.getString(
                        INVESTMENT_PREFERENCES_KEY,
                        "[]"
                );

        if (previousInvestmentData == null) {
            previousInvestmentData =
                    "[]";
        }

        String restoredInvestmentData =
                preparedRestore
                        .investments
                        .toString();

        boolean investmentSaved =
                investmentPreferences
                        .edit()
                        .putString(
                                INVESTMENT_PREFERENCES_KEY,
                                restoredInvestmentData
                        )
                        .commit();

        if (!investmentSaved) {
            throw new RestoreException(
                    "Investment data could not be prepared for restore."
            );
        }

        try {
            database.runInTransaction(
                    () -> {
                        SupportSQLiteDatabase sqlDatabase =
                                database
                                        .getOpenHelper()
                                        .getWritableDatabase();

                        clearFinanceTables(
                                sqlDatabase
                        );

                        insertRows(
                                sqlDatabase,
                                "INSERT INTO `categories` "
                                        + "(`id`,`name`,`type`,`color`) "
                                        + "VALUES (?,?,?,?)",
                                preparedRestore.categories
                        );

                        insertRows(
                                sqlDatabase,
                                "INSERT INTO `accounts` "
                                        + "(`id`,`name`,`type`,"
                                        + "`openingBalance`,`color`) "
                                        + "VALUES (?,?,?,?,?)",
                                preparedRestore.accounts
                        );

                        insertRows(
                                sqlDatabase,
                                "INSERT INTO `goals` "
                                        + "(`id`,`name`,`targetAmount`,"
                                        + "`savedAmount`,`targetDate`,`color`) "
                                        + "VALUES (?,?,?,?,?,?)",
                                preparedRestore.goals
                        );

                        insertRows(
                                sqlDatabase,
                                "INSERT INTO `recurring_transactions` "
                                        + "(`id`,`type`,`amount`,`category`,"
                                        + "`account`,`note`,`frequency`,"
                                        + "`startDate`,`nextRunDate`,`active`) "
                                        + "VALUES (?,?,?,?,?,?,?,?,?,?)",
                                preparedRestore.recurringTransactions
                        );

                        insertRows(
                                sqlDatabase,
                                "INSERT INTO `category_budgets` "
                                        + "(`id`,`category`,`period`,"
                                        + "`limitAmount`) "
                                        + "VALUES (?,?,?,?)",
                                preparedRestore.budgets
                        );

                        insertRows(
                                sqlDatabase,
                                "INSERT INTO `loans` "
                                        + "(`id`,`personName`,`loanType`,"
                                        + "`totalAmount`,`outstandingAmount`,"
                                        + "`interestRate`,`emiAmount`,"
                                        + "`dueDate`,`note`,`active`,"
                                        + "`startDate`,`tenureMonths`,"
                                        + "`historicalPaidAmount`,"
                                        + "`historicalInstallments`) "
                                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                                preparedRestore.loans
                        );

                        insertRows(
                                sqlDatabase,
                                "INSERT INTO `loan_payments` "
                                        + "(`id`,`loanId`,`amount`,"
                                        + "`paymentType`,`account`,"
                                        + "`paymentDate`,`note`) "
                                        + "VALUES (?,?,?,?,?,?,?)",
                                preparedRestore.loanPayments
                        );

                        insertRows(
                                sqlDatabase,
                                "INSERT INTO `subscriptions` "
                                        + "(`id`,`name`,`amount`,"
                                        + "`billingCycle`,`nextDueDate`,"
                                        + "`account`,`category`,`remindDays`,"
                                        + "`note`,`active`) "
                                        + "VALUES (?,?,?,?,?,?,?,?,?,?)",
                                preparedRestore.subscriptions
                        );

                        insertRows(
                                sqlDatabase,
                                "INSERT INTO `credit_cards` "
                                        + "(`id`,`name`,`lastFour`,"
                                        + "`accountName`,`creditLimit`,"
                                        + "`billingDay`,`dueDay`,"
                                        + "`paymentAccount`,`reminderDays`,"
                                        + "`active`) "
                                        + "VALUES (?,?,?,?,?,?,?,?,?,?)",
                                preparedRestore.creditCards
                        );

                        insertRows(
                                sqlDatabase,
                                "INSERT INTO `credit_card_payments` "
                                        + "(`id`,`creditCardId`,"
                                        + "`statementEndDate`,`amount`,"
                                        + "`paymentDate`,`sourceAccount`,"
                                        + "`note`) "
                                        + "VALUES (?,?,?,?,?,?,?)",
                                preparedRestore.creditCardPayments
                        );

                        insertRows(
                                sqlDatabase,
                                "INSERT INTO `transactions` "
                                        + "(`id`,`type`,`amount`,"
                                        + "`category`,`account`,`note`,`date`) "
                                        + "VALUES (?,?,?,?,?,?,?)",
                                preparedRestore.transactions
                        );

                        insertRows(
                                sqlDatabase,
                                "INSERT INTO `expense_items` "
                                        + "(`id`,`transactionId`,`itemName`,"
                                        + "`quantity`,`unit`,`price`,"
                                        + "`total`,`sortOrder`) "
                                        + "VALUES (?,?,?,?,?,?,?,?)",
                                preparedRestore.expenseItems
                        );
                    }
            );

        } catch (Exception databaseException) {
            boolean investmentRollbackSucceeded =
                    investmentPreferences
                            .edit()
                            .putString(
                                    INVESTMENT_PREFERENCES_KEY,
                                    previousInvestmentData
                            )
                            .commit();

            RestoreException restoreException =
                    new RestoreException(
                            "Cloud backup could not be restored. "
                                    + "The local Room database transaction "
                                    + "was rolled back.",
                            databaseException
                    );

            if (!investmentRollbackSucceeded) {
                restoreException.addSuppressed(
                        new IllegalStateException(
                                "Investment rollback could not be completed."
                        )
                );
            }

            throw restoreException;
        }
    }

    private void clearFinanceTables(
            @NonNull SupportSQLiteDatabase database
    ) {
        database.execSQL(
                "DELETE FROM `expense_items`"
        );

        database.execSQL(
                "DELETE FROM `credit_card_payments`"
        );

        database.execSQL(
                "DELETE FROM `loan_payments`"
        );

        database.execSQL(
                "DELETE FROM `transactions`"
        );

        database.execSQL(
                "DELETE FROM `credit_cards`"
        );

        database.execSQL(
                "DELETE FROM `loans`"
        );

        database.execSQL(
                "DELETE FROM `subscriptions`"
        );

        database.execSQL(
                "DELETE FROM `category_budgets`"
        );

        database.execSQL(
                "DELETE FROM `recurring_transactions`"
        );

        database.execSQL(
                "DELETE FROM `goals`"
        );

        database.execSQL(
                "DELETE FROM `accounts`"
        );

        database.execSQL(
                "DELETE FROM `categories`"
        );

        database.execSQL(
                "DELETE FROM `sqlite_sequence` "
                        + "WHERE `name` IN ("
                        + "'transactions',"
                        + "'expense_items',"
                        + "'credit_card_payments',"
                        + "'loan_payments',"
                        + "'credit_cards',"
                        + "'loans',"
                        + "'subscriptions',"
                        + "'category_budgets',"
                        + "'recurring_transactions',"
                        + "'goals',"
                        + "'accounts',"
                        + "'categories'"
                        + ")"
        );
    }

    private void insertRows(
            @NonNull SupportSQLiteDatabase database,
            @NonNull String sql,
            @NonNull List<Object[]> rows
    ) {
        for (Object[] row : rows) {
            database.execSQL(
                    sql,
                    row
            );
        }
    }

    @NonNull
    private RestorePreview createPreview(
            @NonNull EncryptedCloudBackupDownloader
                    .DecryptedCloudBackup backup,
            @NonNull PreparedRestore preparedRestore
    ) {
        return new RestorePreview(
                preparedRestore.backupId,
                preparedRestore.backupCreatedAt,
                backup.getUploadedAtClient(),
                preparedRestore.databaseVersion,
                preparedRestore.appVersionName,
                preparedRestore.appVersionCode,
                preparedRestore.totalRecordCount,
                preparedRestore.transactions.size(),
                preparedRestore.expenseItems.size(),
                preparedRestore.categories.size(),
                preparedRestore.accounts.size(),
                preparedRestore.goals.size(),
                preparedRestore.recurringTransactions.size(),
                preparedRestore.budgets.size(),
                preparedRestore.loans.size(),
                preparedRestore.loanPayments.size(),
                preparedRestore.subscriptions.size(),
                preparedRestore.creditCards.size(),
                preparedRestore.creditCardPayments.size(),
                preparedRestore.investments.length()
        );
    }

    @Nullable
    private Exception validateFirebaseUser(
            @NonNull FirebaseUser firebaseUser,
            @NonNull EncryptedCloudBackupDownloader
                    .DecryptedCloudBackup backup
    ) {
        String userId =
                firebaseUser
                        .getUid()
                        .trim();

        if (userId.isEmpty()) {
            return new IllegalStateException(
                    "Firebase cloud account UID is unavailable."
            );
        }

        if (!firebaseUser.isEmailVerified()) {
            return new IllegalStateException(
                    "Cloud restore requires a verified email account."
            );
        }

        if (!userId.equals(
                backup.getFirebaseUserId()
        )) {
            return new IllegalStateException(
                    "Cloud backup belongs to another Firebase account."
            );
        }

        return null;
    }

    private int requireUniquePositiveId(
            @NonNull JSONObject object,
            @NonNull Set<Integer> usedIds,
            @NonNull String recordName,
            @NonNull String... fields
    ) throws RestoreException {

        int id =
                requirePositiveInt(
                        object,
                        fields
                );

        if (!usedIds.add(
                id
        )) {
            throw new RestoreValidationException(
                    "Cloud backup contains a duplicate "
                            + recordName
                            + " ID."
            );
        }

        return id;
    }

    private int requirePositiveInt(
            @NonNull JSONObject object,
            @NonNull String... fields
    ) throws RestoreException {

        Object rawValue =
                findValue(
                        object,
                        fields
                );

        if (rawValue == null) {
            throw new RestoreValidationException(
                    "Required numeric field \""
                            + primaryFieldName(
                            fields
                    )
                            + "\" is missing."
            );
        }

        long value =
                parseLong(
                        rawValue,
                        Long.MIN_VALUE
                );

        if (value <= 0L
                || value > Integer.MAX_VALUE) {

            throw new RestoreValidationException(
                    "Numeric field \""
                            + primaryFieldName(
                            fields
                    )
                            + "\" is invalid."
            );
        }

        return (int) value;
    }

    private int requireNonNegativeInt(
            @NonNull JSONObject object,
            @NonNull String... fields
    ) throws RestoreException {

        Object rawValue =
                findValue(
                        object,
                        fields
                );

        if (rawValue == null) {
            throw new RestoreValidationException(
                    "Required numeric field \""
                            + primaryFieldName(
                            fields
                    )
                            + "\" is missing."
            );
        }

        long value =
                parseLong(
                        rawValue,
                        Long.MIN_VALUE
                );

        if (value < 0L
                || value > Integer.MAX_VALUE) {

            throw new RestoreValidationException(
                    "Numeric field \""
                            + primaryFieldName(
                            fields
                    )
                            + "\" is invalid."
            );
        }

        return (int) value;
    }

    private int optionalInt(
            @NonNull JSONObject object,
            int fallback,
            @NonNull String... fields
    ) {
        Object rawValue =
                findValue(
                        object,
                        fields
                );

        if (rawValue == null) {
            return fallback;
        }

        long parsed =
                parseLong(
                        rawValue,
                        fallback
                );

        if (parsed < Integer.MIN_VALUE
                || parsed > Integer.MAX_VALUE) {

            return fallback;
        }

        return (int) parsed;
    }

    private long optionalLong(
            @NonNull JSONObject object,
            long fallback,
            @NonNull String... fields
    ) {
        Object rawValue =
                findValue(
                        object,
                        fields
                );

        if (rawValue == null) {
            return fallback;
        }

        return parseLong(
                rawValue,
                fallback
        );
    }

    private double requireFiniteNumber(
            @NonNull JSONObject object,
            @NonNull String... fields
    ) throws RestoreException {

        Object rawValue =
                findValue(
                        object,
                        fields
                );

        if (rawValue == null) {
            throw new RestoreValidationException(
                    "Required amount field \""
                            + primaryFieldName(
                            fields
                    )
                            + "\" is missing."
            );
        }

        double value;

        if (rawValue instanceof Number) {
            value =
                    ((Number) rawValue)
                            .doubleValue();

        } else {
            try {
                value =
                        Double.parseDouble(
                                String.valueOf(
                                        rawValue
                                )
                        );

            } catch (Exception exception) {
                value =
                        Double.NaN;
            }
        }

        if (Double.isNaN(
                value
        )
                || Double.isInfinite(
                value
        )) {

            throw new RestoreValidationException(
                    "Amount field \""
                            + primaryFieldName(
                            fields
                    )
                            + "\" is invalid."
            );
        }

        return value;
    }

    private int optionalBooleanInteger(
            @NonNull JSONObject object,
            boolean fallback,
            @NonNull String... fields
    ) {
        Object rawValue =
                findValue(
                        object,
                        fields
                );

        if (rawValue == null) {
            return fallback
                    ? 1
                    : 0;
        }

        if (rawValue instanceof Boolean) {
            return (Boolean) rawValue
                    ? 1
                    : 0;
        }

        if (rawValue instanceof Number) {
            return ((Number) rawValue)
                    .intValue() == 0
                    ? 0
                    : 1;
        }

        String text =
                String.valueOf(
                                rawValue
                        )
                        .trim();

        return "true".equalsIgnoreCase(
                text
        )
                || "1".equals(
                text
        )
                ? 1
                : 0;
    }

    @NonNull
    private String limitedString(
            @NonNull JSONObject object,
            @NonNull String... fields
    ) throws RestoreException {

        String value =
                optionalString(
                        object,
                        fields
                );

        if (value.length()
                > MAX_TEXT_LENGTH) {

            throw new RestoreValidationException(
                    "Text field \""
                            + primaryFieldName(
                            fields
                    )
                            + "\" exceeds the supported length."
            );
        }

        return value;
    }

    @NonNull
    private String requireString(
            @NonNull JSONObject object,
            @NonNull String... fields
    ) throws RestoreException {

        String value =
                optionalString(
                        object,
                        fields
                )
                        .trim();

        if (value.isEmpty()) {
            throw new RestoreValidationException(
                    "Required text field \""
                            + primaryFieldName(
                            fields
                    )
                            + "\" is missing or empty."
            );
        }

        if (value.length()
                > MAX_TEXT_LENGTH) {

            throw new RestoreValidationException(
                    "Text field \""
                            + primaryFieldName(
                            fields
                    )
                            + "\" exceeds the supported length."
            );
        }

        return value;
    }

    @NonNull
    private String optionalString(
            @NonNull JSONObject object,
            @NonNull String... fields
    ) {
        Object rawValue =
                findValue(
                        object,
                        fields
                );

        if (rawValue == null) {
            return "";
        }

        return String.valueOf(
                rawValue
        );
    }

    @Nullable
    private Object findValue(
            @NonNull JSONObject object,
            @NonNull String... fields
    ) {
        for (String field : fields) {
            if (field == null
                    || field.trim().isEmpty()) {

                continue;
            }

            if (!object.has(
                    field
            )
                    || object.isNull(
                    field
            )) {

                continue;
            }

            Object value =
                    object.opt(
                            field
                    );

            if (value != null
                    && value != JSONObject.NULL) {

                return value;
            }
        }

        return null;
    }

    private long parseLong(
            @NonNull Object rawValue,
            long fallback
    ) {
        if (rawValue instanceof Number) {
            return ((Number) rawValue)
                    .longValue();
        }

        try {
            return Long.parseLong(
                    String.valueOf(
                                    rawValue
                            )
                            .trim()
            );

        } catch (Exception ignored) {
            return fallback;
        }
    }

    @NonNull
    private String primaryFieldName(
            @NonNull String... fields
    ) {
        if (fields.length == 0
                || fields[0] == null
                || fields[0].trim().isEmpty()) {

            return "unknown";
        }

        return fields[0];
    }

    @NonNull
    private JSONObject requireObject(
            @NonNull JSONObject root,
            @NonNull String... fields
    ) throws RestoreException {

        for (String field : fields) {
            JSONObject object =
                    root.optJSONObject(
                            field
                    );

            if (object != null) {
                return object;
            }
        }

        throw new RestoreValidationException(
                "Cloud backup object \""
                        + primaryFieldName(
                        fields
                )
                        + "\" is missing."
        );
    }

    @NonNull
    private JSONArray requireArray(
            @NonNull JSONObject root,
            @NonNull String... fields
    ) throws RestoreException {

        for (String field : fields) {
            JSONArray array =
                    root.optJSONArray(
                            field
                    );

            if (array != null) {
                return array;
            }
        }

        throw new RestoreValidationException(
                "Cloud backup table \""
                        + primaryFieldName(
                        fields
                )
                        + "\" is missing."
        );
    }

    @NonNull
    private JSONObject requireArrayObject(
            @NonNull JSONArray array,
            int index,
            @NonNull String recordName
    ) throws RestoreException {

        JSONObject object =
                array.optJSONObject(
                        index
                );

        if (object == null) {
            throw new RestoreValidationException(
                    "Invalid "
                            + recordName
                            + " record at position "
                            + index
                            + "."
            );
        }

        return object;
    }

    private void verifyDeclaredCount(
            @NonNull JSONObject countObject,
            int actualCount,
            @NonNull String... fields
    ) throws RestoreException {

        int declaredCount =
                requireNonNegativeInt(
                        countObject,
                        fields
                );

        if (declaredCount
                != actualCount) {

            throw new RestoreValidationException(
                    "Cloud backup count verification failed for "
                            + primaryFieldName(
                            fields
                    )
                            + "."
            );
        }
    }

    @NonNull
    private Exception asException(
            @NonNull Exception exception
    ) {
        if (exception instanceof RestoreException) {
            return exception;
        }

        return new RestoreException(
                "Cloud backup restore failed.",
                exception
        );
    }

    private void clearBytes(
            @Nullable byte[] bytes
    ) {
        if (bytes == null) {
            return;
        }

        Arrays.fill(
                bytes,
                (byte) 0
        );
    }

    private static final class PreparedRestore {

        private String backupId =
                "";

        private long backupCreatedAt;

        private int databaseVersion;

        private String appVersionName =
                "";

        private long appVersionCode;

        private int totalRecordCount;

        private List<Object[]> transactions =
                new ArrayList<>();

        private List<Object[]> expenseItems =
                new ArrayList<>();

        private List<Object[]> categories =
                new ArrayList<>();

        private List<Object[]> accounts =
                new ArrayList<>();

        private List<Object[]> goals =
                new ArrayList<>();

        private List<Object[]> recurringTransactions =
                new ArrayList<>();

        private List<Object[]> budgets =
                new ArrayList<>();

        private List<Object[]> loans =
                new ArrayList<>();

        private List<Object[]> loanPayments =
                new ArrayList<>();

        private List<Object[]> subscriptions =
                new ArrayList<>();

        private List<Object[]> creditCards =
                new ArrayList<>();

        private List<Object[]> creditCardPayments =
                new ArrayList<>();

        private JSONArray investments =
                new JSONArray();
    }

    public static final class RestorePreview {

        private final String backupId;

        private final long backupCreatedAt;

        private final long uploadedAtClient;

        private final int databaseVersion;

        private final String appVersionName;

        private final long appVersionCode;

        private final int totalRecordCount;

        private final int transactionCount;

        private final int expenseItemCount;

        private final int categoryCount;

        private final int accountCount;

        private final int goalCount;

        private final int recurringTransactionCount;

        private final int budgetCount;

        private final int loanCount;

        private final int loanPaymentCount;

        private final int subscriptionCount;

        private final int creditCardCount;

        private final int creditCardPaymentCount;

        private final int investmentCount;

        private RestorePreview(
                @NonNull String backupId,
                long backupCreatedAt,
                long uploadedAtClient,
                int databaseVersion,
                @NonNull String appVersionName,
                long appVersionCode,
                int totalRecordCount,
                int transactionCount,
                int expenseItemCount,
                int categoryCount,
                int accountCount,
                int goalCount,
                int recurringTransactionCount,
                int budgetCount,
                int loanCount,
                int loanPaymentCount,
                int subscriptionCount,
                int creditCardCount,
                int creditCardPaymentCount,
                int investmentCount
        ) {
            this.backupId =
                    backupId;

            this.backupCreatedAt =
                    backupCreatedAt;

            this.uploadedAtClient =
                    uploadedAtClient;

            this.databaseVersion =
                    databaseVersion;

            this.appVersionName =
                    appVersionName;

            this.appVersionCode =
                    appVersionCode;

            this.totalRecordCount =
                    totalRecordCount;

            this.transactionCount =
                    transactionCount;

            this.expenseItemCount =
                    expenseItemCount;

            this.categoryCount =
                    categoryCount;

            this.accountCount =
                    accountCount;

            this.goalCount =
                    goalCount;

            this.recurringTransactionCount =
                    recurringTransactionCount;

            this.budgetCount =
                    budgetCount;

            this.loanCount =
                    loanCount;

            this.loanPaymentCount =
                    loanPaymentCount;

            this.subscriptionCount =
                    subscriptionCount;

            this.creditCardCount =
                    creditCardCount;

            this.creditCardPaymentCount =
                    creditCardPaymentCount;

            this.investmentCount =
                    investmentCount;
        }

        @NonNull
        public String getBackupId() {
            return backupId;
        }

        public long getBackupCreatedAt() {
            return backupCreatedAt;
        }

        public long getUploadedAtClient() {
            return uploadedAtClient;
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

        public int getTotalRecordCount() {
            return totalRecordCount;
        }

        public int getTransactionCount() {
            return transactionCount;
        }

        public int getExpenseItemCount() {
            return expenseItemCount;
        }

        public int getCategoryCount() {
            return categoryCount;
        }

        public int getAccountCount() {
            return accountCount;
        }

        public int getGoalCount() {
            return goalCount;
        }

        public int getRecurringTransactionCount() {
            return recurringTransactionCount;
        }

        public int getBudgetCount() {
            return budgetCount;
        }

        public int getLoanCount() {
            return loanCount;
        }

        public int getLoanPaymentCount() {
            return loanPaymentCount;
        }

        public int getSubscriptionCount() {
            return subscriptionCount;
        }

        public int getCreditCardCount() {
            return creditCardCount;
        }

        public int getCreditCardPaymentCount() {
            return creditCardPaymentCount;
        }

        public int getInvestmentCount() {
            return investmentCount;
        }
    }

    public static final class RestoreResult {

        private final String backupId;

        private final long backupCreatedAt;

        private final long restoredAt;

        private final int totalRecordCount;

        private final int transactionCount;

        private final int expenseItemCount;

        private final int accountCount;

        private final int categoryCount;

        private final int investmentCount;

        private RestoreResult(
                @NonNull String backupId,
                long backupCreatedAt,
                long restoredAt,
                int totalRecordCount,
                int transactionCount,
                int expenseItemCount,
                int accountCount,
                int categoryCount,
                int investmentCount
        ) {
            this.backupId =
                    backupId;

            this.backupCreatedAt =
                    backupCreatedAt;

            this.restoredAt =
                    restoredAt;

            this.totalRecordCount =
                    totalRecordCount;

            this.transactionCount =
                    transactionCount;

            this.expenseItemCount =
                    expenseItemCount;

            this.accountCount =
                    accountCount;

            this.categoryCount =
                    categoryCount;

            this.investmentCount =
                    investmentCount;
        }

        @NonNull
        public String getBackupId() {
            return backupId;
        }

        public long getBackupCreatedAt() {
            return backupCreatedAt;
        }

        public long getRestoredAt() {
            return restoredAt;
        }

        public int getTotalRecordCount() {
            return totalRecordCount;
        }

        public int getTransactionCount() {
            return transactionCount;
        }

        public int getExpenseItemCount() {
            return expenseItemCount;
        }

        public int getAccountCount() {
            return accountCount;
        }

        public int getCategoryCount() {
            return categoryCount;
        }

        public int getInvestmentCount() {
            return investmentCount;
        }

        @NonNull
        public String getRestoreMode() {
            return RESTORE_MODE_REPLACE_ALL;
        }
    }

    public interface PreviewCallback {

        void onLoaded(
                @NonNull RestorePreview preview
        );

        void onError(
                @NonNull Exception exception
        );
    }

    public interface RestoreCallback {

        void onSuccess(
                @NonNull RestoreResult result
        );

        void onError(
                @NonNull Exception exception
        );
    }

    public static class RestoreException
            extends Exception {

        public RestoreException(
                @NonNull String message
        ) {
            super(
                    message
            );
        }

        public RestoreException(
                @NonNull String message,
                @NonNull Throwable cause
        ) {
            super(
                    message,
                    cause
            );
        }
    }

    public static final class RestoreValidationException
            extends RestoreException {

        public RestoreValidationException(
                @NonNull String message
        ) {
            super(
                    message
            );
        }

        public RestoreValidationException(
                @NonNull String message,
                @NonNull Throwable cause
        ) {
            super(
                    message,
                    cause
            );
        }
    }

    public static final class BackupFromNewerAppException
            extends RestoreException {

        public BackupFromNewerAppException(
                @NonNull String message
        ) {
            super(
                    message
            );
        }
    }
}