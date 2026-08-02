package com.example.moneymanagerpro.activities;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.notification.SmsAlertStore;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Play-safe SMS alert inbox built from SMS-app notifications. */
public class SmsAlertInboxActivity extends AppCompatActivity {

    private LinearLayout listContainer;
    private TextView summaryText;
    private EditText searchInput;
    private String filter = "ALL";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildScreen());
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private View buildScreen() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(ContextCompat.getColor(this, R.color.app_background));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        scrollView.addView(root);

        TextView back = text("‹  Back", 15, R.color.secondary, true);
        back.setOnClickListener(v -> finish());
        root.addView(back);

        TextView title = text("SMS Alerts", 28, R.color.app_text_primary, true);
        addTop(root, title, 14);
        root.addView(text("Read-only SMS previews captured from notification access. No SMS permission and no cloud upload.", 12, R.color.app_text_secondary, false));

        MaterialCardView privacyCard = card(R.color.info_surface, R.color.info_outline);
        LinearLayout privacy = content();
        privacy.addView(text("Play-safe SMS Inbox", 17, R.color.secondary, true));
        privacy.addView(text("Only notifications shown by your SMS app can appear here. The app cannot read old SMS history or delete messages from the phone.", 11, R.color.app_text_secondary, false));
        MaterialButton settings = button("Open Notification Settings");
        settings.setOnClickListener(v -> openSettings());
        addTop(privacy, settings, 10);
        privacyCard.addView(privacy);
        addTop(root, privacyCard, 16);

        summaryText = text("0 alerts", 15, R.color.app_text_primary, true);
        addTop(root, summaryText, 16);

        searchInput = new EditText(this);
        searchInput.setHint("Search sender or message");
        searchInput.setSingleLine(true);
        searchInput.setPadding(dp(14), 0, dp(14), 0);
        addTop(root, searchInput, 10);

        LinearLayout searchActions = new LinearLayout(this);
        searchActions.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton search = button("Search");
        search.setOnClickListener(v -> refresh());
        searchActions.addView(search, new LinearLayout.LayoutParams(0, dp(48), 1f));
        MaterialButton clearAll = button("Clear Inbox");
        clearAll.setOnClickListener(v -> confirmClear());
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        clearParams.setMargins(dp(8), 0, 0, 0);
        searchActions.addView(clearAll, clearParams);
        addTop(root, searchActions, 8);

        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        filters.addView(filterButton("All", "ALL"));
        filters.addView(filterButton("Banking", "BANKING"));
        filters.addView(filterButton("Offers", "OFFERS"));
        filters.addView(filterButton("Other", "OTHER"));
        addTop(root, filters, 10);

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        addTop(root, listContainer, 14);
        return scrollView;
    }

    private View filterButton(String label, String value) {
        MaterialButton button = button(label);
        button.setTextSize(9);
        button.setOnClickListener(v -> {
            filter = value;
            refresh();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1f);
        params.setMargins(dp(2), 0, dp(2), 0);
        button.setLayoutParams(params);
        return button;
    }

    private void refresh() {
        if (listContainer == null) return;
        List<SmsAlertStore.Item> items = SmsAlertStore.getAll(this);
        String query = searchInput == null ? "" : searchInput.getText().toString().trim().toLowerCase(Locale.ROOT);

        listContainer.removeAllViews();
        int shown = 0;
        int unread = 0;

        for (SmsAlertStore.Item item : items) {
            if (!item.read) unread++;
            if (!"ALL".equals(filter) && !filter.equals(item.category)) continue;
            String haystack = (item.sender + " " + item.message).toLowerCase(Locale.ROOT);
            if (!query.isEmpty() && !haystack.contains(query)) continue;
            listContainer.addView(createAlertCard(item));
            shown++;
        }

        summaryText.setText(shown + " shown  •  " + unread + " unread  •  " + items.size() + " total");

        if (shown == 0) {
            MaterialCardView empty = card(R.color.app_surface_soft, R.color.app_outline);
            LinearLayout content = content();
            content.addView(text("No SMS alerts found", 16, R.color.app_text_primary, true));
            content.addView(text("Enable Notification Access and wait for your SMS app to show a message notification.", 11, R.color.app_text_secondary, false));
            empty.addView(content);
            listContainer.addView(empty);
        }
    }

    private View createAlertCard(SmsAlertStore.Item item) {
        int background = "BANKING".equals(item.category)
                ? R.color.info_surface
                : "OFFERS".equals(item.category)
                ? R.color.purple_surface
                : R.color.app_surface_soft;
        int outline = "BANKING".equals(item.category)
                ? R.color.info_outline
                : "OFFERS".equals(item.category)
                ? R.color.purple_outline
                : R.color.app_outline;

        MaterialCardView card = card(background, outline);
        LinearLayout content = content();

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        TextView sender = text(item.sender, 15, R.color.app_text_primary, !item.read);
        heading.addView(sender, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        heading.addView(text(item.category, 9, R.color.secondary, true));
        content.addView(heading);

        TextView message = text(item.message, 12, R.color.app_text_secondary, false);
        message.setMaxLines(5);
        addTop(content, message, 6);
        content.addView(text(formatDate(item.postedAt), 9, R.color.app_text_secondary, false));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        MaterialButton read = button(item.read ? "Mark Unread" : "Mark Read");
        read.setOnClickListener(v -> {
            SmsAlertStore.markRead(this, item.id, !item.read);
            refresh();
        });
        actions.addView(read);
        MaterialButton delete = button("Delete Local Copy");
        delete.setOnClickListener(v -> {
            SmsAlertStore.delete(this, item.id);
            refresh();
        });
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
        deleteParams.setMargins(dp(8), 0, 0, 0);
        actions.addView(delete, deleteParams);
        addTop(content, actions, 10);

        card.addView(content);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(params);
        return card;
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("Clear local SMS alerts?")
                .setMessage("This removes only alert previews saved inside Money Manager Pro. It does not delete SMS messages from your phone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> {
                    SmsAlertStore.clear(this);
                    refresh();
                })
                .show();
    }

    private void openSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        } catch (Exception exception) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private MaterialCardView card(int background, int outline) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(ContextCompat.getColor(this, background));
        card.setStrokeColor(ContextCompat.getColor(this, outline));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(20));
        card.setCardElevation(dp(1));
        return card;
    }

    private LinearLayout content() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(15), dp(16), dp(15));
        return layout;
    }

    private MaterialButton button(String label) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(10);
        button.setMinHeight(0);
        return button;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(ContextCompat.getColor(this, color));
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private void addTop(LinearLayout parent, View child, int margin) {
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
