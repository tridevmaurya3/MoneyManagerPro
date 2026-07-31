package com.example.moneymanagerpro.utils;

import androidx.annotation.NonNull;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.moneymanagerpro.database.AppDatabase;
import com.example.moneymanagerpro.model.Account;

import java.util.List;

/**
 * Safely renames an account and updates every database record that stores
 * the account name as text.
 *
 * No Room migration is required because this class does not change the
 * database structure; it only updates existing values inside a transaction.
 */
public final class AccountRenameManager {

    private AccountRenameManager() {
        // Utility class.
    }

    public static void updateAccount(
            @NonNull AppDatabase database,
            @NonNull Account account,
            @NonNull String requestedName,
            double openingBalance,
            @NonNull String colorCode
    ) {
        String oldAccountName = safe(account.getName());
        String newAccountName = safe(requestedName);

        if (oldAccountName.isEmpty()) {
            throw new IllegalArgumentException(
                    "Existing account name is missing"
            );
        }

        if (newAccountName.isEmpty()) {
            throw new IllegalArgumentException(
                    "Please enter account name"
            );
        }

        /*
         * Cash app का default account है।
         * इसका नाम बदलने से कई default selections प्रभावित हो सकते हैं,
         * इसलिए Cash का नाम fixed रखा गया है।
         */
        if ("Cash".equalsIgnoreCase(oldAccountName)
                && !"Cash".equalsIgnoreCase(newAccountName)) {

            throw new IllegalArgumentException(
                    "Cash account name cannot be changed"
            );
        }

        ensureNameIsAvailable(
                database,
                account.getId(),
                newAccountName
        );

        /*
         * Account और उससे जुड़े सभी records एक ही transaction में update होंगे।
         * बीच में कोई error आने पर पूरा operation rollback हो जाएगा।
         */
        database.runInTransaction(() -> {

            if (!oldAccountName.equals(newAccountName)) {

                renameLinkedReferences(
                        database,
                        oldAccountName,
                        newAccountName
                );
            }

            account.setName(newAccountName);
            account.setOpeningBalance(openingBalance);
            account.setColor(colorCode);

            database.accountDao().update(account);
        });
    }

    /**
     * जाँच करता है कि नए नाम वाला कोई दूसरा account पहले से मौजूद तो नहीं है।
     */
    private static void ensureNameIsAvailable(
            @NonNull AppDatabase database,
            int currentAccountId,
            @NonNull String requestedName
    ) {
        List<Account> accounts =
                database.accountDao().getAllAccounts();

        if (accounts == null) {
            return;
        }

        for (Account savedAccount : accounts) {

            if (savedAccount == null
                    || savedAccount.getId() == currentAccountId) {

                continue;
            }

            String savedName =
                    safe(savedAccount.getName());

            if (savedName.equalsIgnoreCase(requestedName)) {

                throw new IllegalArgumentException(
                        "An account with this name already exists"
                );
            }
        }
    }

    /**
     * पुराने account name को सभी linked database tables में नए नाम से बदलता है।
     */
    private static void renameLinkedReferences(
            @NonNull AppDatabase database,
            @NonNull String oldAccountName,
            @NonNull String newAccountName
    ) {
        SupportSQLiteDatabase writableDatabase =
                database
                        .getOpenHelper()
                        .getWritableDatabase();

        /*
         * पुरानी Income, Expense और Transfer transactions।
         */
        updateColumn(
                writableDatabase,
                "transactions",
                "account",
                oldAccountName,
                newAccountName
        );

        /*
         * Future recurring income और expenses।
         */
        updateColumn(
                writableDatabase,
                "recurring_transactions",
                "account",
                oldAccountName,
                newAccountName
        );

        /*
         * Subscription payment account।
         */
        updateColumn(
                writableDatabase,
                "subscriptions",
                "account",
                oldAccountName,
                newAccountName
        );

        /*
         * Loan और EMI payment account।
         */
        updateColumn(
                writableDatabase,
                "loan_payments",
                "account",
                oldAccountName,
                newAccountName
        );

        /*
         * Credit card statement payment किस account से हुआ था।
         */
        updateColumn(
                writableDatabase,
                "credit_card_payments",
                "sourceAccount",
                oldAccountName,
                newAccountName
        );

        /*
         * Credit card bill payment के लिए selected bank/cash account।
         */
        updateColumn(
                writableDatabase,
                "credit_cards",
                "paymentAccount",
                oldAccountName,
                newAccountName
        );

        /*
         * Credit Card Manager और Accounts screen के बीच managed account link।
         */
        updateColumn(
                writableDatabase,
                "credit_cards",
                "accountName",
                oldAccountName,
                newAccountName
        );
    }

    /**
     * किसी table के account-name column में पुराने value को नए value से बदलता है।
     *
     * LOWER और TRIM लगाने से:
     * SBI CARD
     * SBI Card
     * SBI Card के आगे-पीछे space
     *
     * सभी एक ही account माने जाएँगे।
     */
    private static void updateColumn(
            @NonNull SupportSQLiteDatabase database,
            @NonNull String tableName,
            @NonNull String columnName,
            @NonNull String oldValue,
            @NonNull String newValue
    ) {
        String sql =
                "UPDATE `" + tableName + "` "
                        + "SET `" + columnName + "` = ? "
                        + "WHERE LOWER(TRIM(`" + columnName + "`)) "
                        + "= LOWER(TRIM(?))";

        database.execSQL(
                sql,
                new Object[]{
                        newValue,
                        oldValue
                }
        );
    }

    @NonNull
    private static String safe(String value) {

        return value == null
                ? ""
                : value.trim();
    }
}