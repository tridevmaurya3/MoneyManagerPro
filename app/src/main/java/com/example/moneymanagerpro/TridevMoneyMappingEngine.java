package com.example.moneymanagerpro;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.Nullable;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.moneymanagerpro.database.DatabaseClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Read-only resolver that connects structured events from SmartSMSPro,
 * Family Hub and LoanManagerPro to MoneyManagerPro's EXISTING accounts,
 * credit cards and categories.
 *
 * Safety rules:
 * 1) Never creates, renames, archives, merges or deletes finance data.
 * 2) Stable local IDs are preferred over display names.
 * 3) Card matching uses only safe metadata such as last four digits.
 * 4) Ambiguous matches always return NEEDS_REVIEW instead of guessing.
 * 5) User-confirmed aliases are stored by TridevMappingStore using hashed keys.
 *
 * Database inspection can be non-trivial on a large ledger. Call this class from
 * a worker/executor, not from the Android main thread.
 */
public final class TridevMoneyMappingEngine {

    public enum MappingKind {
        ACCOUNT,
        CREDIT_CARD,
        CATEGORY,
        NONE
    }

    public static final class MappingResult {
        public final MappingKind kind;
        @Nullable public final String canonicalRef;
        @Nullable public final String displayName;
        @Nullable public final String transactionValue;
        public final TridevIntegrationContract.MatchConfidence confidence;
        public final boolean needsReview;
        public final String reason;

        private MappingResult(
                MappingKind kind,
                @Nullable String canonicalRef,
                @Nullable String displayName,
                @Nullable String transactionValue,
                TridevIntegrationContract.MatchConfidence confidence,
                boolean needsReview,
                String reason) {
            this.kind = kind;
            this.canonicalRef = canonicalRef;
            this.displayName = displayName;
            this.transactionValue = transactionValue;
            this.confidence = confidence;
            this.needsReview = needsReview;
            this.reason = reason == null ? "" : reason;
        }

        public static MappingResult unmatched(String reason) {
            return new MappingResult(
                    MappingKind.NONE,
                    null,
                    null,
                    null,
                    TridevIntegrationContract.MatchConfidence.UNMATCHED,
                    true,
                    reason);
        }
    }

    private final Context appContext;
    private final TridevMappingStore mappingStore;

    public TridevMoneyMappingEngine(Context context) {
        appContext = context.getApplicationContext();
        mappingStore = new TridevMappingStore(appContext);
    }

