package com.example.moneymanagerpro.cloud;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;

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
import com.example.moneymanagerpro.utils.InvestmentStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

/**
 * Creates a complete compressed cloud-backup payload for Money Manager Pro.
 *
 * Important rules:
 *
 * 1. This class reads data only from local Room and investment storage.
 * 2. It does not connect to Firebase.
 * 3. It does not upload any information.
 * 4. It does not contain the user's cloud-backup passphrase.
 * 5. The resulting compressed bytes must be encrypted with
 *    CloudBackupEncryption before upload.
 * 6. The Firebase UID is placed inside the encrypted payload so a
 *    restored backup can be verified against the signed-in account.
 */
public final class CloudBackupPayloadBuilder {

    public static final int PAYLOAD_VERSION = 1;

    public static final String APP_NAME =
            "Money Manager Pro";

    public static final String PAYLOAD_TYPE =
            "MONEY_MANAGER_PRO_FULL_CLOUD_BACKUP";

    public static final String COMPRESSION_TYPE =
            "GZIP";

    public static final String HASH_ALGORITHM =
            "SHA-256";

    /**
     * Keeps an unexpected database corruption or oversized payload from
     * consuming excessive memory.
     */
    private static final int MAX_UNCOMPRESSED_BYTES =
            25 * 1024 * 1024;

    private static final int MAX_COMPRESSED_BYTES =
            25 * 1024 * 1024;

    private static final int MAX_FIREBASE_UID_LENGTH =
            256;

    private CloudBackupPayloadBuilder() {
        // Utility class.
    }

    /**
     * Creates one complete compressed finance backup.
     *
     * This method performs database work and must be called from a
     * background thread, not directly from the main UI thread.
     */
    @NonNull
    public static Payload build(
            @NonNull Context context,
            @NonNull String firebaseUserId
    ) throws PayloadBuildException {

        Context applicationContext =
                context.getApplicationContext();

        String verifiedUserId =
                validateFirebaseUserId(
                        firebaseUserId
                );

        long createdAtMillis =
                System.currentTimeMillis();

        String backupId =
                createBackupId(
                        createdAtMillis
                );

        AppDatabase database =
                DatabaseClient
                        .getInstance(
                                applicationContext
                        )
                        .getAppDatabase();

        BackupSnapshot snapshot =
                readDatabaseSnapshot(
                        database
                );

        /*
         * Investments are currently stored separately in SharedPreferences,
         * so they are added after the Room transaction completes.
         */
        snapshot.investments =
                safeList(
                        InvestmentStore.getAll(
                                applicationContext
                        )
                );

        byte[] uncompressedJsonBytes = null;
        byte[] compressedBytes = null;

        try {
            JSONObject backupRoot =
                    buildBackupRoot(
                            applicationContext,
                            database,
                            verifiedUserId,
                            backupId,
                            createdAtMillis,
                            snapshot
                    );

            uncompressedJsonBytes =
                    backupRoot
                            .toString()
                            .getBytes(
                                    StandardCharsets.UTF_8
                            );

            if (uncompressedJsonBytes.length <= 0) {
                throw new PayloadBuildException(
                        "Cloud backup payload is empty."
                );
            }

            if (uncompressedJsonBytes.length
                    > MAX_UNCOMPRESSED_BYTES) {

                throw new PayloadBuildException(
                        "Cloud backup exceeds the supported 25 MB "
                                + "uncompressed size."
                );
            }

            compressedBytes =
                    gzipCompress(
                            uncompressedJsonBytes
                    );

            if (compressedBytes.length <= 0) {
                throw new PayloadBuildException(
                        "Compressed cloud backup is empty."
                );
            }

            if (compressedBytes.length
                    > MAX_COMPRESSED_BYTES) {

                throw new PayloadBuildException(
                        "Compressed cloud backup exceeds the "
                                + "supported 25 MB size."
                );
            }

            String uncompressedSha256 =
                    sha256Hex(
                            uncompressedJsonBytes
                    );

            String compressedSha256 =
                    sha256Hex(
                            compressedBytes
                    );

            RecordCounts recordCounts =
                    RecordCounts.fromSnapshot(
                            snapshot
                    );

            return new Payload(
                    backupId,
                    verifiedUserId,
                    createdAtMillis,
                    formatUtcDateTime(
                            createdAtMillis
                    ),
                    PAYLOAD_VERSION,
                    database
                            .getOpenHelper()
                            .getReadableDatabase()
                            .getVersion(),
                    getAppVersionName(
                            applicationContext
                    ),
                    getAppVersionCode(
                            applicationContext
                    ),
                    COMPRESSION_TYPE,
                    HASH_ALGORITHM,
                    uncompressedSha256,
                    compressedSha256,
                    uncompressedJsonBytes.length,
                    compressedBytes,
                    recordCounts
            );

        } catch (PayloadBuildException exception) {
            throw exception;

        } catch (Exception exception) {
            throw new PayloadBuildException(
                    "Unable to create the Money Manager Pro "
                            + "cloud backup payload.",
                    exception
            );

        } finally {
            clearBytes(
                    uncompressedJsonBytes
            );

            clearBytes(
                    compressedBytes
            );
        }
    }

