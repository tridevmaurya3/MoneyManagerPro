package com.example.moneymanagerpro.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.notification.DirectSmsAccessManager;
import com.example.moneymanagerpro.notification.SmsAlertStore;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** SMS inbox supporting direct permission access and notification fallback. */
public class SmsAlertInboxActivity extends AppCompatActivity {

    private static final int SMS_PERMISSION_REQUEST_CODE = 4201;

    private final ExecutorService smsExecutor = Executors.newSingleThreadExecutor();

    private LinearLayout listContainer;
    private LinearLayout filterContainer;
    private LinearLayout directAccessContainer;
    private LinearLayout redactionContainer;
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

    @Override
    protected void onDestroy() {
        smsExecutor.shutdownNow();
        super.onDestroy();
    }

    private View buildScreen() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(color(R.color.app_background));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        scrollView.addView(root);

        TextView back = text("‹  Back", 15, R.color.secondary, true);
        back.setOnClickListener(v -> finish());
        root.addView(back);

        TextView title = text("SMS Alerts", 28, R.color.app_text_primary, true);
        addTop(root, title, 14);
        root.addView(text(
                "Read financial SMS directly after permission, with Notification Access kept as a fallback.",
                12,
                R.color.app_text_secondary,
                false
        ));

        MaterialCardView privacyCard = card(R.color.info_surface, R.color.info_outline);
        LinearLayout privacy = content();
        privacy.addView(text("SMS Access & Privacy", 17, R.color.secondary, true));
        privacy.addView(text(
                "Money Manager Pro uses SMS access only to find completed financial transactions such as debit, credit, UPI, card, refund and withdrawal alerts. Processing stays on this device and SMS content is not uploaded.",
                11,
                R.color.app_text_secondary,
                false
        ));

