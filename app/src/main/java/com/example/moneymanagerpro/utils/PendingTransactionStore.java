package com.example.moneymanagerpro.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Stores detected bank/payment transactions locally until the user
 * reviews and confirms or rejects them.
 *
 * Privacy rules:
 *
 * 1. Complete SMS text is never stored.
 * 2. Complete notification text is never stored.
 * 3. Only parsed transaction fields are stored.
 * 4. No information is uploaded to Firebase or any server.
 * 5. Transactions are never added automatically to the finance database.
 * 6. Old pending suggestions are automatically removed.
 */
public final class PendingTransactionStore {

    private static final String PREFERENCES_NAME =
            "pending_transaction_store";

    private static final String KEY_PENDING_TRANSACTIONS =
            "pending_transactions";

    /**
     * Pending suggestions older than 30 days will be removed.
     */
    private static final long RETENTION_PERIOD_MILLIS =
            30L * 24L * 60L * 60L * 1000L;

    /**
     * Notifications for the same payment may be updated or posted
     * more than once. Similar results received within 10 minutes are
     * considered duplicates.
     */
    private static final long DUPLICATE_WINDOW_MILLIS =
            10L * 60L * 1000L;

    /**
     * Keeps local storage small and manageable.
     */
    private static final int MAX_PENDING_TRANSACTIONS = 50;

    private static final Object STORAGE_LOCK =
            new Object();

    private PendingTransactionStore() {
        // Utility class.
    }

    /**
     * Saves a valid parser result in local pending storage.
     *
     * The method never writes directly to the Room transaction database.
     */
    @NonNull
    public static SaveResult save(
            @NonNull Context context,
            @Nullable BankTransactionParser.Result parserResult
    ) {
        if (parserResult == null
                || !parserResult.isValid()
                || parserResult.getAmount() <= 0
                || !isSupportedTransactionType(
                parserResult.getTransactionType()
        )) {
            return SaveResult.INVALID_RESULT;
        }

        PendingTransaction newTransaction =
                PendingTransaction.fromParserResult(
                        parserResult
                );

        synchronized (STORAGE_LOCK) {
            List<PendingTransaction> pendingTransactions =
                    readInternal(context);

            removeExpiredInternal(
                    pendingTransactions,
                    System.currentTimeMillis()
            );

            for (PendingTransaction savedTransaction
                    : pendingTransactions) {

                if (isDuplicate(
                        savedTransaction,
                        newTransaction
                )) {
                    return SaveResult.DUPLICATE;
                }
            }

            pendingTransactions.add(
                    0,
                    newTransaction
            );

            sortNewestFirst(
                    pendingTransactions
            );

            while (pendingTransactions.size()
                    > MAX_PENDING_TRANSACTIONS) {

                pendingTransactions.remove(
                        pendingTransactions.size() - 1
                );
            }

            boolean saved =
                    writeInternal(
                            context,
                            pendingTransactions
                    );

            return saved
                    ? SaveResult.SAVED
                    : SaveResult.STORAGE_ERROR;
        }
    }

    /**
     * Returns all pending transactions, newest first.
     */
    @NonNull
    public static List<PendingTransaction> getAll(
            @NonNull Context context
    ) {
        synchronized (STORAGE_LOCK) {
            List<PendingTransaction> pendingTransactions =
                    readInternal(context);

            boolean removedExpired =
                    removeExpiredInternal(
                            pendingTransactions,
                            System.currentTimeMillis()
                    );

            sortNewestFirst(
                    pendingTransactions
            );

            if (removedExpired) {
                writeInternal(
                        context,
                        pendingTransactions
                );
            }

            return Collections.unmodifiableList(
                    new ArrayList<>(
                            pendingTransactions
                    )
            );
        }
    }

