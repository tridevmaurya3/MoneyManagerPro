package com.example.moneymanagerpro.dashboard;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.activities.AccountActivity;
import com.example.moneymanagerpro.activities.CreditCardActivity;
import com.example.moneymanagerpro.activities.DashboardActivity;
import com.example.moneymanagerpro.activities.RecurringActivity;
import com.example.moneymanagerpro.credit.CreditCardCycleCalculator;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.AccountBalance;
import com.example.moneymanagerpro.model.CreditCard;
import com.example.moneymanagerpro.model.RecurringTransaction;
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

/**
 * Adds a compact obligations and net-worth snapshot to Dashboard.
 * All calculations use only the local Room database.
 */
public final class DashboardObligationsController {

    private static final String PANEL_TAG =
            "money_manager_dashboard_obligations_panel";

    private final Activity activity;
    private LinearLayout panel;
    private LinearLayout billContainer;
    private LinearLayout cardDueContainer;
    private TextView txtAssets;
    private TextView txtLiabilities;
    private TextView txtNetWorth;
    private TextView txtNetWorthStatus;
    private int requestVersion;

    public DashboardObligationsController(
            @NonNull Activity activity
    ) {
        this.activity = activity;
    }

    public void attach() {
        if (!(activity instanceof DashboardActivity)
                || activity.isFinishing()
                || activity.isDestroyed()) {
            return;
        }

        View root = activity.findViewById(android.R.id.content);
        if (!(root instanceof ViewGroup)) {
            return;
        }

        View existing = root.findViewWithTag(PANEL_TAG);
        if (existing instanceof LinearLayout) {
            panel = (LinearLayout) existing;
            bindExistingPanel();
            loadData();
            return;
        }

        LinearLayout dashboardColumn = findDashboardColumn((ViewGroup) root);
        if (dashboardColumn == null) {
            root.postDelayed(this::attach, 120L);
            return;
        }

        panel = buildPanel();
        int insertIndex = findInsertionIndex(dashboardColumn);
        dashboardColumn.addView(panel, insertIndex);
        animatePanel(panel);
        loadData();
    }

    private void bindExistingPanel() {
        billContainer = panel.findViewWithTag("obligation_bill_container");
        cardDueContainer = panel.findViewWithTag("obligation_card_container");
        txtAssets = panel.findViewWithTag("net_assets");
        txtLiabilities = panel.findViewWithTag("net_liabilities");
        txtNetWorth = panel.findViewWithTag("net_worth");
        txtNetWorthStatus = panel.findViewWithTag("net_worth_status");
    }

