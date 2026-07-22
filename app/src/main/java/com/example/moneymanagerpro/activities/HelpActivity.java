package com.example.moneymanagerpro.activities;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

public class HelpActivity extends AppCompatActivity {

    private static final String STATE_HINDI_SELECTED =
            "help_hindi_selected";

    private MaterialButton btnHindi;
    private MaterialButton btnEnglish;

    private LinearLayout guideContainer;

    private boolean isHindiSelected = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);

        if (savedInstanceState != null) {
            isHindiSelected =
                    savedInstanceState.getBoolean(
                            STATE_HINDI_SELECTED,
                            true
                    );
        }

        bindViews();
        setupActions();
        updateLanguage();
    }

    @Override
    protected void onSaveInstanceState(
            @NonNull Bundle outState
    ) {
        outState.putBoolean(
                STATE_HINDI_SELECTED,
                isHindiSelected
        );

        super.onSaveInstanceState(outState);
    }

    private void bindViews() {
        TextView btnBack =
                findViewById(R.id.btnBack);

        btnHindi =
                findViewById(R.id.btnHindi);

        btnEnglish =
                findViewById(R.id.btnEnglish);

        guideContainer =
                findViewById(R.id.guideContainer);

        btnBack.setOnClickListener(
                view -> finish()
        );

        BubbleTouchAnimator.apply(btnBack);
    }

    private void setupActions() {
        btnHindi.setOnClickListener(view -> {
            if (isHindiSelected) {
                return;
            }

            isHindiSelected = true;
            updateLanguage();
        });

        btnEnglish.setOnClickListener(view -> {
            if (!isHindiSelected) {
                return;
            }

            isHindiSelected = false;
            updateLanguage();
        });

        BubbleTouchAnimator.apply(btnHindi);
        BubbleTouchAnimator.apply(btnEnglish);
    }

    private void updateLanguage() {
        styleLanguageButton(
                btnHindi,
                isHindiSelected,
                true
        );

        styleLanguageButton(
                btnEnglish,
                !isHindiSelected,
                false
        );

        guideContainer.removeAllViews();

        if (isHindiSelected) {
            showHindiGuides();
        } else {
            showEnglishGuides();
        }
    }

    private void styleLanguageButton(
            MaterialButton button,
            boolean selected,
            boolean hindiButton
    ) {
        int selectedBackground =
                getColorValue(R.color.secondary);

        int selectedText =
                getColorValue(R.color.white);

        int unselectedBackground =
                getColorValue(R.color.app_surface);

        int unselectedText =
                getColorValue(R.color.app_text_primary);

        int unselectedOutline =
                getColorValue(R.color.app_outline);

        button.setBackgroundTintList(
                ColorStateList.valueOf(
                        selected
                                ? selectedBackground
                                : unselectedBackground
                )
        );

        button.setTextColor(
                selected
                        ? selectedText
                        : unselectedText
        );

        button.setStrokeColor(
                ColorStateList.valueOf(
                        selected
                                ? selectedBackground
                                : unselectedOutline
                )
        );

        button.setStrokeWidth(dp(1));
        button.setCornerRadius(dp(16));
        button.setAllCaps(false);
        button.setAlpha(selected ? 1f : 0.92f);

        if (hindiButton) {
            button.setText(
                    selected
                            ? "✓  हिन्दी"
                            : "हिन्दी"
            );
        } else {
            button.setText(
                    selected
                            ? "✓  English"
                            : "English"
            );
        }
    }

    private void showHindiGuides() {
        addGuideCard(
                "01",
                "Dashboard",
                "ऐप खुलते ही कुल Balance, Income, Expense और Cash in Hand दिखाई देता है।",
                "Balance card दबाने पर Accounts screen खुलती है। Quick Actions से Income, Expense, Reports तथा अन्य जरूरी tools जल्दी खोले जा सकते हैं।",
                GuideTone.INFO
        );

        addGuideCard(
                "02",
                "Income जोड़ना",
                "Add Income में राशि, category और account चुनकर income save करें।",
                "Salary, Business, Gift या अन्य income दर्ज की जा सकती है। Save करने के बाद account balance और Dashboard income अपने-आप update होती है।",
                GuideTone.SUCCESS
        );

        addGuideCard(
                "03",
                "Expense जोड़ना",
                "Add Expense में खर्च की राशि, category, account और तारीख चुनें।",
                "जरूरत होने पर bill या receipt की photo भी जोड़ी जा सकती है। Save किया गया खर्च Dashboard, Transactions और Reports में दिखाई देगा।",
                GuideTone.EXPENSE
        );

        addGuideCard(
                "04",
                "Accounts और Transfer",
                "Cash, Bank, UPI और Wallet के लिए अलग-अलग accounts बनाएँ।",
                "Transfer option से एक account का पैसा दूसरे account में भेजें। Transfer account balance बदलता है, लेकिन उसे Income या Expense नहीं माना जाता।",
                GuideTone.PURPLE
        );

        addGuideCard(
                "05",
                "Transactions",
                "यहाँ सभी Income, Expense और Transfer entries एक साथ दिखाई देती हैं।",
                "किसी transaction को खोलकर Edit या Delete किया जा सकता है। बदलाव के बाद Dashboard और Reports अपने-आप refresh होते हैं।",
                GuideTone.INFO
        );

        addGuideCard(
                "06",
                "Reports और Charts",
                "Daily, Weekly, Monthly और Yearly financial summaries देखें।",
                "Charts में category-wise spending, monthly trends और Income बनाम Expense comparison आसानी से समझा जा सकता है।",
                GuideTone.PURPLE
        );

        addGuideCard(
                "07",
                "Budget और Goals",
                "खर्च की सीमा तय करें और भविष्य की saving के लिए goals बनाएँ।",
                "Budget में category या महीने की spending limit सेट करें। Goals में Bike, Mobile, Education या Trip जैसे लक्ष्य बनाकर progress track करें।",
                GuideTone.WARNING
        );

        addGuideCard(
                "08",
                "Smart Loan Tracker",
                "Loan Taken और Loan Given दोनों प्रकार के loans manage करें।",
                "पुराना जमा पैसा Previous Paid में रखें। नया खर्च केवल EMI या Extra Payment दर्ज करने पर बनेगा और remaining loan अपने-आप कम होगा।",
                GuideTone.EXPENSE
        );

        addGuideCard(
                "09",
                "Bills, Subscriptions और Calendar",
                "Regular bills की due date, reminder और payment account save करें।",
                "Netflix, Recharge, Rent या Electricity Bill जैसे payments manage करें। Mark Paid करने पर expense entry बनती है और अगली due date update होती है।",
                GuideTone.SUCCESS
        );

        addGuideCard(
                "10",
                "Backup, Export और Import",
                "App data सुरक्षित रखें और reports को दूसरी files में निकालें।",
                "Backup Center से full backup बनाएँ या restore करें। Export से CSV/PDF file बनती है और Import CSV से पुराने records ऐप में लाए जा सकते हैं।",
                GuideTone.PURPLE
        );

        addGuideCard(
                "11",
                "Security और Theme",
                "PIN Lock, biometric unlock तथा Light/Dark theme manage करें।",
                "Settings से चार अंकों का PIN लगाया जा सकता है। PIN enabled होने पर supported devices में fingerprint या face unlock भी इस्तेमाल किया जा सकता है।",
                GuideTone.INFO
        );

        addGuideCard(
                "12",
                "Smart Finance Advisor",
                "Income और Expense data के आधार पर personalised financial insights देखें।",
                "Advisor saving rate, top spending category, monthly trend और projected expenses का विश्लेषण करता है। इसकी जानकारी सहायक guidance है, अंतिम financial निर्णय आपका रहेगा।",
                GuideTone.WARNING
        );
    }

    private void showEnglishGuides() {
        addGuideCard(
                "01",
                "Dashboard",
                "View total balance, income, expense and cash in hand when the app opens.",
                "Tap the Balance card to open Accounts. Quick Actions provide fast access to Income, Expense, Reports and other important tools.",
                GuideTone.INFO
        );

        addGuideCard(
                "02",
                "Add Income",
                "Enter the amount, select a category and account, then save the income.",
                "Record salary, business income, gifts or other money received. Account balance and Dashboard income update automatically.",
                GuideTone.SUCCESS
        );

        addGuideCard(
                "03",
                "Add Expense",
                "Enter the expense amount, category, account and transaction date.",
                "A bill or receipt photo may also be attached. The saved expense appears in Dashboard, Transactions and Reports.",
                GuideTone.EXPENSE
        );

        addGuideCard(
                "04",
                "Accounts and Transfer",
                "Create separate accounts for Cash, Bank, UPI and Wallet balances.",
                "Use Transfer to move money between accounts. Transfers change account balances but are not counted as income or expense.",
                GuideTone.PURPLE
        );

        addGuideCard(
                "05",
                "Transactions",
                "Review all income, expense and transfer entries in one place.",
                "Open a transaction to edit or delete it. Dashboard and Reports automatically refresh after a correction.",
                GuideTone.INFO
        );

        addGuideCard(
                "06",
                "Reports and Charts",
                "View daily, weekly, monthly and yearly financial summaries.",
                "Charts explain category spending, monthly trends and Income versus Expense performance through visual graphs.",
                GuideTone.PURPLE
        );

        addGuideCard(
                "07",
                "Budget and Goals",
                "Control spending limits and create saving targets for future plans.",
                "Set category or monthly limits in Budget. Create goals for a bike, mobile, education or trip and monitor saving progress.",
                GuideTone.WARNING
        );

        addGuideCard(
                "08",
                "Smart Loan Tracker",
                "Manage both Loan Taken and Loan Given records.",
                "Enter older payments as Previous Paid. A new expense is created only when an EMI or Extra Payment is recorded.",
                GuideTone.EXPENSE
        );

        addGuideCard(
                "09",
                "Bills, Subscriptions and Calendar",
                "Save due dates, reminders and payment accounts for recurring bills.",
                "Manage subscriptions, rent, recharge and electricity bills. Mark Paid records an expense and moves the bill to its next due date.",
                GuideTone.SUCCESS
        );

        addGuideCard(
                "10",
                "Backup, Export and Import",
                "Protect app data and move financial information through supported files.",
                "Create or restore a full backup in Backup Center. Export CSV/PDF reports or import older records using Import CSV.",
                GuideTone.PURPLE
        );

        addGuideCard(
                "11",
                "Security and Theme",
                "Manage PIN Lock, biometric unlock and Light or Dark appearance.",
                "Create a four-digit PIN from Settings. Supported devices may also use fingerprint or face authentication while PIN Lock is enabled.",
                GuideTone.INFO
        );

        addGuideCard(
                "12",
                "Smart Finance Advisor",
                "Receive personalised insights based on income and expense activity.",
                "The Advisor analyses saving rate, top spending categories, monthly trends and projected expenses. Its suggestions are general guidance.",
                GuideTone.WARNING
        );
    }

    private void addGuideCard(
            String number,
            String title,
            String summary,
            String details,
            GuideTone tone
    ) {
        GuideStyle guideStyle =
                getGuideStyle(tone);

        MaterialCardView card =
                new MaterialCardView(this);

        card.setCardBackgroundColor(
                getColorValue(R.color.app_surface)
        );

        card.setRadius(dp(19));
        card.setCardElevation(0);
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(guideStyle.outlineColor);
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        cardParams.setMargins(
                0,
                0,
                0,
                dp(9)
        );

        card.setLayoutParams(cardParams);

        LinearLayout content =
                new LinearLayout(this);

        content.setOrientation(
                LinearLayout.VERTICAL
        );

        content.setPadding(
                dp(14),
                dp(14),
                dp(14),
                dp(13)
        );

        LinearLayout headerRow =
                new LinearLayout(this);

        headerRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        headerRow.setGravity(
                Gravity.CENTER_VERTICAL
        );

        TextView numberBadge =
                createNumberBadge(
                        number,
                        guideStyle
                );

        headerRow.addView(numberBadge);

        LinearLayout titleContainer =
                new LinearLayout(this);

        titleContainer.setOrientation(
                LinearLayout.VERTICAL
        );

        LinearLayout.LayoutParams titleContainerParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        titleContainerParams.setMargins(
                dp(11),
                0,
                dp(8),
                0
        );

        titleContainer.setLayoutParams(
                titleContainerParams
        );

        TextView stepView =
                createText(
                        isHindiSelected
                                ? "गाइड स्टेप " + number
                                : "GUIDE STEP " + number,
                        9,
                        guideStyle.accentColor,
                        true
                );

        TextView titleView =
                createText(
                        title,
                        16,
                        getColorValue(
                                R.color.app_text_primary
                        ),
                        true
                );

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        titleParams.setMargins(
                0,
                dp(2),
                0,
                0
        );

        titleView.setLayoutParams(titleParams);

        titleContainer.addView(stepView);
        titleContainer.addView(titleView);

        headerRow.addView(titleContainer);

        TextView expandIndicator =
                createText(
                        "▼",
                        13,
                        guideStyle.accentColor,
                        true
                );

        expandIndicator.setGravity(Gravity.CENTER);

        expandIndicator.setBackground(
                createRoundedDrawable(
                        guideStyle.surfaceColor,
                        guideStyle.outlineColor,
                        11
                )
        );

        expandIndicator.setLayoutParams(
                new LinearLayout.LayoutParams(
                        dp(34),
                        dp(34)
                )
        );

        headerRow.addView(expandIndicator);
        content.addView(headerRow);

        TextView summaryView =
                createText(
                        summary,
                        12,
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        false
                );

        summaryView.setLineSpacing(
                dp(3),
                1f
        );

        LinearLayout.LayoutParams summaryParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        summaryParams.setMargins(
                0,
                dp(11),
                0,
                0
        );

        summaryView.setLayoutParams(summaryParams);

        content.addView(summaryView);

        LinearLayout detailsPanel =
                new LinearLayout(this);

        detailsPanel.setOrientation(
                LinearLayout.VERTICAL
        );

        detailsPanel.setVisibility(
                View.GONE
        );

        LinearLayout.LayoutParams detailsPanelParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        detailsPanelParams.setMargins(
                0,
                dp(12),
                0,
                0
        );

        detailsPanel.setLayoutParams(
                detailsPanelParams
        );

        View divider =
                new View(this);

        divider.setBackgroundColor(
                getColorValue(
                        R.color.app_divider
                )
        );

        divider.setLayoutParams(
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(1)
                )
        );

        detailsPanel.addView(divider);

        TextView detailsLabel =
                createText(
                        isHindiSelected
                                ? "पूरा विवरण"
                                : "FULL DETAILS",
                        9,
                        guideStyle.accentColor,
                        true
                );

        LinearLayout.LayoutParams detailsLabelParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        detailsLabelParams.setMargins(
                dp(11),
                dp(11),
                dp(11),
                0
        );

        detailsLabel.setLayoutParams(
                detailsLabelParams
        );

        detailsPanel.addView(detailsLabel);

        TextView detailsView =
                createText(
                        details,
                        12,
                        getColorValue(
                                R.color.app_text_primary
                        ),
                        false
                );

        detailsView.setLineSpacing(
                dp(4),
                1f
        );

        detailsView.setPadding(
                dp(11),
                dp(7),
                dp(11),
                dp(11)
        );

        LinearLayout.LayoutParams detailsParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        detailsView.setLayoutParams(detailsParams);

        detailsPanel.setBackground(
                createRoundedDrawable(
                        guideStyle.surfaceColor,
                        guideStyle.outlineColor,
                        13
                )
        );

        detailsPanel.addView(detailsView);
        content.addView(detailsPanel);

        TextView actionHint =
                createText(
                        getCollapsedHint(),
                        10,
                        guideStyle.accentColor,
                        true
                );

        actionHint.setGravity(
                Gravity.START
        );

        LinearLayout.LayoutParams actionHintParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        actionHintParams.setMargins(
                0,
                dp(10),
                0,
                0
        );

        actionHint.setLayoutParams(actionHintParams);

        content.addView(actionHint);

        card.addView(content);

        card.setContentDescription(
                title + ". " + getCollapsedHint()
        );

        card.setOnClickListener(view -> {
            boolean shouldExpand =
                    detailsPanel.getVisibility()
                            != View.VISIBLE;

            detailsPanel.setVisibility(
                    shouldExpand
                            ? View.VISIBLE
                            : View.GONE
            );

            expandIndicator.setText(
                    shouldExpand
                            ? "▲"
                            : "▼"
            );

            actionHint.setText(
                    shouldExpand
                            ? getExpandedHint()
                            : getCollapsedHint()
            );

            card.setContentDescription(
                    title
                            + ". "
                            + (
                            shouldExpand
                                    ? getExpandedHint()
                                    : getCollapsedHint()
                    )
            );
        });

        BubbleTouchAnimator.apply(card);

        guideContainer.addView(card);
    }

    private TextView createNumberBadge(
            String number,
            GuideStyle guideStyle
    ) {
        TextView badge =
                createText(
                        number,
                        13,
                        guideStyle.accentColor,
                        true
                );

        badge.setGravity(
                Gravity.CENTER
        );

        badge.setBackground(
                createRoundedDrawable(
                        guideStyle.surfaceColor,
                        guideStyle.outlineColor,
                        14
                )
        );

        badge.setLayoutParams(
                new LinearLayout.LayoutParams(
                        dp(46),
                        dp(46)
                )
        );

        return badge;
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
        textView.setGravity(Gravity.START);
        textView.setTextDirection(
                View.TEXT_DIRECTION_FIRST_STRONG
        );

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

        drawable.setColor(backgroundColor);

        drawable.setStroke(
                dp(1),
                outlineColor
        );

        drawable.setCornerRadius(
                dp(radiusDp)
        );

        return drawable;
    }

    private GuideStyle getGuideStyle(
            GuideTone tone
    ) {
        if (tone == GuideTone.SUCCESS) {
            return new GuideStyle(
                    getColorValue(R.color.success),
                    getColorValue(R.color.success_surface),
                    getColorValue(R.color.success_outline)
            );
        }

        if (tone == GuideTone.EXPENSE) {
            return new GuideStyle(
                    getColorValue(R.color.expense),
                    getColorValue(R.color.expense_surface),
                    getColorValue(R.color.expense_outline)
            );
        }

        if (tone == GuideTone.PURPLE) {
            return new GuideStyle(
                    getColorValue(R.color.purple),
                    getColorValue(R.color.purple_surface),
                    getColorValue(R.color.purple_outline)
            );
        }

        if (tone == GuideTone.WARNING) {
            return new GuideStyle(
                    getColorValue(R.color.warning),
                    getColorValue(R.color.warning_surface),
                    getColorValue(R.color.warning_outline)
            );
        }

        return new GuideStyle(
                getColorValue(R.color.secondary),
                getColorValue(R.color.info_surface),
                getColorValue(R.color.info_outline)
        );
    }

    private String getCollapsedHint() {
        return isHindiSelected
                ? "पूरा विवरण देखने के लिए दबाएँ"
                : "Tap to view full details";
    }

    private String getExpandedHint() {
        return isHindiSelected
                ? "विवरण छुपाने के लिए दबाएँ"
                : "Tap to hide full details";
    }

    private int getColorValue(
            int colorResource
    ) {
        return ContextCompat.getColor(
                this,
                colorResource
        );
    }

    private int dp(
            int value
    ) {
        return Math.round(
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    private enum GuideTone {
        INFO,
        SUCCESS,
        EXPENSE,
        PURPLE,
        WARNING
    }

    private static class GuideStyle {

        private final int accentColor;
        private final int surfaceColor;
        private final int outlineColor;

        private GuideStyle(
                int accentColor,
                int surfaceColor,
                int outlineColor
        ) {
            this.accentColor =
                    accentColor;

            this.surfaceColor =
                    surfaceColor;

            this.outlineColor =
                    outlineColor;
        }
    }
}