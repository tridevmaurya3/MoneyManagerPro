package com.example.moneymanagerpro.floating;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
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
    private Dialog chooserDialog;

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

    @Override
    protected void onDestroy() {
        if (chooserDialog != null) {
            try {
                chooserDialog.dismiss();
            } catch (Exception ignored) {
            }
            chooserDialog = null;
        }
        super.onDestroy();
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

        showVisibleAppPicker(apps);
    }

    /**
     * Do not use AlertDialog#setItems here. This Activity intentionally uses a
     * transparent helper theme and OEMs can inherit an invisible list text
     * color from that theme. Building the rows explicitly keeps app names and
     * icons readable on every light/dark device theme.
     */
    private void showVisibleAppPicker(List<UpiApp> apps) {
        Dialog dialog = new Dialog(
                this,
                android.R.style.Theme_Material_Light_Dialog_Alert
        );
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(14));
        card.setBackground(roundedBackground(
                Color.parseColor("#FFFDFB"),
                Color.parseColor("#D8E2DC"),
                20
        ));

        TextView title = new TextView(this);
        title.setText("Scan & Pay with UPI App");
        title.setTextColor(Color.parseColor("#18352B"));
        title.setTextSize(19f);
        title.setTypeface(
                title.getTypeface(),
                android.graphics.Typeface.BOLD
        );
        title.setIncludeFontPadding(false);
        card.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Choose an app, then use that app's own QR scanner.");
        subtitle.setTextColor(Color.parseColor("#64746D"));
        subtitle.setTextSize(12f);
        subtitle.setPadding(0, dp(5), 0, dp(10));
        subtitle.setIncludeFontPadding(false);
        card.addView(subtitle);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        scrollView.setVerticalScrollBarEnabled(true);
        scrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        LinearLayout appList = new LinearLayout(this);
        appList.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(
                appList,
                new ScrollView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        int maxVisibleHeight = dp(Math.min(330, 66 * Math.max(1, apps.size())));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                maxVisibleHeight
        );
        card.addView(scrollView, scrollParams);

        PackageManager packageManager = getPackageManager();
        for (int index = 0; index < apps.size(); index++) {
            UpiApp app = apps.get(index);
            appList.addView(createAppRow(packageManager, app));

            if (index < apps.size() - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(Color.parseColor("#E8EEE9"));
                appList.addView(
                        divider,
                        new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                dp(1)
                        )
                );
            }
        }

        TextView cancel = new TextView(this);
        cancel.setText("Cancel");
        cancel.setTextColor(Color.parseColor("#8A2F25"));
        cancel.setTextSize(14f);
        cancel.setTypeface(
                cancel.getTypeface(),
                android.graphics.Typeface.BOLD
        );
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(dp(12), dp(11), dp(12), dp(11));
        cancel.setBackground(roundedBackground(
                Color.parseColor("#FFF4F1"),
                Color.parseColor("#E6BBB3"),
                14
        ));
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cancelParams.topMargin = dp(12);
        card.addView(cancel, cancelParams);

        dialog.setContentView(card);
        dialog.setOnCancelListener(ignored -> {
            restoreExpenseOverlay();
            finishWithoutAnimation();
        });
        cancel.setOnClickListener(view -> {
            dialog.dismiss();
            restoreExpenseOverlay();
            finishWithoutAnimation();
        });

        chooserDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (chooserDialog == dialog) {
                chooserDialog = null;
            }
        });
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.width = Math.min(
                    getResources().getDisplayMetrics().widthPixels - dp(28),
                    dp(430)
            );
            attributes.height = WindowManager.LayoutParams.WRAP_CONTENT;
            attributes.dimAmount = 0.28f;
            window.setAttributes(attributes);
            window.setGravity(Gravity.CENTER);
        }
    }

    private View createAppRow(
            PackageManager packageManager,
            UpiApp app
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setMinimumHeight(dp(60));
        row.setBackground(roundedBackground(
                Color.parseColor("#FFFDFB"),
                Color.TRANSPARENT,
                12
        ));
        row.setContentDescription("Open " + app.label + " for Scan and Pay");

        FrameLayout iconHolder = new FrameLayout(this);
        GradientDrawable iconBackground = new GradientDrawable();
        iconBackground.setColor(Color.parseColor("#F0F7F3"));
        iconBackground.setCornerRadius(dp(12));
        iconHolder.setBackground(iconBackground);

        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setPadding(dp(5), dp(5), dp(5), dp(5));
        Drawable drawable = loadAppIcon(packageManager, app.packageName);
        if (drawable != null) {
            icon.setImageDrawable(drawable);
        }
        FrameLayout.LayoutParams imageParams = new FrameLayout.LayoutParams(
                dp(40),
                dp(40),
                Gravity.CENTER
        );
        iconHolder.addView(icon, imageParams);

        LinearLayout.LayoutParams holderParams = new LinearLayout.LayoutParams(
                dp(46),
                dp(46)
        );
        row.addView(iconHolder, holderParams);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(12), 0, 0, 0);

        TextView name = new TextView(this);
        name.setText(app.label);
        name.setTextColor(Color.parseColor("#1E2D27"));
        name.setTextSize(15f);
        name.setTypeface(name.getTypeface(), android.graphics.Typeface.BOLD);
        name.setIncludeFontPadding(false);
        labels.addView(name);

        TextView hint = new TextView(this);
        hint.setText("Open app • use native Scan & Pay");
        hint.setTextColor(Color.parseColor("#718078"));
        hint.setTextSize(10.5f);
        hint.setPadding(0, dp(3), 0, 0);
        hint.setIncludeFontPadding(false);
        labels.addView(hint);

        row.addView(
                labels,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                )
        );

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextColor(Color.parseColor("#4D6A5D"));
        arrow.setTextSize(28f);
        arrow.setGravity(Gravity.CENTER);
        row.addView(
                arrow,
                new LinearLayout.LayoutParams(dp(28), dp(46))
        );

        row.setOnClickListener(view -> {
            if (chooserDialog != null) {
                try {
                    chooserDialog.dismiss();
                } catch (Exception ignored) {
                }
            }
            launchNativeUpiApp(app);
        });
        return row;
    }

    @Nullable
    private Drawable loadAppIcon(
            PackageManager packageManager,
            String packageName
    ) {
        try {
            return packageManager.getApplicationIcon(packageName);
        } catch (Exception ignored) {
            return null;
        }
    }

    private GradientDrawable roundedBackground(
            int fillColor,
            int strokeColor,
            int radiusDp
    ) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(radiusDp));
        if (Color.alpha(strokeColor) > 0) {
            drawable.setStroke(dp(1), strokeColor);
        }
        return drawable;
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

    private int dp(int value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
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
