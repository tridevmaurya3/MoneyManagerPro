package com.example.moneymanagerpro.activities;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.AccountBalance;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;

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

        initializeViews();
        applyTouchAnimations();
        setupClickListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadDashboardData();
    }

    private void initializeViews() {
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
    }

    private void applyTouchAnimations() {
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
    }

    private void setupClickListeners() {
        btnAddIncome.setOnClickListener(view ->
                openActivity(AddIncomeActivity.class)
        );

        btnAddExpense.setOnClickListener(view ->
                openActivity(AddExpenseActivity.class)
        );

        btnAccounts.setOnClickListener(view ->
                openActivity(AccountActivity.class)
        );

        btnCategories.setOnClickListener(view ->
                openActivity(CategoryActivity.class)
        );

        btnTransactions.setOnClickListener(view ->
                openActivity(TransactionsActivity.class)
        );

        btnReports.setOnClickListener(view ->
                openActivity(ReportActivity.class)
        );

        btnMoreFeatures.setOnClickListener(view ->
                showMoreToolsMenu()
        );

        cardBalance.setOnClickListener(view ->
                openActivity(AccountActivity.class)
        );

        cardCash.setOnClickListener(view ->
                openActivity(AccountActivity.class)
        );
    }

    private void openActivity(Class<?> activityClass) {
        startActivity(
                new Intent(
                        DashboardActivity.this,
                        activityClass
                )
        );
    }

    private void loadDashboardData() {
        new Thread(() -> {
            double income = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .transactionDao()
                    .getTotalAmountByType("INCOME");

            double expense = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .transactionDao()
                    .getTotalAmountByType("EXPENSE");

            List<AccountBalance> accountBalances =
                    DatabaseClient
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
        scrollView.setClipToPadding(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scrollView.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(
                dpToPx(16),
                dpToPx(10),
                dpToPx(16),
                dpToPx(34)
        );

        GradientDrawable sheetBackground = new GradientDrawable();
        sheetBackground.setColor(getColorValue(R.color.app_background));
        sheetBackground.setCornerRadii(
                new float[]{
                        dpToPx(28), dpToPx(28),
                        dpToPx(28), dpToPx(28),
                        0, 0,
                        0, 0
                }
        );
        mainLayout.setBackground(sheetBackground);

        scrollView.addView(
                mainLayout,
                new ScrollView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        addSheetHandle(mainLayout);
        addSheetHeader(mainLayout, dialog);

        addSectionHeader(
                mainLayout,
                "Planning & Money",
                "Manage transfers, goals, budgets and repayments"
        );

        GridLayout planningGrid = createToolGrid();
        mainLayout.addView(planningGrid);

        addTool(
                dialog,
                planningGrid,
                "Transfer",
                "Move money between accounts",
                "↔",
                TransferActivity.class,
                R.color.purple,
                R.color.purple_surface,
                R.color.purple_outline
        );

        addTool(
                dialog,
                planningGrid,
                "Goals",
                "Track your savings targets",
                "◎",
                GoalActivity.class,
                R.color.success,
                R.color.success_surface,
                R.color.success_outline
        );

        addTool(
                dialog,
                planningGrid,
                "Recurring",
                "Regular income and expenses",
                "↻",
                RecurringActivity.class,
                R.color.orange,
                R.color.warning_surface,
                R.color.warning_outline
        );

        addTool(
                dialog,
                planningGrid,
                "Budgets",
                "Control category spending",
                "%",
                BudgetActivity.class,
                R.color.expense,
                R.color.error_surface,
                R.color.error_outline
        );

        addTool(
                dialog,
                planningGrid,
                "Loans",
                "Track lending and EMI",
                "₹",
                LoanActivity.class,
                R.color.pink,
                R.color.pink_surface,
                R.color.pink_outline
        );

        addTool(
                dialog,
                planningGrid,
                "Investments",
                "Manage saved investments",
                "↗",
                InvestmentActivity.class,
                R.color.purple,
                R.color.purple_surface,
                R.color.purple_outline
        );

        addSectionHeader(
                mainLayout,
                "Insights & Tracking",
                "Understand financial activity and upcoming payments"
        );

        GridLayout insightsGrid = createToolGrid();
        mainLayout.addView(insightsGrid);

        addTool(
                dialog,
                insightsGrid,
                "Analytics",
                "View spending insights",
                "◔",
                AnalyticsActivity.class,
                R.color.teal,
                R.color.teal_surface,
                R.color.teal_outline
        );

        addTool(
                dialog,
                insightsGrid,
                "Charts & Trends",
                "Visual financial reports",
                "▥",
                ChartsActivity.class,
                R.color.teal,
                R.color.teal_surface,
                R.color.teal_outline
        );

        addTool(
                dialog,
                insightsGrid,
                "Smart Advisor",
                "Personal finance guidance",
                "✦",
                FinanceAdvisorActivity.class,
                R.color.purple,
                R.color.purple_surface,
                R.color.purple_outline
        );

        addTool(
                dialog,
                insightsGrid,
                "Calendar",
                "Daily cash-flow view",
                "▦",
                CalendarActivity.class,
                R.color.secondary,
                R.color.info_surface,
                R.color.info_outline
        );

        addTool(
                dialog,
                insightsGrid,
                "Bills & Plans",
                "Subscriptions and bills",
                "□",
                SubscriptionActivity.class,
                R.color.purple,
                R.color.purple_surface,
                R.color.purple_outline
        );

        addTool(
                dialog,
                insightsGrid,
                "Bill Photos",
                "Saved receipt images",
                "▣",
                ReceiptGalleryActivity.class,
                R.color.expense,
                R.color.error_surface,
                R.color.error_outline
        );

        addSectionHeader(
                mainLayout,
                "Data & App",
                "Export, backup, import and configure the app"
        );

        GridLayout dataGrid = createToolGrid();
        mainLayout.addView(dataGrid);

        addTool(
                dialog,
                dataGrid,
                "Export",
                "Create reports and files",
                "⇩",
                ExportActivity.class,
                R.color.app_text_secondary,
                R.color.app_surface_soft,
                R.color.app_outline
        );

        addTool(
                dialog,
                dataGrid,
                "Backup",
                "Protect and restore data",
                "B",
                BackupActivity.class,
                R.color.secondary,
                R.color.info_surface,
                R.color.info_outline
        );

        addTool(
                dialog,
                dataGrid,
                "Import CSV",
                "Import transaction records",
                "CSV",
                CsvImportActivity.class,
                R.color.secondary,
                R.color.info_surface,
                R.color.info_outline
        );

        addTool(
                dialog,
                dataGrid,
                "Settings",
                "Privacy and preferences",
                "⚙",
                SettingsActivity.class,
                R.color.app_text_primary,
                R.color.app_surface_muted,
                R.color.app_outline
        );

        addTool(
                dialog,
                dataGrid,
                "Help Guide",
                "Learn how features work",
                "?",
                HelpActivity.class,
                R.color.teal,
                R.color.teal_surface,
                R.color.teal_outline
        );

        dialog.setContentView(scrollView);

        dialog.setOnShowListener(dialogInterface -> {
            FrameLayout bottomSheet =
                    dialog.findViewById(
                            com.google.android.material.R.id.design_bottom_sheet
                    );

            if (bottomSheet == null) {
                return;
            }

            bottomSheet.setBackgroundColor(Color.TRANSPARENT);

            ViewGroup.LayoutParams params = bottomSheet.getLayoutParams();
            params.height = (int) (
                    getResources().getDisplayMetrics().heightPixels * 0.94f
            );
            bottomSheet.setLayoutParams(params);

            BottomSheetBehavior<FrameLayout> behavior =
                    BottomSheetBehavior.from(bottomSheet);

            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        });

        dialog.show();
    }

    private void addSheetHandle(LinearLayout mainLayout) {
        View handle = new View(this);

        GradientDrawable handleBackground = new GradientDrawable();
        handleBackground.setColor(getColorValue(R.color.app_outline));
        handleBackground.setCornerRadius(dpToPx(10));
        handle.setBackground(handleBackground);

        LinearLayout.LayoutParams handleParams =
                new LinearLayout.LayoutParams(
                        dpToPx(42),
                        dpToPx(5)
                );

        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.setMargins(0, dpToPx(2), 0, dpToPx(18));
        handle.setLayoutParams(handleParams);

        mainLayout.addView(handle);
    }

    private void addSheetHeader(
            LinearLayout mainLayout,
            BottomSheetDialog dialog
    ) {
        LinearLayout headerLayout = new LinearLayout(this);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(Gravity.CENTER_VERTICAL);
        headerLayout.setPadding(dpToPx(2), 0, 0, dpToPx(4));

        LinearLayout titleContainer = new LinearLayout(this);
        titleContainer.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams titleContainerParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );
        titleContainer.setLayoutParams(titleContainerParams);

        TextView title = new TextView(this);
        title.setText("More Tools");
        title.setTextSize(25);
        title.setTextColor(getColorValue(R.color.app_text_primary));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleContainer.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Advanced finance features in one place");
        subtitle.setTextSize(13);
        subtitle.setTextColor(getColorValue(R.color.app_text_secondary));
        subtitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        subtitleParams.setMargins(0, dpToPx(3), 0, 0);
        subtitle.setLayoutParams(subtitleParams);

        titleContainer.addView(subtitle);
        headerLayout.addView(titleContainer);

        TextView closeButton = new TextView(this);
        closeButton.setText("×");
        closeButton.setTextSize(27);
        closeButton.setTextColor(getColorValue(R.color.app_text_secondary));
        closeButton.setGravity(Gravity.CENTER);
        closeButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        closeButton.setClickable(true);
        closeButton.setFocusable(true);

        GradientDrawable closeBackground = new GradientDrawable();
        closeBackground.setColor(getColorValue(R.color.app_surface_soft));
        closeBackground.setCornerRadius(dpToPx(14));
        closeButton.setBackground(closeBackground);

        LinearLayout.LayoutParams closeParams =
                new LinearLayout.LayoutParams(
                        dpToPx(44),
                        dpToPx(44)
                );
        closeParams.setMargins(dpToPx(10), 0, 0, 0);
        closeButton.setLayoutParams(closeParams);

        BubbleTouchAnimator.apply(closeButton);
        closeButton.setOnClickListener(view -> dialog.dismiss());

        headerLayout.addView(closeButton);
        mainLayout.addView(headerLayout);
    }

    private void addSectionHeader(
            LinearLayout mainLayout,
            String titleText,
            String subtitleText
    ) {
        LinearLayout sectionLayout = new LinearLayout(this);
        sectionLayout.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams sectionParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        sectionParams.setMargins(0, dpToPx(23), 0, dpToPx(11));
        sectionLayout.setLayoutParams(sectionParams);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(getColorValue(R.color.app_text_primary));
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        sectionLayout.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextColor(getColorValue(R.color.app_text_secondary));
        subtitle.setTextSize(11);
        subtitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        subtitleParams.setMargins(0, dpToPx(3), 0, 0);
        subtitle.setLayoutParams(subtitleParams);

        sectionLayout.addView(subtitle);
        mainLayout.addView(sectionLayout);
    }

    private GridLayout createToolGrid() {
        GridLayout gridLayout = new GridLayout(this);
        gridLayout.setColumnCount(2);
        gridLayout.setAlignmentMode(GridLayout.ALIGN_MARGINS);
        gridLayout.setUseDefaultMargins(false);

        gridLayout.setLayoutParams(
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        return gridLayout;
    }

    private void addTool(
            BottomSheetDialog dialog,
            GridLayout toolGrid,
            String title,
            String subtitle,
            String iconText,
            Class<?> activityClass,
            @ColorRes int iconColorResource,
            @ColorRes int surfaceColorResource,
            @ColorRes int outlineColorResource
    ) {
        int iconColor = getColorValue(iconColorResource);
        int surfaceColor = getColorValue(surfaceColorResource);
        int outlineColor = getColorValue(outlineColorResource);

        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(surfaceColor);
        card.setRadius(dpToPx(16));
        card.setCardElevation(dpToPx(1));
        card.setStrokeColor(outlineColor);
        card.setStrokeWidth(dpToPx(1));
        card.setClickable(true);
        card.setFocusable(true);
        card.setRippleColor(
                ColorStateList.valueOf(createRippleColor(iconColor))
        );

        GridLayout.LayoutParams cardParams = new GridLayout.LayoutParams();
        cardParams.rowSpec = GridLayout.spec(GridLayout.UNDEFINED);
        cardParams.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        cardParams.width = 0;
        cardParams.height = dpToPx(96);
        cardParams.setMargins(
                dpToPx(5),
                dpToPx(5),
                dpToPx(5),
                dpToPx(5)
        );
        card.setLayoutParams(cardParams);

        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setGravity(Gravity.START);
        contentLayout.setPadding(
                dpToPx(12),
                dpToPx(11),
                dpToPx(12),
                dpToPx(10)
        );

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView iconView = new TextView(this);
        iconView.setText(iconText);
        iconView.setTextColor(iconColor);

        if ("CSV".equals(iconText)) {
            iconView.setTextSize(10);
        } else {
            iconView.setTextSize(18);
        }

        iconView.setGravity(Gravity.CENTER);
        iconView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        GradientDrawable iconBackground = new GradientDrawable();
        iconBackground.setShape(GradientDrawable.RECTANGLE);
        iconBackground.setColor(createIconBackgroundColor(iconColor));
        iconBackground.setCornerRadius(dpToPx(12));
        iconView.setBackground(iconBackground);

        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(
                        dpToPx(34),
                        dpToPx(34)
                );
        iconView.setLayoutParams(iconParams);
        topRow.addView(iconView);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(getColorValue(R.color.app_text_primary));
        titleView.setTextSize(15);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleView.setMaxLines(2);

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );
        titleParams.setMargins(dpToPx(10), 0, 0, 0);
        titleView.setLayoutParams(titleParams);

        topRow.addView(titleView);
        contentLayout.addView(topRow);

        TextView subtitleView = new TextView(this);
        subtitleView.setText(subtitle);
        subtitleView.setTextColor(getColorValue(R.color.app_text_secondary));
        subtitleView.setTextSize(11);
        subtitleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        subtitleView.setMaxLines(2);
        subtitleView.setLineSpacing(0, 1f);

        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        subtitleParams.setMargins(0, dpToPx(10), 0, 0);
        subtitleView.setLayoutParams(subtitleParams);

        contentLayout.addView(subtitleView);
        card.addView(contentLayout);

        BubbleTouchAnimator.apply(card);

        card.setOnClickListener(view -> {
            dialog.dismiss();

            startActivity(
                    new Intent(
                            DashboardActivity.this,
                            activityClass
                    )
            );
        });

        toolGrid.addView(card);
    }

    private int getColorValue(@ColorRes int colorResource) {
        return ContextCompat.getColor(this, colorResource);
    }

    private int createIconBackgroundColor(int baseColor) {
        return Color.argb(
                24,
                Color.red(baseColor),
                Color.green(baseColor),
                Color.blue(baseColor)
        );
    }

    private int createRippleColor(int baseColor) {
        return Color.argb(
                38,
                Color.red(baseColor),
                Color.green(baseColor),
                Color.blue(baseColor)
        );
    }

    private String formatAmount(double amount) {
        NumberFormat numberFormat =
                NumberFormat.getCurrencyInstance(
                        new Locale("en", "IN")
                );

        return numberFormat.format(amount);
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}