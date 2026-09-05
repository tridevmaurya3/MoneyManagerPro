package com.example.moneymanagerpro.floating;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.moneymanagerpro.R;

/**
 * Floating-only presentation for the existing Add Income / Add Expense
 * activities. Their original fields, validation, database writes and save
 * behavior remain untouched.
 */
final class FloatingEntryWindow {

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
                Math.round(screenWidth * 0.92f)
        );
        attributes.height = Math.min(
                screenHeight - dp(activity, 34),
                Math.round(screenHeight * 0.82f)
        );
        attributes.gravity = Gravity.CENTER;
        attributes.dimAmount = 0.18f;
        attributes.alpha = 1.0f;
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
                    dp(activity, 22)
            );
            formBackground.setStroke(
                    dp(activity, 1),
                    Color.parseColor("#C9D8D0")
            );
            formRoot.setBackground(formBackground);
            formRoot.setClipToOutline(true);
        }

        compactFormPresentation(activity, content);
        addCloseControl(activity, content);
        addResizeHandles(activity, content);
        addMoveHandle(activity, content);
    }

    private static void compactFormPresentation(
            Activity activity,
            ViewGroup content
    ) {
        View back = activity.findViewById(R.id.btnBack);
        if (back != null) {
            back.setVisibility(View.GONE);
        }

        hideNearestCardContainingText(
                content,
                "Income Entry"
        );
        hideNearestCardContainingText(
                content,
                "Expense Entry"
        );
        hideNearestCardContainingText(
                content,
                "The selected account balance will increase after this income is saved."
        );
        hideNearestCardContainingText(
                content,
                "The selected account balance will decrease after this expense is saved."
        );

        String[] helperTexts = {
                "Record money received in one of your accounts.",
                "Record money spent from one of your accounts.",
                "Income Details",
                "Expense Details",
                "Enter the amount and select where the money was received",
                "Enter the amount and choose the account used for payment",
                "Enter the total amount you received",
                "Enter the total amount you paid",
                "Select the source of this income",
                "Choose the account where this money was received",
                "Example: Oil → 1 litre × price per litre. Add another row for Potatoes → 1 kg × price per kg.",
                "Select what this money was spent on",
                "Choose the account used to make this payment",
                "Enter a UPI ID manually or scan a payment QR code to fill the receiver details automatically.",
                "Tap the field to choose a different date",
                "Optional information about this income",
                "Optional information about this expense",
                "Optionally attach the shop bill or payment receipt",
                "Select an image from your device"
        };

        for (String text : helperTexts) {
            hideTextExact(content, text);
        }

        View formRoot = content.getChildCount() > 0
                ? content.getChildAt(0)
                : null;
        if (formRoot instanceof ViewGroup) {
            ViewGroup rootGroup = (ViewGroup) formRoot;
            if (rootGroup.getChildCount() > 0) {
                View inner = rootGroup.getChildAt(0);
                inner.setPadding(
                        dp(activity, 12),
                        dp(activity, 28),
                        dp(activity, 12),
                        dp(activity, 14)
                );
            }
        }

        compactMargins(activity, content);

        setMinimumHeight(
                activity,
                content,
                R.id.etAmount,
                48
        );
        setMinimumHeight(
                activity,
                content,
                R.id.dropdownCategory,
                48
        );
        setMinimumHeight(
                activity,
                content,
                R.id.dropdownAccount,
                48
        );
        setMinimumHeight(
                activity,
                content,
                R.id.etDate,
                48
        );
        setMinimumHeight(
                activity,
                content,
                R.id.etNote,
                72
        );
        setMinimumHeight(
                activity,
                content,
                R.id.dropdownUpiEntryMode,
                46
        );
        setMinimumHeight(
                activity,
                content,
                R.id.etUpiPayeeId,
                46
        );
        setMinimumHeight(
                activity,
                content,
                R.id.etUpiPayeeName,
                46
        );

        capFixedHeight(
                activity,
                content,
                R.id.btnSaveIncome,
                50
        );
        capFixedHeight(
                activity,
                content,
                R.id.btnSaveExpense,
                50
        );
        capFixedHeight(
                activity,
                content,
                R.id.btnMoreItem,
                44
        );
        capFixedHeight(
                activity,
                content,
                R.id.btnPayWithUpi,
                46
        );
        capFixedHeight(
                activity,
                content,
                R.id.btnAttachReceipt,
                46
        );
    }

    private static void hideNearestCardContainingText(
            View root,
            String text
    ) {
        TextView target = findTextViewExact(root, text);

        if (target == null) {
            return;
        }

        View current = target;

        while (current != null) {
            String className = current
                    .getClass()
                    .getName();

            if (className.contains("MaterialCardView")) {
                current.setVisibility(View.GONE);
                return;
            }

            ViewParent parent = current.getParent();
            current = parent instanceof View
                    ? (View) parent
                    : null;
        }
    }

    private static void hideTextExact(
            View root,
            String text
    ) {
        TextView target = findTextViewExact(root, text);
        if (target != null) {
            target.setVisibility(View.GONE);
        }
    }

    private static TextView findTextViewExact(
            View root,
            String text
    ) {
        if (root instanceof TextView) {
            TextView textView = (TextView) root;
            CharSequence value = textView.getText();

            if (value != null
                    && value.toString().trim().equals(text)) {
                return textView;
            }
        }

        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;

            for (int index = 0;
                 index < group.getChildCount();
                 index++) {
                TextView found = findTextViewExact(
                        group.getChildAt(index),
                        text
                );

                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    private static void compactMargins(
            Activity activity,
            ViewGroup group
    ) {
        if (group.getClass()
                .getName()
                .contains("TextInputLayout")) {
            return;
        }

        for (int index = 0;
             index < group.getChildCount();
             index++) {
            View child = group.getChildAt(index);
            ViewGroup.LayoutParams rawParams =
                    child.getLayoutParams();

            if (rawParams
                    instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams margins =
                        (ViewGroup.MarginLayoutParams) rawParams;

                margins.topMargin = Math.min(
                        margins.topMargin,
                        dp(activity, 8)
                );
                margins.bottomMargin = Math.min(
                        margins.bottomMargin,
                        dp(activity, 5)
                );
                child.setLayoutParams(margins);
            }

            if (child instanceof LinearLayout) {
                int horizontal = Math.min(
                        child.getPaddingLeft(),
                        dp(activity, 12)
                );
                int verticalTop = Math.min(
                        child.getPaddingTop(),
                        dp(activity, 12)
                );
                int horizontalEnd = Math.min(
                        child.getPaddingRight(),
                        dp(activity, 12)
                );
                int verticalBottom = Math.min(
                        child.getPaddingBottom(),
                        dp(activity, 12)
                );
                child.setPadding(
                        horizontal,
                        verticalTop,
                        horizontalEnd,
                        verticalBottom
                );
            }

            if (child instanceof ViewGroup) {
                compactMargins(
                        activity,
                        (ViewGroup) child
                );
            }
        }
    }

    private static void setMinimumHeight(
            Activity activity,
            View root,
            int id,
            int heightDp
    ) {
        View view = root.findViewById(id);
        if (view != null) {
            view.setMinimumHeight(
                    dp(activity, heightDp)
            );
        }
    }

    private static void capFixedHeight(
            Activity activity,
            View root,
            int id,
            int heightDp
    ) {
        View view = root.findViewById(id);

        if (view == null) {
            return;
        }

        int target = dp(activity, heightDp);
        view.setMinimumHeight(target);

        ViewGroup.LayoutParams params =
                view.getLayoutParams();

        if (params != null && params.height > target) {
            params.height = target;
            view.setLayoutParams(params);
        }
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
                        dp(activity, 34),
                        dp(activity, 34),
                        Gravity.TOP | Gravity.END
                );
        params.setMargins(
                0,
                dp(activity, 7),
                dp(activity, 7),
                0
        );
        content.addView(close, params);
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
        control.setTextSize(18);
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
        handle.setTextSize(13);

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.parseColor("#DDF7EFEA"));
        background.setCornerRadius(dp(activity, 7));
        background.setStroke(
                dp(activity, 1),
                Color.parseColor("#AFC8BE")
        );
        handle.setBackground(background);

        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(
                        dp(activity, 24),
                        dp(activity, 24),
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
        handle.setTextSize(19);

        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(
                        dp(activity, 54),
                        dp(activity, 24),
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
                        dp(activity, 360),
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