    private LinearLayout findDashboardColumn(
            @NonNull ViewGroup parent
    ) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout layout = (LinearLayout) child;
                if (layout.getOrientation() == LinearLayout.VERTICAL
                        && containsSmartDashboard(layout)) {
                    return layout;
                }
            }
            if (child instanceof ViewGroup) {
                LinearLayout found = findDashboardColumn((ViewGroup) child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private boolean containsSmartDashboard(
            @NonNull ViewGroup parent
    ) {
        return parent.findViewWithTag(
                "money_manager_smart_dashboard_2_panel"
        ) != null;
    }

    private int findInsertionIndex(
            @NonNull LinearLayout parent
    ) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if ("money_manager_smart_dashboard_2_panel"
                    .equals(child.getTag())) {
                return Math.min(i + 1, parent.getChildCount());
            }
        }
        return parent.getChildCount();
    }

    @NonNull
    private LinearLayout buildPanel() {
        LinearLayout container = new LinearLayout(activity);
        container.setTag(PANEL_TAG);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, dp(7), 0, dp(8));

        LinearLayout.LayoutParams panelParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        panelParams.setMargins(dp(16), dp(8), dp(16), dp(12));
        container.setLayoutParams(panelParams);

        TextView title = text(
                "Upcoming & Net Worth",
                18,
                R.color.app_text_primary,
                true
        );
        container.addView(title);

        TextView subtitle = text(
                "Bills, card due dates and your current account-based financial position",
                11,
                R.color.app_text_secondary,
                false
        );
        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        subtitleParams.setMargins(0, dp(3), 0, dp(11));
        subtitle.setLayoutParams(subtitleParams);
        container.addView(subtitle);

        container.addView(buildNetWorthCard());
        container.addView(sectionTitle("Upcoming Bills • Next 30 Days"));

        billContainer = new LinearLayout(activity);
        billContainer.setTag("obligation_bill_container");
        billContainer.setOrientation(LinearLayout.VERTICAL);
        container.addView(billContainer);

        container.addView(sectionTitle("Credit Card Due Alerts"));

        cardDueContainer = new LinearLayout(activity);
        cardDueContainer.setTag("obligation_card_container");
        cardDueContainer.setOrientation(LinearLayout.VERTICAL);
        container.addView(cardDueContainer);

        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams buttonRowParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        buttonRowParams.setMargins(0, dp(4), 0, 0);
        buttons.setLayoutParams(buttonRowParams);

        MaterialButton billsButton = button("Manage Bills");
        billsButton.setOnClickListener(view -> activity.startActivity(
                new Intent(activity, RecurringActivity.class)
        ));

        MaterialButton cardsButton = button("Credit Cards");
        cardsButton.setOnClickListener(view -> activity.startActivity(
                new Intent(activity, CreditCardActivity.class)
        ));

        LinearLayout.LayoutParams leftButtonParams =
                new LinearLayout.LayoutParams(0, dp(47), 1f);
        leftButtonParams.setMargins(0, 0, dp(5), 0);
        billsButton.setLayoutParams(leftButtonParams);

        LinearLayout.LayoutParams rightButtonParams =
                new LinearLayout.LayoutParams(0, dp(47), 1f);
        rightButtonParams.setMargins(dp(5), 0, 0, 0);
        cardsButton.setLayoutParams(rightButtonParams);

        buttons.addView(billsButton);
        buttons.addView(cardsButton);
        container.addView(buttons);
        BubbleTouchAnimator.apply(billsButton);
        BubbleTouchAnimator.apply(cardsButton);

        return container;
    }

    @NonNull
    private View buildNetWorthCard() {
        MaterialCardView card = card(
                R.color.info_surface,
                R.color.info_outline
        );

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(15), dp(14), dp(15), dp(14));

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);

        TextView heading = text(
                "Net Worth Snapshot",
                15,
                R.color.secondary,
                true
        );
        header.addView(heading, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));

        TextView openAccounts = text(
                "Accounts  ›",
                11,
                R.color.secondary,
                true
        );
        openAccounts.setPadding(dp(8), dp(6), dp(4), dp(6));
        openAccounts.setClickable(true);
        openAccounts.setFocusable(true);
        openAccounts.setOnClickListener(view -> activity.startActivity(
                new Intent(activity, AccountActivity.class)
        ));
        header.addView(openAccounts);
        content.addView(header);

        LinearLayout metrics = new LinearLayout(activity);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams metricsParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        metricsParams.setMargins(0, dp(12), 0, 0);
        metrics.setLayoutParams(metricsParams);

        txtAssets = metric(metrics, "Assets", "₹0", R.color.success, "net_assets");
        txtLiabilities = metric(metrics, "Liabilities", "₹0", R.color.expense, "net_liabilities");
        txtNetWorth = metric(metrics, "Net Worth", "₹0", R.color.secondary, "net_worth");
        content.addView(metrics);

        txtNetWorthStatus = text(
                "Calculating from local account balances...",
                11,
                R.color.app_text_secondary,
                false
        );
        txtNetWorthStatus.setTag("net_worth_status");
        LinearLayout.LayoutParams statusParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        statusParams.setMargins(0, dp(10), 0, 0);
        txtNetWorthStatus.setLayoutParams(statusParams);
        content.addView(txtNetWorthStatus);

        card.addView(content);
        return card;
    }

    private TextView metric(
            @NonNull LinearLayout parent,
            @NonNull String label,
            @NonNull String initial,
            @ColorRes int color,
            @NonNull String tag
    ) {
        LinearLayout block = new LinearLayout(activity);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setGravity(Gravity.CENTER);
        block.setPadding(dp(5), dp(6), dp(5), dp(6));
        block.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));

        TextView value = text(initial, 15, color, true);
        value.setTag(tag);
        value.setGravity(Gravity.CENTER);
        block.addView(value);

        TextView caption = text(
                label,
                9,
                R.color.app_text_secondary,
                false
        );
        caption.setGravity(Gravity.CENTER);
        block.addView(caption);
        parent.addView(block);
        return value;
    }

    private TextView sectionTitle(@NonNull String value) {
        TextView title = text(
                value,
                14,
                R.color.app_text_primary,
                true
        );
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        params.setMargins(0, dp(16), 0, dp(7));
        title.setLayoutParams(params);
        return title;
    }

    private void loadData() {
        int version = ++requestVersion;
        showLoading();

        new Thread(() -> {
            try {
                List<RecurringTransaction> recurring =
                        DatabaseClient.getInstance(activity)
                                .getAppDatabase()
                                .recurringTransactionDao()
                                .getAllRecurringTransactions();

                List<CreditCard> cards =
                        DatabaseClient.getInstance(activity)
                                .getAppDatabase()
                                .creditCardDao()
                                .getActiveCreditCards();

                List<AccountBalance> balances =
                        DatabaseClient.getInstance(activity)
                                .getAppDatabase()
                                .accountDao()
                                .getAccountBalances();

                Snapshot snapshot = buildSnapshot(recurring, cards, balances);
                activity.runOnUiThread(() -> {
                    if (version == requestVersion
                            && !activity.isFinishing()
                            && !activity.isDestroyed()) {
                        render(snapshot);
                    }
                });
            } catch (Exception exception) {
                activity.runOnUiThread(() -> showFailure(version));
            }
        }).start();
    }

    private void showLoading() {
        if (billContainer != null) {
            billContainer.removeAllViews();
            billContainer.addView(infoCard(
                    "Reviewing recurring expenses...",
                    "Upcoming expense schedules will appear here.",
                    R.color.info_surface,
                    R.color.info_outline,
                    R.color.secondary
            ));
        }
        if (cardDueContainer != null) {
            cardDueContainer.removeAllViews();
            cardDueContainer.addView(infoCard(
                    "Checking card due dates...",
                    "Active cards will appear here.",
                    R.color.purple_surface,
                    R.color.purple_outline,
                    R.color.purple
            ));
        }
    }

    private Snapshot buildSnapshot(
            List<RecurringTransaction> recurring,
            List<CreditCard> cards,
            List<AccountBalance> balances
    ) {
        double assets = 0d;
        double liabilities = 0d;
        Map<String, Double> balanceByName = new HashMap<>();

        if (balances != null) {
            for (AccountBalance balance : balances) {
                if (balance == null) continue;
                balanceByName.put(safe(balance.name).toLowerCase(Locale.US), balance.currentBalance);
                if (balance.currentBalance >= 0d) {
                    assets += balance.currentBalance;
                } else {
                    liabilities += Math.abs(balance.currentBalance);
                }
            }
        }

        List<BillItem> bills = new ArrayList<>();
        Date today = atMidnight(Calendar.getInstance()).getTime();
        Calendar horizonCalendar = atMidnight(Calendar.getInstance());
        horizonCalendar.add(Calendar.DAY_OF_MONTH, 30);
        Date horizon = horizonCalendar.getTime();

        if (recurring != null) {
            for (RecurringTransaction entry : recurring) {
                if (entry == null
                        || !entry.isActive()
                        || !"EXPENSE".equalsIgnoreCase(entry.getType())) {
                    continue;
                }
                Date date = parseIso(entry.getNextRunDate());
                if (date == null || date.before(today) || date.after(horizon)) {
                    continue;
                }
                bills.add(new BillItem(
                        safe(entry.getNote()).isEmpty()
                                ? safe(entry.getCategory())
                                : safe(entry.getNote()),
                        entry.getAmount(),
                        entry.getNextRunDate(),
                        daysBetween(today, date),
                        safe(entry.getFrequency())
                ));
            }
        }
        Collections.sort(bills, Comparator.comparingInt(item -> item.days));

        List<CardDueItem> cardDues = new ArrayList<>();
        if (cards != null) {
            for (CreditCard card : cards) {
                if (card == null || !card.isActive()) continue;
                CreditCardCycleCalculator.Cycle cycle =
                        CreditCardCycleCalculator.calculate(card, Calendar.getInstance());
                double balance = balanceByName.getOrDefault(
                        safe(card.getAccountName()).toLowerCase(Locale.US),
                        0d
                );
                double used = Math.max(0d, -balance);
                cardDues.add(new CardDueItem(
                        safe(card.getName()),
                        safe(card.getLastFour()),
                        cycle.dueDate,
                        cycle.daysUntilDue,
                        used
                ));
            }
        }
        Collections.sort(cardDues, Comparator.comparingInt(item -> item.days));
        return new Snapshot(assets, liabilities, bills, cardDues);
    }

    private void render(@NonNull Snapshot snapshot) {
        txtAssets.setText(money(snapshot.assets));
        txtLiabilities.setText(money(snapshot.liabilities));
        double netWorth = snapshot.assets - snapshot.liabilities;
        txtNetWorth.setText(signedMoney(netWorth));
        txtNetWorth.setTextColor(color(
                netWorth > 0.01d
                        ? R.color.success
                        : netWorth < -0.01d
                        ? R.color.expense
                        : R.color.secondary
        ));
        txtNetWorthStatus.setText(
                netWorth >= 0d
                        ? "Assets are above recorded liabilities by " + money(netWorth) + "."
                        : "Recorded liabilities are above assets by " + money(Math.abs(netWorth)) + "."
        );

        billContainer.removeAllViews();
        if (snapshot.bills.isEmpty()) {
            billContainer.addView(infoCard(
                    "No expense due in the next 30 days",
                    "Add or activate recurring expenses to receive this summary.",
                    R.color.success_surface,
                    R.color.success_outline,
                    R.color.success
            ));
        } else {
            int limit = Math.min(4, snapshot.bills.size());
            for (int i = 0; i < limit; i++) {
                BillItem item = snapshot.bills.get(i);
                billContainer.addView(infoCard(
                        item.title + "  •  " + money(item.amount),
                        dueLabel(item.days) + "  •  " + visibleDate(item.date)
                                + "  •  " + item.frequency,
                        item.days <= 3 ? R.color.error_surface : R.color.warning_surface,
                        item.days <= 3 ? R.color.error_outline : R.color.warning_outline,
                        item.days <= 3 ? R.color.expense : R.color.orange
                ));
            }
        }

        cardDueContainer.removeAllViews();
        if (snapshot.cardDues.isEmpty()) {
            cardDueContainer.addView(infoCard(
                    "No active credit card",
                    "Add a card to track due dates and utilization warnings.",
                    R.color.app_surface_soft,
                    R.color.app_outline,
                    R.color.app_text_secondary
            ));
        } else {
            int limit = Math.min(4, snapshot.cardDues.size());
            for (int i = 0; i < limit; i++) {
                CardDueItem item = snapshot.cardDues.get(i);
                int surface = item.days <= 3
                        ? R.color.error_surface
                        : item.days <= 7
                        ? R.color.warning_surface
                        : R.color.purple_surface;
                int outline = item.days <= 3
                        ? R.color.error_outline
                        : item.days <= 7
                        ? R.color.warning_outline
                        : R.color.purple_outline;
                int accent = item.days <= 3
                        ? R.color.expense
                        : item.days <= 7
                        ? R.color.orange
                        : R.color.purple;
                cardDueContainer.addView(infoCard(
                        item.name + " •••• " + item.lastFour,
                        dueLabel(item.days) + "  •  " + visibleDate(item.date)
                                + (item.used > 0d ? "  •  Used " + money(item.used) : ""),
                        surface,
                        outline,
                        accent
                ));
            }
        }
    }

    private void showFailure(int version) {
        if (version != requestVersion) return;
        billContainer.removeAllViews();
        billContainer.addView(infoCard(
                "Unable to load upcoming bills",
                "Existing data was not changed. Reopen Dashboard to retry.",
                R.color.error_surface,
                R.color.error_outline,
                R.color.expense
        ));
        cardDueContainer.removeAllViews();
        cardDueContainer.addView(infoCard(
                "Unable to load card due dates",
                "Existing card data remains safe.",
                R.color.error_surface,
                R.color.error_outline,
                R.color.expense
        ));
    }

    @NonNull
    private MaterialCardView infoCard(
            @NonNull String title,
            @NonNull String detail,
            @ColorRes int surface,
            @ColorRes int outline,
            @ColorRes int accent
    ) {
        MaterialCardView card = card(surface, outline);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(13), dp(11), dp(13), dp(11));
        content.addView(text(title, 12, accent, true));
        TextView detailView = text(detail, 10, R.color.app_text_secondary, false);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        detailParams.setMargins(0, dp(4), 0, 0);
        detailView.setLayoutParams(detailParams);
        content.addView(detailView);
        card.addView(content);
        return card;
    }

    private MaterialCardView card(
            @ColorRes int surface,
            @ColorRes int outline
    ) {
        MaterialCardView card = new MaterialCardView(activity);
        card.setCardBackgroundColor(color(surface));
        card.setStrokeColor(color(outline));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(17));
        card.setCardElevation(0f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(8));
        card.setLayoutParams(params);
        return card;
    }

    private MaterialButton button(@NonNull String label) {
        MaterialButton button = new MaterialButton(activity);
        button.setText(label);
        button.setTextSize(11);
        button.setAllCaps(false);
        button.setCornerRadius(dp(15));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        return button;
    }

    private TextView text(
            @NonNull String value,
            float size,
            @ColorRes int color,
            boolean bold
    ) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color(color));
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private void animatePanel(@NonNull View view) {
        view.setAlpha(0f);
        view.setTranslationY(dp(18));
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(420L)
                .start();
    }

    private String dueLabel(int days) {
        if (days < 0) return Math.abs(days) + " days overdue";
        if (days == 0) return "Due today";
        if (days == 1) return "Due tomorrow";
        return "Due in " + days + " days";
    }

    private String visibleDate(String iso) {
        Date date = parseIso(iso);
        if (date == null) return iso;
        return new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(date);
    }

    private Date parseIso(String value) {
        String clean = safe(value);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setLenient(false);
        ParsePosition position = new ParsePosition(0);
        Date parsed = format.parse(clean, position);
        return parsed != null && position.getIndex() == clean.length() ? parsed : null;
    }

    private Calendar atMidnight(Calendar source) {
        Calendar copy = (Calendar) source.clone();
        copy.set(Calendar.HOUR_OF_DAY, 0);
        copy.set(Calendar.MINUTE, 0);
        copy.set(Calendar.SECOND, 0);
        copy.set(Calendar.MILLISECOND, 0);
        return copy;
    }

    private int daysBetween(Date start, Date end) {
        return (int) ((end.getTime() - start.getTime()) / 86400000L);
    }

    private String money(double amount) {
        NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        format.setMaximumFractionDigits(0);
        return format.format(Math.abs(amount));
    }

    private String signedMoney(double amount) {
        if (amount > 0.01d) return "+" + money(amount);
        if (amount < -0.01d) return "-" + money(amount);
        return money(0d);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private int color(@ColorRes int resource) {
        return ContextCompat.getColor(activity, resource);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static final class Snapshot {
        final double assets;
        final double liabilities;
        final List<BillItem> bills;
        final List<CardDueItem> cardDues;

        Snapshot(double assets, double liabilities, List<BillItem> bills, List<CardDueItem> cardDues) {
            this.assets = assets;
            this.liabilities = liabilities;
            this.bills = bills;
            this.cardDues = cardDues;
        }
    }

    private static final class BillItem {
        final String title;
        final double amount;
        final String date;
        final int days;
        final String frequency;

        BillItem(String title, double amount, String date, int days, String frequency) {
            this.title = title;
            this.amount = amount;
            this.date = date;
            this.days = days;
            this.frequency = frequency;
        }
    }

    private static final class CardDueItem {
        final String name;
        final String lastFour;
        final String date;
        final int days;
        final double used;

        CardDueItem(String name, String lastFour, String date, int days, double used) {
            this.name = name;
            this.lastFour = lastFour;
            this.date = date;
            this.days = days;
            this.used = used;
        }
    }
}
