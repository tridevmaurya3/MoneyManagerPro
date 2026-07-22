package com.example.moneymanagerpro.activities;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

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
    private TextView txtEmptyAccounts;

    private MaterialButton btnSaveAccount;
    private MaterialButton btnTransferMoney;

    private LinearLayout accountContainer;

    private View emptyAccountsCard;

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
            "Blue",
            "Green",
            "Purple",
            "Orange",
            "Red",
            "Teal"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);

        initializeViews();
        setupDropdowns();
        setupClickListeners();
        applyTouchAnimations();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAccounts();
    }

    private void initializeViews() {
        inputCustomAccountName =
                findViewById(R.id.inputCustomAccountName);

        inputOpeningBalance =
                findViewById(R.id.inputOpeningBalance);

        etCustomAccountName =
                findViewById(R.id.etCustomAccountName);

        etOpeningBalance =
                findViewById(R.id.etOpeningBalance);

        dropdownAccountName =
                findViewById(R.id.dropdownAccountName);

        dropdownColor =
                findViewById(R.id.dropdownColor);

        txtDetectedType =
                findViewById(R.id.txtDetectedType);

        btnSaveAccount =
                findViewById(R.id.btnSaveAccount);

        btnTransferMoney =
                findViewById(R.id.btnTransferMoney);

        accountContainer =
                findViewById(R.id.accountContainer);

        txtEmptyAccounts =
                findViewById(R.id.txtEmptyAccounts);

        View parentView =
                (View) txtEmptyAccounts.getParent();

        emptyAccountsCard = parentView;

        TextView btnBack =
                findViewById(R.id.btnBack);

        btnBack.setOnClickListener(
                view -> finish()
        );
    }

    private void setupClickListeners() {
        btnSaveAccount.setOnClickListener(
                view -> saveAccount()
        );

        btnTransferMoney.setOnClickListener(
                view -> startActivity(
                        new Intent(
                                AccountActivity.this,
                                TransferActivity.class
                        )
                )
        );
    }

    private void applyTouchAnimations() {
        BubbleTouchAnimator.apply(btnSaveAccount);
        BubbleTouchAnimator.apply(btnTransferMoney);
    }

    private void setupDropdowns() {
        ArrayAdapter<String> accountAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_list_item_1,
                        accountNames
                );

        ArrayAdapter<String> colorAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_list_item_1,
                        colorNames
                );

        dropdownAccountName.setAdapter(
                accountAdapter
        );

        dropdownColor.setAdapter(
                colorAdapter
        );

        dropdownAccountName.setText(
                "Cash",
                false
        );

        dropdownColor.setText(
                "Blue",
                false
        );

        updateAccountType("Cash");

        dropdownAccountName.setOnItemClickListener(
                (parent, view, position, id) -> {
                    String accountName =
                            dropdownAccountName
                                    .getText()
                                    .toString()
                                    .trim();

                    updateAccountType(accountName);
                }
        );
    }

    private void updateAccountType(
            String accountName
    ) {
        inputCustomAccountName.setError(null);

        boolean isCustomAccount =
                accountName.equalsIgnoreCase(
                        "Other / Custom Name"
                );

        inputCustomAccountName.setVisibility(
                isCustomAccount
                        ? View.VISIBLE
                        : View.GONE
        );

        if (isCustomAccount) {
            selectedAccountType = "Other";

            txtDetectedType.setText(
                    "Account Type: Custom Account"
            );

            return;
        }

        if (accountName.equalsIgnoreCase("Cash")) {
            selectedAccountType = "Cash";

            txtDetectedType.setText(
                    "Account Type: Cash"
            );

            return;
        }

        String lowerName =
                accountName.toLowerCase(
                        Locale.getDefault()
                );

        if (lowerName.contains("wallet")) {
            selectedAccountType = "Wallet";

            txtDetectedType.setText(
                    "Account Type: Digital Wallet"
            );

            return;
        }

        if (lowerName.contains("google pay")
                || lowerName.contains("amazon pay")
                || lowerName.contains("phonepe")) {

            selectedAccountType = "UPI";

            txtDetectedType.setText(
                    "Account Type: UPI / Digital Payment"
            );

            return;
        }

        selectedAccountType = "Bank";

        txtDetectedType.setText(
                "Account Type: Bank Account"
        );
    }

    private void saveAccount() {
        String selectedName =
                dropdownAccountName
                        .getText()
                        .toString()
                        .trim();

        if (selectedName.isEmpty()) {
            Toast.makeText(
                    this,
                    "Please select an account",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        String accountName;

        if (selectedName.equalsIgnoreCase(
                "Other / Custom Name"
        )) {
            accountName =
                    getEditTextValue(
                            etCustomAccountName
                    );

            if (accountName.isEmpty()) {
                inputCustomAccountName.setError(
                        "Please enter account name"
                );

                etCustomAccountName.requestFocus();
                return;
            }
        } else {
            accountName = selectedName;
        }

        String openingBalanceText =
                getEditTextValue(
                        etOpeningBalance
                );

        double openingBalance = 0;

        if (!openingBalanceText.isEmpty()) {
            try {
                openingBalance =
                        Double.parseDouble(
                                openingBalanceText
                        );

            } catch (Exception exception) {
                inputOpeningBalance.setError(
                        "Enter a valid amount"
                );

                etOpeningBalance.requestFocus();
                return;
            }
        }

        inputCustomAccountName.setError(null);
        inputOpeningBalance.setError(null);

        String finalAccountName =
                accountName;

        double finalOpeningBalance =
                openingBalance;

        String finalAccountType =
                selectedAccountType;

        String selectedColor =
                getColorCode(
                        dropdownColor
                                .getText()
                                .toString()
                                .trim()
                );

        setSaveButtonLoading(true);

        new Thread(() -> {
            List<Account> accounts =
                    DatabaseClient
                            .getInstance(
                                    getApplicationContext()
                            )
                            .getAppDatabase()
                            .accountDao()
                            .getAllAccounts();

            for (Account savedAccount : accounts) {
                if (savedAccount
                        .getName()
                        .equalsIgnoreCase(
                                finalAccountName
                        )) {

                    runOnUiThread(() -> {
                        setSaveButtonLoading(false);

                        Toast.makeText(
                                AccountActivity.this,
                                "An account with this name already exists",
                                Toast.LENGTH_SHORT
                        ).show();
                    });

                    return;
                }
            }

            Account newAccount =
                    new Account();

            newAccount.setName(
                    finalAccountName
            );

            newAccount.setType(
                    finalAccountType
            );

            newAccount.setOpeningBalance(
                    finalOpeningBalance
            );

            newAccount.setColor(
                    selectedColor
            );

            DatabaseClient
                    .getInstance(
                            getApplicationContext()
                    )
                    .getAppDatabase()
                    .accountDao()
                    .insert(newAccount);

            runOnUiThread(() -> {
                resetAccountForm();
                setSaveButtonLoading(false);

                Toast.makeText(
                        AccountActivity.this,
                        "Account saved successfully",
                        Toast.LENGTH_SHORT
                ).show();

                loadAccounts();
            });
        }).start();
    }

    private void setSaveButtonLoading(
            boolean loading
    ) {
        btnSaveAccount.setEnabled(!loading);

        btnSaveAccount.setText(
                loading
                        ? "Saving Account..."
                        : "Save Account"
        );
    }

    private void resetAccountForm() {
        etCustomAccountName.setText("");
        etOpeningBalance.setText("");

        dropdownAccountName.setText(
                "Cash",
                false
        );

        dropdownColor.setText(
                "Blue",
                false
        );

        updateAccountType("Cash");
    }

    private void loadAccounts() {
        new Thread(() -> {
            List<Account> accounts =
                    DatabaseClient
                            .getInstance(
                                    getApplicationContext()
                            )
                            .getAppDatabase()
                            .accountDao()
                            .getAllAccounts();

            List<AccountBalance> balances =
                    DatabaseClient
                            .getInstance(
                                    getApplicationContext()
                            )
                            .getAppDatabase()
                            .accountDao()
                            .getAccountBalances();

            Map<Integer, Double> balanceMap =
                    new HashMap<>();

            for (AccountBalance balance : balances) {
                balanceMap.put(
                        balance.id,
                        balance.currentBalance
                );
            }

            runOnUiThread(() ->
                    showAccounts(
                            accounts,
                            balanceMap
                    )
            );
        }).start();
    }

    private void showAccounts(
            List<Account> accounts,
            Map<Integer, Double> balanceMap
    ) {
        accountContainer.removeAllViews();

        boolean accountListEmpty =
                accounts == null
                        || accounts.isEmpty();

        txtEmptyAccounts.setVisibility(
                accountListEmpty
                        ? View.VISIBLE
                        : View.GONE
        );

        if (emptyAccountsCard != null) {
            emptyAccountsCard.setVisibility(
                    accountListEmpty
                            ? View.VISIBLE
                            : View.GONE
            );
        }

        if (accountListEmpty) {
            return;
        }

        for (Account account : accounts) {
            double currentBalance = 0;

            Double balance =
                    balanceMap.get(
                            account.getId()
                    );

            if (balance != null) {
                currentBalance = balance;
            }

            addAccountCard(
                    account,
                    currentBalance
            );
        }
    }

    private void addAccountCard(
            Account account,
            double currentBalance
    ) {
        int accountColor =
                parseAccountColor(
                        account.getColor()
                );

        MaterialCardView card =
                new MaterialCardView(this);

        card.setCardBackgroundColor(
                getColorValue(
                        R.color.app_surface
                )
        );

        card.setRadius(dpToPx(20));
        card.setCardElevation(dpToPx(1));
        card.setStrokeColor(
                createTranslucentColor(
                        accountColor,
                        90
                )
        );
        card.setStrokeWidth(dpToPx(1));
        card.setClickable(true);
        card.setFocusable(true);

        card.setRippleColor(
                ColorStateList.valueOf(
                        createTranslucentColor(
                                accountColor,
                                35
                        )
                )
        );

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        cardParams.setMargins(
                0,
                0,
                0,
                dpToPx(12)
        );

        card.setLayoutParams(cardParams);

        LinearLayout content =
                new LinearLayout(this);

        content.setOrientation(
                LinearLayout.VERTICAL
        );

        content.setPadding(
                dpToPx(16),
                dpToPx(16),
                dpToPx(16),
                dpToPx(14)
        );

        LinearLayout header =
                new LinearLayout(this);

        header.setGravity(
                Gravity.CENTER_VERTICAL
        );

        header.setOrientation(
                LinearLayout.HORIZONTAL
        );

        TextView accountIcon =
                createAccountIcon(
                        account,
                        accountColor
                );

        header.addView(accountIcon);

        LinearLayout accountDetails =
                new LinearLayout(this);

        accountDetails.setOrientation(
                LinearLayout.VERTICAL
        );

        LinearLayout.LayoutParams detailsParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        detailsParams.setMargins(
                dpToPx(13),
                0,
                dpToPx(8),
                0
        );

        accountDetails.setLayoutParams(
                detailsParams
        );

        TextView txtName =
                new TextView(this);

        txtName.setText(
                account.getName()
        );

        txtName.setTextColor(
                getColorValue(
                        R.color.app_text_primary
                )
        );

        txtName.setTextSize(17);
        txtName.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );
        txtName.setMaxLines(2);

        TextView txtType =
                new TextView(this);

        txtType.setText(
                getAccountTypeLabel(
                        account.getType()
                )
        );

        txtType.setTextColor(
                getColorValue(
                        R.color.app_text_secondary
                )
        );

        txtType.setTextSize(12);

        LinearLayout.LayoutParams typeParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        typeParams.setMargins(
                0,
                dpToPx(3),
                0,
                0
        );

        txtType.setLayoutParams(typeParams);

        accountDetails.addView(txtName);
        accountDetails.addView(txtType);

        header.addView(accountDetails);

        LinearLayout balanceContainer =
                new LinearLayout(this);

        balanceContainer.setOrientation(
                LinearLayout.VERTICAL
        );

        balanceContainer.setGravity(
                Gravity.END
        );

        TextView balanceLabel =
                new TextView(this);

        balanceLabel.setText(
                "Current Balance"
        );

        balanceLabel.setTextColor(
                getColorValue(
                        R.color.app_text_secondary
                )
        );

        balanceLabel.setTextSize(10);
        balanceLabel.setGravity(Gravity.END);

        TextView txtCurrentBalance =
                new TextView(this);

        txtCurrentBalance.setText(
                formatAmount(currentBalance)
        );

        txtCurrentBalance.setTextColor(
                accountColor
        );

        txtCurrentBalance.setTextSize(17);
        txtCurrentBalance.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );
        txtCurrentBalance.setGravity(Gravity.END);
        txtCurrentBalance.setMaxLines(1);

        LinearLayout.LayoutParams balanceParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        balanceParams.setMargins(
                0,
                dpToPx(3),
                0,
                0
        );

        txtCurrentBalance.setLayoutParams(
                balanceParams
        );

        balanceContainer.addView(balanceLabel);
        balanceContainer.addView(txtCurrentBalance);

        header.addView(balanceContainer);

        content.addView(header);

        View divider =
                createDivider();

        LinearLayout.LayoutParams dividerParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dpToPx(1)
                );

        dividerParams.setMargins(
                0,
                dpToPx(15),
                0,
                dpToPx(13)
        );

        divider.setLayoutParams(
                dividerParams
        );

        content.addView(divider);

        LinearLayout lowerRow =
                new LinearLayout(this);

        lowerRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        lowerRow.setGravity(
                Gravity.CENTER_VERTICAL
        );

        LinearLayout openingBalanceContainer =
                new LinearLayout(this);

        openingBalanceContainer.setOrientation(
                LinearLayout.VERTICAL
        );

        LinearLayout.LayoutParams openingContainerParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        openingBalanceContainer.setLayoutParams(
                openingContainerParams
        );

        TextView openingLabel =
                new TextView(this);

        openingLabel.setText(
                "Opening Balance"
        );

        openingLabel.setTextColor(
                getColorValue(
                        R.color.app_text_secondary
                )
        );

        openingLabel.setTextSize(10);

        TextView txtOpeningBalance =
                new TextView(this);

        txtOpeningBalance.setText(
                formatAmount(
                        account.getOpeningBalance()
                )
        );

        txtOpeningBalance.setTextColor(
                getColorValue(
                        R.color.app_text_primary
                )
        );

        txtOpeningBalance.setTextSize(14);
        txtOpeningBalance.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        LinearLayout.LayoutParams openingValueParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        openingValueParams.setMargins(
                0,
                dpToPx(3),
                0,
                0
        );

        txtOpeningBalance.setLayoutParams(
                openingValueParams
        );

        openingBalanceContainer.addView(
                openingLabel
        );

        openingBalanceContainer.addView(
                txtOpeningBalance
        );

        lowerRow.addView(
                openingBalanceContainer
        );

        LinearLayout actionRow =
                new LinearLayout(this);

        actionRow.setGravity(Gravity.END);
        actionRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        MaterialButton btnEdit =
                createActionButton(
                        "Edit",
                        getColorValue(
                                R.color.secondary
                        ),
                        getColorValue(
                                R.color.info_surface
                        ),
                        getColorValue(
                                R.color.info_outline
                        )
                );

        MaterialButton btnDelete =
                createActionButton(
                        "Delete",
                        getColorValue(
                                R.color.expense
                        ),
                        getColorValue(
                                R.color.error_surface
                        ),
                        getColorValue(
                                R.color.error_outline
                        )
                );

        LinearLayout.LayoutParams editParams =
                new LinearLayout.LayoutParams(
                        dpToPx(72),
                        dpToPx(42)
                );

        btnEdit.setLayoutParams(
                editParams
        );

        LinearLayout.LayoutParams deleteParams =
                new LinearLayout.LayoutParams(
                        dpToPx(78),
                        dpToPx(42)
                );

        deleteParams.setMargins(
                dpToPx(7),
                0,
                0,
                0
        );

        btnDelete.setLayoutParams(
                deleteParams
        );

        BubbleTouchAnimator.apply(btnEdit);
        BubbleTouchAnimator.apply(btnDelete);

        btnEdit.setOnClickListener(
                view -> showEditDialog(account)
        );

        btnDelete.setOnClickListener(
                view -> confirmDelete(account)
        );

        actionRow.addView(btnEdit);
        actionRow.addView(btnDelete);

        lowerRow.addView(actionRow);

        content.addView(lowerRow);

        card.addView(content);

        BubbleTouchAnimator.apply(card);

        card.setOnClickListener(
                view -> showEditDialog(account)
        );

        accountContainer.addView(card);
    }

    private TextView createAccountIcon(
            Account account,
            int accountColor
    ) {
        TextView iconView =
                new TextView(this);

        iconView.setText(
                getAccountIconText(
                        account.getType()
                )
        );

        iconView.setTextColor(
                accountColor
        );

        iconView.setTextSize(19);
        iconView.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        iconView.setGravity(Gravity.CENTER);

        GradientDrawable iconBackground =
                new GradientDrawable();

        iconBackground.setShape(
                GradientDrawable.RECTANGLE
        );

        iconBackground.setColor(
                createTranslucentColor(
                        accountColor,
                        25
                )
        );

        iconBackground.setStroke(
                dpToPx(1),
                createTranslucentColor(
                        accountColor,
                        80
                )
        );

        iconBackground.setCornerRadius(
                dpToPx(14)
        );

        iconView.setBackground(
                iconBackground
        );

        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(
                        dpToPx(48),
                        dpToPx(48)
                );

        iconView.setLayoutParams(
                iconParams
        );

        return iconView;
    }

    private MaterialButton createActionButton(
            String text,
            int textColor,
            int backgroundColor,
            int strokeColor
    ) {
        MaterialButton button =
                new MaterialButton(this);

        button.setText(text);
        button.setTextSize(12);
        button.setTextColor(textColor);
        button.setAllCaps(false);
        button.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );
        button.setGravity(Gravity.CENTER);
        button.setCornerRadius(dpToPx(13));

        button.setBackgroundTintList(
                ColorStateList.valueOf(
                        backgroundColor
                )
        );

        button.setStrokeColor(
                ColorStateList.valueOf(
                        strokeColor
                )
        );

        button.setStrokeWidth(dpToPx(1));

        button.setInsetTop(0);
        button.setInsetBottom(0);

        button.setPadding(
                dpToPx(6),
                0,
                dpToPx(6),
                0
        );

        return button;
    }

    private View createDivider() {
        View divider =
                new View(this);

        divider.setBackgroundColor(
                getColorValue(
                        R.color.app_divider
                )
        );

        return divider;
    }

    private void showEditDialog(
            Account account
    ) {
        LinearLayout dialogLayout =
                new LinearLayout(this);

        dialogLayout.setOrientation(
                LinearLayout.VERTICAL
        );

        dialogLayout.setPadding(
                dpToPx(22),
                dpToPx(8),
                dpToPx(22),
                dpToPx(8)
        );

        TextView accountIcon =
                createAccountIcon(
                        account,
                        parseAccountColor(
                                account.getColor()
                        )
                );

        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(
                        dpToPx(54),
                        dpToPx(54)
                );

        iconParams.gravity =
                Gravity.CENTER_HORIZONTAL;

        accountIcon.setLayoutParams(
                iconParams
        );

        dialogLayout.addView(
                accountIcon
        );

        TextView txtAccountName =
                new TextView(this);

        txtAccountName.setText(
                account.getName()
        );

        txtAccountName.setTextColor(
                getColorValue(
                        R.color.app_text_primary
                )
        );

        txtAccountName.setTextSize(20);
        txtAccountName.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );
        txtAccountName.setGravity(
                Gravity.CENTER
        );

        LinearLayout.LayoutParams accountNameParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        accountNameParams.setMargins(
                0,
                dpToPx(10),
                0,
                0
        );

        txtAccountName.setLayoutParams(
                accountNameParams
        );

        dialogLayout.addView(
                txtAccountName
        );

        TextView txtInfo =
                new TextView(this);

        txtInfo.setText(
                "Account name cannot be changed because existing transactions are linked to it."
        );

        txtInfo.setTextColor(
                getColorValue(
                        R.color.app_text_secondary
                )
        );

        txtInfo.setTextSize(12);
        txtInfo.setGravity(Gravity.CENTER);
        txtInfo.setLineSpacing(
                dpToPx(2),
                1f
        );

        LinearLayout.LayoutParams infoParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        infoParams.setMargins(
                0,
                dpToPx(5),
                0,
                dpToPx(4)
        );

        txtInfo.setLayoutParams(
                infoParams
        );

        dialogLayout.addView(txtInfo);

        TextView txtBalanceLabel =
                createLabel(
                        "Opening Balance"
                );

        dialogLayout.addView(
                txtBalanceLabel
        );

        TextInputLayout inputBalance =
                createDialogInputLayout(
                        "Opening balance"
                );

        inputBalance.setPrefixText("₹  ");

        TextInputEditText editBalance =
                new TextInputEditText(this);

        editBalance.setText(
                formatPlainAmount(
                        account.getOpeningBalance()
                )
        );

        editBalance.setInputType(
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL
                        | InputType.TYPE_NUMBER_FLAG_SIGNED
        );

        editBalance.setSingleLine(true);
        editBalance.setTextSize(16);
        editBalance.setTextColor(
                getColorValue(
                        R.color.app_text_primary
                )
        );

        inputBalance.addView(editBalance);

        dialogLayout.addView(
                inputBalance
        );

        TextView txtColorLabel =
                createLabel(
                        "Account Color"
                );

        dialogLayout.addView(
                txtColorLabel
        );

        TextInputLayout inputColor =
                createDialogInputLayout(
                        "Select color"
                );

        inputColor.setEndIconMode(
                TextInputLayout.END_ICON_DROPDOWN_MENU
        );

        MaterialAutoCompleteTextView editColor =
                new MaterialAutoCompleteTextView(this);

        editColor.setFocusable(false);
        editColor.setInputType(
                InputType.TYPE_NULL
        );
        editColor.setTextSize(15);
        editColor.setTextColor(
                getColorValue(
                        R.color.app_text_primary
                )
        );
        editColor.setPadding(
                dpToPx(16),
                0,
                dpToPx(12),
                0
        );

        ArrayAdapter<String> colorAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_list_item_1,
                        colorNames
                );

        editColor.setAdapter(colorAdapter);

        editColor.setText(
                getColorName(
                        account.getColor()
                ),
                false
        );

        inputColor.addView(editColor);

        dialogLayout.addView(
                inputColor
        );

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle("Edit Account")
                        .setView(dialogLayout)
                        .setNegativeButton(
                                "Cancel",
                                null
                        )
                        .setPositiveButton(
                                "Save",
                                null
                        )
                        .create();

        dialog.setOnShowListener(listener -> {
            dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
            ).setTextColor(
                    getColorValue(
                            R.color.secondary
                    )
            );

            dialog.getButton(
                    AlertDialog.BUTTON_NEGATIVE
            ).setTextColor(
                    getColorValue(
                            R.color.app_text_secondary
                    )
            );

            dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener(view -> {
                String openingText =
                        getEditTextValue(
                                editBalance
                        );

                double openingBalance;

                try {
                    openingBalance =
                            openingText.isEmpty()
                                    ? 0
                                    : Double.parseDouble(
                                    openingText
                            );

                } catch (Exception exception) {
                    inputBalance.setError(
                            "Enter a valid amount"
                    );

                    editBalance.requestFocus();
                    return;
                }

                inputBalance.setError(null);

                account.setOpeningBalance(
                        openingBalance
                );

                account.setColor(
                        getColorCode(
                                editColor
                                        .getText()
                                        .toString()
                                        .trim()
                        )
                );

                dialog.getButton(
                        AlertDialog.BUTTON_POSITIVE
                ).setEnabled(false);

                new Thread(() -> {
                    DatabaseClient
                            .getInstance(
                                    getApplicationContext()
                            )
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

    private TextInputLayout createDialogInputLayout(
            String hint
    ) {
        TextInputLayout inputLayout =
                new TextInputLayout(this);

        inputLayout.setHint(hint);

        inputLayout.setBoxBackgroundMode(
                TextInputLayout.BOX_BACKGROUND_OUTLINE
        );

        inputLayout.setBoxBackgroundColor(
                getColorValue(
                        R.color.app_surface
                )
        );

        inputLayout.setBoxStrokeColor(
                getColorValue(
                        R.color.secondary
                )
        );

        inputLayout.setBoxCornerRadii(
                dpToPx(14),
                dpToPx(14),
                dpToPx(14),
                dpToPx(14)
        );

        inputLayout.setHintTextColor(
                ColorStateList.valueOf(
                        getColorValue(
                                R.color.app_text_secondary
                        )
                )
        );

        inputLayout.setLayoutParams(
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        return inputLayout;
    }

    private void confirmDelete(
            Account account
    ) {
        if (account
                .getName()
                .equalsIgnoreCase("Cash")) {

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
                        "Delete \""
                                + account.getName()
                                + "\"?\n\n"
                                + "Existing transaction history will remain safe."
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .setPositiveButton(
                        "Delete",
                        (dialog, which) ->
                                deleteAccount(account)
                )
                .show();
    }

    private void deleteAccount(
            Account account
    ) {
        new Thread(() -> {
            DatabaseClient
                    .getInstance(
                            getApplicationContext()
                    )
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
    }

    private TextView createLabel(
            String text
    ) {
        TextView label =
                new TextView(this);

        label.setText(text);
        label.setTextColor(
                getColorValue(
                        R.color.app_text_primary
                )
        );

        label.setTextSize(14);
        label.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        params.setMargins(
                0,
                dpToPx(16),
                0,
                dpToPx(7)
        );

        label.setLayoutParams(params);

        return label;
    }

    private String getAccountTypeLabel(
            String accountType
    ) {
        if (accountType == null
                || accountType.trim().isEmpty()) {

            return "Financial Account";
        }

        switch (accountType.toLowerCase(
                Locale.getDefault()
        )) {
            case "cash":
                return "Cash Account";

            case "wallet":
                return "Digital Wallet";

            case "upi":
                return "UPI / Digital Payment";

            case "bank":
                return "Bank Account";

            case "other":
                return "Custom Account";

            default:
                return accountType + " Account";
        }
    }

    private String getAccountIconText(
            String accountType
    ) {
        if (accountType == null) {
            return "A";
        }

        switch (accountType.toLowerCase(
                Locale.getDefault()
        )) {
            case "cash":
                return "₹";

            case "wallet":
                return "W";

            case "upi":
                return "U";

            case "bank":
                return "B";

            default:
                return "A";
        }
    }

    private String getColorCode(
            String colorName
    ) {
        if (colorName == null) {
            return "#0F6CBD";
        }

        switch (colorName) {
            case "Green":
                return "#107C10";

            case "Purple":
                return "#6B4FA3";

            case "Orange":
                return "#A15A00";

            case "Red":
                return "#C42B1C";

            case "Teal":
                return "#087A81";

            case "Blue":
            default:
                return "#0F6CBD";
        }
    }

    private String getColorName(
            String colorCode
    ) {
        if (colorCode == null) {
            return "Blue";
        }

        if (colorCode.equalsIgnoreCase("#107C10")
                || colorCode.equalsIgnoreCase("#2E7D32")) {

            return "Green";
        }

        if (colorCode.equalsIgnoreCase("#6B4FA3")
                || colorCode.equalsIgnoreCase("#6A1B9A")) {

            return "Purple";
        }

        if (colorCode.equalsIgnoreCase("#A15A00")
                || colorCode.equalsIgnoreCase("#EF6C00")) {

            return "Orange";
        }

        if (colorCode.equalsIgnoreCase("#C42B1C")
                || colorCode.equalsIgnoreCase("#D32F2F")) {

            return "Red";
        }

        if (colorCode.equalsIgnoreCase("#087A81")
                || colorCode.equalsIgnoreCase("#00838F")) {

            return "Teal";
        }

        return "Blue";
    }

    private int parseAccountColor(
            String colorCode
    ) {
        if (colorCode == null
                || colorCode.trim().isEmpty()) {

            return getColorValue(
                    R.color.secondary
            );
        }

        try {
            return Color.parseColor(
                    colorCode
            );

        } catch (Exception exception) {
            return getColorValue(
                    R.color.secondary
            );
        }
    }

    private int createTranslucentColor(
            int baseColor,
            int alpha
    ) {
        return Color.argb(
                alpha,
                Color.red(baseColor),
                Color.green(baseColor),
                Color.blue(baseColor)
        );
    }

    private String getEditTextValue(
            TextInputEditText editText
    ) {
        if (editText.getText() == null) {
            return "";
        }

        return editText
                .getText()
                .toString()
                .trim();
    }

    private String formatPlainAmount(
            double amount
    ) {
        if (amount == Math.rint(amount)) {
            return String.format(
                    Locale.getDefault(),
                    "%.0f",
                    amount
            );
        }

        return String.format(
                Locale.getDefault(),
                "%.2f",
                amount
        );
    }

    private String formatAmount(
            double amount
    ) {
        NumberFormat numberFormat =
                NumberFormat.getCurrencyInstance(
                        new Locale("en", "IN")
                );

        return numberFormat.format(amount);
    }

    private int getColorValue(
            int colorResource
    ) {
        return ContextCompat.getColor(
                this,
                colorResource
        );
    }

    private int dpToPx(
            int dp
    ) {
        float density =
                getResources()
                        .getDisplayMetrics()
                        .density;

        return Math.round(
                dp * density
        );
    }
}