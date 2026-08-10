package com.example.moneymanagerpro.pro;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.example.moneymanagerpro.backup.BackupIntegrity;
import com.example.moneymanagerpro.utils.BackupStorageManager;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Adds automatic history, restore-point and integrity tools to BackupActivity. */
public final class BackupSecurityProController {

    private static final String PANEL_TAG = "backup_security_pro_panel";
    private static final int MAX_VERIFY_BYTES = 25 * 1024 * 1024;

    private final Activity activity;
    private final BackupHistoryManager historyManager;
    private final BackupStorageManager storageManager;

    private LinearLayout panel;
    private LinearLayout restoreContainer;
    private LinearLayout historyContainer;
    private TextView txtStatus;
    private TextView chipIntegrity;
    private TextView chipRestorePoints;
    private int requestVersion;

    public BackupSecurityProController(@NonNull Activity activity) {
        this.activity = activity;
        this.historyManager = new BackupHistoryManager(activity);
        this.storageManager = new BackupStorageManager(activity);
    }

    public void attach() {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        ViewGroup content = activity.findViewById(android.R.id.content);
        LinearLayout root = findMainVertical(content);
        if (root == null) return;

        View existing = root.findViewWithTag(PANEL_TAG);
        if (existing instanceof LinearLayout) {
            panel = (LinearLayout) existing;
            refresh(false);
            return;
        }

        panel = new LinearLayout(activity);
        panel.setTag(PANEL_TAG);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(0, 0, 0, dp(8));

        LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        panelParams.setMargins(0, dp(20), 0, dp(8));
        panel.setLayoutParams(panelParams);

        panel.addView(text("Backup & Security Pro", 19, "#17351F", true));
        TextView subtitle = text(
                "Automatic history • verified restore points • SHA-256 integrity",
                10,
                "#667085",
                false
        );
        setMargins(subtitle, 0, 3, 0, 10);
        panel.addView(subtitle);

        LinearLayout chips = horizontal();
        chips.addView(chip("Encrypted cloud", "#EAF2FF", "#0F6CBD"));
        chipIntegrity = chip("Integrity checking", "#FFF4E8", "#D83B01");
        chips.addView(chipIntegrity);
        chipRestorePoints = chip("Restore points …", "#EFF9F1", "#107C41");
        chips.addView(chipRestorePoints);
        panel.addView(chips);

        MaterialCardView overview = card("#F7F9FC", "#D8E0E8");
        LinearLayout overviewContent = verticalPadding(13);
        txtStatus = text("Checking backup protection…", 11, "#475467", false);
        txtStatus.setLineSpacing(dp(2), 1f);
        overviewContent.addView(txtStatus);
        overview.addView(overviewContent);
        setMargins(overview, 0, 10, 0, 0);
        panel.addView(overview);

        LinearLayout actions = horizontal();
        setMargins(actions, 0, 9, 0, 0);
        MaterialButton verify = button("Verify Latest", false);
        MaterialButton create = button("Create Restore Point", true);
        verify.setOnClickListener(v -> verifyLatest());
        create.setOnClickListener(v -> createRestorePoint());
        BubbleTouchAnimator.apply(verify);
        BubbleTouchAnimator.apply(create);
        actions.addView(verify);
        actions.addView(create);
        panel.addView(actions);

        MaterialButton refresh = button("Refresh Backup History", false);
        setMargins(refresh, 0, 7, 0, 0);
        refresh.setOnClickListener(v -> refresh(false));
        BubbleTouchAnimator.apply(refresh);
        panel.addView(refresh);

        TextView restoreTitle = text("Verified Restore Points", 15, "#17351F", true);
        setMargins(restoreTitle, 0, 16, 0, 5);
        panel.addView(restoreTitle);

        TextView restoreHelp = text(
                "Tap Verify & Restore on a restore point. The file is checked again with SHA-256 before the normal restore engine is allowed to replace current data.",
                10,
                "#667085",
                false
        );
        restoreHelp.setLineSpacing(dp(2), 1f);
        setMargins(restoreHelp, 0, 0, 0, 8);
        panel.addView(restoreHelp);

        restoreContainer = new LinearLayout(activity);
        restoreContainer.setOrientation(LinearLayout.VERTICAL);
        panel.addView(restoreContainer);

        TextView historyTitle = text("Automatic Backup History", 15, "#17351F", true);
        setMargins(historyTitle, 0, 12, 0, 7);
        panel.addView(historyTitle);

        historyContainer = new LinearLayout(activity);
        historyContainer.setOrientation(LinearLayout.VERTICAL);
        panel.addView(historyContainer);

        root.addView(panel);
        refresh(true);
    }

