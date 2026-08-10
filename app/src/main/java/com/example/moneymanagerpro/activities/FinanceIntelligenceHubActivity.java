package com.example.moneymanagerpro.activities;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.moneymanagerpro.database.AppDatabase;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.AccountBalance;
import com.example.moneymanagerpro.model.Budget;
import com.example.moneymanagerpro.model.CreditCard;
import com.example.moneymanagerpro.model.Loan;
import com.example.moneymanagerpro.model.Transaction;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.text.NumberFormat;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Unified Finance Pro Suite.
 *
 * This screen deliberately reuses the app's existing Room data and feature
 * activities instead of creating a second finance store. The overview and
 * predictions are calculated locally on the device.
 */
public class FinanceIntelligenceHubActivity extends AppCompatActivity {

    private static final String[] DATE_PATTERNS = {
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd",
            "dd-MM-yyyy HH:mm:ss",
            "dd-MM-yyyy HH:mm",
            "dd-MM-yyyy",
            "dd/MM/yyyy HH:mm:ss",
            "dd/MM/yyyy HH:mm",
            "dd/MM/yyyy",
            "MMM dd, yyyy HH:mm",
            "MMM dd, yyyy"
    };

    private TextView txtPeriod;
    private TextView txtBalance;
    private TextView txtIncome;
    private TextView txtExpense;
    private TextView txtSaving;
    private TextView txtCreditAvailable;
    private TextView txtLoanOutstanding;

    private TextView txtExpenseTrend;
    private TextView txtUnusualSpend;
    private TextView txtProjection;
    private TextView txtSavingSuggestion;
    private TextView txtTopCategory;

    private TextView txtBudgetSummary;
    private TextView txtBudgetPrediction;
    private ProgressBar budgetProgress;

    private TextView txtAccountSummary;
    private TextView txtCardSummary;
    private ProgressBar creditProgress;

