package com.example.moneymanagerpro.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.database.AppDatabase;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Account;
import com.example.moneymanagerpro.model.Budget;
import com.example.moneymanagerpro.model.Category;
import com.example.moneymanagerpro.model.Goal;
import com.example.moneymanagerpro.model.Loan;
import com.example.moneymanagerpro.model.RecurringTransaction;
import com.example.moneymanagerpro.model.Transaction;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BackupActivity extends AppCompatActivity {

    private static final int REQUEST_CREATE_BACKUP = 301;
    private static final int REQUEST_RESTORE_BACKUP = 302;

    private TextView txtBackupStatus;
    private Button btnCreateBackup;
    private Button btnRestoreBackup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_backup);

        txtBackupStatus = findViewById(R.id.txtBackupStatus);
        btnCreateBackup = findViewById(R.id.btnCreateBackup);
        btnRestoreBackup = findViewById(R.id.btnRestoreBackup);

        btnCreateBackup.setOnClickListener(v -> chooseBackupLocation());

        btnRestoreBackup.setOnClickListener(v -> chooseBackupFile());
    }

    private void chooseBackupLocation() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");

        intent.putExtra(
                Intent.EXTRA_TITLE,
                "MoneyManagerPro_Backup_" +
                        getFileDate() +
                        ".mmpbackup"
        );

        startActivityForResult(intent, REQUEST_CREATE_BACKUP);
    }

    private void chooseBackupFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");

        startActivityForResult(intent, REQUEST_RESTORE_BACKUP);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            @Nullable Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK ||
                data == null ||
                data.getData() == null) {
            return;
        }

        Uri selectedFile = data.getData();

        if (requestCode == REQUEST_CREATE_BACKUP) {
            createBackup(selectedFile);

        } else if (requestCode == REQUEST_RESTORE_BACKUP) {
            confirmRestore(selectedFile);
        }
    }

    private void confirmRestore(Uri backupUri) {
        new AlertDialog.Builder(this)
                .setTitle("Restore Full Backup")
                .setMessage(
                        "⚠️ वर्तमान app का सारा data हट जाएगा और selected backup का data वापस आएगा।\n\n" +
                                "Restore से पहले नया backup बनाना बेहतर है।"
                )
                .setPositiveButton(
                        "Restore Now",
                        (dialog, which) -> restoreBackup(backupUri)
                )
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createBackup(Uri backupUri) {
        setBackupButtonsEnabled(false);
        txtBackupStatus.setText("Backup file बन रही है...");

        new Thread(() -> {
            try {
                JSONObject backupData = buildBackupJson();

                OutputStream outputStream = getContentResolver()
                        .openOutputStream(backupUri);

                if (outputStream == null) {
                    throw new Exception("Cannot create backup file");
                }

                outputStream.write(
                        backupData
                                .toString(2)
                                .getBytes("UTF-8")
                );

                outputStream.flush();
                outputStream.close();

                runOnUiThread(() -> {
                    txtBackupStatus.setText(
                            "Backup सफलतापूर्वक बन गया। इसे सुरक्षित जगह रखें।"
                    );

                    setBackupButtonsEnabled(true);

                    Toast.makeText(
                            BackupActivity.this,
                            "Full backup created successfully",
                            Toast.LENGTH_LONG
                    ).show();
                });

            } catch (Exception exception) {
                runOnUiThread(() -> {
                    txtBackupStatus.setText(
                            "Backup नहीं बन सका। फिर से कोशिश करें।"
                    );

                    setBackupButtonsEnabled(true);

                    Toast.makeText(
                            BackupActivity.this,
                            "Backup failed",
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }
        }).start();
    }

    private void restoreBackup(Uri backupUri) {
        setBackupButtonsEnabled(false);
        txtBackupStatus.setText("Backup restore हो रहा है...");

        new Thread(() -> {
            try {
                String backupText = readTextFromUri(backupUri);

                JSONObject root = new JSONObject(backupText);

                BackupContent backupContent = parseBackup(root);

                AppDatabase database = DatabaseClient
                        .getInstance(getApplicationContext())
                        .getAppDatabase();

                database.runInTransaction(() -> {
                    database.clearAllTables();

                    for (Category category : backupContent.categories) {
                        database.categoryDao().insert(category);
                    }

                    if (backupContent.accounts.isEmpty()) {
                        Account cashAccount = new Account();
                        cashAccount.setName("Cash");
                        cashAccount.setType("Cash");
                        cashAccount.setOpeningBalance(0);
                        cashAccount.setColor("#2E7D32");

                        database.accountDao().insert(cashAccount);
                    } else {
                        for (Account account : backupContent.accounts) {
                            database.accountDao().insert(account);
                        }
                    }

                    for (Goal goal : backupContent.goals) {
                        database.goalDao().insert(goal);
                    }

                    for (RecurringTransaction recurring :
                            backupContent.recurringTransactions) {
                        database.recurringTransactionDao().insert(recurring);
                    }

                    for (Budget budget : backupContent.budgets) {
                        database.budgetDao().insert(budget);
                    }

                    for (Loan loan : backupContent.loans) {
                        database.loanDao().insert(loan);
                    }

                    for (Transaction transaction :
                            backupContent.transactions) {
                        database.transactionDao().insert(transaction);
                    }
                });

                runOnUiThread(() -> {
                    txtBackupStatus.setText(
                            "Backup सफलतापूर्वक restore हो गया।"
                    );

                    setBackupButtonsEnabled(true);

                    Toast.makeText(
                            BackupActivity.this,
                            "Data restored successfully",
                            Toast.LENGTH_LONG
                    ).show();
                });

            } catch (Exception exception) {
                runOnUiThread(() -> {
                    txtBackupStatus.setText(
                            "Restore नहीं हो सका। सही backup file चुनें।"
                    );

                    setBackupButtonsEnabled(true);

                    Toast.makeText(
                            BackupActivity.this,
                            "Restore failed",
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }
        }).start();
    }

    private JSONObject buildBackupJson() throws Exception {
        JSONObject root = new JSONObject();

        root.put("appName", "Money Manager Pro");
        root.put("backupVersion", 1);
        root.put("createdAt", getCurrentDateTime());

        List<Transaction> transactions = DatabaseClient
                .getInstance(getApplicationContext())
                .getAppDatabase()
                .transactionDao()
                .getAllTransactions();

        List<Category> categories = DatabaseClient
                .getInstance(getApplicationContext())
                .getAppDatabase()
                .categoryDao()
                .getAllCategories();

        List<Account> accounts = DatabaseClient
                .getInstance(getApplicationContext())
                .getAppDatabase()
                .accountDao()
                .getAllAccounts();

        List<Goal> goals = DatabaseClient
                .getInstance(getApplicationContext())
                .getAppDatabase()
                .goalDao()
                .getAllGoals();

        List<RecurringTransaction> recurringTransactions =
                DatabaseClient
                        .getInstance(getApplicationContext())
                        .getAppDatabase()
                        .recurringTransactionDao()
                        .getAllRecurringTransactions();

        List<Budget> budgets = DatabaseClient
                .getInstance(getApplicationContext())
                .getAppDatabase()
                .budgetDao()
                .getAllBudgets();

        List<Loan> loans = DatabaseClient
                .getInstance(getApplicationContext())
                .getAppDatabase()
                .loanDao()
                .getAllLoans();

        root.put("transactions", createTransactionArray(transactions));
        root.put("categories", createCategoryArray(categories));
        root.put("accounts", createAccountArray(accounts));
        root.put("goals", createGoalArray(goals));
        root.put(
                "recurringTransactions",
                createRecurringArray(recurringTransactions)
        );
        root.put("budgets", createBudgetArray(budgets));
        root.put("loans", createLoanArray(loans));

        return root;
    }

    private BackupContent parseBackup(JSONObject root) throws Exception {
        if (root.optInt("backupVersion", 0) != 1) {
            throw new Exception("Unsupported backup version");
        }

        BackupContent content = new BackupContent();

        content.transactions = parseTransactions(
                root.optJSONArray("transactions")
        );

        content.categories = parseCategories(
                root.optJSONArray("categories")
        );

        content.accounts = parseAccounts(
                root.optJSONArray("accounts")
        );

        content.goals = parseGoals(
                root.optJSONArray("goals")
        );

        content.recurringTransactions = parseRecurringTransactions(
                root.optJSONArray("recurringTransactions")
        );

        content.budgets = parseBudgets(
                root.optJSONArray("budgets")
        );

        content.loans = parseLoans(
                root.optJSONArray("loans")
        );

        return content;
    }

    private JSONArray createTransactionArray(
            List<Transaction> transactions
    ) throws Exception {
        JSONArray array = new JSONArray();

        for (Transaction transaction : transactions) {
            JSONObject object = new JSONObject();

            object.put("type", transaction.getType());
            object.put("amount", transaction.getAmount());
            object.put("category", transaction.getCategory());
            object.put("account", transaction.getAccount());
            object.put("note", transaction.getNote());
            object.put("date", transaction.getDate());

            array.put(object);
        }

        return array;
    }

    private JSONArray createCategoryArray(
            List<Category> categories
    ) throws Exception {
        JSONArray array = new JSONArray();

        for (Category category : categories) {
            JSONObject object = new JSONObject();

            object.put("name", category.getName());
            object.put("type", category.getType());
            object.put("color", category.getColor());

            array.put(object);
        }

        return array;
    }

    private JSONArray createAccountArray(
            List<Account> accounts
    ) throws Exception {
        JSONArray array = new JSONArray();

        for (Account account : accounts) {
            JSONObject object = new JSONObject();

            object.put("name", account.getName());
            object.put("type", account.getType());
            object.put(
                    "openingBalance",
                    account.getOpeningBalance()
            );
            object.put("color", account.getColor());

            array.put(object);
        }

        return array;
    }

    private JSONArray createGoalArray(
            List<Goal> goals
    ) throws Exception {
        JSONArray array = new JSONArray();

        for (Goal goal : goals) {
            JSONObject object = new JSONObject();

            object.put("name", goal.getName());
            object.put("targetAmount", goal.getTargetAmount());
            object.put("savedAmount", goal.getSavedAmount());
            object.put("targetDate", goal.getTargetDate());
            object.put("color", goal.getColor());

            array.put(object);
        }

        return array;
    }

    private JSONArray createRecurringArray(
            List<RecurringTransaction> recurringTransactions
    ) throws Exception {
        JSONArray array = new JSONArray();

        for (RecurringTransaction recurring : recurringTransactions) {
            JSONObject object = new JSONObject();

            object.put("type", recurring.getType());
            object.put("amount", recurring.getAmount());
            object.put("category", recurring.getCategory());
            object.put("account", recurring.getAccount());
            object.put("note", recurring.getNote());
            object.put("frequency", recurring.getFrequency());
            object.put("startDate", recurring.getStartDate());
            object.put("nextRunDate", recurring.getNextRunDate());
            object.put("active", recurring.isActive());

            array.put(object);
        }

        return array;
    }

    private JSONArray createBudgetArray(
            List<Budget> budgets
    ) throws Exception {
        JSONArray array = new JSONArray();

        for (Budget budget : budgets) {
            JSONObject object = new JSONObject();

            object.put("category", budget.getCategory());
            object.put("period", budget.getPeriod());
            object.put("limitAmount", budget.getLimitAmount());

            array.put(object);
        }

        return array;
    }

    private JSONArray createLoanArray(
            List<Loan> loans
    ) throws Exception {
        JSONArray array = new JSONArray();

        for (Loan loan : loans) {
            JSONObject object = new JSONObject();

            object.put("personName", loan.getPersonName());
            object.put("loanType", loan.getLoanType());
            object.put("totalAmount", loan.getTotalAmount());
            object.put(
                    "outstandingAmount",
                    loan.getOutstandingAmount()
            );
            object.put("interestRate", loan.getInterestRate());
            object.put("emiAmount", loan.getEmiAmount());
            object.put("dueDate", loan.getDueDate());
            object.put("note", loan.getNote());
            object.put("active", loan.isActive());

            array.put(object);
        }

        return array;
    }

    private List<Transaction> parseTransactions(JSONArray array) {
        List<Transaction> transactions = new ArrayList<>();

        if (array == null) {
            return transactions;
        }

        for (int index = 0; index < array.length(); index++) {
            JSONObject object = array.optJSONObject(index);

            if (object == null) {
                continue;
            }

            Transaction transaction = new Transaction();

            transaction.setType(object.optString("type", ""));
            transaction.setAmount(object.optDouble("amount", 0));
            transaction.setCategory(object.optString("category", ""));
            transaction.setAccount(
                    object.optString("account", "Cash")
            );
            transaction.setNote(object.optString("note", ""));
            transaction.setDate(object.optString("date", ""));

            transactions.add(transaction);
        }

        return transactions;
    }

    private List<Category> parseCategories(JSONArray array) {
        List<Category> categories = new ArrayList<>();

        if (array == null) {
            return categories;
        }

        for (int index = 0; index < array.length(); index++) {
            JSONObject object = array.optJSONObject(index);

            if (object == null) {
                continue;
            }

            Category category = new Category();

            category.setName(object.optString("name", ""));
            category.setType(object.optString("type", ""));
            category.setColor(object.optString("color", "#1565C0"));

            categories.add(category);
        }

        return categories;
    }

    private List<Account> parseAccounts(JSONArray array) {
        List<Account> accounts = new ArrayList<>();

        if (array == null) {
            return accounts;
        }

        for (int index = 0; index < array.length(); index++) {
            JSONObject object = array.optJSONObject(index);

            if (object == null) {
                continue;
            }

            Account account = new Account();

            account.setName(object.optString("name", "Cash"));
            account.setType(object.optString("type", "Cash"));
            account.setOpeningBalance(
                    object.optDouble("openingBalance", 0)
            );
            account.setColor(
                    object.optString("color", "#2E7D32")
            );

            accounts.add(account);
        }

        return accounts;
    }

    private List<Goal> parseGoals(JSONArray array) {
        List<Goal> goals = new ArrayList<>();

        if (array == null) {
            return goals;
        }

        for (int index = 0; index < array.length(); index++) {
            JSONObject object = array.optJSONObject(index);

            if (object == null) {
                continue;
            }

            Goal goal = new Goal();

            goal.setName(object.optString("name", ""));
            goal.setTargetAmount(
                    object.optDouble("targetAmount", 0)
            );
            goal.setSavedAmount(
                    object.optDouble("savedAmount", 0)
            );
            goal.setTargetDate(
                    object.optString("targetDate", "")
            );
            goal.setColor(
                    object.optString("color", "#6C63FF")
            );

            goals.add(goal);
        }

        return goals;
    }

    private List<RecurringTransaction> parseRecurringTransactions(
            JSONArray array
    ) {
        List<RecurringTransaction> recurringTransactions =
                new ArrayList<>();

        if (array == null) {
            return recurringTransactions;
        }

        for (int index = 0; index < array.length(); index++) {
            JSONObject object = array.optJSONObject(index);

            if (object == null) {
                continue;
            }

            RecurringTransaction recurringTransaction =
                    new RecurringTransaction();

            recurringTransaction.setType(
                    object.optString("type", "")
            );
            recurringTransaction.setAmount(
                    object.optDouble("amount", 0)
            );
            recurringTransaction.setCategory(
                    object.optString("category", "")
            );
            recurringTransaction.setAccount(
                    object.optString("account", "Cash")
            );
            recurringTransaction.setNote(
                    object.optString("note", "")
            );
            recurringTransaction.setFrequency(
                    object.optString("frequency", "Monthly")
            );
            recurringTransaction.setStartDate(
                    object.optString("startDate", "")
            );
            recurringTransaction.setNextRunDate(
                    object.optString("nextRunDate", "")
            );
            recurringTransaction.setActive(
                    object.optBoolean("active", true)
            );

            recurringTransactions.add(recurringTransaction);
        }

        return recurringTransactions;
    }

    private List<Budget> parseBudgets(JSONArray array) {
        List<Budget> budgets = new ArrayList<>();

        if (array == null) {
            return budgets;
        }

        for (int index = 0; index < array.length(); index++) {
            JSONObject object = array.optJSONObject(index);

            if (object == null) {
                continue;
            }

            Budget budget = new Budget();

            budget.setCategory(object.optString("category", ""));
            budget.setPeriod(
                    object.optString("period", "Monthly")
            );
            budget.setLimitAmount(
                    object.optDouble("limitAmount", 0)
            );

            budgets.add(budget);
        }

        return budgets;
    }

    private List<Loan> parseLoans(JSONArray array) {
        List<Loan> loans = new ArrayList<>();

        if (array == null) {
            return loans;
        }

        for (int index = 0; index < array.length(); index++) {
            JSONObject object = array.optJSONObject(index);

            if (object == null) {
                continue;
            }

            Loan loan = new Loan();

            loan.setPersonName(
                    object.optString("personName", "")
            );
            loan.setLoanType(
                    object.optString("loanType", "Loan Taken")
            );
            loan.setTotalAmount(
                    object.optDouble("totalAmount", 0)
            );
            loan.setOutstandingAmount(
                    object.optDouble("outstandingAmount", 0)
            );
            loan.setInterestRate(
                    object.optDouble("interestRate", 0)
            );
            loan.setEmiAmount(
                    object.optDouble("emiAmount", 0)
            );
            loan.setDueDate(
                    object.optString("dueDate", "")
            );
            loan.setNote(object.optString("note", ""));
            loan.setActive(object.optBoolean("active", true));

            loans.add(loan);
        }

        return loans;
    }

    private String readTextFromUri(Uri uri) throws Exception {
        InputStream inputStream = getContentResolver()
                .openInputStream(uri);

        if (inputStream == null) {
            throw new Exception("Cannot read backup file");
        }

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, "UTF-8")
        );

        StringBuilder textBuilder = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            textBuilder.append(line);
        }

        reader.close();
        inputStream.close();

        return textBuilder.toString();
    }

    private void setBackupButtonsEnabled(boolean enabled) {
        btnCreateBackup.setEnabled(enabled);
        btnRestoreBackup.setEnabled(enabled);
    }

    private String getFileDate() {
        return new SimpleDateFormat(
                "yyyyMMdd_HHmm",
                Locale.getDefault()
        ).format(new Date());
    }

    private String getCurrentDateTime() {
        return new SimpleDateFormat(
                "dd MMM yyyy, hh:mm a",
                Locale.getDefault()
        ).format(new Date());
    }

    private static class BackupContent {
        List<Transaction> transactions;
        List<Category> categories;
        List<Account> accounts;
        List<Goal> goals;
        List<RecurringTransaction> recurringTransactions;
        List<Budget> budgets;
        List<Loan> loans;
    }
}