    private void refresh(boolean captureLatest) {
        final int version = ++requestVersion;
        if (txtStatus != null) {
            txtStatus.setText("Refreshing backup protection…");
            txtStatus.setTextColor(Color.parseColor("#475467"));
        }

        new Thread(() -> {
            String message;
            boolean integrity = false;
            List<BackupHistoryManager.RestorePoint> points;
            List<BackupHistoryManager.HistoryEntry> history;

            try {
                historyManager.captureCheckpoints(captureLatest);
                integrity = historyManager.verifyLatestLocalBackup();
                points = historyManager.listRestorePoints();
                history = historyManager.getStatusHistory();

                if (!historyManager.hasUsableBackupFolder()) {
                    message = "Offline backup folder is not available yet. Select the backup folder above; encrypted cloud backup remains independent.";
                } else if (integrity) {
                    message = "Latest local backup passed SHA-256 verification. "
                            + points.size() + " restore point"
                            + (points.size() == 1 ? " is" : "s are")
                            + " available.\n" + historyManager.getBackupLocationLabel();
                } else {
                    message = "Latest local backup could not be verified. Do not use an unverified file for restore until a fresh backup succeeds.";
                }
            } catch (Exception exception) {
                points = historyManager.listRestorePoints();
                history = historyManager.getStatusHistory();
                message = useful(exception, "Backup history could not be refreshed.");
            }

            final boolean verified = integrity;
            final String statusMessage = message;
            final List<BackupHistoryManager.RestorePoint> finalPoints = points;
            final List<BackupHistoryManager.HistoryEntry> finalHistory = history;

            activity.runOnUiThread(() -> {
                if (version != requestVersion || activity.isFinishing() || activity.isDestroyed()) return;
                render(statusMessage, verified, finalPoints, finalHistory);
            });
        }).start();
    }

    private void verifyLatest() {
        final int version = ++requestVersion;
        txtStatus.setText("Verifying latest local backup…");

        new Thread(() -> {
            boolean valid = false;
            String message;
            try {
                valid = historyManager.verifyLatestLocalBackup();
                message = valid
                        ? "Integrity verified: the latest local backup matches its SHA-256 checksum."
                        : "Integrity verification failed or no latest local backup is available.";
            } catch (Exception exception) {
                message = useful(exception, "Integrity verification failed.");
            }

            final boolean verified = valid;
            final String result = message;
            activity.runOnUiThread(() -> {
                if (version != requestVersion || activity.isFinishing() || activity.isDestroyed()) return;
                txtStatus.setText(result);
                txtStatus.setTextColor(Color.parseColor(verified ? "#107C41" : "#C42B1C"));
                chipIntegrity.setText(verified ? "Integrity ✓" : "Integrity review");
            });
        }).start();
    }

    private void createRestorePoint() {
        txtStatus.setText("Creating verified restore point…");
        final int version = ++requestVersion;

        new Thread(() -> {
            String message;
            try {
                boolean created = historyManager.captureLocalRestorePoint(true);
                historyManager.captureCheckpoints(false);
                message = created
                        ? "Verified restore point created successfully."
                        : "A latest local backup is not available yet. Create an offline backup first.";
            } catch (Exception exception) {
                message = useful(exception, "Restore point could not be created.");
            }

            final String result = message;
            activity.runOnUiThread(() -> {
                if (version != requestVersion || activity.isFinishing() || activity.isDestroyed()) return;
                txtStatus.setText(result);
                refresh(false);
            });
        }).start();
    }

