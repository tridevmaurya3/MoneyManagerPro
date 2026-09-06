package com.example.moneymanagerpro.floating;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.utils.UpiQrPayloadParser;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

import java.util.ArrayList;
import java.util.List;

/**
 * Invisible bridge used only when the true WindowManager expense overlay needs
 * an Android Activity result. It never opens the Money Manager dashboard.
 */
public final class FloatingExpenseExternalActionActivity extends Activity {

    static final String ACTION_UPI =
            "com.example.moneymanagerpro.floating.action.UPI";
    static final String ACTION_QR =
            "com.example.moneymanagerpro.floating.action.QR";
    static final String ACTION_RECEIPT =
            "com.example.moneymanagerpro.floating.action.RECEIPT";

    static final String EXTRA_PAYMENT_URI = "payment_uri";

    private static final int REQUEST_UPI = 7411;
    private static final int REQUEST_RECEIPT = 7412;

    private static final String[] QR_UPI_APP_LABELS = {
            "Google Pay",
            "PhonePe",
            "Paytm",
            "BHIM",
            "Amazon Pay",
            "CRED"
    };

    private static final String[] QR_UPI_APP_PACKAGES = {
            "com.google.android.apps.nbu.paisa.user",
            "com.phonepe.app",
            "net.one97.paytm",
            "in.org.npci.upiapp",
            "in.amazon.mShop.android.shopping",
            "com.dreamplug.androidapp"
    };

