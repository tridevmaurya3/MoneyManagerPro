package com.example.moneymanagerpro.credit;

import com.example.moneymanagerpro.model.CreditCard;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public final class CreditCardCycleCalculator {

    private CreditCardCycleCalculator() {
    }

    public static Cycle calculate(
            CreditCard creditCard,
            Calendar referenceDate
    ) {
        Calendar today = copyAtMidnight(referenceDate);

        Calendar thisMonthBilling = clampedDate(
                today.get(Calendar.YEAR),
                today.get(Calendar.MONTH),
                creditCard.getBillingDay()
        );

        Calendar closedEnd;

        if (!today.before(thisMonthBilling)) {
            closedEnd = thisMonthBilling;
        } else {
            closedEnd = moveBillingMonth(
                    thisMonthBilling,
                    -1,
                    creditCard.getBillingDay()
            );
        }

        Calendar previousClosedEnd =
                moveBillingMonth(
                        closedEnd,
                        -1,
                        creditCard.getBillingDay()
                );

        Calendar closedStart =
                addDays(previousClosedEnd, 1);

        Calendar currentStart =
                addDays(closedEnd, 1);

        Calendar currentEnd =
                moveBillingMonth(
                        closedEnd,
                        1,
                        creditCard.getBillingDay()
                );

        Calendar dueDate =
                calculateDueDate(
                        closedEnd,
                        creditCard.getDueDay()
                );

        return new Cycle(
                formatIso(closedStart),
                formatIso(closedEnd),
                formatIso(dueDate),
                formatIso(currentStart),
                formatIso(currentEnd),
                daysBetween(today, dueDate)
        );
    }

    public static Calendar calculateDueDate(
            Calendar statementEnd,
            int dueDay
    ) {
        Calendar dueDate = clampedDate(
                statementEnd.get(Calendar.YEAR),
                statementEnd.get(Calendar.MONTH),
                dueDay
        );

        if (!dueDate.after(statementEnd)) {
            Calendar nextMonth =
                    (Calendar) statementEnd.clone();
            nextMonth.add(Calendar.MONTH, 1);

            dueDate = clampedDate(
                    nextMonth.get(Calendar.YEAR),
                    nextMonth.get(Calendar.MONTH),
                    dueDay
            );
        }

        return dueDate;
    }

    public static Statement calculateStatement(
            CreditCard creditCard,
            Calendar statementEndReference
    ) {
        Calendar statementEnd = clampedDate(
                statementEndReference.get(Calendar.YEAR),
                statementEndReference.get(Calendar.MONTH),
                creditCard.getBillingDay()
        );

        Calendar previousEnd =
                moveBillingMonth(
                        statementEnd,
                        -1,
                        creditCard.getBillingDay()
                );

        Calendar statementStart =
                addDays(previousEnd, 1);

        Calendar dueDate =
                calculateDueDate(
                        statementEnd,
                        creditCard.getDueDay()
                );

        return new Statement(
                formatIso(statementStart),
                formatIso(statementEnd),
                formatIso(dueDate)
        );
    }

    public static Calendar previousStatementEnd(
            CreditCard creditCard,
            Calendar statementEnd
    ) {
        return moveBillingMonth(
                statementEnd,
                -1,
                creditCard.getBillingDay()
        );
    }

    private static Calendar moveBillingMonth(
            Calendar source,
            int monthDelta,
            int billingDay
    ) {
        Calendar moved =
                (Calendar) source.clone();
        moved.add(Calendar.MONTH, monthDelta);

        return clampedDate(
                moved.get(Calendar.YEAR),
                moved.get(Calendar.MONTH),
                billingDay
        );
    }

    private static Calendar clampedDate(
            int year,
            int month,
            int requestedDay
    ) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month);
        calendar.set(Calendar.DAY_OF_MONTH, 1);

        int safeDay = Math.max(
                1,
                Math.min(
                        requestedDay,
                        calendar.getActualMaximum(
                                Calendar.DAY_OF_MONTH
                        )
                )
        );

        calendar.set(
                Calendar.DAY_OF_MONTH,
                safeDay
        );

        return calendar;
    }

    private static Calendar copyAtMidnight(
            Calendar source
    ) {
        Calendar copy =
                (Calendar) source.clone();
        copy.set(Calendar.HOUR_OF_DAY, 0);
        copy.set(Calendar.MINUTE, 0);
        copy.set(Calendar.SECOND, 0);
        copy.set(Calendar.MILLISECOND, 0);
        return copy;
    }

    private static Calendar addDays(
            Calendar source,
            int days
    ) {
        Calendar result =
                (Calendar) source.clone();
        result.add(Calendar.DAY_OF_MONTH, days);
        return result;
    }

    private static int daysBetween(
            Calendar start,
            Calendar end
    ) {
        long difference =
                end.getTimeInMillis()
                        - start.getTimeInMillis();

        return (int) (
                difference
                        / (24L * 60L * 60L * 1000L)
        );
    }

    private static String formatIso(
            Calendar calendar
    ) {
        return new SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.US
        ).format(calendar.getTime());
    }

    public static final class Cycle {
        public final String closedStart;
        public final String closedEnd;
        public final String dueDate;
        public final String currentStart;
        public final String currentEnd;
        public final int daysUntilDue;

        private Cycle(
                String closedStart,
                String closedEnd,
                String dueDate,
                String currentStart,
                String currentEnd,
                int daysUntilDue
        ) {
            this.closedStart = closedStart;
            this.closedEnd = closedEnd;
            this.dueDate = dueDate;
            this.currentStart = currentStart;
            this.currentEnd = currentEnd;
            this.daysUntilDue = daysUntilDue;
        }
    }

    public static final class Statement {
        public final String startDate;
        public final String endDate;
        public final String dueDate;

        private Statement(
                String startDate,
                String endDate,
                String dueDate
        ) {
            this.startDate = startDate;
            this.endDate = endDate;
            this.dueDate = dueDate;
        }
    }
}
