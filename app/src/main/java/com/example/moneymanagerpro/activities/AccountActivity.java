package com.example.moneymanagerpro.activities;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Account;
import com.example.moneymanagerpro.model.AccountBalance;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AccountActivity extends AppCompatActivity {

    private TextInputLayout inputCustomAccountName;
    private TextInputLayout inputOpeningBalance;
    private TextInputEditText etCustomAccountName;
    private TextInputEditText etOpeningBalance;
    private MaterialAutoCompleteTextView dropdownAccountName;
    private MaterialAutoCompleteTextView dropdownColor;
    private TextView txtDetectedType;
    private MaterialButton btnSaveAccount;
    private MaterialButton btnTransferMoney;
    private LinearLayout accountContainer;
    private TextView txtEmptyAccounts;

    private String selectedAccountType = "Bank";

    private final String[] accountNames = {
            "Cash",
            "SBI",
            "HDFC",
            "ICICI",
            "Axis Bank",
            "PNB",
            "Bank of Baroda",
            "Canara Bank",
            "Union Bank",
            "Kotak Mahindra",
            "IDFC First Bank",
            "IndusInd Bank",
            "Yes Bank",
            "Federal Bank",
            "Paytm Wallet",
            "PhonePe Wallet",
            "Google Pay",
            "Amazon Pay",
            "Other / Custom Name"
    };

    private final String[] colorNames = {
            "Blue", "Green", "Purple", "Orange", "Red", "Teal"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);

        inputCustomAccountName = findViewById(R.id.inputCustomAccountName);
        inputOpeningBalance = findViewById(R.id.inputOpeningBalance);
        etCustomAccountName = findViewById(R.id.etCustomAccountName);
        etOpeningBalance = findViewById(R.id.etOpeningBalance);
        dropdownAccountName = findViewById(R.id.dropdownAccountName);
        dropdownColor = findViewById(R.id.dropdownColor);
        txtDetectedType = findViewById(R.id.txtDetectedType);
        btnSaveAccount = findViewById(R.id.btnSaveAccount);
        btnTransferMoney = findViewById(R.id.btnTransferMoney);
        accountContainer = findViewById(R.id.accountContainer);
        txtEmptyAccounts = findViewById(R.id.txtEmptyAccounts);

        TextView btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());

        setupDropdowns();

        BubbleTouchAnimator.apply(btnSaveAccount);
        BubbleTouchAnimator.apply(btnTransferMoney);

        btnSaveAccount.setOnClickListener(v -> saveAccount());

        btnTransferMoney.setOnClickListener(v ->
                startActivity(new Intent(AccountActivity.this, TransferActivity.class))
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAccounts();
    }

    private void setupDropdowns() {
        ArrayAdapter<String> accountAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                accountNames
        );

        ArrayAdapter<String> colorAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                colorNames
        );

        dropdownAccountName.setAdapter(accountAdapter);
        dropdownColor.setAdapter(colorAdapter);

        dropdownAccountName.setText("Cash", false);
        dropdownColor.setText("Blue", false);

        updateAccountType("Cash");

        dropdownAccountName.setOnItemClickListener((parent, view, position, id) -> {
            String accountName = dropdownAccountName.getText().toString().trim();
            updateAccountType(accountName);
        });
    }

    private void updateAccountType(String accountName) {
        boolean isCustomAccount = accountName.equalsIgnoreCase("Other / Custom Name");

        inputCustomAccountName.setVisibility(
                isCustomAccount ? View.VISIBLE : View.GONE
        );

        if (isCustomAccount) {
            selectedAccountType = "Other";
            txtDetectedType.setText("Account Type: Custom Account");
            return;
        }

        if (accountName.equalsIgnoreCase("Cash")) {
            selectedAccountType = "Cash";
            txtDetectedType.setText("Account Type: Cash");
            return;
        }

        String lowerName = accountName.toLowerCase();

        if (lowerName.contains("wallet")) {
            selectedAccountType = "Wallet";
            txtDetectedType.setText("Account Type: Digital Wallet");
            return;
        }

        if (lowerName.contains("google pay")
                || lowerName.contains("amazon pay")
                || lowerName.contains("phonepe")) {
            selectedAccountType = "UPI";
            txtDetectedType.setText("Account Type: UPI / Digital Payment");
            return;
        }

        selectedAccountType = "Bank";
        txtDetectedType.setText("Account Type: Bank Account");
    }

    private void saveAccount() {
        String selectedName = dropdownAccountName.getText().toString().trim();

        String accountName;

        if (selectedName.equalsIgnoreCase("Other / Custom Name")) {
            accountName = etCustomAccountName.getText() == null
                    ? ""
                    : etCustomAccountName.getText().toString().trim();

            if (accountName.isEmpty()) {
                inputCustomAccountName.setError("Please enter account name");
                return;
            }
        } else {
            accountName = selectedName;
        }

        String openingBalanceText = etOpeningBalance.getText() == null
                ? ""
                : etOpeningBalance.getText().toString().trim();

        double openingBalance = 0;

        if (!openingBalanceText.isEmpty()) {
            try {
                openingBalance = Double.parseDouble(openingBalanceText);
            } catch (Exception exception) {
                inputOpeningBalance.setError("Enter a valid amount");
                return;
            }
        }

        inputCustomAccountName.setError(null);
        inputOpeningBalance.setError(null);

        String finalAccountName = accountName;
        double finalOpeningBalance = openingBalance;
        String selectedColor = getColorCode(
                dropdownColor.getText().toString().trim()
        );

        btnSaveAccount.setEnabled(false);
        btnSaveAccount.setText("Saving Account...");

        new Thread(() -> {
            List<Account> accounts = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .accountDao()
                    .getAllAccounts();

            for (Account account : accounts) {
                if (account.getName().equalsIgnoreCase(finalAccountName)) {
                    runOnUiThread(() -> {
                        btnSaveAccount.setEnabled(true);
                        btnSaveAccount.setText("Save Account");

                        Toast.makeText(
                                AccountActivity.this,
                                "An account with this name already exists",
                                Toast.LENGTH_SHORT
                        ).show();
                    });

                    return;
                }
            }

            Account account = new Account();
            account.setName(finalAccountName);
            account.setType(selectedAccountType);
            account.setOpeningBalance(finalOpeningBalance);
            account.setColor(selectedColor);

            DatabaseClient.getInstance(getApplicationContext())
                    .getAppDatabase()
                    .accountDao()
                    .insert(account);

            runOnUiThread(() -> {
                etCustomAccountName.setText("");
                etOpeningBalance.setText("");
                dropdownAccountName.setText("Cash", false);
                dropdownColor.setText("Blue", false);
                updateAccountType("Cash");

                btnSaveAccount.setEnabled(true);
                btnSaveAccount.setText("Save Account");

                Toast.makeText(
                        AccountActivity.this,
                        "Account saved successfully",
                        Toast.LENGTH_SHORT
                ).show();

                loadAccounts();
            });
        }).start();
    }

    private void loadAccounts() {
        new Thread(() -> {
            List<Account> accounts = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .accountDao()
                    .getAllAccounts();

            List<AccountBalance> balances = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .accountDao()
                    .getAccountBalances();

            Map<Integer, Double> balanceMap = new HashMap<>();

            for (AccountBalance balance : balances) {
                balanceMap.put(balance.id, balance.currentBalance);
            }

            runOnUiThread(() -> showAccounts(accounts, balanceMap));
        }).start();
    }

    private void showAccounts(
            List<Account> accounts,
            Map<Integer, Double> balanceMap
    ) {
        accountContainer.removeAllViews();

        txtEmptyAccounts.setVisibility(
                accounts.isEmpty() ? View.VISIBLE : View.GONE
        );

        for (Account account : accounts) {
            double currentBalance = 0;

            if (balanceMap.containsKey(account.getId())) {
                currentBalance = balanceMap.get(account.getId());
            }

            addAccountCard(account, currentBalance);
        }
    }

    private void addAccountCard(Account account, double currentBalance) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(Color.WHITE);
        card.setRadius(dpToPx(20));
        card.setCardElevation(dpToPx(5));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dpToPx(12));
        card.setLayoutParams(cardParams);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dpToPx(16), dpToPx(15), dpToPx(16), dpToPx(15));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);

        View colorDot = new View(this);

        GradientDrawable dotBackground = new GradientDrawable();
        dotBackground.setShape(GradientDrawable.OVAL);
        dotBackground.setColor(Color.parseColor(safeColor(account.getColor())));
        colorDot.setBackground(dotBackground);

        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(
                dpToPx(44),
                dpToPx(44)
        );
        colorDot.setLayoutParams(dotParams);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        );
        detailsParams.setMargins(dpToPx(12), 0, 0, 0);
        details.setLayoutParams(detailsParams);

        TextView txtName = new TextView(this);
        txtName.setText(account.getName());
        txtName.setTextColor(Color.parseColor("#172033"));
        txtName.setTextSize(18);
        txtName.setTypeface(Typeface.DEFAULT_BOLD);

        TextView txtType = new TextView(this);
        txtType.setText(account.getType() + " Account");
        txtType.setTextColor(Color.parseColor("#64748B"));
        txtType.setTextSize(13);

        details.addView(txtName);
        details.addView(txtType);

        TextView txtBalance = new TextView(this);
        txtBalance.setText(formatAmount(currentBalance));
        txtBalance.setTextColor(Color.parseColor(safeColor(account.getColor())));
        txtBalance.setTextSize(19);
        txtBalance.setTypeface(Typeface.DEFAULT_BOLD);
        txtBalance.setGravity(Gravity.END);

        header.addView(colorDot);
        header.addView(details);
        header.addView(txtBalance);

        TextView txtOpeningBalance = new TextView(this);
        txtOpeningBalance.setText(
                "Opening Balance: " + formatAmount(account.getOpeningBalance())
        );
        txtOpeningBalance.setTextColor(Color.parseColor("#64748B"));
        txtOpeningBalance.setTextSize(13);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setGravity(Gravity.END);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);

        MaterialButton btnEdit = new MaterialButton(this);
        btnEdit.setText("Edit");
        btnEdit.setTextSize(12);
        btnEdit.setTextColor(Color.WHITE);
        btnEdit.setAllCaps(false);
        btnEdit.setCornerRadius(dpToPx(16));
        btnEdit.setBackgroundTintList(
                ColorStateList.valueOf(Color.parseColor("#3949AB"))
        );

        MaterialButton btnDelete = new MaterialButton(this);
        btnDelete.setText("Delete");
        btnDelete.setTextSize(12);
        btnDelete.setTextColor(Color.WHITE);
        btnDelete.setAllCaps(false);
        btnDelete.setCornerRadius(dpToPx(16));
        btnDelete.setBackgroundTintList(
                ColorStateList.valueOf(Color.parseColor("#D32F2F"))
        );

        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(
                dpToPx(78),
                dpToPx(40)
        );

        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                dpToPx(78),
                dpToPx(40)
        );
        deleteParams.setMargins(dpToPx(8), 0, 0, 0);

        btnEdit.setLayoutParams(editParams);
        btnDelete.setLayoutParams(deleteParams);

        BubbleTouchAnimator.apply(card);
        BubbleTouchAnimator.apply(btnEdit);
        BubbleTouchAnimator.apply(btnDelete);

        btnEdit.setOnClickListener(v -> showEditDialog(account));
        btnDelete.setOnClickListener(v -> confirmDelete(account));

        actionRow.addView(btnEdit);
        actionRow.addView(btnDelete);

        content.addView(header);
        content.addView(txtOpeningBalance);
        content.addView(actionRow);

        card.addView(content);
        accountContainer.addView(card);
    }

    private void showEditDialog(Account account) {
        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(dpToPx(22), dpToPx(10), dpToPx(22), dpToPx(8));

        TextView txtAccountName = new TextView(this);
        txtAccountName.setText(account.getName());
        txtAccountName.setTextColor(Color.parseColor("#172033"));
        txtAccountName.setTextSize(20);
        txtAccountName.setTypeface(Typeface.DEFAULT_BOLD);
        txtAccountName.setGravity(Gravity.CENTER);

        TextView txtInfo = new TextView(this);
        txtInfo.setText("Account name cannot be changed to protect transaction history.");
        txtInfo.setTextColor(Color.parseColor("#64748B"));
        txtInfo.setTextSize(13);
        txtInfo.setGravity(Gravity.CENTER);

        dialogLayout.addView(txtAccountName);
        dialogLayout.addView(txtInfo);

        TextView txtBalanceLabel = createLabel("Opening Balance");
        dialogLayout.addView(txtBalanceLabel);

        TextInputLayout inputBalance = new TextInputLayout(this);
        inputBalance.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);

        TextInputEditText editBalance = new TextInputEditText(this);
        editBalance.setText(String.valueOf(account.getOpeningBalance()));
        editBalance.setGravity(Gravity.CENTER);
        editBalance.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        editBalance.setInputType(2);

        inputBalance.addView(editBalance);
        dialogLayout.addView(inputBalance);

        TextView txtColorLabel = createLabel("Account Color");
        dialogLayout.addView(txtColorLabel);

        TextInputLayout inputColor = new TextInputLayout(this);
        inputColor.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        inputColor.setEndIconMode(TextInputLayout.END_ICON_DROPDOWN_MENU);

        MaterialAutoCompleteTextView editColor = new MaterialAutoCompleteTextView(this);
        editColor.setGravity(Gravity.CENTER);
        editColor.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        editColor.setFocusable(false);
        editColor.setInputType(0);

        ArrayAdapter<String> colorAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                colorNames
        );

        editColor.setAdapter(colorAdapter);
        editColor.setText(getColorName(account.getColor()), false);

        inputColor.addView(editColor);
        dialogLayout.addView(inputColor);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Edit Account")
                .setView(dialogLayout)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();

        dialog.setOnShowListener(listener -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String openingText = editBalance.getText() == null
                        ? ""
                        : editBalance.getText().toString().trim();

                double openingBalance;

                try {
                    openingBalance = openingText.isEmpty()
                            ? 0
                            : Double.parseDouble(openingText);
                } catch (Exception exception) {
                    inputBalance.setError("Enter a valid amount");
                    return;
                }

                account.setOpeningBalance(openingBalance);
                account.setColor(
                        getColorCode(editColor.getText().toString().trim())
                );

                new Thread(() -> {
                    DatabaseClient.getInstance(getApplicationContext())
                            .getAppDatabase()
                            .accountDao()
                            .update(account);

                    runOnUiThread(() -> {
                        dialog.dismiss();

                        Toast.makeText(
                                AccountActivity.this,
                                "Account updated",
                                Toast.LENGTH_SHORT
                        ).show();

                        loadAccounts();
                    });
                }).start();
            });
        });

        dialog.show();
    }

    private void confirmDelete(Account account) {
        if (account.getName().equalsIgnoreCase("Cash")) {
            Toast.makeText(
                    this,
                    "Cash account cannot be deleted",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Delete Account")
                .setMessage(
                        "Delete \"" + account.getName()
                                + "\"? Existing transaction history will remain safe."
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    new Thread(() -> {
                        DatabaseClient.getInstance(getApplicationContext())
                                .getAppDatabase()
                                .accountDao()
                                .delete(account);

                        runOnUiThread(() -> {
                            Toast.makeText(
                                    AccountActivity.this,
                                    "Account deleted",
                                    Toast.LENGTH_SHORT
                            ).show();

                            loadAccounts();
                        });
                    }).start();
                })
                .show();
    }

    private TextView createLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(Color.parseColor("#1565C0"));
        label.setTextSize(14);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dpToPx(16), 0, dpToPx(5));

        label.setLayoutParams(params);

        return label;
    }

    private String getColorCode(String colorName) {
        switch (colorName) {
            case "Green":
                return "#2E7D32";

            case "Purple":
                return "#6A1B9A";

            case "Orange":
                return "#EF6C00";

            case "Red":
                return "#D32F2F";

            case "Teal":
                return "#00838F";

            case "Blue":
            default:
                return "#1565C0";
        }
    }

    private String getColorName(String colorCode) {
        if (colorCode == null) {
            return "Blue";
        }

        if (colorCode.equalsIgnoreCase("#2E7D32")) {
            return "Green";
        }

        if (colorCode.equalsIgnoreCase("#6A1B9A")) {
            return "Purple";
        }

        if (colorCode.equalsIgnoreCase("#EF6C00")) {
            return "Orange";
        }

        if (colorCode.equalsIgnoreCase("#D32F2F")) {
            return "Red";
        }

        if (colorCode.equalsIgnoreCase("#00838F")) {
            return "Teal";
        }

        return "Blue";
    }

    private String safeColor(String colorCode) {
        if (colorCode == null || colorCode.trim().isEmpty()) {
            return "#1565C0";
        }

        return colorCode;
    }

    private String formatAmount(double amount) {
        NumberFormat numberFormat = NumberFormat.getCurrencyInstance(
                new Locale("en", "IN")
        );

        return numberFormat.format(amount);
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density);
    }
}