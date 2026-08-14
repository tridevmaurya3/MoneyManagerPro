package com.example.moneymanagerpro;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * STEP 12 - pure reconciliation scoring rules shared by the queue guard and
 * reconciliation UI.
 *
 * The scorer is intentionally conservative:
 * - amount/currency are hard requirements;
 * - semantic conflicts (for example Loan EMI vs Grocery) block auto matching;
 * - amount + time alone can suggest review but can never auto-merge;
 * - an automatic cross-app match needs identity evidence and a unique-best gap;
 * - explicit linked/reference identity is deterministic and may bypass the gap.
 *
 * Raw SMS bodies must never be supplied. Only structured event metadata is used.
 */
public final class TridevCrossAppReconciliationScorer {

    public static final int AUTO_MATCH_SCORE = 90;
    public static final int REVIEW_SCORE = 65;
    public static final int UNIQUE_BEST_MARGIN = 12;

    private static final long FIVE_MIN = 5L * 60L * 1000L;
    private static final long THIRTY_MIN = 30L * 60L * 1000L;
    private static final long TWO_HOURS = 2L * 60L * 60L * 1000L;
    private static final long SIX_HOURS = 6L * 60L * 60L * 1000L;
    private static final long DAY = 24L * 60L * 60L * 1000L;
    private static final long LOAN_WINDOW = 36L * 60L * 60L * 1000L;

    private TridevCrossAppReconciliationScorer() { }

    public static final class Evaluation {
        public final int score;
        public final boolean identityEvidence;
        public final boolean deterministicLink;
        public final boolean hardConflict;
        public final String reason;

        private Evaluation(
                int score,
                boolean identityEvidence,
                boolean deterministicLink,
                boolean hardConflict,
                String reason) {
            this.score = Math.max(0, Math.min(100, score));
            this.identityEvidence = identityEvidence;
            this.deterministicLink = deterministicLink;
            this.hardConflict = hardConflict;
            this.reason = reason == null ? "" : reason;
        }
    }

