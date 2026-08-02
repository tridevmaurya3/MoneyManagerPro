package com.example.moneymanagerpro.activities;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Transaction;
import com.example.moneymanagerpro.notification.FinancialNotificationListenerService;
import com.example.moneymanagerpro.notification.FinancialNotificationParser;
import com.example.moneymanagerpro.notification.FinancialNotificationStore;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Play-safe financial notification inbox. No SMS permissions are used. */
public class NotificationAssistantActivity extends AppCompatActivity {

    private static final String FILTER_PENDING = "PENDING";
    private static final String FILTER_SAVED = "SAVED";
    private static final String FILTER_IGNORED = "IGNORED";

    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();

    private LinearLayout inboxContainer;
    private TextView statusText;
    private TextView summaryText;
    private TextView activeFilterText;
    private SwitchMaterial captureSwitch;
    private String activeFilter = FILTER_PENDING;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildScreen());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onDestroy() {
        databaseExecutor.shutdown();
        super.onDestroy();
    }

    private View buildScreen() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(ContextCompat.getColor(this, R.color.app_background));

        LinearLayout root = vertical();
        root.setPadding(dp(16), dp(14), dp(16), dp(30));
        scrollView.addView(root, matchWrap());

        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 30, R.color.app_text_primary, true);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        top.addView(text("Financial Notifications", 21, R.color.app_text_primary, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(top);

        MaterialCardView setupCard = card(R.color.info_surface, R.color.info_outline);
        LinearLayout setup = cardContent();
        setup.addView(text("Play-safe Notification Assistant", 16, R.color.secondary, true));
        setup.addView(text(
                "Bank, UPI, wallet and card notifications are parsed only on this phone. No SMS access, no personal-message scan and no cloud upload.",
                11,
                R.color.app_text_secondary,
                false
        ));

        statusText = text("Checking Notification Access…", 12, R.color.app_text_primary, true);
        addTop(setup, statusText, 10);

        LinearLayout setupActions = horizontal();
        MaterialButton settingsButton = button("Open Notification Settings");
        settingsButton.setOnClickListener(v -> openNotificationSettings());
        setupActions.addView(settingsButton, new LinearLayout.LayoutParams(0, dp(48), 1f));

        MaterialButton testButton = button("Add Test Alert");
        LinearLayout.LayoutParams testParams = new LinearLayout.LayoutParams(0, dp(48), .62f);
        testParams.setMargins(dp(8), 0, 0, 0);
        testButton.setLayoutParams(testParams);
        testButton.setOnClickListener(v -> addTestAlert());
        setupActions.addView(testButton);
        addTop(setup, setupActions, 12);

        captureSwitch = new SwitchMaterial(this);
        captureSwitch.setText("Capture new financial notifications");
        captureSwitch.setTextSize(12);
        captureSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            FinancialNotificationStore.setCaptureEnabled(this, checked);
            if (!buttonView.isPressed()) return;
            Toast.makeText(
                    this,
                    checked ? "Notification capture enabled" : "Notification capture paused",
                    Toast.LENGTH_SHORT
            ).show();
        });
        addTop(setup, captureSwitch, 10);
        setupCard.addView(setup);
        addCard(root, setupCard, 12);

        summaryText = text("Pending 0  •  Saved 0  •  Ignored 0", 13, R.color.app_text_primary, true);
        addTop(root, summaryText, 14);

        LinearLayout filters = horizontal();
        filters.setGravity(Gravity.CENTER_VERTICAL);
        addFilterButton(filters, "Pending", FILTER_PENDING);
        addFilterButton(filters, "Saved", FILTER_SAVED);
        addFilterButton(filters, "Ignored", FILTER_IGNORED);
        addTop(root, filters, 12);

        activeFilterText = text("Pending notifications", 17, R.color.app_text_primary, true);
        addTop(root, activeFilterText, 14);
        root.addView(text("Review, edit and confirm before anything is saved.", 10, R.color.app_text_secondary, false));

        inboxContainer = vertical();
        addTop(root, inboxContainer, 10);

        MaterialButton clearButton = button("Clear current list");
        clearButton.setOnClickListener(v -> confirmClearCurrentList());
        addTop(root, clearButton, 8);

        return scrollView;
    }

    private void addFilterButton(LinearLayout parent, String label, String filter) {
        MaterialButton button = smallButton(label);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1f);
        if (parent.getChildCount() > 0) params.setMargins(dp(6), 0, 0, 0);
        button.setLayoutParams(params);
        button.setOnClickListener(v -> {
            activeFilter = filter;
            refresh();
        });
        parent.addView(button);
    }

    private void refresh() {
        boolean accessEnabled = isNotificationAccessEnabled();
        boolean captureEnabled = FinancialNotificationStore.isCaptureEnabled(this);

        captureSwitch.setChecked(captureEnabled);
        statusText.setText(accessEnabled
                ? (captureEnabled
                ? "Notification Access: ON — capture is active"
                : "Notification Access: ON — capture is paused")
                : "Notification Access: OFF — enable it to receive alerts");
        statusText.setTextColor(ContextCompat.getColor(
                this,
                accessEnabled && captureEnabled ? R.color.success : R.color.expense
        ));

        List<FinancialNotificationStore.Item> all = FinancialNotificationStore.getAll(this);
        int pending = 0;
        int saved = 0;
        int ignored = 0;
        for (FinancialNotificationStore.Item item : all) {
            if (FILTER_SAVED.equals(item.status)) saved++;
            else if (FILTER_IGNORED.equals(item.status)) ignored++;
            else pending++;
        }
        summaryText.setText("Pending " + pending + "  •  Saved " + saved + "  •  Ignored " + ignored);

        activeFilterText.setText(
                FILTER_SAVED.equals(activeFilter)
                        ? "Saved notifications"
                        : FILTER_IGNORED.equals(activeFilter)
                        ? "Ignored notifications"
                        : "Pending notifications"
        );

        List<FinancialNotificationStore.Item> items =
                FinancialNotificationStore.getByStatus(this, activeFilter);

        inboxContainer.removeAllViews();
        if (items.isEmpty()) {
            addEmptyState(accessEnabled);
            return;
        }

        for (FinancialNotificationStore.Item item : items) {
            inboxContainer.addView(createItemCard(item));
        }
    }

    private void addEmptyState(boolean accessEnabled) {
        MaterialCardView empty = card(R.color.app_surface_soft, R.color.app_outline);
        LinearLayout content = cardContent();
        content.addView(text("Nothing here yet", 14, R.color.app_text_primary, true));
        content.addView(text(
                FILTER_PENDING.equals(activeFilter)
                        ? (accessEnabled
                        ? "A supported financial alert will appear here. Use Add Test Alert to verify the complete flow now."
                        : "Enable Notification Access first, then use Add Test Alert for a safe local test.")
                        : "This list is currently empty.",
                10,
                R.color.app_text_secondary,
                false
        ));
        empty.addView(content);
        addCard(inboxContainer, empty, 0);
    }

    private View createItemCard(@NonNull FinancialNotificationStore.Item item) {
        boolean income = "INCOME".equals(item.type);
        int surface = income ? R.color.success_surface : R.color.error_surface;
        int outline = income ? R.color.success_outline : R.color.error_outline;
        int accent = income ? R.color.success : R.color.expense;

        MaterialCardView card = card(surface, outline);
        LinearLayout content = cardContent();

        LinearLayout amountRow = horizontal();
        amountRow.setGravity(Gravity.CENTER_VERTICAL);
        amountRow.addView(text(
                        (income ? "+₹" : "-₹") + money(item.amount),
                        18,
                        accent,
                        true
                ),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        amountRow.addView(text(item.status, 10, accent, true));
        content.addView(amountRow);

        TextView merchant = text(item.merchant, 14, R.color.app_text_primary, true);
        addTop(content, merchant, 5);
        content.addView(text(
                item.category + "  •  " + item.account + "  •  " + item.source,
                10,
                R.color.app_text_secondary,
                false
        ));
        content.addView(text(formatDate(item.postedAt), 9, R.color.app_text_secondary, false));

        String metadata = buildMetadata(item);
        if (!metadata.isEmpty()) {
            TextView meta = text(metadata, 9, R.color.app_text_secondary, false);
            addTop(content, meta, 5);
        }

        TextView sourceText = text(
                item.title + (item.body.isEmpty() ? "" : "\n" + item.body),
                9,
                R.color.app_text_secondary,
                false
        );
        sourceText.setMaxLines(3);
        addTop(content, sourceText, 7);

        LinearLayout buttons = horizontal();
        buttons.setGravity(Gravity.END);

        MaterialButton review = smallButton(
                FILTER_PENDING.equals(item.status) ? "Review & Save" : "View / Edit"
        );
        review.setOnClickListener(v -> showReviewEditor(item));
        buttons.addView(review);

        if (FILTER_PENDING.equals(item.status)) {
            MaterialButton ignore = smallButton("Ignore");
            LinearLayout.LayoutParams ignoreParams =
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
            ignoreParams.setMargins(dp(6), 0, 0, 0);
            ignore.setLayoutParams(ignoreParams);
            ignore.setOnClickListener(v -> {
                FinancialNotificationStore.updateStatus(this, item.id, FILTER_IGNORED);
                refresh();
            });
            buttons.addView(ignore);
        } else if (FILTER_IGNORED.equals(item.status)) {
            MaterialButton restore = smallButton("Restore");
            LinearLayout.LayoutParams restoreParams =
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
            restoreParams.setMargins(dp(6), 0, 0, 0);
            restore.setLayoutParams(restoreParams);
            restore.setOnClickListener(v -> {
                FinancialNotificationStore.updateStatus(this, item.id, FILTER_PENDING);
                activeFilter = FILTER_PENDING;
                refresh();
            });
            buttons.addView(restore);
        }

        addTop(content, buttons, 10);
        card.addView(content);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dp(9));
        card.setLayoutParams(cardParams);
        return card;
    }

    private String buildMetadata(FinancialNotificationStore.Item item) {
        List<String> parts = new ArrayList<>();
        if (!item.lastFour.isEmpty()) parts.add("••••" + item.lastFour);
        if (!item.reference.isEmpty()) parts.add("Ref " + item.reference);
        if (!item.vpa.isEmpty()) parts.add(item.vpa);
        return android.text.TextUtils.join("  •  ", parts);
    }

    private void showReviewEditor(@NonNull FinancialNotificationStore.Item item) {
        LinearLayout form = vertical();
        form.setPadding(dp(20), dp(8), dp(20), 0);

        Spinner typeSpinner = spinner(new String[]{"EXPENSE", "INCOME"});
        typeSpinner.setSelection("INCOME".equals(item.type) ? 1 : 0);
        addField(form, "Transaction Type", typeSpinner);

        EditText amountInput = editText("Amount", String.valueOf(item.amount));
        amountInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        addField(form, "Amount", amountInput);

        EditText merchantInput = editText("Merchant / Sender", item.merchant);
        addField(form, "Merchant / Sender", merchantInput);

        EditText categoryInput = editText("Category", item.category);
        addField(form, "Category", categoryInput);

        EditText accountInput = editText("Account", item.account);
        addField(form, "Account", accountInput);

        EditText noteInput = editText("Note", item.userNote);
        addField(form, "Additional Note", noteInput);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Review Financial Alert")
                .setView(form)
                .setPositiveButton(
                        FILTER_SAVED.equals(item.status) ? "Save Changes" : "Save Transaction",
                        null
                )
                .setNeutralButton("Delete Alert", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                double amount;
                try {
                    amount = Double.parseDouble(amountInput.getText().toString().trim());
                } catch (Exception exception) {
                    amountInput.setError("Enter a valid amount");
                    return;
                }

                String merchant = merchantInput.getText().toString().trim();
                String category = categoryInput.getText().toString().trim();
                String account = accountInput.getText().toString().trim();
                String note = noteInput.getText().toString().trim();

                if (amount <= 0d) {
                    amountInput.setError("Amount must be greater than zero");
                    return;
                }
                if (merchant.isEmpty()) {
                    merchantInput.setError("Enter merchant or sender");
                    return;
                }
                if (category.isEmpty()) {
                    categoryInput.setError("Enter category");
                    return;
                }
                if (account.isEmpty()) {
                    accountInput.setError("Enter account");
                    return;
                }

                String type = String.valueOf(typeSpinner.getSelectedItem());
                FinancialNotificationStore.updateDetails(
                        this,
                        item.id,
                        type,
                        amount,
                        merchant,
                        category,
                        account,
                        note
                );

                FinancialNotificationStore.Item updated =
                        FinancialNotificationStore.find(this, item.id);

                if (updated == null) {
                    dialog.dismiss();
                    refresh();
                    return;
                }

                if (FILTER_SAVED.equals(item.status)) {
                    Toast.makeText(this, "Alert details updated", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    refresh();
                } else {
                    dialog.dismiss();
                    saveTransaction(updated);
                }
            });

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v ->
                    new AlertDialog.Builder(this)
                            .setTitle("Delete this alert?")
                            .setMessage("This removes only the local notification record.")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Delete", (confirmDialog, which) -> {
                                FinancialNotificationStore.delete(this, item.id);
                                dialog.dismiss();
                                refresh();
                            })
                            .show()
            );
        });

        dialog.show();
    }

    private void saveTransaction(@NonNull FinancialNotificationStore.Item item) {
        if (FILTER_SAVED.equals(item.status)) {
            Toast.makeText(this, "This alert is already saved", Toast.LENGTH_SHORT).show();
            return;
        }

        databaseExecutor.execute(() -> {
            try {
                Transaction transaction = new Transaction();
                transaction.setType(item.type);
                transaction.setAmount(item.amount);
                transaction.setCategory(item.category);
                transaction.setAccount(item.account);
                transaction.setDate(new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        .format(new Date(item.postedAt)));

                StringBuilder note = new StringBuilder("Financial notification: ")
                        .append(item.merchant);
                if (!item.source.isEmpty()) note.append(" | ").append(item.source);
                if (!item.reference.isEmpty()) note.append(" | Ref ").append(item.reference);
                if (!item.vpa.isEmpty()) note.append(" | UPI ").append(item.vpa);
                if (!item.userNote.isEmpty()) note.append(" | ").append(item.userNote);
                transaction.setNote(note.toString());

                DatabaseClient.getInstance(getApplicationContext())
                        .getAppDatabase()
                        .transactionDao()
                        .insert(transaction);

                FinancialNotificationStore.updateStatus(this, item.id, FILTER_SAVED);

                runOnUiThread(() -> {
                    Toast.makeText(this, "Transaction saved successfully", Toast.LENGTH_SHORT).show();
                    activeFilter = FILTER_PENDING;
                    refresh();
                });
            } catch (Exception exception) {
                runOnUiThread(() -> Toast.makeText(
                        this,
                        "Transaction could not be saved: " + exception.getClass().getSimpleName(),
                        Toast.LENGTH_LONG
                ).show());
            }
        });
    }

    private void addTestAlert() {
        String title = "Money received";
        String body = "INR 1250.00 credited to A/c XX4582 via UPI from TESTUSER@upi. Ref 123456789012";
        FinancialNotificationParser.ParsedNotification parsed =
                FinancialNotificationParser.parse(
                        "com.example.testbank",
                        title,
                        body,
                        System.currentTimeMillis()
                );

        if (parsed == null) {
            Toast.makeText(this, "Test parser failed", Toast.LENGTH_LONG).show();
            return;
        }

        boolean added = FinancialNotificationStore.add(this, parsed);
        activeFilter = FILTER_PENDING;
        refresh();
        Toast.makeText(
                this,
                added ? "Test financial alert added" : "Identical test alert already exists",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void confirmClearCurrentList() {
        new AlertDialog.Builder(this)
                .setTitle("Clear " + activeFilter.toLowerCase(Locale.ROOT) + " alerts?")
                .setMessage("This removes only notification inbox records. Saved transactions remain unchanged.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> {
                    FinancialNotificationStore.clearStatus(this, activeFilter);
                    refresh();
                })
                .show();
    }

    private void openNotificationSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        } catch (Exception exception) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private boolean isNotificationAccessEnabled() {
        String enabled = Settings.Secure.getString(
                getContentResolver(),
                "enabled_notification_listeners"
        );
        if (enabled == null) return false;
        ComponentName component = new ComponentName(this, FinancialNotificationListenerService.class);
        return enabled.contains(component.flattenToString()) || enabled.contains(getPackageName());
    }

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                values
        ));
        return spinner;
    }

    private EditText editText(String hint, String value) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(value == null ? "" : value);
        input.setSingleLine(true);
        input.setTextSize(14);
        return input;
    }

    private void addField(LinearLayout form, String label, View input) {
        TextView title = text(label, 11, R.color.app_text_secondary, true);
        addTop(form, title, form.getChildCount() == 0 ? 0 : 10);
        form.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
        ));
    }

    private MaterialCardView card(int background, int stroke) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(ContextCompat.getColor(this, background));
        card.setStrokeColor(ContextCompat.getColor(this, stroke));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(18));
        card.setCardElevation(0f);
        return card;
    }

    private LinearLayout cardContent() {
        LinearLayout layout = vertical();
        layout.setPadding(dp(14), dp(14), dp(14), dp(14));
        return layout;
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private TextView text(String value, float size, int colorResource, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(ContextCompat.getColor(this, colorResource));
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private MaterialButton button(String label) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setTextSize(10);
        button.setAllCaps(false);
        return button;
    }

    private MaterialButton smallButton(String label) {
        MaterialButton button = button(label);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(42)
        ));
        return button;
    }

    private void addCard(LinearLayout parent, View child, int topMargin) {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(topMargin), 0, 0);
        parent.addView(child, params);
    }

    private void addTop(LinearLayout parent, View child, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                child.getLayoutParams() == null
                        ? ViewGroup.LayoutParams.MATCH_PARENT
                        : child.getLayoutParams().width,
                child.getLayoutParams() == null
                        ? ViewGroup.LayoutParams.WRAP_CONTENT
                        : child.getLayoutParams().height
        );
        params.setMargins(0, dp(margin), 0, 0);
        parent.addView(child, params);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private String formatDate(long time) {
        return new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                .format(new Date(time));
    }

    private String money(double amount) {
        return String.format(Locale.getDefault(), "%.2f", amount);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
