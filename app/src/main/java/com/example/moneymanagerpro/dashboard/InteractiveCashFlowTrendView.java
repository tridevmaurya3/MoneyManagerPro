package com.example.moneymanagerpro.dashboard;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Lightweight interactive chart used by Smart Dashboard 2.0.
 *
 * It renders income and expense trends without adding a third-party chart
 * dependency. Touching or dragging across the chart highlights one period and
 * exposes its values through the listener.
 */
public final class InteractiveCashFlowTrendView extends View {

    public interface OnPointSelectedListener {
        void onPointSelected(@NonNull CashFlowPoint point, int position);
    }

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint incomePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint expensePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint incomeFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint expenseFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedPointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path incomePath = new Path();
    private final Path expensePath = new Path();
    private final Path incomeFillPath = new Path();
    private final Path expenseFillPath = new Path();
    private final RectF chartBounds = new RectF();
    private final RectF tooltipBounds = new RectF();

    private final List<CashFlowPoint> points = new ArrayList<>();

    @Nullable
    private OnPointSelectedListener pointSelectedListener;

    private int selectedIndex = -1;
    private float animationProgress = 1f;
    private float maximumValue = 1f;
    private ValueAnimator revealAnimator;

    public InteractiveCashFlowTrendView(Context context) {
        this(context, null);
    }

    public InteractiveCashFlowTrendView(
            Context context,
            @Nullable AttributeSet attrs
    ) {
        this(context, attrs, 0);
    }

    public InteractiveCashFlowTrendView(
            Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr
    ) {
        super(context, attrs, defStyleAttr);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        configurePaints();
        setClickable(true);
        setFocusable(true);
    }

    private void configurePaints() {
        gridPaint.setColor(Color.parseColor("#D9E2EC"));
        gridPaint.setStrokeWidth(dp(1));

        incomePaint.setColor(Color.parseColor("#107C10"));
        incomePaint.setStyle(Paint.Style.STROKE);
        incomePaint.setStrokeWidth(dp(2.5f));
        incomePaint.setStrokeCap(Paint.Cap.ROUND);
        incomePaint.setStrokeJoin(Paint.Join.ROUND);

        expensePaint.setColor(Color.parseColor("#C42B1C"));
        expensePaint.setStyle(Paint.Style.STROKE);
        expensePaint.setStrokeWidth(dp(2.5f));
        expensePaint.setStrokeCap(Paint.Cap.ROUND);
        expensePaint.setStrokeJoin(Paint.Join.ROUND);

        incomeFillPaint.setColor(Color.parseColor("#1A107C10"));
        incomeFillPaint.setStyle(Paint.Style.FILL);

        expenseFillPaint.setColor(Color.parseColor("#14C42B1C"));
        expenseFillPaint.setStyle(Paint.Style.FILL);

        labelPaint.setColor(Color.parseColor("#667085"));
        labelPaint.setTextSize(sp(10));
        labelPaint.setTextAlign(Paint.Align.CENTER);

        selectedLinePaint.setColor(Color.parseColor("#8A94A6"));
        selectedLinePaint.setStrokeWidth(dp(1));

        selectedPointPaint.setStyle(Paint.Style.FILL);

        tooltipPaint.setColor(Color.parseColor("#F7F9FC"));
        tooltipPaint.setStyle(Paint.Style.FILL);
        tooltipPaint.setShadowLayer(dp(7), 0, dp(2), Color.parseColor("#26000000"));

        tooltipTextPaint.setColor(Color.parseColor("#1D2939"));
        tooltipTextPaint.setTextSize(sp(10));
    }

    public void setData(@Nullable List<CashFlowPoint> newPoints) {
        points.clear();

        if (newPoints != null) {
            for (CashFlowPoint point : newPoints) {
                if (point != null) {
                    points.add(point);
                }
            }
        }

        maximumValue = 1f;
        for (CashFlowPoint point : points) {
            maximumValue = Math.max(
                    maximumValue,
                    (float) Math.max(point.getIncome(), point.getExpense())
            );
        }

        selectedIndex = points.isEmpty() ? -1 : points.size() - 1;
        startRevealAnimation();
    }

