package com.example.moneymanagerpro.activities;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
import com.example.moneymanagerpro.notification.FinancialNotificationStore;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Play-safe notification inbox. It never requests or uses SMS permissions. */
public class NotificationAssistantActivity extends AppCompatActivity {

    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private LinearLayout inboxContainer;
    private TextView statusText;
    private TextView summaryText;

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

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(28));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 28, R.color.app_text_primary, true);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView pageTitle = text("Financial Notifications", 20, R.color.app_text_primary, true);
        top.addView(pageTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(top);

        MaterialCardView privacyCard = card(R.color.info_surface, R.color.info_outline);
        LinearLayout privacy = verticalContent();
        privacy.addView(text("Play-safe Notification Assistant", 16, R.color.secondary, true));
        privacy.addView(text(
                "Reads only financial notification text after your permission. No SMS access, no personal-message scan and no cloud upload.",
                11,
                R.color.app_text_secondary,
                false
        ));
        statusText = text("Checking Notification Access…", 12, R.color.app_text_primary, true);
        addTopMargin(privacy, statusText, 10);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton accessButton = button("Open Notification Settings");
        accessButton.setOnClickListener(v -> openNotificationSettings());
        actions.addView(accessButton, new LinearLayout.LayoutParams(0, dp(48), 1f));
        MaterialButton refreshButton = button("Refresh");
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(0, dp(48), .55f);
        refreshParams.setMargins(dp(8), 0, 0, 0);
        refreshButton.setLayoutParams(refreshParams);
        refreshButton.setOnClickListener(v -> refresh());
        actions.addView(refreshButton);
        addTopMargin(privacy, actions, 12);
        privacyCard.addView(privacy);
        addCard(root, privacyCard, 12);

        summaryText = text("Pending 0  •  Saved 0  •  Ignored 0", 13, R.color.app_text_primary, true);
        addTopMargin(root, summaryText, 14);

        TextView sectionTitle = text("Notification Inbox", 17, R.color.app_text_primary, true);
        addTopMargin(root, sectionTitle, 14);
        TextView hint = text("Review every detected transaction before saving it.", 10, R.color.app_text_secondary, false);
        root.addView(hint);

        inboxContainer = new LinearLayout(this);
        inboxContainer.setOrientation(LinearLayout.VERTICAL);
        addTopMargin(root, inboxContainer, 10);
        return scrollView;
    }

    private void refresh() {
        boolean enabled = isNotificationAccessEnabled();
        statusText.setText(enabled
                ? "Notification Access: ON — waiting for financial notifications"
                : "Notification Access: OFF — tap below to enable it");
        statusText.setTextColor(ContextCompat.getColor(
                this,
                enabled ? R.color.success : R.color.expense
        ));

        List<FinancialNotificationStore.Item> items = FinancialNotificationStore.getAll(this);
        int pending = 0, saved = 0, ignored = 0;
        for (FinancialNotificationStore.Item item : items) {
            if ("SAVED".equals(item.status)) saved++;
            else if ("IGNORED".equals(item.status)) ignored++;
            else pending++;
        }
        summaryText.setText("Pending " + pending + "  •  Saved " + saved + "  •  Ignored " + ignored);

        inboxContainer.removeAllViews();
        if (items.isEmpty()) {
            MaterialCardView empty = card(R.color.app_surface_soft, R.color.app_outline);
            LinearLayout content = verticalContent();
            content.addView(text("No financial notifications yet", 14, R.color.app_text_primary, true));
            content.addView(text(
                    enabled
                            ? "A supported bank, UPI, wallet or card notification will appear here for confirmation."
                            : "Enable Notification Access first. The app will not request SMS permission.",
                    10,
                    R.color.app_text_secondary,
                    false
            ));
            empty.addView(content);
            addCard(inboxContainer, empty, 0);
            return;
        }

        for (FinancialNotificationStore.Item item : items) {
            inboxContainer.addView(createItemCard(item));
        }
    }

    private View createItemCard(@NonNull FinancialNotificationStore.Item item) {
        boolean income = "INCOME".equals(item.type);
        int surface = income ? R.color.success_surface : R.color.error_surface;
        int outline = income ? R.color.success_outline : R.color.error_outline;
        int accent = income ? R.color.success : R.color.expense;

        MaterialCardView card = card(surface, outline);
        LinearLayout content = verticalContent();

        LinearLayout amountRow = new LinearLayout(this);
        amountRow.setOrientation(LinearLayout.HORIZONTAL);
        amountRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView amount = text(
                (income ? "+₹" : "-₹") + String.format(Locale.getDefault(), "%.2f", item.amount),
                18,
                accent,
                true
        );
        amountRow.addView(amount, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView state = text(item.status, 10, accent, true);
        amountRow.addView(state);
        content.addView(amountRow);

        TextView merchant = text(item.merchant, 14, R.color.app_text_primary, true);
        addTopMargin(content, merchant, 5);
        content.addView(text(item.category + "  •  " + item.type, 10, R.color.app_text_secondary, false));
        content.addView(text(formatDate(item.postedAt), 9, R.color.app_text_secondary, false));

        TextView source = text(item.title + (item.body.isEmpty() ? "" : "\n" + item.body), 9, R.color.app_text_secondary, false);
        source.setMaxLines(3);
        addTopMargin(content, source, 7);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);
        MaterialButton review = smallButton("Review");
        review.setOnClickListener(v -> showReview(item));
        buttons.addView(review);
        MaterialButton ignore = smallButton("Ignore");
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        p.setMargins(dp(6), 0, 0, 0);
        ignore.setLayoutParams(p);
        ignore.setOnClickListener(v -> {
            FinancialNotificationStore.updateStatus(this, item.id, "IGNORED");
            refresh();
        });
        buttons.addView(ignore);
        addTopMargin(content, buttons, 10);

        card.addView(content);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dp(9));
        card.setLayoutParams(cardParams);
        return card;
    }

    private void showReview(@NonNull FinancialNotificationStore.Item item) {
        String details = ("INCOME".equals(item.type) ? "Credit" : "Debit")
                + "\nAmount: ₹" + String.format(Locale.getDefault(), "%.2f", item.amount)
                + "\nCategory: " + item.category
                + "\nAccount: Cash"
                + (item.lastFour.isEmpty() ? "" : "\nAccount/Card: ••••" + item.lastFour)
                + (item.reference.isEmpty() ? "" : "\nReference: " + item.reference)
                + "\n\nNothing is saved until you confirm.";

        new AlertDialog.Builder(this)
                .setTitle(item.merchant)
                .setMessage(details)
                .setPositiveButton("Save Transaction", (dialog, which) -> saveTransaction(item))
                .setNeutralButton("Delete", (dialog, which) -> {
                    FinancialNotificationStore.delete(this, item.id);
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveTransaction(@NonNull FinancialNotificationStore.Item item) {
        if ("SAVED".equals(item.status)) {
            Toast.makeText(this, "This notification is already saved", Toast.LENGTH_SHORT).show();
            return;
        }
        databaseExecutor.execute(() -> {
            try {
                Transaction transaction = new Transaction();
                transaction.setType(item.type);
                transaction.setAmount(item.amount);
                transaction.setCategory(item.category);
                transaction.setAccount("Cash");
                transaction.setDate(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(item.postedAt)));
                transaction.setNote(
                        "Imported from financial notification: " + item.merchant
                                + (item.reference.isEmpty() ? "" : " | Ref " + item.reference)
                );
                DatabaseClient.getInstance(this)
                        .getAppDatabase()
                        .transactionDao()
                        .insert(transaction);
                FinancialNotificationStore.updateStatus(this, item.id, "SAVED");
                runOnUiThread(() -> {
                    Toast.makeText(this, "Transaction saved", Toast.LENGTH_SHORT).show();
                    refresh();
                });
            } catch (Exception exception) {
                runOnUiThread(() -> Toast.makeText(
                        this,
                        "Transaction could not be saved",
                        Toast.LENGTH_LONG
                ).show());
            }
        });
    }

    private void openNotificationSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        } catch (Exception exception) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private boolean isNotificationAccessEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (enabled == null) return false;
        ComponentName component = new ComponentName(this, FinancialNotificationListenerService.class);
        return enabled.contains(component.flattenToString()) || enabled.contains(getPackageName());
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

    private LinearLayout verticalContent() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(14), dp(14), dp(14), dp(14));
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
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(topMargin), 0, 0);
        parent.addView(child, params);
    }

    private void addTopMargin(LinearLayout parent, View child, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                child.getLayoutParams() == null ? ViewGroup.LayoutParams.MATCH_PARENT : child.getLayoutParams().width,
                child.getLayoutParams() == null ? ViewGroup.LayoutParams.WRAP_CONTENT : child.getLayoutParams().height
        );
        params.setMargins(0, dp(margin), 0, 0);
        parent.addView(child, params);
    }

    private String formatDate(long time) {
        return new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(new Date(time));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
