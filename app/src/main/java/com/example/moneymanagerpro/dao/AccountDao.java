package com.example.moneymanagerpro.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.moneymanagerpro.model.Account;
import com.example.moneymanagerpro.model.AccountBalance;

import java.util.List;

@Dao
public interface AccountDao {

    @Insert
    void insert(Account account);

    @Update
    void update(Account account);

    @Delete
    void delete(Account account);

    @Query("SELECT * FROM accounts WHERE archived = 0 ORDER BY name ASC")
    List<Account> getAllAccounts();

    @Query("SELECT * FROM accounts ORDER BY archived ASC, name ASC")
    List<Account> getAllAccountsIncludingArchived();

    @Query("SELECT COUNT(*) FROM accounts WHERE archived = 0")
    int getAccountCount();

    @Query("SELECT * FROM accounts WHERE name = :accountName AND archived = 0 LIMIT 1")
    Account findByName(String accountName);

    @Query("SELECT " +
            "a.id AS id, " +
            "a.name AS name, " +
            "a.type AS type, " +
            "a.openingBalance AS openingBalance, " +
            "a.color AS color, " +
            "(a.openingBalance + COALESCE(SUM(" +
            "CASE " +
            "WHEN t.type = 'INCOME' THEN t.amount " +
            "WHEN t.type = 'TRANSFER_IN' THEN t.amount " +
            "WHEN t.type = 'EXPENSE' THEN -t.amount " +
            "WHEN t.type = 'TRANSFER_OUT' THEN -t.amount " +
            "ELSE 0 END" +
            "), 0)) AS currentBalance " +
            "FROM accounts AS a " +
            "LEFT JOIN transactions AS t ON a.name = t.account " +
            "WHERE a.archived = 0 " +
            "GROUP BY a.id, a.name, a.type, a.openingBalance, a.color " +
            "ORDER BY a.name ASC")
    List<AccountBalance> getAccountBalances();
}
