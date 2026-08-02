package com.example.moneymanagerpro.dashboard;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.activities.CalendarActivity;
import com.example.moneymanagerpro.activities.DashboardActivity;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the original Dashboard summary and Quick Actions visible, then places
 * every long intelligence panel directly below its own expandable row.
 */
public final class DashboardAccordionController {

    private static final String HUB_TAG = "dashboard_compact_section_hub";
    private static final String SMART_TAG = "money_manager_smart_dashboard_2_panel";
    private static final String TRENDS_TAG = "advanced_dashboard_insights_panel";
    private static final String OBLIGATIONS_TAG = "money_manager_dashboard_obligations_panel";
    private static final String GOAL_DEBT_TAG = "smart_goal_debt_dashboard_card";

    private final Activity activity;
    private final List<Section> sections = new ArrayList<>();
    private LinearLayout dashboardColumn;
    private LinearLayout hub;
    private int attachAttempt;
    private Section openSection;

    public DashboardAccordionController(@NonNull Activity activity) {
        this.activity = activity;
    }

    public void attach() {
        if (!(activity instanceof DashboardActivity)
                || activity.isFinishing()
                || activity.isDestroyed()) {
            return;
        }

        View root = activity.findViewById(android.R.id.content);
        if (!(root instanceof ViewGroup)) return;

        View existing = ((ViewGroup) root).findViewWithTag(HUB_TAG);
        if (existing instanceof LinearLayout) {
            hub = (LinearLayout) existing;
            return;
        }

        dashboardColumn = findDashboardColumn((ViewGroup) root);
        View smart = ((ViewGroup) root).findViewWithTag(SMART_TAG);
        View trends = ((ViewGroup) root).findViewWithTag(TRENDS_TAG);
        View obligations = ((ViewGroup) root).findViewWithTag(OBLIGATIONS_TAG);
        View goalDebt = ((ViewGroup) root).findViewWithTag(GOAL_DEBT_TAG);

        if (dashboardColumn == null || smart == null || trends == null
                || obligations == null || goalDebt == null) {
            if (attachAttempt++ < 18) root.postDelayed(this::attach, 160L);
            return;
        }

        normalizePanel(smart);
        normalizePanel(trends);
        normalizePanel(obligations);
        normalizeGoalDebtCard(goalDebt);

        sections.clear();
        sections.add(new Section("Smart Overview", "Cash flow and budget health", "▥", smart,
                R.color.success, R.color.success_surface, R.color.success_outline));
        sections.add(new Section("Trends & Insights", "Cash-flow chart and category spending", "↗", trends,
                R.color.secondary, R.color.info_surface, R.color.info_outline));
        sections.add(new Section("Bills & Net Worth", "Upcoming dues and financial position", "▣", obligations,
                R.color.expense, R.color.error_surface, R.color.error_outline));
        sections.add(new Section("Goal & Debt Planner", "Debt payoff and savings forecasts", "◎", goalDebt,
                R.color.purple, R.color.purple_surface, R.color.purple_outline));
        sections.add(new Section("Calendar & Alerts", "Transactions, EMI, card dues and deadlines", "▦",
                buildCalendarLauncher(), R.color.expense, R.color.error_surface, R.color.error_outline));

        buildHubAfterMoreFinancialTools();
        closeAll(false);
    }

    private void buildHubAfterMoreFinancialTools() {
        if (dashboardColumn == null) return;

        hub = new LinearLayout(activity);
        hub.setTag(HUB_TAG);
        hub.setOrientation(LinearLayout.VERTICAL);
        hub.setPadding(0, dp(2), 0, dp(8));
        LinearLayout.LayoutParams hubParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        hubParams.setMargins(0, dp(14), 0, dp(6));
        hub.setLayoutParams(hubParams);

        hub.addView(text("Dashboard Tools", 19, R.color.app_text_primary, true));
        TextView subtitle = text("Tap a section below to expand it", 10, R.color.app_text_secondary, false);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.setMargins(0, dp(2), 0, dp(8));
        subtitle.setLayoutParams(subtitleParams);
        hub.addView(subtitle);

        for (Section section : sections) {
            detach(section.content);
            section.header = buildSectionHeader(section);
            hub.addView(section.header);
            hub.addView(section.content);
        }

        int insertionIndex = dashboardColumn.getChildCount();
        View moreTools = activity.findViewById(R.id.btnMoreFeatures);
        View moreToolsBlock = moreTools == null ? null : directChildOf(dashboardColumn, moreTools);
        if (moreToolsBlock != null) {
            insertionIndex = dashboardColumn.indexOfChild(moreToolsBlock) + 1;
        } else {
            for (Section section : sections) {
                if (section.content.getParent() == dashboardColumn) {
                    insertionIndex = Math.min(insertionIndex, dashboardColumn.indexOfChild(section.content));
                }
            }
        }
        dashboardColumn.addView(hub, Math.max(0, Math.min(insertionIndex, dashboardColumn.getChildCount())));
    }

