package com.example.moneymanagerpro.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.cloud.TridevIntegrationCloudManager;
import com.example.moneymanagerpro.cloud.TridevIntegrationCloudScheduler;
import com.example.moneymanagerpro.cloud.TridevIntegrationCloudSnapshot;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** STEP 13 - private encrypted integration cloud recovery screen. */
public final class IntegrationCloudRecoveryActivity extends AppCompatActivity {

    private TextView statusView;
    private TextView metaView;
    private TextView eventCountView;
    private TextView mappingCountView;
    private MaterialButton syncButton;
    private MaterialButton restoreButton;
    private MaterialButton openBackupButton;
    private ProgressBar progress;

    private TridevIntegrationCloudManager cloudManager;
    private int statusGeneration = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_integration_cloud_recovery);

        cloudManager = new TridevIntegrationCloudManager(getApplicationContext());
        TridevIntegrationCloudScheduler.ensurePeriodic(getApplicationContext());
        bindViews();
        setupActions();
        loadStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cloudManager != null) loadStatus();
    }

    private void bindViews() {
        statusView = findViewById(R.id.integrationCloudStatus);
        metaView = findViewById(R.id.integrationCloudMeta);
        eventCountView = findViewById(R.id.integrationCloudEventCount);
        mappingCountView = findViewById(R.id.integrationCloudMappingCount);
        syncButton = findViewById(R.id.integrationCloudSync);
        restoreButton = findViewById(R.id.integrationCloudRestore);
        openBackupButton = findViewById(R.id.integrationCloudOpenBackup);
        progress = findViewById(R.id.integrationCloudProgress);
    }

    private void setupActions() {
        findViewById(R.id.integrationCloudBack).setOnClickListener(v -> finish());
        syncButton.setOnClickListener(v -> syncNow());
        restoreButton.setOnClickListener(v -> confirmRestore());
        openBackupButton.setOnClickListener(v -> {
            try {
                startActivity(new Intent(this, BackupActivity.class));
            } catch (RuntimeException failure) {
                Toast.makeText(this, "Cloud backup settings could not be opened.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadStatus() {
        final int generation = ++statusGeneration;
        setBusy(true);
        statusView.setText(R.string.integration_cloud_loading);
        cloudManager.loadStatus(status -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed() || generation != statusGeneration) return;
            setBusy(false);
            renderStatus(status);
        }));
    }

    private void renderStatus(@NonNull TridevIntegrationCloudManager.CloudStatus status) {
        StringBuilder message = new StringBuilder(status.message);
        if (!status.signedIn) {
            message.append("\nAccount: Not signed in");
        } else if (!status.emailVerified) {
            message.append("\nAccount: Signed in • email verification required");
        } else {
            message.append("\nAccount: Verified");
        }
        message.append(status.passphraseReady
                ? "\nRecovery key: Ready on this device"
                : "\nRecovery key: Unlock cloud backup first");
        statusView.setText(message.toString());

        eventCountView.setText(String.valueOf(status.remoteEventCount));
        mappingCountView.setText(String.valueOf(status.remoteMappingCount));

        StringBuilder meta = new StringBuilder();
        meta.append("Remote snapshot: ")
                .append(status.remoteCreatedAt > 0L
                        ? formatTime(status.remoteCreatedAt)
                        : getString(R.string.integration_cloud_never));
        meta.append("\nLast sync on this phone: ")
                .append(status.lastLocalSyncAt > 0L
                        ? formatTime(status.lastLocalSyncAt)
                        : getString(R.string.integration_cloud_never));
        meta.append("\nLast restore on this phone: ")
                .append(status.lastLocalRestoreAt > 0L
                        ? formatTime(status.lastLocalRestoreAt)
                        : getString(R.string.integration_cloud_never));
        if (status.remoteTruncated) {
            meta.append("\nCloud snapshot reached the safety item limit; resolve older reviews before the next sync.");
        }
        if (!status.lastError.isEmpty()) {
            meta.append("\nLast cloud issue: ").append(status.lastError);
        }
        metaView.setText(meta.toString());

        boolean ready = status.signedIn && status.emailVerified && status.passphraseReady;
        syncButton.setEnabled(ready);
        restoreButton.setEnabled(ready && status.remoteSnapshotExists);
    }

    private void syncNow() {
        setBusy(true);
        statusView.setText("Encrypting and syncing integration recovery state…");
        new Thread(() -> cloudManager.syncNow(new TridevIntegrationCloudManager.SyncCallback() {
            @Override
            public void onSuccess(@NonNull TridevIntegrationCloudManager.CloudStatus status) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    setBusy(false);
                    renderStatus(status);
                    Toast.makeText(
                            IntegrationCloudRecoveryActivity.this,
                            "Encrypted integration recovery synced.",
                            Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onError(@NonNull Exception exception) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    setBusy(false);
                    statusView.setText(safeMessage(exception));
                    Toast.makeText(
                            IntegrationCloudRecoveryActivity.this,
                            safeMessage(exception),
                            Toast.LENGTH_LONG).show();
                });
            }
        }), "IntegrationCloudManualSync").start();
    }

    private void confirmRestore() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.integration_cloud_restore)
                .setMessage(R.string.integration_cloud_restore_warning)
                .setPositiveButton(R.string.integration_cloud_restore_confirm,
                        (dialog, which) -> restoreNow())
                .setNegativeButton(R.string.integration_cloud_cancel, null)
                .show();
    }

    private void restoreNow() {
        setBusy(true);
        statusView.setText("Downloading and verifying encrypted integration recovery…");
        cloudManager.restoreNow(new TridevIntegrationCloudManager.RestoreCallback() {
            @Override
            public void onSuccess(
                    @NonNull TridevIntegrationCloudSnapshot.RestoreResult result,
                    @NonNull TridevIntegrationCloudManager.CloudStatus status) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    setBusy(false);
                    renderStatus(status);
                    String summary = "Recovered mappings " + result.restoredMappings
                            + " • Events " + result.restoredEvents
                            + " • Already present " + result.alreadyPresentEvents;
                    if (result.skippedMappings > 0 || result.rejectedEvents > 0) {
                        summary += " • Skipped "
                                + (result.skippedMappings + result.rejectedEvents);
                    }
                    Toast.makeText(
                            IntegrationCloudRecoveryActivity.this,
                            summary,
                            Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onError(@NonNull Exception exception) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    setBusy(false);
                    statusView.setText(safeMessage(exception));
                    Toast.makeText(
                            IntegrationCloudRecoveryActivity.this,
                            safeMessage(exception),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        syncButton.setEnabled(!busy);
        restoreButton.setEnabled(!busy);
        openBackupButton.setEnabled(!busy);
    }

    private String formatTime(long millis) {
        return new SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.getDefault())
                .format(new Date(millis));
    }

    private String safeMessage(Exception exception) {
        if (exception == null || exception.getMessage() == null
                || exception.getMessage().trim().isEmpty()) {
            return "Integration cloud recovery failed safely.";
        }
        return exception.getMessage().trim();
    }
}