    /**
     * Reads all Room tables inside one database transaction so all tables
     * represent one consistent point-in-time snapshot.
     */
    @NonNull
    private static BackupSnapshot readDatabaseSnapshot(
            @NonNull AppDatabase database
    ) throws PayloadBuildException {

        BackupSnapshot snapshot =
                new BackupSnapshot();

        try {
            database.runInTransaction(
                    () -> {
                        snapshot.transactions =
                                safeList(
                                        database
                                                .transactionDao()
                                                .getAllTransactions()
                                );

                        snapshot.expenseItems =
                                safeList(
                                        database
                                                .expenseItemDao()
                                                .getAllExpenseItems()
                                );

                        snapshot.categories =
                                safeList(
                                        database
                                                .categoryDao()
                                                .getAllCategories()
                                );

                        snapshot.accounts =
                                safeList(
                                        database
                                                .accountDao()
                                                .getAllAccounts()
                                );

                        snapshot.goals =
                                safeList(
                                        database
                                                .goalDao()
                                                .getAllGoals()
                                );

                        snapshot.recurringTransactions =
                                safeList(
                                        database
                                                .recurringTransactionDao()
                                                .getAllRecurringTransactions()
                                );

                        snapshot.budgets =
                                safeList(
                                        database
                                                .budgetDao()
                                                .getAllBudgets()
                                );

                        snapshot.loans =
                                safeList(
                                        database
                                                .loanDao()
                                                .getAllLoans()
                                );

                        snapshot.loanPayments =
                                safeList(
                                        database
                                                .loanPaymentDao()
                                                .getAllLoanPayments()
                                );

                        snapshot.subscriptions =
                                safeList(
                                        database
                                                .subscriptionDao()
                                                .getAllSubscriptions()
                                );

                        snapshot.creditCards =
                                safeList(
                                        database
                                                .creditCardDao()
                                                .getAllCreditCards()
                                );

                        snapshot.creditCardPayments =
                                safeList(
                                        database
                                                .creditCardPaymentDao()
                                                .getAllPayments()
                                );
                    }
            );

            return snapshot;

        } catch (Exception exception) {
            throw new PayloadBuildException(
                    "Unable to read the local finance database.",
                    exception
            );
        }
    }

