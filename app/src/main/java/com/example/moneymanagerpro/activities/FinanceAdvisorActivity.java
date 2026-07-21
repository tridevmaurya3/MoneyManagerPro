package com.example.moneymanagerpro.activities;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Transaction;
import com.google.android.material.card.MaterialCardView;

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FinanceAdvisorActivity extends AppCompatActivity {

    private TextView txtScore;
    private TextView txtScoreLabel;
    private TextView txtIncome;
    private TextView txtExpense;
    private TextView txtSaving;
    private TextView txtOverview;
    private LinearLayout recommendationContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finance_advisor);

        txtScore = findViewById(R.id.txtScore);
        txtScoreLabel = findViewById(R.id.txtScoreLabel);
        txtIncome = findViewById(R.id.txtAdvisorIncome);
        txtExpense = findViewById(R.id.txtAdvisorExpense);
        txtSaving = findViewById(R.id.txtAdvisorSaving);
        txtOverview = findViewById(R.id.txtAdvisorOverview);
        recommendationContainer = findViewById(R.id.recommendationContainer);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAdvice();
    }

    private void loadAdvice() {
        new Thread(() -> {
            List<Transaction> transactions = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .transactionDao()
                    .getAllTransactions();

            AdvisorData advisorData = calculateAdvisorData(transactions);

            runOnUiThread(() -> showAdvisorData(advisorData));
        }).start();
    }

    private AdvisorData calculateAdvisorData(List<Transaction> transactions) {
        AdvisorData data = new AdvisorData();

        Calendar today = Calendar.getInstance();
        int currentYear = today.get(Calendar.YEAR);
        int currentMonth = today.get(Calendar.MONTH);

        Calendar previousMonth = (Calendar) today.clone();
        previousMonth.add(Calendar.MONTH, -1);

        int previousYear = previousMonth.get(Calendar.YEAR);
        int previousMonthNumber = previousMonth.get(Calendar.MONTH);

        for (Transaction transaction : transactions) {
            Date date = parseDate(transaction.getDate());

            if (date == null) {
                continue;
            }

            Calendar transactionCalendar = Calendar.getInstance();
            transactionCalendar.setTime(date);

            String type = transaction.getType();
            double amount = transaction.getAmount();

            boolean isCurrentMonth =
                    transactionCalendar.get(Calendar.YEAR) == currentYear
                            && transactionCalendar.get(Calendar.MONTH) == currentMonth;

            boolean isPreviousMonth =
                    transactionCalendar.get(Calendar.YEAR) == previousYear
                            && transactionCalendar.get(Calendar.MONTH) == previousMonthNumber;

            if (isCurrentMonth) {
                if ("INCOME".equalsIgnoreCase(type)) {
                    data.currentIncome += amount;
                    data.currentEntries++;
                }

                if ("EXPENSE".equalsIgnoreCase(type)) {
                    data.currentExpense += amount;
                    data.currentEntries++;

                    String category = transaction.getCategory();

                    if (category == null || category.trim().isEmpty()) {
                        category = "Other";
                    }

                    double oldValue = data.categoryExpenses.containsKey(category)
                            ? data.categoryExpenses.get(category)
                            : 0;

                    data.categoryExpenses.put(category, oldValue + amount);
                }
            }

            if (isPreviousMonth && "EXPENSE".equalsIgnoreCase(type)) {
                data.previousExpense += amount;
            }
        }

        data.saving = data.currentIncome - data.currentExpense;
        data.score = calculateScore(data);

        return data;
    }

    private int calculateScore(AdvisorData data) {
        if (data.currentEntries == 0) {
            return 0;
        }

        int score = 100;

        if (data.currentIncome <= 0) {
            score -= 30;
        } else {
            double savingsRate = (data.saving / data.currentIncome) * 100;

            if (data.currentExpense > data.currentIncome) {
                score -= 35;
            } else if (savingsRate < 10) {
                score -= 20;
            } else if (savingsRate < 25) {
                score -= 8;
            }
        }

        double largestCategoryAmount = getLargestCategoryAmount(data.categoryExpenses);

        if (data.currentExpense > 0
                && (largestCategoryAmount / data.currentExpense) * 100 >= 50) {
            score -= 12;
        }

        if (data.previousExpense > 0
                && data.currentExpense > data.previousExpense * 1.20) {
            score -= 10;
        }

        return Math.max(score, 0);
    }

    private void showAdvisorData(AdvisorData data) {
        txtScore.setText(String.valueOf(data.score));
        txtScoreLabel.setText(getScoreLabel(data.score));

        txtIncome.setText(formatMoney(data.currentIncome));
        txtExpense.setText(formatMoney(data.currentExpense));
        txtSaving.setText(formatMoney(data.saving));

        txtOverview.setText(
                "This month: " + formatMoney(data.currentIncome)
                        + " income and " + formatMoney(data.currentExpense)
                        + " expense recorded."
        );

        recommendationContainer.removeAllViews();

        List<AdviceItem> adviceItems = buildAdvice(data);

        for (AdviceItem item : adviceItems) {
            addAdviceCard(item);
        }
    }

    private List<AdviceItem> buildAdvice(AdvisorData data) {
        List<AdviceItem> items = new ArrayList<>();

        if (data.currentEntries == 0) {
            items.add(new AdviceItem(
                    "Start tracking today",
                    "Add your income and expenses to receive personal financial insights.",
                    "#EEF2FF",
                    "#3730A3"
            ));

            return items;
        }

        if (data.currentIncome <= 0) {
            items.add(new AdviceItem(
                    "Income is missing",
                    "Add this month's income for an accurate savings analysis.",
                    "#FFF7ED",
                    "#9A3412"
            ));
        } else if (data.currentExpense > data.currentIncome) {
            items.add(new AdviceItem(
                    "Spending is above income",
                    "Your expense is " + formatMoney(data.currentExpense - data.currentIncome)
                            + " higher than your income. Review non-essential spending.",
                    "#FEF2F2",
                    "#991B1B"
            ));
        } else {
            double savingPercent = (data.saving / data.currentIncome) * 100;

            items.add(new AdviceItem(
                    "Current savings rate",
                    "You have saved " + String.format(Locale.US, "%.1f", savingPercent)
                            + "% of this month's income.",
                    "#ECFDF5",
                    "#065F46"
            ));
        }

        String topCategory = getLargestCategoryName(data.categoryExpenses);
        double topCategoryAmount = getLargestCategoryAmount(data.categoryExpenses);

        if (!topCategory.isEmpty() && data.currentExpense > 0) {
            double categoryPercent = (topCategoryAmount / data.currentExpense) * 100;

            items.add(new AdviceItem(
                    "Top spending category",
                    topCategory + " uses " + String.format(Locale.US, "%.1f", categoryPercent)
                            + "% of your monthly expenses: "
                            + formatMoney(topCategoryAmount) + ".",
                    "#F5F3FF",
                    "#5B21B6"
            ));
        }

        Calendar today = Calendar.getInstance();
        int daysPassed = Math.max(today.get(Calendar.DAY_OF_MONTH), 1);
        double dailyAverage = data.currentExpense / daysPassed;

        items.add(new AdviceItem(
                "Daily spending average",
                "Your average daily expense this month is "
                        + formatMoney(dailyAverage) + ".",
                "#EFF6FF",
                "#1D4ED8"
        ));

        if (data.previousExpense > 0) {
            double difference = data.currentExpense - data.previousExpense;
            double percentage = Math.abs(difference / data.previousExpense) * 100;

            if (difference > 0) {
                items.add(new AdviceItem(
                        "Expense trend increased",
                        "Your expense is " + String.format(Locale.US, "%.1f", percentage)
                                + "% higher than last month.",
                        "#FFF7ED",
                        "#9A3412"
                ));
            } else {
                items.add(new AdviceItem(
                        "Expense trend improved",
                        "Your expense is " + String.format(Locale.US, "%.1f", percentage)
                                + "% lower than last month.",
                        "#ECFDF5",
                        "#065F46"
                ));
            }
        }

        return items;
    }

    private void addAdviceCard(AdviceItem item) {
        MaterialCardView cardView = new MaterialCardView(this);
        cardView.setCardBackgroundColor(Color.parseColor(item.backgroundColor));
        cardView.setRadius(dp(18));
        cardView.setCardElevation(dp(2));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );

        cardParams.setMargins(0, 0, 0, dp(10));
        cardView.setLayoutParams(cardParams);

        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(dp(16), dp(14), dp(16), dp(14));

        TextView title = new TextView(this);
        title.setText(item.title);
        title.setTextColor(Color.parseColor(item.textColor));
        title.setTextSize(16);
        title.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView description = new TextView(this);
        description.setText(item.description);
        description.setTextColor(Color.parseColor(item.textColor));
        description.setTextSize(13);
        description.setPadding(0, dp(5), 0, 0);

        contentLayout.addView(title);
        contentLayout.addView(description);

        cardView.addView(contentLayout);
        recommendationContainer.addView(cardView);
    }

    private String getScoreLabel(int score) {
        if (score >= 80) {
            return "Excellent money control";
        }

        if (score >= 60) {
            return "Good, with room to improve";
        }

        if (score >= 40) {
            return "Review your spending habits";
        }

        if (score > 0) {
            return "Action needed this month";
        }

        return "Add entries to calculate score";
    }

    private String getLargestCategoryName(LinkedHashMap<String, Double> categoryExpenses) {
        String largestCategory = "";
        double largestAmount = 0;

        for (Map.Entry<String, Double> entry : categoryExpenses.entrySet()) {
            if (entry.getValue() > largestAmount) {
                largestAmount = entry.getValue();
                largestCategory = entry.getKey();
            }
        }

        return largestCategory;
    }

    private double getLargestCategoryAmount(LinkedHashMap<String, Double> categoryExpenses) {
        double largestAmount = 0;

        for (double amount : categoryExpenses.values()) {
            largestAmount = Math.max(largestAmount, amount);
        }

        return largestAmount;
    }

    private Date parseDate(String dateText) {
        if (dateText == null || dateText.trim().isEmpty()) {
            return null;
        }

        String[] patterns = {
                "dd MMMM yyyy",
                "dd MMM yyyy",
                "yyyy-MM-dd",
                "dd/MM/yyyy"
        };

        for (String pattern : patterns) {
            try {
                SimpleDateFormat formatter = new SimpleDateFormat(pattern, Locale.ENGLISH);
                formatter.setLenient(false);
                return formatter.parse(dateText.trim());
            } catch (ParseException ignored) {
            }
        }

        return null;
    }

    private String formatMoney(double amount) {
        NumberFormat formatter = NumberFormat.getNumberInstance(new Locale("en", "IN"));
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);

        return "₹" + formatter.format(amount);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private static class AdvisorData {
        double currentIncome;
        double currentExpense;
        double previousExpense;
        double saving;
        int currentEntries;
        int score;

        LinkedHashMap<String, Double> categoryExpenses = new LinkedHashMap<>();
    }

    private static class AdviceItem {
        String title;
        String description;
        String backgroundColor;
        String textColor;

        AdviceItem(
                String title,
                String description,
                String backgroundColor,
                String textColor
        ) {
            this.title = title;
            this.description = description;
            this.backgroundColor = backgroundColor;
            this.textColor = textColor;
        }
    }
}