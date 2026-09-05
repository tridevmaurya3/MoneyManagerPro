package com.example.moneymanagerpro.floating;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * Applies only to the floating subclasses. The original income/expense
 * activities and their database/save behavior remain untouched.
 */
final class FloatingEntryWindow {

    private static final float MIN_ALPHA = 0.38f;
    private static final float MAX_ALPHA = 1.0f;

    private FloatingEntryWindow() {
    }

    static void apply(Activity activity) {
        Window window = activity.getWindow();

        if (window == null) {
            return;
        }

        window.setBackgroundDrawable(
                new ColorDrawable(Color.TRANSPARENT)
        );
        window.addFlags(
                WindowManager.LayoutParams.FLAG_DIM_BEHIND
        );

        int screenWidth = activity
                .getResources()
                .getDisplayMetrics()
                .widthPixels;
        int screenHeight = activity
                .getResources()
                .getDisplayMetrics()
                .heightPixels;

        WindowManager.LayoutParams attributes =
                window.getAttributes();
        attributes.width = Math.min(
                screenWidth - dp(activity, 20),
                Math.round(screenWidth * 0.94f)
        );
        attributes.height = Math.min(
                screenHeight - dp(activity, 34),
                Math.round(screenHeight * 0.86f)
        );
        attributes.gravity = Gravity.CENTER;
        attributes.dimAmount = 0.18f;
        attributes.alpha = MAX_ALPHA;
        window.setAttributes(attributes);

        activity.setFinishOnTouchOutside(true);

        ViewGroup content = activity.findViewById(
                android.R.id.content
        );

        if (content == null) {
            return;
        }

        View formRoot = content.getChildCount() > 0
                ? content.getChildAt(0)
                : null;

        if (formRoot != null) {
            GradientDrawable formBackground =
                    new GradientDrawable(
                            GradientDrawable.Orientation.TOP_BOTTOM,
                            new int[]{
                                    Color.parseColor("#EAF8EE"),
                                    Color.parseColor("#FFF1F1"),
                                    Color.parseColor("#EEF6FF")
                            }
                    );
            formBackground.setCornerRadius(
                    dp(activity, 24)
            );
            formBackground.setStroke(
                    dp(activity, 1),
                    Color.parseColor("#C9D8D0")
            );
            formRoot.setBackground(formBackground);
            formRoot.setClipToOutline(true);
        }

        addCloseControl(activity, content);
        addTransparencyControl(activity, content);
        addResizeHandles(activity, content);
        addMoveHandle(activity, content);
    }

    private static void addCloseControl(
            Activity activity,
            ViewGroup content
    ) {
        TextView close = createFloatingControl(
                activity,
                "×",
                "Close floating form"
        );

        close.setOnClickListener(view -> activity.finish());

        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(
                        dp(activity, 38),
                        dp(activity, 38),
                        Gravity.TOP | Gravity.END
                );
        params.setMargins(
                0,
                dp(activity, 8),
                dp(activity, 8),
                0
        );
        content.addView(close, params);
    }

    private static void addTransparencyControl(
            Activity activity,
            ViewGroup content
    ) {
        TextView transparency = createFloatingControl(
                activity,
                "◐",
                "Transparency"
        );

        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(
                        dp(activity, 38),
                        dp(activity, 38),
                        Gravity.TOP | Gravity.END
                );
        params.setMargins(
                0,
                dp(activity, 8),
                dp(activity, 52),
                0
        );
        content.addView(transparency, params);

        transparency.setOnClickListener(
                view -> showTransparencyPopup(
                        activity,
                        transparency
                )
        );
    }

    private static TextView createFloatingControl(
            Activity activity,
            String text,
            String contentDescription
    ) {
        TextView control = new TextView(activity);
        control.setText(text);
        control.setContentDescription(contentDescription);
        control.setGravity(Gravity.CENTER);
        control.setTextColor(Color.parseColor("#28443A"));
        control.setTextSize(20);
        control.setElevation(dp(activity, 5));

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(Color.parseColor("#E6F7EE"));
        background.setStroke(
                dp(activity, 1),
                Color.parseColor("#A8CFB7")
        );
        control.setBackground(background);
        return control;
    }