    @NonNull
    private static JSONObject buildBackupRoot(
            @NonNull Context context,
            @NonNull AppDatabase database,
            @NonNull String firebaseUserId,
            @NonNull String backupId,
            long createdAtMillis,
            @NonNull BackupSnapshot snapshot
    ) throws Exception {

        JSONObject root =
                new JSONObject();

        root.put(
                "payloadType",
                PAYLOAD_TYPE
        );

        root.put(
                "payloadVersion",
                PAYLOAD_VERSION
        );

        root.put(
                "appName",
                APP_NAME
        );

        root.put(
                "packageName",
                context.getPackageName()
        );

        root.put(
                "cloudOwnerUid",
                firebaseUserId
        );

        root.put(
                "backupId",
                backupId
        );

        root.put(
                "createdAtMillis",
                createdAtMillis
        );

        root.put(
                "createdAtUtc",
                formatUtcDateTime(
                        createdAtMillis
                )
        );

        root.put(
                "databaseVersion",
                database
                        .getOpenHelper()
                        .getReadableDatabase()
                        .getVersion()
        );

        root.put(
                "appVersionName",
                getAppVersionName(
                        context
                )
        );

        root.put(
                "appVersionCode",
                getAppVersionCode(
                        context
                )
        );

        root.put(
                "compression",
                COMPRESSION_TYPE
        );

        root.put(
                "transactions",
                createTransactionArray(
                        snapshot.transactions
                )
        );

        root.put(
                "expenseItems",
                createExpenseItemArray(
                        snapshot.expenseItems
                )
        );

        root.put(
                "categories",
                createCategoryArray(
                        snapshot.categories
                )
        );

        root.put(
                "accounts",
                createAccountArray(
                        snapshot.accounts
                )
        );

        root.put(
                "goals",
                createGoalArray(
                        snapshot.goals
                )
        );

        root.put(
                "recurringTransactions",
                createRecurringTransactionArray(
                        snapshot.recurringTransactions
                )
        );

        root.put(
                "budgets",
                createBudgetArray(
                        snapshot.budgets
                )
        );

        root.put(
                "loans",
                createLoanArray(
                        snapshot.loans
                )
        );

        root.put(
                "loanPayments",
                createLoanPaymentArray(
                        snapshot.loanPayments
                )
        );

        root.put(
                "subscriptions",
                createSubscriptionArray(
                        snapshot.subscriptions
                )
        );

        root.put(
                "creditCards",
                createCreditCardArray(
                        snapshot.creditCards
                )
        );

        root.put(
                "creditCardPayments",
                createCreditCardPaymentArray(
                        snapshot.creditCardPayments
                )
        );

        root.put(
                "investments",
                createInvestmentArray(
                        snapshot.investments
                )
        );

        RecordCounts recordCounts =
                RecordCounts.fromSnapshot(
                        snapshot
                );

        root.put(
                "recordCounts",
                recordCounts.toJson()
        );

        return root;
    }

    @NonNull
    private static JSONArray createTransactionArray(
            @NonNull List<Transaction> transactions
    ) throws Exception {

        JSONArray array =
                new JSONArray();

        for (Transaction transaction : transactions) {
            if (transaction == null) {
                continue;
            }

            JSONObject object =
                    new JSONObject();

            object.put(
                    "id",
                    transaction.getId()
            );

            object.put(
                    "type",
                    safeString(
                            transaction.getType()
                    )
            );

            object.put(
                    "amount",
                    transaction.getAmount()
            );

            object.put(
                    "category",
                    safeString(
                            transaction.getCategory()
                    )
            );

            object.put(
                    "account",
                    safeString(
                            transaction.getAccount()
                    )
            );

            object.put(
                    "note",
                    safeString(
                            transaction.getNote()
                    )
            );

            object.put(
                    "date",
                    safeString(
                            transaction.getDate()
                    )
            );

            array.put(object);
        }

        return array;
    }

    @NonNull
    private static JSONArray createExpenseItemArray(
            @NonNull List<ExpenseItem> expenseItems
    ) throws Exception {

        JSONArray array =
                new JSONArray();

        for (ExpenseItem item : expenseItems) {
            if (item == null) {
                continue;
            }

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
                    safeString(
                            item.getItemName()
                    )
            );

            object.put(
                    "quantity",
                    item.getQuantity()
            );

            object.put(
                    "unit",
                    safeString(
                            item.getUnit()
                    )
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

            array.put(object);
        }

        return array;
    }

    @NonNull
    private static JSONArray createCategoryArray(
            @NonNull List<Category> categories
    ) throws Exception {

        JSONArray array =
                new JSONArray();

        for (Category category : categories) {
            if (category == null) {
                continue;
            }

            JSONObject object =
                    new JSONObject();

            object.put(
                    "id",
                    category.getId()
            );

            object.put(
                    "name",
                    safeString(
                            category.getName()
                    )
            );

            object.put(
                    "type",
                    safeString(
                            category.getType()
                    )
            );

            object.put(
                    "color",
                    safeString(
                            category.getColor()
                    )
            );

            array.put(object);
        }

        return array;
    }

    @NonNull
    private static JSONArray createAccountArray(
            @NonNull List<Account> accounts
    ) throws Exception {

        JSONArray array =
                new JSONArray();

        for (Account account : accounts) {
            if (account == null) {
                continue;
            }

            JSONObject object =
                    new JSONObject();

            object.put(
                    "id",
                    account.getId()
            );

            object.put(
                    "name",
                    safeString(
                            account.getName()
                    )
            );

            object.put(
                    "type",
                    safeString(
                            account.getType()
                    )
            );

            object.put(
                    "openingBalance",
                    account.getOpeningBalance()
            );

            object.put(
                    "color",
                    safeString(
                            account.getColor()
                    )
            );

            array.put(object);
        }

        return array;
    }