    private void render(
            @NonNull String status,
            boolean verified,
            @NonNull List<BackupHistoryManager.RestorePoint> points,
            @NonNull List<BackupHistoryManager.HistoryEntry> history
    ) {
        txtStatus.setText(status);
        txtStatus.setTextColor(Color.parseColor(verified ? "#107C41" : "#475467"));
        chipIntegrity.setText(verified ? "Integrity ✓" : "Integrity check");
        chipRestorePoints.setText("Restore points " + points.size());

        restoreContainer.removeAllViews();
        if (points.isEmpty()) {
            restoreContainer.addView(infoRow(
                    "No restore points yet",
                    "A verified point will be created after the latest offline backup changes.",
                    "#F7F9FC",
                    "#D8E0E8"
            ));
        } else {
            int visible = Math.min(5, points.size());
            for (int i = 0; i < visible; i++) {
                restoreContainer.addView(restorePointRow(points.get(i), i));
            }
        }

        historyContainer.removeAllViews();
        if (history.isEmpty()) {
            historyContainer.addView(infoRow(
                    "History will appear automatically",
                    "Successful offline and encrypted-cloud backup checkpoints are recorded without storing your recovery passphrase.",
                    "#F4F0FF",
                    "#D8C8F2"
            ));
        } else {
            int visible = Math.min(6, history.size());
            for (int i = 0; i < visible; i++) {
                BackupHistoryManager.HistoryEntry item = history.get(i);
                String details = BackupHistoryManager.formatTime(item.timeMillis)
                        + " • " + item.recordCount + " records"
                        + " • " + BackupHistoryManager.formatSize(item.byteCount);
                historyContainer.addView(infoRow(
                        item.type,
                        details,
                        "Encrypted cloud".equals(item.type) ? "#EEF5FF" : "#F7F9FC",
                        "Encrypted cloud".equals(item.type) ? "#BDD5EE" : "#D8E0E8"
                ));
            }
        }
    }

    private MaterialCardView restorePointRow(
            @NonNull BackupHistoryManager.RestorePoint point,
            int index
    ) {
        MaterialCardView card = card("#EFF9F1", "#B9DFC3");
        LinearLayout content = verticalPadding(11);
        content.addView(text("Restore Point " + (index + 1), 12, "#17351F", true));

        TextView details = text(
                BackupHistoryManager.formatTime(point.lastModified)
                        + " • " + BackupHistoryManager.formatSize(point.sizeBytes),
                10,
                "#667085",
                false
        );
        setMargins(details, 0, 3, 0, 7);
        content.addView(details);

        MaterialButton restore = button("Verify & Restore", true);
        LinearLayout.LayoutParams restoreParams = new LinearLayout.LayoutParams(-1, dp(40));
        restore.setLayoutParams(restoreParams);
        restore.setOnClickListener(v -> verifyAndRestore(point));
        BubbleTouchAnimator.apply(restore);
        content.addView(restore);

        card.addView(content);
        setMargins(card, 0, 0, 0, 6);
        return card;
    }