    private int requestVersion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildScreen());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSnapshot();
    }

    @NonNull
    private View buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.parseColor("#F5F8F4"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(36));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);

        MaterialButton back = button("‹", false);
        back.setTextSize(25);
        back.setContentDescription("Back");
        back.setLayoutParams(new LinearLayout.LayoutParams(dp(46), dp(46)));
        back.setOnClickListener(view -> finish());
        BubbleTouchAnimator.apply(back);
        header.addView(back);

        LinearLayout titleBlock = vertical();
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        );
        titleParams.setMargins(dp(11), 0, 0, 0);
        titleBlock.setLayoutParams(titleParams);
        titleBlock.addView(text("Finance Pro Suite", 24, "#17351F", true));
        titleBlock.addView(text(
                "Five advanced money tools in one live workspace",
                11,
                "#617067",
                false
        ));
        header.addView(titleBlock);
        root.addView(header);

        txtPeriod = text("Loading current month…", 11, "#617067", false);
        setTopMargin(txtPeriod, 10);
        root.addView(txtPeriod);

        addHeading(root, "Smart Financial Dashboard 2.0",
                "Income, expense, saving, accounts, credit and loans at a glance");

        MaterialCardView hero = card("#EEF7FF", "#BED9EF", 20);
        LinearLayout heroContent = verticalPadding(14);
        LinearLayout row1 = horizontal();
        txtBalance = metric("Total Balance", "…", "#0F6CBD");
        txtSaving = metric("Monthly Saving", "…", "#107C41");
        row1.addView(txtBalance);
        row1.addView(txtSaving);
        heroContent.addView(row1);

        LinearLayout row2 = horizontal();
        setTopMargin(row2, 9);
        txtIncome = metric("Income", "…", "#107C41");
        txtExpense = metric("Expense", "…", "#C42B1C");
        row2.addView(txtIncome);
        row2.addView(txtExpense);
        heroContent.addView(row2);

        LinearLayout row3 = horizontal();
        setTopMargin(row3, 9);
        txtCreditAvailable = metric("Credit Available", "…", "#8764B8");
        txtLoanOutstanding = metric("Loan Outstanding", "…", "#D83B01");
        row3.addView(txtCreditAvailable);
        row3.addView(txtLoanOutstanding);
        heroContent.addView(row3);
        hero.addView(heroContent);
        root.addView(hero);

        addHeading(root, "AI Financial Insights",
                "Private offline analysis of trends, unusual spending and month-end direction");

        MaterialCardView aiCard = card("#FFF9EC", "#E9D7A8", 18);
        LinearLayout aiContent = verticalPadding(14);
        txtExpenseTrend = insightLine("Expense trend", "Calculating…");
        txtUnusualSpend = insightLine("Unusual spending", "Calculating…");
        txtProjection = insightLine("Month-end projection", "Calculating…");
        txtTopCategory = insightLine("Top category", "Calculating…");
        txtSavingSuggestion = insightLine("Saving suggestion", "Calculating…");
        aiContent.addView(txtExpenseTrend);
        aiContent.addView(txtUnusualSpend);
        aiContent.addView(txtProjection);
        aiContent.addView(txtTopCategory);
        aiContent.addView(txtSavingSuggestion);
        aiCard.addView(aiContent);
        root.addView(aiCard);

        MaterialButton assistant = button("Open Smart Financial Assistant", true);
        setTopMargin(assistant, 9);
        assistant.setOnClickListener(view -> open(FinanceAdvisorActivity.class));
        BubbleTouchAnimator.apply(assistant);
        root.addView(assistant);

        addHeading(root, "Advanced Analytics",
                "Monthly/yearly analysis, category trends and interactive cash-flow charts");

        MaterialCardView analyticsCard = card("#F4F0FF", "#D8C8F2", 18);
        LinearLayout analyticsContent = verticalPadding(13);
        TextView analyticsNote = text(
                "Use Analytics for comparisons and category intelligence, or Charts for interactive visual cash-flow review.",
                11,
                "#5D526B",
                false
        );
        analyticsNote.setLineSpacing(dp(2), 1f);
        analyticsContent.addView(analyticsNote);
        LinearLayout analyticsButtons = horizontal();
        setTopMargin(analyticsButtons, 10);
        MaterialButton openAnalytics = button("Advanced Analytics", false);
        MaterialButton openCharts = button("Interactive Charts", false);
        openAnalytics.setOnClickListener(view -> open(AnalyticsActivity.class));
        openCharts.setOnClickListener(view -> open(ChartsActivity.class));
        BubbleTouchAnimator.apply(openAnalytics);
        BubbleTouchAnimator.apply(openCharts);
        analyticsButtons.addView(openAnalytics);
        analyticsButtons.addView(openCharts);
        analyticsContent.addView(analyticsButtons);
        analyticsCard.addView(analyticsContent);
        root.addView(analyticsCard);

        addHeading(root, "Smart Budget System",
                "Category limits, remaining budget, overspending warning and projection");

        MaterialCardView budgetCard = card("#F1FAF3", "#B9DFC3", 18);
        LinearLayout budgetContent = verticalPadding(14);
        txtBudgetSummary = text("Loading budget health…", 13, "#17351F", true);
        budgetContent.addView(txtBudgetSummary);
        budgetProgress = progress("#107C41");
        setTopMargin(budgetProgress, 10);
        budgetContent.addView(budgetProgress);
        txtBudgetPrediction = text("Calculating month-end budget direction…", 11, "#617067", false);
        setTopMargin(txtBudgetPrediction, 8);
        txtBudgetPrediction.setLineSpacing(dp(2), 1f);
        budgetContent.addView(txtBudgetPrediction);
        MaterialButton openBudget = button("Open Smart Budget Planner", true);
        setTopMargin(openBudget, 10);
        openBudget.setOnClickListener(view -> open(BudgetActivity.class));
        BubbleTouchAnimator.apply(openBudget);
        budgetContent.addView(openBudget);
        budgetCard.addView(budgetContent);
        root.addView(budgetCard);

        addHeading(root, "Accounts & Credit Cards Pro",
                "Editable accounts, statement cycles, outstanding, available limit and due tracking");

        MaterialCardView accountCard = card("#FFF5F3", "#F0C8C0", 18);
        LinearLayout accountContent = verticalPadding(14);
        txtAccountSummary = text("Loading accounts…", 12, "#17351F", true);
        accountContent.addView(txtAccountSummary);
        txtCardSummary = text("Loading credit cards…", 11, "#617067", false);
        setTopMargin(txtCardSummary, 6);
        txtCardSummary.setLineSpacing(dp(2), 1f);
        accountContent.addView(txtCardSummary);
        creditProgress = progress("#8764B8");
        setTopMargin(creditProgress, 10);
        accountContent.addView(creditProgress);

        LinearLayout accountButtons = horizontal();
        setTopMargin(accountButtons, 10);
        MaterialButton accounts = button("Manage Accounts", false);
        MaterialButton cards = button("Credit Cards Pro", false);
        accounts.setOnClickListener(view -> open(AccountActivity.class));
        cards.setOnClickListener(view -> open(CreditCardActivity.class));
        BubbleTouchAnimator.apply(accounts);
        BubbleTouchAnimator.apply(cards);
        accountButtons.addView(accounts);
        accountButtons.addView(cards);
        accountContent.addView(accountButtons);

        MaterialButton loans = button("Open Loan Manager", true);
        setTopMargin(loans, 8);
        loans.setOnClickListener(view -> open(LoanActivity.class));
        BubbleTouchAnimator.apply(loans);
        accountContent.addView(loans);
        accountCard.addView(accountContent);
        root.addView(accountCard);

        TextView privacy = text(
                "Finance Pro calculations use only your existing local app data. No new finance database is created by this screen.",
                10,
                "#6A766E",
                false
        );
        privacy.setGravity(Gravity.CENTER);
        privacy.setLineSpacing(dp(2), 1f);
        setTopMargin(privacy, 16);
        root.addView(privacy);

        return scroll;
    }

    private void loadSnapshot() {
        final int version = ++requestVersion;
        txtPeriod.setText("Refreshing current financial intelligence…");

        new Thread(() -> {
            try {
                AppDatabase database = DatabaseClient
                        .getInstance(getApplicationContext())
                        .getAppDatabase();

                Snapshot snapshot = analyse(
                        database.transactionDao().getAllTransactions(),
                        database.accountDao().getAccountBalances(),
                        database.budgetDao().getAllBudgets(),
                        database.creditCardDao().getActiveCreditCards(),
                        database.loanDao().getActiveLoans()
                );

                runOnUiThread(() -> {
                    if (version != requestVersion || isFinishing() || isDestroyed()) {
                        return;
                    }
                    render(snapshot);
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    if (version != requestVersion || isFinishing() || isDestroyed()) {
                        return;
                    }
                    txtPeriod.setText("Unable to refresh finance intelligence. Reopen this page to retry.");
                });
            }
        }).start();
    }

    @NonNull
    private Snapshot analyse(
            List<Transaction> transactions,
            List<AccountBalance> balances,
            List<Budget> budgets,
            List<CreditCard> cards,
            List<Loan> loans
    ) {
        Snapshot data = new Snapshot();
        Calendar now = Calendar.getInstance();
        Calendar previousMonth = (Calendar) now.clone();
        previousMonth.add(Calendar.MONTH, -1);

        Map<String, Double> currentCategorySpend = new LinkedHashMap<>();
        Map<String, HistoryStat> historyByCategory = new HashMap<>();
        List<ExpensePoint> currentExpenses = new ArrayList<>();
        long historicalCutoff = now.getTimeInMillis() - 120L * 24L * 60L * 60L * 1000L;

        if (transactions != null) {
            for (Transaction transaction : transactions) {
                if (transaction == null) continue;
                Date date = parseDate(transaction.getDate());
                if (date == null) continue;

                Calendar when = Calendar.getInstance();
                when.setTime(date);
                double amount = Math.abs(transaction.getAmount());
                if (amount <= 0d || Double.isNaN(amount) || Double.isInfinite(amount)) continue;

                String type = safe(transaction.getType());
                boolean current = sameMonth(when, now);
                boolean previous = sameMonth(when, previousMonth);

                if ("INCOME".equalsIgnoreCase(type)) {
                    if (current) data.income += amount;
                    continue;
                }

                if (!"EXPENSE".equalsIgnoreCase(type)) continue;

                String category = safe(transaction.getCategory());
                if (category.isEmpty()) category = "Other Expense";

                if (current) {
                    data.expense += amount;
                    addCategory(currentCategorySpend, category, amount);
                    currentExpenses.add(new ExpensePoint(category, amount));
                } else if (previous) {
                    data.previousExpense += amount;
                }

                if (!current && date.getTime() >= historicalCutoff) {
                    String key = category.toLowerCase(Locale.ROOT);
                    HistoryStat stat = historyByCategory.get(key);
                    if (stat == null) {
                        stat = new HistoryStat();
                        historyByCategory.put(key, stat);
                    }
                    stat.total += amount;
                    stat.count++;
                }
            }
        }

        data.saving = data.income - data.expense;
        data.savingRate = data.income > 0d ? data.saving / data.income * 100d : 0d;

        int elapsedDays = Math.max(1, now.get(Calendar.DAY_OF_MONTH));
        int monthDays = now.getActualMaximum(Calendar.DAY_OF_MONTH);
        data.projectedExpense = data.expense / elapsedDays * monthDays;

        for (Map.Entry<String, Double> entry : currentCategorySpend.entrySet()) {
            if (entry.getValue() > data.topCategoryAmount) {
                data.topCategory = entry.getKey();
                data.topCategoryAmount = entry.getValue();
            }
        }

        for (ExpensePoint point : currentExpenses) {
            HistoryStat history = historyByCategory.get(point.category.toLowerCase(Locale.ROOT));
            if (history == null || history.count < 2) continue;
            double average = history.total / history.count;
            if (average > 0d && point.amount >= Math.max(1000d, average * 2.2d)) {
                data.unusualCount++;
                data.unusualAmount += point.amount;
            }
        }

        if (balances != null) {
            data.accountCount = balances.size();
            for (AccountBalance balance : balances) {
                if (balance == null) continue;
                data.totalBalance += balance.currentBalance;
                if (balance.name != null) {
                    data.balanceByAccount.put(
                            balance.name.trim().toLowerCase(Locale.ROOT),
                            balance.currentBalance
                    );
                }
            }
        }

        if (budgets != null) {
            for (Budget budget : budgets) {
                if (budget == null || !"Monthly".equalsIgnoreCase(safe(budget.getPeriod()))) continue;
                double limit = Math.max(0d, budget.getLimitAmount());
                if (limit <= 0d) continue;
                double spent = findCategoryAmount(currentCategorySpend, safe(budget.getCategory()));
                data.budgetCount++;
                data.budgetLimit += limit;
                data.budgetSpent += spent;
                if (spent > limit) data.overBudgetCategories++;
            }
        }
        data.budgetRemaining = data.budgetLimit - data.budgetSpent;
        data.projectedBudgetSpend = data.budgetSpent / elapsedDays * monthDays;

        if (cards != null) {
            data.cardCount = cards.size();
            int nearestDue = Integer.MAX_VALUE;
            for (CreditCard card : cards) {
                if (card == null) continue;
                double limit = Math.max(0d, card.getCreditLimit());
                double accountBalance = data.balanceByAccount.getOrDefault(
                        safe(card.getAccountName()).toLowerCase(Locale.ROOT),
                        0d
                );
                double used = Math.max(0d, -accountBalance);
                data.creditLimit += limit;
                data.creditOutstanding += used;
                data.creditAvailable += Math.max(0d, limit - used);
                int dueDays = daysUntilDue(card.getDueDay(), now);
                if (dueDays < 9999) nearestDue = Math.min(nearestDue, dueDays);
            }
            data.nearestCardDueDays = nearestDue == Integer.MAX_VALUE ? -1 : nearestDue;
        }

        if (loans != null) {
            data.loanCount = loans.size();
            for (Loan loan : loans) {
                if (loan == null) continue;
                data.loanOutstanding += Math.max(0d, loan.getOutstandingAmount());
                data.loanEmiTotal += Math.max(0d, loan.getEmiAmount());
            }
        }

        return data;
    }

    private void render(@NonNull Snapshot data) {
        txtPeriod.setText(new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH)
                .format(new Date()) + " • live local analysis");

        setMetric(txtBalance, "Total Balance", signedMoney(data.totalBalance), data.totalBalance >= 0d);
        setMetric(txtIncome, "Income", money(data.income), true);
        setMetric(txtExpense, "Expense", money(data.expense), false);
        setMetric(txtSaving, "Monthly Saving", signedMoney(data.saving), data.saving >= 0d);
        setMetric(txtCreditAvailable, "Credit Available", money(data.creditAvailable), true);
        setMetric(txtLoanOutstanding, "Loan Outstanding", money(data.loanOutstanding), false);

        if (data.previousExpense > 0d) {
            double change = (data.expense - data.previousExpense) / data.previousExpense * 100d;
            String direction = change >= 0d ? "higher" : "lower";
            txtExpenseTrend.setText("Expense trend\n" + oneDecimal(Math.abs(change))
                    + "% " + direction + " than previous month");
            txtExpenseTrend.setTextColor(Color.parseColor(change > 10d ? "#C42B1C" : "#475467"));
        } else {
            txtExpenseTrend.setText("Expense trend\nPrevious-month expense is not available for a reliable comparison yet.");
            txtExpenseTrend.setTextColor(Color.parseColor("#475467"));
        }

        if (data.unusualCount == 0) {
            txtUnusualSpend.setText("Unusual spending\nNo strong high-value outlier found against recent category history.");
            txtUnusualSpend.setTextColor(Color.parseColor("#107C41"));
        } else {
            txtUnusualSpend.setText("Unusual spending\n" + data.unusualCount
                    + " entr" + (data.unusualCount == 1 ? "y" : "ies")
                    + " • " + money(data.unusualAmount) + " needs review");
            txtUnusualSpend.setTextColor(Color.parseColor("#C42B1C"));
        }

        txtProjection.setText("Month-end projection\nCurrent pace suggests about "
                + money(data.projectedExpense) + " total expense.");

        txtTopCategory.setText("Top category\n"
                + (data.topCategory.isEmpty()
                ? "No expense category recorded this month."
                : data.topCategory + " • " + money(data.topCategoryAmount)));

        txtSavingSuggestion.setText("Saving suggestion\n" + savingSuggestion(data));
        txtSavingSuggestion.setTextColor(Color.parseColor(data.saving >= 0d ? "#107C41" : "#C42B1C"));

        if (data.budgetCount == 0 || data.budgetLimit <= 0d) {
            txtBudgetSummary.setText("No monthly category budget is active.");
            txtBudgetPrediction.setText("Open Smart Budget Planner to create category limits and receive overspending guidance.");
            budgetProgress.setProgress(0);
        } else {
            double usage = data.budgetSpent / data.budgetLimit * 100d;
            txtBudgetSummary.setText(money(data.budgetSpent) + " used of " + money(data.budgetLimit)
                    + " • " + signedMoney(data.budgetRemaining) + " remaining");
            budgetProgress.setProgress((int) Math.min(100d, Math.max(0d, usage)));
            budgetProgress.setProgressTintList(ColorStateList.valueOf(
                    Color.parseColor(usage >= 100d ? "#C42B1C" : usage >= 80d ? "#D83B01" : "#107C41")
            ));

            if (data.overBudgetCategories > 0) {
                txtBudgetPrediction.setText(data.overBudgetCategories + " categor"
                        + (data.overBudgetCategories == 1 ? "y is" : "ies are")
                        + " already over limit. Projected budgeted-category spend is "
                        + money(data.projectedBudgetSpend) + ".");
                txtBudgetPrediction.setTextColor(Color.parseColor("#C42B1C"));
            } else if (data.projectedBudgetSpend > data.budgetLimit) {
                txtBudgetPrediction.setText("Overspending warning: at the current pace, budgeted categories may reach "
                        + money(data.projectedBudgetSpend) + " by month end.");
                txtBudgetPrediction.setTextColor(Color.parseColor("#D83B01"));
            } else {
                txtBudgetPrediction.setText("On track: projected budgeted-category spend is "
                        + money(data.projectedBudgetSpend) + " against " + money(data.budgetLimit) + ".");
                txtBudgetPrediction.setTextColor(Color.parseColor("#107C41"));
            }
        }

        txtAccountSummary.setText(data.accountCount + " active account"
                + (data.accountCount == 1 ? "" : "s") + " • combined balance "
                + signedMoney(data.totalBalance));

        if (data.cardCount == 0) {
            txtCardSummary.setText("No active credit card. Add one to enable statement-cycle, utilization and due-date intelligence.");
            creditProgress.setProgress(0);
        } else {
            double utilization = data.creditLimit <= 0d
                    ? 0d : data.creditOutstanding / data.creditLimit * 100d;
            String due = data.nearestCardDueDays < 0
                    ? "due date unavailable"
                    : data.nearestCardDueDays == 0
                    ? "nearest payment due today"
                    : "nearest payment due in " + data.nearestCardDueDays + " days";
            txtCardSummary.setText(data.cardCount + " active card"
                    + (data.cardCount == 1 ? "" : "s") + " • "
                    + money(data.creditOutstanding) + " outstanding • "
                    + money(data.creditAvailable) + " available • " + due);
            creditProgress.setProgress((int) Math.min(100d, Math.max(0d, utilization)));
            creditProgress.setProgressTintList(ColorStateList.valueOf(
                    Color.parseColor(utilization >= 70d ? "#C42B1C" : utilization >= 40d ? "#D83B01" : "#8764B8")
            ));
        }
    }

    @NonNull
    private String savingSuggestion(@NonNull Snapshot data) {
        if (data.income <= 0d) {
            return "Add this month’s income to unlock a meaningful saving-rate target.";
        }
        if (data.saving < 0d) {
            return "Reduce flexible spending by at least " + money(Math.abs(data.saving))
                    + " to return to a positive monthly position.";
        }
        if (data.savingRate < 10d) {
            double gap = Math.max(0d, data.income * 0.10d - data.saving);
            return "Current saving rate is " + oneDecimal(data.savingRate)
                    + "%. Preserving about " + money(gap) + " more would reach a 10% buffer.";
        }
        if (data.savingRate < 20d) {
            return "Current saving rate is " + oneDecimal(data.savingRate)
                    + "%. A 20% target for this income is " + money(data.income * 0.20d) + ".";
        }
        return "Current saving rate is " + oneDecimal(data.savingRate)
                + "%. Keep the buffer positive while reviewing any unusual or over-budget spending.";
    }

    private void open(@NonNull Class<?> target) {
        startActivity(new Intent(this, target));
    }

    private boolean sameMonth(@NonNull Calendar first, @NonNull Calendar second) {
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR)
                && first.get(Calendar.MONTH) == second.get(Calendar.MONTH);
    }

    private int daysUntilDue(int rawDueDay, @NonNull Calendar now) {
        if (rawDueDay <= 0) return 9999;
        Calendar due = (Calendar) now.clone();
        due.set(Calendar.HOUR_OF_DAY, 0);
        due.set(Calendar.MINUTE, 0);
        due.set(Calendar.SECOND, 0);
        due.set(Calendar.MILLISECOND, 0);
        due.set(Calendar.DAY_OF_MONTH, Math.min(rawDueDay, due.getActualMaximum(Calendar.DAY_OF_MONTH)));
        Calendar today = (Calendar) now.clone();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        if (due.before(today)) {
            due.add(Calendar.MONTH, 1);
            due.set(Calendar.DAY_OF_MONTH, Math.min(rawDueDay, due.getActualMaximum(Calendar.DAY_OF_MONTH)));
        }
        long diff = due.getTimeInMillis() - today.getTimeInMillis();
        return (int) Math.max(0L, Math.round(diff / 86400000d));
    }

    private Date parseDate(String value) {
        String clean = safe(value);
        if (clean.isEmpty()) return null;
        for (String pattern : DATE_PATTERNS) {
            SimpleDateFormat formatter = new SimpleDateFormat(pattern, Locale.US);
            formatter.setLenient(false);
            ParsePosition position = new ParsePosition(0);
            Date parsed = formatter.parse(clean, position);
            if (parsed != null && position.getIndex() == clean.length()) return parsed;
        }
        return null;
    }

    private void addCategory(Map<String, Double> values, String category, double amount) {
        String matching = null;
        for (String key : values.keySet()) {
            if (key.equalsIgnoreCase(category)) {
                matching = key;
                break;
            }
        }
        String target = matching == null ? category : matching;
        values.put(target, values.getOrDefault(target, 0d) + amount);
    }

    private double findCategoryAmount(Map<String, Double> values, String category) {
        for (Map.Entry<String, Double> entry : values.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(category)) return entry.getValue();
        }
        return 0d;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String money(double amount) {
        NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        format.setMaximumFractionDigits(0);
        return format.format(Math.abs(amount));
    }

    private String signedMoney(double amount) {
        return (amount >= 0d ? "+" : "−") + money(amount);
    }

    private String oneDecimal(double value) {
        return String.format(Locale.ENGLISH, "%.1f", value);
    }

    private void addHeading(LinearLayout root, String title, String subtitle) {
        TextView heading = text(title, 18, "#17351F", true);
        setTopMargin(heading, 20);
        root.addView(heading);
        TextView sub = text(subtitle, 10, "#6A766E", false);
        setTopMargin(sub, 2);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) sub.getLayoutParams();
        params.bottomMargin = dp(9);
        sub.setLayoutParams(params);
        root.addView(sub);
    }

    private TextView metric(String label, String value, String accent) {
        TextView view = text(label + "\n" + value, 11, accent, true);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(66));
        view.setPadding(dp(6), dp(9), dp(6), dp(9));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        view.setLayoutParams(params);
        return view;
    }

    private void setMetric(TextView view, String label, String value, boolean positive) {
        view.setText(label + "\n" + value);
        view.setTextColor(Color.parseColor(positive ? "#107C41" : "#C42B1C"));
    }

    private TextView insightLine(String label, String initial) {
        TextView view = text(label + "\n" + initial, 11, "#475467", false);
        view.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(10));
        view.setLayoutParams(params);
        return view;
    }

    private ProgressBar progress(String tint) {
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(0);
        bar.setProgressTintList(ColorStateList.valueOf(Color.parseColor(tint)));
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(8)
        ));
        return bar;
    }

    private MaterialCardView card(String background, String stroke, int radiusDp) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(Color.parseColor(background));
        card.setStrokeColor(Color.parseColor(stroke));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(radiusDp));
        card.setCardElevation(0f);
        card.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return card;
    }

    private MaterialButton button(String label, boolean strong) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setTextAllCaps(false);
        button.setTextSize(11);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(Color.parseColor(strong ? "#FFFFFF" : "#17351F"));
        button.setBackgroundTintList(ColorStateList.valueOf(
                Color.parseColor(strong ? "#0F6CBD" : "#FFFFFF")
        ));
        button.setStrokeColor(ColorStateList.valueOf(Color.parseColor(strong ? "#0F6CBD" : "#C9D7CD")));
        button.setStrokeWidth(dp(1));
        button.setCornerRadius(dp(14));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                strong ? ViewGroup.LayoutParams.MATCH_PARENT : 0,
                dp(46),
                strong ? 0f : 1f
        );
        if (!strong) params.setMargins(dp(3), 0, dp(3), 0);
        button.setLayoutParams(params);
        return button;
    }

    private TextView text(String value, int size, String color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.parseColor(color));
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return view;
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout verticalPadding(int paddingDp) {
        LinearLayout layout = vertical();
        layout.setPadding(dp(paddingDp), dp(paddingDp), dp(paddingDp), dp(paddingDp));
        return layout;
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setBaselineAligned(false);
        layout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return layout;
    }

    private void setTopMargin(View view, int marginDp) {
        ViewGroup.LayoutParams raw = view.getLayoutParams();
        LinearLayout.LayoutParams params;
        if (raw instanceof LinearLayout.LayoutParams) {
            params = (LinearLayout.LayoutParams) raw;
        } else {
            params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
        params.topMargin = dp(marginDp);
        view.setLayoutParams(params);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class HistoryStat {
        double total;
        int count;
    }

    private static final class ExpensePoint {
        final String category;
        final double amount;

        ExpensePoint(String category, double amount) {
            this.category = category;
            this.amount = amount;
        }
    }

    private static final class Snapshot {
        double income;
        double expense;
        double previousExpense;
        double saving;
        double savingRate;
        double projectedExpense;
        String topCategory = "";
        double topCategoryAmount;
        int unusualCount;
        double unusualAmount;

        int accountCount;
        double totalBalance;
        final Map<String, Double> balanceByAccount = new HashMap<>();

        int budgetCount;
        double budgetLimit;
        double budgetSpent;
        double budgetRemaining;
        double projectedBudgetSpend;
        int overBudgetCategories;

        int cardCount;
        double creditLimit;
        double creditOutstanding;
        double creditAvailable;
        int nearestCardDueDays = -1;

        int loanCount;
        double loanOutstanding;
        double loanEmiTotal;
    }
}