    @NonNull
    public static Evaluation scoreEvents(
            @Nullable TridevIntegrationContract.Event left,
            @Nullable TridevIntegrationContract.Event right) {
        if (left == null || right == null) {
            return conflict("Missing reconciliation event");
        }
        if (left.amountMinor <= 0L || left.amountMinor != right.amountMinor) {
            return conflict("Amounts do not match");
        }
        if (!currency(left).equals(currency(right))) {
            return conflict("Currencies do not match");
        }

        if (deterministicallyLinked(left, right)) {
            return new Evaluation(
                    100,
                    true,
                    true,
                    false,
                    "Explicit cross-app reference identifies the same transaction");
        }

        int score = 25; // exact amount
        boolean identity = false;

        if (left.direction == right.direction) {
            score += 15;
        } else if (left.direction == TridevIntegrationContract.Direction.UNKNOWN
                || right.direction == TridevIntegrationContract.Direction.UNKNOWN) {
            score += 5;
        } else {
            return conflict("Debit/credit direction conflicts");
        }

        String leftGroup = semanticGroup(left);
        String rightGroup = semanticGroup(right);
        if (semanticConflict(leftGroup, rightGroup)) {
            return conflict("Transaction purposes conflict: " + leftGroup + " vs " + rightGroup);
        }
        if (!leftGroup.isEmpty() && leftGroup.equals(rightGroup)) {
            score += 10;
        }

        long delta = Math.abs(effectiveTime(left) - effectiveTime(right));
        long allowed = Math.max(candidateWindowMillis(left), candidateWindowMillis(right));
        if (delta > allowed) {
            return conflict("Transaction times are too far apart");
        }
        if (delta <= FIVE_MIN) score += 25;
        else if (delta <= THIRTY_MIN) score += 20;
        else if (delta <= TWO_HOURS) score += 14;
        else if (delta <= SIX_HOURS) score += 8;
        else if (delta <= DAY) score += 4;
        else score += 2;

        String leftMoneyAccount = safeRef(left.references == null
                ? "" : left.references.moneyManagerAccountId);
        String rightMoneyAccount = safeRef(right.references == null
                ? "" : right.references.moneyManagerAccountId);
        if (!leftMoneyAccount.isEmpty() && leftMoneyAccount.equals(rightMoneyAccount)) {
            score += 30;
            identity = true;
        } else {
            String leftLast4 = TridevEventFingerprint.lastFour(left.accountHint);
            String rightLast4 = TridevEventFingerprint.lastFour(right.accountHint);
            String leftAccount = TridevEventFingerprint.normalizeHint(left.accountHint);
            String rightAccount = TridevEventFingerprint.normalizeHint(right.accountHint);
            if (!leftLast4.isEmpty() && leftLast4.equals(rightLast4)) {
                score += 25;
                identity = true;
            } else if (!leftAccount.isEmpty() && leftAccount.equals(rightAccount)) {
                score += 20;
                identity = true;
            } else if (TridevEventFingerprint.tokenSimilarity(leftAccount, rightAccount) >= 0.72d) {
                score += 10;
                identity = true;
            }
        }

        String leftMerchant = TridevEventFingerprint.normalizeHint(left.merchantHint);
        String rightMerchant = TridevEventFingerprint.normalizeHint(right.merchantHint);
        if (!leftMerchant.isEmpty() && leftMerchant.equals(rightMerchant)) {
            score += 18;
            identity = true;
        } else {
            double merchantSimilarity = TridevEventFingerprint.tokenSimilarity(
                    leftMerchant,
                    rightMerchant);
            if (merchantSimilarity >= 0.72d) {
                score += 12;
                identity = true;
            } else if (merchantSimilarity >= 0.50d) {
                score += 6;
            }
        }

        String leftCategory = TridevEventFingerprint.normalizeHint(left.categoryHint);
        String rightCategory = TridevEventFingerprint.normalizeHint(right.categoryHint);
        if (!leftCategory.isEmpty() && leftCategory.equals(rightCategory)) {
            score += 8;
        } else if (TridevEventFingerprint.tokenSimilarity(
                leftCategory,
                rightCategory) >= 0.60d) {
            score += 4;
        }

        if (left.scope == right.scope && left.scope != TridevIntegrationContract.Scope.UNKNOWN) {
            score += 3;
        }
        if (!safe(left.sourceApp).equals(safe(right.sourceApp))) {
            score += 5;
        } else {
            // Same-source records must rely on exact source-record idempotency,
            // not fuzzy matching.
            score -= 20;
        }

        String leftFingerprint = safe(left.dedupeFingerprint);
        String rightFingerprint = safe(right.dedupeFingerprint);
        if (!leftFingerprint.isEmpty() && leftFingerprint.equals(rightFingerprint)) {
            score += identity ? 10 : 4;
        }

        return new Evaluation(
                score,
                identity,
                false,
                false,
                identity
                        ? "Amount/time plus account or merchant identity agree"
                        : "Amount/time similarity has no strong identity evidence");
    }