    private void verifyAndRestore(@NonNull BackupHistoryManager.RestorePoint point) {
        txtStatus.setText("Verifying selected restore point…");
        txtStatus.setTextColor(Color.parseColor("#0F6CBD"));

        new Thread(() -> {
            boolean valid = false;
            String failure = "Selected restore point failed integrity verification.";
            try {
                valid = verifyRestorePoint(point.uri);
            } catch (Exception exception) {
                failure = useful(exception, failure);
            }

            final boolean verified = valid;
            final String failureMessage = failure;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (!verified) {
                    txtStatus.setText(failureMessage);
                    txtStatus.setTextColor(Color.parseColor("#C42B1C"));
                    return;
                }
                txtStatus.setText("Restore point verified. Confirmation required.");
                txtStatus.setTextColor(Color.parseColor("#107C41"));
                showRestoreConfirmation(point);
            });
        }).start();
    }

    private boolean verifyRestorePoint(@NonNull Uri uri) throws Exception {
        long size = storageManager.getDocumentSize(uri);
        if (size > MAX_VERIFY_BYTES) return false;

        byte[] bytes;
        try (InputStream input = storageManager.openBackupInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_VERIFY_BYTES) return false;
                output.write(buffer, 0, read);
            }
            bytes = output.toByteArray();
        }

        JSONObject root = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        return BackupIntegrity.verify(root, root.optString("integritySha256", ""));
    }

    private void showRestoreConfirmation(@NonNull BackupHistoryManager.RestorePoint point) {
        new AlertDialog.Builder(activity)
                .setTitle("Restore this point?")
                .setMessage(
                        "Verified restore point:\n"
                                + BackupHistoryManager.formatTime(point.lastModified)
                                + "\n"
                                + BackupHistoryManager.formatSize(point.sizeBytes)
                                + "\n\nCurrent app data will be replaced by this backup. "
                                + "The normal Money Manager Pro restore engine will validate the file again before applying it."
                )
                .setPositiveButton("Restore", (dialog, which) -> invokeExistingRestore(point.uri))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void invokeExistingRestore(@NonNull Uri restorePointUri) {
        try {
            Method method = activity.getClass().getDeclaredMethod("restoreBackup", Uri.class);
            method.setAccessible(true);
            method.invoke(activity, restorePointUri);
        } catch (Exception exception) {
            txtStatus.setText(useful(exception, "Restore engine could not be opened."));
            txtStatus.setTextColor(Color.parseColor("#C42B1C"));
        }
    }

    private MaterialCardView infoRow(String title, String detail, String background, String outline) {
        MaterialCardView card = card(background, outline);
        LinearLayout content = verticalPadding(11);
        content.addView(text(title, 12, "#17351F", true));
        TextView details = text(detail, 10, "#667085", false);
        details.setLineSpacing(dp(2), 1f);
        setMargins(details, 0, 3, 0, 0);
        content.addView(details);
        card.addView(content);
        setMargins(card, 0, 0, 0, 6);
        return card;
    }

    private LinearLayout findMainVertical(ViewGroup root) {
        if (root == null) return null;
        LinearLayout best = null;
        int bestChildren = -1;
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout layout = (LinearLayout) child;
                if (layout.getOrientation() == LinearLayout.VERTICAL && layout.getChildCount() > bestChildren) {
                    best = layout;
                    bestChildren = layout.getChildCount();
                }
            }
            if (child instanceof ViewGroup) {
                LinearLayout nested = findMainVertical((ViewGroup) child);
                if (nested != null && nested.getChildCount() > bestChildren) {
                    best = nested;
                    bestChildren = nested.getChildCount();
                }
            }
        }
        return best;
    }

    private MaterialCardView card(String background, String outline) {
        MaterialCardView card = new MaterialCardView(activity);
        card.setCardBackgroundColor(Color.parseColor(background));
        card.setStrokeColor(Color.parseColor(outline));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(16));
        card.setCardElevation(0f);
        card.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return card;
    }

    private TextView chip(String value, String background, String foreground) {
        TextView chip = text(value, 9, foreground, true);
        chip.setGravity(Gravity.CENTER);
        chip.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(background)));
        chip.setPadding(dp(8), dp(5), dp(8), dp(5));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(32), 1f);
        params.setMargins(dp(2), 0, dp(2), 0);
        chip.setLayoutParams(params);
        return chip;
    }

    private MaterialButton button(String label, boolean strong) {
        MaterialButton button = new MaterialButton(activity);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(10);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setCornerRadius(dp(13));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setTextColor(Color.parseColor(strong ? "#FFFFFF" : "#17351F"));
        button.setBackgroundTintList(ColorStateList.valueOf(
                Color.parseColor(strong ? "#0F6CBD" : "#FFFFFF")
        ));
        button.setStrokeColor(ColorStateList.valueOf(Color.parseColor(strong ? "#0F6CBD" : "#C9D7CD")));
        button.setStrokeWidth(dp(1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(43), 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        button.setLayoutParams(params);
        return button;
    }

    private TextView text(String value, float size, String color, boolean bold) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(Color.parseColor(color));
        text.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        text.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return text;
    }

    private LinearLayout verticalPadding(int padding) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(padding), dp(padding), dp(padding), dp(padding));
        return layout;
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setBaselineAligned(false);
        layout.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return layout;
    }

    private void setMargins(View view, int left, int top, int right, int bottom) {
        ViewGroup.LayoutParams raw = view.getLayoutParams();
        LinearLayout.LayoutParams params = raw instanceof LinearLayout.LayoutParams
                ? (LinearLayout.LayoutParams) raw
                : new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        view.setLayoutParams(params);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private String useful(Exception exception, String fallback) {
        String message = exception == null ? null : exception.getMessage();
        return message == null || message.trim().isEmpty() ? fallback : message.trim();
    }
}
