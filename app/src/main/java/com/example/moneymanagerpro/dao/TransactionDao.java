package com.example.moneymanagerpro.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.moneymanagerpro.model.CategoryTotal;
import com.example.moneymanagerpro.model.Transaction;

import java.util.List;

@Dao
public interface TransactionDao {

    @Insert
    long insert(Transaction transaction);

    @Update
    void update(Transaction transaction);

    @Delete
    void delete(Transaction transaction);

    @Query("SELECT * FROM transactions ORDER BY id DESC")
    List<Transaction> getAllTransactions();

    @Query("SELECT COALESCE(SUM(CASE " +
            "WHEN type = 'EXPENSE' THEN amount " +
            "WHEN type = 'INCOME' THEN -amount " +
            "ELSE 0 END), 0) " +
            "FROM transactions " +
            "WHERE account = :account " +
            "AND date BETWEEN :startDate AND :endDate")
    double getNetCardSpendForPeriod(
            String account,
            String startDate,
            String endDate
    );

    @Query("SELECT COALESCE(SUM(amount), 0) " +
            "FROM transactions WHERE type = :type")
    double getTotalAmountByType(String type);

    @Query("SELECT COALESCE(SUM(amount), 0) " +
            "FROM transactions " +
            "WHERE type = :type " +
            "AND date BETWEEN :startDate AND :endDate")
    double getTotalAmountByTypeForPeriod(
            String type,
            String startDate,
            String endDate
    );

    @Query("SELECT category AS category, " +
            "COALESCE(SUM(amount), 0) AS total " +
            "FROM transactions " +
            "WHERE type = :type " +
            "AND date BETWEEN :startDate AND :endDate " +
            "GROUP BY category " +
            "ORDER BY total DESC")
    List<CategoryTotal> getCategoryTotalsForPeriod(
            String type,
            String startDate,
            String endDate
    );

    @Query("SELECT COALESCE(SUM(amount), 0) " +
            "FROM transactions " +
            "WHERE type = 'EXPENSE' " +
            "AND category = :category " +
            "AND date BETWEEN :startDate AND :endDate")
    double getExpenseTotalForCategoryPeriod(
            String category,
            String startDate,
            String endDate
    );
}