    /**
     * Resolve a bank/account/card hint to an existing MoneyManager destination.
     * externalKey should be structured, e.g. "bank:hdfc:last4:4582".
     */
    public MappingResult resolveAccount(
            @Nullable String externalKey,
            @Nullable String accountOrCardHint,
            @Nullable String lastFourHint) {
        Snapshot snapshot = readSnapshot();

        String remembered = mappingStore.findAccountAlias(externalKey);
        if (remembered != null) {
            MappingResult result = resolveCanonicalRef(snapshot, remembered, true);
            if (result != null) return result;
        }

        String directRef = canonicalRefOrNull(accountOrCardHint);
        if (directRef != null) {
            MappingResult result = resolveCanonicalRef(snapshot, directRef, false);
            if (result != null) return result;
        }

        String lastFour = safeLastFour(lastFourHint);
        if (lastFour != null) {
            List<Candidate> cardMatches = new ArrayList<>();
            for (Candidate card : snapshot.cards) {
                if (lastFour.equals(card.lastFour)) cardMatches.add(card);
            }
            if (cardMatches.size() == 1) {
                return resultFor(
                        cardMatches.get(0),
                        TridevIntegrationContract.MatchConfidence.EXACT,
                        false,
                        "Unique credit-card last-four match");
            }
            if (cardMatches.size() > 1) {
                String normalizedHint = normalize(accountOrCardHint);
                List<Candidate> narrowed = exactOrStrongNameMatches(cardMatches, normalizedHint);
                if (narrowed.size() == 1) {
                    return resultFor(
                            narrowed.get(0),
                            TridevIntegrationContract.MatchConfidence.HIGH,
                            false,
                            "Card suffix plus issuer/name match");
                }
                return MappingResult.unmatched(
                        "More than one MoneyManager card has the same last four digits");
            }
        }

        String normalizedHint = normalize(accountOrCardHint);
        if (normalizedHint.isEmpty()) {
            return MappingResult.unmatched("No account/card hint available");
        }

        List<Candidate> all = new ArrayList<>();
        all.addAll(snapshot.accounts);
        all.addAll(snapshot.cards);

        List<Candidate> exact = new ArrayList<>();
        for (Candidate candidate : all) {
            if (normalizedHint.equals(normalize(candidate.displayName))
                    || normalizedHint.equals(normalize(candidate.transactionValue))) {
                exact.add(candidate);
            }
        }
        exact = collapseSameTransactionTarget(exact);
        if (exact.size() == 1) {
            Candidate candidate = exact.get(0);
            return resultFor(
                    candidate,
                    TridevIntegrationContract.MatchConfidence.EXACT,
                    candidate.archived,
                    candidate.archived
                            ? "Exact match points to an archived account; review required"
                            : "Exact MoneyManager account/card name match");
        }
        if (exact.size() > 1) {
            return MappingResult.unmatched("Multiple exact account/card matches found");
        }

        Candidate best = null;
        double bestScore = 0d;
        double secondScore = 0d;
        for (Candidate candidate : all) {
            double score = Math.max(
                    tokenSimilarity(normalizedHint, normalize(candidate.displayName)),
                    tokenSimilarity(normalizedHint, normalize(candidate.transactionValue)));
            if (score > bestScore) {
                secondScore = bestScore;
                bestScore = score;
                best = candidate;
            } else if (score > secondScore) {
                secondScore = score;
            }
        }

        if (best != null && bestScore >= 0.78d && bestScore - secondScore >= 0.12d) {
            return resultFor(
                    best,
                    TridevIntegrationContract.MatchConfidence.HIGH,
                    true,
                    "Strong name match; user confirmation required before first use");
        }

        return MappingResult.unmatched("No safe unique MoneyManager account/card match");
    }

    /** Resolve only to categories already present in MoneyManager history/config. */
    public MappingResult resolveCategory(
            @Nullable String externalKey,
            @Nullable String categoryHint) {
        Snapshot snapshot = readSnapshot();
        if (snapshot.categories.isEmpty()) {
            return MappingResult.unmatched("No existing MoneyManager categories found");
        }

        String remembered = mappingStore.findCategoryAlias(externalKey);
        if (remembered != null) {
            String current = findExactCategory(snapshot.categories, remembered);
            if (current != null) {
                return categoryResult(
                        current,
                        TridevIntegrationContract.MatchConfidence.EXACT,
                        false,
                        "User-confirmed category mapping");
            }
        }

        String normalizedHint = normalize(categoryHint);
        if (normalizedHint.isEmpty()) {
            return MappingResult.unmatched("No category hint available");
        }

        List<String> exact = new ArrayList<>();
        for (String category : snapshot.categories) {
            if (normalizedHint.equals(normalize(category))) exact.add(category);
        }
        if (exact.size() == 1) {
            return categoryResult(
                    exact.get(0),
                    TridevIntegrationContract.MatchConfidence.EXACT,
                    false,
                    "Exact existing MoneyManager category match");
        }
        if (exact.size() > 1) {
            return MappingResult.unmatched("Duplicate category labels require review");
        }

        Set<String> semanticWords = semanticFamily(normalizedHint);
        if (!semanticWords.isEmpty()) {
            List<String> semanticMatches = new ArrayList<>();
            for (String category : snapshot.categories) {
                Set<String> categoryWords = semanticFamily(normalize(category));
                if (!Collections.disjoint(semanticWords, categoryWords)) {
                    semanticMatches.add(category);
                }
            }
            if (semanticMatches.size() == 1) {
                return categoryResult(
                        semanticMatches.get(0),
                        TridevIntegrationContract.MatchConfidence.HIGH,
                        true,
                        "Unique semantic category suggestion; confirm once to remember it");
            }
            if (semanticMatches.size() > 1) {
                return MappingResult.unmatched(
                        "More than one existing MoneyManager category fits this event");
            }
        }

        String best = null;
        double bestScore = 0d;
        double secondScore = 0d;
        for (String category : snapshot.categories) {
            double score = tokenSimilarity(normalizedHint, normalize(category));
            if (score > bestScore) {
                secondScore = bestScore;
                bestScore = score;
                best = category;
            } else if (score > secondScore) {
                secondScore = score;
            }
        }
        if (best != null && bestScore >= 0.80d && bestScore - secondScore >= 0.15d) {
            return categoryResult(
                    best,
                    TridevIntegrationContract.MatchConfidence.HIGH,
                    true,
                    "Strong existing category match; confirmation required");
        }

        return MappingResult.unmatched("No safe unique existing category match");
    }

