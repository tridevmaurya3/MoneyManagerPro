package com.example.moneymanagerpro.activities;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.TridevIntegrationReviewManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * STEP 6 - Integration Review Center.
 *
 * This activity is intentionally private (android:exported="false"). It no
 * longer accepts external SmartSMS intents. Incoming finance events arrive only
 * through the UID-verified TridevFinanceEventProvider and are reviewed here from
 * MoneyManager's isolated integration queue.
 */
public class SmartSmsTransactionReviewActivity extends AppCompatActivity {

    private LinearLayout reviewEventContainer;
    private TextView reviewPendingCount;
    private TextView reviewMappableCount;
    private TextView reviewLockedCount;
    private TextView reviewStatus;
    private View reviewProgress;
    private View reviewEmptyCard;
    private MaterialButton reviewRefresh;

    private TridevIntegrationReviewManager reviewManager;
    private int loadGeneration = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smart_sms_transaction_review);

        reviewManager = new TridevIntegrationReviewManager(getApplicationContext());
        bindViews();
        setupActions();
        loadReviews();
    }

    private void bindViews() {
        reviewEventContainer = findViewById(R.id.reviewEventContainer);
        reviewPendingCount = findViewById(R.id.reviewPendingCount);
        reviewMappableCount = findViewById(R.id.reviewMappableCount);
        reviewLockedCount = findViewById(R.id.reviewLockedCount);
        reviewStatus = findViewById(R.id.reviewStatus);
        reviewProgress = findViewById(R.id.reviewProgress);
        reviewEmptyCard = findViewById(R.id.reviewEmptyCard);
        reviewRefresh = findViewById(R.id.reviewRefresh);
    }

    private void setupActions() {
        findViewById(R.id.bridgeBack).setOnClickListener(v -> finish());
        reviewRefresh.setOnClickListener(v -> loadReviews());
    }

    private void loadReviews() {
        final int generation = ++loadGeneration;
        reviewRefresh.setEnabled(false);
        reviewProgress.setVisibility(View.VISIBLE);
        reviewStatus.setVisibility(View.VISIBLE);
        reviewStatus.setText(R.string.integration_review_loading);
        reviewEmptyCard.setVisibility(View.GONE);

        new Thread(() -> {
            List<TridevIntegrationReviewManager.ReviewItem> items;
            try {
                items = reviewManager.loadReviewItems(100);
            } catch (RuntimeException failure) {
                items = null;
            }

            final List<TridevIntegrationReviewManager.ReviewItem> result = items;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || generation != loadGeneration) return;
                reviewRefresh.setEnabled(true);
                reviewProgress.setVisibility(View.GONE);

                if (result == null) {
                    reviewEventContainer.removeAllViews();
                    reviewStatus.setVisibility(View.VISIBLE);
                    reviewStatus.setText("Integration review could not be loaded safely.");
                    updateCounts(0, 0, 0);
                    return;
                }
                renderReviews(result);
            });
        }, "IntegrationReviewLoad").start();
    }

    private void renderReviews(List<TridevIntegrationReviewManager.ReviewItem> items) {
        reviewEventContainer.removeAllViews();

        int mappable = 0;
        int locked = 0;
        for (TridevIntegrationReviewManager.ReviewItem item : items) {
            if (item.canConfirmMapping) mappable++;
            else locked++;
        }
        updateCounts(items.size(), mappable, locked);

        if (items.isEmpty()) {
            reviewStatus.setVisibility(View.GONE);
            reviewEmptyCard.setVisibility(View.VISIBLE);
            return;
        }

        reviewEmptyCard.setVisibility(View.GONE);
        reviewStatus.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (TridevIntegrationReviewManager.ReviewItem item : items) {
            View card = inflater.inflate(
                    R.layout.item_integration_review_event,
                    reviewEventContainer,
                    false);
            bindReviewCard(card, item);
            reviewEventContainer.addView(card);
        }
    }

    private void bindReviewCard(
            View card,
            TridevIntegrationReviewManager.ReviewItem item) {
        TextView source = card.findViewById(R.id.reviewEventSource);
        TextView status = card.findViewById(R.id.reviewEventStatus);
        TextView amount = card.findViewById(R.id.reviewEventAmount);
        TextView meta = card.findViewById(R.id.reviewEventMeta);
        TextView accountHint = card.findViewById(R.id.reviewEventAccountHint);
        TextView categoryHint = card.findViewById(R.id.reviewEventCategoryHint);
        TextView merchant = card.findViewById(R.id.reviewEventMerchant);
        TextView reason = card.findViewById(R.id.reviewEventReason);
        TextView lockReason = card.findViewById(R.id.reviewEventLockReason);
        MaterialAutoCompleteTextView accountDropdown =
                card.findViewById(R.id.reviewAccountDropdown);
        MaterialAutoCompleteTextView categoryDropdown =
                card.findViewById(R.id.reviewCategoryDropdown);
        MaterialButton confirm = card.findViewById(R.id.reviewConfirmButton);

        source.setText(item.sourceLabel);
        amount.setText(formatAmount(item.amountMinor, item.direction));
        amount.setTextColor(ContextCompat.getColor(
                this,
                "CREDIT".equalsIgnoreCase(item.direction)
                        ? R.color.success
                        : R.color.expense));

        boolean locked = !item.canConfirmMapping;
        status.setText(locked
                ? R.string.integration_review_locked
                : R.string.integration_review_mappable);
        decorateStatus(status, locked);

        String type = item.moneyType.isEmpty() ? item.direction : item.moneyType;
        meta.setText(type + " • " + formatDate(item.occurredAt));

        accountHint.setText("Account signal: " + safeLabel(item.accountHint, "Not identified"));
        categoryHint.setText("Category signal: " + safeLabel(item.categoryHint, "Not identified"));

        if (item.merchantHint.isEmpty()) {
            merchant.setVisibility(View.GONE);
        } else {
            merchant.setVisibility(View.VISIBLE);
            merchant.setText("Merchant: " + item.merchantHint);
        }

        if (locked) {
            reason.setVisibility(View.GONE);
            lockReason.setVisibility(View.VISIBLE);
            lockReason.setText(item.lockReason);
            setWarningBackground(lockReason);
        } else {
            lockReason.setVisibility(View.GONE);
            reason.setVisibility(View.VISIBLE);
            reason.setText(buildSuggestionReason(item));
        }

        final String[] selectedAccountRef = {""};
        final String[] selectedCategoryRef = {""};

        configureDropdown(
                accountDropdown,
                item.accountChoices,
                item.accountSuggestionRef,
                selectedAccountRef);
        configureDropdown(
                categoryDropdown,
                item.categoryChoices,
                item.categorySuggestionRef,
                selectedCategoryRef);

        accountDropdown.setEnabled(item.canConfirmMapping);
        categoryDropdown.setEnabled(item.canConfirmMapping);
        confirm.setEnabled(item.canConfirmMapping);

        confirm.setOnClickListener(v -> {
            if (!item.canConfirmMapping) return;
            if (selectedAccountRef[0].isEmpty() || selectedCategoryRef[0].isEmpty()) {
                Toast.makeText(
                        this,
                        "Choose both an account/card and a category.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            processMapping(
                    confirm,
                    item.eventId,
                    selectedAccountRef[0],
                    selectedCategoryRef[0]);
        });
    }

    private void configureDropdown(
            MaterialAutoCompleteTextView dropdown,
            List<TridevIntegrationReviewManager.Choice> choices,
            String suggestionRef,
            String[] selectedRef) {
        List<String> labels = new ArrayList<>();
        for (TridevIntegrationReviewManager.Choice choice : choices) {
            labels.add(choice.label);
        }
        dropdown.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                labels));
        dropdown.setThreshold(0);
        dropdown.setOnClickListener(v -> dropdown.showDropDown());

        int initialIndex = findChoiceIndex(choices, suggestionRef);
        if (initialIndex >= 0) {
            dropdown.setText(choices.get(initialIndex).label, false);
            selectedRef[0] = choices.get(initialIndex).canonicalRef;
        } else {
            // Fail closed: when no safe suggestion exists, force an explicit
            // user choice instead of silently defaulting to the first account.
            dropdown.setText("", false);
            selectedRef[0] = "";
        }

        dropdown.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < choices.size()) {
                selectedRef[0] = choices.get(position).canonicalRef;
            }
        });
    }

    private int findChoiceIndex(
            List<TridevIntegrationReviewManager.Choice> choices,
            String canonicalRef) {
        if (canonicalRef == null || canonicalRef.trim().isEmpty()) return -1;
        for (int index = 0; index < choices.size(); index++) {
            if (canonicalRef.equalsIgnoreCase(choices.get(index).canonicalRef)) return index;
        }
        return -1;
    }

    private void processMapping(
            MaterialButton button,
            String eventId,
            String accountRef,
            String categoryRef) {
        button.setEnabled(false);
        button.setText(R.string.integration_review_processing);

        new Thread(() -> {
            TridevIntegrationReviewManager.ConfirmResult result;
            try {
                result = reviewManager.confirmMappingsAndProcess(
                        eventId,
                        accountRef,
                        categoryRef);
            } catch (RuntimeException failure) {
                result = null;
            }

            final TridevIntegrationReviewManager.ConfirmResult finalResult = result;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                button.setText(R.string.integration_review_confirm);
                if (finalResult == null) {
                    button.setEnabled(true);
                    Toast.makeText(
                            this,
                            "Mapping could not be processed safely.",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(this, finalResult.message, Toast.LENGTH_LONG).show();
                loadReviews();
            });
        }, "IntegrationMappingConfirm").start();
    }

    private String buildSuggestionReason(TridevIntegrationReviewManager.ReviewItem item) {
        String account = item.accountReason.isEmpty()
                ? "Choose the correct MoneyManager account/card."
                : item.accountReason;
        String category = item.categoryReason.isEmpty()
                ? "Choose the correct existing category."
                : item.categoryReason;
        return "Account: " + account + "\nCategory: " + category;
    }

    private String formatAmount(long amountMinor, String direction) {
        double value = amountMinor / 100.0d;
        DecimalFormat format = new DecimalFormat("#,##0.00");
        String prefix = "CREDIT".equalsIgnoreCase(direction) ? "+₹" : "-₹";
        return prefix + format.format(value);
    }

    private String formatDate(long time) {
        long safeTime = time > 0L ? time : System.currentTimeMillis();
        return new SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.getDefault())
                .format(new Date(safeTime));
    }

    private void updateCounts(int pending, int mappable, int locked) {
        reviewPendingCount.setText(String.valueOf(pending));
        reviewMappableCount.setText(String.valueOf(mappable));
        reviewLockedCount.setText(String.valueOf(locked));
    }

    private void decorateStatus(TextView view, boolean locked) {
        int textColor = ContextCompat.getColor(
                this,
                locked ? R.color.orange : R.color.success);
        int background = ContextCompat.getColor(
                this,
                locked ? R.color.warning_surface : R.color.success_surface);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(background);
        shape.setCornerRadius(dp(20));
        view.setBackground(shape);
        view.setTextColor(textColor);
    }

    private void setWarningBackground(TextView view) {
        int background = ContextCompat.getColor(this, R.color.warning_surface);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(background);
        shape.setCornerRadius(dp(12));
        view.setBackground(shape);
    }

    private String safeLabel(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        return value.trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
