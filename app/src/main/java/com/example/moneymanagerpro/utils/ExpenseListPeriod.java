package com.example.moneymanagerpro.utils;

import java.util.Calendar;

/** Local-calendar ranges for the Expense list only; no ledger or account writes. */
public final class ExpenseListPeriod {
    private ExpenseListPeriod() { }
    public static final class Range {
        public final long start, endExclusive;
        Range(long start, long endExclusive) { this.start = start; this.endExclusive = endExclusive; }
        public boolean contains(long date) { return date >= start && date < endExclusive; }
    }
    public static long day(long time) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(time);
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
    public static long addDays(long date, int count) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(date); c.add(Calendar.DAY_OF_MONTH, count);
        return c.getTimeInMillis();
    }
    private static Calendar month(long date) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(day(date)); c.set(Calendar.DAY_OF_MONTH, 1); return c;
    }
    private static boolean sameMonth(long first, long second) {
        Calendar a = month(first), b = month(second);
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.MONTH) == b.get(Calendar.MONTH);
    }
    public static Range range(String period, long now, long selectedMonth, Long from, Long to) {
        long today = day(now), tomorrow = addDays(today, 1);
        if ("Today".equals(period)) return new Range(today, tomorrow);
        if ("Yesterday".equals(period)) return new Range(addDays(today, -1), today);
        if ("This Week".equals(period)) return new Range(monday(today), tomorrow);
        if ("Selected Month".equals(period)) {
            Calendar start = month(selectedMonth), end = (Calendar)start.clone(); end.add(Calendar.MONTH, 1);
            return new Range(start.getTimeInMillis(), sameMonth(now, selectedMonth) ? tomorrow : end.getTimeInMillis());
        }
        if ("Selected Year".equals(period)) {
            Calendar start = month(selectedMonth); start.set(Calendar.MONTH, Calendar.JANUARY);
            Calendar end = (Calendar)start.clone(); end.add(Calendar.YEAR, 1);
            return new Range(start.getTimeInMillis(), end.getTimeInMillis());
        }
        return new Range(from == null ? Long.MIN_VALUE : day(from),
                to == null ? Long.MAX_VALUE : addDays(day(to), 1));
    }
    private static long monday(long date) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(day(date));
        int daysSinceMonday = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        return addDays(day(date), -daysSinceMonday);
    }
    /** In month view today is separate; older entries form clipped Monday–Sunday groups. */
    public static long groupStart(String period, long date, long now, long selectedMonth) {
        long entry = day(date);
        if (!"Selected Month".equals(period) || entry == day(now)) return entry;
        return Math.max(monday(entry), month(selectedMonth).getTimeInMillis());
    }
    public static long groupEnd(String period, long start, long now, long selectedMonth) {
        if (!"Selected Month".equals(period) || start == day(now)) return start;
        Calendar end = month(selectedMonth); end.add(Calendar.MONTH, 1);
        long last = addDays(end.getTimeInMillis(), -1);
        if (sameMonth(now, selectedMonth)) last = Math.min(last, addDays(day(now), -1));
        return Math.min(addDays(monday(start), 6), last);
    }
}