    /** Persist a user-confirmed account/card mapping only if the target still exists. */
    public boolean rememberConfirmedAccountMapping(
            String externalKey,
            String canonicalRef) {
        Snapshot snapshot = readSnapshot();
        MappingResult current = resolveCanonicalRef(snapshot, canonicalRef, false);
        if (current == null || current.canonicalRef == null) return false;
        mappingStore.rememberAccountAlias(externalKey, current.canonicalRef);
        return true;
    }

    /** Persist a user-confirmed category mapping only if that exact category exists. */
    public boolean rememberConfirmedCategoryMapping(
            String externalKey,
            String exactCategory) {
        Snapshot snapshot = readSnapshot();
        String existing = findExactCategory(snapshot.categories, exactCategory);
        if (existing == null) return false;
        mappingStore.rememberCategoryAlias(externalKey, existing);
        return true;
    }

    public void forgetAccountMapping(String externalKey) {
        mappingStore.forgetAccountAlias(externalKey);
    }

    public void forgetCategoryMapping(String externalKey) {
        mappingStore.forgetCategoryAlias(externalKey);
    }

    /**
     * Returns a read-only catalog suitable for a future "Choose account/category"
     * review screen. No underlying finance record is changed.
     */
    public Catalog readCatalog() {
        Snapshot snapshot = readSnapshot();
        List<CatalogItem> accounts = new ArrayList<>();
        for (Candidate candidate : snapshot.accounts) {
            accounts.add(new CatalogItem(
                    candidate.canonicalRef,
                    candidate.displayName,
                    candidate.transactionValue,
                    candidate.archived));
        }
        List<CatalogItem> cards = new ArrayList<>();
        for (Candidate candidate : snapshot.cards) {
            cards.add(new CatalogItem(
                    candidate.canonicalRef,
                    candidate.displayName,
                    candidate.transactionValue,
                    candidate.archived));
        }
        return new Catalog(accounts, cards, new ArrayList<>(snapshot.categories));
    }

    public static final class Catalog {
        public final List<CatalogItem> accounts;
        public final List<CatalogItem> creditCards;
        public final List<String> categories;

        private Catalog(
                List<CatalogItem> accounts,
                List<CatalogItem> creditCards,
                List<String> categories) {
            this.accounts = Collections.unmodifiableList(accounts);
            this.creditCards = Collections.unmodifiableList(creditCards);
            this.categories = Collections.unmodifiableList(categories);
        }
    }

    public static final class CatalogItem {
        public final String canonicalRef;
        public final String displayName;
        public final String transactionValue;
        public final boolean archived;

        private CatalogItem(
                String canonicalRef,
                String displayName,
                String transactionValue,
                boolean archived) {
            this.canonicalRef = canonicalRef;
            this.displayName = displayName;
            this.transactionValue = transactionValue;
            this.archived = archived;
        }
    }

