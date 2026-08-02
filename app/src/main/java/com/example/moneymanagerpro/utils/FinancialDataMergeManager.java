package com.example.moneymanagerpro.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.moneymanagerpro.database.AppDatabase;
import com.example.moneymanagerpro.model.Account;
import com.example.moneymanagerpro.model.CreditCard;

import java.util.Locale;

public final class FinancialDataMergeManager {

    private FinancialDataMergeManager() {
    }

    @NonNull
    public static Preview preview(
            @NonNull AppDatabase database,
            @NonNull String accountName
    ) {
        SupportSQLiteDatabase sql = database.getOpenHelper().getReadableDatabase();
        return new Preview(
                count(sql, "transactions", "account", accountName),
                count(sql, "recurring_transactions", "account", accountName),
                count(sql, "subscriptions", "account", accountName),
                count(sql, "loan_payments", "account", accountName),
                count(sql, "credit_card_payments", "sourceAccount", accountName),
                count(sql, "credit_cards", "paymentAccount", accountName),
                count(sql, "credit_cards", "accountName", accountName)
        );
    }

    public static void mergeAccounts(
            @NonNull AppDatabase database,
            @NonNull Account source,
            @NonNull Account target
    ) {
        if (source.getId() == target.getId()) {
            throw new IllegalArgumentException("Select two different accounts");
        }
        if ("Cash".equalsIgnoreCase(safe(source.getName()))) {
            throw new IllegalArgumentException("Cash cannot be merged into another account");
        }
        if (source.isArchived() || target.isArchived()) {
            throw new IllegalArgumentException("Restore archived accounts before merging");
        }

        database.runInTransaction(() -> {
            renameEveryReference(
                    database.getOpenHelper().getWritableDatabase(),
                    source.getName(),
                    target.getName()
            );
            target.setOpeningBalance(
                    target.getOpeningBalance() + source.getOpeningBalance()
            );
            database.accountDao().update(target);
            database.accountDao().delete(source);
        });
    }

    public static void setArchived(
            @NonNull AppDatabase database,
            @NonNull Account account,
            boolean archived
    ) {
        if ("Cash".equalsIgnoreCase(safe(account.getName()))) {
            throw new IllegalArgumentException("Cash account cannot be archived");
        }
        CreditCard linked = database.creditCardDao().findByAccountName(account.getName());
        if (archived && linked != null && linked.isActive()) {
            throw new IllegalArgumentException(
                    "Deactivate or remap the linked credit card before archiving"
            );
        }
        account.setArchived(archived);
        database.accountDao().update(account);
    }

    public static void deleteIfUnused(
            @NonNull AppDatabase database,
            @NonNull Account account
    ) {
        if ("Cash".equalsIgnoreCase(safe(account.getName()))) {
            throw new IllegalArgumentException("Cash account cannot be deleted");
        }
        Preview preview = preview(database, account.getName());
        if (preview.totalReferences() > 0) {
            throw new IllegalArgumentException(
                    "This account is still linked to "
                            + preview.totalReferences()
                            + " records. Merge or archive it instead."
            );
        }
        if (Math.abs(account.getOpeningBalance()) > 0.005d) {
            throw new IllegalArgumentException(
                    "Set opening balance to zero before permanent deletion"
            );
        }
        database.accountDao().delete(account);
    }

    public static void mapOldAccountToCard(
            @NonNull AppDatabase database,
            @NonNull Account oldAccount,
            @NonNull CreditCard card
    ) {
        Account canonical = database.accountDao().findByName(card.getAccountName());
        if (canonical == null) {
            throw new IllegalStateException("Linked credit card account is missing");
        }
        if (oldAccount.getId() == canonical.getId()) {
            throw new IllegalArgumentException("This account is already linked to the card");
        }
        mergeAccounts(database, oldAccount, canonical);
    }

