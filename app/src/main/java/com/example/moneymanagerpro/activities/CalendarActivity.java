package com.example.moneymanagerpro.activities;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Transaction;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CalendarActivity extends AppCompatActivity {

    private TextView txtMonthTitle;
    private TextView txtMonthIncome;
    private TextView txtMonthExpense;
    private TextView txtMonthNet;
    private TextView txtNoCalendarData;
    private GridLayout calendarGrid;

    private Calendar selectedMonth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);

        txtMonthTitle = findViewById(R.id.txtMonthTitle);
        txtMonthIncome = findViewById(R.id.txtMonthIncome);
        txtMonthExpense = findViewById(R.id.txtMonthExpense);
        txtMonthNet = findViewById(R.id.txtMonthNet);
        txtNoCalendarData = findViewById(R.id.txtNoCalendarData);
        calendarGrid = findViewById(R.id.calendarGrid);

        View btnPreviousMonth = findViewById(R.id.btnPreviousMonth);
        View btnNextMonth = findViewById(R.id.btnNextMonth);

        selectedMonth = Calendar.getInstance();
        selectedMonth.set(Calendar.DAY_OF_MONTH, 1);

        BubbleTouchAnimator.apply(btnPreviousMonth);
        BubbleTouchAnimator.apply(btnNextMonth);

        btnPreviousMonth.setOnClickListener(v -> {
            selectedMonth.add(Calendar.MONTH, -1);
            loadCalendarData();
        });

        btnNextMonth.setOnClickListener(v -> {
            selectedMonth.add(Calendar.MONTH, 1);
            loadCalendarData();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCalendarData();
    }

    private void loadCalendarData() {
        Calendar monthToLoad = (Calendar) selectedMonth.clone();

        new Thread(() -> {
            List<Transaction> transactions = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .transactionDao()
                    .getAllTransactions();

            Map<Integer, DailyTotal> dailyTotals = new HashMap<>();

            double monthIncome = 0;
            double monthExpense = 0;

            for (Transaction transaction : transactions) {
                Date transactionDate = parseTransactionDate(transaction.getDate());

                if (transactionDate == null) {
                    continue;
                }

                Calendar transactionCalendar = Calendar.getInstance();
                transactionCalendar.setTime(transactionDate);

                boolean isSameMonth =
                        transactionCalendar.get(Calendar.YEAR) == monthToLoad.get(Calendar.YEAR)
                                && transactionCalendar.get(Calendar.MONTH) == monthToLoad.get(Calendar.MONTH);

                if (!isSameMonth) {
                    continue;
                }

                int day = transactionCalendar.get(Calendar.DAY_OF_MONTH);

                if (!dailyTotals.containsKey(day)) {
                    dailyTotals.put(day, new DailyTotal());
                }

                DailyTotal total = dailyTotals.get(day);

                if ("INCOME".equals(transaction.getType())) {
                    total.income += transaction.getAmount();
                    monthIncome += transaction.getAmount();
                } else if ("EXPENSE".equals(transaction.getType())) {
                    total.expense += transaction.getAmount();
                    monthExpense += transaction.getAmount();
                }
            }

            double finalIncome = monthIncome;
            double finalExpense = monthExpense;

            runOnUiThread(() ->
                    showCalendar(monthToLoad, dailyTotals, finalIncome, finalExpense)
            );
        }).start();
    }

    private void showCalendar(
            Calendar month,
            Map<Integer, DailyTotal> dailyTotals,
            double monthIncome,
            double monthExpense
    ) {
        SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH);
        txtMonthTitle.setText(monthFormat.format(month.getTime()));

        txtMonthIncome.setText(formatAmount(monthIncome));
        txtMonthExpense.setText(formatAmount(monthExpense));

        double netAmount = monthIncome - monthExpense;
        txtMonthNet.setText(formatAmount(netAmount));

        if (netAmount >= 0) {
            txtMonthNet.setTextColor(Color.parseColor("#2E7D32"));
        } else {
            txtMonthNet.setTextColor(Color.parseColor("#D32F2F"));
        }

        calendarGrid.removeAllViews();

        boolean hasData = monthIncome > 0 || monthExpense > 0;
        txtNoCalendarData.setVisibility(hasData ? View.GONE : View.VISIBLE);

        Calendar firstDay = (Calendar) month.clone();
        firstDay.set(Calendar.DAY_OF_MONTH, 1);

        int firstDayPosition = firstDay.get(Calendar.DAY_OF_WEEK) - 1;
        int totalDays = firstDay.getActualMaximum(Calendar.DAY_OF_MONTH);

        for (int i = 0; i < firstDayPosition; i++) {
            addEmptyCell();
        }

        for (int day = 1; day <= totalDays; day++) {
            DailyTotal total = dailyTotals.get(day);

            if (total == null) {
                total = new DailyTotal();
            }

            addDayCell(day, total, month);
        }

        int totalCells = firstDayPosition + totalDays;
        int remainingCells = totalCells % 7 == 0 ? 0 : 7 - (totalCells % 7);

        for (int i = 0; i < remainingCells; i++) {
            addEmptyCell();
        }
    }

    private void addEmptyCell() {
        View emptyView = new View(this);

        GridLayout.LayoutParams params = createGridParams();
        emptyView.setLayoutParams(params);

        calendarGrid.addView(emptyView);
    }

    private void addDayCell(int day, DailyTotal total, Calendar month) {
        LinearLayout dayCell = new LinearLayout(this);
        dayCell.setOrientation(LinearLayout.VERTICAL);
        dayCell.setGravity(Gravity.CENTER);
        dayCell.setPadding(dpToPx(3), dpToPx(4), dpToPx(3), dpToPx(4));
        dayCell.setLayoutParams(createGridParams());

        double net = total.income - total.expense;

        boolean hasIncome = total.income > 0;
        boolean hasExpense = total.expense > 0;
        boolean isToday = isToday(day, month);

        int backgroundColor = Color.WHITE;
        int amountColor = Color.parseColor("#64748B");

        if (hasIncome && hasExpense) {
            backgroundColor = Color.parseColor("#FFF4D9");
            amountColor = Color.parseColor("#B26A00");
        } else if (hasIncome) {
            backgroundColor = Color.parseColor("#E8F5E9");
            amountColor = Color.parseColor("#2E7D32");
        } else if (hasExpense) {
            backgroundColor = Color.parseColor("#FFEBEE");
            amountColor = Color.parseColor("#D32F2F");
        }

        GradientDrawable background = new GradientDrawable();
        background.setColor(backgroundColor);
        background.setCornerRadius(dpToPx(14));

        if (isToday) {
            background.setStroke(dpToPx(2), Color.parseColor("#3949AB"));
        }

        dayCell.setBackground(background);

        TextView txtDay = new TextView(this);
        txtDay.setText(String.valueOf(day));
        txtDay.setTextColor(Color.parseColor("#172033"));
        txtDay.setTextSize(14);
        txtDay.setTypeface(Typeface.DEFAULT_BOLD);
        txtDay.setGravity(Gravity.CENTER);

        TextView txtAmount = new TextView(this);
        txtAmount.setTextSize(10);
        txtAmount.setTypeface(Typeface.DEFAULT_BOLD);
        txtAmount.setGravity(Gravity.CENTER);
        txtAmount.setTextColor(amountColor);

        if (hasIncome && hasExpense) {
            txtAmount.setText("↕ " + formatCompact(net));
        } else if (hasIncome) {
            txtAmount.setText("↑ " + formatCompact(total.income));
        } else if (hasExpense) {
            txtAmount.setText("↓ " + formatCompact(total.expense));
        } else {
            txtAmount.setText("");
        }

        dayCell.addView(txtDay);
        dayCell.addView(txtAmount);

        BubbleTouchAnimator.apply(dayCell);

        dayCell.setOnClickListener(v -> {
            if (hasIncome || hasExpense) {
                Toast.makeText(
                        CalendarActivity.this,
                        day + " " + getMonthShortName(month)
                                + "\nIncome: " + formatAmount(total.income)
                                + "\nExpense: " + formatAmount(total.expense)
                                + "\nNet: " + formatAmount(net),
                        Toast.LENGTH_LONG
                ).show();
            }
        });

        calendarGrid.addView(dayCell);
    }

    private GridLayout.LayoutParams createGridParams() {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();

        params.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);

        params.width = 0;
        params.height = dpToPx(70);

        params.setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2));

        return params;
    }

    private boolean isToday(int day, Calendar month) {
        Calendar today = Calendar.getInstance();

        return today.get(Calendar.YEAR) == month.get(Calendar.YEAR)
                && today.get(Calendar.MONTH) == month.get(Calendar.MONTH)
                && today.get(Calendar.DAY_OF_MONTH) == day;
    }

    private String getMonthShortName(Calendar month) {
        return new SimpleDateFormat("MMM", Locale.ENGLISH).format(month.getTime());
    }

    private Date parseTransactionDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        try {
            return new SimpleDateFormat(
                    "yyyy-MM-dd HH:mm",
                    Locale.US
            ).parse(value);
        } catch (Exception ignored) {
        }

        try {
            return new SimpleDateFormat(
                    "yyyy-MM-dd",
                    Locale.US
            ).parse(value);
        } catch (Exception ignored) {
        }

        return null;
    }

    private String formatAmount(double amount) {
        NumberFormat numberFormat = NumberFormat.getCurrencyInstance(
                new Locale("en", "IN")
        );

        return numberFormat.format(amount);
    }

    private String formatCompact(double amount) {
        double absoluteAmount = Math.abs(amount);
        DecimalFormat format = new DecimalFormat("0.#");

        if (absoluteAmount >= 100000) {
            return "₹" + format.format(amount / 100000) + "L";
        }

        if (absoluteAmount >= 1000) {
            return "₹" + format.format(amount / 1000) + "K";
        }

        return "₹" + format.format(amount);
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density);
    }

    private static class DailyTotal {
        double income;
        double expense;
    }
}