    @NonNull
    public static Evaluation scoreLedger(
            @Nullable TridevIntegrationContract.Event event,
            @Nullable String ledgerType,
            @Nullable String ledgerAccount,
            @Nullable String ledgerCategory,
            @Nullable String ledgerNote,
            long ledgerTime) {
        if (event == null || event.amountMinor <= 0L || ledgerTime <= 0L) {
            return conflict("Ledger candidate is incomplete");
        }

        String marker = "tridev_event:" + safe(event.eventId).toLowerCase(Locale.ROOT);
        String normalizedNote = safe(ledgerNote).toLowerCase(Locale.ROOT);
        if (!marker.equals("tridev_event:") && normalizedNote.contains(marker)) {
            return new Evaluation(100, true, true, false,
                    "Deterministic integration marker matches this ledger row");
        }

        String expectedType = expectedLedgerType(event);
        String actualType = safe(ledgerType).toUpperCase(Locale.ROOT);
        if (!expectedType.isEmpty() && !expectedType.equals(actualType)) {
            return conflict("Ledger transaction type conflicts");
        }

        long delta = Math.abs(effectiveTime(event) - ledgerTime);
        long allowed = isLoanLike(event) ? LOAN_WINDOW : DAY;
        if (delta > allowed) return conflict("Ledger transaction time is too far away");

        int score = 25;
        boolean identity = false;
        if (!expectedType.isEmpty()) score += 15;

        if (delta <= FIVE_MIN) score += 25;
        else if (delta <= THIRTY_MIN) score += 20;
        else if (delta <= TWO_HOURS) score += 15;
        else if (delta <= SIX_HOURS) score += 8;
        else if (delta <= DAY) score += 3;
        else score += 2;

        String eventLast4 = TridevEventFingerprint.lastFour(event.accountHint);
        String ledgerLast4 = TridevEventFingerprint.lastFour(ledgerAccount);
        String eventAccount = TridevEventFingerprint.normalizeHint(event.accountHint);
        String normalizedLedgerAccount = TridevEventFingerprint.normalizeHint(ledgerAccount);
        if (!eventLast4.isEmpty() && eventLast4.equals(ledgerLast4)) {
            score += 25;
            identity = true;
        } else if (!eventAccount.isEmpty() && eventAccount.equals(normalizedLedgerAccount)) {
            score += 20;
            identity = true;
        } else if (TridevEventFingerprint.tokenSimilarity(
                eventAccount,
                normalizedLedgerAccount) >= 0.65d) {
            score += 12;
            identity = true;
        }

        String eventCategory = TridevEventFingerprint.normalizeHint(event.categoryHint);
        String normalizedLedgerCategory = TridevEventFingerprint.normalizeHint(ledgerCategory);
        if (!eventCategory.isEmpty() && eventCategory.equals(normalizedLedgerCategory)) {
            score += 10;
        } else if (semanticCategoryCompatible(eventCategory, normalizedLedgerCategory)) {
            score += 8;
        } else if (TridevEventFingerprint.tokenSimilarity(
                eventCategory,
                normalizedLedgerCategory) >= 0.60d) {
            score += 5;
        }

        String merchant = TridevEventFingerprint.normalizeHint(event.merchantHint);
        if (!merchant.isEmpty()) {
            double noteSimilarity = TridevEventFingerprint.tokenSimilarity(merchant, ledgerNote);
            if (noteSimilarity >= 0.75d) {
                score += 15;
                identity = true;
            } else if (noteSimilarity >= 0.50d) {
                score += 8;
            }
        }

        return new Evaluation(
                score,
                identity,
                false,
                false,
                identity
                        ? "Ledger amount/time plus account or merchant identity agree"
                        : "Ledger amount/time match still lacks identity evidence");
    }

    public static boolean isAutoSafe(@Nullable Evaluation best, int secondBestScore) {
        if (best == null || best.hardConflict) return false;
        if (best.deterministicLink) return true;
        return best.score >= AUTO_MATCH_SCORE
                && best.identityEvidence
                && best.score - Math.max(0, secondBestScore) >= UNIQUE_BEST_MARGIN;
    }

    public static boolean shouldReview(@Nullable Evaluation best) {
        return best != null && !best.hardConflict && best.score >= REVIEW_SCORE;
    }

    public static long candidateWindowMillis(@Nullable TridevIntegrationContract.Event event) {
        return isLoanLike(event) ? LOAN_WINDOW : SIX_HOURS;
    }

    private static boolean deterministicallyLinked(
            TridevIntegrationContract.Event left,
            TridevIntegrationContract.Event right) {
        if (sameNonEmpty(left.linkedEventId, right.eventId)
                || sameNonEmpty(right.linkedEventId, left.eventId)) return true;
        if (left.references == null || right.references == null) return false;
        return sameNonEmpty(left.references.familyGroceryRecordId,
                right.references.familyGroceryRecordId)
                || sameNonEmpty(left.references.familyFinanceRecordId,
                right.references.familyFinanceRecordId)
                || sameNonEmpty(left.references.loanManagerPaymentId,
                right.references.loanManagerPaymentId)
                || sameNonEmpty(left.references.moneyManagerTransactionId,
                right.references.moneyManagerTransactionId);
    }

    private static boolean sameNonEmpty(@Nullable String left, @Nullable String right) {
        String a = safeRef(left);
        String b = safeRef(right);
        return !a.isEmpty() && a.equals(b);
    }

