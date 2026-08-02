package com.example.moneymanagerpro.activities;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.credit.CreditCardCycleCalculator;
import com.example.moneymanagerpro.database.AppDatabase;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.CreditCard;
import com.example.moneymanagerpro.model.Goal;
import com.example.moneymanagerpro.model.Loan;
import com.example.moneymanagerpro.model.RecurringTransaction;
import com.example.moneymanagerpro.model.Subscription;
import com.example.moneymanagerpro.model.Transaction;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.text.NumberFormat;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CalendarActivity extends AppCompatActivity {

    private final Calendar selectedMonth = Calendar.getInstance();
    private TextView monthTitle;
    private TextView alertSummary;
    private TextView emptyAlerts;
    private GridLayout calendarGrid;
    private LinearLayout alertContainer;
    private MaterialButton previousButton;
    private MaterialButton nextButton;
    private int loadVersion;
    private List<FinanceEvent> allEvents = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        selectedMonth.set(Calendar.DAY_OF_MONTH, 1);
        clearTime(selectedMonth);
        setContentView(buildScreen());
        bindActions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    @NonNull
    private View buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(color(R.color.app_background));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(32));
        scroll.addView(root);

        TextView back = text("←  Back", 13, R.color.secondary, true);
        back.setPadding(0, dp(4), 0, dp(10));
        back.setOnClickListener(v -> finish());
        root.addView(back);
        root.addView(text("Unified Finance Calendar", 25, R.color.app_text_primary, true));
        TextView subtitle = text("Transactions, bills, subscriptions, EMI, card dues and goals in one place", 11, R.color.app_text_secondary, false);
        subtitle.setPadding(0, dp(3), 0, dp(14));
        root.addView(subtitle);

        MaterialCardView selector = card(R.color.info_surface, R.color.info_outline);
        LinearLayout selectorRow = row();
        selectorRow.setPadding(dp(10), dp(9), dp(10), dp(9));
        previousButton = button("‹");
        nextButton = button("›");
        monthTitle = text("", 17, R.color.secondary, true);
        monthTitle.setGravity(Gravity.CENTER);
        selectorRow.addView(previousButton, new LinearLayout.LayoutParams(dp(44), dp(44)));
        selectorRow.addView(monthTitle, new LinearLayout.LayoutParams(0, dp(44), 1f));
        selectorRow.addView(nextButton, new LinearLayout.LayoutParams(dp(44), dp(44)));
        selector.addView(selectorRow);
        root.addView(selector);

        LinearLayout legend = row();
        legend.setPadding(0, dp(12), 0, dp(8));
        addLegend(legend, "Txn", R.color.success);
        addLegend(legend, "Bill", R.color.orange);
        addLegend(legend, "EMI", R.color.expense);
        addLegend(legend, "Card", R.color.purple);
        addLegend(legend, "Goal", R.color.secondary);
        root.addView(legend);

        LinearLayout weekdays = row();
        for (String name : new String[]{"S", "M", "T", "W", "T", "F", "S"}) {
            TextView day = text(name, 11, R.color.app_text_secondary, true);
            day.setGravity(Gravity.CENTER);
            weekdays.addView(day, new LinearLayout.LayoutParams(0, dp(30), 1f));
        }
        root.addView(weekdays);

        calendarGrid = new GridLayout(this);
        calendarGrid.setColumnCount(7);
        calendarGrid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        root.addView(calendarGrid);

        TextView centerTitle = text("Notification Center", 19, R.color.app_text_primary, true);
        LinearLayout.LayoutParams centerTitleParams = new LinearLayout.LayoutParams(-1, -2);
        centerTitleParams.setMargins(0, dp(22), 0, 0);
        centerTitle.setLayoutParams(centerTitleParams);
        root.addView(centerTitle);
        alertSummary = text("Loading upcoming alerts...", 11, R.color.app_text_secondary, false);
        alertSummary.setPadding(0, dp(3), 0, dp(9));
        root.addView(alertSummary);

        LinearLayout quickRow = row();
        MaterialButton bills = actionButton("Manage Bills");
        bills.setOnClickListener(v -> startActivity(new Intent(this, RecurringActivity.class)));
        MaterialButton cards = actionButton("Credit Cards");
        cards.setOnClickListener(v -> startActivity(new Intent(this, CreditCardActivity.class)));
        quickRow.addView(bills, weightedButtonParams(true));
        quickRow.addView(cards, weightedButtonParams(false));
        root.addView(quickRow);

        alertContainer = new LinearLayout(this);
        alertContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(alertContainer);
        emptyAlerts = text("No upcoming financial alerts in the next 30 days.", 12, R.color.app_text_secondary, false);
        emptyAlerts.setGravity(Gravity.CENTER);
        emptyAlerts.setPadding(dp(12), dp(28), dp(12), dp(28));
        root.addView(emptyAlerts);
        return scroll;
    }

    private void bindActions() {
        previousButton.setOnClickListener(v -> { selectedMonth.add(Calendar.MONTH, -1); renderMonth(); });
        nextButton.setOnClickListener(v -> { selectedMonth.add(Calendar.MONTH, 1); renderMonth(); });
        BubbleTouchAnimator.apply(previousButton);
        BubbleTouchAnimator.apply(nextButton);
    }

    private void loadData() {
        final int version = ++loadVersion;
        setButtonsEnabled(false);
        new Thread(() -> {
            try {
                AppDatabase db = DatabaseClient.getInstance(getApplicationContext()).getAppDatabase();
                List<FinanceEvent> events = new ArrayList<>();
                addTransactionEvents(events, db.transactionDao().getAllTransactions());
                addRecurringEvents(events, db.recurringTransactionDao().getAllRecurringTransactions());
                addSubscriptionEvents(events, db.subscriptionDao().getActiveSubscriptions());
                addLoanEvents(events, db.loanDao().getActiveLoans());
                addCardEvents(events, db.creditCardDao().getActiveCreditCards());
                addGoalEvents(events, db.goalDao().getAllGoals());
                Collections.sort(events, Comparator.comparingLong(e -> e.date.getTime()));
                runOnUiThread(() -> {
                    if (version != loadVersion || isFinishing() || isDestroyed()) return;
                    allEvents = events;
                    renderMonth();
                    renderAlerts();
                    setButtonsEnabled(true);
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    setButtonsEnabled(true);
                    Toast.makeText(this, "Unable to load unified calendar", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void renderMonth() {
        monthTitle.setText(new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(selectedMonth.getTime()));
        calendarGrid.removeAllViews();
        Map<Integer, List<FinanceEvent>> byDay = new HashMap<>();
        int year = selectedMonth.get(Calendar.YEAR);
        int month = selectedMonth.get(Calendar.MONTH);
        for (FinanceEvent event : allEvents) {
            Calendar c = Calendar.getInstance();
            c.setTime(event.date);
            if (c.get(Calendar.YEAR) == year && c.get(Calendar.MONTH) == month) {
                byDay.computeIfAbsent(c.get(Calendar.DAY_OF_MONTH), ignored -> new ArrayList<>()).add(event);
            }
        }
        Calendar first = (Calendar) selectedMonth.clone();
        first.set(Calendar.DAY_OF_MONTH, 1);
        int offset = first.get(Calendar.DAY_OF_WEEK) - 1;
        for (int i = 0; i < offset; i++) addEmptyCell();
        int max = first.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int day = 1; day <= max; day++) addDayCell(day, byDay.get(day));
        int used = offset + max;
        int remainder = used % 7;
        if (remainder != 0) for (int i = remainder; i < 7; i++) addEmptyCell();
    }

    private void addDayCell(int day, List<FinanceEvent> events) {
        boolean hasEvents = events != null && !events.isEmpty();
        MaterialCardView card = card(hasEvents ? R.color.info_surface : R.color.app_surface,
                hasEvents ? R.color.info_outline : R.color.app_outline_soft);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dp(72);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(dp(2), dp(2), dp(2), dp(2));
        card.setLayoutParams(params);
        card.setRadius(dp(12));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        TextView number = text(String.valueOf(day), 13, R.color.app_text_primary, true);
        number.setGravity(Gravity.CENTER);
        content.addView(number);
        if (hasEvents) {
            TextView count = text(events.size() + " item" + (events.size() == 1 ? "" : "s"), 8, colorForType(events.get(0).type), true);
            count.setGravity(Gravity.CENTER);
            count.setPadding(0, dp(4), 0, 0);
            content.addView(count);
            card.setOnClickListener(v -> showDayEvents(day, events));
            BubbleTouchAnimator.apply(card);
        }
        card.addView(content);
        calendarGrid.addView(card);
    }

    private void showDayEvents(int day, List<FinanceEvent> events) {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(4), dp(8), dp(4));
        for (FinanceEvent event : events) list.addView(buildEventCard(event, false));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        new AlertDialog.Builder(this)
                .setTitle(day + " " + new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(selectedMonth.getTime()))
                .setView(scroll)
                .setPositiveButton("Close", null)
                .show();
    }

    private void renderAlerts() {
        alertContainer.removeAllViews();
        Calendar today = Calendar.getInstance();
        clearTime(today);
        Calendar end = (Calendar) today.clone();
        end.add(Calendar.DAY_OF_MONTH, 30);
        List<FinanceEvent> upcoming = new ArrayList<>();
        for (FinanceEvent event : allEvents) {
            if (!event.date.before(today.getTime()) && !event.date.after(end.getTime()) && !"Transaction".equals(event.type)) {
                upcoming.add(event);
            }
        }
        alertSummary.setText(upcoming.size() + " upcoming obligation" + (upcoming.size() == 1 ? "" : "s") + " in the next 30 days");
        emptyAlerts.setVisibility(upcoming.isEmpty() ? View.VISIBLE : View.GONE);
        for (int i = 0; i < Math.min(upcoming.size(), 12); i++) alertContainer.addView(buildEventCard(upcoming.get(i), true));
    }

    @NonNull
    private View buildEventCard(FinanceEvent event, boolean showCountdown) {
        MaterialCardView card = card(surfaceForType(event.type), outlineForType(event.type));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(5), 0, dp(5));
        card.setLayoutParams(params);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(13), dp(11), dp(13), dp(11));
        LinearLayout header = row();
        TextView title = text(event.title, 13, R.color.app_text_primary, true);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        header.addView(text(event.type, 10, colorForType(event.type), true));
        content.addView(header);
        String detail = formatDate(event.date);
        if (event.amount > 0) detail += "  •  " + money(event.amount);
        if (showCountdown) detail += "  •  " + countdown(event.date);
        TextView detailView = text(detail, 10, R.color.app_text_secondary, false);
        detailView.setPadding(0, dp(4), 0, 0);
        content.addView(detailView);
        if (!event.detail.isEmpty()) {
            TextView note = text(event.detail, 10, R.color.app_text_secondary, false);
            note.setPadding(0, dp(3), 0, 0);
            content.addView(note);
        }
        card.addView(content);
        return card;
    }

    private void addTransactionEvents(List<FinanceEvent> out, List<Transaction> items) {
        if (items == null) return;
        for (Transaction item : items) {
            Date date = parseDate(item.getDate());
            if (date == null) continue;
            String type = item.getType() == null ? "" : item.getType();
            String title = ("INCOME".equalsIgnoreCase(type) ? "Income" : "Expense") + " • " + safe(item.getCategory(), "Transaction");
            out.add(new FinanceEvent(date, "Transaction", title, item.getAmount(), safe(item.getNote(), "")));
        }
    }

    private void addRecurringEvents(List<FinanceEvent> out, List<RecurringTransaction> items) {
        if (items == null) return;
        for (RecurringTransaction item : items) {
            if (!item.isActive()) continue;
            Date date = parseDate(item.getNextRunDate());
            if (date != null) out.add(new FinanceEvent(date, "Bill", safe(item.getCategory(), "Recurring payment"), item.getAmount(), safe(item.getNote(), item.getFrequency())));
        }
    }

    private void addSubscriptionEvents(List<FinanceEvent> out, List<Subscription> items) {
        if (items == null) return;
        for (Subscription item : items) {
            Date date = parseDate(item.getNextDueDate());
            if (date != null) out.add(new FinanceEvent(date, "Bill", safe(item.getName(), "Subscription"), item.getAmount(), safe(item.getBillingCycle(), "Subscription")));
        }
    }

    private void addLoanEvents(List<FinanceEvent> out, List<Loan> items) {
        if (items == null) return;
        for (Loan item : items) {
            if (!item.isActive() || !"Loan Taken".equalsIgnoreCase(item.getLoanType())) continue;
            Date date = parseDate(item.getDueDate());
            if (date != null) out.add(new FinanceEvent(date, "EMI", safe(item.getPersonName(), "Loan EMI"), item.getEmiAmount(), "Outstanding " + money(item.getOutstandingAmount())));
        }
    }

    private void addCardEvents(List<FinanceEvent> out, List<CreditCard> items) {
        if (items == null) return;
        Calendar reference = Calendar.getInstance();
        for (CreditCard item : items) {
            CreditCardCycleCalculator.Cycle cycle = CreditCardCycleCalculator.calculate(item, reference);
            Date date = parseDate(cycle.dueDate);
            if (date != null) out.add(new FinanceEvent(date, "Card", safe(item.getName(), "Credit Card") + " •••• " + safe(item.getLastFour(), ""), 0, "Payment account: " + safe(item.getPaymentAccount(), "Cash")));
        }
    }

    private void addGoalEvents(List<FinanceEvent> out, List<Goal> items) {
        if (items == null) return;
        for (Goal item : items) {
            if (item.getSavedAmount() >= item.getTargetAmount()) continue;
            Date date = parseDate(item.getTargetDate());
            if (date != null) out.add(new FinanceEvent(date, "Goal", safe(item.getName(), "Savings Goal"), item.getTargetAmount() - item.getSavedAmount(), "Remaining target"));
        }
    }

    private Date parseDate(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String[] patterns = {"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd", "dd-MM-yyyy HH:mm", "dd-MM-yyyy", "dd/MM/yyyy HH:mm", "dd/MM/yyyy"};
        for (String pattern : patterns) {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
            format.setLenient(false);
            ParsePosition position = new ParsePosition(0);
            Date date = format.parse(value.trim(), position);
            if (date != null && position.getIndex() == value.trim().length()) {
                Calendar c = Calendar.getInstance();
                c.setTime(date);
                clearTime(c);
                return c.getTime();
            }
        }
        return null;
    }

    private String countdown(Date date) {
        Calendar today = Calendar.getInstance();
        clearTime(today);
        long days = (date.getTime() - today.getTimeInMillis()) / 86400000L;
        if (days == 0) return "Due today";
        if (days == 1) return "Due tomorrow";
        return days + " days left";
    }

    private void addEmptyCell() {
        View empty = new View(this);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dp(72);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(dp(2), dp(2), dp(2), dp(2));
        empty.setLayoutParams(params);
        calendarGrid.addView(empty);
    }

    private void addLegend(LinearLayout parent, String label, int colorRes) {
        TextView view = text("● " + label, 9, colorRes, true);
        view.setGravity(Gravity.CENTER);
        parent.addView(view, new LinearLayout.LayoutParams(0, dp(28), 1f));
    }

    private MaterialCardView card(int background, int outline) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(color(background));
        card.setStrokeColor(color(outline));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(16));
        card.setCardElevation(0);
        return card;
    }

    private TextView text(String value, float size, int colorRes, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color(colorRes));
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private MaterialButton button(String label) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setTextSize(22);
        button.setAllCaps(false);
        button.setCornerRadius(dp(13));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        return button;
    }

    private MaterialButton actionButton(String label) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setTextSize(11);
        button.setAllCaps(false);
        button.setCornerRadius(dp(13));
        BubbleTouchAnimator.apply(button);
        return button;
    }

    private LinearLayout.LayoutParams weightedButtonParams(boolean left) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(46), 1f);
        params.setMargins(left ? 0 : dp(5), 0, left ? dp(5) : 0, dp(7));
        return params;
    }

    private int colorForType(String type) {
        if ("Bill".equals(type)) return R.color.orange;
        if ("EMI".equals(type)) return R.color.expense;
        if ("Card".equals(type)) return R.color.purple;
        if ("Goal".equals(type)) return R.color.secondary;
        return R.color.success;
    }

    private int surfaceForType(String type) {
        if ("Bill".equals(type)) return R.color.warning_surface;
        if ("EMI".equals(type)) return R.color.expense_surface;
        if ("Card".equals(type)) return R.color.purple_surface;
        if ("Goal".equals(type)) return R.color.info_surface;
        return R.color.success_surface;
    }

    private int outlineForType(String type) {
        if ("Bill".equals(type)) return R.color.warning_outline;
        if ("EMI".equals(type)) return R.color.expense_outline;
        if ("Card".equals(type)) return R.color.purple_outline;
        if ("Goal".equals(type)) return R.color.info_outline;
        return R.color.success_outline;
    }

    private String money(double amount) {
        return NumberFormat.getCurrencyInstance(new Locale("en", "IN")).format(amount);
    }

    private String formatDate(Date date) {
        return new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(date);
    }

    private String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private int color(int resource) {
        return ContextCompat.getColor(this, resource);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void setButtonsEnabled(boolean enabled) {
        previousButton.setEnabled(enabled);
        nextButton.setEnabled(enabled);
        previousButton.setAlpha(enabled ? 1f : 0.5f);
        nextButton.setAlpha(enabled ? 1f : 0.5f);
    }

    private static void clearTime(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private static final class FinanceEvent {
        final Date date;
        final String type;
        final String title;
        final double amount;
        final String detail;
        FinanceEvent(Date date, String type, String title, double amount, String detail) {
            this.date = date;
            this.type = type;
            this.title = title;
            this.amount = amount;
            this.detail = detail == null ? "" : detail;
        }
    }
}
