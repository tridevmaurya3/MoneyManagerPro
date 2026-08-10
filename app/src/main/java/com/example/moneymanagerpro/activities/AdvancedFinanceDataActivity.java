package com.example.moneymanagerpro.activities;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
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
import com.example.moneymanagerpro.credit.CreditCardDataIntegrityAuditor;
import com.example.moneymanagerpro.credit.CreditCardStatementImporter;
import com.example.moneymanagerpro.database.AppDatabase;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Account;
import com.example.moneymanagerpro.model.CreditCard;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
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

    private static final String COLOR_TEXT_PRIMARY = "#17351F";
    private static final String COLOR_TEXT_SECONDARY = "#667085";
    private static final String COLOR_BLUE = "#0F6CBD";
    private static final String COLOR_GREEN = "#107C41";
    private static final String COLOR_PURPLE = "#7355A6";
    private static final String COLOR_RED = "#C42B1C";
    private static final String COLOR_AMBER = "#B26A00";

    private MaterialAutoCompleteTextView sourceAccountDropdown;
    private MaterialAutoCompleteTextView targetAccountDropdown;
    private MaterialAutoCompleteTextView cardDropdown;
    private TextView status;
    private TextView accountCountChip;
    private TextView cardCountChip;

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
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setBackgroundColor(getColor(R.color.app_background));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(30));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        root.addView(buildHeader());
        root.addView(buildSummaryCard());

        sourceAccountDropdown = dropdown("Source / old account", "Select the account you want to review");
        targetAccountDropdown = dropdown("Target account", "Select where linked records should move");
        cardDropdown = dropdown("Credit card", "Select the credit card to manage");

        root.addView(accountManagementCard());
        root.addView(creditCardSyncCard());
        root.addView(buildInfoCard());

        return scroll;
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, dp(2), 0, dp(4));

        MaterialButton back = new MaterialButton(this);
        back.setText("←");
        back.setTextSize(18);
        back.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        back.setTextColor(Color.parseColor(COLOR_BLUE));
        back.setAllCaps(false);
        back.setMinWidth(0);
        back.setMinHeight(0);
        back.setInsetTop(0);
        back.setInsetBottom(0);
        back.setPadding(0, 0, 0, 0);
        back.setCornerRadius(dp(13));
        back.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#EEF5FF")));
        back.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#BDD5EE")));
        back.setStrokeWidth(dp(1));
        back.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(42)));
        back.setOnClickListener(view -> finish());
        BubbleTouchAnimator.apply(back);
        header.addView(back);

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleBoxParams = new LinearLayout.LayoutParams(0, -2, 1f);
        titleBoxParams.setMargins(dp(11), 0, 0, 0);
        titleBox.setLayoutParams(titleBoxParams);

        TextView title = text("Account & Card Data Center", 20, true, COLOR_TEXT_PRIMARY);
        TextView subtitle = text(
                "Clean, merge and reconcile finance data safely",
                10,
                false,
                COLOR_TEXT_SECONDARY
        );
        titleBox.addView(title);
        titleBox.addView(subtitle);
        header.addView(titleBox);

        return header;
    }

    private View buildSummaryCard() {
        MaterialCardView card = fluentCard("#F7FAF8", "#D8E4DC", 18);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(13), dp(12), dp(13), dp(12));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        labels.addView(text("Finance data overview", 14, true, COLOR_TEXT_PRIMARY));
        labels.addView(text(
                "Live account and credit-card records",
                9,
                false,
                COLOR_TEXT_SECONDARY
        ));

        MaterialButton refresh = smallActionButton(
                "↻ Refresh",
                "#EEF5FF",
                "#BDD5EE",
                COLOR_BLUE
        );
        refresh.setOnClickListener(v -> loadData());
        BubbleTouchAnimator.apply(refresh);

        top.addView(labels);
        top.addView(refresh);
        content.addView(top);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams chipRowParams = new LinearLayout.LayoutParams(-1, -2);
        chipRowParams.setMargins(0, dp(10), 0, 0);
        chips.setLayoutParams(chipRowParams);

        accountCountChip = statusChip("Accounts …", "#EEF5FF", "#BDD5EE", COLOR_BLUE);
        cardCountChip = statusChip("Cards …", "#F4F0FF", "#D8C8F2", COLOR_PURPLE);
        chips.addView(accountCountChip);
        chips.addView(cardCountChip);
        content.addView(chips);

        status = text("Loading finance data…", 10, false, COLOR_TEXT_SECONDARY);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.setMargins(0, dp(8), 0, 0);
        status.setLayoutParams(statusParams);
        content.addView(status);

        card.addView(content);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(8), 0, dp(2));
        card.setLayoutParams(params);
        return card;
    }

    private View accountManagementCard() {
        MaterialCardView card = fluentCard("#F8FBFF", "#C9DDF2", 19);
        LinearLayout content = cardContent();

        content.addView(sectionHeader(
                "A",
                "Account Management 2.0",
                "Merge duplicates, archive old accounts and remove unused records safely",
                "#EAF3FF",
                "#B8D3EF",
                COLOR_BLUE
        ));

        content.addView(sourceAccountDropdown);
        content.addView(targetAccountDropdown);

        content.addView(buttonRow(
                action("Preview Links", this::previewSource, ActionTone.BLUE),
                action("Merge to Target", this::confirmMerge, ActionTone.PURPLE)
        ));
        content.addView(buttonRow(
                action("Archive / Restore", this::toggleArchive, ActionTone.AMBER),
                action("Safe Delete", this::confirmDelete, ActionTone.RED)
        ));

        card.addView(content);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(10), 0, 0);
        card.setLayoutParams(params);
        return card;
    }

    private View creditCardSyncCard() {
        MaterialCardView card = fluentCard("#FAF8FF", "#DDD0EF", 19);
        LinearLayout content = cardContent();

        content.addView(sectionHeader(
                "C",
                "Credit Card Advanced Sync",
                "Map old accounts, import statements and reconcile card activity",
                "#F2EDFF",
                "#D5C6EF",
                COLOR_PURPLE
        ));

        content.addView(cardDropdown);

        content.addView(buttonRow(
                action("Map Old Account", this::confirmMap, ActionTone.BLUE),
                action("Rename Card + Account", this::showRenameCard, ActionTone.PURPLE)
        ));
        content.addView(buttonRow(
                action("Import Statement CSV", () ->
                        statementPicker.launch(new String[]{"text/*", "text/csv"}), ActionTone.GREEN),
                action("Reconciliation", this::showReconciliation, ActionTone.BLUE)
        ));
        content.addView(buttonRow(
                action("Duplicate Audit", this::showDuplicateAudit, ActionTone.AMBER),
                action("Refresh Card Data", this::loadData, ActionTone.NEUTRAL)
        ));

        card.addView(content);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(10), 0, 0);
        card.setLayoutParams(params);
        return card;
    }

    private View buildInfoCard() {
        MaterialCardView card = fluentCard("#FFF9EC", "#E8D7A7", 16);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.TOP);
        content.setPadding(dp(12), dp(11), dp(12), dp(11));

        TextView icon = text("i", 12, true, COLOR_AMBER);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(roundedDrawable("#FFF1CC", "#E4C873", 12));
        content.addView(icon, new LinearLayout.LayoutParams(dp(32), dp(32)));

        TextView help = text(
                "Statement CSV: date, description, amount and optional type. Refunds, reversals and payments are treated as credits; purchases as expenses. Exact duplicate rows are skipped automatically.",
                9,
                false,
                COLOR_TEXT_SECONDARY
        );
        help.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams helpParams = new LinearLayout.LayoutParams(0, -2, 1f);
        helpParams.setMargins(dp(9), 0, 0, 0);
        help.setLayoutParams(helpParams);
        content.addView(help);

        card.addView(content);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(10), 0, 0);
        card.setLayoutParams(params);
        return card;
    }

    private void loadData() {
        if (status != null) {
            status.setText("Refreshing finance data…");
            status.setTextColor(Color.parseColor(COLOR_TEXT_SECONDARY));
        }

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

                if (accountCountChip != null) {
                    accountCountChip.setText(allAccounts.size() + " Accounts");
                }
                if (cardCountChip != null) {
                    cardCountChip.setText(cards.size() + " Active Cards");
                }
                if (status != null) {
                    status.setText("Data center ready • changes are applied only after confirmation");
                    status.setTextColor(Color.parseColor(COLOR_GREEN));
                }
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
        input.setBoxCornerRadii(dp(13), dp(13), dp(13), dp(13));
        input.setBoxStrokeColor(Color.parseColor(COLOR_BLUE));
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

    private void showDuplicateAudit() {
        CreditCard card = selectedCard();
        if (card == null) return;
        new Thread(() -> {
            CreditCardDataIntegrityAuditor.Preview preview =
                    CreditCardDataIntegrityAuditor.preview(database(), card);
            runOnUiThread(() -> {
                AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                        .setTitle("Card duplicate & outstanding audit")
                        .setMessage(preview.describe())
                        .setNegativeButton("Close", null);
                if (preview.extraRows > 0) {
                    dialog.setPositiveButton("Repair Exact Duplicates", (d, which) ->
                            runOperation("Exact duplicate rows repaired", () ->
                                    CreditCardDataIntegrityAuditor.repairExactDuplicates(database(), card)));
                }
                dialog.show();
            });
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
        if (status != null) {
            status.setText("Applying verified finance-data change…");
            status.setTextColor(Color.parseColor(COLOR_BLUE));
        }

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
        runOnUiThread(() -> {
            if (status != null) {
                status.setText("Action not completed • finance data was not changed");
                status.setTextColor(Color.parseColor(COLOR_RED));
            }
            new AlertDialog.Builder(this)
                    .setTitle("Action not completed")
                    .setMessage(
                            exception.getMessage() == null
                                    ? "Finance data was not changed."
                                    : exception.getMessage()
                    )
                    .setPositiveButton("OK", null)
                    .show();
        });
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

    private MaterialAutoCompleteTextView dropdown(String hint, String helper) {
        MaterialAutoCompleteTextView view = new MaterialAutoCompleteTextView(this);
        view.setHint(hint);
        view.setTextSize(13);
        view.setSingleLine(false);
        view.setMaxLines(2);
        view.setTextColor(Color.parseColor(COLOR_TEXT_PRIMARY));
        view.setHintTextColor(Color.parseColor("#7A8792"));
        view.setPadding(dp(13), dp(7), dp(38), dp(7));
        view.setBackground(roundedDrawable("#FFFFFF", "#CBD8D0", 13));
        view.setCompoundDrawablePadding(dp(8));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(54)
        );
        params.setMargins(0, dp(9), 0, 0);
        view.setLayoutParams(params);
        view.setContentDescription(hint + ". " + helper);
        return view;
    }

    private View sectionHeader(
            String badgeText,
            String title,
            String subtitle,
            String badgeSurface,
            String badgeOutline,
            String accent
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(3));

        TextView badge = text(badgeText, 13, true, accent);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(roundedDrawable(badgeSurface, badgeOutline, 13));
        row.addView(badge, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(0, -2, 1f);
        labelsParams.setMargins(dp(10), 0, 0, 0);
        labels.setLayoutParams(labelsParams);
        labels.addView(text(title, 15, true, COLOR_TEXT_PRIMARY));
        TextView sub = text(subtitle, 9, false, COLOR_TEXT_SECONDARY);
        sub.setLineSpacing(dp(1), 1f);
        labels.addView(sub);
        row.addView(labels);
        return row;
    }

    private LinearLayout cardContent() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(13), dp(13), dp(13), dp(13));
        return content;
    }

    private LinearLayout buttonRow(MaterialButton first, MaterialButton second) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setBaselineAligned(false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(7), 0, 0);
        row.setLayoutParams(params);

        LinearLayout.LayoutParams firstParams = new LinearLayout.LayoutParams(0, dp(46), 1f);
        firstParams.setMargins(0, 0, dp(4), 0);
        first.setLayoutParams(firstParams);

        LinearLayout.LayoutParams secondParams = new LinearLayout.LayoutParams(0, dp(46), 1f);
        secondParams.setMargins(dp(4), 0, 0, 0);
        second.setLayoutParams(secondParams);

        row.addView(first);
        row.addView(second);
        return row;
    }

    private MaterialButton action(String label, Runnable runnable, ActionTone tone) {
        String background;
        String outline;
        String textColor;

        switch (tone) {
            case GREEN:
                background = "#EFF9F1";
                outline = "#B9DFC3";
                textColor = COLOR_GREEN;
                break;
            case PURPLE:
                background = "#F4F0FF";
                outline = "#D8C8F2";
                textColor = COLOR_PURPLE;
                break;
            case RED:
                background = "#FFF2F0";
                outline = "#F0C8C0";
                textColor = COLOR_RED;
                break;
            case AMBER:
                background = "#FFF9EC";
                outline = "#E9D7A8";
                textColor = COLOR_AMBER;
                break;
            case NEUTRAL:
                background = "#F7F9FC";
                outline = "#D8E0E8";
                textColor = "#475467";
                break;
            case BLUE:
            default:
                background = "#EEF5FF";
                outline = "#BDD5EE";
                textColor = COLOR_BLUE;
                break;
        }

        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(9.5f);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(Color.parseColor(textColor));
        button.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(background)));
        button.setStrokeColor(ColorStateList.valueOf(Color.parseColor(outline)));
        button.setStrokeWidth(dp(1));
        button.setCornerRadius(dp(13));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setPadding(dp(5), 0, dp(5), 0);
        button.setOnClickListener(view -> runnable.run());
        BubbleTouchAnimator.apply(button);
        return button;
    }

    private MaterialButton smallActionButton(
            String label,
            String background,
            String outline,
            String foreground
    ) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(9);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(Color.parseColor(foreground));
        button.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(background)));
        button.setStrokeColor(ColorStateList.valueOf(Color.parseColor(outline)));
        button.setStrokeWidth(dp(1));
        button.setCornerRadius(dp(12));
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setLayoutParams(new LinearLayout.LayoutParams(-2, dp(36)));
        return button;
    }

    private TextView statusChip(
            String value,
            String background,
            String outline,
            String foreground
    ) {
        TextView chip = text(value, 9, true, foreground);
        chip.setGravity(Gravity.CENTER);
        chip.setBackground(roundedDrawable(background, outline, 12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(31), 1f);
        params.setMargins(dp(2), 0, dp(2), 0);
        chip.setLayoutParams(params);
        return chip;
    }

    private MaterialCardView fluentCard(String background, String outline, int radiusDp) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(Color.parseColor(background));
        card.setStrokeColor(Color.parseColor(outline));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(radiusDp));
        card.setCardElevation(0f);
        return card;
    }

    private TextView text(
            String value,
            float size,
            boolean bold,
            String color
    ) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(Color.parseColor(color));
        text.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        text.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        return text;
    }

    private GradientDrawable roundedDrawable(
            String background,
            String outline,
            int radiusDp
    ) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor(background));
        drawable.setStroke(dp(1), Color.parseColor(outline));
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
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

    private enum ActionTone {
        BLUE,
        GREEN,
        PURPLE,
        AMBER,
        RED,
        NEUTRAL
    }

    private interface Operation {
        void run() throws Exception;
    }
}
