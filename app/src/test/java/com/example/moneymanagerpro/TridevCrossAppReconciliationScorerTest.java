package com.example.moneymanagerpro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TridevCrossAppReconciliationScorerTest {

    private static final long BASE_TIME = 1_800_000_000_000L;

    @Test
    public void amountAndTimeOnly_neverAutoMerge() {
        TridevIntegrationContract.Event sms = event(
                "sms-1",
                TridevIntegrationContract.APP_SMART_SMS,
                TridevIntegrationContract.EventType.SMS_FINANCIAL_SIGNAL,
                185000L,
                BASE_TIME,
                "",
                "",
                "",
                "",
                TridevIntegrationContract.References.empty());
        TridevIntegrationContract.Event family = event(
                "family-1",
                TridevIntegrationContract.APP_FAMILY_HUB,
                TridevIntegrationContract.EventType.EXPENSE,
                185000L,
                BASE_TIME + 60_000L,
                "",
                "",
                "",
                "",
                TridevIntegrationContract.References.empty());

        TridevCrossAppReconciliationScorer.Evaluation evaluation =
                TridevCrossAppReconciliationScorer.scoreEvents(sms, family);

        assertFalse(evaluation.identityEvidence);
        assertFalse(TridevCrossAppReconciliationScorer.isAutoSafe(evaluation, 0));
        assertTrue(evaluation.score >= TridevCrossAppReconciliationScorer.REVIEW_SCORE);
    }

    @Test
    public void groceryWithCardMerchantAndCategory_canAutoMatchWhenUnique() {
        TridevIntegrationContract.Event sms = event(
                "sms-grocery",
                TridevIntegrationContract.APP_SMART_SMS,
                TridevIntegrationContract.EventType.SMS_FINANCIAL_SIGNAL,
                185000L,
                BASE_TIME,
                "HDFC Card ending 4582",
                "Reliance Smart",
                "Grocery",
                "",
                TridevIntegrationContract.References.empty());
        TridevIntegrationContract.Event grocery = event(
                "family-grocery",
                TridevIntegrationContract.APP_FAMILY_HUB,
                TridevIntegrationContract.EventType.GROCERY_PURCHASE,
                185000L,
                BASE_TIME + 2L * 60L * 1000L,
                "HDFC Credit Card 4582",
                "Reliance Smart",
                "Groceries",
                "",
                TridevIntegrationContract.References.empty());

        TridevCrossAppReconciliationScorer.Evaluation evaluation =
                TridevCrossAppReconciliationScorer.scoreEvents(sms, grocery);

        assertTrue(evaluation.identityEvidence);
        assertTrue(evaluation.score >= TridevCrossAppReconciliationScorer.AUTO_MATCH_SCORE);
        assertTrue(TridevCrossAppReconciliationScorer.isAutoSafe(evaluation, 0));
    }

    @Test
    public void closeSecondCandidate_blocksAutomaticChoice() {
        TridevIntegrationContract.Event sms = event(
                "sms-2",
                TridevIntegrationContract.APP_SMART_SMS,
                TridevIntegrationContract.EventType.SMS_FINANCIAL_SIGNAL,
                320000L,
                BASE_TIME,
                "SBI 1234",
                "Indian Oil",
                "Fuel",
                "",
                TridevIntegrationContract.References.empty());
        TridevIntegrationContract.Event family = event(
                "family-2",
                TridevIntegrationContract.APP_FAMILY_HUB,
                TridevIntegrationContract.EventType.EXPENSE,
                320000L,
                BASE_TIME + 60_000L,
                "SBI 1234",
                "Indian Oil",
                "Fuel",
                "",
                TridevIntegrationContract.References.empty());

        TridevCrossAppReconciliationScorer.Evaluation evaluation =
                TridevCrossAppReconciliationScorer.scoreEvents(sms, family);
        int closeSecond = Math.max(0,
                evaluation.score - TridevCrossAppReconciliationScorer.UNIQUE_BEST_MARGIN + 1);

        assertFalse(TridevCrossAppReconciliationScorer.isAutoSafe(
                evaluation,
                closeSecond));
    }

    @Test
    public void loanAndGroceryPurposes_conflictEvenWithSameAmountAndTime() {
        TridevIntegrationContract.Event loan = event(
                "loan-1",
                TridevIntegrationContract.APP_LOAN_MANAGER,
                TridevIntegrationContract.EventType.LOAN_PAYMENT,
                3044700L,
                BASE_TIME,
                "SBI 1234",
                "Home Loan",
                "Loan EMI",
                "",
                TridevIntegrationContract.References.empty());
        TridevIntegrationContract.Event grocery = event(
                "grocery-1",
                TridevIntegrationContract.APP_FAMILY_HUB,
                TridevIntegrationContract.EventType.GROCERY_PURCHASE,
                3044700L,
                BASE_TIME,
                "SBI 1234",
                "Supermarket",
                "Grocery",
                "",
                TridevIntegrationContract.References.empty());

        TridevCrossAppReconciliationScorer.Evaluation evaluation =
                TridevCrossAppReconciliationScorer.scoreEvents(loan, grocery);

        assertTrue(evaluation.hardConflict);
        assertEquals(0, evaluation.score);
        assertFalse(TridevCrossAppReconciliationScorer.isAutoSafe(evaluation, 0));
    }

    @Test
    public void explicitLinkedEvent_isDeterministic() {
        TridevIntegrationContract.Event canonical = event(
                "canonical-event",
                TridevIntegrationContract.APP_SMART_SMS,
                TridevIntegrationContract.EventType.SMS_FINANCIAL_SIGNAL,
                99900L,
                BASE_TIME,
                "",
                "",
                "",
                "",
                TridevIntegrationContract.References.empty());
        TridevIntegrationContract.Event projection = event(
                "projection-event",
                TridevIntegrationContract.APP_FAMILY_HUB,
                TridevIntegrationContract.EventType.EXPENSE,
                99900L,
                BASE_TIME + 10L * 60L * 1000L,
                "",
                "",
                "",
                "canonical-event",
                TridevIntegrationContract.References.empty());

        TridevCrossAppReconciliationScorer.Evaluation evaluation =
                TridevCrossAppReconciliationScorer.scoreEvents(canonical, projection);

        assertTrue(evaluation.deterministicLink);
        assertEquals(100, evaluation.score);
        assertTrue(TridevCrossAppReconciliationScorer.isAutoSafe(evaluation, 99));
    }

    @Test
    public void loanWindow_isLongerThanNormalPurchaseWindow() {
        TridevIntegrationContract.Event loan = event(
                "loan-window",
                TridevIntegrationContract.APP_LOAN_MANAGER,
                TridevIntegrationContract.EventType.LOAN_PAYMENT,
                3044700L,
                BASE_TIME,
                "",
                "Lender",
                "Loan EMI",
                "",
                TridevIntegrationContract.References.empty());
        TridevIntegrationContract.Event purchase = event(
                "purchase-window",
                TridevIntegrationContract.APP_FAMILY_HUB,
                TridevIntegrationContract.EventType.GROCERY_PURCHASE,
                10000L,
                BASE_TIME,
                "",
                "Store",
                "Grocery",
                "",
                TridevIntegrationContract.References.empty());

        assertTrue(
                TridevCrossAppReconciliationScorer.candidateWindowMillis(loan)
                        > TridevCrossAppReconciliationScorer.candidateWindowMillis(purchase));
    }

    private TridevIntegrationContract.Event event(
            String eventId,
            String sourceApp,
            TridevIntegrationContract.EventType type,
            long amountMinor,
            long occurredAt,
            String accountHint,
            String merchantHint,
            String categoryHint,
            String linkedEventId,
            TridevIntegrationContract.References references) {
        return new TridevIntegrationContract.Event(
                eventId,
                sourceApp,
                eventId + "-source",
                type,
                TridevIntegrationContract.Direction.DEBIT,
                TridevIntegrationContract.Scope.PERSONAL,
                amountMinor,
                "INR",
                occurredAt,
                occurredAt,
                accountHint,
                merchantHint,
                categoryHint,
                linkedEventId,
                "",
                TridevIntegrationContract.SyncState.PENDING,
                TridevIntegrationContract.MatchConfidence.UNMATCHED,
                references);
    }
}
