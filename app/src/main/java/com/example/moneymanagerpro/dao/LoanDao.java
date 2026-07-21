package com.example.moneymanagerpro.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.moneymanagerpro.model.Loan;

import java.util.List;

@Dao
public interface LoanDao {

    @Insert
    long insert(Loan loan);

    @Update
    void update(Loan loan);

    @Delete
    void delete(Loan loan);

    @Query("SELECT * FROM loans ORDER BY active DESC, dueDate ASC")
    List<Loan> getAllLoans();

    @Query("SELECT * FROM loans WHERE active = 1 ORDER BY dueDate ASC")
    List<Loan> getActiveLoans();
}