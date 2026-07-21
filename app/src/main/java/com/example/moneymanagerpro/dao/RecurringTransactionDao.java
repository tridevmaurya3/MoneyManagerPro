package com.example.moneymanagerpro.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.moneymanagerpro.model.RecurringTransaction;

import java.util.List;

@Dao
public interface RecurringTransactionDao {

    @Insert
    long insert(RecurringTransaction recurringTransaction);

    @Update
    void update(RecurringTransaction recurringTransaction);

    @Delete
    void delete(RecurringTransaction recurringTransaction);

    @Query("SELECT * FROM recurring_transactions ORDER BY id DESC")
    List<RecurringTransaction> getAllRecurringTransactions();

    @Query("SELECT * FROM recurring_transactions " +
            "WHERE active = 1 AND nextRunDate <= :currentDate")
    List<RecurringTransaction> getDueRecurringTransactions(
            String currentDate
    );
}