    @NonNull
    public List<CashFlowPoint> getData() {
        return Collections.unmodifiableList(points);
    }

    public void setOnPointSelectedListener(
            @Nullable OnPointSelectedListener listener
    ) {
        pointSelectedListener = listener;
    }

    private void startRevealAnimation() {
        if (revealAnimator != null) {
            revealAnimator.cancel();
        }

        animationProgress = 0f;
        revealAnimator = ValueAnimator.ofFloat(0f, 1f);
        revealAnimator.setDuration(650L);
        revealAnimator.addUpdateListener(animation -> {
            animationProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        revealAnimator.start();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        float left = getPaddingLeft() + dp(8);
        float top = getPaddingTop() + dp(14);
        float right = getWidth() - getPaddingRight() - dp(8);
        float bottom = getHeight() - getPaddingBottom() - dp(28);

        if (right <= left || bottom <= top) {
            return;
        }

        chartBounds.set(left, top, right, bottom);
        drawGrid(canvas);

        if (points.isEmpty()) {
            drawEmptyState(canvas);
            return;
        }

        buildPaths();
        canvas.drawPath(incomeFillPath, incomeFillPaint);
        canvas.drawPath(expenseFillPath, expenseFillPaint);
        canvas.drawPath(incomePath, incomePaint);
        canvas.drawPath(expensePath, expensePaint);
        drawLabels(canvas);
        drawSelection(canvas);
    }

    private void drawGrid(@NonNull Canvas canvas) {
        for (int index = 0; index <= 3; index++) {
            float y = chartBounds.top
                    + chartBounds.height() * index / 3f;
            canvas.drawLine(
                    chartBounds.left,
                    y,
                    chartBounds.right,
                    y,
                    gridPaint
            );
        }
    }

    private void drawEmptyState(@NonNull Canvas canvas) {
        Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        emptyPaint.setColor(Color.parseColor("#667085"));
        emptyPaint.setTextAlign(Paint.Align.CENTER);
        emptyPaint.setTextSize(sp(12));
        canvas.drawText(
                "Add transactions to view the cash-flow trend",
                chartBounds.centerX(),
                chartBounds.centerY(),
                emptyPaint
        );
    }

    private void buildPaths() {
        incomePath.reset();
        expensePath.reset();
        incomeFillPath.reset();
        expenseFillPath.reset();

        float step = points.size() <= 1
                ? 0f
                : chartBounds.width() / (points.size() - 1f);

        for (int index = 0; index < points.size(); index++) {
            CashFlowPoint point = points.get(index);
            float x = chartBounds.left + step * index;
            float incomeY = valueToY(point.getIncome() * animationProgress);
            float expenseY = valueToY(point.getExpense() * animationProgress);

            if (index == 0) {
                incomePath.moveTo(x, incomeY);
                expensePath.moveTo(x, expenseY);
                incomeFillPath.moveTo(x, chartBounds.bottom);
                incomeFillPath.lineTo(x, incomeY);
                expenseFillPath.moveTo(x, chartBounds.bottom);
                expenseFillPath.lineTo(x, expenseY);
            } else {
                incomePath.lineTo(x, incomeY);
                expensePath.lineTo(x, expenseY);
                incomeFillPath.lineTo(x, incomeY);
                expenseFillPath.lineTo(x, expenseY);
            }
        }

        float lastX = chartBounds.left + step * (points.size() - 1);
        incomeFillPath.lineTo(lastX, chartBounds.bottom);
        incomeFillPath.close();
        expenseFillPath.lineTo(lastX, chartBounds.bottom);
        expenseFillPath.close();
    }

    private float valueToY(double value) {
        float ratio = (float) Math.max(0d, value) / maximumValue;
        ratio = Math.min(1f, ratio);
        return chartBounds.bottom - chartBounds.height() * ratio;
    }

    private void drawLabels(@NonNull Canvas canvas) {
        float step = points.size() <= 1
                ? 0f
                : chartBounds.width() / (points.size() - 1f);

        for (int index = 0; index < points.size(); index++) {
            float x = chartBounds.left + step * index;
            canvas.drawText(
                    points.get(index).getLabel(),
                    x,
                    chartBounds.bottom + dp(20),
                    labelPaint
            );
        }
    }

    private void drawSelection(@NonNull Canvas canvas) {
        if (selectedIndex < 0 || selectedIndex >= points.size()) {
            return;
        }

        float step = points.size() <= 1
                ? 0f
                : chartBounds.width() / (points.size() - 1f);
        float x = chartBounds.left + step * selectedIndex;
        CashFlowPoint point = points.get(selectedIndex);
        float incomeY = valueToY(point.getIncome() * animationProgress);
        float expenseY = valueToY(point.getExpense() * animationProgress);

        canvas.drawLine(
                x,
                chartBounds.top,
                x,
                chartBounds.bottom,
                selectedLinePaint
        );

        selectedPointPaint.setColor(incomePaint.getColor());
        canvas.drawCircle(x, incomeY, dp(4), selectedPointPaint);
        selectedPointPaint.setColor(expensePaint.getColor());
        canvas.drawCircle(x, expenseY, dp(4), selectedPointPaint);

        drawTooltip(canvas, point, x);
    }

    private void drawTooltip(
            @NonNull Canvas canvas,
            @NonNull CashFlowPoint point,
            float selectedX
    ) {
        NumberFormat money = NumberFormat.getCurrencyInstance(
                new Locale("en", "IN")
        );
        money.setMaximumFractionDigits(0);

        String incomeText = "Income  " + money.format(point.getIncome());
        String expenseText = "Expense  " + money.format(point.getExpense());

        float width = Math.max(
                tooltipTextPaint.measureText(incomeText),
                tooltipTextPaint.measureText(expenseText)
        ) + dp(24);
        float height = dp(48);
        float left = selectedX - width / 2f;
        left = Math.max(chartBounds.left, left);
        left = Math.min(chartBounds.right - width, left);
        float top = chartBounds.top + dp(5);

        tooltipBounds.set(left, top, left + width, top + height);
        canvas.drawRoundRect(
                tooltipBounds,
                dp(10),
                dp(10),
                tooltipPaint
        );

        canvas.drawText(
                incomeText,
                tooltipBounds.left + dp(12),
                tooltipBounds.top + dp(19),
                tooltipTextPaint
        );
        canvas.drawText(
                expenseText,
                tooltipBounds.left + dp(12),
                tooltipBounds.top + dp(38),
                tooltipTextPaint
        );
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (points.isEmpty()) {
            return super.onTouchEvent(event);
        }

        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN
                || action == MotionEvent.ACTION_MOVE
                || action == MotionEvent.ACTION_UP) {
            selectNearestPoint(event.getX());

            if (action == MotionEvent.ACTION_UP) {
                performClick();
            }
            return true;
        }

        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void selectNearestPoint(float touchX) {
        if (points.size() == 1) {
            updateSelectedIndex(0);
            return;
        }

        float clamped = Math.max(
                chartBounds.left,
                Math.min(chartBounds.right, touchX)
        );
        float ratio = (clamped - chartBounds.left) / chartBounds.width();
        int index = Math.round(ratio * (points.size() - 1));
        updateSelectedIndex(index);
    }

    private void updateSelectedIndex(int index) {
        int safeIndex = Math.max(0, Math.min(points.size() - 1, index));
        if (safeIndex == selectedIndex) {
            return;
        }

        selectedIndex = safeIndex;
        invalidate();

        if (pointSelectedListener != null) {
            pointSelectedListener.onPointSelected(
                    points.get(selectedIndex),
                    selectedIndex
            );
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }

    public static final class CashFlowPoint {

        private final String label;
        private final double income;
        private final double expense;

        public CashFlowPoint(
                @NonNull String label,
                double income,
                double expense
        ) {
            this.label = label.trim();
            this.income = Math.max(0d, income);
            this.expense = Math.max(0d, expense);
        }

        @NonNull
        public String getLabel() {
            return label;
        }

        public double getIncome() {
            return income;
        }

        public double getExpense() {
            return expense;
        }

        public double getNetCashFlow() {
            return income - expense;
        }
    }
}
