package com.example.moneymanagerpro.activities;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.AccountBalance;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class DashboardActivity extends AppCompatActivity {

    private TextView txtBalance;
    private TextView txtIncome;
    private TextView txtExpense;
    private TextView txtCash;

    private View cardBalance;
    private View cardIncome;
    private View cardExpense;
    private View cardCash;

    private View btnAddIncome;
    private View btnAddExpense;
    private View btnAccounts;
    private View btnCategories;
    private View btnTransactions;
    private View btnReports;
    private View btnMoreFeatures;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        txtBalance = findViewById(R.id.txtBalance);
        txtIncome = findViewById(R.id.txtIncome);
        txtExpense = findViewById(R.id.txtExpense);
        txtCash = findViewById(R.id.txtCash);

        cardBalance = findViewById(R.id.cardBalance);
        cardIncome = findViewById(R.id.cardIncome);
        cardExpense = findViewById(R.id.cardExpense);
        cardCash = findViewById(R.id.cardCash);

        btnAddIncome = findViewById(R.id.btnAddIncome);
        btnAddExpense = findViewById(R.id.btnAddExpense);
        btnAccounts = findViewById(R.id.btnAccounts);
        btnCategories = findViewById(R.id.btnCategories);
        btnTransactions = findViewById(R.id.btnTransactions);
        btnReports = findViewById(R.id.btnReports);
        btnMoreFeatures = findViewById(R.id.btnMoreFeatures);

        BubbleTouchAnimator.apply(cardBalance);
        BubbleTouchAnimator.apply(cardIncome);
        BubbleTouchAnimator.apply(cardExpense);
        BubbleTouchAnimator.apply(cardCash);

        BubbleTouchAnimator.apply(btnAddIncome);
        BubbleTouchAnimator.apply(btnAddExpense);
        BubbleTouchAnimator.apply(btnAccounts);
        BubbleTouchAnimator.apply(btnCategories);
        BubbleTouchAnimator.apply(btnTransactions);
        BubbleTouchAnimator.apply(btnReports);
        BubbleTouchAnimator.apply(btnMoreFeatures);

        btnAddIncome.setOnClickListener(v ->
                startActivity(new Intent(
                        DashboardActivity.this,
                        AddIncomeActivity.class
                ))
        );

        btnAddExpense.setOnClickListener(v ->
                startActivity(new Intent(
                        DashboardActivity.this,
                        AddExpenseActivity.class
                ))
        );

        btnAccounts.setOnClickListener(v ->
                startActivity(new Intent(
                        DashboardActivity.this,
                        AccountActivity.class
                ))
        );

        btnCategories.setOnClickListener(v ->
                startActivity(new Intent(
                        DashboardActivity.this,
                        CategoryActivity.class
                ))
        );

        btnTransactions.setOnClickListener(v ->
                startActivity(new Intent(
                        DashboardActivity.this,
                        TransactionsActivity.class
                ))
        );

        btnReports.setOnClickListener(v ->
                startActivity(new Intent(
                        DashboardActivity.this,
                        ReportActivity.class
                ))
        );

        btnMoreFeatures.setOnClickListener(v -> showMoreToolsMenu());

        cardBalance.setOnClickListener(v ->
                startActivity(new Intent(
                        DashboardActivity.this,
                        AccountActivity.class
                ))
        );

        cardCash.setOnClickListener(v ->
                startActivity(new Intent(
                        DashboardActivity.this,
                        AccountActivity.class
                ))
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadDashboardData();
    }

    private void loadDashboardData() {
        new Thread(() -> {
            double income = DatabaseClient.getInstance(getApplicationContext())
                    .getAppDatabase()
                    .transactionDao()
                    .getTotalAmountByType("INCOME");

            double expense = DatabaseClient.getInstance(getApplicationContext())
                    .getAppDatabase()
                    .transactionDao()
                    .getTotalAmountByType("EXPENSE");

            List<AccountBalance> accountBalances = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .accountDao()
                    .getAccountBalances();

            double totalBalance = 0;
            double cashBalance = 0;

            for (AccountBalance accountBalance : accountBalances) {
                totalBalance += accountBalance.currentBalance;

                if (accountBalance.name != null
                        && accountBalance.name.equalsIgnoreCase("Cash")) {
                    cashBalance = accountBalance.currentBalance;
                }
            }

            double finalIncome = income;
            double finalExpense = expense;
            double finalTotalBalance = totalBalance;
            double finalCashBalance = cashBalance;

            runOnUiThread(() -> {
                txtIncome.setText(formatAmount(finalIncome));
                txtExpense.setText(formatAmount(finalExpense));
                txtBalance.setText(formatAmount(finalTotalBalance));
                txtCash.setText(formatAmount(finalCashBalance));
            });
        }).start();
    }

    private void showMoreToolsMenu() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.WHITE);

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(dpToPx(16), dpToPx(18), dpToPx(16), dpToPx(28));

        scrollView.addView(mainLayout);

        TextView title = new TextView(this);
        title.setText("More Tools");
        title.setTextSize(23);
        title.setTextColor(Color.parseColor("#172033"));
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        TextView subtitle = new TextView(this);
        subtitle.setText("Advanced finance features");
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.parseColor("#64748B"));

        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );

        subtitleParams.setMargins(0, dpToPx(4), 0, dpToPx(12));
        subtitle.setLayoutParams(subtitleParams);

        GridLayout toolGrid = new GridLayout(this);
        toolGrid.setColumnCount(2);
        toolGrid.setAlignmentMode(GridLayout.ALIGN_MARGINS);
        toolGrid.setUseDefaultMargins(false);

        mainLayout.addView(title);
        mainLayout.addView(subtitle);
        mainLayout.addView(toolGrid);

        addTool(dialog, toolGrid, "Transfer", "#6A1B9A", TransferActivity.class);
        addTool(dialog, toolGrid, "Goals", "#2E7D32", GoalActivity.class);
        addTool(dialog, toolGrid, "Analytics", "#00838F", AnalyticsActivity.class);
        addTool(dialog, toolGrid, "Charts\n& Trends", "#0F766E", ChartsActivity.class);
        addTool(dialog, toolGrid, "Smart\nAdvisor", "#6D28D9", FinanceAdvisorActivity.class);
        addTool(dialog, toolGrid, "Calendar", "#3949AB", CalendarActivity.class);
        addTool(dialog, toolGrid, "Bills\n& Plans", "#7B1FA2", SubscriptionActivity.class);
        addTool(dialog, toolGrid, "Bill\nPhotos", "#B91C1C", ReceiptGalleryActivity.class);
        addTool(dialog, toolGrid, "Recurring", "#EF6C00", RecurringActivity.class);
        addTool(dialog, toolGrid, "Budgets", "#C62828", BudgetActivity.class);
        addTool(dialog, toolGrid, "Loans", "#5D4037", LoanActivity.class);
        addTool(dialog, toolGrid, "Investments", "#6D28D9", InvestmentActivity.class);
        addTool(dialog, toolGrid, "Export", "#37474F", ExportActivity.class);
        addTool(dialog, toolGrid, "Backup", "#455A64", BackupActivity.class);
        addTool(dialog, toolGrid, "Import\nCSV", "#1D4ED8", CsvImportActivity.class);
        addTool(dialog, toolGrid, "Settings", "#424242", SettingsActivity.class);
        addTool(dialog, toolGrid, "Help\nGuide", "#0F766E", HelpActivity.class);

        dialog.setContentView(scrollView);
        dialog.show();
    }

    private void addTool(
            BottomSheetDialog dialog,
            GridLayout toolGrid,
            String title,
            String color,
            Class<?> activityClass
    ) {
        MaterialButton button = new MaterialButton(this);

        button.setText(title);
        button.setTextSize(14);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));

        button.setBackgroundTintList(
                ColorStateList.valueOf(Color.parseColor(color))
        );

        button.setCornerRadius(dpToPx(20));

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();

        params.rowSpec = GridLayout.spec(GridLayout.UNDEFINED);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);

        params.width = 0;
        params.height = dpToPx(86);

        params.setMargins(
                dpToPx(5),
                dpToPx(5),
                dpToPx(5),
                dpToPx(5)
        );

        button.setLayoutParams(params);

        BubbleTouchAnimator.apply(button);

        button.setOnClickListener(v -> {
            dialog.dismiss();

            startActivity(new Intent(
                    DashboardActivity.this,
                    activityClass
            ));
        });

        toolGrid.addView(button);
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