    private Snapshot readSnapshot() {
        SupportSQLiteDatabase db = DatabaseClient
                .getInstance(appContext)
                .getAppDatabase()
                .getOpenHelper()
                .getReadableDatabase();

        List<Candidate> accounts = readAccounts(db);
        List<Candidate> cards = readCards(db);
        List<String> categories = readCategories(db);
        return new Snapshot(accounts, cards, categories);
    }

    private List<Candidate> readAccounts(SupportSQLiteDatabase db) {
        List<Candidate> result = new ArrayList<>();
        try (Cursor cursor = db.query("SELECT * FROM accounts")) {
            int idIndex = findColumn(cursor, "id", "accountId", "account_id");
            int nameIndex = findColumn(cursor, "name", "accountName", "account_name");
            int archivedIndex = findColumn(cursor, "archived", "isArchived", "is_archived");
            if (idIndex < 0 || nameIndex < 0) return result;

            while (cursor.moveToNext()) {
                long id = cursor.getLong(idIndex);
                String name = trimToNull(cursor.getString(nameIndex));
                if (id <= 0L || name == null) continue;
                boolean archived = archivedIndex >= 0 && cursor.getInt(archivedIndex) != 0;
                result.add(new Candidate(
                        MappingKind.ACCOUNT,
                        "account:" + id,
                        name,
                        name,
                        null,
                        archived));
            }
        } catch (RuntimeException ignored) {
            // Mapping must fail closed; existing MoneyManager operation continues.
        }
        return result;
    }

    private List<Candidate> readCards(SupportSQLiteDatabase db) {
        List<Candidate> result = new ArrayList<>();
        try (Cursor cursor = db.query("SELECT * FROM credit_cards")) {
            int idIndex = findColumn(cursor, "id", "creditCardId", "credit_card_id");
            int nameIndex = findColumn(cursor, "name", "cardName", "card_name");
            int suffixIndex = findColumn(
                    cursor,
                    "lastFour", "lastFourDigits", "last4", "last_four", "last_four_digits");
            int accountNameIndex = findColumn(
                    cursor,
                    "accountName", "account_name", "linkedAccount", "linked_account");
            int activeIndex = findColumn(cursor, "active", "isActive", "is_active");
            if (idIndex < 0 || nameIndex < 0) return result;

            while (cursor.moveToNext()) {
                long id = cursor.getLong(idIndex);
                String name = trimToNull(cursor.getString(nameIndex));
                if (id <= 0L || name == null) continue;

                String suffix = suffixIndex >= 0
                        ? safeLastFour(cursor.getString(suffixIndex))
                        : null;
                String accountName = accountNameIndex >= 0
                        ? trimToNull(cursor.getString(accountNameIndex))
                        : null;
                if (accountName == null) accountName = name;
                boolean inactive = activeIndex >= 0 && cursor.getInt(activeIndex) == 0;

                String display = suffix == null || name.contains(suffix)
                        ? name
                        : name + " •••• " + suffix;
                result.add(new Candidate(
                        MappingKind.CREDIT_CARD,
                        "card:" + id,
                        display,
                        accountName,
                        suffix,
                        inactive));
            }
        } catch (RuntimeException ignored) {
            // Older schema without credit_cards is still safe.
        }
        return result;
    }

    private List<String> readCategories(SupportSQLiteDatabase db) {
        LinkedHashSet<String> categories = new LinkedHashSet<>();
        collectDistinctColumn(db, "transactions", "category", categories);
        collectDistinctColumn(db, "budgets", "category", categories);
        collectDistinctColumn(db, "recurring_transactions", "category", categories);
        return new ArrayList<>(categories);
    }

    private void collectDistinctColumn(
            SupportSQLiteDatabase db,
            String table,
            String column,
            Set<String> target) {
        if (!hasColumn(db, table, column)) return;
        try (Cursor cursor = db.query(
                "SELECT DISTINCT " + column + " FROM " + table
                        + " WHERE " + column + " IS NOT NULL")) {
            while (cursor.moveToNext()) {
                String value = trimToNull(cursor.getString(0));
                if (value != null && value.length() <= 80) target.add(value);
            }
        } catch (RuntimeException ignored) {
            // Optional category source; keep whatever has already been found.
        }
    }

