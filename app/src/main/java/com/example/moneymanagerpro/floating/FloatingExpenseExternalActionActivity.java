package com.example.moneymanagerpro.floating;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

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
    private static final int REQUEST_QR_UPI_APP = 7413;

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
     * Manual UPI-ID mode. The fully populated upi://pay URI is routed to the
     * Android chooser so the user explicitly selects the payment app.
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
     * QR mode deliberately does NOT launch a scanner when the dropdown option
     * is selected. This method is reached only after the user presses the
     * existing Choose UPI App button. Android then shows the installed UPI-app
     * chooser; the selected app owns the next step (including its QR action).
     */
    private void openQrModeUpiAppChooser() {
        FloatingOverlayUiState.hideExpenseForExternalAction();

        Intent upiAppIntent = new Intent(
                Intent.ACTION_VIEW,
                Uri.parse("upi://pay")
        );
        Intent chooser = Intent.createChooser(
                upiAppIntent,
                "Choose UPI App"
        );

        try {
            startActivityForResult(chooser, REQUEST_QR_UPI_APP);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(
                    this,
                    "No UPI payment app is available",
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

        if (requestCode == REQUEST_QR_UPI_APP) {
            // QR mode has no automatic scanner/result parsing here. Returning
            // from the selected UPI app simply restores the same overlay state.
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
