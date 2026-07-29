package com.example.moneymanagerpro.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.moneymanagerpro.model.ExpenseItem;

import java.util.List;

@Dao
public interface ExpenseItemDao {

    @Insert
    void insertAll(List<ExpenseItem> expenseItems);

    @Query(
            "SELECT * FROM expense_items " +
                    "WHERE transactionId = :transactionId " +
                    "ORDER BY sortOrder ASC, id ASC"
    )
    List<ExpenseItem> getItemsForTransaction(int transactionId);

    @Query(
            "SELECT * FROM expense_items " +
                    "ORDER BY transactionId ASC, sortOrder ASC, id ASC"
    )
    List<ExpenseItem> getAllExpenseItems();

    @Query("DELETE FROM expense_items WHERE transactionId = :transactionId")
    void deleteItemsForTransaction(int transactionId);
}
