package com.example.moneymanagerpro.charts;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FinanceChartView extends View {

    public static final int MODE_PIE = 0;
    public static final int MODE_BAR = 1;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final List<String> pieLabels = new ArrayList<>();
    private final List<Double> pieValues = new ArrayList<>();

    private final List<String> monthLabels = new ArrayList<>();
    private final List<Double> incomeValues = new ArrayList<>();
    private final List<Double> expenseValues = new ArrayList<>();

    private final int[] chartColors = {
            Color.parseColor("#7C3AED"),
            Color.parseColor("#0F766E"),
            Color.parseColor("#F97316"),
            Color.parseColor("#DB2777"),
            Color.parseColor("#2563EB"),
            Color.parseColor("#CA8A04")
    };

    private int mode = MODE_PIE;
    private int selectedIndex = -1;

    public FinanceChartView(Context context) {
        super(context);
        init();
    }

    public FinanceChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FinanceChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        textPaint.setColor(Color.parseColor("#172033"));
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
    }

    public void setMode(int mode) {
        this.mode = mode;
        selectedIndex = -1;
        invalidate();
    }

    public void setPieData(Map<String, Double> data) {
        pieLabels.clear();
        pieValues.clear();

        for (Map.Entry<String, Double> entry : data.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) {
                pieLabels.add(entry.getKey());
                pieValues.add(entry.getValue());
            }
        }

        selectedIndex = -1;
        invalidate();
    }

    public void setMonthlyData(
            List<String> labels,
            List<Double> incomes,
            List<Double> expenses
    ) {
        monthLabels.clear();
        incomeValues.clear();
        expenseValues.clear();

        monthLabels.addAll(labels);
        incomeValues.addAll(incomes);
        expenseValues.addAll(expenses);

        selectedIndex = -1;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mode == MODE_PIE) {
            drawPieChart(canvas);
        } else {
            drawBarChart(canvas);
        }
    }

    private void drawPieChart(Canvas canvas) {
        if (pieValues.isEmpty()) {
            drawEmptyMessage(canvas, "No expense data for this month");
            return;
        }

        float width = getWidth();
        float height = getHeight();

        float centerX = width / 2f;
        float centerY = height * 0.25f;
        float radius = Math.min(width * 0.23f, height * 0.145f);

        double total = 0;

        for (double value : pieValues) {
            total += value;
        }

        float startAngle = -90f;

        for (int i = 0; i < pieValues.size(); i++) {
            float sweepAngle = (float) ((pieValues.get(i) / total) * 360f);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(chartColors[i % chartColors.length]);

            canvas.drawArc(
                    centerX - radius,
                    centerY - radius,
                    centerX + radius,
                    centerY + radius,
                    startAngle,
                    sweepAngle,
                    true,
                    paint
            );

            if (selectedIndex == i) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(4));
                paint.setColor(Color.WHITE);

                canvas.drawArc(
                        centerX - radius,
                        centerY - radius,
                        centerX + radius,
                        centerY + radius,
                        startAngle,
                        sweepAngle,
                        true,
                        paint
                );
            }

            startAngle += sweepAngle;
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(centerX, centerY, radius * 0.54f, paint);

        double centerAmount = selectedIndex >= 0
                ? pieValues.get(selectedIndex)
                : total;

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(sp(17));
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setColor(Color.parseColor("#172033"));

        canvas.drawText(
                formatCompactMoney(centerAmount),
                centerX,
                centerY + 6,
                textPaint
        );

        float rowTop = centerY + radius + dp(24);
        float rowHeight = dp(34);
        float rowGap = dp(5);

        for (int i = 0; i < pieLabels.size(); i++) {
            float top = rowTop + (i * (rowHeight + rowGap));
            float bottom = top + rowHeight;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(
                    selectedIndex == i
                            ? Color.parseColor("#F3E8FF")
                            : Color.parseColor("#F8FAFC")
            );

            canvas.drawRoundRect(
                    dp(12),
                    top,
                    width - dp(12),
                    bottom,
                    dp(11),
                    dp(11),
                    paint
            );

            paint.setColor(chartColors[i % chartColors.length]);
            canvas.drawCircle(dp(28), top + (rowHeight / 2f), dp(5), paint);

            textPaint.setTextAlign(Paint.Align.LEFT);
            textPaint.setTextSize(sp(12));
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            textPaint.setColor(Color.parseColor("#334155"));

            canvas.drawText(
                    shortText(pieLabels.get(i), 20),
                    dp(41),
                    top + dp(21),
                    textPaint
            );

            textPaint.setTextAlign(Paint.Align.RIGHT);
            textPaint.setTextSize(sp(11));
            textPaint.setColor(Color.parseColor("#172033"));

            canvas.drawText(
                    formatExactMoney(pieValues.get(i)),
                    width - dp(24),
                    top + dp(21),
                    textPaint
            );
        }
    }

    private void drawBarChart(Canvas canvas) {
        if (monthLabels.isEmpty()) {
            drawEmptyMessage(canvas, "No monthly data available");
            return;
        }

        float width = getWidth();
        float height = getHeight();

        float chartLeft = dp(42);
        float chartRight = width - dp(18);
        float chartTop = dp(48);
        float chartBottom = height - dp(58);

        float chartWidth = chartRight - chartLeft;
        float chartHeight = chartBottom - chartTop;

        double maximum = 1;

        for (double value : incomeValues) {
            maximum = Math.max(maximum, value);
        }

        for (double value : expenseValues) {
            maximum = Math.max(maximum, value);
        }

        maximum = maximum * 1.15;

        paint.setStrokeWidth(dp(1));
        paint.setColor(Color.parseColor("#E2E8F0"));

        for (int i = 0; i <= 4; i++) {
            float y = chartTop + (chartHeight * i / 4f);
            canvas.drawLine(chartLeft, y, chartRight, y, paint);
        }

        float groupWidth = chartWidth / monthLabels.size();
        float barWidth = Math.min(dp(18), groupWidth * 0.22f);

        for (int i = 0; i < monthLabels.size(); i++) {
            float groupCenter = chartLeft + (groupWidth * i) + (groupWidth / 2f);

            float incomeHeight = (float) ((incomeValues.get(i) / maximum) * chartHeight);
            float expenseHeight = (float) ((expenseValues.get(i) / maximum) * chartHeight);

            if (selectedIndex == i) {
                paint.setColor(Color.parseColor("#F1F5F9"));

                canvas.drawRoundRect(
                        groupCenter - (groupWidth / 2f) + dp(2),
                        chartTop,
                        groupCenter + (groupWidth / 2f) - dp(2),
                        chartBottom + dp(5),
                        dp(12),
                        dp(12),
                        paint
                );
            }

            paint.setColor(Color.parseColor("#2E7D32"));

            canvas.drawRoundRect(
                    groupCenter - barWidth - dp(2),
                    chartBottom - incomeHeight,
                    groupCenter - dp(2),
                    chartBottom,
                    dp(8),
                    dp(8),
                    paint
            );

            paint.setColor(Color.parseColor("#DC2626"));

            canvas.drawRoundRect(
                    groupCenter + dp(2),
                    chartBottom - expenseHeight,
                    groupCenter + barWidth + dp(2),
                    chartBottom,
                    dp(8),
                    dp(8),
                    paint
            );

            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(sp(11));
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            textPaint.setColor(Color.parseColor("#64748B"));

            canvas.drawText(
                    monthLabels.get(i),
                    groupCenter,
                    chartBottom + dp(23),
                    textPaint
            );
        }

        if (selectedIndex >= 0 && selectedIndex < monthLabels.size()) {
            drawBarTooltip(
                    canvas,
                    selectedIndex,
                    chartLeft,
                    chartRight,
                    chartTop,
                    chartBottom,
                    maximum
            );
        } else {
            drawBarLegend(canvas, width);
        }
    }

    private void drawBarLegend(Canvas canvas, float width) {
        paint.setColor(Color.parseColor("#2E7D32"));
        canvas.drawCircle(width / 2f - dp(62), dp(22), dp(5), paint);

        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize(sp(11));
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        textPaint.setColor(Color.parseColor("#475569"));
        canvas.drawText("Income", width / 2f - dp(52), dp(26), textPaint);

        paint.setColor(Color.parseColor("#DC2626"));
        canvas.drawCircle(width / 2f + dp(18), dp(22), dp(5), paint);

        canvas.drawText("Expense", width / 2f + dp(28), dp(26), textPaint);
    }

    private void drawBarTooltip(
            Canvas canvas,
            int index,
            float chartLeft,
            float chartRight,
            float chartTop,
            float chartBottom,
            double maximum
    ) {
        float chartWidth = chartRight - chartLeft;
        float groupWidth = chartWidth / monthLabels.size();
        float groupCenter = chartLeft + (groupWidth * index) + (groupWidth / 2f);

        float incomeHeight = (float) ((incomeValues.get(index) / maximum)
                * (chartBottom - chartTop));

        float expenseHeight = (float) ((expenseValues.get(index) / maximum)
                * (chartBottom - chartTop));

        float highestBarTop = Math.min(
                chartBottom - incomeHeight,
                chartBottom - expenseHeight
        );

        float tooltipWidth = dp(160);
        float tooltipHeight = dp(48);

        float tooltipLeft = groupCenter - (tooltipWidth / 2f);

        if (tooltipLeft < dp(8)) {
            tooltipLeft = dp(8);
        }

        if (tooltipLeft + tooltipWidth > getWidth() - dp(8)) {
            tooltipLeft = getWidth() - tooltipWidth - dp(8);
        }

        float tooltipTop = Math.max(
                chartTop + dp(4),
                highestBarTop - tooltipHeight - dp(14)
        );

        paint.setColor(Color.parseColor("#172033"));
        paint.setShadowLayer(dp(5), 0, dp(2), Color.parseColor("#33000000"));

        canvas.drawRoundRect(
                tooltipLeft,
                tooltipTop,
                tooltipLeft + tooltipWidth,
                tooltipTop + tooltipHeight,
                dp(12),
                dp(12),
                paint
        );

        paint.clearShadowLayer();

        Path arrow = new Path();
        arrow.moveTo(groupCenter - dp(7), tooltipTop + tooltipHeight);
        arrow.lineTo(groupCenter + dp(7), tooltipTop + tooltipHeight);
        arrow.lineTo(groupCenter, tooltipTop + tooltipHeight + dp(8));
        arrow.close();

        canvas.drawPath(arrow, paint);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(sp(10));
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setColor(Color.WHITE);

        canvas.drawText(
                monthLabels.get(index)
                        + "  •  Income " + formatNoDecimalMoney(incomeValues.get(index)),
                tooltipLeft + (tooltipWidth / 2f),
                tooltipTop + dp(18),
                textPaint
        );

        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        canvas.drawText(
                "Expense " + formatNoDecimalMoney(expenseValues.get(index)),
                tooltipLeft + (tooltipWidth / 2f),
                tooltipTop + dp(35),
                textPaint
        );
    }

    private void drawEmptyMessage(Canvas canvas, String message) {
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(sp(16));
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        textPaint.setColor(Color.parseColor("#64748B"));

        canvas.drawText(message, getWidth() / 2f, getHeight() / 2f, textPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP) {
            return true;
        }

        if (mode == MODE_PIE) {
            selectPiePart(event.getX(), event.getY());
        } else {
            selectMonth(event.getX());
        }

        performClick();
        return true;
    }

    private void selectPiePart(float x, float y) {
        if (pieValues.isEmpty()) {
            return;
        }

        float centerX = getWidth() / 2f;
        float centerY = getHeight() * 0.25f;
        float radius = Math.min(getWidth() * 0.23f, getHeight() * 0.145f);

        float distance = (float) Math.sqrt(
                Math.pow(x - centerX, 2) + Math.pow(y - centerY, 2)
        );

        if (distance > radius) {
            return;
        }

        double total = 0;

        for (double value : pieValues) {
            total += value;
        }

        float angle = (float) Math.toDegrees(Math.atan2(y - centerY, x - centerX));
        float relativeAngle = angle + 90f;

        if (relativeAngle < 0) {
            relativeAngle += 360f;
        }

        float currentAngle = 0;

        for (int i = 0; i < pieValues.size(); i++) {
            float sweepAngle = (float) ((pieValues.get(i) / total) * 360f);

            if (relativeAngle >= currentAngle
                    && relativeAngle <= currentAngle + sweepAngle) {
                selectedIndex = i;
                invalidate();
                return;
            }

            currentAngle += sweepAngle;
        }
    }

    private void selectMonth(float x) {
        if (monthLabels.isEmpty()) {
            return;
        }

        float chartLeft = dp(42);
        float chartRight = getWidth() - dp(18);

        if (x < chartLeft || x > chartRight) {
            return;
        }

        float groupWidth = (chartRight - chartLeft) / monthLabels.size();
        int index = (int) ((x - chartLeft) / groupWidth);

        if (index >= 0 && index < monthLabels.size()) {
            selectedIndex = index;
            invalidate();
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private String shortText(String text, int maxLength) {
        if (text == null || text.trim().isEmpty()) {
            return "Other";
        }

        if (text.length() <= maxLength) {
            return text;
        }

        return text.substring(0, maxLength - 1) + "…";
    }

    private String formatExactMoney(double amount) {
        NumberFormat formatter = NumberFormat.getNumberInstance(new Locale("en", "IN"));
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);

        return "₹" + formatter.format(amount);
    }

    private String formatNoDecimalMoney(double amount) {
        NumberFormat formatter = NumberFormat.getNumberInstance(new Locale("en", "IN"));
        formatter.setMinimumFractionDigits(0);
        formatter.setMaximumFractionDigits(0);

        return "₹" + formatter.format(amount);
    }

    private String formatCompactMoney(double amount) {
        if (amount >= 100000) {
            return String.format(Locale.US, "₹%.2fL", amount / 100000d);
        }

        if (amount >= 1000) {
            return String.format(Locale.US, "₹%.1fK", amount / 1000d);
        }

        return formatNoDecimalMoney(amount);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}