    @NonNull
    private MaterialCardView buildSectionHeader(@NonNull Section section) {
        MaterialCardView card = new MaterialCardView(activity);
        card.setCardBackgroundColor(Color.WHITE);
        card.setStrokeColor(color(section.outlineColor));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(14));
        card.setCardElevation(0f);
        card.setClickable(true);
        card.setFocusable(true);
        card.setRippleColor(ColorStateList.valueOf(withAlpha(color(section.accentColor), 32)));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, dp(54));
        cardParams.setMargins(0, 0, 0, dp(6));
        card.setLayoutParams(cardParams);

        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(6), dp(12), dp(6));

        TextView icon = text(section.icon, 16, section.accentColor, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackgroundTintList(ColorStateList.valueOf(color(section.surfaceColor)));
        row.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1f);
        labelParams.setMargins(dp(10), 0, dp(8), 0);
        labels.setLayoutParams(labelParams);
        labels.addView(text(section.title, 13, R.color.app_text_primary, true));
        labels.addView(text(section.subtitle, 9, R.color.app_text_secondary, false));
        row.addView(labels);

        section.arrow = text("⌄", 21, R.color.app_text_secondary, true);
        section.arrow.setGravity(Gravity.CENTER);
        row.addView(section.arrow, new LinearLayout.LayoutParams(dp(30), dp(36)));
        card.addView(row);
        card.setOnClickListener(v -> toggle(section));
        BubbleTouchAnimator.apply(card);
        return card;
    }

    @NonNull
    private View buildCalendarLauncher() {
        MaterialCardView card = new MaterialCardView(activity);
        card.setCardBackgroundColor(color(R.color.error_surface));
        card.setStrokeColor(color(R.color.error_outline));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(16));
        card.setCardElevation(0f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(8));
        card.setLayoutParams(params);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(13), dp(14), dp(13));
        content.addView(text("Unified Finance Calendar", 15, R.color.expense, true));
        TextView detail = text(
                "View income, expenses, recurring bills, EMI, credit-card dues, goals and the next 30-day alert center.",
                10, R.color.app_text_secondary, false
        );
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, -2);
        detailParams.setMargins(0, dp(4), 0, dp(10));
        detail.setLayoutParams(detailParams);
        content.addView(detail);

        MaterialButton open = new MaterialButton(activity);
        open.setText("Open Calendar & Alerts");
        open.setAllCaps(false);
        open.setTextSize(12);
        open.setCornerRadius(dp(14));
        open.setOnClickListener(v -> activity.startActivity(new Intent(activity, CalendarActivity.class)));
        BubbleTouchAnimator.apply(open);
        content.addView(open, new LinearLayout.LayoutParams(-1, dp(48)));
        card.addView(content);
        return card;
    }

    private void toggle(@NonNull Section target) {
        boolean alreadyOpen = openSection == target && target.content.getVisibility() == View.VISIBLE;
        for (Section section : sections) setSectionVisible(section, !alreadyOpen && section == target, true);
        openSection = alreadyOpen ? null : target;
    }

    private void closeAll(boolean animate) {
        for (Section section : sections) setSectionVisible(section, false, animate);
        openSection = null;
    }

    private void setSectionVisible(@NonNull Section section, boolean visible, boolean animate) {
        View content = section.content;
        content.animate().cancel();
        if (visible) {
            content.setVisibility(View.VISIBLE);
            if (animate) {
                content.setAlpha(0f);
                content.setTranslationY(-dp(8));
                content.animate().alpha(1f).translationY(0f).setDuration(210L).start();
            } else {
                content.setAlpha(1f);
                content.setTranslationY(0f);
            }
        } else {
            content.setVisibility(View.GONE);
            content.setAlpha(1f);
            content.setTranslationY(0f);
        }
        if (section.arrow != null) {
            section.arrow.setText(visible ? "⌃" : "⌄");
            section.arrow.setTextColor(color(visible ? section.accentColor : R.color.app_text_secondary));
        }
        if (section.header != null) {
            section.header.setCardBackgroundColor(color(visible ? section.surfaceColor : R.color.app_surface));
            section.header.setStrokeColor(color(visible ? section.accentColor : section.outlineColor));
        }
    }

    private void normalizePanel(@NonNull View panel) {
        if (panel.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) panel.getLayoutParams();
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.setMargins(0, 0, 0, dp(8));
            panel.setLayoutParams(params);
        }
    }

    private void normalizeGoalDebtCard(@NonNull View goalDebt) {
        normalizePanel(goalDebt);
        goalDebt.setMinimumHeight(dp(150));
        if (goalDebt instanceof MaterialCardView) {
            MaterialCardView card = (MaterialCardView) goalDebt;
            card.setRadius(dp(18));
            card.setCardElevation(dp(1));
        }
    }

    private void detach(@NonNull View view) {
        if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
    }

    private View directChildOf(@NonNull ViewGroup parent, @NonNull View descendant) {
        View current = descendant;
        while (current.getParent() instanceof View) {
            View parentView = (View) current.getParent();
            if (parentView == parent) return current;
            current = parentView;
        }
        return null;
    }

    private LinearLayout findDashboardColumn(@NonNull ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout layout = (LinearLayout) child;
                if (layout.getOrientation() == LinearLayout.VERTICAL
                        && layout.findViewWithTag(SMART_TAG) != null) return layout;
            }
            if (child instanceof ViewGroup) {
                LinearLayout found = findDashboardColumn((ViewGroup) child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private TextView text(String value, float size, int colorRes, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color(colorRes));
        view.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private int color(int resource) {
        return ContextCompat.getColor(activity, resource);
    }

    private int withAlpha(int base, int alpha) {
        return Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base));
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static final class Section {
        final String title;
        final String subtitle;
        final String icon;
        final View content;
        final int accentColor;
        final int surfaceColor;
        final int outlineColor;
        MaterialCardView header;
        TextView arrow;

        Section(String title, String subtitle, String icon, View content,
                int accentColor, int surfaceColor, int outlineColor) {
            this.title = title;
            this.subtitle = subtitle;
            this.icon = icon;
            this.content = content;
            this.accentColor = accentColor;
            this.surfaceColor = surfaceColor;
            this.outlineColor = outlineColor;
        }
    }
}
