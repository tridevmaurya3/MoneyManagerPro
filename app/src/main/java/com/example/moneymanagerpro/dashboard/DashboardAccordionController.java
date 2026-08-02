package com.example.moneymanagerpro.dashboard;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.activities.DashboardActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the permanent dashboard summary visible while placing the long feature
 * panels behind a compact, single-open accordion selector.
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

        View existingHub = ((ViewGroup) root).findViewWithTag(HUB_TAG);
        if (existingHub != null) {
            refreshSectionReferences((ViewGroup) root);
            return;
        }

        dashboardColumn = findDashboardColumn((ViewGroup) root);
        View smart = ((ViewGroup) root).findViewWithTag(SMART_TAG);
        View trends = ((ViewGroup) root).findViewWithTag(TRENDS_TAG);
        View obligations = ((ViewGroup) root).findViewWithTag(OBLIGATIONS_TAG);
        View goalDebt = ((ViewGroup) root).findViewWithTag(GOAL_DEBT_TAG);

        if (dashboardColumn == null || smart == null || trends == null
                || obligations == null || goalDebt == null) {
            if (attachAttempt++ < 15) {
                root.postDelayed(this::attach, 160L);
            }
            return;
        }

        View quickActions = findQuickActionsBlock(dashboardColumn);
        sections.clear();
        if (quickActions != null) {
            sections.add(new Section("Quick Actions", "Add or manage money", quickActions, "⚡"));
        }
        sections.add(new Section("Smart Overview", "Cash flow and budget health", smart, "◈"));
        sections.add(new Section("Trends", "Charts and category spending", trends, "↗"));
        sections.add(new Section("Bills & Net Worth", "Upcoming dues and position", obligations, "₹"));
        sections.add(new Section("Goal & Debt", "Payoff and savings planner", goalDebt, "◎"));

        normalizeGoalDebtCard(goalDebt);
        moveQuickActionsBeforeSmartPanel(quickActions, smart);
        addHubBeforeFirstSection();
        closeAll(false);
    }

    private void refreshSectionReferences(@NonNull ViewGroup root) {
        for (Section section : sections) {
            if (section.content != null && section.content.getParent() != null) continue;
            if (section.tag != null) section.content = root.findViewWithTag(section.tag);
        }
    }

    private void addHubBeforeFirstSection() {
        if (dashboardColumn == null || sections.isEmpty()) return;

        int insertionIndex = dashboardColumn.getChildCount();
        for (Section section : sections) {
            if (section.content != null && section.content.getParent() == dashboardColumn) {
                insertionIndex = Math.min(insertionIndex, dashboardColumn.indexOfChild(section.content));
            }
        }

        LinearLayout hub = new LinearLayout(activity);
        hub.setTag(HUB_TAG);
        hub.setOrientation(LinearLayout.VERTICAL);
        hub.setPadding(0, dp(4), 0, dp(4));
        LinearLayout.LayoutParams hubParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        hubParams.setMargins(0, dp(14), 0, dp(4));
        hub.setLayoutParams(hubParams);

        TextView title = text("Dashboard Tools", 18, R.color.app_text_primary, true);
        hub.addView(title);
        TextView subtitle = text(
                "Tap one section to open it. Opening another section automatically closes the previous one.",
                10,
                R.color.app_text_secondary,
                false
        );
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.setMargins(0, dp(2), 0, dp(8));
        subtitle.setLayoutParams(subtitleParams);
        hub.addView(subtitle);

        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(false);
        LinearLayout buttonRow = new LinearLayout(activity);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setPadding(0, 0, dp(4), 0);

        for (Section section : sections) {
            MaterialButton button = new MaterialButton(activity);
            section.button = button;
            button.setText(section.icon + "  " + section.title);
            button.setAllCaps(false);
            button.setTextSize(11);
            button.setMinHeight(0);
            button.setMinimumHeight(0);
            button.setInsetTop(0);
            button.setInsetBottom(0);
            button.setCornerRadius(dp(14));
            button.setPadding(dp(13), 0, dp(13), 0);
            button.setStrokeWidth(dp(1));
            button.setOnClickListener(view -> toggle(section));
            LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(42)
            );
            buttonParams.setMargins(0, 0, dp(7), 0);
            buttonRow.addView(button, buttonParams);
            applyButtonState(section, false);
        }

        scroll.addView(buttonRow);
        hub.addView(scroll);
        dashboardColumn.addView(hub, Math.max(0, insertionIndex));
    }

    private void toggle(@NonNull Section target) {
        if (openSection == target && target.content != null
                && target.content.getVisibility() == View.VISIBLE) {
            closeAll(true);
            return;
        }

        for (Section section : sections) {
            boolean shouldOpen = section == target;
            setSectionVisible(section, shouldOpen, true);
        }
        openSection = target;
    }

    private void closeAll(boolean animate) {
        for (Section section : sections) {
            setSectionVisible(section, false, animate);
        }
        openSection = null;
    }

    private void setSectionVisible(
            @NonNull Section section,
            boolean visible,
            boolean animate
    ) {
        View content = section.content;
        if (content == null) return;
        content.animate().cancel();

        if (visible) {
            content.setVisibility(View.VISIBLE);
            if (animate) {
                content.setAlpha(0f);
                content.setTranslationY(-dp(8));
                content.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(210L)
                        .start();
            } else {
                content.setAlpha(1f);
                content.setTranslationY(0f);
            }
        } else {
            content.setVisibility(View.GONE);
            content.setAlpha(1f);
            content.setTranslationY(0f);
        }
        applyButtonState(section, visible);
    }

    private void applyButtonState(@NonNull Section section, boolean selected) {
        if (section.button == null) return;
        int background = selected ? color(R.color.secondary) : Color.parseColor("#F7FAF8");
        int foreground = selected ? Color.WHITE : color(R.color.secondary);
        int stroke = selected ? color(R.color.secondary) : Color.parseColor("#C9D8CE");
        section.button.setBackgroundTintList(ColorStateList.valueOf(background));
        section.button.setTextColor(foreground);
        section.button.setStrokeColor(ColorStateList.valueOf(stroke));
    }

    private void moveQuickActionsBeforeSmartPanel(View quickActions, View smartPanel) {
        if (quickActions == null || smartPanel == null || dashboardColumn == null) return;
        if (quickActions.getParent() != dashboardColumn || smartPanel.getParent() != dashboardColumn) return;
        int smartIndex = dashboardColumn.indexOfChild(smartPanel);
        int quickIndex = dashboardColumn.indexOfChild(quickActions);
        if (quickIndex > smartIndex) {
            dashboardColumn.removeView(quickActions);
            dashboardColumn.addView(quickActions, Math.max(0, smartIndex));
        }
    }

    private void normalizeGoalDebtCard(@NonNull View goalDebt) {
        if (goalDebt.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) goalDebt.getLayoutParams();
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.setMargins(0, dp(8), 0, dp(12));
            goalDebt.setLayoutParams(params);
        }
        if (goalDebt instanceof MaterialCardView) {
            MaterialCardView card = (MaterialCardView) goalDebt;
            card.setRadius(dp(18));
            card.setCardElevation(dp(1));
        }
        goalDebt.setMinimumHeight(dp(150));
    }

    private View findQuickActionsBlock(@NonNull LinearLayout column) {
        View addIncome = activity.findViewById(R.id.btnAddIncome);
        View moreFeatures = activity.findViewById(R.id.btnMoreFeatures);
        if (addIncome == null) return null;

        View direct = directChildOf(column, addIncome);
        View directMore = moreFeatures == null ? null : directChildOf(column, moreFeatures);
        if (direct != null && direct == directMore) return direct;

        View common = lowestCommonAncestor(addIncome, moreFeatures);
        if (common != null) {
            View directCommon = directChildOf(column, common);
            if (directCommon != null) return directCommon;
        }
        return direct;
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

    private View lowestCommonAncestor(View first, View second) {
        if (first == null || second == null) return null;
        List<View> ancestors = new ArrayList<>();
        View current = first;
        while (current != null) {
            ancestors.add(current);
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        current = second;
        while (current != null) {
            if (ancestors.contains(current)) return current;
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        return null;
    }

    private LinearLayout findDashboardColumn(@NonNull ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout layout = (LinearLayout) child;
                if (layout.getOrientation() == LinearLayout.VERTICAL
                        && layout.findViewWithTag(SMART_TAG) != null) {
                    return layout;
                }
            }
            if (child instanceof ViewGroup) {
                LinearLayout found = findDashboardColumn((ViewGroup) child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private TextView text(String value, int size, int colorRes, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color(colorRes));
        view.setGravity(Gravity.START);
        if (bold) view.setTypeface(view.getTypeface(), Typeface.BOLD);
        return view;
    }

    private int color(int resId) {
        return ContextCompat.getColor(activity, resId);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static final class Section {
        final String title;
        final String subtitle;
        View content;
        final String icon;
        final String tag;
        MaterialButton button;

        Section(String title, String subtitle, View content, String icon) {
            this.title = title;
            this.subtitle = subtitle;
            this.content = content;
            this.icon = icon;
            this.tag = content != null && content.getTag() instanceof String
                    ? (String) content.getTag() : null;
        }
    }
}
