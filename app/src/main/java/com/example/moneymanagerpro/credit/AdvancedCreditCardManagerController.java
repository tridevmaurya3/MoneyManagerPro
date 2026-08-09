package com.example.moneymanagerpro.credit;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.moneymanagerpro.database.AppDatabase;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.AccountBalance;
import com.example.moneymanagerpro.model.CreditCard;
import com.google.android.material.card.MaterialCardView;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Adds a portfolio-level advanced credit-card dashboard without changing the
 * existing database schema or card editor. It calculates utilization, due-date
 * urgency, estimated minimum payment and an educational carried-balance
 * interest estimate locally on the device.
 */
public final class AdvancedCreditCardManagerController {

    private static final String PANEL_TAG = "advanced_credit_card_manager_panel";
    private static final double ESTIMATED_MONTHLY_INTEREST_RATE = 0.035d;

    private final Activity activity;
    private LinearLayout panel;
    private int requestVersion;

    public AdvancedCreditCardManagerController(@NonNull Activity activity) {
        this.activity = activity;
    }

    public void attach() {
        ViewGroup content = activity.findViewById(android.R.id.content);
        LinearLayout root = findVerticalLinearLayout(content);
        if (root == null) {
            return;
        }

        View existing = root.findViewWithTag(PANEL_TAG);
        if (existing instanceof LinearLayout) {
            panel = (LinearLayout) existing;
        } else {
            panel = new LinearLayout(activity);
            panel.setTag(PANEL_TAG);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(0, 0, 0, dp(8));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, dp(16), 0, dp(12));
            panel.setLayoutParams(params);

            int insertionIndex = Math.min(3, root.getChildCount());
            root.addView(panel, insertionIndex);
        }

