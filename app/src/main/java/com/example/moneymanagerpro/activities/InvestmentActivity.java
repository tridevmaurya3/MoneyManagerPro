package com.example.moneymanagerpro.activities;

import android.app.DatePickerDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.model.InvestmentItem;
import com.example.moneymanagerpro.utils.InvestmentStore;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class InvestmentActivity extends AppCompatActivity {

    private TextInputLayout inputInvestmentName;
    private TextInputLayout inputInvestedAmount;
    private TextInputLayout inputCurrentValue;

    private TextInputEditText etInvestmentName;
    private TextInputEditText etInvestedAmount;
    private TextInputEditText etCurrentValue;
    private TextInputEditText etMonthlyContribution;
    private TextInputEditText etInvestmentDate;
    private TextInputEditText etInvestmentNote;

    private MaterialAutoCompleteTextView dropdownInvestmentType;
    private MaterialButton btnSaveInvestment;

    private TextView txtTotalInvested;
    private TextView txtCurrentPortfolioValue;
    private TextView txtProfitLoss;
    private TextView txtReturnPercentage;
    private TextView txtInvestmentEmpty;

    private LinearLayout investmentContainer;

    private Calendar selectedCalendar;
    private String selectedDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_investment);

        TextView btnBack = findViewById(R.id.btnBack);

        inputInvestmentName = findViewById(R.id.inputInvestmentName);
        inputInvestedAmount = findViewById(R.id.inputInvestedAmount);
        inputCurrentValue = findViewById(R.id.inputCurrentValue);

        etInvestmentName = findViewById(R.id.etInvestmentName);
        etInvestedAmount = findViewById(R.id.etInvestedAmount);
        etCurrentValue = findViewById(R.id.etCurrentValue);
        etMonthlyContribution = findViewById(R.id.etMonthlyContribution);
        etInvestmentDate = findViewById(R.id.etInvestmentDate);
        etInvestmentNote = findViewById(R.id.etInvestmentNote);

        dropdownInvestmentType = findViewById(R.id.dropdownInvestmentType);
        btnSaveInvestment = findViewById(R.id.btnSaveInvestment);

        txtTotalInvested = findViewById(R.id.txtTotalInvested);
        txtCurrentPortfolioValue = findViewById(R.id.txtCurrentPortfolioValue);
        txtProfitLoss = findViewById(R.id.txtProfitLoss);
        txtReturnPercentage = findViewById(R.id.txtReturnPercentage);
        txtInvestmentEmpty = findViewById(R.id.txtInvestmentEmpty);

        investmentContainer = findViewById(R.id.investmentContainer);

        selectedCalendar = Calendar.getInstance();

        setupInvestmentTypes();
        updateDateField();

        btnBack.setOnClickListener(v -> finish());

        etInvestmentDate.setOnClickListener(v -> showDatePicker());

        btnSaveInvestment.setOnClickListener(v -> saveInvestment());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadInvestments();
    }

    private void setupInvestmentTypes() {
        String[] investmentTypes = {
                "SIP / Mutual Fund",
                "Fixed Deposit",
                "Stocks",
                "Gold",
                "PPF",
                "NPS",
                "Crypto",
                "Real Estate",
                "Other"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                investmentTypes
        );

        dropdownInvestmentType.setAdapter(adapter);
        dropdownInvestmentType.setText("SIP / Mutual Fund", false);
    }

    private void showDatePicker() {
        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    selectedCalendar.set(Calendar.YEAR, year);
                    selectedCalendar.set(Calendar.MONTH, month);
                    selectedCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                    updateDateField();
                },
                selectedCalendar.get(Calendar.YEAR),
                selectedCalendar.get(Calendar.MONTH),
                selectedCalendar.get(Calendar.DAY_OF_MONTH)
        );

        dialog.show();
    }

    private void updateDateField() {
        selectedDate = new SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.US
        ).format(selectedCalendar.getTime());

        String visibleDate = new SimpleDateFormat(
                "dd MMMM yyyy",
                Locale.ENGLISH
        ).format(selectedCalendar.getTime());

        etInvestmentDate.setText(visibleDate);
    }

    private void saveInvestment() {
        String name = getText(etInvestmentName);
        String investedText = getText(etInvestedAmount);
        String currentValueText = getText(etCurrentValue);

        if (name.isEmpty()) {
            inputInvestmentName.setError("Enter investment name");
            return;
        }

        if (investedText.isEmpty()) {
            inputInvestedAmount.setError("Enter invested amount");
            return;
        }

        if (currentValueText.isEmpty()) {
            inputCurrentValue.setError("Enter current value");
            return;
        }

        double investedAmount;
        double currentValue;
        double monthlyContribution;

        try {
            investedAmount = Double.parseDouble(investedText);
            currentValue = Double.parseDouble(currentValueText);

            String monthlyText = getText(etMonthlyContribution);
            monthlyContribution = monthlyText.isEmpty()
                    ? 0
                    : Double.parseDouble(monthlyText);

        } catch (Exception exception) {
            inputInvestedAmount.setError("Enter valid amounts");
            inputCurrentValue.setError("Enter valid amounts");
            return;
        }

        if (investedAmount <= 0 || currentValue < 0 || monthlyContribution < 0) {
            inputInvestedAmount.setError("Amounts cannot be negative");
            return;
        }

        inputInvestmentName.setError(null);
        inputInvestedAmount.setError(null);
        inputCurrentValue.setError(null);

        InvestmentItem item = new InvestmentItem();

        item.setName(name);
        item.setType(dropdownInvestmentType.getText().toString().trim());
        item.setInvestedAmount(investedAmount);
        item.setCurrentValue(currentValue);
        item.setMonthlyContribution(monthlyContribution);
        item.setStartDate(selectedDate);
        item.setNote(getText(etInvestmentNote));

        InvestmentStore.add(getApplicationContext(), item);

        clearForm();
        loadInvestments();
    }

    private void clearForm() {
        etInvestmentName.setText("");
        etInvestedAmount.setText("");
        etCurrentValue.setText("");
        etMonthlyContribution.setText("");
        etInvestmentNote.setText("");

        selectedCalendar = Calendar.getInstance();
        updateDateField();

        dropdownInvestmentType.setText("SIP / Mutual Fund", false);
    }

    private void loadInvestments() {
        List<InvestmentItem> investments = InvestmentStore.getAll(
                getApplicationContext()
        );

        investmentContainer.removeAllViews();

        double totalInvested = 0;
        double totalCurrentValue = 0;

        for (InvestmentItem item : investments) {
            totalInvested += item.getInvestedAmount();
            totalCurrentValue += item.getCurrentValue();
        }

        double profitLoss = totalCurrentValue - totalInvested;

        txtTotalInvested.setText(formatMoney(totalInvested));
        txtCurrentPortfolioValue.setText(formatMoney(totalCurrentValue));
        txtProfitLoss.setText(formatMoney(profitLoss));

        if (profitLoss >= 0) {
            txtProfitLoss.setTextColor(Color.parseColor("#166534"));
        } else {
            txtProfitLoss.setTextColor(Color.parseColor("#B91C1C"));
        }

        if (totalInvested > 0) {
            double returnPercentage = (profitLoss / totalInvested) * 100;

            txtReturnPercentage.setText(
                    String.format(
                            Locale.US,
                            "Portfolio return: %.2f%%",
                            returnPercentage
                    )
            );
        } else {
            txtReturnPercentage.setText("Portfolio return: 0.00%");
        }

        if (investments.isEmpty()) {
            txtInvestmentEmpty.setVisibility(View.VISIBLE);
            return;
        }

        txtInvestmentEmpty.setVisibility(View.GONE);

        for (InvestmentItem item : investments) {
            investmentContainer.addView(createInvestmentCard(item));
        }
    }

    private MaterialCardView createInvestmentCard(InvestmentItem item) {
        double profitLoss = item.getCurrentValue() - item.getInvestedAmount();
        boolean isProfit = profitLoss >= 0;

        MaterialCardView cardView = new MaterialCardView(this);
        cardView.setCardBackgroundColor(Color.WHITE);
        cardView.setRadius(dp(22));
        cardView.setCardElevation(dp(5));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );

        cardParams.setMargins(0, 0, 0, dp(14));
        cardView.setLayoutParams(cardParams);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(15), dp(16), dp(15));

        TextView txtName = new TextView(this);
        txtName.setText(item.getName());
        txtName.setTextColor(Color.parseColor("#172033"));
        txtName.setTextSize(19);
        txtName.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView txtType = new TextView(this);
        txtType.setText(
                item.getType()
                        + "  •  Started " + item.getStartDate()
        );

        txtType.setTextColor(Color.parseColor("#64748B"));
        txtType.setTextSize(13);
        txtType.setPadding(0, dp(4), 0, 0);

        LinearLayout amountRow = new LinearLayout(this);
        amountRow.setOrientation(LinearLayout.HORIZONTAL);
        amountRow.setPadding(0, dp(14), 0, 0);

        TextView investedLabel = createAmountLabel(
                "Invested\n" + formatMoney(item.getInvestedAmount()),
                "#1D4ED8"
        );

        TextView currentLabel = createAmountLabel(
                "Current Value\n" + formatMoney(item.getCurrentValue()),
                "#6D28D9"
        );

        TextView profitLabel = createAmountLabel(
                (isProfit ? "Gain\n" : "Loss\n") + formatMoney(profitLoss),
                isProfit ? "#15803D" : "#B91C1C"
        );

        amountRow.addView(investedLabel);
        amountRow.addView(currentLabel);
        amountRow.addView(profitLabel);

        TextView monthlyInfo = new TextView(this);

        if (item.getMonthlyContribution() > 0) {
            monthlyInfo.setText(
                    "Monthly contribution: "
                            + formatMoney(item.getMonthlyContribution())
            );
        } else {
            monthlyInfo.setText("One-time investment");
        }

        monthlyInfo.setTextColor(Color.parseColor("#475569"));
        monthlyInfo.setTextSize(13);
        monthlyInfo.setPadding(0, dp(12), 0, 0);

        if (item.getNote() != null && !item.getNote().trim().isEmpty()) {
            TextView note = new TextView(this);
            note.setText(item.getNote());
            note.setTextColor(Color.parseColor("#64748B"));
            note.setTextSize(13);
            note.setPadding(0, dp(6), 0, 0);

            content.addView(txtName);
            content.addView(txtType);
            content.addView(amountRow);
            content.addView(monthlyInfo);
            content.addView(note);
        } else {
            content.addView(txtName);
            content.addView(txtType);
            content.addView(amountRow);
            content.addView(monthlyInfo);
        }

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setPadding(0, dp(14), 0, 0);

        MaterialButton btnUpdateValue = new MaterialButton(this);
        btnUpdateValue.setText("Update Value");
        btnUpdateValue.setTextColor(Color.WHITE);
        btnUpdateValue.setTextSize(13);
        btnUpdateValue.setAllCaps(false);
        btnUpdateValue.setBackgroundTintList(
                ColorStateList.valueOf(Color.parseColor("#6D28D9"))
        );

        LinearLayout.LayoutParams updateParams = new LinearLayout.LayoutParams(
                0,
                dp(48),
                1
        );

        updateParams.setMargins(0, 0, dp(5), 0);
        btnUpdateValue.setLayoutParams(updateParams);

        btnUpdateValue.setOnClickListener(v -> showUpdateValueDialog(item));

        MaterialButton btnDelete = new MaterialButton(this);
        btnDelete.setText("Delete");
        btnDelete.setTextColor(Color.parseColor("#B91C1C"));
        btnDelete.setTextSize(13);
        btnDelete.setAllCaps(false);
        btnDelete.setBackgroundTintList(
                ColorStateList.valueOf(Color.parseColor("#FFF1F2"))
        );

        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                0,
                dp(48),
                1
        );

        deleteParams.setMargins(dp(5), 0, 0, 0);
        btnDelete.setLayoutParams(deleteParams);

        btnDelete.setOnClickListener(v -> showDeleteDialog(item));

        buttonRow.addView(btnUpdateValue);
        buttonRow.addView(btnDelete);

        content.addView(buttonRow);
        cardView.addView(content);

        return cardView;
    }

    private TextView createAmountLabel(String text, String color) {
        TextView textView = new TextView(this);

        textView.setText(text);
        textView.setTextColor(Color.parseColor(color));
        textView.setTextSize(12);
        textView.setTypeface(null, android.graphics.Typeface.BOLD);
        textView.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        );

        textView.setLayoutParams(params);

        return textView;
    }

    private void showUpdateValueDialog(InvestmentItem item) {
        EditText input = new EditText(this);

        input.setInputType(
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL
        );

        input.setGravity(Gravity.CENTER);
        input.setText(String.valueOf(item.getCurrentValue()));
        input.setSelectAllOnFocus(true);

        int padding = dp(18);
        input.setPadding(padding, padding, padding, padding);

        new AlertDialog.Builder(this)
                .setTitle("Update Current Value")
                .setMessage(item.getName())
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    try {
                        double newValue = Double.parseDouble(
                                input.getText().toString().trim()
                        );

                        if (newValue < 0) {
                            return;
                        }

                        item.setCurrentValue(newValue);

                        InvestmentStore.update(
                                getApplicationContext(),
                                item
                        );

                        loadInvestments();

                    } catch (Exception ignored) {
                    }
                })
                .show();
    }

    private void showDeleteDialog(InvestmentItem item) {
        new AlertDialog.Builder(this)
                .setTitle("Delete investment?")
                .setMessage(
                        item.getName()
                                + " will be removed from your portfolio."
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    InvestmentStore.delete(
                            getApplicationContext(),
                            item.getId()
                    );

                    loadInvestments();
                })
                .show();
    }

    private String getText(TextInputEditText editText) {
        return editText.getText() == null
                ? ""
                : editText.getText().toString().trim();
    }

    private String formatMoney(double amount) {
        NumberFormat formatter = NumberFormat.getNumberInstance(
                new Locale("en", "IN")
        );

        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);

        return "₹" + formatter.format(amount);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}