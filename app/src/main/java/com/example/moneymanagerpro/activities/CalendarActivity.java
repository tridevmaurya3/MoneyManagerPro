package com.example.moneymanagerpro.activities;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Transaction;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

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

    private MaterialButton btnPreviousMonth;
    private MaterialButton btnNextMonth;

    private GridLayout calendarGrid;

    private Calendar selectedMonth;

    private int calendarRequestVersion = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);

        bindViews();
        prepareCalendar();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCalendarData();
    }

    private void bindViews() {
        txtMonthTitle =
                findViewById(R.id.txtMonthTitle);

        txtMonthIncome =
                findViewById(R.id.txtMonthIncome);

        txtMonthExpense =
                findViewById(R.id.txtMonthExpense);

        txtMonthNet =
                findViewById(R.id.txtMonthNet);

        txtNoCalendarData =
                findViewById(R.id.txtNoCalendarData);

        btnPreviousMonth =
                findViewById(R.id.btnPreviousMonth);

        btnNextMonth =
                findViewById(R.id.btnNextMonth);

        calendarGrid =
                findViewById(R.id.calendarGrid);
    }

    private void prepareCalendar() {
        selectedMonth =
                Calendar.getInstance();

        selectedMonth.set(
                Calendar.DAY_OF_MONTH,
                1
        );

        clearTime(selectedMonth);

        btnPreviousMonth.setOnClickListener(view -> {
            selectedMonth.add(
                    Calendar.MONTH,
                    -1
            );

            loadCalendarData();
        });

        btnNextMonth.setOnClickListener(view -> {
            selectedMonth.add(
                    Calendar.MONTH,
                    1
            );

            loadCalendarData();
        });

        BubbleTouchAnimator.apply(
                btnPreviousMonth
        );

        BubbleTouchAnimator.apply(
                btnNextMonth
        );
    }

    private void loadCalendarData() {
        Calendar monthToLoad =
                (Calendar) selectedMonth.clone();

        int currentRequest =
                ++calendarRequestVersion;

        updateMonthTitle(monthToLoad);
        setLoadingState(true);

        new Thread(() -> {
            try {
                List<Transaction> transactions =
                        DatabaseClient
                                .getInstance(
                                        getApplicationContext()
                                )
                                .getAppDatabase()
                                .transactionDao()
                                .getAllTransactions();

                MonthData monthData =
                        calculateMonthData(
                                transactions,
                                monthToLoad
                        );

                runOnUiThread(() -> {
                    if (currentRequest
                            != calendarRequestVersion) {

                        return;
                    }

                    showCalendar(
                            monthToLoad,
                            monthData
                    );

                    setLoadingState(false);
                });

            } catch (Exception exception) {
                runOnUiThread(() -> {
                    if (currentRequest
                            != calendarRequestVersion) {

                        return;
                    }

                    showCalendar(
                            monthToLoad,
                            new MonthData()
                    );

                    setLoadingState(false);

                    Toast.makeText(
                            CalendarActivity.this,
                            "Unable to load calendar data",
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }
        }).start();
    }

    private MonthData calculateMonthData(
            List<Transaction> transactions,
            Calendar monthToLoad
    ) {
        MonthData result =
                new MonthData();

        if (transactions == null) {
            return result;
        }

        int selectedYear =
                monthToLoad.get(
                        Calendar.YEAR
                );

        int selectedMonthNumber =
                monthToLoad.get(
                        Calendar.MONTH
                );

        for (Transaction transaction : transactions) {
            if (transaction == null) {
                continue;
            }

            Date transactionDate =
                    parseTransactionDate(
                            transaction.getDate()
                    );

            if (transactionDate == null) {
                continue;
            }

            Calendar transactionCalendar =
                    Calendar.getInstance();

            transactionCalendar.setTime(
                    transactionDate
            );

            boolean sameMonth =
                    transactionCalendar.get(
                            Calendar.YEAR
                    ) == selectedYear
                            && transactionCalendar.get(
                            Calendar.MONTH
                    ) == selectedMonthNumber;

            if (!sameMonth) {
                continue;
            }

            double amount =
                    transaction.getAmount();

            if (Double.isNaN(amount)
                    || Double.isInfinite(amount)) {

                continue;
            }

            int day =
                    transactionCalendar.get(
                            Calendar.DAY_OF_MONTH
                    );

            DailyTotal dailyTotal =
                    result.dailyTotals.get(day);

            if (dailyTotal == null) {
                dailyTotal =
                        new DailyTotal();

                result.dailyTotals.put(
                        day,
                        dailyTotal
                );
            }

            String type =
                    safeText(
                            transaction.getType(),
                            ""
                    );

            if ("INCOME".equalsIgnoreCase(type)) {
                dailyTotal.income += amount;
                dailyTotal.incomeCount++;

                result.monthIncome += amount;

            } else if ("EXPENSE".equalsIgnoreCase(type)) {
                dailyTotal.expense += amount;
                dailyTotal.expenseCount++;

                result.monthExpense += amount;
            }
        }

        return result;
    }

    private void showCalendar(
            Calendar month,
            MonthData monthData
    ) {
        updateMonthTitle(month);

        txtMonthIncome.setText(
                formatAmount(
                        monthData.monthIncome
                )
        );

        txtMonthExpense.setText(
                formatAmount(
                        monthData.monthExpense
                )
        );

        double monthNet =
                monthData.monthIncome
                        - monthData.monthExpense;

        txtMonthNet.setText(
                formatSignedAmount(
                        monthNet
                )
        );

        int netColor;

        if (monthNet > 0) {
            netColor =
                    getColorValue(
                            R.color.success
                    );

        } else if (monthNet < 0) {
            netColor =
                    getColorValue(
                            R.color.expense
                    );

        } else {
            netColor =
                    getColorValue(
                            R.color.app_text_secondary
                    );
        }

        txtMonthNet.setTextColor(
                netColor
        );

        calendarGrid.removeAllViews();

        boolean hasData =
                !monthData.dailyTotals.isEmpty();

        txtNoCalendarData.setVisibility(
                hasData
                        ? View.GONE
                        : View.VISIBLE
        );

        Calendar firstDay =
                (Calendar) month.clone();

        firstDay.set(
                Calendar.DAY_OF_MONTH,
                1
        );

        int firstDayPosition =
                firstDay.get(
                        Calendar.DAY_OF_WEEK
                ) - 1;

        int totalDays =
                firstDay.getActualMaximum(
                        Calendar.DAY_OF_MONTH
                );

        for (int index = 0;
             index < firstDayPosition;
             index++) {

            addEmptyCell();
        }

        for (int day = 1;
             day <= totalDays;
             day++) {

            DailyTotal dailyTotal =
                    monthData.dailyTotals.get(day);

            if (dailyTotal == null) {
                dailyTotal =
                        new DailyTotal();
            }

            addDayCell(
                    day,
                    dailyTotal,
                    month
            );
        }

        int totalUsedCells =
                firstDayPosition
                        + totalDays;

        int remainingCells =
                totalUsedCells % 7 == 0
                        ? 0
                        : 7 - (
                        totalUsedCells % 7
                );

        for (int index = 0;
             index < remainingCells;
             index++) {

            addEmptyCell();
        }
    }

    private void updateMonthTitle(
            Calendar month
    ) {
        String title =
                new SimpleDateFormat(
                        "MMMM yyyy",
                        Locale.ENGLISH
                ).format(
                        month.getTime()
                );

        txtMonthTitle.setText(title);
    }

    private void setLoadingState(
            boolean loading
    ) {
        btnPreviousMonth.setEnabled(!loading);
        btnNextMonth.setEnabled(!loading);

        btnPreviousMonth.setAlpha(
                loading
                        ? 0.55f
                        : 1f
        );

        btnNextMonth.setAlpha(
                loading
                        ? 0.55f
                        : 1f
        );
    }

    private void addEmptyCell() {
        View emptyCell =
                new View(this);

        emptyCell.setLayoutParams(
                createGridParams()
        );

        calendarGrid.addView(
                emptyCell
        );
    }

    private void addDayCell(
            int day,
            DailyTotal total,
            Calendar month
    ) {
        boolean hasIncome =
                total.incomeCount > 0;

        boolean hasExpense =
                total.expenseCount > 0;

        boolean hasData =
                hasIncome || hasExpense;

        boolean today =
                isToday(
                        day,
                        month
                );

        double netAmount =
                total.income
                        - total.expense;

        int cardBackgroundColor;
        int cardOutlineColor;
        int amountColor;
        int amountBackgroundColor;

        if (hasIncome && hasExpense) {
            cardBackgroundColor =
                    getColorValue(
                            R.color.warning_surface
                    );

            cardOutlineColor =
                    getColorValue(
                            R.color.warning_outline
                    );

            amountColor =
                    getColorValue(
                            R.color.warning
                    );

            amountBackgroundColor =
                    getColorValue(
                            R.color.app_surface
                    );

        } else if (hasIncome) {
            cardBackgroundColor =
                    getColorValue(
                            R.color.success_surface
                    );

            cardOutlineColor =
                    getColorValue(
                            R.color.success_outline
                    );

            amountColor =
                    getColorValue(
                            R.color.success
                    );

            amountBackgroundColor =
                    getColorValue(
                            R.color.app_surface
                    );

        } else if (hasExpense) {
            cardBackgroundColor =
                    getColorValue(
                            R.color.expense_surface
                    );

            cardOutlineColor =
                    getColorValue(
                            R.color.expense_outline
                    );

            amountColor =
                    getColorValue(
                            R.color.expense
                    );

            amountBackgroundColor =
                    getColorValue(
                            R.color.app_surface
                    );

        } else if (today) {
            cardBackgroundColor =
                    getColorValue(
                            R.color.info_surface
                    );

            cardOutlineColor =
                    getColorValue(
                            R.color.primary
                    );

            amountColor =
                    getColorValue(
                            R.color.primary
                    );

            amountBackgroundColor =
                    getColorValue(
                            R.color.app_surface
                    );

        } else {
            cardBackgroundColor =
                    getColorValue(
                            R.color.app_surface
                    );

            cardOutlineColor =
                    getColorValue(
                            R.color.app_outline_soft
                    );

            amountColor =
                    getColorValue(
                            R.color.app_text_secondary
                    );

            amountBackgroundColor =
                    getColorValue(
                            R.color.app_surface_soft
                    );
        }

        MaterialCardView dayCard =
                new MaterialCardView(this);

        dayCard.setLayoutParams(
                createGridParams()
        );

        dayCard.setCardBackgroundColor(
                cardBackgroundColor
        );

        dayCard.setRadius(
                dpToPx(13)
        );

        dayCard.setCardElevation(0);

        dayCard.setStrokeWidth(
                today
                        ? dpToPx(2)
                        : dpToPx(1)
        );

        dayCard.setStrokeColor(
                today
                        ? getColorValue(
                        R.color.primary
                )
                        : cardOutlineColor
        );

        dayCard.setClickable(hasData);
        dayCard.setFocusable(hasData);

        dayCard.setAlpha(
                hasData || today
                        ? 1f
                        : 0.88f
        );

        LinearLayout content =
                new LinearLayout(this);

        content.setLayoutParams(
                new MaterialCardView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        );

        content.setOrientation(
                LinearLayout.VERTICAL
        );

        content.setGravity(
                Gravity.CENTER
        );

        content.setPadding(
                dpToPx(2),
                dpToPx(5),
                dpToPx(2),
                dpToPx(5)
        );

        TextView dayNumber =
                new TextView(this);

        dayNumber.setText(
                String.valueOf(day)
        );

        dayNumber.setGravity(
                Gravity.CENTER
        );

        dayNumber.setTextSize(13);
        dayNumber.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        dayNumber.setTextColor(
                getDayNumberColor(
                        day,
                        month,
                        today
                )
        );

        TextView amountView =
                new TextView(this);

        amountView.setGravity(
                Gravity.CENTER
        );

        amountView.setTextSize(8);
        amountView.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        amountView.setTextColor(
                amountColor
        );

        amountView.setSingleLine(true);

        LinearLayout.LayoutParams amountParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        dpToPx(22)
                );

        amountParams.setMargins(
                0,
                dpToPx(4),
                0,
                0
        );

        amountView.setLayoutParams(
                amountParams
        );

        if (hasIncome && hasExpense) {
            amountView.setText(
                    "↕ "
                            + formatCompactSigned(
                            netAmount
                    )
            );

        } else if (hasIncome) {
            amountView.setText(
                    "↑ "
                            + formatCompact(
                            total.income
                    )
            );

        } else if (hasExpense) {
            amountView.setText(
                    "↓ "
                            + formatCompact(
                            total.expense
                    )
            );

        } else if (today) {
            amountView.setText("Today");

        } else {
            amountView.setText("");
        }

        if (hasData || today) {
            amountView.setPadding(
                    dpToPx(5),
                    0,
                    dpToPx(5),
                    0
            );

            amountView.setBackground(
                    createRoundedDrawable(
                            amountBackgroundColor,
                            cardOutlineColor,
                            10
                    )
            );
        }

        content.addView(
                dayNumber
        );

        content.addView(
                amountView
        );

        dayCard.addView(
                content
        );

        if (hasData) {
            BubbleTouchAnimator.apply(
                    dayCard
            );

            DailyTotal selectedTotal =
                    total;

            dayCard.setOnClickListener(view ->
                    showDaySummaryDialog(
                            day,
                            selectedTotal,
                            month
                    )
            );
        }

        calendarGrid.addView(
                dayCard
        );
    }

    private int getDayNumberColor(
            int day,
            Calendar month,
            boolean today
    ) {
        if (today) {
            return getColorValue(
                    R.color.primary
            );
        }

        Calendar date =
                (Calendar) month.clone();

        date.set(
                Calendar.DAY_OF_MONTH,
                day
        );

        int dayOfWeek =
                date.get(
                        Calendar.DAY_OF_WEEK
                );

        if (dayOfWeek
                == Calendar.SUNDAY) {

            return getColorValue(
                    R.color.expense
            );
        }

        if (dayOfWeek
                == Calendar.SATURDAY) {

            return getColorValue(
                    R.color.secondary
            );
        }

        return getColorValue(
                R.color.app_text_primary
        );
    }

    private void showDaySummaryDialog(
            int day,
            DailyTotal total,
            Calendar month
    ) {
        Calendar selectedDate =
                (Calendar) month.clone();

        selectedDate.set(
                Calendar.DAY_OF_MONTH,
                day
        );

        String visibleDate =
                new SimpleDateFormat(
                        "EEEE, dd MMMM yyyy",
                        Locale.ENGLISH
                ).format(
                        selectedDate.getTime()
                );

        double netAmount =
                total.income
                        - total.expense;

        int netColor;

        if (netAmount > 0) {
            netColor =
                    getColorValue(
                            R.color.success
                    );

        } else if (netAmount < 0) {
            netColor =
                    getColorValue(
                            R.color.expense
                    );

        } else {
            netColor =
                    getColorValue(
                            R.color.warning
                    );
        }

        LinearLayout dialogContent =
                new LinearLayout(this);

        dialogContent.setOrientation(
                LinearLayout.VERTICAL
        );

        dialogContent.setPadding(
                dpToPx(22),
                dpToPx(5),
                dpToPx(22),
                dpToPx(6)
        );

        MaterialCardView netCard =
                createNetSummaryCard(
                        netAmount,
                        netColor
                );

        dialogContent.addView(
                netCard
        );

        LinearLayout amountRow =
                new LinearLayout(this);

        amountRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        amountRow.setBaselineAligned(false);

        LinearLayout.LayoutParams amountRowParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        amountRowParams.setMargins(
                0,
                dpToPx(12),
                0,
                0
        );

        amountRow.setLayoutParams(
                amountRowParams
        );

        MaterialCardView incomeCard =
                createDialogMetricCard(
                        "Income",
                        formatAmount(
                                total.income
                        ),
                        total.incomeCount
                                + " transaction(s)",
                        getColorValue(
                                R.color.success
                        ),
                        getColorValue(
                                R.color.success_surface
                        ),
                        getColorValue(
                                R.color.success_outline
                        ),
                        "↑"
                );

        LinearLayout.LayoutParams incomeParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        incomeParams.setMargins(
                0,
                0,
                dpToPx(5),
                0
        );

        incomeCard.setLayoutParams(
                incomeParams
        );

        MaterialCardView expenseCard =
                createDialogMetricCard(
                        "Expense",
                        formatAmount(
                                total.expense
                        ),
                        total.expenseCount
                                + " transaction(s)",
                        getColorValue(
                                R.color.expense
                        ),
                        getColorValue(
                                R.color.expense_surface
                        ),
                        getColorValue(
                                R.color.expense_outline
                        ),
                        "↓"
                );

        LinearLayout.LayoutParams expenseParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        expenseParams.setMargins(
                dpToPx(5),
                0,
                0,
                0
        );

        expenseCard.setLayoutParams(
                expenseParams
        );

        amountRow.addView(
                incomeCard
        );

        amountRow.addView(
                expenseCard
        );

        dialogContent.addView(
                amountRow
        );

        MaterialCardView activityCard =
                createActivityCard(
                        total
                );

        LinearLayout.LayoutParams activityParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        activityParams.setMargins(
                0,
                dpToPx(12),
                0,
                0
        );

        activityCard.setLayoutParams(
                activityParams
        );

        dialogContent.addView(
                activityCard
        );

        new AlertDialog.Builder(this)
                .setTitle(visibleDate)
                .setView(dialogContent)
                .setPositiveButton(
                        "Close",
                        null
                )
                .show();
    }

    private MaterialCardView createNetSummaryCard(
            double netAmount,
            int netColor
    ) {
        int surfaceColor;

        int outlineColor;

        if (netAmount > 0) {
            surfaceColor =
                    getColorValue(
                            R.color.success_surface
                    );

            outlineColor =
                    getColorValue(
                            R.color.success_outline
                    );

        } else if (netAmount < 0) {
            surfaceColor =
                    getColorValue(
                            R.color.expense_surface
                    );

            outlineColor =
                    getColorValue(
                            R.color.expense_outline
                    );

        } else {
            surfaceColor =
                    getColorValue(
                            R.color.warning_surface
                    );

            outlineColor =
                    getColorValue(
                            R.color.warning_outline
                    );
        }

        MaterialCardView card =
                new MaterialCardView(this);

        card.setCardBackgroundColor(
                surfaceColor
        );

        card.setRadius(
                dpToPx(16)
        );

        card.setCardElevation(0);
        card.setStrokeWidth(dpToPx(1));
        card.setStrokeColor(outlineColor);

        LinearLayout content =
                new LinearLayout(this);

        content.setOrientation(
                LinearLayout.HORIZONTAL
        );

        content.setGravity(
                Gravity.CENTER_VERTICAL
        );

        content.setPadding(
                dpToPx(14),
                dpToPx(13),
                dpToPx(14),
                dpToPx(13)
        );

        TextView icon =
                createText(
                        "±",
                        20,
                        netColor,
                        true
                );

        icon.setGravity(
                Gravity.CENTER
        );

        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(
                        dpToPx(42),
                        dpToPx(42)
                );

        icon.setLayoutParams(
                iconParams
        );

        content.addView(
                icon
        );

        LinearLayout textContainer =
                new LinearLayout(this);

        textContainer.setOrientation(
                LinearLayout.VERTICAL
        );

        LinearLayout.LayoutParams textParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        textParams.setMargins(
                dpToPx(10),
                0,
                0,
                0
        );

        textContainer.setLayoutParams(
                textParams
        );

        TextView label =
                createText(
                        "Daily Net Cash Flow",
                        10,
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        true
                );

        TextView amount =
                createText(
                        formatSignedAmount(
                                netAmount
                        ),
                        20,
                        netColor,
                        true
                );

        LinearLayout.LayoutParams amountParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        amountParams.setMargins(
                0,
                dpToPx(3),
                0,
                0
        );

        amount.setLayoutParams(
                amountParams
        );

        textContainer.addView(
                label
        );

        textContainer.addView(
                amount
        );

        content.addView(
                textContainer
        );

        card.addView(
                content
        );

        return card;
    }

    private MaterialCardView createDialogMetricCard(
            String label,
            String amount,
            String description,
            int accentColor,
            int backgroundColor,
            int outlineColor,
            String iconText
    ) {
        MaterialCardView card =
                new MaterialCardView(this);

        card.setCardBackgroundColor(
                backgroundColor
        );

        card.setRadius(
                dpToPx(15)
        );

        card.setCardElevation(0);
        card.setStrokeWidth(dpToPx(1));
        card.setStrokeColor(outlineColor);

        LinearLayout content =
                new LinearLayout(this);

        content.setOrientation(
                LinearLayout.VERTICAL
        );

        content.setGravity(
                Gravity.CENTER
        );

        content.setPadding(
                dpToPx(8),
                dpToPx(12),
                dpToPx(8),
                dpToPx(12)
        );

        TextView icon =
                createText(
                        iconText,
                        17,
                        accentColor,
                        true
                );

        icon.setGravity(
                Gravity.CENTER
        );

        TextView labelView =
                createText(
                        label,
                        10,
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        true
                );

        LinearLayout.LayoutParams labelParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        labelParams.setMargins(
                0,
                dpToPx(3),
                0,
                0
        );

        labelView.setLayoutParams(
                labelParams
        );

        TextView amountView =
                createText(
                        amount,
                        14,
                        accentColor,
                        true
                );

        amountView.setGravity(
                Gravity.CENTER
        );

        LinearLayout.LayoutParams amountParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        amountParams.setMargins(
                0,
                dpToPx(4),
                0,
                0
        );

        amountView.setLayoutParams(
                amountParams
        );

        TextView descriptionView =
                createText(
                        description,
                        9,
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        false
                );

        descriptionView.setGravity(
                Gravity.CENTER
        );

        LinearLayout.LayoutParams descriptionParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        descriptionParams.setMargins(
                0,
                dpToPx(3),
                0,
                0
        );

        descriptionView.setLayoutParams(
                descriptionParams
        );

        content.addView(icon);
        content.addView(labelView);
        content.addView(amountView);
        content.addView(descriptionView);

        card.addView(content);

        return card;
    }

    private MaterialCardView createActivityCard(
            DailyTotal total
    ) {
        MaterialCardView card =
                new MaterialCardView(this);

        card.setCardBackgroundColor(
                getColorValue(
                        R.color.app_surface_soft
                )
        );

        card.setRadius(
                dpToPx(14)
        );

        card.setCardElevation(0);
        card.setStrokeWidth(dpToPx(1));

        card.setStrokeColor(
                getColorValue(
                        R.color.app_outline_soft
                )
        );

        LinearLayout content =
                new LinearLayout(this);

        content.setOrientation(
                LinearLayout.VERTICAL
        );

        content.setPadding(
                dpToPx(13),
                dpToPx(11),
                dpToPx(13),
                dpToPx(11)
        );

        TextView title =
                createText(
                        "Daily Activity",
                        11,
                        getColorValue(
                                R.color.app_text_primary
                        ),
                        true
                );

        int totalTransactions =
                total.incomeCount
                        + total.expenseCount;

        TextView description =
                createText(
                        totalTransactions
                                + " total transaction(s) recorded on this date.",
                        10,
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        false
                );

        description.setLineSpacing(
                dpToPx(2),
                1f
        );

        LinearLayout.LayoutParams descriptionParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        descriptionParams.setMargins(
                0,
                dpToPx(4),
                0,
                0
        );

        description.setLayoutParams(
                descriptionParams
        );

        content.addView(
                title
        );

        content.addView(
                description
        );

        card.addView(
                content
        );

        return card;
    }

    private GridLayout.LayoutParams createGridParams() {
        GridLayout.LayoutParams params =
                new GridLayout.LayoutParams();

        params.rowSpec =
                GridLayout.spec(
                        GridLayout.UNDEFINED
                );

        params.columnSpec =
                GridLayout.spec(
                        GridLayout.UNDEFINED,
                        1f
                );

        params.width = 0;
        params.height = dpToPx(72);

        params.setMargins(
                dpToPx(2),
                dpToPx(2),
                dpToPx(2),
                dpToPx(2)
        );

        return params;
    }

    private boolean isToday(
            int day,
            Calendar month
    ) {
        Calendar today =
                Calendar.getInstance();

        return today.get(
                Calendar.YEAR
        ) == month.get(
                Calendar.YEAR
        )
                && today.get(
                Calendar.MONTH
        ) == month.get(
                Calendar.MONTH
        )
                && today.get(
                Calendar.DAY_OF_MONTH
        ) == day;
    }

    private Date parseTransactionDate(
            String value
    ) {
        if (value == null
                || value.trim().isEmpty()) {

            return null;
        }

        String cleanValue =
                value.trim();

        String[] patterns = {
                "yyyy-MM-dd HH:mm:ss.SSS",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd'T'HH:mm:ss.SSS",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm",
                "yyyy-MM-dd",
                "dd MMMM yyyy HH:mm:ss",
                "dd MMMM yyyy HH:mm",
                "dd MMM yyyy HH:mm:ss",
                "dd MMM yyyy HH:mm",
                "dd MMMM yyyy",
                "dd MMM yyyy",
                "dd/MM/yyyy HH:mm:ss",
                "dd/MM/yyyy HH:mm",
                "dd/MM/yyyy"
        };

        for (String pattern : patterns) {
            try {
                SimpleDateFormat format =
                        new SimpleDateFormat(
                                pattern,
                                Locale.ENGLISH
                        );

                format.setLenient(false);

                Date parsedDate =
                        format.parse(
                                cleanValue
                        );

                if (parsedDate != null) {
                    return parsedDate;
                }

            } catch (Exception ignored) {
                // Try the next supported date format.
            }
        }

        return null;
    }

    private TextView createText(
            String text,
            float textSize,
            int textColor,
            boolean bold
    ) {
        TextView textView =
                new TextView(this);

        textView.setText(text);
        textView.setTextSize(textSize);
        textView.setTextColor(textColor);

        if (bold) {
            textView.setTypeface(
                    Typeface.DEFAULT,
                    Typeface.BOLD
            );
        }

        return textView;
    }

    private GradientDrawable createRoundedDrawable(
            int backgroundColor,
            int outlineColor,
            int radiusDp
    ) {
        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(
                backgroundColor
        );

        drawable.setStroke(
                dpToPx(1),
                outlineColor
        );

        drawable.setCornerRadius(
                dpToPx(radiusDp)
        );

        return drawable;
    }

    private String formatAmount(
            double amount
    ) {
        NumberFormat numberFormat =
                NumberFormat.getNumberInstance(
                        new Locale(
                                "en",
                                "IN"
                        )
                );

        numberFormat.setMinimumFractionDigits(2);
        numberFormat.setMaximumFractionDigits(2);

        return "₹"
                + numberFormat.format(
                amount
        );
    }

    private String formatSignedAmount(
            double amount
    ) {
        if (amount > 0) {
            return "+"
                    + formatAmount(amount);

        } else if (amount < 0) {
            return "-"
                    + formatAmount(
                    Math.abs(amount)
            );
        }

        return formatAmount(0);
    }

    private String formatCompact(
            double amount
    ) {
        double absoluteAmount =
                Math.abs(amount);

        DecimalFormat decimalFormat =
                new DecimalFormat("0.#");

        if (absoluteAmount >= 10000000) {
            return "₹"
                    + decimalFormat.format(
                    absoluteAmount / 10000000
            )
                    + "Cr";
        }

        if (absoluteAmount >= 100000) {
            return "₹"
                    + decimalFormat.format(
                    absoluteAmount / 100000
            )
                    + "L";
        }

        if (absoluteAmount >= 1000) {
            return "₹"
                    + decimalFormat.format(
                    absoluteAmount / 1000
            )
                    + "K";
        }

        return "₹"
                + decimalFormat.format(
                absoluteAmount
        );
    }

    private String formatCompactSigned(
            double amount
    ) {
        if (amount > 0) {
            return "+"
                    + formatCompact(amount);

        } else if (amount < 0) {
            return "-"
                    + formatCompact(amount);
        }

        return formatCompact(0);
    }

    private String safeText(
            String value,
            String fallback
    ) {
        if (value == null
                || value.trim().isEmpty()) {

            return fallback;
        }

        return value.trim();
    }

    private void clearTime(
            Calendar calendar
    ) {
        calendar.set(
                Calendar.HOUR_OF_DAY,
                0
        );

        calendar.set(
                Calendar.MINUTE,
                0
        );

        calendar.set(
                Calendar.SECOND,
                0
        );

        calendar.set(
                Calendar.MILLISECOND,
                0
        );
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
        return Math.round(
                dp
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    private static class MonthData {

        private final Map<Integer, DailyTotal> dailyTotals =
                new HashMap<>();

        private double monthIncome;
        private double monthExpense;
    }

    private static class DailyTotal {

        private double income;
        private double expense;

        private int incomeCount;
        private int expenseCount;
    }
}