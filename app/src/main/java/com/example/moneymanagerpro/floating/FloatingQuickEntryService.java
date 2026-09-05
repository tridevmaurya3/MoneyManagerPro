package com.example.moneymanagerpro.floating;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.moneymanagerpro.activities.DashboardActivity;

public final class FloatingQuickEntryService extends Service {

    private static final String CHANNEL_ID =
            "money_manager_floating_quick_entry";
    private static final int NOTIFICATION_ID = 7402;

    private WindowManager windowManager;
    private View bubbleView;
    private View actionStripView;
    private WindowManager.LayoutParams bubbleParams;
    private WindowManager.LayoutParams actionStripParams;

    @Override
    public void onCreate() {
        super.onCreate();

        if (!FloatingQuickEntrySettings.isEnabled(this)
                || !Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        createNotificationChannel();
        startForeground(
                NOTIFICATION_ID,
                buildNotification()
        );

        windowManager = (WindowManager)
                getSystemService(WINDOW_SERVICE);
        showBubble();
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId
    ) {
        if (!FloatingQuickEntrySettings.isEnabled(this)
                || !Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (bubbleView == null && windowManager != null) {
            showBubble();
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        hideActionStrip();

        if (windowManager != null && bubbleView != null) {
            try {
                windowManager.removeView(bubbleView);
            } catch (Exception ignored) {
            }
        }

        bubbleView = null;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void showBubble() {
        if (windowManager == null || bubbleView != null) {
            return;
        }

        TextView bubble = new TextView(this);
        bubble.setText("₹+");
        bubble.setGravity(Gravity.CENTER);
        bubble.setTextSize(22);
        bubble.setTextColor(Color.WHITE);
        bubble.setTypeface(
                bubble.getTypeface(),
                android.graphics.Typeface.BOLD
        );
        bubble.setContentDescription(
                "Money Manager quick entry"
        );
        bubble.setElevation(dp(8));

        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.parseColor("#0F7A32"),
                        Color.parseColor("#159447"),
                        Color.parseColor("#0F6CBD")
                }
        );
        background.setShape(GradientDrawable.OVAL);
        background.setStroke(
                dp(2),
                Color.parseColor("#BDE8CB")
        );
        bubble.setBackground(background);

        int size = dp(64);
        bubbleParams = new WindowManager.LayoutParams(
                size,
                size,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = getResources()
                .getDisplayMetrics().widthPixels - dp(84);
        bubbleParams.y = Math.round(
                getResources()
                        .getDisplayMetrics()
                        .heightPixels * 0.56f
        );

        bubble.setOnTouchListener(new View.OnTouchListener() {
            private int startX;
            private int startY;
            private float downX;
            private float downY;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    startX = bubbleParams.x;
                    startY = bubbleParams.y;
                    downX = event.getRawX();
                    downY = event.getRawY();
                    return true;
                }

                if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    bubbleParams.x = startX
                            + Math.round(event.getRawX() - downX);
                    bubbleParams.y = startY
                            + Math.round(event.getRawY() - downY);
                    clampBubblePosition();
                    windowManager.updateViewLayout(
                            bubbleView,
                            bubbleParams
                    );
                    updateActionStripPosition();
                    return true;
                }

                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    float dx = Math.abs(event.getRawX() - downX);
                    float dy = Math.abs(event.getRawY() - downY);

                    if (dx < dp(8) && dy < dp(8)) {
                        toggleActionStrip();
                    }
                    return true;
                }

                return false;
            }
        });