    /**
     * Finds one pending transaction by its unique ID.
     */
    @Nullable
    public static PendingTransaction findById(
            @NonNull Context context,
            @Nullable String transactionId
    ) {
        String requestedId =
                safe(transactionId);

        if (requestedId.isEmpty()) {
            return null;
        }

        synchronized (STORAGE_LOCK) {
            List<PendingTransaction> pendingTransactions =
                    readInternal(context);

            boolean removedExpired =
                    removeExpiredInternal(
                            pendingTransactions,
                            System.currentTimeMillis()
                    );

            if (removedExpired) {
                writeInternal(
                        context,
                        pendingTransactions
                );
            }

            for (PendingTransaction transaction
                    : pendingTransactions) {

                if (requestedId.equals(
                        transaction.getId()
                )) {
                    return transaction;
                }
            }

            return null;
        }
    }

    /**
     * Removes a transaction after it has been confirmed or rejected.
     */
    public static boolean remove(
            @NonNull Context context,
            @Nullable String transactionId
    ) {
        String requestedId =
                safe(transactionId);

        if (requestedId.isEmpty()) {
            return false;
        }

        synchronized (STORAGE_LOCK) {
            List<PendingTransaction> pendingTransactions =
                    readInternal(context);

            boolean removed = false;

            Iterator<PendingTransaction> iterator =
                    pendingTransactions.iterator();

            while (iterator.hasNext()) {
                PendingTransaction transaction =
                        iterator.next();

                if (requestedId.equals(
                        transaction.getId()
                )) {
                    iterator.remove();
                    removed = true;
                    break;
                }
            }

            if (!removed) {
                return false;
            }

            return writeInternal(
                    context,
                    pendingTransactions
            );
        }
    }

    /**
     * Updates the suggested category after the user changes it
     * on the review screen.
     */
    public static boolean updateSuggestedCategory(
            @NonNull Context context,
            @Nullable String transactionId,
            @Nullable String category
    ) {
        String requestedId =
                safe(transactionId);

        String newCategory =
                limitLength(
                        category,
                        60
                );

        if (requestedId.isEmpty()
                || newCategory.isEmpty()) {

            return false;
        }

        synchronized (STORAGE_LOCK) {
            List<PendingTransaction> pendingTransactions =
                    readInternal(context);

            boolean updated = false;

            for (int index = 0;
                 index < pendingTransactions.size();
                 index++) {

                PendingTransaction transaction =
                        pendingTransactions.get(index);

                if (!requestedId.equals(
                        transaction.getId()
                )) {
                    continue;
                }

                PendingTransaction updatedTransaction =
                        transaction.copyWithCategory(
                                newCategory
                        );

                pendingTransactions.set(
                        index,
                        updatedTransaction
                );

                updated = true;
                break;
            }

            if (!updated) {
                return false;
            }

            return writeInternal(
                    context,
                    pendingTransactions
            );
        }
    }

    /**
     * Returns the current number of pending suggestions.
     */
    public static int getCount(
            @NonNull Context context
    ) {
        return getAll(context).size();
    }

    /**
     * Deletes all pending transaction suggestions.
     */
    public static boolean clear(
            @NonNull Context context
    ) {
        synchronized (STORAGE_LOCK) {
            return getPreferences(context)
                    .edit()
                    .remove(
                            KEY_PENDING_TRANSACTIONS
                    )
                    .commit();
        }
    }

    /**
     * Removes expired suggestions manually.
     */
    public static int removeExpired(
            @NonNull Context context
    ) {
        synchronized (STORAGE_LOCK) {
            List<PendingTransaction> pendingTransactions =
                    readInternal(context);

            int previousSize =
                    pendingTransactions.size();

            removeExpiredInternal(
                    pendingTransactions,
                    System.currentTimeMillis()
            );

            int removedCount =
                    previousSize
                            - pendingTransactions.size();

            if (removedCount > 0) {
                writeInternal(
                        context,
                        pendingTransactions
                );
            }

            return removedCount;
        }
    }

