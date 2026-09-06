package com.example.moneymanagerpro.floating;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Transparent bridge for floating Expense external actions.
 *
 * UPI payment is intentionally delegated to the selected UPI app's own native
 * screen. Money Manager does not scan the QR and does not construct a third-
 * party payment intent in this flow. The user opens the UPI app here and uses
 * that app's own Scan & Pay experience.
 */
public final class FloatingExpenseExternalActionActivity extends Activity {

    static final String ACTION_UPI =
            "com.example.moneymanagerpro.floating.action.UPI";
    static final String ACTION_QR =
            "com.example.moneymanagerpro.floating.action.QR";
    static final String ACTION_RECEIPT =
            "com.example.moneymanagerpro.floating.action.RECEIPT";

    // Kept for binary/source compatibility with the existing service routing.
    static final String EXTRA_PAYMENT_URI = "payment_uri";

    private static final int REQUEST_RECEIPT = 7412;

    private static final String[] FALLBACK_UPI_PACKAGES = {
            "com.google.android.apps.nbu.paisa.user",
            "com.phonepe.app",
            "net.one97.paytm",
            "in.org.npci.upiapp",
            "in.amazon.mShop.android.shopping",
            "com.dreamplug.androidapp"
    };

    private boolean nativeUpiLaunched;
    private boolean pausedAfterNativeLaunch;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            nativeUpiLaunched = savedInstanceState.getBoolean(
                    "native_upi_launched",
                    false
            );
            pausedAfterNativeLaunch = savedInstanceState.getBoolean(
                    "native_upi_paused",
                    false
            );
            return;
        }

        String action = getIntent() == null
                ? null
                : getIntent().getAction();

        if (ACTION_QR.equals(action) || ACTION_UPI.equals(action)) {
            openNativeUpiAppChooser();
        } else if (ACTION_RECEIPT.equals(action)) {
            openReceiptPicker();
        } else {
            finishWithoutAnimation();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("native_upi_launched", nativeUpiLaunched);
        outState.putBoolean("native_upi_paused", pausedAfterNativeLaunch);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        if (nativeUpiLaunched) {
            pausedAfterNativeLaunch = true;
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nativeUpiLaunched && pausedAfterNativeLaunch) {
            // The user has returned from the selected UPI app. We deliberately
            // do not assume payment success because launcher activities do not
            // provide a trustworthy payment result.
            restoreExpenseOverlay();
            finishWithoutAnimation();
        }
    }

    private void openNativeUpiAppChooser() {
        FloatingOverlayUiState.hideExpenseForExternalAction();

        List<UpiApp> apps = detectNativeUpiApps();
        if (apps.isEmpty()) {
            Toast.makeText(
                    this,
                    "No compatible UPI app is available",
                    Toast.LENGTH_LONG
            ).show();
            restoreExpenseOverlay();
            finishWithoutAnimation();
            return;
        }

        CharSequence[] labels = new CharSequence[apps.size()];
        for (int index = 0; index < apps.size(); index++) {
            labels[index] = apps.get(index).label;
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Scan & Pay with UPI App")
                .setMessage("Choose an app, then use that app's own QR scanner.")
                .setItems(labels, (whichDialog, which) ->
                        launchNativeUpiApp(apps.get(which)))
                .setNegativeButton("Cancel", (whichDialog, which) -> {
                    restoreExpenseOverlay();
                    finishWithoutAnimation();
                })
                .create();

        dialog.setOnCancelListener(ignored -> {
            restoreExpenseOverlay();
            finishWithoutAnimation();
        });
        dialog.show();
    }

    private List<UpiApp> detectNativeUpiApps() {
        PackageManager packageManager = getPackageManager();
        Map<String, UpiApp> discovered = new LinkedHashMap<>();

        Intent probe = new Intent(
                Intent.ACTION_VIEW,
                Uri.parse("upi://pay?pa=merchant@upi&pn=Merchant&cu=INR")
        );

        try {
            List<ResolveInfo> resolved = packageManager.queryIntentActivities(
                    probe,
                    PackageManager.MATCH_DEFAULT_ONLY
            );
            for (ResolveInfo info : resolved) {
                if (info == null || info.activityInfo == null) {
                    continue;
                }
                String packageName = info.activityInfo.packageName;
                addLaunchablePackage(
                        packageManager,
                        discovered,
                        packageName,
                        info.loadLabel(packageManager)
                );
            }
        } catch (Exception ignored) {
        }

        for (String packageName : FALLBACK_UPI_PACKAGES) {
            addLaunchablePackage(
                    packageManager,
                    discovered,
                    packageName,
                    null
            );
        }

        List<UpiApp> result = new ArrayList<>(discovered.values());
        Collections.sort(
                result,
                (left, right) -> left.label.toLowerCase(Locale.US)
                        .compareTo(right.label.toLowerCase(Locale.US))
        );
        return result;
    }

    private void addLaunchablePackage(
            PackageManager packageManager,
            Map<String, UpiApp> discovered,
            String packageName,
            @Nullable CharSequence knownLabel
    ) {
        if (packageName == null
                || packageName.trim().isEmpty()
                || packageName.equals(getPackageName())
                || discovered.containsKey(packageName)) {
            return;
        }

        Intent launchIntent;
        try {
            launchIntent = packageManager.getLaunchIntentForPackage(packageName);
        } catch (Exception exception) {
            launchIntent = null;
        }
        if (launchIntent == null) {
            return;
        }

        String label = knownLabel == null
                ? ""
                : knownLabel.toString().trim();
        if (label.isEmpty()) {
            try {
                label = packageManager
                        .getApplicationLabel(
                                packageManager.getApplicationInfo(packageName, 0)
                        )
                        .toString()
                        .trim();
            } catch (Exception ignored) {
                label = packageName;
            }
        }

        discovered.put(packageName, new UpiApp(packageName, label));
    }

    private void launchNativeUpiApp(UpiApp app) {
        if (app == null) {
            restoreExpenseOverlay();
            finishWithoutAnimation();
            return;
        }

        Intent launchIntent;
        try {
            launchIntent = getPackageManager()
                    .getLaunchIntentForPackage(app.packageName);
        } catch (Exception exception) {
            launchIntent = null;
        }

        if (launchIntent == null) {
            Toast.makeText(
                    this,
                    app.label + " cannot be opened",
                    Toast.LENGTH_LONG
            ).show();
            restoreExpenseOverlay();
            finishWithoutAnimation();
            return;
        }

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        nativeUpiLaunched = true;
        pausedAfterNativeLaunch = false;

        try {
            startActivity(launchIntent);
        } catch (Exception exception) {
            nativeUpiLaunched = false;
            Toast.makeText(
                    this,
                    "Unable to open " + app.label,
                    Toast.LENGTH_LONG
            ).show();
            restoreExpenseOverlay();
            finishWithoutAnimation();
        }
    }

    private void openReceiptPicker() {
        FloatingOverlayUiState.hideExpenseForExternalAction();

        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("image/*");
        picker.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );

        try {
            startActivityForResult(picker, REQUEST_RECEIPT);
        } catch (Exception exception) {
            Toast.makeText(
                    this,
                    "No image picker is available",
                    Toast.LENGTH_SHORT
            ).show();
            restoreExpenseOverlay();
            finishWithoutAnimation();
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            @Nullable Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != REQUEST_RECEIPT) {
            return;
        }

        String uriText = "";
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                );
            } catch (Exception ignored) {
            }
            uriText = uri.toString();
        }

        sendResultToService(
                FloatingQuickEntryService.ACTION_EXTERNAL_RECEIPT_RESULT,
                uriText
        );
        restoreExpenseOverlay();
        finishWithoutAnimation();
    }

    private void sendResultToService(String action, String payload) {
        Intent intent = new Intent(this, FloatingQuickEntryService.class);
        intent.setAction(action);
        intent.putExtra(
                FloatingQuickEntryService.EXTRA_EXTERNAL_PAYLOAD,
                payload == null ? "" : payload
        );
        ContextCompat.startForegroundService(this, intent);
    }

    private void restoreExpenseOverlay() {
        getWindow().getDecorView().post(
                FloatingOverlayUiState::restoreExpenseAfterExternalAction
        );
    }

    private void finishWithoutAnimation() {
        finish();
        overridePendingTransition(0, 0);
    }

    private static final class UpiApp {
        final String packageName;
        final String label;

        UpiApp(String packageName, String label) {
            this.packageName = packageName;
            this.label = label;
        }
    }
}
