package com.example.moneymanagerpro.activities;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.TridevIntegrationHealthManager;
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
 * STEP 11 - Integration Health & Review Center.
 *
 * This activity stays private (android:exported="false"). Incoming finance
 * events still arrive only through the trusted integration providers. The screen
 * combines connection readiness, actual queue health, safe retry and the STEP 6
 * mapping/review workflow in one place.
 */
public class SmartSmsTransactionReviewActivity extends AppCompatActivity {

    private LinearLayout healthAppContainer;
    private TextView healthPendingCount;
    private TextView healthFailedCount;
    private TextView healthSyncedCount;
    private TextView healthMappingCount;
    private TextView healthStatus;
    private MaterialButton healthRetry;
    private MaterialButton healthReconciliation;

    private LinearLayout reviewEventContainer;
    private TextView reviewPendingCount;
    private TextView reviewMappableCount;
    private TextView reviewLockedCount;
    private TextView reviewStatus;
    private View reviewProgress;
    private View reviewEmptyCard;
    private MaterialButton reviewRefresh;

    private TridevIntegrationHealthManager healthManager;
    private TridevIntegrationReviewManager reviewManager;
    private int healthLoadGeneration = 0;
    private int reviewLoadGeneration = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smart_sms_transaction_review);

        healthManager = new TridevIntegrationHealthManager(getApplicationContext());
        reviewManager = new TridevIntegrationReviewManager(getApplicationContext());
        bindViews();
        setupActions();
        refreshAll();
    }

    private void bindViews() {
        healthAppContainer = findViewById(R.id.integrationHealthAppContainer);
        healthPendingCount = findViewById(R.id.healthPendingCount);
        healthFailedCount = findViewById(R.id.healthFailedCount);
        healthSyncedCount = findViewById(R.id.healthSyncedCount);
        healthMappingCount = findViewById(R.id.healthMappingCount);
        healthStatus = findViewById(R.id.healthStatus);
        healthRetry = findViewById(R.id.healthRetry);
        healthReconciliation = findViewById(R.id.healthReconciliation);

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
        reviewRefresh.setOnClickListener(v -> refreshAll());
        healthRetry.setOnClickListener(v -> retrySafeSync());
        healthReconciliation.setOnClickListener(v -> {
            try {
                startActivity(new Intent(this, SpecialReconciliationActivity.class));
            } catch (RuntimeException failure) {
                Toast.makeText(this, "Reconciliation Center could not be opened.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void refreshAll() {
        loadHealth();
        loadReviews();
    }

    private void loadHealth() {
        final int generation = ++healthLoadGeneration;
        healthRetry.setEnabled(false);
        healthStatus.setText(R.string.integration_health_loading);

        new Thread(() -> {
            TridevIntegrationHealthManager.Snapshot snapshot;
            try {
                snapshot = healthManager.loadSnapshot();
            } catch (RuntimeException failure) {
                snapshot = null;
            }

            final TridevIntegrationHealthManager.Snapshot result = snapshot;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || generation != healthLoadGeneration) return;
                healthRetry.setEnabled(true);
                if (result == null) {
                    healthAppContainer.removeAllViews();
                    healthPendingCount.setText("0");
                    healthFailedCount.setText("0");
                    healthSyncedCount.setText("0");
                    healthMappingCount.setText("0");
                    healthStatus.setText("Integration health could not be loaded safely.");
                    return;
                }
                renderHealth(result);
            });
        }, "IntegrationHealthLoad").start();
    }

    private void renderHealth(TridevIntegrationHealthManager.Snapshot snapshot) {
        healthAppContainer.removeAllViews();
        for (int index = 0; index < snapshot.apps.size(); index++) {
            TridevIntegrationHealthManager.AppHealth app = snapshot.apps.get(index);
            healthAppContainer.addView(createHealthRow(app));
            if (index < snapshot.apps.size() - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(ContextCompat.getColor(this, R.color.app_outline));
                LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(1));
                dividerParams.setMargins(dp(2), 0, dp(2), 0);
                healthAppContainer.addView(divider, dividerParams);
            }
        }

        healthPendingCount.setText(String.valueOf(snapshot.pendingCount));
        healthFailedCount.setText(String.valueOf(snapshot.failedCount));
        healthSyncedCount.setText(String.valueOf(snapshot.syncedCount));
        healthMappingCount.setText(String.valueOf(snapshot.totalMappings()));
        healthRetry.setEnabled(snapshot.pendingCount + snapshot.failedCount > 0);

        if (snapshot.lastActivityAt > 0L) {
            healthStatus.setText(getString(
                    R.string.integration_health_last_sync,
                    formatHealthTime(snapshot.lastActivityAt)));
        } else {
            healthStatus.setText(R.string.integration_health_no_activity);
        }
    }

    private View createHealthRow(TridevIntegrationHealthManager.AppHealth app) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(8), dp(2), dp(8));

        LinearLayout textArea = new LinearLayout(this);
        textArea.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText(app.label);
        title.setTextColor(ContextCompat.getColor(this, R.color.app_text_primary));
        title.setTextSize(12.5f);
        title.setTypeface(Typeface.DEFAULT_BOLD);

        TextView detail = new TextView(this);
        detail.setText(buildHealthDetail(app));
        detail.setTextColor(ContextCompat.getColor(this, R.color.app_text_secondary));
        detail.setTextSize(8.5f);
        detail.setMaxLines(3);

        textArea.addView(title);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        detailParams.setMargins(0, dp(2), 0, 0);
        textArea.addView(detail, detailParams);

        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f);
        row.addView(textArea, textParams);

        TextView chip = new TextView(this);
        chip.setText(readinessLabel(app.readiness));
        chip.setGravity(Gravity.CENTER);
        chip.setTextSize(8f);
        chip.setTypeface(Typeface.DEFAULT_BOLD);
        chip.setPadding(dp(8), dp(5), dp(8), dp(5));
        decorateReadiness(chip, app.readiness);

        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        chipParams.setMargins(dp(8), 0, 0, 0);
        row.addView(chip, chipParams);
        return row;
    }

    private String buildHealthDetail(TridevIntegrationHealthManager.AppHealth app) {
        StringBuilder text = new StringBuilder(app.detail);
        if (app.eventCount > 0) {
            text.append("\nEvents ").append(app.eventCount)
                    .append(" • Synced ").append(app.syncedCount);
            if (app.reviewCount > 0) text.append(" • Review ").append(app.reviewCount);
            if (app.failedCount > 0) text.append(" • Failed ").append(app.failedCount);
            if (app.pendingCount > 0) text.append(" • Pending ").append(app.pendingCount);
        }
        return text.toString();
    }

    private String readinessLabel(TridevIntegrationHealthManager.Readiness readiness) {
        switch (readiness) {
            case CONNECTED:
                return "CONNECTED";
            case READY:
                return "READY";
            case ACTION_REQUIRED:
                return "ACTION";
            case NOT_INSTALLED:
            default:
                return "OFFLINE";
        }
    }

    private void decorateReadiness(
            TextView view,
            TridevIntegrationHealthManager.Readiness readiness) {
        int textColor;
        int background;
        switch (readiness) {
            case CONNECTED:
                textColor = ContextCompat.getColor(this, R.color.success);
                background = ContextCompat.getColor(this, R.color.success_surface);
                break;
            case READY:
                textColor = ContextCompat.getColor(this, R.color.teal);
                background = ContextCompat.getColor(this, R.color.teal_surface);
                break;
            case ACTION_REQUIRED:
                textColor = ContextCompat.getColor(this, R.color.orange);
                background = ContextCompat.getColor(this, R.color.warning_surface);
                break;
            case NOT_INSTALLED:
            default:
                textColor = ContextCompat.getColor(this, R.color.app_text_secondary);
                background = ContextCompat.getColor(this, R.color.app_surface_muted);
                break;
        }
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(background);
        shape.setCornerRadius(dp(16));
        view.setBackground(shape);
        view.setTextColor(textColor);
    }

    private void retrySafeSync() {
        healthRetry.setEnabled(false);
        healthRetry.setText("Retrying…");

        new Thread(() -> {
            TridevIntegrationHealthManager.RetrySummary summary;
            try {
                summary = healthManager.retryPendingFailed(50);
            } catch (RuntimeException failure) {
                summary = null;
            }

            final TridevIntegrationHealthManager.RetrySummary result = summary;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                healthRetry.setText(R.string.integration_health_retry);
                if (result == null) {
                    healthRetry.setEnabled(true);
                    Toast.makeText(this, "Safe retry could not be completed.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
                }
                refreshAll();
            });
        }, "IntegrationSafeRetry").start();
    }

    private String formatHealthTime(long time) {
        return new SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.getDefault())
                .format(new Date(time));
    }

    private void loadReviews() {
        final int generation = ++reviewLoadGeneration;
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
                if (isFinishing() || isDestroyed() || generation != reviewLoadGeneration) return;
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
                refreshAll();
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