    private static boolean removeExpiredInternal(
            @NonNull List<PendingTransaction> transactions,
            long currentTime
    ) {
        boolean changed = false;

        Iterator<PendingTransaction> iterator =
                transactions.iterator();

        while (iterator.hasNext()) {
            PendingTransaction transaction =
                    iterator.next();

            long referenceTime =
                    transaction.getDetectedAt() > 0
                            ? transaction.getDetectedAt()
                            : transaction.getCreatedAt();

            boolean invalid =
                    transaction.getAmount() <= 0
                            || !isSupportedTransactionType(
                            transaction.getTransactionType()
                    );

            boolean expired =
                    referenceTime <= 0
                            || currentTime - referenceTime
                            > RETENTION_PERIOD_MILLIS;

            if (invalid || expired) {
                iterator.remove();
                changed = true;
            }
        }

        return changed;
    }

    /**
     * Duplicate protection.
     *
     * When UTR/reference is available, matching reference + amount +
     * transaction type is considered a duplicate.
     *
     * Without UTR, amount/type/time plus merchant, account or source
     * similarity is used.
     */
    private static boolean isDuplicate(
            @NonNull PendingTransaction first,
            @NonNull PendingTransaction second
    ) {
        if (!sameTransactionType(
                first.getTransactionType(),
                second.getTransactionType()
        )) {
            return false;
        }

        if (!sameAmount(
                first.getAmount(),
                second.getAmount()
        )) {
            return false;
        }

        String firstReference =
                normalizeForComparison(
                        first.getReference()
                );

        String secondReference =
                normalizeForComparison(
                        second.getReference()
                );

        if (!firstReference.isEmpty()
                && !secondReference.isEmpty()) {

            return firstReference.equals(
                    secondReference
            );
        }

        long timeDifference =
                Math.abs(
                        first.getDetectedAt()
                                - second.getDetectedAt()
                );

        if (timeDifference
                > DUPLICATE_WINDOW_MILLIS) {

            return false;
        }

        boolean sameMerchant =
                sameNonEmptyValue(
                        first.getMerchant(),
                        second.getMerchant()
                );

        boolean sameAccount =
                sameNonEmptyValue(
                        first.getAccountHint(),
                        second.getAccountHint()
                );

        boolean sameSourcePackage =
                sameNonEmptyValue(
                        first.getSourcePackage(),
                        second.getSourcePackage()
                );

        boolean sameSourceName =
                sameNonEmptyValue(
                        first.getSourceName(),
                        second.getSourceName()
                );

        if (sameMerchant || sameAccount) {
            return true;
        }

        boolean bothMerchantValuesMissing =
                safe(first.getMerchant()).isEmpty()
                        && safe(
                        second.getMerchant()
                ).isEmpty();

        boolean bothAccountValuesMissing =
                safe(first.getAccountHint()).isEmpty()
                        && safe(
                        second.getAccountHint()
                ).isEmpty();

        return bothMerchantValuesMissing
                && bothAccountValuesMissing
                && (sameSourcePackage
                || sameSourceName);
    }

    private static boolean sameTransactionType(
            @Nullable String first,
            @Nullable String second
    ) {
        return safe(first).equalsIgnoreCase(
                safe(second)
        );
    }

    private static boolean sameAmount(
            double first,
            double second
    ) {
        long firstPaise =
                Math.round(
                        first * 100d
                );

        long secondPaise =
                Math.round(
                        second * 100d
                );

        return firstPaise == secondPaise;
    }

    private static boolean sameNonEmptyValue(
            @Nullable String first,
            @Nullable String second
    ) {
        String firstValue =
                normalizeForComparison(first);

        String secondValue =
                normalizeForComparison(second);

        return !firstValue.isEmpty()
                && !secondValue.isEmpty()
                && firstValue.equals(secondValue);
    }