    private boolean hasColumn(SupportSQLiteDatabase db, String table, String column) {
        try (Cursor cursor = db.query("PRAGMA table_info(" + table + ")")) {
            int nameIndex = findColumn(cursor, "name");
            if (nameIndex < 0) return false;
            while (cursor.moveToNext()) {
                String value = cursor.getString(nameIndex);
                if (column.equalsIgnoreCase(value)) return true;
            }
        } catch (RuntimeException ignored) {
        }
        return false;
    }

    @Nullable
    private MappingResult resolveCanonicalRef(
            Snapshot snapshot,
            String canonicalRef,
            boolean remembered) {
        String safe = canonicalRefOrNull(canonicalRef);
        if (safe == null) return null;
        for (Candidate candidate : snapshot.accounts) {
            if (safe.equals(candidate.canonicalRef)) {
                return resultFor(
                        candidate,
                        TridevIntegrationContract.MatchConfidence.EXACT,
                        candidate.archived,
                        candidate.archived
                                ? "Mapped account is archived; review before new posting"
                                : remembered ? "User-confirmed account mapping" : "Stable account ID match");
            }
        }
        for (Candidate candidate : snapshot.cards) {
            if (safe.equals(candidate.canonicalRef)) {
                return resultFor(
                        candidate,
                        TridevIntegrationContract.MatchConfidence.EXACT,
                        candidate.archived,
                        candidate.archived
                                ? "Mapped card is inactive; review before new posting"
                                : remembered ? "User-confirmed card mapping" : "Stable card ID match");
            }
        }
        return null;
    }

    private MappingResult resultFor(
            Candidate candidate,
            TridevIntegrationContract.MatchConfidence confidence,
            boolean needsReview,
            String reason) {
        return new MappingResult(
                candidate.kind,
                candidate.canonicalRef,
                candidate.displayName,
                candidate.transactionValue,
                confidence,
                needsReview,
                reason);
    }

    private MappingResult categoryResult(
            String category,
            TridevIntegrationContract.MatchConfidence confidence,
            boolean needsReview,
            String reason) {
        return new MappingResult(
                MappingKind.CATEGORY,
                "category:" + normalize(category).replace(' ', '_'),
                category,
                category,
                confidence,
                needsReview,
                reason);
    }

    private List<Candidate> exactOrStrongNameMatches(
            List<Candidate> candidates,
            String normalizedHint) {
        if (normalizedHint.isEmpty()) return candidates;
        List<Candidate> exact = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (normalizedHint.equals(normalize(candidate.displayName))
                    || normalizedHint.equals(normalize(candidate.transactionValue))) {
                exact.add(candidate);
            }
        }
        if (!exact.isEmpty()) return exact;

