package com.example.moneymanagerpro.pro;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

/**
 * Extends HelpActivity with category-wise Pro guides and enforces a true
 * accordion: opening one help card always closes the previously open card.
 */
public final class HelpProController {

    private static final String PRO_HEADER_TAG = "help_pro_categories_header";
    private static final String PRO_CARD_TAG_PREFIX = "help_pro_card_";

    private final Activity activity;
    private LinearLayout guideContainer;
    private MaterialButton btnHindi;
    private MaterialButton btnEnglish;
    private MaterialCardView openCard;
    private boolean rebindPosted;
    private boolean building;

    public HelpProController(@NonNull Activity activity) {
        this.activity = activity;
    }

    public void attach() {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        guideContainer = activity.findViewById(R.id.guideContainer);
        btnHindi = activity.findViewById(R.id.btnHindi);
        btnEnglish = activity.findViewById(R.id.btnEnglish);
        if (guideContainer == null) return;

        guideContainer.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
            @Override
            public void onChildViewAdded(View parent, View child) {
                if (!building) scheduleRebind();
            }

            @Override
            public void onChildViewRemoved(View parent, View child) {
                if (!building) scheduleRebind();
            }
        });

        scheduleRebind();
    }

    private void scheduleRebind() {
        if (rebindPosted || guideContainer == null) return;
        rebindPosted = true;
        guideContainer.postDelayed(() -> {
            rebindPosted = false;
            if (activity.isFinishing() || activity.isDestroyed()) return;
            bindAccordionAndProGuides();
        }, 180L);
    }

    private void bindAccordionAndProGuides() {
        if (guideContainer == null) return;
        building = true;
        try {
            boolean hindi = isHindi();

            if (guideContainer.findViewWithTag(PRO_HEADER_TAG) == null) {
                appendProGuides(hindi);
            }

            for (int i = 0; i < guideContainer.getChildCount(); i++) {
                View child = guideContainer.getChildAt(i);
                if (child instanceof MaterialCardView) {
                    configureAccordion((MaterialCardView) child, hindi);
                }
            }
        } finally {
            building = false;
        }
    }

    private boolean isHindi() {
        if (btnHindi == null || btnEnglish == null) return true;
        String hindiText = String.valueOf(btnHindi.getText());
        String englishText = String.valueOf(btnEnglish.getText());
        if (englishText.startsWith("✓")) return false;
        if (hindiText.startsWith("✓")) return true;
        return true;
    }

    private void appendProGuides(boolean hindi) {
        TextView heading = new TextView(activity);
        heading.setTag(PRO_HEADER_TAG);
        heading.setText(hindi ? "नए Pro और Advanced फीचर्स" : "New Pro & Advanced Features");
        heading.setTextSize(19);
        heading.setTextColor(Color.parseColor("#17351F"));
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, -2);
        headingParams.setMargins(0, dp(17), 0, dp(3));
        heading.setLayoutParams(headingParams);
        guideContainer.addView(heading);

        TextView subtitle = new TextView(activity);
        subtitle.setText(hindi
                ? "पूरे ऐप inspection के बाद जोड़े गए advanced tools को category-wise समझें।"
                : "Category-wise guidance for advanced tools found and added after the full app inspection.");
        subtitle.setTextSize(10);
        subtitle.setTextColor(Color.parseColor("#667085"));
        subtitle.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.setMargins(0, 0, 0, dp(9));
        subtitle.setLayoutParams(subtitleParams);
        guideContainer.addView(subtitle);

        if (hindi) {
            addProCard("P1", "Finance Pro Suite & AI Intelligence",
                    "Smart Dashboard 2.0, AI insights, analytics, smart budget और financial health एक जगह देखें।",
                    "Dashboard → Dashboard Tools → Finance Pro Suite खोलें। यहाँ Income, Expense, Saving, Balance, Credit Available और Loan Outstanding का compact overview मिलता है। AI section खर्च के trend, unusual spending, month-end projection, top category और saving suggestion दिखाता है। Advanced Analytics/Charts, Smart Budget, Accounts, Credit Cards और Loan Manager के direct buttons भी इसी workspace में हैं।",
                    "#EEF5FF", "#BDD5EE", "#0F6CBD");

            addProCard("P2", "Smart Notifications & Transaction Intelligence",
                    "नई app transactions, possible duplicates और due reminders के smart alerts पाएँ।",
                    "यह feature केवल Money Manager Pro के अंदर save/import की गई transaction data को analyse करता है। SMS inbox पढ़ा नहीं जाता और READ_SMS/RECEIVE_SMS permissions इस्तेमाल नहीं होतीं। पहली run पर पुराना data baseline बनता है; उसके बाद नई entries का summary alert मिल सकता है। Recent matching amount/category/account entries duplicate review के लिए flag होती हैं। Existing Bill/Subscription और Loan EMI reminder engines चलते रहते हैं तथा Credit Card outstanding होने पर due-date alert भी मिलता है। कोई duplicate entry अपने-आप delete नहीं होती।",
                    "#FFF9EC", "#E9D7A8", "#B26A00");

            addProCard("P3", "Backup & Security Pro",
                    "Encrypted cloud backup के साथ automatic history, verified restore points और integrity status देखें।",
                    "Backup Center में Backup & Security Pro section खोलें। Latest offline backup SHA-256 से verify होता है। Backup file बदलने पर background history worker verified restore point बनाता है और पुराने points सीमित संख्या में रखता है। Create Restore Point से manual checkpoint भी बनाया जा सकता है। Automatic Backup History में successful offline और encrypted-cloud checkpoints का समय, record count और size दिखता है; recovery passphrase history में store नहीं होती। Restore करने के लिए उसी Backup page के existing Restore action से MoneyManagerPro_RestorePoint file चुनें।",
                    "#EFF9F1", "#B9DFC3", "#107C41");

            addProCard("P4", "Reports Pro",
                    "Custom date range से A4 PDF, real Excel workbook और Share-ready statement बनाएँ।",
                    "Reports page के नीचे Reports Pro section में From और To तारीख चुनें। Use Current Month Statement से वर्तमान महीने की range तुरंत set हो जाती है। Preview में Income, Expense, Net Balance और transaction count verify करें। PDF button A4 multi-page statement बनाता है; Excel button .xlsx workbook बनाता है; Share आखिरी generated file को Android Share sheet में भेजता है। Report केवल चुनी हुई range की entries से local device पर generate होती है।",
                    "#F4F0FF", "#D8C8F2", "#8764B8");

            addProCard("P5", "Budget, Accounts, Credit Cards & Loans Pro",
                    "Spending limits, available credit, due dates और debt position को connected तरीके से manage करें।",
                    "Smart Budget category limits और month-end overspending prediction दिखाता है। Accounts में names/details edit और account balances manage किए जा सकते हैं। Credit Cards Pro credit limit, outstanding, available limit, utilization, billing/due cycle और payment urgency दिखाता है। Loan Manager outstanding balance, EMI और payments track करता है। Finance Pro Suite इन सभी modules का combined overview देता है ताकि अलग-अलग pages की information एक financial picture में समझ आए।",
                    "#FFF2F0", "#F0C8C0", "#C42B1C");

            addProCard("P6", "Data, Import, Export & Receipt Tools",
                    "Transactions और supporting data को import/export तथा review करने वाले advanced tools समझें।",
                    "Transactions, CSV Import, Export, Receipt Gallery, Account Data Center, Credit Card statement/import tools और Advanced Finance Data जैसे modules app के recorded financial data के साथ काम करते हैं। Import से पहले source/columns verify करें और duplicate review करें। Export/Reports से बनाई file को share करने से पहले date range और totals check करें। Receipt image supporting reference है; financial amount हमेशा saved transaction entry से verify करें।",
                    "#F7F9FC", "#D8E0E8", "#475467");

            addProCard("P7", "Professional UI, Dashboard Tools & Security",
                    "Fluent-inspired compact navigation, status chips और security controls का सही उपयोग करें।",
                    "App में Microsoft 365/Windows 11 Fluent-inspired light surfaces, compact cards, soft borders, rounded controls और responsive spacing लागू हैं। Dashboard Tools में advanced sections accordion रूप में grouped हैं। Help cards भी अब single-open accordion हैं—दूसरा card खोलने पर पहला अपने-आप बंद होगा। Settings/Backup में PIN, biometric, inactivity lock, backup schedules और encrypted cloud protection जैसे security options उपलब्ध हैं; device और recovery credentials सुरक्षित रखें।",
                    "#EEF8F4", "#C4DED3", "#13795B");
        } else {
            addProCard("P1", "Finance Pro Suite & AI Intelligence",
                    "Use Smart Dashboard 2.0, AI insights, analytics, smart budget and financial health in one workspace.",
                    "Open Dashboard → Dashboard Tools → Finance Pro Suite. The compact overview combines Income, Expense, Saving, Balance, Credit Available and Loan Outstanding. AI insights analyse expense trends, unusual spending, month-end projection, top category and saving guidance. Direct actions open Advanced Analytics/Charts, Smart Budget, Accounts, Credit Cards and Loan Manager.",
                    "#EEF5FF", "#BDD5EE", "#0F6CBD");

            addProCard("P2", "Smart Notifications & Transaction Intelligence",
                    "Get smart alerts for new app transactions, possible duplicates and upcoming dues.",
                    "This feature analyses only transactions saved or imported inside Money Manager Pro. It does not read the SMS inbox and does not use READ_SMS or RECEIVE_SMS permissions. The first run establishes a baseline; later app entries may trigger a summary alert. Recent matching amount/category/account entries can be flagged for duplicate review. Existing bill/subscription and loan EMI reminder engines continue to run, while outstanding credit cards receive due-date alerts. Nothing is deleted automatically.",
                    "#FFF9EC", "#E9D7A8", "#B26A00");

            addProCard("P3", "Backup & Security Pro",
                    "Combine encrypted cloud backup with automatic history, verified restore points and integrity status.",
                    "Open Backup Center and use Backup & Security Pro. The latest offline backup is checked with SHA-256. When the latest file changes, a background history worker creates a verified restore point and retains a limited rolling history. Create Restore Point adds a manual checkpoint. Automatic Backup History records successful offline and encrypted-cloud checkpoint time, record count and size without storing the recovery passphrase. Use the existing Restore action and select a MoneyManagerPro_RestorePoint file when you need an older checkpoint.",
                    "#EFF9F1", "#B9DFC3", "#107C41");

            addProCard("P4", "Reports Pro",
                    "Create A4 PDF, real Excel workbooks and share-ready statements for any custom date range.",
                    "On Reports, choose From and To dates in Reports Pro. Use Current Month Statement for a one-tap monthly range. Verify Income, Expense, Net Balance and entry count in the preview. PDF creates a multi-page A4 statement, Excel creates an .xlsx workbook, and Share sends the latest generated file through Android Share. Only transactions inside the selected period are included and generation happens locally.",
                    "#F4F0FF", "#D8C8F2", "#8764B8");

            addProCard("P5", "Budget, Accounts, Credit Cards & Loans Pro",
                    "Manage spending limits, available credit, due dates and debt position as connected finance data.",
                    "Smart Budget provides category limits and month-end overspending prediction. Accounts supports editable account information and balances. Credit Cards Pro shows credit limit, outstanding, available limit, utilization, billing/due cycle and urgency. Loan Manager tracks outstanding amount, EMI and payments. Finance Pro Suite combines these modules into one financial overview.",
                    "#FFF2F0", "#F0C8C0", "#C42B1C");

            addProCard("P6", "Data, Import, Export & Receipt Tools",
                    "Understand advanced tools used to import, export and review transactions and supporting data.",
                    "Transactions, CSV Import, Export, Receipt Gallery, Account Data Center, credit-card statement/import tools and Advanced Finance Data work with recorded financial data. Verify source columns before importing and review possible duplicates. Check the selected period and totals before sharing exports. Receipt images are supporting references; verify financial amounts against the saved transaction entry.",
                    "#F7F9FC", "#D8E0E8", "#475467");

            addProCard("P7", "Professional UI, Dashboard Tools & Security",
                    "Use Fluent-inspired compact navigation, smart status chips and security controls consistently.",
                    "The app uses Microsoft 365/Windows 11 Fluent-inspired light surfaces, compact cards, soft borders, rounded controls and responsive spacing. Dashboard Tools groups advanced sections as accordions. Help is also single-open: opening another card automatically closes the previous one. Security options include PIN, biometric authentication, inactivity lock, backup schedules and encrypted-cloud protection; keep device and recovery credentials secure.",
                    "#EEF8F4", "#C4DED3", "#13795B");
        }
    }

    private void addProCard(
            String number,
            String title,
            String summary,
            String details,
            String surface,
            String outline,
            String accent
    ) {
        MaterialCardView card = new MaterialCardView(activity);
        card.setTag(PRO_CARD_TAG_PREFIX + number);
        card.setCardBackgroundColor(Color.WHITE);
        card.setRadius(dp(18));
        card.setCardElevation(0f);
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(Color.parseColor(outline));
        card.setClickable(true);
        card.setFocusable(true);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.setMargins(0, 0, 0, dp(9));
        card.setLayoutParams(cardParams);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(13), dp(12), dp(13), dp(12));

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView badge = new TextView(activity);
        badge.setText(number);
        badge.setTextSize(11);
        badge.setTextColor(Color.parseColor(accent));
        badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(rounded(surface, outline, 13));
        header.addView(badge, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1f);
        labelParams.setMargins(dp(10), 0, dp(7), 0);
        labels.setLayoutParams(labelParams);

        TextView category = new TextView(activity);
        category.setText(isHindi() ? "PRO CATEGORY" : "PRO CATEGORY");
        category.setTextSize(8);
        category.setTextColor(Color.parseColor(accent));
        category.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        labels.addView(category);

        TextView titleView = new TextView(activity);
        titleView.setText(title);
        titleView.setTextSize(14);
        titleView.setTextColor(Color.parseColor("#17351F"));
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        labels.addView(titleView);
        header.addView(labels);

        TextView indicator = new TextView(activity);
        indicator.setText("▼");
        indicator.setTextSize(12);
        indicator.setTextColor(Color.parseColor(accent));
        indicator.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        indicator.setGravity(Gravity.CENTER);
        indicator.setBackground(rounded(surface, outline, 10));
        header.addView(indicator, new LinearLayout.LayoutParams(dp(32), dp(32)));
        content.addView(header);

        TextView summaryView = new TextView(activity);
        summaryView.setText(summary);
        summaryView.setTextSize(11);
        summaryView.setTextColor(Color.parseColor("#667085"));
        summaryView.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(-1, -2);
        summaryParams.setMargins(0, dp(9), 0, 0);
        summaryView.setLayoutParams(summaryParams);
        content.addView(summaryView);

        LinearLayout detailsPanel = new LinearLayout(activity);
        detailsPanel.setOrientation(LinearLayout.VERTICAL);
        detailsPanel.setVisibility(View.GONE);
        detailsPanel.setPadding(dp(10), dp(9), dp(10), dp(9));
        detailsPanel.setBackground(rounded(surface, outline, 12));
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(-1, -2);
        detailsParams.setMargins(0, dp(10), 0, 0);
        detailsPanel.setLayoutParams(detailsParams);

        TextView detailsView = new TextView(activity);
        detailsView.setText(details);
        detailsView.setTextSize(11);
        detailsView.setTextColor(Color.parseColor("#344054"));
        detailsView.setLineSpacing(dp(3), 1f);
        detailsPanel.addView(detailsView);
        content.addView(detailsPanel);

        TextView hint = new TextView(activity);
        hint.setText(collapsedHint(isHindi()));
        hint.setTextSize(9);
        hint.setTextColor(Color.parseColor(accent));
        hint.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.setMargins(0, dp(8), 0, 0);
        hint.setLayoutParams(hintParams);
        content.addView(hint);

        card.addView(content);
        BubbleTouchAnimator.apply(card);
        guideContainer.addView(card);
    }

    private void configureAccordion(@NonNull MaterialCardView card, boolean hindi) {
        CardParts parts = resolve(card);
        if (parts == null) return;

        card.setOnClickListener(view -> {
            boolean shouldOpen = parts.details.getVisibility() != View.VISIBLE;
            closeAllExcept(shouldOpen ? card : null, hindi);

            if (shouldOpen) {
                parts.details.setVisibility(View.VISIBLE);
                parts.details.setAlpha(0f);
                parts.details.setTranslationY(-dp(6));
                parts.details.animate().alpha(1f).translationY(0f).setDuration(180L).start();
                parts.indicator.setText("▲");
                parts.hint.setText(expandedHint(hindi));
                openCard = card;
            } else {
                closeCard(card, parts, hindi);
                openCard = null;
            }
        });
        BubbleTouchAnimator.apply(card);
    }

    private void closeAllExcept(MaterialCardView keepOpen, boolean hindi) {
        for (int i = 0; i < guideContainer.getChildCount(); i++) {
            View child = guideContainer.getChildAt(i);
            if (!(child instanceof MaterialCardView)) continue;
            MaterialCardView card = (MaterialCardView) child;
            if (card == keepOpen) continue;
            CardParts parts = resolve(card);
            if (parts != null) closeCard(card, parts, hindi);
        }
        if (keepOpen == null) openCard = null;
    }

    private void closeCard(MaterialCardView card, CardParts parts, boolean hindi) {
        parts.details.animate().cancel();
        parts.details.setVisibility(View.GONE);
        parts.details.setAlpha(1f);
        parts.details.setTranslationY(0f);
        parts.indicator.setText("▼");
        parts.hint.setText(collapsedHint(hindi));
        if (openCard == card) openCard = null;
    }

    private CardParts resolve(MaterialCardView card) {
        if (card.getChildCount() == 0 || !(card.getChildAt(0) instanceof LinearLayout)) return null;
        LinearLayout content = (LinearLayout) card.getChildAt(0);
        if (content.getChildCount() < 4) return null;
        if (!(content.getChildAt(0) instanceof LinearLayout)
                || !(content.getChildAt(2) instanceof LinearLayout)
                || !(content.getChildAt(3) instanceof TextView)) return null;

        LinearLayout header = (LinearLayout) content.getChildAt(0);
        if (header.getChildCount() < 3 || !(header.getChildAt(2) instanceof TextView)) return null;

        return new CardParts(
                (LinearLayout) content.getChildAt(2),
                (TextView) header.getChildAt(2),
                (TextView) content.getChildAt(3)
        );
    }

    private String collapsedHint(boolean hindi) {
        return hindi ? "टच करें • पूरा विवरण खोलें" : "Tap • open full details";
    }

    private String expandedHint(boolean hindi) {
        return hindi ? "टच करें • विवरण बन्द करें" : "Tap • close details";
    }

    private GradientDrawable rounded(String background, String outline, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor(background));
        drawable.setStroke(dp(1), Color.parseColor(outline));
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static final class CardParts {
        final LinearLayout details;
        final TextView indicator;
        final TextView hint;

        CardParts(LinearLayout details, TextView indicator, TextView hint) {
            this.details = details;
            this.indicator = indicator;
            this.hint = hint;
        }
    }
}
