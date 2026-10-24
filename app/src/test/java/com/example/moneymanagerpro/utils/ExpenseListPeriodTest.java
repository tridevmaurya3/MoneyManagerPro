package com.example.moneymanagerpro.utils;

import org.junit.Test;
import java.util.Calendar;
import java.util.TimeZone;
import static org.junit.Assert.*;

public class ExpenseListPeriodTest {
    private long at(int y,int m,int d) {
        Calendar c=Calendar.getInstance();c.clear();c.set(y,m-1,d,12,0);return c.getTimeInMillis();
    }
    @Test public void todayExcludesAdjacentDays() {
        long now=at(2026,10,24);
        ExpenseListPeriod.Range r=ExpenseListPeriod.range("Today",now,now,null,null);
        assertTrue(r.contains(now));assertFalse(r.contains(at(2026,10,23)));assertFalse(r.contains(at(2026,10,25)));
    }
    @Test public void yesterdayCrossesNewYear() {
        long now=at(2027,1,1);
        ExpenseListPeriod.Range r=ExpenseListPeriod.range("Yesterday",now,now,null,null);
        assertTrue(r.contains(at(2026,12,31)));assertFalse(r.contains(now));
    }
    @Test public void weekCrossesMonthAndStopsAtToday() {
        long now=at(2026,11,1);
        ExpenseListPeriod.Range r=ExpenseListPeriod.range("This Week",now,now,null,null);
        assertTrue(r.contains(at(2026,10,26)));assertTrue(r.contains(now));
        assertFalse(r.contains(at(2026,10,25)));assertFalse(r.contains(at(2026,11,2)));
    }
    @Test public void mondayDoesNotIncludePreviousWeekOrFutureDays() {
        long now=at(2026,10,19);
        ExpenseListPeriod.Range r=ExpenseListPeriod.range("This Week",now,now,null,null);
        assertEquals(ExpenseListPeriod.day(now),r.start);
        assertFalse(r.contains(at(2026,10,18)));assertFalse(r.contains(at(2026,10,20)));
    }
    @Test public void currentMonthAndHistoricalMonthAreDifferentScopes() {
        long now=at(2026,10,24);
        ExpenseListPeriod.Range current=ExpenseListPeriod.range("Selected Month",now,now,null,null);
        assertTrue(current.contains(at(2026,10,1)));assertFalse(current.contains(at(2026,10,25)));
        ExpenseListPeriod.Range past=ExpenseListPeriod.range("Selected Month",now,at(2024,2,1),null,null);
        assertTrue(past.contains(at(2024,2,29)));assertFalse(past.contains(at(2024,3,1)));
    }
    @Test public void customRangeIncludesEntireEndDate() {
        long now=at(2026,10,24);
        ExpenseListPeriod.Range r=ExpenseListPeriod.range("Custom Range",now,now,at(2026,9,30),at(2026,10,2));
        assertTrue(r.contains(at(2026,10,2)));assertFalse(r.contains(at(2026,10,3)));
    }
    @Test public void monthGroupsClipFirstWeekAndKeepTodaySeparate() {
        long now=at(2026,10,24),month=at(2026,10,1);
        long first=ExpenseListPeriod.groupStart("Selected Month",at(2026,10,2),now,month);
        assertEquals(ExpenseListPeriod.day(month),first);
        assertEquals(ExpenseListPeriod.day(at(2026,10,4)),ExpenseListPeriod.groupEnd("Selected Month",first,now,month));
        long previous=ExpenseListPeriod.groupStart("Selected Month",at(2026,10,23),now,month);
        assertEquals(ExpenseListPeriod.day(at(2026,10,19)),previous);
        assertEquals(ExpenseListPeriod.day(at(2026,10,23)),ExpenseListPeriod.groupEnd("Selected Month",previous,now,month));
        assertEquals(ExpenseListPeriod.day(now),ExpenseListPeriod.groupStart("Selected Month",now,now,month));
    }
    @Test public void dailyWeekGroupsDoNotMergeSeparateTransactionsDates() {
        long now=at(2026,10,24);
        assertEquals(ExpenseListPeriod.day(at(2026,10,23)),ExpenseListPeriod.groupStart("This Week",at(2026,10,23),now,now));
    }
    @Test public void dayBoundaryUsesCalendarAcrossDaylightSaving() {
        TimeZone previous=TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));long now=at(2026,3,8);
            ExpenseListPeriod.Range r=ExpenseListPeriod.range("Today",now,now,null,null);
            assertEquals(23L*60*60*1000,r.endExclusive-r.start);
            assertTrue(r.contains(now));assertFalse(r.contains(at(2026,3,9)));
        } finally {TimeZone.setDefault(previous);}
    }
}