        List<Candidate> strong = new ArrayList<>();
        for (Candidate candidate : candidates) {
            double score = Math.max(
                    tokenSimilarity(normalizedHint, normalize(candidate.displayName)),
                    tokenSimilarity(normalizedHint, normalize(candidate.transactionValue)));
            if (score >= 0.65d) strong.add(candidate);
        }
        return strong;
    }

    private List<Candidate> collapseSameTransactionTarget(List<Candidate> source) {
        List<Candidate> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Candidate candidate : source) {
            String key = normalize(candidate.transactionValue);
            if (seen.add(key)) result.add(candidate);
        }
        return result;
    }

    @Nullable
    private String findExactCategory(List<String> categories, String value) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) return null;
        String found = null;
        for (String category : categories) {
            if (!normalized.equals(normalize(category))) continue;
            if (found != null && !found.equals(category)) return null;
            found = category;
        }
        return found;
    }

    private Set<String> semanticFamily(String normalizedText) {
        if (normalizedText.isEmpty()) return Collections.emptySet();
        Set<String> words = tokens(normalizedText);
        Set<String> result = new HashSet<>();

        addSemanticIfMatches(words, result, "grocery",
                "grocery", "groceries", "supermarket", "food", "ration", "kirana");
        addSemanticIfMatches(words, result, "fuel",
                "fuel", "petrol", "diesel", "cng", "gasoline");
        addSemanticIfMatches(words, result, "loan",
                "loan", "emi", "installment", "instalment");
        addSemanticIfMatches(words, result, "utility",
                "bill", "electricity", "power", "water", "utility", "utilities", "recharge");
        addSemanticIfMatches(words, result, "shopping",
                "shopping", "purchase", "amazon", "flipkart", "retail");
        addSemanticIfMatches(words, result, "medical",
                "medical", "medicine", "pharmacy", "hospital", "health", "doctor");
        addSemanticIfMatches(words, result, "salary",
                "salary", "payroll", "wages", "income");
        addSemanticIfMatches(words, result, "refund",
                "refund", "reversal", "cashback");
        addSemanticIfMatches(words, result, "transfer",
                "transfer", "self", "internal");
        return result;
    }

    private void addSemanticIfMatches(
            Set<String> words,
            Set<String> result,
            String family,
            String... aliases) {
        for (String alias : aliases) {
            if (words.contains(alias)) {
                result.add(family);
                return;
            }
        }
    }

    private double tokenSimilarity(String left, String right) {
        Set<String> a = tokens(left);
        Set<String> b = tokens(right);
        if (a.isEmpty() || b.isEmpty()) return 0d;
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        if (intersection.isEmpty()) return 0d;
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection.size() / (double) union.size();
    }

    private Set<String> tokens(String normalized) {
        if (normalized == null || normalized.isEmpty()) return Collections.emptySet();
        Set<String> stop = new HashSet<>(Arrays.asList(
                "the", "a", "an", "from", "to", "at", "by", "via", "ending", "xx"));
        Set<String> result = new HashSet<>();
        for (String token : normalized.split(" ")) {
            if (token.length() < 2 || stop.contains(token)) continue;
            result.add(token);
        }
        return result;
    }

    private String normalize(@Nullable String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT)
                .replace('•', ' ')
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    @Nullable
    private String canonicalRefOrNull(@Nullable String value) {
        String safe = trimToNull(value);
        if (safe == null) return null;
        safe = safe.toLowerCase(Locale.ROOT);
        return safe.matches("(account|card):[0-9]+") ? safe : null;
    }

    @Nullable
    private String safeLastFour(@Nullable String value) {
        if (value == null) return null;
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.length() < 4) return null;
        return digits.substring(digits.length() - 4);
    }

    private int findColumn(Cursor cursor, String... names) {
        for (String requested : names) {
            for (int i = 0; i < cursor.getColumnCount(); i++) {
                if (requested.equalsIgnoreCase(cursor.getColumnName(i))) return i;
            }
        }
        return -1;
    }

    @Nullable
    private String trimToNull(@Nullable String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static final class Candidate {
        final MappingKind kind;
        final String canonicalRef;
        final String displayName;
        final String transactionValue;
        @Nullable final String lastFour;
        final boolean archived;

        Candidate(
                MappingKind kind,
                String canonicalRef,
                String displayName,
                String transactionValue,
                @Nullable String lastFour,
                boolean archived) {
            this.kind = kind;
            this.canonicalRef = canonicalRef;
            this.displayName = displayName;
            this.transactionValue = transactionValue;
            this.lastFour = lastFour;
            this.archived = archived;
        }
    }

    private static final class Snapshot {
        final List<Candidate> accounts;
        final List<Candidate> cards;
        final List<String> categories;

        Snapshot(
                List<Candidate> accounts,
                List<Candidate> cards,
                List<String> categories) {
            this.accounts = accounts;
            this.cards = cards;
            this.categories = categories;
        }
    }
}