    @NonNull
    private static String normalizeForComparison(
            @Nullable String value
    ) {
        return safe(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll(
                        "[^a-z0-9]+",
                        ""
                )
                .trim();
    }

    private static boolean isSupportedTransactionType(
            @Nullable String transactionType
    ) {
        return BankTransactionParser.TYPE_EXPENSE
                .equalsIgnoreCase(
                        safe(transactionType)
                )
                || BankTransactionParser.TYPE_INCOME
                .equalsIgnoreCase(
                        safe(transactionType)
                );
    }

    private static void sortNewestFirst(
            @NonNull List<PendingTransaction> transactions
    ) {
        Collections.sort(
                transactions,
                new Comparator<PendingTransaction>() {
                    @Override
                    public int compare(
                            PendingTransaction first,
                            PendingTransaction second
                    ) {
                        return Long.compare(
                                second.getDetectedAt(),
                                first.getDetectedAt()
                        );
                    }
                }
        );
    }

    @NonNull
    private static List<PendingTransaction> readInternal(
            @NonNull Context context
    ) {
        List<PendingTransaction> transactions =
                new ArrayList<>();

        String savedJson =
                getPreferences(context)
                        .getString(
                                KEY_PENDING_TRANSACTIONS,
                                "[]"
                        );

        if (savedJson == null
                || savedJson.trim().isEmpty()) {

            return transactions;
        }

        try {
            JSONArray jsonArray =
                    new JSONArray(savedJson);

            for (int index = 0;
                 index < jsonArray.length();
                 index++) {

                JSONObject jsonObject =
                        jsonArray.optJSONObject(index);

                if (jsonObject == null) {
                    continue;
                }

                PendingTransaction transaction =
                        PendingTransaction.fromJson(
                                jsonObject
                        );

                if (transaction != null) {
                    transactions.add(
                            transaction
                    );
                }
            }

        } catch (JSONException ignored) {
            /*
             * Corrupted pending storage will not crash the app.
             * A fresh empty list will be returned.
             */
        }

        return transactions;
    }

    private static boolean writeInternal(
            @NonNull Context context,
            @NonNull List<PendingTransaction> transactions
    ) {
        JSONArray jsonArray =
                new JSONArray();

        for (PendingTransaction transaction
                : transactions) {

            try {
                jsonArray.put(
                        transaction.toJson()
                );

            } catch (JSONException ignored) {
                /*
                 * One malformed item must not prevent other valid
                 * pending transactions from being saved.
                 */
            }
        }

        /*
         * commit() is intentionally used instead of apply().
         *
         * NotificationListenerService may be stopped by Android
         * immediately after processing the notification. commit()
         * ensures the pending result has been written before returning.
         */
        return getPreferences(context)
                .edit()
                .putString(
                        KEY_PENDING_TRANSACTIONS,
                        jsonArray.toString()
                )
                .commit();
    }

    @NonNull
    private static SharedPreferences getPreferences(
            @NonNull Context context
    ) {
        return context
                .getApplicationContext()
                .getSharedPreferences(
                        PREFERENCES_NAME,
                        Context.MODE_PRIVATE
                );
    }

    @NonNull
    private static String safe(
            @Nullable String value
    ) {
        return value == null
                ? ""
                : value.trim();
    }

    @NonNull
    private static String limitLength(
            @Nullable String value,
            int maximumLength
    ) {
        String cleanValue =
                safe(value)
                        .replace('\n', ' ')
                        .replace('\r', ' ')
                        .replaceAll(
                                "\\s+",
                                " "
                        )
                        .trim();

        if (cleanValue.length()
                <= maximumLength) {

            return cleanValue;
        }

        return cleanValue
                .substring(
                        0,
                        maximumLength
                )
                .trim();
    }

    public enum SaveResult {

        /**
         * A new pending transaction was stored.
         */
        SAVED,

        /**
         * The same transaction was already present.
         */
        DUPLICATE,

        /**
         * Parser result was invalid or incomplete.
         */
        INVALID_RESULT,

        /**
         * Android could not write the result to local storage.
         */
        STORAGE_ERROR
    }

    /**
     * Immutable transaction suggestion used by the future
     * Pending Transaction Review screen.
     */
    public static final class PendingTransaction {

        private static final String JSON_ID =
                "id";

        private static final String JSON_TRANSACTION_TYPE =
                "transactionType";

        private static final String JSON_AMOUNT =
                "amount";

        private static final String JSON_MERCHANT =
                "merchant";

        private static final String JSON_ACCOUNT_HINT =
                "accountHint";

        private static final String JSON_REFERENCE =
                "reference";

        private static final String JSON_SOURCE_TYPE =
                "sourceType";

        private static final String JSON_SOURCE_NAME =
                "sourceName";

        private static final String JSON_SOURCE_PACKAGE =
                "sourcePackage";

        private static final String JSON_SUGGESTED_CATEGORY =
                "suggestedCategory";

        private static final String JSON_CONFIDENCE =
                "confidence";

        private static final String JSON_DETECTED_AT =
                "detectedAt";

        private static final String JSON_CREATED_AT =
                "createdAt";

        private final String id;

        private final String transactionType;

        private final double amount;

        private final String merchant;

        private final String accountHint;

        private final String reference;

        private final String sourceType;

        private final String sourceName;

        private final String sourcePackage;

        private final String suggestedCategory;

        private final int confidence;

        private final long detectedAt;

        private final long createdAt;

        private PendingTransaction(
                @NonNull String id,
                @NonNull String transactionType,
                double amount,
                @NonNull String merchant,
                @NonNull String accountHint,
                @NonNull String reference,
                @NonNull String sourceType,
                @NonNull String sourceName,
                @NonNull String sourcePackage,
                @NonNull String suggestedCategory,
                int confidence,
                long detectedAt,
                long createdAt
        ) {
            this.id = id;
            this.transactionType = transactionType;
            this.amount = amount;
            this.merchant = merchant;
            this.accountHint = accountHint;
            this.reference = reference;
            this.sourceType = sourceType;
            this.sourceName = sourceName;
            this.sourcePackage = sourcePackage;
            this.suggestedCategory = suggestedCategory;
            this.confidence = confidence;
            this.detectedAt = detectedAt;
            this.createdAt = createdAt;
        }

        @NonNull
        private static PendingTransaction fromParserResult(
                @NonNull BankTransactionParser.Result result
        ) {
            long currentTime =
                    System.currentTimeMillis();

            long detectedTime =
                    result.getDetectedAt() > 0
                            ? result.getDetectedAt()
                            : currentTime;

            return new PendingTransaction(
                    UUID.randomUUID().toString(),
                    limitLength(
                            result.getTransactionType(),
                            20
                    ).toUpperCase(Locale.ROOT),
                    result.getAmount(),
                    limitLength(
                            result.getMerchant(),
                            80
                    ),
                    limitLength(
                            result.getAccountHint(),
                            40
                    ),
                    limitLength(
                            result.getReference(),
                            80
                    ),
                    limitLength(
                            result.getSourceType(),
                            30
                    ),
                    limitLength(
                            result.getSourceName(),
                            60
                    ),
                    limitLength(
                            result.getSourcePackage(),
                            120
                    ),
                    limitLength(
                            result.getSuggestedCategory(),
                            60
                    ),
                    Math.max(
                            0,
                            Math.min(
                                    result.getConfidence(),
                                    100
                            )
                    ),
                    detectedTime,
                    currentTime
            );
        }

        @Nullable
        private static PendingTransaction fromJson(
                @NonNull JSONObject jsonObject
        ) {
            String id =
                    safe(
                            jsonObject.optString(
                                    JSON_ID,
                                    ""
                            )
                    );

            String transactionType =
                    safe(
                            jsonObject.optString(
                                    JSON_TRANSACTION_TYPE,
                                    ""
                            )
                    );

            double amount =
                    jsonObject.optDouble(
                            JSON_AMOUNT,
                            0
                    );

            if (id.isEmpty()
                    || amount <= 0
                    || !isSupportedTransactionType(
                    transactionType
            )) {
                return null;
            }

            long detectedAt =
                    jsonObject.optLong(
                            JSON_DETECTED_AT,
                            0
                    );

            long createdAt =
                    jsonObject.optLong(
                            JSON_CREATED_AT,
                            detectedAt
                    );

            return new PendingTransaction(
                    id,
                    transactionType,
                    amount,
                    limitLength(
                            jsonObject.optString(
                                    JSON_MERCHANT,
                                    ""
                            ),
                            80
                    ),
                    limitLength(
                            jsonObject.optString(
                                    JSON_ACCOUNT_HINT,
                                    ""
                            ),
                            40
                    ),
                    limitLength(
                            jsonObject.optString(
                                    JSON_REFERENCE,
                                    ""
                            ),
                            80
                    ),
                    limitLength(
                            jsonObject.optString(
                                    JSON_SOURCE_TYPE,
                                    ""
                            ),
                            30
                    ),
                    limitLength(
                            jsonObject.optString(
                                    JSON_SOURCE_NAME,
                                    ""
                            ),
                            60
                    ),
                    limitLength(
                            jsonObject.optString(
                                    JSON_SOURCE_PACKAGE,
                                    ""
                            ),
                            120
                    ),
                    limitLength(
                            jsonObject.optString(
                                    JSON_SUGGESTED_CATEGORY,
                                    ""
                            ),
                            60
                    ),
                    Math.max(
                            0,
                            Math.min(
                                    jsonObject.optInt(
                                            JSON_CONFIDENCE,
                                            0
                                    ),
                                    100
                            )
                    ),
                    detectedAt,
                    createdAt
            );
        }

        @NonNull
        private JSONObject toJson()
                throws JSONException {

            JSONObject jsonObject =
                    new JSONObject();

            jsonObject.put(
                    JSON_ID,
                    id
            );

            jsonObject.put(
                    JSON_TRANSACTION_TYPE,
                    transactionType
            );

            jsonObject.put(
                    JSON_AMOUNT,
                    amount
            );

            jsonObject.put(
                    JSON_MERCHANT,
                    merchant
            );

            jsonObject.put(
                    JSON_ACCOUNT_HINT,
                    accountHint
            );

            jsonObject.put(
                    JSON_REFERENCE,
                    reference
            );

            jsonObject.put(
                    JSON_SOURCE_TYPE,
                    sourceType
            );

            jsonObject.put(
                    JSON_SOURCE_NAME,
                    sourceName
            );

            jsonObject.put(
                    JSON_SOURCE_PACKAGE,
                    sourcePackage
            );

            jsonObject.put(
                    JSON_SUGGESTED_CATEGORY,
                    suggestedCategory
            );

            jsonObject.put(
                    JSON_CONFIDENCE,
                    confidence
            );

            jsonObject.put(
                    JSON_DETECTED_AT,
                    detectedAt
            );

            jsonObject.put(
                    JSON_CREATED_AT,
                    createdAt
            );

            return jsonObject;
        }

        @NonNull
        private PendingTransaction copyWithCategory(
                @NonNull String newCategory
        ) {
            return new PendingTransaction(
                    id,
                    transactionType,
                    amount,
                    merchant,
                    accountHint,
                    reference,
                    sourceType,
                    sourceName,
                    sourcePackage,
                    limitLength(
                            newCategory,
                            60
                    ),
                    confidence,
                    detectedAt,
                    createdAt
            );
        }

        @NonNull
        public String getId() {
            return id;
        }

        @NonNull
        public String getTransactionType() {
            return transactionType;
        }

        public double getAmount() {
            return amount;
        }

        @NonNull
        public String getMerchant() {
            return merchant;
        }

        @NonNull
        public String getAccountHint() {
            return accountHint;
        }

        @NonNull
        public String getReference() {
            return reference;
        }

        @NonNull
        public String getSourceType() {
            return sourceType;
        }

        @NonNull
        public String getSourceName() {
            return sourceName;
        }

        @NonNull
        public String getSourcePackage() {
            return sourcePackage;
        }

        @NonNull
        public String getSuggestedCategory() {
            return suggestedCategory;
        }

        public int getConfidence() {
            return confidence;
        }

        public long getDetectedAt() {
            return detectedAt;
        }

        public long getCreatedAt() {
            return createdAt;
        }

        public boolean isExpense() {
            return BankTransactionParser.TYPE_EXPENSE
                    .equalsIgnoreCase(
                            transactionType
                    );
        }

        public boolean isIncome() {
            return BankTransactionParser.TYPE_INCOME
                    .equalsIgnoreCase(
                            transactionType
                    );
        }
    }
}