    @NonNull
    private static JSONArray createGoalArray(
            @NonNull List<Goal> goals
    ) throws Exception {

        JSONArray array =
                new JSONArray();

        for (Goal goal : goals) {
            if (goal == null) {
                continue;
            }

            JSONObject object =
                    new JSONObject();

            object.put(
                    "id",
                    goal.getId()
            );

            object.put(
                    "name",
                    safeString(
                            goal.getName()
                    )
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
                    safeString(
                            goal.getTargetDate()
                    )
            );

            object.put(
                    "color",
                    safeString(
                            goal.getColor()
                    )
            );

            array.put(object);
        }

        return array;
    }

    @NonNull
    private static JSONArray createRecurringTransactionArray(
            @NonNull List<RecurringTransaction> recurringTransactions
    ) throws Exception {

        JSONArray array =
                new JSONArray();

        for (RecurringTransaction recurring
                : recurringTransactions) {

            if (recurring == null) {
                continue;
            }

            JSONObject object =
                    new JSONObject();

            object.put(
                    "id",
                    recurring.getId()
            );

            object.put(
                    "type",
                    safeString(
                            recurring.getType()
                    )
            );

            object.put(
                    "amount",
                    recurring.getAmount()
            );

            object.put(
                    "category",
                    safeString(
                            recurring.getCategory()
                    )
            );

            object.put(
                    "account",
                    safeString(
                            recurring.getAccount()
                    )
            );

            object.put(
                    "note",
                    safeString(
                            recurring.getNote()
                    )
            );

            object.put(
                    "frequency",
                    safeString(
                            recurring.getFrequency()
                    )
            );

            object.put(
                    "startDate",
                    safeString(
                            recurring.getStartDate()
                    )
            );

            object.put(
                    "nextRunDate",
                    safeString(
                            recurring.getNextRunDate()
                    )
            );

            object.put(
                    "active",
                    recurring.isActive()
            );

            array.put(object);
        }

        return array;
    }

    @NonNull
    private static JSONArray createBudgetArray(
            @NonNull List<Budget> budgets
    ) throws Exception {

        JSONArray array =
                new JSONArray();

        for (Budget budget : budgets) {
            if (budget == null) {
                continue;
            }

            JSONObject object =
                    new JSONObject();

            object.put(
                    "id",
                    budget.getId()
            );

            object.put(
                    "category",
                    safeString(
                            budget.getCategory()
                    )
            );

            object.put(
                    "period",
                    safeString(
                            budget.getPeriod()
                    )
            );

            object.put(
                    "limitAmount",
                    budget.getLimitAmount()
            );

            array.put(object);
        }

        return array;
    }

    @NonNull
    private static JSONArray createLoanArray(
            @NonNull List<Loan> loans
    ) throws Exception {

        JSONArray array =
                new JSONArray();

        for (Loan loan : loans) {
            if (loan == null) {
                continue;
            }

            JSONObject object =
                    new JSONObject();

            object.put(
                    "id",
                    loan.getId()
            );

            object.put(
                    "personName",
                    safeString(
                            loan.getPersonName()
                    )
            );

            object.put(
                    "loanType",
                    safeString(
                            loan.getLoanType()
                    )
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
                    safeString(
                            loan.getDueDate()
                    )
            );

            object.put(
                    "note",
                    safeString(
                            loan.getNote()
                    )
            );

            object.put(
                    "active",
                    loan.isActive()
            );

            object.put(
                    "startDate",
                    safeString(
                            loan.getStartDate()
                    )
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

            array.put(object);
        }

        return array;
    }

    @NonNull
    private static JSONArray createLoanPaymentArray(
            @NonNull List<LoanPayment> loanPayments
    ) throws Exception {

        JSONArray array =
                new JSONArray();

        for (LoanPayment payment : loanPayments) {
            if (payment == null) {
                continue;
            }

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
                    safeString(
                            payment.getPaymentType()
                    )
            );

            object.put(
                    "account",
                    safeString(
                            payment.getAccount()
                    )
            );

            object.put(
                    "paymentDate",
                    safeString(
                            payment.getPaymentDate()
                    )
            );

            object.put(
                    "note",
                    safeString(
                            payment.getNote()
                    )
            );

            array.put(object);
        }

        return array;
    }

