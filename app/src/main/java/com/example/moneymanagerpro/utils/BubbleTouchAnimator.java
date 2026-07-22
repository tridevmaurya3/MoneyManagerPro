package com.example.moneymanagerpro.utils;

import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

public class BubbleTouchAnimator {

    public static void apply(View view) {
        if (view == null) {
            return;
        }

        view.setClickable(true);

        view.setOnTouchListener((v, event) -> {
            if (!v.isEnabled()) {
                return false;
            }

            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.animate()
                        .cancel();

                v.animate()
                        .scaleX(0.94f)
                        .scaleY(0.94f)
                        .alpha(0.90f)
                        .setDuration(90)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();

            } else if (event.getAction() == MotionEvent.ACTION_UP ||
                    event.getAction() == MotionEvent.ACTION_CANCEL) {

                v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(280)
                        .setInterpolator(new OvershootInterpolator(2f))
                        .start();
            }

            return false;
        });
    }
}