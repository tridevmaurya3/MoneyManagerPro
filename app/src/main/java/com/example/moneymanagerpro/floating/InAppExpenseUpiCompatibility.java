package com.example.moneymanagerpro.floating;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.activities.AddExpenseActivity;
import com.example.moneymanagerpro.utils.UpiQrPayloadParser;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Compatibility layer for the normal in-app Add Expense screen.
 *
 * The original screen used to scan a UPI QR, copy only payee/name/amount into
 * the form, and later create a brand-new upi://pay URI. Merchant QR parameters
 * such as mc, tr, mode, orgid, url or signature-related fields could therefore
 * be lost. This controller keeps the original scanned UPI URI intact and uses
 * it for the payment intent while leaving the Activity's existing result
 * parser, save logic and database flow unchanged.
 */
final class InAppExpenseUpiCompatibility {

    private static final Map<Activity, Controller> CONTROLLERS =
            new WeakHashMap<>();
    private static boolean registered;

    private InAppExpenseUpiCompatibility() {
    }

    static synchronized void register(Application application) {
        if (registered || application == null) {
            return;
        }
        registered = true;

        application.registerActivityLifecycleCallbacks(
                new Application.ActivityLifecycleCallbacks() {
                    @Override
                    public void onActivityResumed(@NonNull Activity activity) {
                        if (!(activity instanceof AddExpenseActivity)) {
                            return;
                        }
                        Controller controller = CONTROLLERS.get(activity);
                        if (controller == null) {
                            controller = new Controller((AddExpenseActivity) activity);
                            CONTROLLERS.put(activity, controller);
                        }
                        controller.attach();
                    }

                    @Override
                    public void onActivityDestroyed(@NonNull Activity activity) {
                        CONTROLLERS.remove(activity);
                    }

                    @Override public void onActivityCreated(@NonNull Activity a, @Nullable Bundle b) {}
                    @Override public void onActivityStarted(@NonNull Activity a) {}
                    @Override public void onActivityPaused(@NonNull Activity a) {}
                    @Override public void onActivityStopped(@NonNull Activity a) {}
                    @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle b) {}
                }
        );
    }

    private static final class Controller {
        private final AddExpenseActivity activity;
        private MaterialAutoCompleteTextView modeDropdown;
        private TextInputEditText amountField;
        private TextInputEditText upiIdField;
        private TextInputEditText upiNameField;
        private android.view.View payButton;
        private String scannedUpiUri = "";
        private boolean attached;

        Controller(AddExpenseActivity activity) {
            this.activity = activity;
        }

        void attach() {
            if (attached || activity.isFinishing()) {
                return;
            }

            modeDropdown = activity.findViewById(R.id.dropdownUpiEntryMode);
            amountField = activity.findViewById(R.id.etAmount);
            upiIdField = activity.findViewById(R.id.etUpiPayeeId);
            upiNameField = activity.findViewById(R.id.etUpiPayeeName);
            payButton = activity.findViewById(R.id.btnPayWithUpi);

            if (modeDropdown == null
                    || amountField == null
                    || upiIdField == null
                    || upiNameField == null
                    || payButton == null) {
                return;
            }

            attached = true;

            modeDropdown.setOnItemClickListener((parent, view, position, id) -> {
                if (position == 1) {
                    startQrScan();
                } else {
                    scannedUpiUri = "";
                }
            });

            payButton.setOnClickListener(view -> {
                if (!scannedUpiUri.isEmpty()) {
                    launchPreservedQrPayment();
                } else {
                    invokeNoArg(activity, "launchUpiPayment");
                }
            });
        }

        private void startQrScan() {
            GmsBarcodeScannerOptions options =
                    new GmsBarcodeScannerOptions.Builder()
                            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                            .enableAutoZoom()
                            .build();
            GmsBarcodeScanner scanner = GmsBarcodeScanning.getClient(activity, options);

            scanner.startScan()
                    .addOnSuccessListener(barcode -> {
                        String raw = barcode.getRawValue() == null
                                ? ""
                                : barcode.getRawValue().trim();
                        UpiQrPayloadParser.Result parsed = UpiQrPayloadParser.parse(raw);

                        if (!parsed.isValid()) {
                            scannedUpiUri = "";
                            modeDropdown.setText("Enter UPI ID", false);
                            Toast.makeText(
                                    activity,
                                    "This QR code does not contain a valid UPI payment ID",
                                    Toast.LENGTH_LONG
                            ).show();
                            return;
                        }

                        scannedUpiUri = raw;
                        upiIdField.setText(parsed.getPayeeId());
                        upiNameField.setText(parsed.getPayeeName());

                        if (text(amountField).isEmpty()
                                && positive(parsed.getAmount()) != null) {
                            amountField.setText(parsed.getAmount());
                        }

                        modeDropdown.setText("UPI QR Scanned", false);
                        Toast.makeText(
                                activity,
                                "UPI details filled from QR code",
                                Toast.LENGTH_SHORT
                        ).show();
                    })
                    .addOnCanceledListener(() -> {
                        scannedUpiUri = "";
                        modeDropdown.setText("Enter UPI ID", false);
                    })
                    .addOnFailureListener(exception -> {
                        scannedUpiUri = "";
                        modeDropdown.setText("Enter UPI ID", false);
                        Toast.makeText(
                                activity,
                                "QR scanner could not start. Check Google Play services and try again.",
                                Toast.LENGTH_LONG
                        ).show();
                    });
        }

        @SuppressWarnings("unchecked")
        private void launchPreservedQrPayment() {
            Uri paymentUri = preserveQrAndFillMissingAmount(
                    scannedUpiUri,
                    text(amountField)
            );
            if (paymentUri == null) {
                scannedUpiUri = "";
                invokeNoArg(activity, "launchUpiPayment");
                return;
            }

            try {
                Object launcherObject = readField(activity, "upiPaymentLauncher");
                if (!(launcherObject instanceof ActivityResultLauncher)) {
                    scannedUpiUri = "";
                    invokeNoArg(activity, "launchUpiPayment");
                    return;
                }

                Intent paymentIntent = new Intent(Intent.ACTION_VIEW, paymentUri);
                Intent chooser = Intent.createChooser(
                        paymentIntent,
                        "Choose UPI / Payment App"
                );
                ((ActivityResultLauncher<Intent>) launcherObject).launch(chooser);
            } catch (Exception exception) {
                Toast.makeText(
                        activity,
                        "Unable to open a UPI payment app",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    /**
     * Preserve every parameter that came from the scanned QR. For a static QR
     * that contains no amount, add only the user-entered amount and INR currency.
     * Never replace merchant code/reference/mode/signature-related parameters.
     */
    static Uri preserveQrAndFillMissingAmount(String rawUri, String amountText) {
        try {
            Uri original = Uri.parse(rawUri == null ? "" : rawUri.trim());
            if (!"upi".equalsIgnoreCase(original.getScheme())
                    || !"pay".equalsIgnoreCase(original.getAuthority())) {
                return null;
            }

            Uri.Builder builder = original.buildUpon();
            String existingAmount = original.getQueryParameter("am");
            String currency = original.getQueryParameter("cu");

            if ((existingAmount == null || existingAmount.trim().isEmpty())
                    && positive(amountText) != null) {
                builder.appendQueryParameter("am", amountText.trim());
            }
            if (currency == null || currency.trim().isEmpty()) {
                builder.appendQueryParameter("cu", "INR");
            }
            return builder.build();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String text(TextInputEditText field) {
        return field == null || field.getText() == null
                ? ""
                : field.getText().toString().trim();
    }

    private static BigDecimal positive(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            BigDecimal amount = new BigDecimal(value.trim());
            return amount.compareTo(BigDecimal.ZERO) > 0 ? amount : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Object readField(Object target, String name) {
        Class<?> type = target == null ? null : target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (Exception ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private static Object invokeNoArg(Object target, String name) {
        Class<?> type = target == null ? null : target.getClass();
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod(name);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (Exception ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }
}
