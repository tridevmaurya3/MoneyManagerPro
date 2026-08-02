package com.example.moneymanagerpro.activities;

import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.credit.CreditCardStatementImporter;
import com.example.moneymanagerpro.database.AppDatabase;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Account;
import com.example.moneymanagerpro.model.CreditCard;
import com.example.moneymanagerpro.utils.FinancialDataMergeManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AdvancedFinanceDataActivity extends AppCompatActivity {

    private MaterialAutoCompleteTextView sourceAccountDropdown;
    private MaterialAutoCompleteTextView targetAccountDropdown;
    private MaterialAutoCompleteTextView cardDropdown;
    private TextView status;

    private List<Account> allAccounts = new ArrayList<>();
    private List<CreditCard> cards = new ArrayList<>();
    private Uri pendingStatementUri;

    private final ActivityResultLauncher<String[]> statementPicker =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(),
                    uri -> {
                        if (uri == null) return;
                        pendingStatementUri = uri;
                        confirmStatementImport();
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildPage());
        loadData();
    }

    private ScrollView buildPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(getColor(R.color.app_background));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(30));
        scroll.addView(root);

        MaterialButton back = button("← Back", false);
        back.setOnClickListener(view -> finish());
        root.addView(back);

        root.addView(text("Account & Credit Card Data Center", 23, true));
        root.addView(text(
                "Rename, map, merge, archive, safely delete and reconcile statement data.",
                12,
                false
        ));

        status = text("Loading finance data…", 12, false);
        LinearLayout.LayoutParams statusParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        statusParams.setMargins(0, dp(12), 0, dp(12));
        status.setLayoutParams(statusParams);
        root.addView(status);

        sourceAccountDropdown = dropdown("Source / old account");
        targetAccountDropdown = dropdown("Target account");
        cardDropdown = dropdown("Credit card");

        root.addView(section(
                "Account Management 2.0",
                sourceAccountDropdown,
                targetAccountDropdown,
                buttonRow(
                        action("Preview Duplicate / Links", this::previewSource),
                        action("Merge into Target", this::confirmMerge)
                ),
                buttonRow(
                        action("Archive / Restore", this::toggleArchive),
                        action("Safe Delete", this::confirmDelete)
                )
        ));

        root.addView(section(
                "Credit Card Advanced Sync",
                cardDropdown,
                buttonRow(
                        action("Map Old Account to Card", this::confirmMap),
                        action("Rename Card + Account", this::showRenameCard)
                ),
                buttonRow(
                        action("Import Statement CSV", () ->
                                statementPicker.launch(new String[]{"text/*", "text/csv"})),
                        action("Reconciliation Preview", this::showReconciliation)
                )
        ));

        TextView help = text(
                "Statement CSV columns: date, description, amount and optional type. "
                        + "Refunds, reversals and payments are imported as credits; purchases as expenses. "
                        + "Duplicate rows are skipped automatically.",
                11,
                false
        );
        LinearLayout.LayoutParams helpParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        helpParams.setMargins(0, dp(12), 0, 0);
        help.setLayoutParams(helpParams);
        root.addView(help);

        return scroll;
    }

    private void loadData() {
        new Thread(() -> {
            AppDatabase database = database();
            List<Account> loadedAccounts =
                    database.accountDao().getAllAccountsIncludingArchived();
            List<CreditCard> loadedCards =
                    database.creditCardDao().getActiveCreditCards();

            runOnUiThread(() -> {
                allAccounts = loadedAccounts == null
                        ? new ArrayList<>() : loadedAccounts;
                cards = loadedCards == null
                        ? new ArrayList<>() : loadedCards;
                bindDropdowns();
                status.setText(
                        allAccounts.size() + " accounts • "
                                + cards.size() + " active credit cards"
                );
            });
        }).start();
    }

    private void bindDropdowns() {
        List<String> labels = new ArrayList<>();
        for (Account account : allAccounts) {
            labels.add(accountLabel(account));
        }
        sourceAccountDropdown.setAdapter(adapter(labels));
        targetAccountDropdown.setAdapter(adapter(labels));
        if (!labels.isEmpty()) {
            sourceAccountDropdown.setText(labels.get(0), false);
            targetAccountDropdown.setText(
                    labels.get(Math.min(1, labels.size() - 1)),
                    false
            );
        }

        List<String> cardLabels = new ArrayList<>();
        for (CreditCard card : cards) {
            cardLabels.add(card.getName() + " •••• " + card.getLastFour());
        }
        cardDropdown.setAdapter(adapter(cardLabels));
        if (!cardLabels.isEmpty()) cardDropdown.setText(cardLabels.get(0), false);
    }

    private void previewSource() {
        Account source = selectedAccount(sourceAccountDropdown);
        if (source == null) return;
        new Thread(() -> {
            FinancialDataMergeManager.Preview preview =
                    FinancialDataMergeManager.preview(database(), source.getName());
            runOnUiThread(() -> new AlertDialog.Builder(this)
                    .setTitle("Linked-record preview")
                    .setMessage(
                            source.getName()
                                    + (source.isArchived() ? " (Archived)" : "")
                                    + "\n\n" + preview.describe()
                                    + "\n\nTotal linked records: "
                                    + preview.totalReferences()
                    )
                    .setPositiveButton("OK", null)
                    .show());
        }).start();
    }

    private void confirmMerge() {
        Account source = selectedAccount(sourceAccountDropdown);
        Account target = selectedAccount(targetAccountDropdown);
        if (source == null || target == null) return;

        new Thread(() -> {
            FinancialDataMergeManager.Preview preview =
                    FinancialDataMergeManager.preview(database(), source.getName());
            runOnUiThread(() -> new AlertDialog.Builder(this)
                    .setTitle("Merge accounts?")
                    .setMessage(
                            "Move " + preview.totalReferences() + " linked records and opening balance from\n\n"
                                    + source.getName() + "\n\nto\n\n" + target.getName()
                                    + "\n\nThe source account will then be removed."
                    )
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Merge Safely", (dialog, which) ->
                            runOperation(
                                    "Accounts merged",
                                    () -> FinancialDataMergeManager.mergeAccounts(
                                            database(), source, target
                                    )
                            ))
                    .show());
        }).start();
    }

    private void toggleArchive() {
        Account account = selectedAccount(sourceAccountDropdown);
        if (account == null) return;
        boolean archive = !account.isArchived();
        new AlertDialog.Builder(this)
                .setTitle(archive ? "Archive account?" : "Restore account?")
                .setMessage(
                        archive
                                ? "Archived accounts disappear from payment dropdowns. Transactions remain safe."
                                : "The account will become available in payment dropdowns again."
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                        archive ? "Archive" : "Restore",
                        (dialog, which) -> runOperation(
                                archive ? "Account archived" : "Account restored",
                                () -> FinancialDataMergeManager.setArchived(
                                        database(), account, archive
                                )
                        )
                )
                .show();
    }

    private void confirmDelete() {
        Account account = selectedAccount(sourceAccountDropdown);
        if (account == null) return;
        new Thread(() -> {
            FinancialDataMergeManager.Preview preview =
                    FinancialDataMergeManager.preview(database(), account.getName());
            runOnUiThread(() -> new AlertDialog.Builder(this)
                    .setTitle("Permanent delete check")
                    .setMessage(
                            preview.describe()
                                    + "\n\nDeletion is allowed only when there are no linked records "
                                    + "and opening balance is zero. Otherwise use Merge or Archive."
                    )
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete if Safe", (dialog, which) ->
                            runOperation(
                                    "Unused account deleted",
                                    () -> FinancialDataMergeManager.deleteIfUnused(
                                            database(), account
                                    )
                            ))
                    .show());
        }).start();
    }

    private void confirmMap() {
        Account oldAccount = selectedAccount(sourceAccountDropdown);
        CreditCard card = selectedCard();
        if (oldAccount == null || card == null) return;
        new Thread(() -> {
            FinancialDataMergeManager.Preview preview =
                    FinancialDataMergeManager.preview(
                            database(), oldAccount.getName()
                    );
            runOnUiThread(() -> new AlertDialog.Builder(this)
                    .setTitle("Map old account to card?")
                    .setMessage(
                            preview.totalReferences()
                                    + " linked records will move from\n\n"
                                    + oldAccount.getName() + "\n\nto\n\n"
                                    + card.getAccountName()
                                    + "\n\nThe old account will be merged and removed."
                    )
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Map & Merge", (dialog, which) ->
                            runOperation(
                                    "Old account mapped to credit card",
                                    () -> FinancialDataMergeManager.mapOldAccountToCard(
                                            database(), oldAccount, card
                                    )
                            ))
                    .show());
        }).start();
    }

    private void showRenameCard() {
        CreditCard card = selectedCard();
        if (card == null) return;

        TextInputLayout input = new TextInputLayout(this);
        input.setHint("New card name");
        input.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        TextInputEditText edit = new TextInputEditText(this);
        edit.setText(card.getName());
        input.addView(edit);

        new AlertDialog.Builder(this)
                .setTitle("Rename card and linked account")
                .setMessage(
                        "The card, masked account and all linked transaction references will update together."
                )
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Rename", (dialog, which) -> {
                    String name = edit.getText() == null
                            ? "" : edit.getText().toString().trim();
                    runOperation(
                            "Card and linked account renamed",
                            () -> FinancialDataMergeManager.renameCardAndLinkedAccount(
                                    database(), card, name
                            )
                    );
                })
                .show();
    }

    private void confirmStatementImport() {
        CreditCard card = selectedCard();
        if (card == null || pendingStatementUri == null) return;
        Uri uri = pendingStatementUri;
        new AlertDialog.Builder(this)
                .setTitle("Import statement?")
                .setMessage(
                        "Rows will be linked to " + card.getAccountName()
                                + ". Duplicate rows will be skipped. "
                                + "Refunds and payments reduce card spending."
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Import", (dialog, which) ->
                        new Thread(() -> {
                            try {
                                CreditCardStatementImporter.Result result =
                                        CreditCardStatementImporter.importCsv(
                                                this, database(), card, uri
                                        );
                                runOnUiThread(() -> new AlertDialog.Builder(this)
                                        .setTitle("Statement imported")
                                        .setMessage(
                                                "Imported: " + result.imported
                                                        + "\nRefunds/reversals: " + result.refunds
                                                        + "\nPayments: " + result.payments
                                                        + "\nDuplicates skipped: "
                                                        + result.duplicatesSkipped
                                        )
                                        .setPositiveButton("OK", null)
                                        .show());
                            } catch (Exception exception) {
                                showError(exception);
                            }
                        }).start())
                .show();
    }

    private void showReconciliation() {
        CreditCard card = selectedCard();
        if (card == null) return;
        new Thread(() -> {
            SupportSQLiteDatabase sql =
                    database().getOpenHelper().getReadableDatabase();
            double purchases = sum(
                    sql,
                    "SELECT COALESCE(SUM(amount),0) FROM transactions "
                            + "WHERE account = ? AND type = 'EXPENSE'",
                    card.getAccountName()
            );
            double credits = sum(
                    sql,
                    "SELECT COALESCE(SUM(amount),0) FROM transactions "
                            + "WHERE account = ? AND type = 'INCOME'",
                    card.getAccountName()
            );
            double recordedPayments = sum(
                    sql,
                    "SELECT COALESCE(SUM(amount),0) FROM credit_card_payments "
                            + "WHERE creditCardId = ?",
                    card.getId()
            );
            double reconciled = Math.max(0, purchases - credits - recordedPayments);
            runOnUiThread(() -> new AlertDialog.Builder(this)
                    .setTitle("Refund & payment reconciliation")
                    .setMessage(
                            "Purchases: " + money(purchases)
                                    + "\nRefunds / statement credits: " + money(credits)
                                    + "\nRecorded card payments: " + money(recordedPayments)
                                    + "\n\nReconciled outstanding: " + money(reconciled)
                                    + "\n\nUse the bank statement as the final authority."
                    )
                    .setPositiveButton("OK", null)
                    .show());
        }).start();
    }

    private double sum(
            SupportSQLiteDatabase sql,
            String query,
            Object argument
    ) {
        try (android.database.Cursor cursor =
                     sql.query(query, new Object[]{argument})) {
            return cursor.moveToFirst() ? cursor.getDouble(0) : 0;
        }
    }

    private void runOperation(String successMessage, Operation operation) {
        new Thread(() -> {
            try {
                operation.run();
                runOnUiThread(() -> {
                    Toast.makeText(
                            this, successMessage, Toast.LENGTH_LONG
                    ).show();
                    loadData();
                });
            } catch (Exception exception) {
                showError(exception);
            }
        }).start();
    }

    private void showError(Exception exception) {
        runOnUiThread(() -> new AlertDialog.Builder(this)
                .setTitle("Action not completed")
                .setMessage(
                        exception.getMessage() == null
                                ? "Finance data was not changed."
                                : exception.getMessage()
                )
                .setPositiveButton("OK", null)
                .show());
    }

    private Account selectedAccount(
            MaterialAutoCompleteTextView dropdown
    ) {
        String selected = dropdown.getText().toString();
        for (Account account : allAccounts) {
            if (accountLabel(account).equals(selected)) return account;
        }
        Toast.makeText(this, "Select an account", Toast.LENGTH_SHORT).show();
        return null;
    }

    private CreditCard selectedCard() {
        String selected = cardDropdown.getText().toString();
        for (CreditCard card : cards) {
            if ((card.getName() + " •••• " + card.getLastFour())
                    .equals(selected)) return card;
        }
        Toast.makeText(this, "Select a credit card", Toast.LENGTH_SHORT).show();
        return null;
    }

    private String accountLabel(Account account) {
        return account.getName()
                + (account.isArchived() ? "  [Archived]" : "");
    }

    private ArrayAdapter<String> adapter(List<String> values) {
        return new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, values
        );
    }

    private MaterialAutoCompleteTextView dropdown(String hint) {
        MaterialAutoCompleteTextView view =
                new MaterialAutoCompleteTextView(this);
        view.setHint(hint);
        view.setPadding(dp(14), dp(7), dp(14), dp(7));
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(58)
                );
        params.setMargins(0, dp(8), 0, 0);
        view.setLayoutParams(params);
        return view;
    }

    private MaterialCardView section(String title, android.view.View... views) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(18));
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(getColor(R.color.app_outline));
        card.setCardElevation(0);
        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        cardParams.setMargins(0, dp(10), 0, 0);
        card.setLayoutParams(cardParams);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(14), dp(14), dp(14));
        content.addView(text(title, 17, true));
        for (android.view.View view : views) content.addView(view);
        card.addView(content);
        return card;
    }

    private LinearLayout buttonRow(MaterialButton first, MaterialButton second) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        params.setMargins(0, dp(8), 0, 0);
        row.setLayoutParams(params);
        first.setLayoutParams(new LinearLayout.LayoutParams(0, dp(52), 1));
        second.setLayoutParams(new LinearLayout.LayoutParams(0, dp(52), 1));
        row.addView(first);
        row.addView(second);
        return row;
    }

    private MaterialButton action(String label, Runnable runnable) {
        MaterialButton button = button(label, true);
        button.setTextSize(11);
        button.setOnClickListener(view -> runnable.run());
        return button;
    }

    private MaterialButton button(String label, boolean outlined) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setCornerRadius(dp(14));
        if (outlined) button.setStrokeWidth(dp(1));
        return button;
    }

    private TextView text(String value, int size, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(getColor(R.color.app_text_primary));
        if (bold) text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return text;
    }

    private String money(double amount) {
        return NumberFormat.getCurrencyInstance(
                new Locale("en", "IN")
        ).format(amount);
    }

    private AppDatabase database() {
        return DatabaseClient.getInstance(
                getApplicationContext()
        ).getAppDatabase();
    }

    private int dp(int value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
    }

    private interface Operation {
        void run() throws Exception;
    }
}