        bubbleView = bubble;
        windowManager.addView(
                bubbleView,
                bubbleParams
        );
    }

    private void toggleActionStrip() {
        if (actionStripView != null) {
            hideActionStrip();
            return;
        }

        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.VERTICAL);
        strip.setPadding(dp(8), dp(8), dp(8), dp(8));
        strip.setElevation(dp(8));

        GradientDrawable stripBackground = new GradientDrawable();
        stripBackground.setColor(Color.parseColor("#F7FFFFFF"));
        stripBackground.setCornerRadius(dp(18));
        stripBackground.setStroke(
                dp(1),
                Color.parseColor("#D2DED7")
        );
        strip.setBackground(stripBackground);

        strip.addView(
                createAction(
                        "+  Add Income",
                        Color.parseColor("#107C10"),
                        view -> openEntry(
                                FloatingAddIncomeActivity.class
                        )
                )
        );
        strip.addView(
                createAction(
                        "−  Add Expense",
                        Color.parseColor("#C42B1C"),
                        view -> openEntry(
                                FloatingAddExpenseActivity.class
                        )
                )
        );
        strip.addView(
                createAction(
                        "×  Close",
                        Color.parseColor("#53645C"),
                        view -> hideActionStrip()
                )
        );

        actionStripParams = new WindowManager.LayoutParams(
                dp(176),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        actionStripParams.gravity = Gravity.TOP | Gravity.START;
        actionStripView = strip;
        updateActionStripPosition();
        windowManager.addView(
                actionStripView,
                actionStripParams
        );
    }

    private TextView createAction(
            String text,
            int textColor,
            View.OnClickListener listener
    ) {
        TextView action = new TextView(this);
        action.setText(text);
        action.setTextColor(textColor);
        action.setTextSize(14);
        action.setTypeface(
                action.getTypeface(),
                android.graphics.Typeface.BOLD
        );
        action.setGravity(Gravity.CENTER_VERTICAL);
        action.setPadding(
                dp(14),
                dp(11),
                dp(12),
                dp(11)
        );

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.parseColor("#F5FAF7"));
        background.setCornerRadius(dp(13));
        action.setBackground(background);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        params.bottomMargin = dp(5);
        action.setLayoutParams(params);
        action.setOnClickListener(listener);
        return action;
    }

    private void openEntry(Class<?> activityClass) {
        hideActionStrip();

        Intent intent = new Intent(this, activityClass);
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        );
        startActivity(intent);
    }

    private void hideActionStrip() {
        if (windowManager != null && actionStripView != null) {
            try {
                windowManager.removeView(actionStripView);
            } catch (Exception ignored) {
            }
        }

        actionStripView = null;
        actionStripParams = null;
    }

    private void updateActionStripPosition() {
        if (actionStripParams == null || bubbleParams == null) {
            return;
        }

        int screenWidth = getResources()
                .getDisplayMetrics().widthPixels;
        int stripWidth = dp(176);

        if (bubbleParams.x > screenWidth / 2) {
            actionStripParams.x = Math.max(
                    dp(6),
                    bubbleParams.x - stripWidth - dp(8)
            );
        } else {
            actionStripParams.x = bubbleParams.x + dp(72);
        }

        actionStripParams.y = Math.max(
                dp(8),
                bubbleParams.y - dp(18)
        );

        if (windowManager != null && actionStripView != null
                && actionStripView.isAttachedToWindow()) {
            windowManager.updateViewLayout(
                    actionStripView,
                    actionStripParams
            );
        }
    }

    private void clampBubblePosition() {
        int screenWidth = getResources()
                .getDisplayMetrics().widthPixels;
        int screenHeight = getResources()
                .getDisplayMetrics().heightPixels;
        int size = dp(64);

        bubbleParams.x = Math.max(
                0,
                Math.min(screenWidth - size, bubbleParams.x)
        );
        bubbleParams.y = Math.max(
                dp(24),
                Math.min(screenHeight - size - dp(32), bubbleParams.y)
        );
    }

    private int overlayType() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private Notification buildNotification() {
        Intent dashboardIntent = new Intent(
                this,
                DashboardActivity.class
        );
        dashboardIntent.addFlags(
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                1,
                dashboardIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(
                this,
                CHANNEL_ID
        )
                .setSmallIcon(android.R.drawable.ic_menu_add)
                .setContentTitle("Money Manager floating entry")
                .setContentText("Quick Add Income / Expense is active")
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager manager =
                (NotificationManager) getSystemService(
                        Context.NOTIFICATION_SERVICE
                );

        if (manager == null) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Floating quick entry",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(
                "Keeps the Money Manager floating quick-entry button active"
        );
        manager.createNotificationChannel(channel);
    }

    private int dp(int value) {
        return Math.round(
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }
}