        loadPortfolio();
    }

    private void loadPortfolio() {
        final int version = ++requestVersion;
        new Thread(() -> {
            AppDatabase database = DatabaseClient
                    .getInstance(activity.getApplicationContext())
                    .getAppDatabase();

            List<CreditCard> cards = database.creditCardDao().getActiveCreditCards();
            List<AccountBalance> balances = database.accountDao().getAccountBalances();

            Map<String, Double> balanceByAccount = new HashMap<>();
            if (balances != null) {
                for (AccountBalance balance : balances) {
                    if (balance != null && balance.name != null) {
                        balanceByAccount.put(balance.name.trim().toLowerCase(Locale.ROOT), balance.currentBalance);
                    }
                }
            }

            List<CardInsight> insights = new ArrayList<>();
            double totalLimit = 0d;
            double totalUsed = 0d;
            double totalMinimumDue = 0d;
            double estimatedInterest = 0d;
            int urgentCards = 0;

            if (cards != null) {
                for (CreditCard card : cards) {
                    if (card == null) continue;
                    double limit = Math.max(0d, card.getCreditLimit());
                    double accountBalance = balanceByAccount.getOrDefault(
                            safe(card.getAccountName()).toLowerCase(Locale.ROOT), 0d
                    );
                    double used = Math.max(0d, -accountBalance);
                    double utilization = limit <= 0d ? 0d : used / limit * 100d;
                    double minimumDue = used <= 0d ? 0d : Math.max(100d, used * 0.05d);
                    double interest = used * ESTIMATED_MONTHLY_INTEREST_RATE;
                    Calendar nextDue = nextDueDate(card.getDueDay());
                    int daysToDue = daysBetween(Calendar.getInstance(), nextDue);
                    if (used > 0d && daysToDue <= Math.max(3, card.getReminderDays())) urgentCards++;

                    insights.add(new CardInsight(
                            card, used, utilization, minimumDue, interest, nextDue, daysToDue
                    ));
                    totalLimit += limit;
                    totalUsed += used;
                    totalMinimumDue += minimumDue;
                    estimatedInterest += interest;
                }
            }

            PortfolioData data = new PortfolioData(
                    insights, totalLimit, totalUsed, totalMinimumDue, estimatedInterest, urgentCards
            );

            activity.runOnUiThread(() -> {
                if (version != requestVersion || activity.isFinishing() || activity.isDestroyed()) return;
                render(data);
            });
        }).start();
    }

    private void render(PortfolioData data) {
        panel.removeAllViews();

        TextView title = text("Advanced Credit Card Manager", 20, Color.parseColor("#1D2939"), true);
        panel.addView(title);

        TextView subtitle = text(
                "Portfolio utilization, due urgency, minimum-payment estimate and carried-balance cost",
                11,
                Color.parseColor("#667085"),
                false
        );
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.setMargins(0, dp(4), 0, dp(12));
        subtitle.setLayoutParams(subtitleParams);
        panel.addView(subtitle);

        if (data.insights.isEmpty()) {
            panel.addView(messageCard(
                    "No active credit card",
                    "Add a card below to activate utilization, due-date and payment intelligence.",
                    "#F7F9FC",
                    "#D9E2EC",
                    "#475467"
            ));
            animatePanel();
            return;
        }

        double totalUtilization = data.totalLimit <= 0d ? 0d : data.totalUsed / data.totalLimit * 100d;
        int riskColor = utilizationColor(totalUtilization);

        MaterialCardView overviewCard = card("#F4F8FF", "#C8DCF2", 20);
        LinearLayout overview = new LinearLayout(activity);
        overview.setOrientation(LinearLayout.VERTICAL);
        overview.setPadding(dp(15), dp(14), dp(15), dp(14));

        LinearLayout metricRow = new LinearLayout(activity);
        metricRow.setOrientation(LinearLayout.HORIZONTAL);
        metricRow.setBaselineAligned(false);
        metricRow.addView(metric("Total Limit", money(data.totalLimit), "#0F6CBD"));
        metricRow.addView(metric("Total Used", money(data.totalUsed), colorHex(riskColor)));
        overview.addView(metricRow);

        ProgressBar utilizationBar = horizontalProgress(riskColor);
        int utilizationTarget = (int) Math.min(1000d, Math.max(0d, totalUtilization * 10d));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(9)
        );
        barParams.setMargins(0, dp(12), 0, 0);
        utilizationBar.setLayoutParams(barParams);
        overview.addView(utilizationBar);

        TextView utilizationText = text(
                Math.round(totalUtilization) + "% portfolio utilization • " + utilizationStatus(totalUtilization),
                11,
                riskColor,
                true
        );
        LinearLayout.LayoutParams utilizationTextParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        utilizationTextParams.setMargins(0, dp(6), 0, 0);
        utilizationText.setLayoutParams(utilizationTextParams);
        overview.addView(utilizationText);

        LinearLayout forecastRow = new LinearLayout(activity);
        forecastRow.setOrientation(LinearLayout.HORIZONTAL);
        forecastRow.setBaselineAligned(false);
        LinearLayout.LayoutParams forecastParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        forecastParams.setMargins(0, dp(12), 0, 0);
        forecastRow.setLayoutParams(forecastParams);
        forecastRow.addView(metric("Est. Minimum Due", money(data.totalMinimumDue), "#8764B8"));
        forecastRow.addView(metric("Est. Monthly Interest", money(data.estimatedInterest), "#D83B01"));
        overview.addView(forecastRow);

        TextView disclaimer = text(
                "Interest uses an educational 3.5% monthly estimate. Your bank statement remains the final source.",
                9,
                Color.parseColor("#667085"),
                false
        );
        LinearLayout.LayoutParams disclaimerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        disclaimerParams.setMargins(0, dp(9), 0, 0);
        disclaimer.setLayoutParams(disclaimerParams);
        overview.addView(disclaimer);
        overviewCard.addView(overview);
        panel.addView(overviewCard);

        utilizationBar.post(() -> android.animation.ObjectAnimator
                .ofInt(utilizationBar, "progress", 0, utilizationTarget)
                .setDuration(700L)
                .start());

        TextView cardsTitle = text("Card Risk & Due Summary", 16, Color.parseColor("#1D2939"), true);
        LinearLayout.LayoutParams cardsTitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardsTitleParams.setMargins(0, dp(18), 0, dp(8));
        cardsTitle.setLayoutParams(cardsTitleParams);
        panel.addView(cardsTitle);

        for (int index = 0; index < data.insights.size(); index++) {
            panel.addView(cardInsightView(data.insights.get(index), index));
        }

        animatePanel();
    }

    private View cardInsightView(CardInsight insight, int index) {
        int accent = utilizationColor(insight.utilization);
        String surface = insight.daysToDue <= Math.max(3, insight.card.getReminderDays()) && insight.used > 0d
                ? "#FFF5F3" : "#FFFFFF";
        String outline = insight.daysToDue <= Math.max(3, insight.card.getReminderDays()) && insight.used > 0d
                ? "#F2C4BC" : "#E4EAF0";

        MaterialCardView card = card(surface, outline, 17);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dp(9));
        card.setLayoutParams(cardParams);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(12), dp(14), dp(13));

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(
                insight.card.getName() + " •••• " + insight.card.getLastFour(),
                14,
                Color.parseColor("#1D2939"),
                true
        );
        header.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView utilization = badge(Math.round(insight.utilization) + "% used", accent);
        header.addView(utilization);
        content.addView(header);

        ProgressBar bar = horizontalProgress(accent);
        int target = (int) Math.min(1000d, Math.max(0d, insight.utilization * 10d));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(8)
        );
        progressParams.setMargins(0, dp(9), 0, 0);
        bar.setLayoutParams(progressParams);
        content.addView(bar);

        String dueLabel = insight.used <= 0d
                ? "No outstanding amount"
                : insight.daysToDue == 0
                ? "Due today"
                : "Due in " + insight.daysToDue + " days • "
                + new SimpleDateFormat("dd MMM", Locale.ENGLISH).format(insight.nextDue.getTime());

        TextView due = text(dueLabel, 11,
                insight.daysToDue <= Math.max(3, insight.card.getReminderDays()) && insight.used > 0d
                        ? Color.parseColor("#C42B1C") : Color.parseColor("#667085"),
                insight.used > 0d);
        LinearLayout.LayoutParams dueParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        dueParams.setMargins(0, dp(7), 0, 0);
        due.setLayoutParams(dueParams);
        content.addView(due);

        TextView details = text(
                "Outstanding " + money(insight.used)
                        + " • Est. minimum " + money(insight.minimumDue)
                        + " • Payment from " + safe(insight.card.getPaymentAccount()),
                10,
                Color.parseColor("#667085"),
                false
        );
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        detailsParams.setMargins(0, dp(4), 0, 0);
        details.setLayoutParams(detailsParams);
        content.addView(details);

        card.addView(content);
        card.setAlpha(0f);
        card.setTranslationY(dp(18));
        card.animate().alpha(1f).translationY(0f).setStartDelay(index * 65L).setDuration(320L).start();
        bar.post(() -> android.animation.ObjectAnimator.ofInt(bar, "progress", 0, target)
                .setDuration(650L).start());
        return card;
    }

    private View metric(String label, String value, String accentHex) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(5), dp(3), dp(5), dp(3));
        box.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(text(label, 10, Color.parseColor("#667085"), true));
        TextView amount = text(value, 17, Color.parseColor(accentHex), true);
        LinearLayout.LayoutParams amountParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        amountParams.setMargins(0, dp(3), 0, 0);
        amount.setLayoutParams(amountParams);
        box.addView(amount);
        return box;
    }

    private TextView badge(String value, int accent) {
        TextView badge = text(value, 10, accent, true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(9), dp(5), dp(9), dp(5));
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(withAlpha(accent, 22));
        background.setStroke(dp(1), withAlpha(accent, 80));
        background.setCornerRadius(dp(12));
        badge.setBackground(background);
        return badge;
    }

    private ProgressBar horizontalProgress(int color) {
        ProgressBar progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setProgress(0);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(color));
        progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#E8EDF3")));
        return progress;
    }

    private MaterialCardView messageCard(String title, String message, String background, String stroke, String accent) {
        MaterialCardView card = card(background, stroke, 17);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(12), dp(14), dp(12));
        content.addView(text(title, 14, Color.parseColor(accent), true));
        TextView body = text(message, 11, Color.parseColor("#667085"), false);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        bodyParams.setMargins(0, dp(4), 0, 0);
        body.setLayoutParams(bodyParams);
        content.addView(body);
        card.addView(content);
        return card;
    }

    private MaterialCardView card(String background, String stroke, int radiusDp) {
        MaterialCardView card = new MaterialCardView(activity);
        card.setCardBackgroundColor(Color.parseColor(background));
        card.setStrokeColor(Color.parseColor(stroke));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(radiusDp));
        card.setCardElevation(0f);
        card.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return card;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(dp(2), 1f);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private void animatePanel() {
        panel.setAlpha(0f);
        panel.setTranslationY(dp(20));
        panel.animate().alpha(1f).translationY(0f).setDuration(400L).start();
    }

    private int utilizationColor(double utilization) {
        if (utilization >= 70d) return Color.parseColor("#C42B1C");
        if (utilization >= 30d) return Color.parseColor("#D83B01");
        return Color.parseColor("#107C10");
    }

    private String utilizationStatus(double utilization) {
        if (utilization >= 70d) return "High risk";
        if (utilization >= 30d) return "Moderate usage";
        return "Healthy usage";
    }

    private Calendar nextDueDate(int requestedDay) {
        Calendar now = Calendar.getInstance();
        Calendar due = (Calendar) now.clone();
        int day = Math.max(1, requestedDay);
        day = Math.min(day, due.getActualMaximum(Calendar.DAY_OF_MONTH));
        due.set(Calendar.DAY_OF_MONTH, day);
        due.set(Calendar.HOUR_OF_DAY, 23);
        due.set(Calendar.MINUTE, 59);
        due.set(Calendar.SECOND, 59);
        due.set(Calendar.MILLISECOND, 0);
        if (due.before(now)) {
            due.add(Calendar.MONTH, 1);
            due.set(Calendar.DAY_OF_MONTH, Math.min(Math.max(1, requestedDay), due.getActualMaximum(Calendar.DAY_OF_MONTH)));
        }
        return due;
    }

    private int daysBetween(Calendar from, Calendar to) {
        long difference = Math.max(0L, to.getTimeInMillis() - from.getTimeInMillis());
        return (int) Math.ceil(difference / 86400000d);
    }

    private LinearLayout findVerticalLinearLayout(View view) {
        if (view instanceof LinearLayout
                && ((LinearLayout) view).getOrientation() == LinearLayout.VERTICAL) {
            return (LinearLayout) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                LinearLayout found = findVerticalLinearLayout(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "Cash" : value.trim();
    }

    private String money(double amount) {
        NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        format.setMaximumFractionDigits(0);
        return format.format(Math.abs(amount));
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(color), Color.green(color), Color.blue(color));
    }

    private String colorHex(int color) {
        return String.format(Locale.US, "#%06X", 0xFFFFFF & color);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static final class CardInsight {
        final CreditCard card;
        final double used;
        final double utilization;
        final double minimumDue;
        final double interest;
        final Calendar nextDue;
        final int daysToDue;

        CardInsight(CreditCard card, double used, double utilization, double minimumDue,
                    double interest, Calendar nextDue, int daysToDue) {
            this.card = card;
            this.used = used;
            this.utilization = utilization;
            this.minimumDue = minimumDue;
            this.interest = interest;
            this.nextDue = nextDue;
            this.daysToDue = daysToDue;
        }
    }

    private static final class PortfolioData {
        final List<CardInsight> insights;
        final double totalLimit;
        final double totalUsed;
        final double totalMinimumDue;
        final double estimatedInterest;
        final int urgentCards;

        PortfolioData(List<CardInsight> insights, double totalLimit, double totalUsed,
                      double totalMinimumDue, double estimatedInterest, int urgentCards) {
            this.insights = insights;
            this.totalLimit = totalLimit;
            this.totalUsed = totalUsed;
            this.totalMinimumDue = totalMinimumDue;
            this.estimatedInterest = estimatedInterest;
            this.urgentCards = urgentCards;
        }
    }
}
