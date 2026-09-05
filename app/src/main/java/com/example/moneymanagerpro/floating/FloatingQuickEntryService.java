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
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.moneymanagerpro.activities.DashboardActivity;

public final class FloatingQuickEntryService extends Service {

    private static final String CHANNEL_ID =
            "money_manager_floating_quick_entry";
    private static final int NOTIFICATION_ID = 7402;
    private static final float MIN_BUBBLE_ALPHA = 0.28f;

    private final Handler uiHandler =
            new Handler(Looper.getMainLooper());

    private WindowManager windowManager;
    private View bubbleView;
    private View actionStripView;
    private WindowManager.LayoutParams bubbleParams;
    private WindowManager.LayoutParams actionStripParams;
    private int actionStripWidthPx;
    private FloatingQuickEntryFormOverlay entryForm;

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
        uiHandler.removeCallbacksAndMessages(null);
        hideActionStrip();

        if (entryForm != null) {
            entryForm.dismiss();
            entryForm = null;
        }

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

        FrameLayout bubble = new FrameLayout(this);
        bubble.setContentDescription(
                "Money Manager quick entry"
        );
        bubble.setElevation(dp(9));
        bubble.setPadding(
                dp(7),
                dp(7),
                dp(7),
                dp(7)
        );

        GradientDrawable halo = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.parseColor("#55C9F3D7"),
                        Color.parseColor("#469EE8BC"),
                        Color.parseColor("#338CCBFA")
                }
        );
        halo.setShape(GradientDrawable.OVAL);
        halo.setStroke(
                dp(1),
                Color.parseColor("#8AB8E2C5")
        );
        bubble.setBackground(halo);

        ImageView appIcon = new ImageView(this);
        appIcon.setContentDescription(null);
        appIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        appIcon.setImageDrawable(
                getApplicationInfo().loadIcon(
                        getPackageManager()
                )
        );

        FrameLayout.LayoutParams iconParams =
                new FrameLayout.LayoutParams(
                        dp(48),
                        dp(48),
                        Gravity.CENTER
                );
        bubble.addView(appIcon, iconParams);

        bubble.setAlpha(
                FloatingQuickEntrySettings
                        .getBubbleAlpha(this)
        );

        int size = dp(66);
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
                .getDisplayMetrics().widthPixels - dp(86);
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

        LinearLayout strip = createOverlayPanel();

        strip.addView(
                createAction(
                        "+  Add Income",
                        Color.parseColor("#107C10"),
                        view -> openEntry(false)
                )
        );
        strip.addView(
                createAction(
                        "−  Add Expense",
                        Color.parseColor("#C42B1C"),
                        view -> openEntry(true)
                )
        );
        strip.addView(
                createAction(
                        "◐  Transparency",
                        Color.parseColor("#315F92"),
                        view -> showBubbleTransparencyPanel()
                )
        );
        strip.addView(
                createAction(
                        "×  Close",
                        Color.parseColor("#53645C"),
                        view -> hideActionStrip()
                )
        );

        attachActionStrip(strip, dp(184));
    }

    private void showBubbleTransparencyPanel() {
        hideActionStrip();

        LinearLayout panel = createOverlayPanel();
        panel.setPadding(
                dp(12),
                dp(10),
                dp(12),
                dp(10)
        );

        TextView title = new TextView(this);
        title.setText("Floating button transparency");
        title.setTextColor(Color.parseColor("#28443A"));
        title.setTextSize(12);
        title.setTypeface(
                title.getTypeface(),
                android.graphics.Typeface.BOLD
        );
        panel.addView(title);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(100);

        float currentAlpha =
                FloatingQuickEntrySettings
                        .getBubbleAlpha(this);
        int progress = Math.round(
                ((currentAlpha - MIN_BUBBLE_ALPHA)
                        / (1.0f - MIN_BUBBLE_ALPHA))
                        * 100f
        );
        seekBar.setProgress(
                Math.max(0, Math.min(100, progress))
        );

        LinearLayout.LayoutParams seekParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        seekParams.topMargin = dp(3);
        panel.addView(seekBar, seekParams);

        TextView hint = new TextView(this);
        hint.setText("More transparent  ←  →  More visible");
        hint.setTextColor(Color.parseColor("#687A72"));
        hint.setTextSize(9);
        panel.addView(hint);

        attachActionStrip(panel, dp(224));

        Runnable autoHide = this::hideActionStrip;

        seekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(
                            SeekBar bar,
                            int value,
                            boolean fromUser
                    ) {
                        if (!fromUser) {
                            return;
                        }

                        float alpha = MIN_BUBBLE_ALPHA
                                + (1.0f - MIN_BUBBLE_ALPHA)
                                * (value / 100f);

                        if (bubbleView != null) {
                            bubbleView.setAlpha(alpha);
                        }
                        FloatingQuickEntrySettings
                                .setBubbleAlpha(
                                        FloatingQuickEntryService.this,
                                        alpha
                                );
                        uiHandler.removeCallbacks(autoHide);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar bar) {
                        uiHandler.removeCallbacks(autoHide);
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar bar) {
                        uiHandler.removeCallbacks(autoHide);
                        uiHandler.postDelayed(
                                autoHide,
                                1000L
                        );
                    }
                }
        );
    }

    private LinearLayout createOverlayPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(7), dp(7), dp(7), dp(7));
        panel.setElevation(dp(8));

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.parseColor("#F3F8F5"));
        background.setCornerRadius(dp(18));
        background.setStroke(
                dp(1),
                Color.parseColor("#BCD4C7")
        );
        panel.setBackground(background);
        return panel;
    }

    private void attachActionStrip(
            View panel,
            int width
    ) {
        actionStripWidthPx = width;
        actionStripParams = new WindowManager.LayoutParams(
                width,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        actionStripParams.gravity = Gravity.TOP | Gravity.START;
        actionStripView = panel;
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
        action.setTextSize(13);
        action.setTypeface(
                action.getTypeface(),
                android.graphics.Typeface.BOLD
        );
        action.setGravity(Gravity.CENTER_VERTICAL);
        action.setPadding(
                dp(13),
                dp(9),
                dp(11),
                dp(9)
        );

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.parseColor("#F7FBF8"));
        background.setCornerRadius(dp(12));
        action.setBackground(background);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        params.bottomMargin = dp(4);
        action.setLayoutParams(params);
        action.setOnClickListener(listener);
        return action;
    }

    private void openEntry(boolean expense) {
        hideActionStrip();

        if (entryForm != null) {
            entryForm.dismiss();
        }

        entryForm = new FloatingQuickEntryFormOverlay(
                this,
                expense,
                overlay -> {
                    if (entryForm == overlay) {
                        entryForm = null;
                    }
                }
        );
        entryForm.show();
    }

    private void hideActionStrip() {
        uiHandler.removeCallbacksAndMessages(null);

        if (windowManager != null && actionStripView != null) {
            try {
                windowManager.removeView(actionStripView);
            } catch (Exception ignored) {
            }
        }

        actionStripView = null;
        actionStripParams = null;
        actionStripWidthPx = 0;
    }

    private void updateActionStripPosition() {
        if (actionStripParams == null || bubbleParams == null) {
            return;
        }

        int screenWidth = getResources()
                .getDisplayMetrics().widthPixels;
        int stripWidth = actionStripWidthPx > 0
                ? actionStripWidthPx
                : dp(184);

        if (bubbleParams.x > screenWidth / 2) {
            actionStripParams.x = Math.max(
                    dp(6),
                    bubbleParams.x - stripWidth - dp(8)
            );
        } else {
            actionStripParams.x = bubbleParams.x + dp(74);
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
        int size = dp(66);

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
