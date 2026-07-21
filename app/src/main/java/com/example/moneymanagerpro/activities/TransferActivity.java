package com.example.moneymanagerpro.activities;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Account;
import com.example.moneymanagerpro.model.Transaction;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class TransferActivity extends AppCompatActivity {

    private TextInputLayout inputAmount;
    private TextInputEditText etAmount;
    private TextInputEditText etDate;
    private TextInputEditText etNote;
    private MaterialAutoCompleteTextView dropdownFromAccount;
    private MaterialAutoCompleteTextView dropdownToAccount;
    private MaterialButton btnSaveTransfer;

    private Calendar selectedCalendar;
    private String selectedDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transfer);

        inputAmount = findViewById(R.id.inputAmount);
        etAmount = findViewById(R.id.etAmount);
        etDate = findViewById(R.id.etDate);
        etNote = findViewById(R.id.etNote);
        dropdownFromAccount = findViewById(R.id.dropdownFromAccount);
        dropdownToAccount = findViewById(R.id.dropdownToAccount);
        btnSaveTransfer = findViewById(R.id.btnSaveTransfer);

        TextView btnBack = findViewById(R.id.btnBack);

        selectedCalendar = Calendar.getInstance();
        updateDateField();

        btnBack.setOnClickListener(v -> finish());

        etDate.setOnClickListener(v -> showDatePicker());

        btnSaveTransfer.setOnClickListener(v -> saveTransfer());

        loadAccounts();
    }

    private void loadAccounts() {
        new Thread(() -> {
            List<Account> accounts = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .accountDao()
                    .getAllAccounts();

            List<String> accountNames = new ArrayList<>();

            for (Account account : accounts) {
                accountNames.add(account.getName());
            }

            runOnUiThread(() -> {
                if (accountNames.size() < 2) {
                    btnSaveTransfer.setEnabled(false);

                    Toast.makeText(
                            TransferActivity.this,
                            "Please add at least two accounts before transfer",
                            Toast.LENGTH_LONG
                    ).show();

                    return;
                }

                setAccountDropdowns(accountNames);
            });
        }).start();
    }

    private void setAccountDropdowns(List<String> accountNames) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                accountNames
        );

        dropdownFromAccount.setAdapter(adapter);
        dropdownToAccount.setAdapter(adapter);

        String fromAccount = accountNames.get(0);

        for (String account : accountNames) {
            if (account.equalsIgnoreCase("Cash")) {
                fromAccount = account;
                break;
            }
        }

        String toAccount = accountNames.get(0);

        for (String account : accountNames) {
            if (!account.equalsIgnoreCase(fromAccount)) {
                toAccount = account;
                break;
            }
        }

        dropdownFromAccount.setText(fromAccount, false);
        dropdownToAccount.setText(toAccount, false);
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
                "yyyy-MM-dd HH:mm",
                Locale.US
        ).format(selectedCalendar.getTime());

        String visibleDate = new SimpleDateFormat(
                "dd MMMM yyyy",
                Locale.ENGLISH
        ).format(selectedCalendar.getTime());

        etDate.setText(visibleDate);
    }

    private void saveTransfer() {
        String amountText = etAmount.getText() == null
                ? ""
                : etAmount.getText().toString().trim();

        if (amountText.isEmpty()) {
            inputAmount.setError("Please enter transfer amount");
            return;
        }

        double amount;

        try {
            amount = Double.parseDouble(amountText);
        } catch (Exception exception) {
            inputAmount.setError("Enter a valid amount");
            return;
        }

        if (amount <= 0) {
            inputAmount.setError("Amount must be greater than zero");
            return;
        }

        inputAmount.setError(null);

        String fromAccount = dropdownFromAccount.getText().toString().trim();
        String toAccount = dropdownToAccount.getText().toString().trim();

        if (fromAccount.isEmpty() || toAccount.isEmpty()) {
            Toast.makeText(
                    this,
                    "Please select both accounts",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        if (fromAccount.equalsIgnoreCase(toAccount)) {
            Toast.makeText(
                    this,
                    "From and To accounts must be different",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        String note = etNote.getText() == null
                ? ""
                : etNote.getText().toString().trim();

        String transferOutNote = "Transfer to " + toAccount;
        String transferInNote = "Transfer from " + fromAccount;

        if (!note.isEmpty()) {
            transferOutNote += " - " + note;
            transferInNote += " - " + note;
        }

        Transaction transferOut = new Transaction();
        transferOut.setType("TRANSFER_OUT");
        transferOut.setAmount(amount);
        transferOut.setCategory("Account Transfer");
        transferOut.setAccount(fromAccount);
        transferOut.setNote(transferOutNote);
        transferOut.setDate(selectedDate);

        Transaction transferIn = new Transaction();
        transferIn.setType("TRANSFER_IN");
        transferIn.setAmount(amount);
        transferIn.setCategory("Account Transfer");
        transferIn.setAccount(toAccount);
        transferIn.setNote(transferInNote);
        transferIn.setDate(selectedDate);

        btnSaveTransfer.setEnabled(false);
        btnSaveTransfer.setText("Transferring...");

        new Thread(() -> {
            try {
                DatabaseClient.getInstance(getApplicationContext())
                        .getAppDatabase()
                        .runInTransaction(() -> {
                            DatabaseClient.getInstance(getApplicationContext())
                                    .getAppDatabase()
                                    .transactionDao()
                                    .insert(transferOut);

                            DatabaseClient.getInstance(getApplicationContext())
                                    .getAppDatabase()
                                    .transactionDao()
                                    .insert(transferIn);
                        });

                runOnUiThread(() -> {
                    Toast.makeText(
                            TransferActivity.this,
                            "Money transferred successfully",
                            Toast.LENGTH_SHORT
                    ).show();

                    finish();
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    btnSaveTransfer.setEnabled(true);
                    btnSaveTransfer.setText("Transfer Money");

                    Toast.makeText(
                            TransferActivity.this,
                            "Unable to complete transfer",
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }
        }).start();
    }
}