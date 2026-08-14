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
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Read-only mapping engine for cross-app finance events.
 *
 * It maps structured hints from SmartSMSPro, Family Hub and LoanManagerPro to
 * MoneyManagerPro's EXISTING accounts, credit cards and categories. It never
 * creates, renames, archives, merges or deletes finance data.
 *
 * Canonical references are stable local IDs:
 *   account:<id>
 *   card:<id>
 *   category:<id>
 *
 * Ambiguous/weak matches are returned as NEEDS_REVIEW. This class may inspect
 * several Room tables, so callers must run it on a worker/executor.
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
        /** Exact value MoneyManager currently uses in transaction.account/category. */
        @Nullable public final String transactionValue;
        /** Only populated for CATEGORY, e.g. Income/Expense when available. */
        @Nullable public final String categoryType;
        public final TridevIntegrationContract.MatchConfidence confidence;
        public final boolean needsReview;
        public final String reason;

        private MappingResult(
                MappingKind kind,
                @Nullable String canonicalRef,
                @Nullable String displayName,
                @Nullable String transactionValue,
                @Nullable String categoryType,
                TridevIntegrationContract.MatchConfidence confidence,
                boolean needsReview,
                String reason) {
            this.kind = kind;
            this.canonicalRef = canonicalRef;
            this.displayName = displayName;
            this.transactionValue = transactionValue;
            this.categoryType = categoryType;
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
                    null,
                    TridevIntegrationContract.MatchConfidence.UNMATCHED,
                    true,
                    reason);
        }
    }

    public static final class Catalog {
        public final List<CatalogItem> accounts;
        public final List<CatalogItem> creditCards;
        public final List<CategoryCatalogItem> categories;

        private Catalog(
                List<CatalogItem> accounts,
                List<CatalogItem> creditCards,
                List<CategoryCatalogItem> categories) {
            this.accounts = Collections.unmodifiableList(accounts);
            this.creditCards = Collections.unmodifiableList(creditCards);
            this.categories = Collections.unmodifiableList(categories);
        }
    }

    public static final class CatalogItem {
        public final String canonicalRef;
        public final String displayName;
        public final String transactionValue;
        @Nullable public final String type;
        public final boolean unavailableForNewPosting;

        private CatalogItem(
                String canonicalRef,
                String displayName,
                String transactionValue,
                @Nullable String type,
                boolean unavailableForNewPosting) {
            this.canonicalRef = canonicalRef;
            this.displayName = displayName;
            this.transactionValue = transactionValue;
            this.type = type;
            this.unavailableForNewPosting = unavailableForNewPosting;
        }
    }

    public static final class CategoryCatalogItem {
        public final String canonicalRef;
        public final String name;
        @Nullable public final String type;

        private CategoryCatalogItem(String canonicalRef, String name, @Nullable String type) {
            this.canonicalRef = canonicalRef;
            this.name = name;
            this.type = type;
        }
    }

    private final Context appContext;
    private final TridevMappingStore mappingStore;

    public TridevMoneyMappingEngine(Context context) {
        appContext = context.getApplicationContext();
        mappingStore = new TridevMappingStore(appContext);
    }

    /**
     * Resolve a bank/account/card hint. externalKey should be structured, for
     * example bank:hdfc:last4:4582, not a raw SMS body.
     */
    public MappingResult resolveAccount(
            @Nullable String externalKey,
            @Nullable String accountOrCardHint,
            @Nullable String lastFourHint) {
        Snapshot snapshot = readSnapshot();

        String remembered = mappingStore.findAccountAlias(externalKey);
        if (remembered != null) {
            AccountCandidate rememberedCandidate = findAccountByRef(snapshot, remembered);
            if (rememberedCandidate != null) {
                return accountResult(
                        rememberedCandidate,
                        TridevIntegrationContract.MatchConfidence.EXACT,
                        rememberedCandidate.unavailable,
                        rememberedCandidate.unavailable
                                ? "Confirmed mapping now points to archived/inactive destination"
                                : "User-confirmed account/card mapping");
            }
        }

        String directRef = accountCanonicalRefOrNull(accountOrCardHint);
        if (directRef != null) {
            AccountCandidate direct = findAccountByRef(snapshot, directRef);
            if (direct != null) {
                return accountResult(
                        direct,
                        TridevIntegrationContract.MatchConfidence.EXACT,
                        direct.unavailable,
                        direct.unavailable
                                ? "Stable ID is archived/inactive; review required"
                                : "Stable MoneyManager ID match");
            }
        }

        String lastFour = safeLastFour(lastFourHint);
        if (lastFour != null) {
            List<AccountCandidate> cardMatches = new ArrayList<>();
            for (AccountCandidate card : snapshot.cards) {
                if (lastFour.equals(card.lastFour)) cardMatches.add(card);
            }
            if (cardMatches.size() == 1) {
                AccountCandidate card = cardMatches.get(0);
                return accountResult(
                        card,
                        TridevIntegrationContract.MatchConfidence.EXACT,
                        card.unavailable,
                        card.unavailable
                                ? "Card suffix matches an inactive card"
                                : "Unique credit-card last-four match");
            }
            if (cardMatches.size() > 1) {
                List<AccountCandidate> narrowed = strongNameMatches(
                        cardMatches,
                        normalize(accountOrCardHint),
                        0.60d);
                if (narrowed.size() == 1) {
                    AccountCandidate card = narrowed.get(0);
                    return accountResult(
                            card,
                            TridevIntegrationContract.MatchConfidence.HIGH,
                            card.unavailable,
                            "Card suffix plus issuer/name match");
                }
                return MappingResult.unmatched(
                        "More than one MoneyManager card has the same last four digits");
            }
        }

        String hint = normalize(accountOrCardHint);
        if (hint.isEmpty()) {
            return MappingResult.unmatched("No account/card hint available");
        }

        List<AccountCandidate> all = new ArrayList<>();
        all.addAll(snapshot.accounts);
        all.addAll(snapshot.cards);

        List<AccountCandidate> exact = new ArrayList<>();
        for (AccountCandidate candidate : all) {
            if (hint.equals(normalize(candidate.displayName))
                    || hint.equals(normalize(candidate.transactionValue))) {
                exact.add(candidate);
            }
        }
        exact = collapseSameLedgerTarget(exact);
        if (exact.size() == 1) {
            AccountCandidate candidate = exact.get(0);
            return accountResult(
                    candidate,
                    TridevIntegrationContract.MatchConfidence.EXACT,
                    candidate.unavailable,
                    candidate.unavailable
                            ? "Exact destination is archived/inactive"
                            : "Exact MoneyManager account/card name match");
        }
        if (exact.size() > 1) {
            return MappingResult.unmatched("Multiple exact account/card matches found");
        }

        AccountCandidate best = null;
        double bestScore = 0d;
        double secondScore = 0d;
        for (AccountCandidate candidate : all) {
            double score = Math.max(
                    tokenSimilarity(hint, normalize(candidate.displayName)),
                    tokenSimilarity(hint, normalize(candidate.transactionValue)));
            if (score > bestScore) {
                secondScore = bestScore;
                bestScore = score;
                best = candidate;
            } else if (score > secondScore) {
                secondScore = score;
            }
        }

        if (best != null && bestScore >= 0.78d && bestScore - secondScore >= 0.12d) {
            return accountResult(
                    best,
                    TridevIntegrationContract.MatchConfidence.HIGH,
                    true,
                    "Strong unique name suggestion; confirm once before automatic use");
        }

        return MappingResult.unmatched("No safe unique MoneyManager account/card match");
    }

    public MappingResult resolveCategory(
            @Nullable String externalKey,
            @Nullable String categoryHint) {
        return resolveCategory(externalKey, categoryHint, null);
    }

    /**
     * Resolve only to an EXISTING category row. expectedType may be Income or
     * Expense. When supplied, a category of the wrong type is never auto-used.
     */
    public MappingResult resolveCategory(
            @Nullable String externalKey,
            @Nullable String categoryHint,
            @Nullable String expectedType) {
        Snapshot snapshot = readSnapshot();
        List<CategoryCandidate> eligible = filterCategories(snapshot.categories, expectedType);
        if (eligible.isEmpty()) {
            return MappingResult.unmatched(
                    expectedType == null
                            ? "No existing MoneyManager categories found"
                            : "No existing MoneyManager category of requested type found");
        }

        String remembered = mappingStore.findCategoryAlias(externalKey);
        if (remembered != null) {
            CategoryCandidate category = findCategoryByRef(eligible, remembered);
            if (category != null) {
                return categoryResult(
                        category,
                        TridevIntegrationContract.MatchConfidence.EXACT,
                        false,
                        "User-confirmed category mapping");
            }
        }

        String directRef = categoryCanonicalRefOrNull(categoryHint);
        if (directRef != null) {
            CategoryCandidate category = findCategoryByRef(eligible, directRef);
            if (category != null) {
                return categoryResult(
                        category,
                        TridevIntegrationContract.MatchConfidence.EXACT,
                        false,
                        "Stable MoneyManager category ID match");
            }
        }

        String hint = normalize(categoryHint);
        if (hint.isEmpty()) {
            return MappingResult.unmatched("No category hint available");
        }

        List<CategoryCandidate> exact = new ArrayList<>();
        for (CategoryCandidate category : eligible) {
            if (hint.equals(normalize(category.name))) exact.add(category);
        }
        if (exact.size() == 1) {
            return categoryResult(
                    exact.get(0),
                    TridevIntegrationContract.MatchConfidence.EXACT,
                    false,
                    "Exact existing MoneyManager category match");
        }
        if (exact.size() > 1) {
            return MappingResult.unmatched("Duplicate category names require review");
        }

        Set<String> family = semanticFamily(hint);
        if (!family.isEmpty()) {
            List<CategoryCandidate> semantic = new ArrayList<>();
            for (CategoryCandidate category : eligible) {
                if (!Collections.disjoint(family, semanticFamily(normalize(category.name)))) {
                    semantic.add(category);
                }
            }
            if (semantic.size() == 1) {
                return categoryResult(
                        semantic.get(0),
                        TridevIntegrationContract.MatchConfidence.HIGH,
                        true,
                        "Unique semantic suggestion; confirm once to remember it");
            }
            if (semantic.size() > 1) {
                return MappingResult.unmatched(
                        "More than one existing category fits this event");
            }
        }

        CategoryCandidate best = null;
        double bestScore = 0d;
        double secondScore = 0d;
        for (CategoryCandidate category : eligible) {
            double score = tokenSimilarity(hint, normalize(category.name));
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
                    "Strong category suggestion; confirmation required");
        }

        return MappingResult.unmatched("No safe unique existing category match");
    }

    /** Save only a target that still exists in MoneyManager. */
    public boolean rememberConfirmedAccountMapping(String externalKey, String canonicalRef) {
        Snapshot snapshot = readSnapshot();
        AccountCandidate target = findAccountByRef(snapshot, canonicalRef);
        if (target == null || target.unavailable) return false;
        mappingStore.rememberAccountAlias(externalKey, target.canonicalRef);
        return true;
    }

    /** Save only a stable category ID that still exists. */
    public boolean rememberConfirmedCategoryMapping(String externalKey, String canonicalRef) {
        Snapshot snapshot = readSnapshot();
        CategoryCandidate target = findCategoryByRef(snapshot.categories, canonicalRef);
        if (target == null) return false;
        mappingStore.rememberCategoryAlias(externalKey, target.canonicalRef);
        return true;
    }

    public void forgetAccountMapping(String externalKey) {
        mappingStore.forgetAccountAlias(externalKey);
    }

    public void forgetCategoryMapping(String externalKey) {
        mappingStore.forgetCategoryAlias(externalKey);
    }

    /** Read-only catalog for the future review/mapping UI. */
    public Catalog readCatalog() {
        Snapshot snapshot = readSnapshot();
        List<CatalogItem> accounts = new ArrayList<>();
        for (AccountCandidate item : snapshot.accounts) {
            accounts.add(new CatalogItem(
                    item.canonicalRef,
                    item.displayName,
                    item.transactionValue,
                    item.type,
                    item.unavailable));
        }
        List<CatalogItem> cards = new ArrayList<>();
        for (AccountCandidate item : snapshot.cards) {
            cards.add(new CatalogItem(
                    item.canonicalRef,
                    item.displayName,
                    item.transactionValue,
                    item.type,
                    item.unavailable));
        }
        List<CategoryCatalogItem> categories = new ArrayList<>();
        for (CategoryCandidate item : snapshot.categories) {
            categories.add(new CategoryCatalogItem(
                    item.canonicalRef,
                    item.name,
                    item.type));
        }
        return new Catalog(accounts, cards, categories);
    }

    private Snapshot readSnapshot() {
        SupportSQLiteDatabase db = DatabaseClient
                .getInstance(appContext)
                .getAppDatabase()
                .getOpenHelper()
                .getReadableDatabase();
        return new Snapshot(readAccounts(db), readCards(db), readCategories(db));
    }

    private List<AccountCandidate> readAccounts(SupportSQLiteDatabase db) {
        List<AccountCandidate> result = new ArrayList<>();
        try (Cursor cursor = db.query("SELECT * FROM accounts")) {
            int id = findColumn(cursor, "id");
            int name = findColumn(cursor, "name");
            int type = findColumn(cursor, "type");
            int archived = findColumn(cursor, "archived");
            if (id < 0 || name < 0) return result;

            while (cursor.moveToNext()) {
                long localId = cursor.getLong(id);
                String label = trimToNull(cursor.getString(name));
                if (localId <= 0L || label == null) continue;
                result.add(new AccountCandidate(
                        MappingKind.ACCOUNT,
                        "account:" + localId,
                        label,
                        label,
                        type >= 0 ? trimToNull(cursor.getString(type)) : null,
                        null,
                        archived >= 0 && cursor.getInt(archived) != 0));
            }
        } catch (RuntimeException ignored) {
            // Integration mapping fails closed without affecting MoneyManager.
        }
        return result;
    }

    private List<AccountCandidate> readCards(SupportSQLiteDatabase db) {
        List<AccountCandidate> result = new ArrayList<>();
        try (Cursor cursor = db.query("SELECT * FROM credit_cards")) {
            int id = findColumn(cursor, "id");
            int name = findColumn(cursor, "name");
            int lastFour = findColumn(cursor, "lastFour");
            int accountName = findColumn(cursor, "accountName");
            int active = findColumn(cursor, "active");
            if (id < 0 || name < 0) return result;

            while (cursor.moveToNext()) {
                long localId = cursor.getLong(id);
                String cardName = trimToNull(cursor.getString(name));
                if (localId <= 0L || cardName == null) continue;

                String suffix = lastFour >= 0
                        ? safeLastFour(cursor.getString(lastFour))
                        : null;
                String ledgerAccount = accountName >= 0
                        ? trimToNull(cursor.getString(accountName))
                        : null;
                if (ledgerAccount == null) ledgerAccount = cardName;

                String display = suffix == null || cardName.contains(suffix)
                        ? cardName
                        : cardName + " •••• " + suffix;
                boolean inactive = active >= 0 && cursor.getInt(active) == 0;
                result.add(new AccountCandidate(
                        MappingKind.CREDIT_CARD,
                        "card:" + localId,
                        display,
                        ledgerAccount,
                        "Credit Card",
                        suffix,
                        inactive));
            }
        } catch (RuntimeException ignored) {
            // Older/partial installs remain safe.
        }
        return result;
    }

    private List<CategoryCandidate> readCategories(SupportSQLiteDatabase db) {
        List<CategoryCandidate> result = new ArrayList<>();
        try (Cursor cursor = db.query("SELECT * FROM categories")) {
            int id = findColumn(cursor, "id");
            int name = findColumn(cursor, "name");
            int type = findColumn(cursor, "type");
            if (id < 0 || name < 0) return result;

            while (cursor.moveToNext()) {
                long localId = cursor.getLong(id);
                String label = trimToNull(cursor.getString(name));
                if (localId <= 0L || label == null) continue;
                result.add(new CategoryCandidate(
                        "category:" + localId,
                        label,
                        type >= 0 ? trimToNull(cursor.getString(type)) : null));
            }
        } catch (RuntimeException ignored) {
            // Do not infer categories from raw text when master catalog is unavailable.
        }
        return result;
    }

    @Nullable
    private AccountCandidate findAccountByRef(Snapshot snapshot, String ref) {
        String safe = accountCanonicalRefOrNull(ref);
        if (safe == null) return null;
        for (AccountCandidate item : snapshot.accounts) {
            if (safe.equals(item.canonicalRef)) return item;
        }
        for (AccountCandidate item : snapshot.cards) {
            if (safe.equals(item.canonicalRef)) return item;
        }
        return null;
    }

    @Nullable
    private CategoryCandidate findCategoryByRef(List<CategoryCandidate> categories, String ref) {
        String safe = categoryCanonicalRefOrNull(ref);
        if (safe == null) return null;
        for (CategoryCandidate item : categories) {
            if (safe.equals(item.canonicalRef)) return item;
        }
        return null;
    }

    private List<CategoryCandidate> filterCategories(
            List<CategoryCandidate> source,
            @Nullable String expectedType) {
        String expected = normalize(expectedType);
        if (expected.isEmpty()) return source;
        List<CategoryCandidate> result = new ArrayList<>();
        for (CategoryCandidate item : source) {
            if (expected.equals(normalize(item.type))) result.add(item);
        }
        return result;
    }

    private MappingResult accountResult(
            AccountCandidate candidate,
            TridevIntegrationContract.MatchConfidence confidence,
            boolean needsReview,
            String reason) {
        return new MappingResult(
                candidate.kind,
                candidate.canonicalRef,
                candidate.displayName,
                candidate.transactionValue,
                null,
                confidence,
                needsReview,
                reason);
    }

    private MappingResult categoryResult(
            CategoryCandidate candidate,
            TridevIntegrationContract.MatchConfidence confidence,
            boolean needsReview,
            String reason) {
        return new MappingResult(
                MappingKind.CATEGORY,
                candidate.canonicalRef,
                candidate.name,
                candidate.name,
                candidate.type,
                confidence,
                needsReview,
                reason);
    }

    private List<AccountCandidate> strongNameMatches(
            List<AccountCandidate> candidates,
            String hint,
            double threshold) {
        if (hint.isEmpty()) return candidates;
        List<AccountCandidate> result = new ArrayList<>();
        for (AccountCandidate candidate : candidates) {
            double score = Math.max(
                    tokenSimilarity(hint, normalize(candidate.displayName)),
                    tokenSimilarity(hint, normalize(candidate.transactionValue)));
            if (score >= threshold) result.add(candidate);
        }
        return result;
    }

    private List<AccountCandidate> collapseSameLedgerTarget(List<AccountCandidate> source) {
        List<AccountCandidate> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (AccountCandidate candidate : source) {
            String key = normalize(candidate.transactionValue);
            if (seen.add(key)) result.add(candidate);
        }
        return result;
    }

    private Set<String> semanticFamily(String normalizedText) {
        if (normalizedText == null || normalizedText.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> words = tokens(normalizedText);
        Set<String> result = new HashSet<>();
        addFamily(words, result, "grocery",
                "grocery", "groceries", "supermarket", "food", "ration", "kirana");
        addFamily(words, result, "fuel",
                "fuel", "petrol", "diesel", "cng", "gasoline");
        addFamily(words, result, "loan",
                "loan", "emi", "installment", "instalment");
        addFamily(words, result, "utility",
                "bill", "electricity", "power", "water", "utility", "utilities", "recharge");
        addFamily(words, result, "shopping",
                "shopping", "purchase", "amazon", "flipkart", "retail");
        addFamily(words, result, "medical",
                "medical", "medicine", "pharmacy", "hospital", "health", "doctor");
        addFamily(words, result, "salary",
                "salary", "payroll", "wages");
        addFamily(words, result, "refund",
                "refund", "reversal", "cashback");
        addFamily(words, result, "transfer",
                "transfer", "self", "internal");
        return result;
    }

    private void addFamily(
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
    private String accountCanonicalRefOrNull(@Nullable String value) {
        String safe = trimToNull(value);
        if (safe == null) return null;
        safe = safe.toLowerCase(Locale.ROOT);
        return safe.matches("(account|card):[0-9]+") ? safe : null;
    }

    @Nullable
    private String categoryCanonicalRefOrNull(@Nullable String value) {
        String safe = trimToNull(value);
        if (safe == null) return null;
        safe = safe.toLowerCase(Locale.ROOT);
        return safe.matches("category:[0-9]+") ? safe : null;
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
            for (int index = 0; index < cursor.getColumnCount(); index++) {
                if (requested.equalsIgnoreCase(cursor.getColumnName(index))) return index;
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

    private static final class AccountCandidate {
        final MappingKind kind;
        final String canonicalRef;
        final String displayName;
        final String transactionValue;
        @Nullable final String type;
        @Nullable final String lastFour;
        final boolean unavailable;

        AccountCandidate(
                MappingKind kind,
                String canonicalRef,
                String displayName,
                String transactionValue,
                @Nullable String type,
                @Nullable String lastFour,
                boolean unavailable) {
            this.kind = kind;
            this.canonicalRef = canonicalRef;
            this.displayName = displayName;
            this.transactionValue = transactionValue;
            this.type = type;
            this.lastFour = lastFour;
            this.unavailable = unavailable;
        }
    }

    private static final class CategoryCandidate {
        final String canonicalRef;
        final String name;
        @Nullable final String type;

        CategoryCandidate(String canonicalRef, String name, @Nullable String type) {
            this.canonicalRef = canonicalRef;
            this.name = name;
            this.type = type;
        }
    }

    private static final class Snapshot {
        final List<AccountCandidate> accounts;
        final List<AccountCandidate> cards;
        final List<CategoryCandidate> categories;

        Snapshot(
                List<AccountCandidate> accounts,
                List<AccountCandidate> cards,
                List<CategoryCandidate> categories) {
            this.accounts = accounts;
            this.cards = cards;
            this.categories = categories;
        }
    }
}
