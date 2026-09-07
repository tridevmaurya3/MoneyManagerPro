package com.example.moneymanagerpro.floating;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Compact in-place UPI app picker used by both expense forms. */
final class NativeUpiAppPopup {

    private static final String[] FALLBACK_UPI_PACKAGES = {
            "com.google.android.apps.nbu.paisa.user",
            "com.phonepe.app",
            "net.one97.paytm",
            "in.org.npci.upiapp",
            "in.amazon.mShop.android.shopping",
            "com.dreamplug.androidapp"
    };

    private NativeUpiAppPopup() {
    }

    static void show(Context context, View anchor) {
        if (context == null || anchor == null) {
            return;
        }

        List<UpiApp> apps = detectApps(context);
        if (apps.isEmpty()) {
            Toast.makeText(context, "No compatible UPI app is available", Toast.LENGTH_LONG).show();
            return;
        }

        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(context, 4), dp(context, 4), dp(context, 4), dp(context, 4));

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setScrollbarFadingEnabled(false);
        scroll.setBackground(rounded(context, "#FBFFFC", "#B9D1C4", 12));
        scroll.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int popupWidth = Math.min(
                screenWidth - dp(context, 24),
                Math.max(anchor.getWidth(), dp(context, 228))
        );
        int popupHeight = Math.min(
                dp(context, 230),
                Math.max(dp(context, 48), apps.size() * dp(context, 44) + dp(context, 8))
        );

        PopupWindow popup = new PopupWindow(scroll, popupWidth, popupHeight, true);
        popup.setOutsideTouchable(true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(dp(context, 10));
        popup.setClippingEnabled(true);

        PackageManager packageManager = context.getPackageManager();
        for (UpiApp app : apps) {
            list.addView(createRow(context, packageManager, app, popup));
        }

        popup.showAsDropDown(anchor, 0, dp(context, 3), Gravity.END);
    }

    private static View createRow(
            Context context,
            PackageManager packageManager,
            UpiApp app,
            PopupWindow popup
    ) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 7), dp(context, 3), dp(context, 7), dp(context, 3));
        row.setBackground(rounded(context, "#FBFFFC", "#E6EEE9", 9));

        FrameLayout iconHolder = new FrameLayout(context);
        iconHolder.setBackground(rounded(context, "#F0F7F3", "#DDEAE3", 8));
        ImageView icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setPadding(dp(context, 2), dp(context, 2), dp(context, 2), dp(context, 2));
        try {
            Drawable drawable = packageManager.getApplicationIcon(app.packageName);
            icon.setImageDrawable(drawable);
        } catch (Exception ignored) {
        }
        iconHolder.addView(icon, new FrameLayout.LayoutParams(
                dp(context, 27), dp(context, 27), Gravity.CENTER
        ));
        row.addView(iconHolder, new LinearLayout.LayoutParams(
                dp(context, 31), dp(context, 31)
        ));

        TextView name = new TextView(context);
        name.setText(app.label);
        name.setTextColor(Color.parseColor("#1F3028"));
        name.setTextSize(11.5f);
        name.setGravity(Gravity.CENTER_VERTICAL);
        name.setSingleLine(true);
        name.setPadding(dp(context, 8), 0, dp(context, 4), 0);
        row.addView(name, new LinearLayout.LayoutParams(
                0, dp(context, 34), 1f
        ));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 40)
        );
        params.bottomMargin = dp(context, 2);
        row.setLayoutParams(params);

        row.setOnClickListener(view -> {
            popup.dismiss();
            launchApp(context, app);
        });
        return row;
    }

    private static void launchApp(Context context, UpiApp app) {
        if (context instanceof Service) {
            Intent bridge = new Intent(
                    context,
                    FloatingExpenseExternalActionActivity.class
            );
            bridge.setAction(
                    FloatingExpenseExternalActionActivity.ACTION_NATIVE_APP
            );
            bridge.putExtra(
                    FloatingExpenseExternalActionActivity.EXTRA_NATIVE_PACKAGE,
                    app.packageName
            );
            bridge.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_NO_ANIMATION
                            | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            );
            try {
                context.startActivity(bridge);
            } catch (Exception exception) {
                Toast.makeText(
                        context,
                        "Unable to open " + app.label,
                        Toast.LENGTH_LONG
                ).show();
            }
            return;
        }

        Intent launchIntent;
        try {
            launchIntent = context.getPackageManager().getLaunchIntentForPackage(app.packageName);
        } catch (Exception exception) {
            launchIntent = null;
        }
        if (launchIntent == null) {
            Toast.makeText(context, app.label + " cannot be opened", Toast.LENGTH_LONG).show();
            return;
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (!(context instanceof Activity)) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        try {
            context.startActivity(launchIntent);
        } catch (Exception exception) {
            Toast.makeText(context, "Unable to open " + app.label, Toast.LENGTH_LONG).show();
        }
    }

    private static List<UpiApp> detectApps(Context context) {
        PackageManager packageManager = context.getPackageManager();
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
                addPackage(packageManager, discovered, info.activityInfo.packageName,
                        info.loadLabel(packageManager));
            }
        } catch (Exception ignored) {
        }

        for (String packageName : FALLBACK_UPI_PACKAGES) {
            addPackage(packageManager, discovered, packageName, null);
        }

        List<UpiApp> result = new ArrayList<>(discovered.values());
        Collections.sort(result, (left, right) -> left.label.toLowerCase(Locale.US)
                .compareTo(right.label.toLowerCase(Locale.US)));
        return result;
    }

    private static void addPackage(
            PackageManager packageManager,
            Map<String, UpiApp> discovered,
            String packageName,
            CharSequence knownLabel
    ) {
        if (packageName == null || packageName.trim().isEmpty()
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
        String label = knownLabel == null ? "" : knownLabel.toString().trim();
        if (label.isEmpty()) {
            try {
                label = packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(packageName, 0)
                ).toString().trim();
            } catch (Exception ignored) {
                label = packageName;
            }
        }
        discovered.put(packageName, new UpiApp(packageName, label));
    }

    private static GradientDrawable rounded(
            Context context,
            String fill,
            String stroke,
            int radiusDp
    ) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor(fill));
        drawable.setCornerRadius(dp(context, radiusDp));
        drawable.setStroke(dp(context, 1), Color.parseColor(stroke));
        return drawable;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
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
