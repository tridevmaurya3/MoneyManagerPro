package com.example.moneymanagerpro;

import android.app.Activity;
import android.text.InputType;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ScrollView;
import android.view.ViewGroup;
import androidx.appcompat.app.AlertDialog;
import com.example.moneymanagerpro.model.Loan;
import com.example.moneymanagerpro.model.LoanPayment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.TextInputEditText;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Small companion-loan controls. Configuration and conflict messages are actionable. */
public final class LinkedLoanControls {
    private LinkedLoanControls() { }
    public static void attach(Activity activity, LinearLayout content, Loan loan, Runnable refresh) {
        if ("Loan Given".equalsIgnoreCase(loan.getLoanType())) return;
        TextView status = new TextView(activity);
        status.setText(LinkedLoanBridge.isLinked(loan) ?
                (LinkedLoanSyncInitializer.lastError().isEmpty() ? "Linked to LoanManagerPro • automatic updates"
                        : "Loan sync waiting: " + LinkedLoanSyncInitializer.lastError())
                : "Independent tracker • link to the same LoanManagerPro loan");
        status.setTextSize(12); content.addView(status);
        MaterialButton action = new MaterialButton(activity);
        action.setText(LinkedLoanBridge.isLinked(loan) ? "Edit Linked Loan"
                : LinkedLoanBridge.isPending(loan) ? "Edit & Sync Loan" : "Link LoanManager Loan");
        action.setOnClickListener(v -> {
            if (LinkedLoanBridge.isLinked(loan) || LinkedLoanBridge.isPending(loan)) edit(activity, loan, refresh);
            else selectLink(activity, loan, refresh, action);
        });
        content.addView(action);
        if (!LinkedLoanBridge.isLinked(loan) && LinkedLoanBridge.isPending(loan)) {
            MaterialButton link = new MaterialButton(activity); link.setText("Link Existing LoanManager Loan");
            link.setOnClickListener(v -> selectLink(activity, loan, refresh, link)); content.addView(link);
        }
    }
    public static boolean routeExpense(Activity activity,
                                       com.example.moneymanagerpro.model.Transaction transaction,
                                       boolean delete, Runnable refresh) {
        if (!LinkedLoanBridge.ownsLoanExpense(transaction)) return false;
        new Thread(() -> {
            try {
                LinkedLoanBridge.LinkedPayment linked = LinkedLoanBridge.resolveExpense(
                        activity.getApplicationContext(), transaction);
                if (delete) {
                    LinkedLoanBridge.changePayment(activity.getApplicationContext(), linked.loan,
                            linked.payment, 0, "", "", true);
                    activity.runOnUiThread(() -> { if (!gone(activity)) { refresh.run(); toast(activity, "Linked EMI deleted"); } });
                } else activity.runOnUiThread(() -> {
                    if (!gone(activity)) payment(activity, linked.loan, linked.payment, refresh);
                });
            } catch (RuntimeException failure) {
                activity.runOnUiThread(() -> { if (!gone(activity)) toast(activity, failure.getMessage()); });
            }
        }).start();
        return true;
    }

