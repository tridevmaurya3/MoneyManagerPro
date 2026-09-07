package com.example.moneymanagerpro.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Calculates an expense-item total without asking the user to type a rate
 * suffix such as "/kg". For smaller measurement units, Price is interpreted
 * against the natural larger rate unit used for that measurement:
 * g -> kg, mg -> g, ml -> litre, cl -> litre, cm/mm -> metre.
 * Count/package-style units keep the existing quantity x price behavior.
 */
public final class ExpenseItemPriceCalculator {

    private ExpenseItemPriceCalculator() {
    }

    public static BigDecimal calculate(
            BigDecimal quantity,
            String unit,
            BigDecimal price
    ) {
        if (quantity == null || price == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal normalizedQuantity = quantity.multiply(rateFactor(unit));
        return normalizedQuantity
                .multiply(price)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal rateFactor(String unit) {
        String normalized = normalize(unit);

        if (normalized.equals("g")
                || normalized.equals("gram")
                || normalized.equals("gram (g)")) {
            return new BigDecimal("0.001");
        }

        if (normalized.equals("mg")
                || normalized.equals("milligram")
                || normalized.equals("milligram (mg)")) {
            return new BigDecimal("0.001");
        }

        if (normalized.equals("ml")
                || normalized.equals("millilitre")
                || normalized.equals("millilitre (ml)")
                || normalized.equals("milliliter")
                || normalized.equals("milliliter (ml)")) {
            return new BigDecimal("0.001");
        }

        if (normalized.equals("cl")
                || normalized.equals("centilitre")
                || normalized.equals("centilitre (cl)")
                || normalized.equals("centiliter")
                || normalized.equals("centiliter (cl)")) {
            return new BigDecimal("0.01");
        }

        if (normalized.equals("cm")
                || normalized.equals("centimetre")
                || normalized.equals("centimetre (cm)")
                || normalized.equals("centimeter")
                || normalized.equals("centimeter (cm)")) {
            return new BigDecimal("0.01");
        }

        if (normalized.equals("mm")
                || normalized.equals("millimetre")
                || normalized.equals("millimetre (mm)")
                || normalized.equals("millimeter")
                || normalized.equals("millimeter (mm)")) {
            return new BigDecimal("0.001");
        }

        return BigDecimal.ONE;
    }

    private static String normalize(String unit) {
        return unit == null
                ? ""
                : unit.trim().toLowerCase(Locale.US);
    }
}
