package com.example.moneymanagerpro.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.moneymanagerpro.model.Budget;

import java.util.List;

@Dao
public interface BudgetDao {

    @Insert
    long insert(Budget budget);

    @Update
    void update(Budget budget);

    @Delete
    void delete(Budget budget);

    @Query("SELECT * FROM category_budgets ORDER BY category ASC")
    List<Budget> getAllBudgets();

    @Query("SELECT * FROM category_budgets " +
            "WHERE category = :category AND period = :period LIMIT 1")
    Budget getBudgetForCategory(String category, String period);
}