    private static void showTransparencyPopup(
            Activity activity,
            View anchor
    ) {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(
                dp(activity, 14),
                dp(activity, 12),
                dp(activity, 14),
                dp(activity, 12)
        );

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.parseColor("#E6222A2F"));
        background.setCornerRadius(dp(activity, 16));
        panel.setBackground(background);

        TextView title = new TextView(activity);
        title.setText("Transparency");
        title.setTextColor(Color.WHITE);
        title.setTextSize(13);
        title.setTypeface(
                title.getTypeface(),
                android.graphics.Typeface.BOLD
        );
        panel.addView(title);

        SeekBar seekBar = new SeekBar(activity);
        seekBar.setMax(100);

        Window window = activity.getWindow();
        float currentAlpha = window == null
                ? MAX_ALPHA
                : window.getAttributes().alpha;
        int progress = Math.round(
                ((currentAlpha - MIN_ALPHA)
                        / (MAX_ALPHA - MIN_ALPHA))
                        * 100f
        );
        seekBar.setProgress(
                Math.max(0, Math.min(100, progress))
        );

        LinearLayout.LayoutParams seekParams =
                new LinearLayout.LayoutParams(
                        dp(activity, 210),
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        seekParams.topMargin = dp(activity, 5);
        panel.addView(seekBar, seekParams);

        TextView hint = new TextView(activity);
        hint.setText("Less visible  ←  →  More visible");
        hint.setTextColor(Color.parseColor("#E4EAED"));
        hint.setTextSize(10);
        panel.addView(hint);

        PopupWindow popup = new PopupWindow(
                panel,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        popup.setOutsideTouchable(true);
        popup.setBackgroundDrawable(
                new ColorDrawable(Color.TRANSPARENT)
        );
        popup.setElevation(dp(activity, 10));

        Handler handler = new Handler(Looper.getMainLooper());
        Runnable dismissPopup = () -> {
            if (popup.isShowing()) {
                popup.dismiss();
            }
        };

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

                        Window activeWindow = activity.getWindow();

                        if (activeWindow == null) {
                            return;
                        }

                        float alpha = MIN_ALPHA
                                + (MAX_ALPHA - MIN_ALPHA)
                                * (value / 100f);
                        WindowManager.LayoutParams attrs =
                                activeWindow.getAttributes();
                        attrs.alpha = alpha;
                        activeWindow.setAttributes(attrs);
                        handler.removeCallbacks(dismissPopup);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar bar) {
                        handler.removeCallbacks(dismissPopup);
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar bar) {
                        handler.removeCallbacks(dismissPopup);
                        handler.postDelayed(
                                dismissPopup,
                                1200L
                        );
                    }
                }
        );

        popup.setOnDismissListener(
                () -> handler.removeCallbacks(dismissPopup)
        );

        popup.showAsDropDown(
                anchor,
                -dp(activity, 210),
                dp(activity, 4)
        );
    }

    private static void addResizeHandles(
            Activity activity,
            ViewGroup content
    ) {
        addResizeHandle(
                activity,
                content,
                Gravity.TOP | Gravity.START,
                -1,
                -1,
                "↖"
        );
        addResizeHandle(
                activity,
                content,
                Gravity.TOP | Gravity.END,
                1,
                -1,
                "↗"
        );
        addResizeHandle(
                activity,
                content,
                Gravity.BOTTOM | Gravity.START,
                -1,
                1,
                "↙"
        );
        addResizeHandle(
                activity,
                content,
                Gravity.BOTTOM | Gravity.END,
                1,
                1,
                "↘"
        );
    }

    private static void addResizeHandle(
            Activity activity,
            ViewGroup content,
            int gravity,
            int horizontalDirection,
            int verticalDirection,
            String symbol
    ) {
        TextView handle = new TextView(activity);
        handle.setText(symbol);
        handle.setContentDescription("Resize floating form");
        handle.setGravity(Gravity.CENTER);
        handle.setTextColor(Color.parseColor("#36584B"));
        handle.setTextSize(15);

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.parseColor("#DDF7EFEA"));
        background.setCornerRadius(dp(activity, 8));
        background.setStroke(
                dp(activity, 1),
                Color.parseColor("#AFC8BE")
        );
        handle.setBackground(background);

        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(
                        dp(activity, 28),
                        dp(activity, 28),
                        gravity
                );
        content.addView(handle, params);

        handle.setOnTouchListener(
                new ResizeTouchListener(
                        activity,
                        horizontalDirection,
                        verticalDirection
                )
        );
    }

    private static void addMoveHandle(
            Activity activity,
            ViewGroup content
    ) {
        TextView handle = new TextView(activity);
        handle.setText("—");
        handle.setContentDescription("Move floating form");
        handle.setGravity(Gravity.CENTER);
        handle.setTextColor(Color.parseColor("#628077"));
        handle.setTextSize(22);

        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(
                        dp(activity, 64),
                        dp(activity, 28),
                        Gravity.TOP | Gravity.CENTER_HORIZONTAL
                );
        content.addView(handle, params);

        handle.setOnTouchListener(
                new MoveTouchListener(activity)
        );
    }

    private static final class ResizeTouchListener
            implements View.OnTouchListener {

        private final Activity activity;
        private final int horizontalDirection;
        private final int verticalDirection;

        private float startRawX;
        private float startRawY;
        private int startWidth;
        private int startHeight;

        ResizeTouchListener(
                Activity activity,
                int horizontalDirection,
                int verticalDirection
        ) {
            this.activity = activity;
            this.horizontalDirection = horizontalDirection;
            this.verticalDirection = verticalDirection;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            Window window = activity.getWindow();

            if (window == null) {
                return false;
            }

            WindowManager.LayoutParams attrs =
                    window.getAttributes();

            if (event.getActionMasked()
                    == MotionEvent.ACTION_DOWN) {
                startRawX = event.getRawX();
                startRawY = event.getRawY();
                startWidth = attrs.width;
                startHeight = attrs.height;
                return true;
            }

            if (event.getActionMasked()
                    == MotionEvent.ACTION_MOVE) {
                int deltaX = Math.round(
                        event.getRawX() - startRawX
                );
                int deltaY = Math.round(
                        event.getRawY() - startRawY
                );

                int screenWidth = activity
                        .getResources()
                        .getDisplayMetrics()
                        .widthPixels;
                int screenHeight = activity
                        .getResources()
                        .getDisplayMetrics()
                        .heightPixels;

                int newWidth = startWidth
                        + horizontalDirection * deltaX;
                int newHeight = startHeight
                        + verticalDirection * deltaY;

                attrs.width = clamp(
                        newWidth,
                        dp(activity, 300),
                        screenWidth - dp(activity, 12)
                );
                attrs.height = clamp(
                        newHeight,
                        dp(activity, 390),
                        screenHeight - dp(activity, 22)
                );
                window.setAttributes(attrs);
                return true;
            }

            return event.getActionMasked()
                    == MotionEvent.ACTION_UP
                    || event.getActionMasked()
                    == MotionEvent.ACTION_CANCEL;
        }
    }

    private static final class MoveTouchListener
            implements View.OnTouchListener {

        private final Activity activity;
        private float startRawX;
        private float startRawY;
        private int startX;
        private int startY;

        MoveTouchListener(Activity activity) {
            this.activity = activity;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            Window window = activity.getWindow();

            if (window == null) {
                return false;
            }

            WindowManager.LayoutParams attrs =
                    window.getAttributes();

            if (event.getActionMasked()
                    == MotionEvent.ACTION_DOWN) {
                startRawX = event.getRawX();
                startRawY = event.getRawY();
                startX = attrs.x;
                startY = attrs.y;
                return true;
            }

            if (event.getActionMasked()
                    == MotionEvent.ACTION_MOVE) {
                attrs.x = startX + Math.round(
                        event.getRawX() - startRawX
                );
                attrs.y = startY + Math.round(
                        event.getRawY() - startRawY
                );
                window.setAttributes(attrs);
                return true;
            }

            return event.getActionMasked()
                    == MotionEvent.ACTION_UP
                    || event.getActionMasked()
                    == MotionEvent.ACTION_CANCEL;
        }
    }

    private static int clamp(
            int value,
            int minimum,
            int maximum
    ) {
        return Math.max(
                minimum,
                Math.min(maximum, value)
        );
    }

    private static int dp(Activity activity, int value) {
        return Math.round(
                value
                        * activity
                        .getResources()
                        .getDisplayMetrics()
                        .density
        );
    }
}