    @NonNull
    private static JSONArray createSubscriptionArray(
            @NonNull List<Subscription> subscriptions
    ) throws Exception {

        JSONArray array =
                new JSONArray();

        for (Subscription subscription : subscriptions) {
            if (subscription == null) {
                continue;
            }

            JSONObject object =
                    new JSONObject();

            object.put(
                    "id",
                    subscription.getId()
            );

            object.put(
                    "name",
                    safeString(
                            subscription.getName()
                    )
            );

            object.put(
                    "amount",
                    subscription.getAmount()
            );

            object.put(
                    "billingCycle",
                    safeString(
                            subscription.getBillingCycle()
                    )
            );

            object.put(
                    "nextDueDate",
                    safeString(
                            subscription.getNextDueDate()
                    )
            );

            object.put(
                    "account",
                    safeString(
                            subscription.getAccount()
                    )
            );

            object.put(
                    "category",
                    safeString(
                            subscription.getCategory()
                    )
            );

            object.put(
                    "remindDays",
                    subscription.getRemindDays()
            );

            object.put(
                    "note",
                    safeString(
                            subscription.getNote()
                    )
            );

            object.put(
                    "active",
                    subscription.isActive()
            );

            array.put(object);
        }

        return array;
    }

    @NonNull
    private static JSONArray createCreditCardArray(
            @NonNull List<CreditCard> creditCards
    ) throws Exception {

        JSONArray array =
                new JSONArray();

        for (CreditCard card : creditCards) {
            if (card == null) {
                continue;
            }

            JSONObject object =
                    new JSONObject();

            object.put(
                    "id",
                    card.getId()
            );

            object.put(
                    "name",
                    safeString(
                            card.getName()
                    )
            );

            object.put(
                    "lastFour",
                    safeString(
                            card.getLastFour()
                    )
            );

            object.put(
                    "accountName",
                    safeString(
                            card.getAccountName()
                    )
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
                    safeString(
                            card.getPaymentAccount()
                    )
            );

            object.put(
                    "reminderDays",
                    card.getReminderDays()
            );

            object.put(
                    "active",
                    card.isActive()
            );

            array.put(object);
        }

        return array;
    }

    @NonNull
    private static JSONArray createCreditCardPaymentArray(
            @NonNull List<CreditCardPayment> payments
    ) throws Exception {

        JSONArray array =
                new JSONArray();

        for (CreditCardPayment payment : payments) {
            if (payment == null) {
                continue;
            }

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
                    safeString(
                            payment.getStatementEndDate()
                    )
            );

            object.put(
                    "amount",
                    payment.getAmount()
            );

            object.put(
                    "paymentDate",
                    safeString(
                            payment.getPaymentDate()
                    )
            );

            object.put(
                    "sourceAccount",
                    safeString(
                            payment.getSourceAccount()
                    )
            );

            object.put(
                    "note",
                    safeString(
                            payment.getNote()
                    )
            );

            array.put(object);
        }

