package com.example.moneymanagerpro;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.example.moneymanagerpro.database.AppDatabase;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Loan;
import com.example.moneymanagerpro.model.LoanPayment;
import com.example.moneymanagerpro.model.Account;
import com.example.moneymanagerpro.model.Category;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Mirrors canonical LoanManager loans; all cross-app writes run off the UI thread. */
public final class LinkedLoanBridge {
    public static final Uri URI = Uri.parse("content://com.tridev.loanmanagerpro.linkedloans");
    private static final Object LOCK = new Object();
    private static final String LINK = "[LMP_LINK:", REV = "[LMP_REV:", ORIGIN = "[MM_ORIGIN:";
    private LinkedLoanBridge() { }

    public static boolean isPending(Loan loan) { return !markerValue(loan.getNote(), ORIGIN).isEmpty(); }
    public static boolean isLinked(Loan loan) { return !key(loan).isEmpty(); }
    public static String key(Loan loan) { return LinkedLoanIdentity.key(loan.getNote()); }
    public static String humanNote(String note) { return LinkedLoanIdentity.humanNote(note); }
    public static double paymentLimit(Loan loan, boolean extraPayment) {
        if (!isLinked(loan) || extraPayment) return loan.getOutstandingAmount();
        return loan.getOutstandingAmount() + Math.max(0,
                Math.round(loan.getOutstandingAmount() * loan.getInterestRate() / 1200d));
    }
    public static String remainingText(Loan loan) {
        String value = markerValue(loan.getNote(), "[LMP_TERM:");
        return "-1".equals(value) || value.isEmpty() ? "Review EMI settings" : value + " month(s)";
    }
    public static String payoffText(Loan loan) { return markerValue(loan.getNote(), "[LMP_PAYOFF:"); }
    public static void markNew(Loan loan) {
        // LoanManager is a borrowing/principal tracker. Native loans-given remain independent.
        if (!"Loan Given".equalsIgnoreCase(loan.getLoanType())) {
            loan.setNote(humanNote(loan.getNote()) + "\n" + ORIGIN + UUID.randomUUID() + "]");
        }
    }
    public static void sync(Context context) {
        synchronized (LOCK) {
            JSONArray catalog = catalog(context);
            AppDatabase database = db(context);
            List<Loan> local = database.loanDao().getAllLoans();
            String problem = "";
            // Only loans newly created after this feature are exported automatically.
            // Legacy independent trackers are matched conservatively or linked explicitly.
            for (Loan loan : local) {
                String origin = markerValue(loan.getNote(), ORIGIN);
                if (!isLinked(loan) && !origin.isEmpty()) {
                    try {
                        boolean sameName = false;
                        for (int j = 0; j < catalog.length(); j++) {
                            JSONObject existing = catalog.optJSONObject(j);
                            if (existing != null && loan.getPersonName().trim().equalsIgnoreCase(existing.optString("name").trim())
                                    && !origin.equals(existing.optString("origin"))) sameName = true;
                        }
                        if (sameName) {
                            problem = "Link the existing LoanManager loan for " + loan.getPersonName();
                            continue;
                        }
                        Bundle create = details(loan); create.putString("action", "CREATE");
                        create.putString("token", origin);
                        JSONObject remote = command(context, create);
                        apply(context, remote, loan.getId());
                    } catch (RuntimeException invalid) {
                        problem = loan.getPersonName() + ": " + invalid.getMessage();
                    }
                }
            }
            local = database.loanDao().getAllLoans();
            for (int i = 0; i < catalog.length(); i++) {
                JSONObject entry = catalog.optJSONObject(i);
                if (entry == null || !"INR".equals(entry.optString("currency"))) continue;
                String remoteKey = entry.optString("key");
                Loan linked = findLinked(local, remoteKey);
                if (linked == null) {
                    List<Loan> candidates = new ArrayList<>();
                    for (Loan loan : local) {
                        if (isLinked(loan) || "Loan Given".equalsIgnoreCase(loan.getLoanType())) continue;
                        if (loan.getPersonName().trim().equalsIgnoreCase(entry.optString("name").trim())) candidates.add(loan);
                    }
                    // Same-name legacy records need an explicit link, including matching amounts:
                    // a lender can have multiple distinct loans and labels are not identity.
                    if (!candidates.isEmpty()) continue;
                }
                try {
                    apply(context, snapshot(context, remoteKey), linked == null ? 0 : linked.getId());
                } catch (RuntimeException unavailable) { problem = unavailable.getMessage(); }
            }
            if (!problem.isEmpty()) throw new IllegalStateException(problem);
        }
    }
    public static JSONArray catalog(Context context) {
        Bundle response = call(context, "catalog_v1", new Bundle());
        requireOK(response);
        try { return new JSONArray(response.getString("catalog", "[]")); }
        catch (org.json.JSONException failure) { throw new IllegalStateException("Invalid loan catalog", failure); }
    }
    public static JSONObject snapshot(Context context, String key) {
        Bundle input = new Bundle(); input.putString("key", key);
        Bundle response = call(context, "snapshot_v1", input); requireOK(response); return json(response);
    }
    public static void link(Context context, Loan loan, String remoteKey) {
        synchronized (LOCK) {
            List<Loan> all = db(context).loanDao().getAllLoans();
            Loan owner = findLinked(all, remoteKey);
            if (owner != null && owner.getId() != loan.getId()) throw new IllegalStateException("This loan is already linked to another tracker");
            if (isLinked(loan) && !key(loan).equals(remoteKey)) throw new IllegalStateException("Loan is already linked");
            apply(context, snapshot(context, remoteKey), loan.getId());
        }
    }
    public static void edit(Context context, Loan current, Loan edited) {
        synchronized (LOCK) {
            if (!isLinked(current) && isPending(current)) {
                current.setPersonName(edited.getPersonName()); current.setTotalAmount(edited.getTotalAmount());
                current.setOutstandingAmount(edited.getOutstandingAmount()); current.setEmiAmount(edited.getEmiAmount());
                current.setInterestRate(edited.getInterestRate()); current.setStartDate(edited.getStartDate());
                current.setNote(humanNote(edited.getNote()) + "\n" + ORIGIN + markerValue(current.getNote(), ORIGIN) + "]");
                db(context).loanDao().update(current); sync(context); return;
            }
            Bundle input = details(edited); identify(input, current); input.putString("action", "EDIT_LOAN");
            apply(context, command(context, input), current.getId());
        }
    }
    public static void archive(Context context, Loan loan) {
        synchronized (LOCK) {
            Bundle input = new Bundle(); identify(input, loan); input.putString("action", "ARCHIVE");
            apply(context, command(context, input), loan.getId());
        }
    }
    public static void recordPayment(Context context, Loan loan, String type, double amount,
                                     String accountName, String note, String token) {
        synchronized (LOCK) {
            Bundle input = new Bundle(); identify(input, loan); input.putString("token", token);
            input.putString("action", "ADD_PAYMENT"); input.putString("payment_type", "EXTRA".equals(type) ? "PREPAYMENT" : "EMI");
            input.putLong("amount", whole(amount)); input.putString("date", new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new java.util.Date()));
            input.putString("note", safe(note));
            String accountRef = "";
            TridevMoneyMappingEngine.Catalog liveCatalog = new TridevMoneyMappingEngine(context).readCatalog();
            for (TridevMoneyMappingEngine.CatalogItem item : liveCatalog.accounts) {
                if (!item.unavailableForNewPosting && (accountName.equals(item.displayName) || accountName.equals(item.transactionValue))) accountRef = item.canonicalRef;
            }
            for (TridevMoneyMappingEngine.CatalogItem item : liveCatalog.creditCards) {
                if (!item.unavailableForNewPosting && (accountName.equals(item.displayName) || accountName.equals(item.transactionValue))) accountRef = item.canonicalRef;
            }
            if (accountRef.isEmpty()) throw new IllegalStateException("Select an existing payment account");
            String categoryName = "EXTRA".equals(type) ? "Loan Prepayment" : "Loan EMI";
            String categoryRef = "";
            // Reuse a current category or create only the category this payment needs.
            for (Category category : db(context).categoryDao().getAllCategories()) {
                if ("EXPENSE".equals(category.getType()) && categoryName.equalsIgnoreCase(category.getName())) categoryRef = "category:" + category.getId();
            }
            if (categoryRef.isEmpty()) {
                Category category = new Category(); category.setName(categoryName); category.setType("EXPENSE"); category.setColor("#1565C0");
                db(context).categoryDao().insert(category);
                for (Category saved : db(context).categoryDao().getAllCategories()) {
                    if ("EXPENSE".equals(saved.getType()) && categoryName.equalsIgnoreCase(saved.getName())) categoryRef = "category:" + saved.getId();
                }
            }
            if (categoryRef.isEmpty()) throw new IllegalStateException("Payment category unavailable");
            input.putString("account_ref", accountRef); input.putString("category_ref", categoryRef);
            apply(context, command(context, input), loan.getId());
        }
    }
    public static void changePayment(Context context, Loan loan, LoanPayment payment,
                                     double amount, String date, String note, boolean delete) {
        synchronized (LOCK) {
            String identity = markerValue(payment.getNote(), "[LMP_PAYMENT:");
            String prefix = key(loan) + ":";
            if (!identity.startsWith(prefix)) throw new IllegalStateException("Payment belongs to another loan");
            Bundle input = new Bundle(); identify(input, loan);
            input.putString("action", delete ? "DELETE_PAYMENT" : "EDIT_PAYMENT");
            input.putLong("payment_id", Long.parseLong(identity.substring(prefix.length())));
            if (!delete) {
                input.putLong("amount", whole(amount)); input.putString("date", date);
                input.putString("note", note);
            }
            apply(context, command(context, input), loan.getId());
        }
    }
    public static List<LoanPayment> visibleHistory(Loan loan, List<LoanPayment> payments) {
        if (!isLinked(loan)) return payments;
        List<LoanPayment> linked = new ArrayList<>();
        for (LoanPayment payment : payments) {
            if (payment.getNote().contains("[LMP_PAYMENT:" + key(loan) + ":")) linked.add(payment);
        }
        return linked;
    }

    public static final class LinkedPayment {
        public final Loan loan;
        public final LoanPayment payment;
        LinkedPayment(Loan loan, LoanPayment payment) { this.loan = loan; this.payment = payment; }
    }
    public static boolean ownsLoanExpense(com.example.moneymanagerpro.model.Transaction transaction) {
        String note = safe(transaction.getNote());
        return note.contains("Synced from LoanManagerPro") && note.contains("TRIDEV_EVENT:");
    }
    public static LinkedPayment resolveExpense(Context context, com.example.moneymanagerpro.model.Transaction transaction) {
        synchronized (LOCK) {
            java.util.regex.Matcher marker = java.util.regex.Pattern.compile(
                    "TRIDEV_EVENT:([A-Za-z0-9:_\\-]+)").matcher(safe(transaction.getNote()));
            if (!marker.find()) throw new IllegalStateException("Loan payment identity is unavailable");
            TridevEventQueue.QueueItem item = TridevEventQueue.getInstance(context).find(marker.group(1));
            if (item == null || item.event == null || item.event.references == null) throw new IllegalStateException("Loan payment identity is unavailable");
            if (!TridevIntegrationContract.APP_LOAN_MANAGER.equals(item.event.sourceApp)
                    || !String.valueOf(transaction.getId()).equals(item.event.references.moneyManagerTransactionId)) {
                throw new IllegalStateException("This expense is not an owned linked loan payment");
            }
            String loanId = item.event.references.loanManagerLoanId;
            String paymentId = item.event.references.loanManagerPaymentId;
            Loan target = null;
            for (Loan loan : db(context).loanDao().getAllLoans()) {
                if (isLinked(loan) && key(loan).endsWith(":" + loanId)) {
                    if (target != null) throw new IllegalStateException("Loan link is ambiguous; open the Loan section");
                    target = loan;
                }
            }
            if (target == null) throw new IllegalStateException("Link this loan in the Loan section before editing its EMI expense");
            for (LoanPayment payment : db(context).loanPaymentDao().getPaymentsForLoan(target.getId())) {
                if (payment.getNote().contains("[LMP_PAYMENT:" + key(target) + ":" + paymentId + "]")) return new LinkedPayment(target, payment);
            }
            throw new IllegalStateException("Loan payment history is syncing. Refresh and try again.");
        }
    }

    private static Bundle details(Loan loan) {
        Bundle input = new Bundle(); input.putString("name", loan.getPersonName());
        input.putLong("total", whole(loan.getTotalAmount())); input.putLong("outstanding", whole(loan.getOutstandingAmount()));
        input.putLong("emi", whole(loan.getEmiAmount())); input.putDouble("rate", loan.getInterestRate());
        input.putBoolean("active", loan.isActive());
        input.putString("start", loan.getStartDate()); input.putString("note", humanNote(loan.getNote())); return input;
    }
    private static void identify(Bundle input, Loan loan) {
        if (!isLinked(loan)) throw new IllegalStateException("Link the loan first");
        input.putString("key", key(loan)); input.putString("expected", markerValue(loan.getNote(), REV));
        input.putString("token", UUID.randomUUID().toString());
    }
    private static JSONObject command(Context context, Bundle input) {
        Bundle response = call(context, "command_v1", input); requireOK(response); return json(response);
    }
    private static Bundle call(Context context, String method, Bundle input) {
        android.content.pm.ProviderInfo provider = context.getPackageManager()
                .resolveContentProvider(URI.getAuthority(), 0);
        if (provider == null || !"com.tridev.loanmanagerpro".equals(provider.packageName)
                || !TridevCompanionTrust.verifyOrPinInstalledPackage(context,
                        TridevCompanionTrust.LOAN_MANAGER_PACKAGE)) {
            throw new IllegalStateException("Update LoanManagerPro. Its installed signing certificate must match the existing companion connection; the two apps may use different keys.");
        }
        Bundle response = context.getContentResolver().call(URI, method, null, input);
        if (response == null) throw new IllegalStateException("LoanManagerPro is unavailable. Update both apps and try again."); return response;
    }
    private static void requireOK(Bundle response) {
        if (!"OK".equals(response.getString("status"))) throw new IllegalStateException(response.getString("reason", "Loan sync failed. Refresh and try again."));
    }
    private static JSONObject json(Bundle response) {
        try { return new JSONObject(response.getString("snapshot", "")); }
        catch (org.json.JSONException failure) { throw new IllegalStateException("Invalid loan snapshot", failure); }
    }
    private static void apply(Context context, JSONObject remote, int preferredId) {
        AppDatabase database = db(context);
        String remoteKey = remote.optString("key"), version = remote.optString("version");
        if (!remoteKey.matches("[a-fA-F0-9\\-]{36}:[0-9]+") || !version.matches("[a-f0-9]{64}")
                || !"INR".equals(remote.optString("currency"))) throw new IllegalStateException("Invalid linked loan identity/currency");
        database.runInTransaction(() -> {
            List<Loan> all = database.loanDao().getAllLoans();
            Loan linked = findLinked(all, remoteKey);
            Loan target = linked;
            if (target == null && preferredId > 0) {
                for (Loan item : all) if (item.getId() == preferredId) target = item;
                if (target == null || (isLinked(target) && !remoteKey.equals(key(target)))) throw new IllegalStateException("Local loan link changed");
            }
            if (target != null && version.equals(markerValue(target.getNote(), REV))) return;
            boolean create = target == null;
            if (create) target = new Loan();
            target.setPersonName(remote.optString("name")); target.setLoanType("Loan Taken");
            target.setTotalAmount(remote.optLong("total")); target.setOutstandingAmount(remote.optLong("outstanding"));
            target.setEmiAmount(remote.optLong("emi")); target.setInterestRate(remote.optDouble("rate"));
            target.setStartDate(remote.optString("start")); target.setDueDate(remote.optString("due"));
            target.setActive("ACTIVE".equals(remote.optString("status")) && target.getOutstandingAmount() > 0);
            target.setNote(humanNote(remote.optString("note")) + "\n" + LINK + remoteKey + "]\n" + REV + version + "]"
                    + "\n[LMP_TERM:" + remote.optInt("remaining", -1) + "]"
                    + "\n[LMP_PAYOFF:" + remote.optString("payoff") + "]");
            if (create) target.setId((int) database.loanDao().insert(target)); else database.loanDao().update(target);
            mirrorHistory(context, database, target, remote.optJSONArray("payments"));
        });
    }
    private static void mirrorHistory(Context context, AppDatabase database, Loan loan, JSONArray payments) {
        if (payments == null) throw new IllegalStateException("Loan history is missing");
        List<LoanPayment> current = database.loanPaymentDao().getPaymentsForLoan(loan.getId());
        Set<String> seen = new HashSet<>();
        SupportSQLiteDatabase sql = database.getOpenHelper().getWritableDatabase();
        for (int i = 0; i < payments.length(); i++) {
            JSONObject payment = payments.optJSONObject(i); if (payment == null) throw new IllegalStateException("Invalid loan history row");
            String identity = "[LMP_PAYMENT:" + key(loan) + ":" + payment.optLong("id") + "]";
            seen.add(identity); LoanPayment row = null;
            for (LoanPayment item : current) if (item.getNote().contains(identity)) row = item;
            // A manual legacy history row is left intact; only exact linked identities are rewritten.
            String account = accountName(context, payment.optString("account"));
            String note = humanNote(payment.optString("note")) + "\n" + identity;
            String type = "PREPAYMENT".equals(payment.optString("type")) ? "EXTRA" : "EMI";
            if (row == null) {
                row = new LoanPayment(); row.setLoanId(loan.getId()); row.setAmount(payment.optLong("amount"));
                row.setPaymentType(type); row.setAccount(account); row.setPaymentDate(payment.optString("date")); row.setNote(note);
                database.loanPaymentDao().insert(row);
            } else {
                ContentValues values = new ContentValues(); values.put("amount", payment.optLong("amount"));
                values.put("paymentType", type); values.put("account", account); values.put("paymentDate", payment.optString("date")); values.put("note", note);
                sql.update("loan_payments", 0, values, "id = ? AND loanId = ?", new Object[]{row.getId(), loan.getId()});
            }
        }
        for (LoanPayment old : current) {
            String identity = fullMarker(old.getNote(), "[LMP_PAYMENT:");
            if (!identity.isEmpty() && !seen.contains(identity)) {
                sql.delete("loan_payments", "id = ? AND loanId = ?", new Object[]{old.getId(), loan.getId()});
            }
        }
        // Deliberately no writes to transactions: the existing LoanManager bridge handles those.
    }
    private static String accountName(Context context, String ref) {
        TridevMoneyMappingEngine.Catalog catalog = new TridevMoneyMappingEngine(context).readCatalog();
        for (TridevMoneyMappingEngine.CatalogItem item : catalog.accounts) if (ref.equals(item.canonicalRef)) return item.displayName;
        for (TridevMoneyMappingEngine.CatalogItem item : catalog.creditCards) if (ref.equals(item.canonicalRef)) return item.displayName;
        return "LoanManagerPro";
    }
    private static Loan findLinked(List<Loan> all, String key) { for (Loan loan : all) if (key.equals(key(loan))) return loan; return null; }
    private static AppDatabase db(Context context) { return DatabaseClient.getInstance(context.getApplicationContext()).getAppDatabase(); }
    private static long whole(double amount) { return LinkedLoanIdentity.wholeRupees(amount); }
    private static String markerValue(String note, String prefix) { return LinkedLoanIdentity.value(note, prefix); }
    private static String fullMarker(String note, String prefix) { return LinkedLoanIdentity.marker(note, prefix); }
    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
