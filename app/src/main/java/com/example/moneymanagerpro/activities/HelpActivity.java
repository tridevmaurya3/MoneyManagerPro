package com.example.moneymanagerpro.activities;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

public class HelpActivity extends AppCompatActivity {

    private MaterialButton btnHindi;
    private MaterialButton btnEnglish;
    private LinearLayout guideContainer;

    private boolean isHindiSelected = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);

        TextView btnBack = findViewById(R.id.btnBack);
        btnHindi = findViewById(R.id.btnHindi);
        btnEnglish = findViewById(R.id.btnEnglish);
        guideContainer = findViewById(R.id.guideContainer);

        btnBack.setOnClickListener(v -> finish());

        BubbleTouchAnimator.apply(btnHindi);
        BubbleTouchAnimator.apply(btnEnglish);

        btnHindi.setOnClickListener(v -> {
            isHindiSelected = true;
            updateLanguage();
        });

        btnEnglish.setOnClickListener(v -> {
            isHindiSelected = false;
            updateLanguage();
        });

        updateLanguage();
    }

    private void updateLanguage() {
        int selectedColor = Color.parseColor("#0F766E");
        int unselectedColor = Color.parseColor("#E2E8F0");

        btnHindi.setBackgroundTintList(ColorStateList.valueOf(
                isHindiSelected ? selectedColor : unselectedColor
        ));

        btnHindi.setTextColor(
                isHindiSelected ? Color.WHITE : Color.parseColor("#172033")
        );

        btnEnglish.setBackgroundTintList(ColorStateList.valueOf(
                isHindiSelected ? unselectedColor : selectedColor
        ));

        btnEnglish.setTextColor(
                isHindiSelected ? Color.parseColor("#172033") : Color.WHITE
        );

        guideContainer.removeAllViews();

        if (isHindiSelected) {
            showHindiGuides();
        } else {
            showEnglishGuides();
        }
    }

    private void showHindiGuides() {
        addGuideCard(
                "1",
                "Dashboard",
                "ऐप खुलते ही आपका कुल Balance, Income, Expense और Cash in Hand दिखता है।",
                "Balance card दबाने पर Accounts खुलते हैं। नीचे दिए buttons से जल्दी Income, Expense, Reports और बाकी tools खोल सकते हैं।"
        );

        addGuideCard(
                "2",
                "Income जोड़ना",
                "Add Income दबाएँ, राशि, category और account चुनें, फिर Save करें।",
                "Salary, Business, Gift या अन्य income जोड़ सकते हैं। Save करने पर balance और dashboard income अपने-आप update होगी।"
        );

        addGuideCard(
                "3",
                "Expense जोड़ना",
                "Add Expense दबाएँ, खर्च की राशि, category और account चुनें।",
                "जरूरत हो तो bill की photo भी लगाएँ। केवल आज या चुनी हुई तारीख का खर्च ही report और dashboard में जुड़ेगा।"
        );

        addGuideCard(
                "4",
                "Accounts और Transfer",
                "Cash, Bank, UPI या Wallet के अलग-अलग account बनाएँ।",
                "Transfer option से Cash से Bank या Bank से UPI में पैसा भेजें। Transfer को income या expense नहीं माना जाता।"
        );

        addGuideCard(
                "5",
                "Transactions",
                "Transactions में आपकी सभी Income, Expense और Transfer entries दिखती हैं।",
                "किसी entry को खोलकर Edit या Delete कर सकते हैं। गलत entry सुधारने के बाद dashboard अपने-आप बदलता है।"
        );

        addGuideCard(
                "6",
                "Reports और Charts",
                "Reports में daily, weekly, monthly और yearly हिसाब देखें।",
                "Charts में category-wise खर्च, monthly trend और income-versus-expense graph दिखता है।"
        );

        addGuideCard(
                "7",
                "Budget और Goals",
                "Budget में किसी category या महीने का खर्च limit सेट करें।",
                "Goals में Bike, Mobile, Trip जैसे लक्ष्य बनाकर saving progress देख सकते हैं।"
        );

        addGuideCard(
                "8",
                "Smart Loan Tracker",
                "Loan बनाते समय पुराना जमा पैसा Previous Paid में डालें।",
                "यह पुरानी रकम current expense में नहीं जुड़ेगी। आगे से Pay EMI या Extra Payment दबाने पर ही नया खर्च बनेगा और remaining loan अपने-आप कम होगा।"
        );

        addGuideCard(
                "9",
                "Bills, Subscriptions और Calendar",
                "Netflix, recharge, rent या बिजली bill की due date जोड़ें।",
                "Calendar से तारीख के अनुसार खर्च देखें। Subscription और recurring entry से नियमित payments याद रखना आसान होता है।"
        );

        addGuideCard(
                "10",
                "Backup, Export और Import",
                "Backup से अपना app data सुरक्षित रखें। Restore से वही data वापस ला सकते हैं।",
                "Export से CSV या PDF report निकालें। पुराना Excel/CSV data Import CSV से ऐप में लाया जा सकता है।"
        );

        addGuideCard(
                "11",
                "Security और Theme",
                "Settings में PIN, Fingerprint Lock और Light/Dark theme का विकल्प मिलता है।",
                "PIN भूलने से बचने के लिए उसे सुरक्षित जगह लिखकर रखें। Backup बनाने के बाद ही कोई बड़ा बदलाव करें।"
        );

        addGuideCard(
                "12",
                "Smart Advisor",
                "Smart Advisor आपके income, expense और category data को देखकर सुझाव देता है।",
                "यह खर्च कम करने, budget बनाने और बचत सुधारने में मदद करता है। यह केवल सहायक सलाह है, अंतिम financial निर्णय आपका रहेगा।"
        );
    }

    private void showEnglishGuides() {
        addGuideCard(
                "1",
                "Dashboard",
                "The dashboard shows your total balance, income, expense and cash in hand.",
                "Tap the balance card to open accounts. The quick buttons open Income, Expense, Reports and other useful tools."
        );

        addGuideCard(
                "2",
                "Add Income",
                "Tap Add Income, enter the amount, select category and account, then save.",
                "You can record salary, business income, gifts and other money received. The dashboard updates automatically."
        );

        addGuideCard(
                "3",
                "Add Expense",
                "Tap Add Expense, enter the amount, select category and account.",
                "You may attach a bill photo if needed. Only the selected date's expense is added to reports and the dashboard."
        );

        addGuideCard(
                "4",
                "Accounts and Transfer",
                "Create separate Cash, Bank, UPI and Wallet accounts.",
                "Use Transfer to move money between accounts. Transfers are not counted as income or expense."
        );

        addGuideCard(
                "5",
                "Transactions",
                "Transactions shows all income, expense and transfer entries.",
                "Open an entry to edit or delete it. The dashboard refreshes after any correction."
        );

        addGuideCard(
                "6",
                "Reports and Charts",
                "Reports provide daily, weekly, monthly and yearly summaries.",
                "Charts show category spending, monthly trend and income-versus-expense analysis."
        );

        addGuideCard(
                "7",
                "Budget and Goals",
                "Set a spending limit for a category or a month in Budget.",
                "Create goals such as a bike, mobile or trip, and track your saving progress."
        );

        addGuideCard(
                "8",
                "Smart Loan Tracker",
                "Enter older paid money in Previous Paid while creating the loan.",
                "That old amount does not become a current expense. Only Pay EMI or Extra Payment creates a new transaction and reduces the remaining loan."
        );

        addGuideCard(
                "9",
                "Bills, Subscriptions and Calendar",
                "Add due dates for Netflix, recharge, rent or electricity bills.",
                "Use Calendar to view spending by date. Subscriptions and recurring entries help manage regular payments."
        );

        addGuideCard(
                "10",
                "Backup, Export and Import",
                "Create a backup to protect your app data and use Restore when needed.",
                "Export reports to CSV or PDF. Import CSV can bring older Excel or CSV data into the app."
        );

        addGuideCard(
                "11",
                "Security and Theme",
                "Settings includes PIN lock, fingerprint unlock and Light or Dark theme.",
                "Keep your PIN in a safe place and create a backup before making major changes."
        );

        addGuideCard(
                "12",
                "Smart Advisor",
                "Smart Advisor studies your income, expense and category data to provide useful suggestions.",
                "It helps with spending awareness, budget planning and saving. Final financial decisions remain yours."
        );
    }

    private void addGuideCard(
            String number,
            String title,
            String summary,
            String details
    ) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(Color.WHITE);
        card.setRadius(dpToPx(20));
        card.setCardElevation(dpToPx(4));
        card.setStrokeWidth(dpToPx(1));
        card.setStrokeColor(Color.parseColor("#D8E0EA"));
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dpToPx(12));
        card.setLayoutParams(cardParams);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dpToPx(16), dpToPx(15), dpToPx(16), dpToPx(15));

        TextView step = new TextView(this);
        step.setText("STEP " + number);
        step.setTextColor(Color.parseColor("#0F766E"));
        step.setTextSize(12);
        step.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        step.setGravity(Gravity.CENTER);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(Color.parseColor("#172033"));
        titleView.setTextSize(18);
        titleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        titleView.setGravity(Gravity.CENTER);

        TextView summaryView = new TextView(this);
        summaryView.setText(summary);
        summaryView.setTextColor(Color.parseColor("#475569"));
        summaryView.setTextSize(14);
        summaryView.setGravity(Gravity.CENTER);

        TextView detailsView = new TextView(this);
        detailsView.setText(details);
        detailsView.setTextColor(Color.parseColor("#172033"));
        detailsView.setTextSize(14);
        detailsView.setGravity(Gravity.CENTER);
        detailsView.setPadding(0, dpToPx(10), 0, 0);
        detailsView.setVisibility(View.GONE);

        TextView hintView = new TextView(this);
        hintView.setText(isHindiSelected
                ? "पूरा विवरण देखने के लिए दबाएँ"
                : "Tap to view full details");
        hintView.setTextColor(Color.parseColor("#0F766E"));
        hintView.setTextSize(12);
        hintView.setGravity(Gravity.CENTER);
        hintView.setPadding(0, dpToPx(8), 0, 0);

        content.addView(step);
        content.addView(titleView);
        content.addView(summaryView);
        content.addView(detailsView);
        content.addView(hintView);

        card.addView(content);

        BubbleTouchAnimator.apply(card);

        card.setOnClickListener(v -> {
            boolean showDetails = detailsView.getVisibility() != View.VISIBLE;

            detailsView.setVisibility(
                    showDetails ? View.VISIBLE : View.GONE
            );

            hintView.setText(showDetails
                    ? (isHindiSelected
                       ? "विवरण छुपाने के लिए दबाएँ"
                       : "Tap to hide details")
                    : (isHindiSelected
                       ? "पूरा विवरण देखने के लिए दबाएँ"
                       : "Tap to view full details"));
        });

        guideContainer.addView(card);
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density);
    }
}