    private String selectedQrUpiPackage = "";
    private String selectedQrUpiLabel = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            return;
        }

        String action = getIntent() == null
                ? null
                : getIntent().getAction();

        if (ACTION_UPI.equals(action)) {
            openUpiChooser();
        } else if (ACTION_QR.equals(action)) {
            openQrModeUpiAppChooser();
        } else if (ACTION_RECEIPT.equals(action)) {
            openReceiptPicker();
        } else {
            finishWithoutAnimation();
        }
    }

    /**
     * Manual UPI-ID mode: exactly the normal upi://pay chooser flow.
     */
    private void openUpiChooser() {
        String uriText = getIntent().getStringExtra(EXTRA_PAYMENT_URI);
        if (uriText == null || uriText.trim().isEmpty()) {
            restoreExpenseOverlay();
            finishWithoutAnimation();
            return;
        }

        FloatingOverlayUiState.hideExpenseForExternalAction();

        Intent paymentIntent = new Intent(
                Intent.ACTION_VIEW,
                Uri.parse(uriText)
        );
        Intent chooser = Intent.createChooser(
                paymentIntent,
                "Choose UPI / Payment App"
        );

        try {
            startActivityForResult(chooser, REQUEST_UPI);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(
                    this,
                    "No UPI payment app is available",
                    Toast.LENGTH_LONG
            ).show();
            sendResultToService(
                    FloatingQuickEntryService.ACTION_EXTERNAL_UPI_RESULT,
                    ""
            );
            restoreExpenseOverlay();
            finishWithoutAnimation();
        }
    }

    /**
     * QR mode is intentionally two-step:
     * 1) choose the UPI app,
     * 2) open the scanner, then send the scanned UPI URI to that exact app.
     *
     * Merely choosing "Scan UPI QR Code" in the floating dropdown never starts
     * an external screen. This method runs only after Choose UPI App is tapped.
     */
    private void openQrModeUpiAppChooser() {
        FloatingOverlayUiState.hideExpenseForExternalAction();

        List<Integer> availableIndexes = detectAvailableUpiApps();
        if (availableIndexes.isEmpty()) {
            // Package visibility can be restrictive on some Android builds.
            // Keep the known UPI apps visible; explicit launch below remains the
            // final installed-app check and fails safely if one is unavailable.
            for (int index = 0; index < QR_UPI_APP_PACKAGES.length; index++) {
                availableIndexes.add(index);
            }
        }

        CharSequence[] labels = new CharSequence[availableIndexes.size()];
        for (int index = 0; index < availableIndexes.size(); index++) {
            labels[index] = QR_UPI_APP_LABELS[availableIndexes.get(index)];
        }

        final List<Integer> choices = availableIndexes;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Choose UPI / Payment App")
                .setItems(labels, (whichDialog, which) -> {
                    int appIndex = choices.get(which);
                    selectedQrUpiPackage = QR_UPI_APP_PACKAGES[appIndex];
                    selectedQrUpiLabel = QR_UPI_APP_LABELS[appIndex];
                    startQrScannerForSelectedApp();
                })
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

    private List<Integer> detectAvailableUpiApps() {
        List<Integer> result = new ArrayList<>();
        PackageManager packageManager = getPackageManager();
        Uri probeUri = Uri.parse(
                "upi://pay?pa=merchant@upi&pn=Merchant&cu=INR"
        );

        for (int index = 0; index < QR_UPI_APP_PACKAGES.length; index++) {
            Intent probe = new Intent(Intent.ACTION_VIEW, probeUri);
            probe.setPackage(QR_UPI_APP_PACKAGES[index]);
            try {
                if (probe.resolveActivity(packageManager) != null) {
                    result.add(index);
                }
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private void startQrScannerForSelectedApp() {
        if (selectedQrUpiPackage.isEmpty()) {
            restoreExpenseOverlay();
            finishWithoutAnimation();
            return;
        }

        GmsBarcodeScannerOptions options =
                new GmsBarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .enableAutoZoom()
                        .build();
        GmsBarcodeScanner scanner = GmsBarcodeScanning.getClient(this, options);

        scanner.startScan()
                .addOnSuccessListener(barcode -> {
                    String rawValue = barcode.getRawValue() == null
                            ? ""
                            : barcode.getRawValue().trim();

                    UpiQrPayloadParser.Result parsed =
                            UpiQrPayloadParser.parse(rawValue);
                    if (!parsed.isValid()) {
                        Toast.makeText(
                                this,
                                "This QR code does not contain a valid UPI payment ID",
                                Toast.LENGTH_LONG
                        ).show();
                        sendResultToService(
                                FloatingQuickEntryService.ACTION_EXTERNAL_QR_RESULT,
                                rawValue
                        );
                        restoreExpenseOverlay();
                        finishWithoutAnimation();
                        return;
                    }

                    // Fill the same Receiver UPI ID / Name / Amount fields in
                    // the floating form before handing payment to the chosen app.
                    sendResultToService(
                            FloatingQuickEntryService.ACTION_EXTERNAL_QR_RESULT,
                            rawValue
                    );
                    launchScannedPaymentInSelectedApp(rawValue);
                })
                .addOnCanceledListener(() -> {
                    sendResultToService(
                            FloatingQuickEntryService.ACTION_EXTERNAL_QR_RESULT,
                            ""
                    );
                    restoreExpenseOverlay();
                    finishWithoutAnimation();
                })
                .addOnFailureListener(exception -> {
                    Toast.makeText(
                            this,
                            "QR scanner could not start. Check Google Play services and try again.",
                            Toast.LENGTH_LONG
                    ).show();
                    restoreExpenseOverlay();
                    finishWithoutAnimation();
                });
    }

    private void launchScannedPaymentInSelectedApp(String scannedUpiUri) {
        Intent paymentIntent = new Intent(
                Intent.ACTION_VIEW,
                Uri.parse(scannedUpiUri)
        );
        paymentIntent.setPackage(selectedQrUpiPackage);

        try {
            startActivityForResult(paymentIntent, REQUEST_UPI);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(
                    this,
                    selectedQrUpiLabel + " is not installed or cannot handle this UPI QR",
                    Toast.LENGTH_LONG
            ).show();
            restoreExpenseOverlay();
            finishWithoutAnimation();
        } catch (Exception exception) {
            Toast.makeText(
                    this,
                    "Unable to open " + selectedQrUpiLabel,
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
        } catch (ActivityNotFoundException exception) {
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

        if (requestCode == REQUEST_UPI) {
            sendResultToService(
                    FloatingQuickEntryService.ACTION_EXTERNAL_UPI_RESULT,
                    collectUpiResponse(data)
            );
            restoreExpenseOverlay();
            finishWithoutAnimation();
            return;
        }

        if (requestCode == REQUEST_RECEIPT) {
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
    }

    private String collectUpiResponse(@Nullable Intent data) {
        if (data == null) {
            return "";
        }

        StringBuilder response = new StringBuilder();
        if (data.getDataString() != null) {
            response.append(data.getDataString());
        }

        if (data.getExtras() != null) {
            for (String key : data.getExtras().keySet()) {
                Object value = data.getExtras().get(key);
                if (value == null) {
                    continue;
                }
                if (response.length() > 0) {
                    response.append('&');
                }
                response.append(key).append('=').append(value);
            }
        }
        return response.toString();
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
}