        MaterialButton notificationSettings = filledButton(
                "Open Notification Settings",
                R.color.button_secondary
        );
        notificationSettings.setOnClickListener(v -> openNotificationSettings());
        LinearLayout.LayoutParams notificationParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
        );
        notificationParams.setMargins(0, dp(12), 0, 0);
        privacy.addView(notificationSettings, notificationParams);

        privacyCard.addView(privacy);
        addTop(root, privacyCard, 16);

        directAccessContainer = new LinearLayout(this);
        directAccessContainer.setOrientation(LinearLayout.VERTICAL);
        addTop(root, directAccessContainer, 10);

        redactionContainer = new LinearLayout(this);
        redactionContainer.setOrientation(LinearLayout.VERTICAL);
        redactionContainer.setVisibility(View.GONE);
        addTop(root, redactionContainer, 10);

        summaryText = text("0 alerts", 15, R.color.app_text_primary, true);
        addTop(root, summaryText, 16);

        MaterialCardView searchBox = createSearchBox();
        addTop(root, searchBox, 10);

        LinearLayout searchActions = new LinearLayout(this);
        searchActions.setOrientation(LinearLayout.HORIZONTAL);

        MaterialButton search = filledButton("Search", R.color.button_primary);
        search.setOnClickListener(v -> refresh());
        searchActions.addView(search, new LinearLayout.LayoutParams(0, dp(50), 1f));

        MaterialButton clearAll = filledButton("Clear Inbox", R.color.button_danger);
        clearAll.setOnClickListener(v -> confirmClear());
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(0, dp(50), 1f);
        clearParams.setMargins(dp(10), 0, 0, 0);
        searchActions.addView(clearAll, clearParams);
        addTop(root, searchActions, 10);

        filterContainer = new LinearLayout(this);
        filterContainer.setOrientation(LinearLayout.HORIZONTAL);
        filterContainer.addView(filterButton("All", "ALL"));
        filterContainer.addView(filterButton("Banking", "BANKING"));
        filterContainer.addView(filterButton("Offers", "OFFERS"));
        filterContainer.addView(filterButton("Other", "OTHER"));
        addTop(root, filterContainer, 12);
        updateFilterStyles();

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        addTop(root, listContainer, 16);
        return scrollView;
    }

    private MaterialCardView createSearchBox() {
        MaterialCardView searchCard = card(R.color.app_surface, R.color.app_outline);
        searchCard.setRadius(dp(16));
        searchCard.setCardElevation(0);

        searchInput = new EditText(this);
        searchInput.setHint("Search sender or message");
        searchInput.setSingleLine(true);
        searchInput.setTextSize(14);
        searchInput.setTextColor(color(R.color.app_text_primary));
        searchInput.setHintTextColor(color(R.color.app_text_tertiary));
        searchInput.setGravity(Gravity.CENTER_VERTICAL);
        searchInput.setPadding(dp(15), 0, dp(15), 0);
        searchInput.setBackground(null);

        FrameLayout.LayoutParams inputParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
        );
        searchCard.addView(searchInput, inputParams);
        return searchCard;
    }

    private View filterButton(String label, String value) {
        MaterialButton button = outlinedButton(
                label,
                R.color.primary,
                R.color.app_surface,
                R.color.success_outline
        );
        button.setTextSize(10);
        button.setTag(value);
        button.setOnClickListener(v -> {
            filter = value;
            updateFilterStyles();
            refresh();
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        button.setLayoutParams(params);
        return button;
    }

    private void updateFilterStyles() {
        if (filterContainer == null) return;

        for (int index = 0; index < filterContainer.getChildCount(); index++) {
            View child = filterContainer.getChildAt(index);
            if (!(child instanceof MaterialButton)) continue;

            MaterialButton button = (MaterialButton) child;
            boolean selected = filter.equals(String.valueOf(button.getTag()));

            if (selected) {
                button.setBackgroundTintList(ColorStateList.valueOf(color(R.color.button_primary)));
                button.setTextColor(color(R.color.white));
                button.setStrokeWidth(0);
            } else {
                button.setBackgroundTintList(ColorStateList.valueOf(color(R.color.success_surface)));
                button.setTextColor(color(R.color.primary));
                button.setStrokeColor(ColorStateList.valueOf(color(R.color.success_outline)));
                button.setStrokeWidth(dp(1));
            }
        }
    }

    private void refresh() {
        if (listContainer == null) return;

        updateDirectAccessCard();
        updateRedactionNotice();

        List<SmsAlertStore.Item> items = SmsAlertStore.getAll(this);
        String query = searchInput == null
                ? ""
                : searchInput.getText().toString().trim().toLowerCase(Locale.ROOT);

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

        summaryText.setText(
                shown + " shown  •  " + unread + " unread  •  " + items.size() + " total"
        );

        if (shown == 0) {
            MaterialCardView empty = card(R.color.app_surface_soft, R.color.app_outline);
            LinearLayout emptyContent = content();
            emptyContent.addView(text("No financial SMS found", 16, R.color.app_text_primary, true));

            String help = DirectSmsAccessManager.hasAllPermissions(this)
                    ? "Tap Import Recent Transactions or wait for a new completed banking/UPI/card SMS. OTP and non-transactional messages are ignored."
                    : "Grant SMS access to import recent financial messages. Notification Access can still be used as a fallback.";

            emptyContent.addView(text(
                    help,
                    11,
                    R.color.app_text_secondary,
                    false
            ));
            empty.addView(emptyContent);
            listContainer.addView(empty);
        }
    }

    private void updateDirectAccessCard() {
        if (directAccessContainer == null) return;

        directAccessContainer.removeAllViews();

        boolean readGranted = DirectSmsAccessManager.hasReadPermission(this);
        boolean receiveGranted = DirectSmsAccessManager.hasReceivePermission(this);
        boolean allGranted = readGranted && receiveGranted;

        MaterialCardView accessCard = card(
                allGranted ? R.color.success_surface : R.color.warning_surface,
                allGranted ? R.color.success_outline : R.color.warning_outline
        );
        LinearLayout accessContent = content();

        accessContent.addView(text(
                allGranted ? "Direct SMS access is active" : "Direct SMS access is required",
                16,
                allGranted ? R.color.text_success : R.color.text_warning,
                true
        ));

        TextView status = text(
                "READ_SMS: " + (readGranted ? "Granted" : "Not granted")
                        + "  •  RECEIVE_SMS: " + (receiveGranted ? "Granted" : "Not granted"),
                11,
                R.color.app_text_secondary,
                false
        );
        addTop(accessContent, status, 5);

        TextView detail = text(
                allGranted
                        ? "Recent transactional SMS can be imported, and new financial SMS will be captured automatically."
                        : "The permissions are used only for local money-management features. OTP and unrelated personal messages are not imported.",
                11,
                R.color.app_text_secondary,
                false
        );
        addTop(accessContent, detail, 4);

        MaterialButton action = filledButton(
                allGranted ? "Import Recent Transactions" : "Grant SMS Access",
                allGranted ? R.color.button_primary : R.color.button_secondary
        );
        action.setOnClickListener(v -> {
            if (allGranted) {
                importRecentSms();
            } else {
                showSmsPermissionDisclosure();
            }
        });
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
        );
        actionParams.setMargins(0, dp(12), 0, 0);
        accessContent.addView(action, actionParams);

        accessCard.addView(accessContent);
        directAccessContainer.addView(accessCard);
    }

    private void showSmsPermissionDisclosure() {
        new AlertDialog.Builder(this)
                .setTitle("Allow financial SMS access?")
                .setMessage(
                        "Money Manager Pro will use READ_SMS to scan recent messages and RECEIVE_SMS to detect new messages. "
                                + "Only completed financial alerts such as debits, credits, UPI, card payments, refunds and withdrawals are copied into the app. "
                                + "OTP and unrelated personal messages are ignored. Processing remains on this device and SMS content is not uploaded."
                )
                .setNegativeButton("Not Now", null)
                .setPositiveButton("Continue", (dialog, which) -> requestSmsPermissions())
                .show();
    }

    private void requestSmsPermissions() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{
                        Manifest.permission.READ_SMS,
                        Manifest.permission.RECEIVE_SMS
                },
                SMS_PERMISSION_REQUEST_CODE
        );
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != SMS_PERMISSION_REQUEST_CODE) return;

        if (DirectSmsAccessManager.hasAllPermissions(this)) {
            Toast.makeText(this, "SMS access granted", Toast.LENGTH_SHORT).show();
            importRecentSms();
        } else {
            Toast.makeText(
                    this,
                    "SMS access was not fully granted. Notification mode remains available.",
                    Toast.LENGTH_LONG
            ).show();
            refresh();
        }
    }

    private void importRecentSms() {
        if (!DirectSmsAccessManager.hasReadPermission(this)) {
            showSmsPermissionDisclosure();
            return;
        }

        Toast.makeText(this, "Scanning recent financial SMS…", Toast.LENGTH_SHORT).show();

        smsExecutor.execute(() -> {
            int imported = DirectSmsAccessManager.importRecentFinancialSms(this);
            runOnUiThread(() -> {
                Toast.makeText(
                        this,
                        imported == 0
                                ? "No new financial SMS found"
                                : imported + " financial SMS imported",
                        Toast.LENGTH_LONG
                ).show();
                refresh();
            });
        });
    }

    private void updateRedactionNotice() {
        if (redactionContainer == null) return;

        int count = SmsAlertStore.getRedactedCount(this);
        if (count <= 0) {
            redactionContainer.removeAllViews();
            redactionContainer.setVisibility(View.GONE);
            return;
        }

        redactionContainer.removeAllViews();
        redactionContainer.setVisibility(View.VISIBLE);

        String sourcePackage = SmsAlertStore.getLastRedactedPackage(this);
        long lastAt = SmsAlertStore.getLastRedactedAt(this);

        MaterialCardView warningCard = card(R.color.warning_surface, R.color.warning_outline);
        LinearLayout warningContent = content();
        warningContent.addView(text(
                "Android hid notification text",
                16,
                R.color.text_warning,
                true
        ));

        String countText = count == 1
                ? "1 SMS notification was hidden before Notification Access could read it."
                : count + " SMS notifications were hidden before Notification Access could read them.";

        TextView explanation = text(
                countText + " Direct READ_SMS access can still import eligible financial messages after permission is granted.",
                11,
                R.color.app_text_secondary,
                false
        );
        addTop(warningContent, explanation, 5);

        if (lastAt > 0L) {
            TextView time = text(
                    "Last hidden alert: " + formatDate(lastAt),
                    10,
                    R.color.app_text_secondary,
                    false
            );
            addTop(warningContent, time, 5);
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        MaterialButton appSettings = filledButton(
                "SMS App Settings",
                R.color.button_secondary
        );
        appSettings.setEnabled(!sourcePackage.trim().isEmpty());
        appSettings.setOnClickListener(v -> openSmsAppSettings(sourcePackage));
        actions.addView(appSettings, new LinearLayout.LayoutParams(0, dp(46), 1f));

        MaterialButton dismiss = outlinedButton(
                "Dismiss",
                R.color.text_warning,
                R.color.warning_surface,
                R.color.warning_outline
        );
        dismiss.setOnClickListener(v -> {
            SmsAlertStore.clearRedactedNotice(this);
            refresh();
        });
        LinearLayout.LayoutParams dismissParams = new LinearLayout.LayoutParams(0, dp(46), 1f);
        dismissParams.setMargins(dp(8), 0, 0, 0);
        actions.addView(dismiss, dismissParams);
        addTop(warningContent, actions, 10);

        warningCard.addView(warningContent);
        redactionContainer.addView(warningCard);
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

        MaterialCardView alertCard = card(background, outline);
        LinearLayout alertContent = content();

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);

        TextView sender = text(item.sender, 15, R.color.app_text_primary, !item.read);
        heading.addView(
                sender,
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        );

        String badge = "direct-sms".equals(item.packageName)
                ? "DIRECT SMS"
                : item.category;
        heading.addView(text(badge, 9, R.color.secondary, true));
        alertContent.addView(heading);

        TextView message = text(item.message, 12, R.color.app_text_secondary, false);
        message.setMaxLines(6);
        addTop(alertContent, message, 6);
        alertContent.addView(text(
                formatDate(item.postedAt),
                9,
                R.color.app_text_secondary,
                false
        ));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);

        MaterialButton read = outlinedButton(
                item.read ? "Mark Unread" : "Mark Read",
                R.color.primary,
                R.color.success_surface,
                R.color.success_outline
        );
        read.setOnClickListener(v -> {
            SmsAlertStore.markRead(this, item.id, !item.read);
            refresh();
        });
        actions.addView(read, new LinearLayout.LayoutParams(0, dp(44), 1f));

        MaterialButton delete = outlinedButton(
                "Delete Local Copy",
                R.color.expense,
                R.color.error_surface,
                R.color.error_outline
        );
        delete.setOnClickListener(v -> {
            SmsAlertStore.delete(this, item.id);
            refresh();
        });
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        deleteParams.setMargins(dp(8), 0, 0, 0);
        actions.addView(delete, deleteParams);
        addTop(alertContent, actions, 10);

        alertCard.addView(alertContent);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dp(10));
        alertCard.setLayoutParams(cardParams);
        return alertCard;
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("Clear local SMS alerts?")
                .setMessage(
                        "This removes only SMS copies saved inside Money Manager Pro. "
                                + "It does not delete messages from your phone's SMS app."
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> {
                    SmsAlertStore.clear(this);
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

    private void openSmsAppSettings(String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) return;

        try {
            Intent intent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName);
            } else {
                intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + packageName));
            }
            startActivity(intent);
        } catch (Exception exception) {
            Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            fallback.setData(Uri.parse("package:" + packageName));
            startActivity(fallback);
        }
    }

    private MaterialCardView card(int background, int outline) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(color(background));
        card.setStrokeColor(color(outline));
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

    private MaterialButton filledButton(String label, int backgroundColor) {
        MaterialButton button = baseButton(label);
        button.setBackgroundTintList(ColorStateList.valueOf(color(backgroundColor)));
        button.setTextColor(color(R.color.white));
        button.setStrokeWidth(0);
        return button;
    }

    private MaterialButton outlinedButton(
            String label,
            int textColor,
            int backgroundColor,
            int outlineColor
    ) {
        MaterialButton button = baseButton(label);
        button.setBackgroundTintList(ColorStateList.valueOf(color(backgroundColor)));
        button.setTextColor(color(textColor));
        button.setStrokeColor(ColorStateList.valueOf(color(outlineColor)));
        button.setStrokeWidth(dp(1));
        return button;
    }

    private MaterialButton baseButton(String label) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(11);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setCornerRadius(dp(16));
        button.setPadding(dp(10), 0, dp(10), 0);
        BubbleTouchAnimator.apply(button);
        return button;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color(color));
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
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

    private String formatDate(long time) {
        return new SimpleDateFormat(
                "dd MMM yyyy, hh:mm a",
                Locale.getDefault()
        ).format(new Date(time));
    }

    private int color(int resource) {
        return ContextCompat.getColor(this, resource);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
