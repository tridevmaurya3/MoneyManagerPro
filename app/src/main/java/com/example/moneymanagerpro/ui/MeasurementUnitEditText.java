package com.example.moneymanagerpro.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.ListPopupWindow;

import com.google.android.material.textfield.TextInputEditText;

/**
 * Read-only TextInputEditText that keeps the existing Add Expense item-row
 * contract while presenting measurement units as a dropdown list.
 */
public class MeasurementUnitEditText extends TextInputEditText {

    private static final String[] MEASUREMENT_UNITS = {
            "Unit",
            "Piece",
            "Pair",
            "Set",
            "Dozen",
            "Pack",
            "Packet",
            "Box",
            "Bag",
            "Bottle",
            "Can",
            "Jar",
            "Pouch",
            "Sachet",
            "Roll",
            "Sheet",
            "Strip",
            "Tablet",
            "Capsule",
            "Kilogram (kg)",
            "Gram (g)",
            "Milligram (mg)",
            "Tonne",
            "Pound (lb)",
            "Ounce (oz)",
            "Litre",
            "Millilitre (ml)",
            "Centilitre (cl)",
            "Gallon",
            "Cup",
            "Tablespoon",
            "Teaspoon",
            "Metre (m)",
            "Centimetre (cm)",
            "Millimetre (mm)",
            "Kilometre (km)",
            "Inch",
            "Foot (ft)",
            "Square metre (m²)",
            "Square foot (ft²)"
    };

    private ListPopupWindow unitPopup;

    public MeasurementUnitEditText(@NonNull Context context) {
        super(context);
        initialize();
    }

    public MeasurementUnitEditText(
            @NonNull Context context,
            @Nullable AttributeSet attrs
    ) {
        super(context, attrs);
        initialize();
    }

    public MeasurementUnitEditText(
            @NonNull Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr
    ) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    private void initialize() {
        setKeyListener(null);
        setCursorVisible(false);
        setFocusable(false);
        setClickable(true);
        setLongClickable(false);
        setCompoundDrawablesWithIntrinsicBounds(
                0,
                0,
                android.R.drawable.arrow_down_float,
                0
        );
        setCompoundDrawablePadding(dp(8));
        setOnClickListener(view -> showUnitDropdown());
    }

    private void showUnitDropdown() {
        if (unitPopup == null) {
            unitPopup = new ListPopupWindow(getContext());
            unitPopup.setAnchorView(this);
            unitPopup.setModal(true);
            unitPopup.setAdapter(
                    new ArrayAdapter<>(
                            getContext(),
                            android.R.layout.simple_list_item_1,
                            MEASUREMENT_UNITS
                    )
            );
            unitPopup.setOnItemClickListener(
                    (parent, view, position, id) -> {
                        if (position >= 0
                                && position < MEASUREMENT_UNITS.length) {
                            setText(MEASUREMENT_UNITS[position]);
                            setSelection(length());
                        }

                        unitPopup.dismiss();
                    }
            );
        }

        int fieldWidth = getWidth();

        if (fieldWidth > 0) {
            unitPopup.setWidth(fieldWidth);
        }

        unitPopup.show();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (unitPopup != null) {
            unitPopup.dismiss();
        }

        super.onDetachedFromWindow();
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