    public static void renameCardAndLinkedAccount(
            @NonNull AppDatabase database,
            @NonNull CreditCard card,
            @NonNull String requestedName
    ) {
        String newName = safe(requestedName);
        if (newName.length() < 2) {
            throw new IllegalArgumentException("Enter a valid card name");
        }
        String oldCardName = safe(card.getName());
        String oldAccountName = safe(card.getAccountName());
        String newAccountName = newName + " •••• " + safe(card.getLastFour());

        Account linked = database.accountDao().findByName(oldAccountName);
        Account collision = database.accountDao().findByName(newAccountName);
        if (collision != null && (linked == null || collision.getId() != linked.getId())) {
            throw new IllegalArgumentException(
                    "An account with the new card name already exists"
            );
        }

        database.runInTransaction(() -> {
            SupportSQLiteDatabase sql =
                    database.getOpenHelper().getWritableDatabase();

            if (linked != null && !oldAccountName.equalsIgnoreCase(newAccountName)) {
                renameEveryReference(sql, oldAccountName, newAccountName);
                linked.setName(newAccountName);
                database.accountDao().update(linked);
            }

            // Transactions previously categorized using only the old card name
            // remain connected to the renamed card.
            sql.execSQL(
                    "UPDATE transactions SET category = ? "
                            + "WHERE LOWER(TRIM(category)) = LOWER(TRIM(?))",
                    new Object[]{newName, oldCardName}
            );

            card.setName(newName);
            card.setAccountName(newAccountName);
            database.creditCardDao().update(card);
        });
    }

    private static void renameEveryReference(
            @NonNull SupportSQLiteDatabase database,
            @NonNull String oldName,
            @NonNull String newName
    ) {
        update(database, "transactions", "account", oldName, newName);
        update(database, "recurring_transactions", "account", oldName, newName);
        update(database, "subscriptions", "account", oldName, newName);
        update(database, "loan_payments", "account", oldName, newName);
        update(database, "credit_card_payments", "sourceAccount", oldName, newName);
        update(database, "credit_cards", "paymentAccount", oldName, newName);
        update(database, "credit_cards", "accountName", oldName, newName);
    }

    private static void update(
            SupportSQLiteDatabase database,
            String table,
            String column,
            String oldName,
            String newName
    ) {
        database.execSQL(
                "UPDATE " + table + " SET " + column + " = ? "
                        + "WHERE LOWER(TRIM(" + column + ")) = LOWER(TRIM(?))",
                new Object[]{newName, oldName}
        );
    }

    private static int count(
            SupportSQLiteDatabase database,
            String table,
            String column,
            String name
    ) {
        try (android.database.Cursor cursor = database.query(
                "SELECT COUNT(*) FROM " + table
                        + " WHERE LOWER(TRIM(" + column + ")) = LOWER(TRIM(?))",
                new Object[]{name}
        )) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    @NonNull
    private static String safe(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Preview {
        public final int transactions;
        public final int recurring;
        public final int subscriptions;
        public final int loanPayments;
        public final int cardPayments;
        public final int paymentAccountLinks;
        public final int managedCardLinks;

        Preview(
                int transactions,
                int recurring,
                int subscriptions,
                int loanPayments,
                int cardPayments,
                int paymentAccountLinks,
                int managedCardLinks
        ) {
            this.transactions = transactions;
            this.recurring = recurring;
            this.subscriptions = subscriptions;
            this.loanPayments = loanPayments;
            this.cardPayments = cardPayments;
            this.paymentAccountLinks = paymentAccountLinks;
            this.managedCardLinks = managedCardLinks;
        }

        public int totalReferences() {
            return transactions + recurring + subscriptions + loanPayments
                    + cardPayments + paymentAccountLinks + managedCardLinks;
        }

        @NonNull
        public String describe() {
            return String.format(
                    Locale.ENGLISH,
                    "Transactions: %d\nRecurring: %d\nSubscriptions: %d\n"
                            + "Loan payments: %d\nCard payments: %d\n"
                            + "Card links: %d",
                    transactions,
                    recurring,
                    subscriptions,
                    loanPayments,
                    cardPayments,
                    paymentAccountLinks + managedCardLinks
            );
        }
    }
}