    private static void selectLink(Activity activity, Loan loan, Runnable refresh, MaterialButton button) {
        button.setEnabled(false);
        new Thread(() -> {
            try {
                JSONArray entries = LinkedLoanBridge.catalog(activity.getApplicationContext());
                List<JSONObject> choices = new ArrayList<>(); List<String> labels = new ArrayList<>();
                for (int i = 0; i < entries.length(); i++) {
                    JSONObject item = entries.optJSONObject(i);
                    if (item != null && "INR".equals(item.optString("currency"))) {
                        choices.add(item); labels.add(item.optString("name") + " • ₹" + item.optLong("total")
                                + " • #" + item.optString("key").substring(item.optString("key").indexOf(':') + 1));
                    }
                }
                activity.runOnUiThread(() -> {
                    if (gone(activity)) return; button.setEnabled(true);
                    if (choices.isEmpty()) { toast(activity, "No LoanManagerPro loans found"); return; }
                    new AlertDialog.Builder(activity).setTitle("Select the same loan")
                            .setItems(labels.toArray(new String[0]), (dialog, which) ->
                                new AlertDialog.Builder(activity).setTitle("Link this loan?")
                                    .setMessage("The LoanManager balance and payment history will be shown here. Existing expense entries and earlier local history are retained. No new expense is created by linking.")
                                    .setNegativeButton("Cancel", null)
                                    .setPositiveButton("Link", (d, w) -> run(activity, () ->
                                        LinkedLoanBridge.link(activity.getApplicationContext(), loan,
                                                choices.get(which).optString("key")), refresh, null)).show())
                            .setNegativeButton("Cancel", null).show();
                });
            } catch (RuntimeException failure) {
                activity.runOnUiThread(() -> { if (!gone(activity)) { button.setEnabled(true); toast(activity, failure.getMessage()); } });
            }
        }).start();
    }
    private static void edit(Activity activity, Loan loan, Runnable refresh) {
        LinearLayout form = form(activity);
        TextInputEditText name = field(activity, form, "Loan name", loan.getPersonName(), false);
        TextInputEditText total = field(activity, form, "Original principal (₹)", number(loan.getTotalAmount()), true);
        TextInputEditText outstanding = field(activity, form, "Outstanding (₹; change only to correct balance)", number(loan.getOutstandingAmount()), true);
        TextInputEditText emi = field(activity, form, "EMI (₹)", number(loan.getEmiAmount()), true);
        TextInputEditText rate = field(activity, form, "Annual interest rate (%)", String.valueOf(loan.getInterestRate()), true);
        TextInputEditText start = field(activity, form, "EMI start date (yyyy-MM-dd)", loan.getStartDate(), false);
        TextInputEditText note = field(activity, form, "Note", LinkedLoanBridge.humanNote(loan.getNote()), false);
        ScrollView scroll = new ScrollView(activity); scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Edit linked loan")
                .setView(scroll).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                Loan edited = new Loan(); edited.setPersonName(text(name)); edited.setTotalAmount(Double.parseDouble(text(total)));
                edited.setOutstandingAmount(Double.parseDouble(text(outstanding))); edited.setEmiAmount(Double.parseDouble(text(emi)));
                edited.setInterestRate(Double.parseDouble(text(rate))); edited.setStartDate(text(start)); edited.setNote(text(note));
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                run(activity, () -> LinkedLoanBridge.edit(activity.getApplicationContext(), loan, edited), refresh, dialog);
            } catch (RuntimeException failure) { toast(activity, "Check the entered amounts and dates"); }
        }));
        dialog.show();
    }
    public static void payment(Activity activity, Loan loan, LoanPayment payment, Runnable refresh) {
        if (!LinkedLoanBridge.isLinked(loan) || !payment.getNote().contains("[LMP_PAYMENT:")) return;
        new AlertDialog.Builder(activity).setTitle("Linked payment")
                .setItems(new String[]{"Edit Payment", "Delete Payment"}, (d, which) -> {
                    if (which == 1) {
                        new AlertDialog.Builder(activity).setTitle("Delete this payment in both apps?")
                                .setMessage("The loan will be recalculated. The existing integration removes only its own expense entry.")
                                .setNegativeButton("Cancel", null).setPositiveButton("Delete", (confirm, w) ->
                                    run(activity, () -> LinkedLoanBridge.changePayment(activity.getApplicationContext(), loan,
                                            payment, 0, "", "", true), refresh, null)).show();
                    } else {
                        LinearLayout form = form(activity);
                        TextInputEditText amount = field(activity, form, "Amount (₹)", number(payment.getAmount()), true);
                        TextInputEditText date = field(activity, form, "Payment date (yyyy-MM-dd)", payment.getPaymentDate(), false);
                        TextInputEditText note = field(activity, form, "Note", LinkedLoanBridge.humanNote(payment.getNote()), false);
                        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Edit linked payment")
                                .setView(form).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
                        dialog.setOnShowListener(show -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                            try {
                                double value = Double.parseDouble(text(amount));
                                String dateValue = text(date), noteValue = text(note);
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                                run(activity, () -> LinkedLoanBridge.changePayment(activity.getApplicationContext(), loan,
                                        payment, value, dateValue, noteValue, false), refresh, dialog);
                            } catch (RuntimeException failure) { toast(activity, "Enter a valid amount"); }
                        })); dialog.show();
                    }
                }).setNegativeButton("Cancel", null).show();
    }
    private static void run(Activity activity, Runnable operation, Runnable refresh, AlertDialog dialog) {
        new Thread(() -> {
            try {
                operation.run(); activity.runOnUiThread(() -> {
                    if (gone(activity)) return;
                    if (dialog != null) dialog.dismiss(); refresh.run(); toast(activity, "Linked loan updated");
                });
            } catch (RuntimeException failure) {
                activity.runOnUiThread(() -> {
                    if (gone(activity)) return;
                    if (dialog != null) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    toast(activity, failure.getMessage()); LinkedLoanSyncInitializer.request(activity);
                });
            }
        }).start();
    }
    private static LinearLayout form(Activity activity) {
        LinearLayout form = new LinearLayout(activity); form.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(20 * activity.getResources().getDisplayMetrics().density); form.setPadding(pad, pad / 2, pad, pad / 2); return form;
    }
    private static TextInputEditText field(Activity activity, LinearLayout form, String hint, String value, boolean number) {
        TextInputLayout input = new TextInputLayout(activity); input.setHint(hint);
        input.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        TextInputEditText edit = new TextInputEditText(activity); edit.setText(value);
        edit.setInputType(number ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL : InputType.TYPE_CLASS_TEXT);
        input.addView(edit, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Math.round(8 * activity.getResources().getDisplayMetrics().density); form.addView(input, params); return edit;
    }
    private static String number(double value) { return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString(); }
    private static String text(TextInputEditText view) { return view.getText() == null ? "" : view.getText().toString().trim(); }
    private static boolean gone(Activity activity) { return activity.isFinishing() || activity.isDestroyed(); }
    private static void toast(Activity activity, String message) { Toast.makeText(activity, message == null ? "Loan sync unavailable" : message, Toast.LENGTH_LONG).show(); }
}