    private static String semanticGroup(TridevIntegrationContract.Event event) {
        if (event.eventType == TridevIntegrationContract.EventType.LOAN_PAYMENT) return "loan";
        if (event.eventType == TridevIntegrationContract.EventType.GROCERY_PURCHASE) return "grocery";
        if (event.eventType == TridevIntegrationContract.EventType.REFUND) return "refund";
        if (event.eventType == TridevIntegrationContract.EventType.TRANSFER
                || event.direction == TridevIntegrationContract.Direction.TRANSFER) return "transfer";
        if (event.eventType == TridevIntegrationContract.EventType.BILL_PAYMENT) return "bill";

        String text = (TridevEventFingerprint.normalizeHint(event.categoryHint) + " "
                + TridevEventFingerprint.normalizeHint(event.merchantHint)).trim();
        if (containsAny(text, "emi", "loan", "prepayment", "mortgage")) return "loan";
        if (containsAny(text, "grocery", "groceries", "supermarket", "mart")) return "grocery";
        if (containsAny(text, "refund", "reversal", "cashback")) return "refund";
        if (containsAny(text, "transfer", "atm", "cash withdrawal", "card payment")) return "transfer";
        if (containsAny(text, "electricity", "utility", "bill", "water", "gas")) return "bill";
        if (containsAny(text, "fuel", "petrol", "diesel")) return "fuel";
        if (containsAny(text, "salary", "income")) return "income";
        return "";
    }

    private static boolean semanticConflict(String left, String right) {
        if (left.isEmpty() || right.isEmpty() || left.equals(right)) return false;
        if (("loan".equals(left) && "grocery".equals(right))
                || ("grocery".equals(left) && "loan".equals(right))) return true;
        if (("refund".equals(left) && !"income".equals(right))
                || ("refund".equals(right) && !"income".equals(left))) return true;
        if (("transfer".equals(left) && !"transfer".equals(right))
                || ("transfer".equals(right) && !"transfer".equals(left))) return true;
        return false;
    }

    private static boolean semanticCategoryCompatible(String left, String right) {
        if (left.isEmpty() || right.isEmpty()) return false;
        if (left.equals(right)) return true;
        if (containsAny(left, "emi", "loan", "prepayment")
                && containsAny(right, "emi", "loan", "prepayment")) return true;
        if (containsAny(left, "grocery", "groceries")
                && containsAny(right, "grocery", "groceries")) return true;
        if (containsAny(left, "refund", "reversal")
                && containsAny(right, "refund", "reversal")) return true;
        return false;
    }

    private static boolean isLoanLike(@Nullable TridevIntegrationContract.Event event) {
        return event != null && "loan".equals(semanticGroup(event));
    }

    private static String expectedLedgerType(TridevIntegrationContract.Event event) {
        if (event.direction == TridevIntegrationContract.Direction.DEBIT) return "EXPENSE";
        if (event.direction == TridevIntegrationContract.Direction.CREDIT) return "INCOME";
        switch (event.eventType) {
            case INCOME:
            case REFUND:
                return "INCOME";
            case EXPENSE:
            case GROCERY_PURCHASE:
            case BILL_PAYMENT:
            case LOAN_PAYMENT:
                return "EXPENSE";
            default:
                return "";
        }
    }

    private static long effectiveTime(TridevIntegrationContract.Event event) {
        return event.occurredAt > 0L ? event.occurredAt : event.createdAt;
    }

    private static String currency(TridevIntegrationContract.Event event) {
        String value = safe(event.currency).toUpperCase(Locale.ROOT);
        return value.isEmpty() ? TridevIntegrationContract.DEFAULT_CURRENCY : value;
    }

    private static String safeRef(@Nullable String value) {
        return safe(value).toLowerCase(Locale.ROOT);
    }

    private static String safe(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean containsAny(String text, String... values) {
        String safe = text == null ? "" : text.toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (safe.contains(value)) return true;
        }
        return false;
    }

    private static Evaluation conflict(String reason) {
        return new Evaluation(0, false, false, true, reason);
    }
}
