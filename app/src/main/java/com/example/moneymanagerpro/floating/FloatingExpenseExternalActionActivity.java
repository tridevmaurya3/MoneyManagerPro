package com.example.moneymanagerpro.floating;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

/**
 * Invisible bridge used only when the true WindowManager expense overlay needs
 * an Android Activity result (UPI chooser, QR scanner or bill image picker).
 * It never opens the Money Manager dashboard and immediately finishes after
 * delivering the external result back to FloatingQuickEntryService.
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
            openQrScanner();
        } else if (ACTION_RECEIPT.equals(action)) {
            openReceiptPicker();
        } else {
            finishWithoutAnimation();
        }
    }

    private void openUpiChooser() {
        String uriText = getIntent().getStringExtra(EXTRA_PAYMENT_URI);
        if (uriText == null || uriText.trim().isEmpty()) {
            finishWithoutAnimation();
            return;
        }

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
            finishWithoutAnimation();
        }
    }

    private void openQrScanner() {
        GmsBarcodeScannerOptions options =
                new GmsBarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .enableAutoZoom()
                        .build();
        GmsBarcodeScanner scanner =
                GmsBarcodeScanning.getClient(this, options);

        scanner.startScan()
                .addOnSuccessListener(barcode -> {
                    sendResultToService(
                            FloatingQuickEntryService.ACTION_EXTERNAL_QR_RESULT,
                            barcode.getRawValue() == null
                                    ? ""
                                    : barcode.getRawValue()
                    );
                    finishWithoutAnimation();
                })
                .addOnCanceledListener(() -> {
                    sendResultToService(
                            FloatingQuickEntryService.ACTION_EXTERNAL_QR_RESULT,
                            ""
                    );
                    finishWithoutAnimation();
                })
                .addOnFailureListener(exception -> {
                    Toast.makeText(
                            this,
                            "QR scanner could not start. Check Google Play services and try again.",
                            Toast.LENGTH_LONG
                    ).show();
                    sendResultToService(
                            FloatingQuickEntryService.ACTION_EXTERNAL_QR_RESULT,
                            ""
                    );
                    finishWithoutAnimation();
                });
    }

    private void openReceiptPicker() {
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

    private void finishWithoutAnimation() {
        finish();
        overridePendingTransition(0, 0);
    }
}
