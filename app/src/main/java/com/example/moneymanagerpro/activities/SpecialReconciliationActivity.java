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
import com.example.moneymanagerpro.TridevAdvancedReconciliationManager;
import com.example.moneymanagerpro.TridevSpecialReconciliationManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** STEP 12 - private advanced MoneyManager reconciliation UI. */
public class SpecialReconciliationActivity extends AppCompatActivity {

    private LinearLayout eventContainer;
    private TextView pendingCount;
    private TextView transferCount;
    private TextView refundCount;
    private TextView duplicateCount;
    private TextView status;
    private View progress;
    private View emptyCard;
    private MaterialButton refresh;

    private TridevAdvancedReconciliationManager manager;
    private int loadGeneration = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_special_reconciliation);

        manager = new TridevAdvancedReconciliationManager(getApplicationContext());
        bindViews();
        findViewById(R.id.reconcileBack).setOnClickListener(v -> finish());
        refresh.setOnClickListener(v -> loadItems());
        loadItems();
    }

    private void bindViews() {
        eventContainer = findViewById(R.id.reconcileEventContainer);
        pendingCount = findViewById(R.id.reconcilePendingCount);
        transferCount = findViewById(R.id.reconcileTransferCount);
        refundCount = findViewById(R.id.reconcileRefundCount);
        duplicateCount = findViewById(R.id.reconcileDuplicateCount);
        status = findViewById(R.id.reconcileStatus);
        progress = findViewById(R.id.reconcileProgress);
        emptyCard = findViewById(R.id.reconcileEmptyCard);
        refresh = findViewById(R.id.reconcileRefresh);
    }

    private void loadItems() {
        final int generation = ++loadGeneration;
        refresh.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        status.setVisibility(View.VISIBLE);
        status.setText(R.string.reconcile_loading);
        emptyCard.setVisibility(View.GONE);

        new Thread(() -> {
            List<TridevAdvancedReconciliationManager.AdvancedItem> items;
            try {
                items = manager.loadItems(100);
            } catch (RuntimeException failure) {
                items = null;
            }
            final List<TridevAdvancedReconciliationManager.AdvancedItem> result = items;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || generation != loadGeneration) return;
                refresh.setEnabled(true);
                progress.setVisibility(View.GONE);
                if (result == null) {
                    eventContainer.removeAllViews();
                    status.setVisibility(View.VISIBLE);
                    status.setText("Reconciliation items could not be loaded safely.");
                    updateCounts(0, 0, 0, 0);
                    return;
                }
                renderItems(result);
            });
        }, "AdvancedReconciliationLoad").start();
    }

    private void renderItems(List<TridevAdvancedReconciliationManager.AdvancedItem> items) {
        eventContainer.removeAllViews();
        int transfers = 0;
        int refunds = 0;
        int duplicates = 0;
        for (TridevAdvancedReconciliationManager.AdvancedItem advanced : items) {
            TridevSpecialReconciliationManager.SpecialItem item = advanced.base;
            if (item.transferLike) transfers++;
            if (item.refundLike) refunds++;
            if (item.duplicateEvidence) duplicates++;
        }
        updateCounts(items.size(), transfers, refunds, duplicates);

        if (items.isEmpty()) {
            status.setVisibility(View.GONE);
            emptyCard.setVisibility(View.VISIBLE);
            return;
        }

        emptyCard.setVisibility(View.GONE);
        status.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (TridevAdvancedReconciliationManager.AdvancedItem item : items) {
            View card = inflater.inflate(
                    R.layout.item_special_reconciliation_event,
                    eventContainer,
                    false);
            bindCard(card, item);
            eventContainer.addView(card);
        }
    }

    private void bindCard(
            View card,
            TridevAdvancedReconciliationManager.AdvancedItem advanced) {
        TridevSpecialReconciliationManager.SpecialItem item = advanced.base;
        TextView source = card.findViewById(R.id.reconcileEventSource);
        TextView meta = card.findViewById(R.id.reconcileEventMeta);
        TextView amount = card.findViewById(R.id.reconcileEventAmount);
        TextView badge = card.findViewById(R.id.reconcileEventBadge);
        TextView signal = card.findViewById(R.id.reconcileEventSignal);

        source.setText(item.sourceLabel);
        meta.setText(item.eventType + " • " + formatDate(item.occurredAt));
        amount.setText(formatAmount(item.amountMinor, item.direction));
        amount.setTextColor(ContextCompat.getColor(
                this,
                "CREDIT".equalsIgnoreCase(item.direction)
                        ? R.color.success
                        : R.color.expense));
        decorateBadge(badge, item);
        signal.setText(buildSignal(item, advanced.candidateNotice));

        bindExistingSection(card, advanced);
        bindDuplicateSection(card, item);
        bindTransferSection(card, item);
        bindRefundSection(card, item);
        bindReturnToMapping(card, item);
    }

    private void bindExistingSection(
            View card,
            TridevAdvancedReconciliationManager.AdvancedItem advanced) {
        TridevSpecialReconciliationManager.SpecialItem item = advanced.base;
        View section = card.findViewById(R.id.reconcileExistingSection);
        MaterialAutoCompleteTextView dropdown =
                card.findViewById(R.id.reconcileExistingDropdown);
        MaterialButton button = card.findViewById(R.id.reconcileLinkExisting);

        if (advanced.rankedLedgerCandidates.isEmpty()) {
            section.setVisibility(View.GONE);
            return;
        }
        section.setVisibility(View.VISIBLE);

        List<String> labels = new ArrayList<>();
        for (TridevAdvancedReconciliationManager.RankedLedgerCandidate candidate
                : advanced.rankedLedgerCandidates) {
            labels.add(candidate.label);
        }
        dropdown.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                labels));
        dropdown.setThreshold(0);
        dropdown.setOnClickListener(v -> dropdown.showDropDown());

        final long[] selectedId = {0L};
        long suggested = parseLong(item.existingTransactionRef);
        int suggestedIndex = rankedCandidateIndex(
                advanced.rankedLedgerCandidates,
                suggested);
        if (suggestedIndex >= 0) {
            TridevAdvancedReconciliationManager.RankedLedgerCandidate candidate =
                    advanced.rankedLedgerCandidates.get(suggestedIndex);
            dropdown.setText(candidate.label, false);
            selectedId[0] = candidate.transactionId;
        } else {
            // No auto default: even the highest-scored candidate requires an
            // explicit user selection unless the queue already referenced it.
            dropdown.setText("", false);
        }

        dropdown.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < advanced.rankedLedgerCandidates.size()) {
                selectedId[0] = advanced.rankedLedgerCandidates.get(position).transactionId;
            }
        });

        button.setOnClickListener(v -> {
            if (selectedId[0] <= 0L) {
                Toast.makeText(this, R.string.reconcile_select_existing, Toast.LENGTH_SHORT).show();
                return;
            }
            runAction(button, () -> manager.linkExistingTransaction(
                    item.eventId,
                    selectedId[0]));
        });
    }

    private void bindDuplicateSection(
            View card,
            TridevSpecialReconciliationManager.SpecialItem item) {
        MaterialButton button = card.findViewById(R.id.reconcileConfirmQueuedDuplicate);
        boolean visible = !item.duplicateOfEventId.isEmpty();
        button.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
            button.setOnClickListener(v -> runAction(
                    button,
                    () -> manager.confirmQueuedDuplicate(item.eventId)));
        }
    }

    private void bindTransferSection(
            View card,
            TridevSpecialReconciliationManager.SpecialItem item) {
        View section = card.findViewById(R.id.reconcileTransferSection);
        if (!item.transferLike) {
            section.setVisibility(View.GONE);
            return;
        }
        section.setVisibility(View.VISIBLE);

        MaterialAutoCompleteTextView from = card.findViewById(R.id.reconcileFromDropdown);
        MaterialAutoCompleteTextView to = card.findViewById(R.id.reconcileToDropdown);
        MaterialButton button = card.findViewById(R.id.reconcileProcessTransfer);
        final String[] fromRef = {""};
        final String[] toRef = {""};

        configureChoiceDropdown(from, item.accountChoices, item.defaultFromRef, fromRef);
        configureChoiceDropdown(to, item.accountChoices, item.defaultToRef, toRef);

        button.setEnabled(item.accountChoices.size() >= 2);
        button.setOnClickListener(v -> {
            if (fromRef[0].isEmpty() || toRef[0].isEmpty()) {
                Toast.makeText(this, R.string.reconcile_select_transfer_accounts, Toast.LENGTH_SHORT).show();
                return;
            }
            if (fromRef[0].equalsIgnoreCase(toRef[0])) {
                Toast.makeText(this, "From and To accounts must be different.", Toast.LENGTH_SHORT).show();
                return;
            }
            runAction(button, () -> manager.processTransfer(
                    item.eventId,
                    fromRef[0],
                    toRef[0]));
        });
    }

    private void bindRefundSection(
            View card,
            TridevSpecialReconciliationManager.SpecialItem item) {
        View section = card.findViewById(R.id.reconcileRefundSection);
        if (!item.refundLike) {
            section.setVisibility(View.GONE);
            return;
        }
        section.setVisibility(View.VISIBLE);

        MaterialAutoCompleteTextView account =
                card.findViewById(R.id.reconcileRefundAccountDropdown);
        MaterialAutoCompleteTextView category =
                card.findViewById(R.id.reconcileRefundCategoryDropdown);
        MaterialButton button = card.findViewById(R.id.reconcileProcessRefund);
        final String[] accountRef = {""};
        final String[] categoryRef = {""};

        configureChoiceDropdown(
                account,
                item.accountChoices,
                item.defaultRefundAccountRef,
                accountRef);
        configureChoiceDropdown(
                category,
                item.incomeCategoryChoices,
                item.defaultRefundCategoryRef,
                categoryRef);

        button.setEnabled(!item.accountChoices.isEmpty() && !item.incomeCategoryChoices.isEmpty());
        button.setOnClickListener(v -> {
            if (accountRef[0].isEmpty() || categoryRef[0].isEmpty()) {
                Toast.makeText(this, R.string.reconcile_select_refund_fields, Toast.LENGTH_SHORT).show();
                return;
            }
            runAction(button, () -> manager.processRefund(
                    item.eventId,
                    accountRef[0],
                    categoryRef[0]));
        });
    }

    private void bindReturnToMapping(
            View card,
            TridevSpecialReconciliationManager.SpecialItem item) {
        MaterialButton button = card.findViewById(R.id.reconcileNotDuplicate);
        boolean visible = item.duplicateEvidence && !item.transferLike && !item.refundLike;
        button.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
            button.setOnClickListener(v -> runAction(
                    button,
                    () -> manager.returnToMappingReview(item.eventId)));
        }
    }

    private void configureChoiceDropdown(
            MaterialAutoCompleteTextView dropdown,
            List<TridevSpecialReconciliationManager.Choice> choices,
            String suggestionRef,
            String[] selectedRef) {
        List<String> labels = new ArrayList<>();
        for (TridevSpecialReconciliationManager.Choice choice : choices) {
            labels.add(choice.label);
        }
        dropdown.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                labels));
        dropdown.setThreshold(0);
        dropdown.setOnClickListener(v -> dropdown.showDropDown());

        int index = choiceIndex(choices, suggestionRef);
        if (index >= 0) {
            dropdown.setText(choices.get(index).label, false);
            selectedRef[0] = choices.get(index).canonicalRef;
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

    private void runAction(MaterialButton button, ActionCall call) {
        button.setEnabled(false);
        String originalText = button.getText() == null ? "" : button.getText().toString();
        button.setText(R.string.reconcile_processing);

        new Thread(() -> {
            TridevSpecialReconciliationManager.ActionResult result;
            try {
                result = call.run();
            } catch (RuntimeException failure) {
                result = null;
            }
            final TridevSpecialReconciliationManager.ActionResult finalResult = result;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                button.setText(originalText);
                if (finalResult == null) {
                    button.setEnabled(true);
                    Toast.makeText(this, "Reconciliation failed safely.", Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(this, finalResult.message, Toast.LENGTH_LONG).show();
                loadItems();
            });
        }, "AdvancedReconciliationAction").start();
    }

    private String buildSignal(
            TridevSpecialReconciliationManager.SpecialItem item,
            String candidateNotice) {
        StringBuilder text = new StringBuilder();
        text.append("Account: ").append(safe(item.accountHint, "Not identified"));
        text.append("\nCategory: ").append(safe(item.categoryHint, "Not identified"));
        if (!item.merchantHint.isEmpty()) {
            text.append("\nMerchant: ").append(item.merchantHint);
        }
        if (item.duplicateEvidence) {
            text.append("\nPossible duplicate evidence detected — choose an explicit action below.");
        }
        if (candidateNotice != null && !candidateNotice.trim().isEmpty()) {
            text.append("\nAdvanced match: ").append(candidateNotice.trim());
        }
        return text.toString();
    }

    private void decorateBadge(
            TextView badge,
            TridevSpecialReconciliationManager.SpecialItem item) {
        int textColor;
        int background;
        String label;
        if (item.duplicateEvidence) {
            label = "POSSIBLE DUPLICATE";
            textColor = ContextCompat.getColor(this, R.color.expense);
            background = ContextCompat.getColor(this, R.color.error_surface);
        } else if (item.refundLike) {
            label = "REFUND / REVERSAL";
            textColor = ContextCompat.getColor(this, R.color.success);
            background = ContextCompat.getColor(this, R.color.success_surface);
        } else {
            label = "TRANSFER / CARD PAYMENT";
            textColor = ContextCompat.getColor(this, R.color.orange);
            background = ContextCompat.getColor(this, R.color.warning_surface);
        }
        badge.setText(label);
        badge.setTextColor(textColor);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(background);
        shape.setCornerRadius(dp(20));
        badge.setBackground(shape);
    }

    private String formatAmount(long amountMinor, String direction) {
        DecimalFormat format = new DecimalFormat("#,##0.00");
        String prefix;
        if ("CREDIT".equalsIgnoreCase(direction)) prefix = "+₹";
        else if ("TRANSFER".equalsIgnoreCase(direction)) prefix = "₹";
        else prefix = "-₹";
        return prefix + format.format(amountMinor / 100.0d);
    }

    private String formatDate(long time) {
        long safeTime = time > 0L ? time : System.currentTimeMillis();
        return new SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.getDefault())
                .format(new Date(safeTime));
    }

    private int choiceIndex(
            List<TridevSpecialReconciliationManager.Choice> choices,
            String canonicalRef) {
        if (canonicalRef == null || canonicalRef.trim().isEmpty()) return -1;
        for (int index = 0; index < choices.size(); index++) {
            if (canonicalRef.equalsIgnoreCase(choices.get(index).canonicalRef)) return index;
        }
        return -1;
    }

    private int rankedCandidateIndex(
            List<TridevAdvancedReconciliationManager.RankedLedgerCandidate> candidates,
            long transactionId) {
        if (transactionId <= 0L) return -1;
        for (int index = 0; index < candidates.size(); index++) {
            if (candidates.get(index).transactionId == transactionId) return index;
        }
        return -1;
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value == null ? "" : value.trim());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private void updateCounts(int pending, int transfers, int refunds, int duplicates) {
        pendingCount.setText(String.valueOf(pending));
        transferCount.setText(String.valueOf(transfers));
        refundCount.setText(String.valueOf(refunds));
        duplicateCount.setText(String.valueOf(duplicates));
    }

    private String safe(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        return value.trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private interface ActionCall {
        TridevSpecialReconciliationManager.ActionResult run();
    }
}
