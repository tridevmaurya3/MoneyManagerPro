package com.example.moneymanagerpro.account;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.moneymanagerpro.database.DatabaseClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Keeps Credit Card records and Accounts backed by one canonical account name.
 *
 * A card uses "Card Name •••• 1234" as its canonical transaction account.
 * If an older manually-created Credit Card account uses only "Card Name",
 * all linked finance rows are moved to the canonical account and the duplicate
 * account row is removed. The operation is local, transactional and idempotent.
 */
public final class CreditCardAccountSyncInitializer extends ContentProvider {

    private static final AtomicBoolean SYNC_STARTED = new AtomicBoolean(false);

    @Override
    public boolean onCreate() {
        if (getContext() == null) return false;

        android.content.Context appContext = getContext().getApplicationContext();
        if (SYNC_STARTED.compareAndSet(false, true)) {
            new Thread(() -> {
                try {
                    SupportSQLiteDatabase database = DatabaseClient
                            .getInstance(appContext)
                            .getAppDatabase()
                            .getOpenHelper()
                            .getWritableDatabase();
                    synchronize(database);
                } catch (Exception ignored) {
                    // A later process launch safely retries this idempotent sync.
                } finally {
                    SYNC_STARTED.set(false);
                }
            }, "card-account-sync").start();
        }

        return true;
    }

    private void synchronize(@NonNull SupportSQLiteDatabase database) {
        List<CardAccount> cards = new ArrayList<>();

        try (Cursor cursor = database.query(
                "SELECT name, accountName FROM credit_cards WHERE active = 1"
        )) {
            int nameIndex = cursor.getColumnIndex("name");
            int accountIndex = cursor.getColumnIndex("accountName");

            while (cursor.moveToNext()) {
                String cardName = clean(cursor.getString(nameIndex));
                String canonicalAccount = clean(cursor.getString(accountIndex));

                if (!cardName.isEmpty()
                        && !canonicalAccount.isEmpty()
                        && !cardName.equalsIgnoreCase(canonicalAccount)) {
                    cards.add(new CardAccount(cardName, canonicalAccount));
                }
            }
        }

        database.beginTransaction();
        try {
            for (CardAccount card : cards) {
                mergeLegacyCardAccount(database, card);
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    private void mergeLegacyCardAccount(
            @NonNull SupportSQLiteDatabase database,
            @NonNull CardAccount card
    ) {
        AccountRow canonical = findAccount(database, card.canonicalAccount);
        AccountRow legacy = findAccount(database, card.cardName);

        if (canonical == null || legacy == null || canonical.id == legacy.id) return;

        // Only merge an account that was explicitly created as a Credit Card.
        // This prevents a bank account with the same bank/card label being removed.
        if (!"credit card".equalsIgnoreCase(legacy.type)) return;

        Object[] renameArgs = {card.canonicalAccount, legacy.name};

        database.execSQL(
                "UPDATE transactions SET account = ? WHERE account = ? COLLATE NOCASE",
                renameArgs
        );
        database.execSQL(
                "UPDATE recurring_transactions SET account = ? WHERE account = ? COLLATE NOCASE",
                renameArgs
        );
        database.execSQL(
                "UPDATE subscriptions SET account = ? WHERE account = ? COLLATE NOCASE",
                renameArgs
        );
        database.execSQL(
                "UPDATE loan_payments SET account = ? WHERE account = ? COLLATE NOCASE",
                renameArgs
        );
        database.execSQL(
                "UPDATE credit_card_payments SET sourceAccount = ? WHERE sourceAccount = ? COLLATE NOCASE",
                renameArgs
        );
        database.execSQL(
                "UPDATE credit_cards SET paymentAccount = ? WHERE paymentAccount = ? COLLATE NOCASE",
                renameArgs
        );

        // Preserve both opening balances because historical transactions from
        // both aliases now belong to the single canonical card account.
        database.execSQL(
                "UPDATE accounts SET openingBalance = openingBalance + ? " +
                        "WHERE id = ?",
                new Object[]{legacy.openingBalance, canonical.id}
        );

        database.execSQL(
                "DELETE FROM accounts WHERE id = ?",
                new Object[]{legacy.id}
        );
    }

    @Nullable
    private AccountRow findAccount(
            @NonNull SupportSQLiteDatabase database,
            @NonNull String name
    ) {
        try (Cursor cursor = database.query(
                "SELECT id, name, type, openingBalance FROM accounts " +
                        "WHERE trim(name) = trim(?) COLLATE NOCASE ORDER BY id LIMIT 1",
                new Object[]{name}
        )) {
            if (!cursor.moveToFirst()) return null;

            return new AccountRow(
                    cursor.getInt(cursor.getColumnIndex("id")),
                    clean(cursor.getString(cursor.getColumnIndex("name"))),
                    clean(cursor.getString(cursor.getColumnIndex("type"))),
                    cursor.getDouble(cursor.getColumnIndex("openingBalance"))
            );
        }
    }

    @NonNull
    private String clean(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    @Nullable
    @Override
    public Cursor query(
            @NonNull Uri uri,
            @Nullable String[] projection,
            @Nullable String selection,
            @Nullable String[] selectionArgs,
            @Nullable String sortOrder
    ) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(
            @NonNull Uri uri,
            @Nullable String selection,
            @Nullable String[] selectionArgs
    ) {
        return 0;
    }

    @Override
    public int update(
            @NonNull Uri uri,
            @Nullable ContentValues values,
            @Nullable String selection,
            @Nullable String[] selectionArgs
    ) {
        return 0;
    }

    private static final class CardAccount {
        final String cardName;
        final String canonicalAccount;

        CardAccount(String cardName, String canonicalAccount) {
            this.cardName = cardName;
            this.canonicalAccount = canonicalAccount;
        }
    }

    private static final class AccountRow {
        final int id;
        final String name;
        final String type;
        final double openingBalance;

        AccountRow(int id, String name, String type, double openingBalance) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.openingBalance = openingBalance;
        }
    }
}