        return array;
    }

    @NonNull
    private static JSONArray createInvestmentArray(
            @NonNull List<InvestmentItem> investments
    ) throws Exception {

        JSONArray array =
                new JSONArray();

        for (InvestmentItem investment : investments) {
            if (investment == null) {
                continue;
            }

            JSONObject object =
                    new JSONObject();

            object.put(
                    "id",
                    safeString(
                            investment.getId()
                    )
            );

            object.put(
                    "name",
                    safeString(
                            investment.getName()
                    )
            );

            object.put(
                    "type",
                    safeString(
                            investment.getType()
                    )
            );

            object.put(
                    "startDate",
                    safeString(
                            investment.getStartDate()
                    )
            );

            object.put(
                    "note",
                    safeString(
                            investment.getNote()
                    )
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

            array.put(object);
        }

        return array;
    }

    @NonNull
    private static byte[] gzipCompress(
            @NonNull byte[] sourceBytes
    ) throws Exception {

        ByteArrayOutputStream outputStream =
                new ByteArrayOutputStream();

        try (GZIPOutputStream gzipOutputStream =
                     new GZIPOutputStream(
                             outputStream
                     )) {

            gzipOutputStream.write(
                    sourceBytes
            );

            gzipOutputStream.finish();
        }

        return outputStream.toByteArray();
    }

    @NonNull
    private static String sha256Hex(
            @NonNull byte[] bytes
    ) throws Exception {

        MessageDigest digest =
                MessageDigest.getInstance(
                        HASH_ALGORITHM
                );

        byte[] hashBytes =
                digest.digest(
                        bytes
                );

        try {
            StringBuilder result =
                    new StringBuilder(
                            hashBytes.length * 2
                    );

            for (byte hashByte : hashBytes) {
                result.append(
                        String.format(
                                Locale.US,
                                "%02x",
                                hashByte & 0xff
                        )
                );
            }

            return result.toString();

        } finally {
            clearBytes(
                    hashBytes
            );
        }
    }

    @NonNull
    private static String createBackupId(
            long createdAtMillis
    ) {
        SimpleDateFormat formatter =
                new SimpleDateFormat(
                        "yyyyMMdd_HHmmss",
                        Locale.US
                );

        formatter.setTimeZone(
                TimeZone.getTimeZone(
                        "UTC"
                )
        );

        return "mmp_"
                + formatter.format(
                new Date(
                        createdAtMillis
                )
        )
                + "_"
                + UUID.randomUUID()
                .toString()
                .replace(
                        "-",
                        ""
                )
                .substring(
                        0,
                        16
                );
    }

    @NonNull
    private static String formatUtcDateTime(
            long timestamp
    ) {
        SimpleDateFormat formatter =
                new SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                        Locale.US
                );

        formatter.setTimeZone(
                TimeZone.getTimeZone(
                        "UTC"
                )
        );

        return formatter.format(
                new Date(timestamp)
        );
    }

    @NonNull
    private static String getAppVersionName(
            @NonNull Context context
    ) {
        try {
            PackageInfo packageInfo =
                    context
                            .getPackageManager()
                            .getPackageInfo(
                                    context.getPackageName(),
                                    0
                            );

            String versionName =
                    packageInfo.versionName;

            return versionName == null
                    ? ""
                    : versionName.trim();

        } catch (Exception exception) {
            return "";
        }
    }

    private static long getAppVersionCode(
            @NonNull Context context
    ) {
        try {
            PackageInfo packageInfo =
                    context
                            .getPackageManager()
                            .getPackageInfo(
                                    context.getPackageName(),
                                    0
                            );

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return packageInfo.getLongVersionCode();
            }

            return packageInfo.versionCode;

        } catch (Exception exception) {
            return 0L;
        }
    }

    @NonNull
    private static String validateFirebaseUserId(
            @NonNull String firebaseUserId
    ) throws PayloadBuildException {

        String cleanUserId =
                firebaseUserId.trim();

        if (cleanUserId.isEmpty()) {
            throw new PayloadBuildException(
                    "Firebase cloud account UID is unavailable."
            );
        }

        if (cleanUserId.length()
                > MAX_FIREBASE_UID_LENGTH) {

            throw new PayloadBuildException(
                    "Firebase cloud account UID exceeds "
                            + "the supported length."
            );
        }

        if (cleanUserId.indexOf('\n') >= 0
                || cleanUserId.indexOf('\r') >= 0
                || cleanUserId.indexOf('\0') >= 0) {

            throw new PayloadBuildException(
                    "Firebase cloud account UID contains "
                            + "unsupported characters."
            );
        }

        return cleanUserId;
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
    private static <T> List<T> safeList(
            @Nullable List<T> source
    ) {
        if (source == null
                || source.isEmpty()) {

            return Collections.emptyList();
        }

        return new ArrayList<>(
                source
        );
    }

    private static void clearBytes(
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

    private static final class BackupSnapshot {

        private List<Transaction> transactions =
                Collections.emptyList();

        private List<ExpenseItem> expenseItems =
                Collections.emptyList();

        private List<Category> categories =
                Collections.emptyList();

        private List<Account> accounts =
                Collections.emptyList();

        private List<Goal> goals =
                Collections.emptyList();

        private List<RecurringTransaction> recurringTransactions =
                Collections.emptyList();

        private List<Budget> budgets =
                Collections.emptyList();

        private List<Loan> loans =
                Collections.emptyList();

        private List<LoanPayment> loanPayments =
                Collections.emptyList();

        private List<Subscription> subscriptions =
                Collections.emptyList();

        private List<CreditCard> creditCards =
                Collections.emptyList();

        private List<CreditCardPayment> creditCardPayments =
                Collections.emptyList();

        private List<InvestmentItem> investments =
                Collections.emptyList();
    }

    /**
     * Non-sensitive backup counts used for status display.
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
        private static RecordCounts fromSnapshot(
                @NonNull BackupSnapshot snapshot
        ) {
            return new RecordCounts(
                    snapshot.transactions.size(),
                    snapshot.expenseItems.size(),
                    snapshot.categories.size(),
                    snapshot.accounts.size(),
                    snapshot.goals.size(),
                    snapshot.recurringTransactions.size(),
                    snapshot.budgets.size(),
                    snapshot.loans.size(),
                    snapshot.loanPayments.size(),
                    snapshot.subscriptions.size(),
                    snapshot.creditCards.size(),
                    snapshot.creditCardPayments.size(),
                    snapshot.investments.size()
            );
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
     * Immutable result passed to the future encryption and uploader layer.
     */
    public static final class Payload {

        private final String backupId;
        private final String firebaseUserId;
        private final long createdAtMillis;
        private final String createdAtUtc;
        private final int payloadVersion;
        private final int databaseVersion;
        private final String appVersionName;
        private final long appVersionCode;
        private final String compressionType;
        private final String hashAlgorithm;
        private final String uncompressedSha256;
        private final String compressedSha256;
        private final int uncompressedByteCount;
        private final byte[] compressedBytes;
        private final RecordCounts recordCounts;

        private Payload(
                @NonNull String backupId,
                @NonNull String firebaseUserId,
                long createdAtMillis,
                @NonNull String createdAtUtc,
                int payloadVersion,
                int databaseVersion,
                @NonNull String appVersionName,
                long appVersionCode,
                @NonNull String compressionType,
                @NonNull String hashAlgorithm,
                @NonNull String uncompressedSha256,
                @NonNull String compressedSha256,
                int uncompressedByteCount,
                @NonNull byte[] compressedBytes,
                @NonNull RecordCounts recordCounts
        ) {
            this.backupId =
                    backupId;

            this.firebaseUserId =
                    firebaseUserId;

            this.createdAtMillis =
                    createdAtMillis;

            this.createdAtUtc =
                    createdAtUtc;

            this.payloadVersion =
                    payloadVersion;

            this.databaseVersion =
                    databaseVersion;

            this.appVersionName =
                    appVersionName;

            this.appVersionCode =
                    appVersionCode;

            this.compressionType =
                    compressionType;

            this.hashAlgorithm =
                    hashAlgorithm;

            this.uncompressedSha256 =
                    uncompressedSha256;

            this.compressedSha256 =
                    compressedSha256;

            this.uncompressedByteCount =
                    uncompressedByteCount;

            this.compressedBytes =
                    Arrays.copyOf(
                            compressedBytes,
                            compressedBytes.length
                    );

            this.recordCounts =
                    recordCounts;
        }

        @NonNull
        public String getBackupId() {
            return backupId;
        }

        @NonNull
        public String getFirebaseUserId() {
            return firebaseUserId;
        }

        public long getCreatedAtMillis() {
            return createdAtMillis;
        }

        @NonNull
        public String getCreatedAtUtc() {
            return createdAtUtc;
        }

        public int getPayloadVersion() {
            return payloadVersion;
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
        public String getCompressionType() {
            return compressionType;
        }

        @NonNull
        public String getHashAlgorithm() {
            return hashAlgorithm;
        }

        @NonNull
        public String getUncompressedSha256() {
            return uncompressedSha256;
        }

        @NonNull
        public String getCompressedSha256() {
            return compressedSha256;
        }

        public int getUncompressedByteCount() {
            return uncompressedByteCount;
        }

        public int getCompressedByteCount() {
            return compressedBytes.length;
        }

        @NonNull
        public byte[] getCompressedBytes() {
            return Arrays.copyOf(
                    compressedBytes,
                    compressedBytes.length
            );
        }

        @NonNull
        public RecordCounts getRecordCounts() {
            return recordCounts;
        }

        /**
         * Clears this object's internal compressed backup bytes after the
         * uploader has encrypted them.
         */
        public void clearSensitiveData() {
            Arrays.fill(
                    compressedBytes,
                    (byte) 0
            );
        }
    }

    public static final class PayloadBuildException
            extends Exception {

        public PayloadBuildException(
                @NonNull String message
        ) {
            super(message);
        }

        public PayloadBuildException(
                @NonNull String message,
                @NonNull Throwable cause
        ) {
            super(
                    message,
                    cause
            );
        }
    }
}