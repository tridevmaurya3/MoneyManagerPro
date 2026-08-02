package com.example.moneymanagerpro.budget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.moneymanagerpro.model.Transaction;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class AiBudgetPlannerEngineTest {

    private final AiBudgetPlannerEngine engine = new AiBudgetPlannerEngine();

    @Test
    public void buildsMonthlyAveragesFromCurrentThreeCalendarMonths() {
        List<Transaction> transactions = new ArrayList<>();
        transactions.add(transaction("INCOME", 30000, "Salary", monthsAgo(0)));
        transactions.add(transaction("INCOME", 30000, "Salary", monthsAgo(1)));
        transactions.add(transaction("INCOME", 30000, "Salary", monthsAgo(2)));
        transactions.add(transaction("EXPENSE", 3000, "Food", monthsAgo(0)));
        transactions.add(transaction("EXPENSE", 3000, "Food", monthsAgo(1)));
        transactions.add(transaction("EXPENSE", 3000, "Food", monthsAgo(2)));

        AiBudgetPlannerEngine.Plan plan = engine.buildPlan(transactions);

        assertEquals(30000.0, plan.getAverageMonthlyIncome(), 0.01);
        assertEquals(3000.0, plan.getAverageMonthlyExpense(), 0.01);
        assertEquals(6000.0, plan.getTargetSaving(), 0.01);
        assertEquals(6, plan.getAnalysedTransactionCount());
        assertEquals(1, plan.getSuggestions().size());
        assertEquals("Food", plan.getSuggestions().get(0).getCategory());
    }

    @Test
    public void excludesTransactionsOlderThanThreeCalendarMonths() {
        List<Transaction> transactions = new ArrayList<>();
        transactions.add(transaction("EXPENSE", 9000, "Old Expense", monthsAgo(3)));
        transactions.add(transaction("EXPENSE", 1500, "Current Expense", monthsAgo(0)));

        AiBudgetPlannerEngine.Plan plan = engine.buildPlan(transactions);

        assertEquals(500.0, plan.getAverageMonthlyExpense(), 0.01);
        assertEquals(1, plan.getAnalysedTransactionCount());
        assertEquals(1, plan.getSuggestions().size());
        assertEquals("Current Expense", plan.getSuggestions().get(0).getCategory());
    }

    @Test
    public void ignoresTransfersInvalidAmountsAndMissingDates() {
        List<Transaction> transactions = new ArrayList<>();
        transactions.add(transaction("TRANSFER_OUT", 5000, "Transfer", monthsAgo(0)));
        transactions.add(transaction("EXPENSE", -1, "Invalid", ""));
        transactions.add(transaction("EXPENSE", Double.NaN, "Invalid", monthsAgo(0)));

        AiBudgetPlannerEngine.Plan plan = engine.buildPlan(transactions);

        assertEquals(0, plan.getAnalysedTransactionCount());
        assertTrue(plan.getSuggestions().isEmpty());
    }

    private Transaction transaction(
            String type,
            double amount,
            String category,
            String date
    ) {
        Transaction transaction = new Transaction();
        transaction.setType(type);
        transaction.setAmount(amount);
        transaction.setCategory(category);
        transaction.setDate(date);
        transaction.setAccount("Cash");
        transaction.setNote("");
        return transaction;
    }

    private String monthsAgo(int months) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -months);
        calendar.set(Calendar.DAY_OF_MONTH, 15);
        return new SimpleDateFormat(
                "yyyy-MM-dd HH:mm",
                Locale.US
        ).format(calendar.getTime());
    }
}
