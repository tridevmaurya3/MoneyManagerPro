package com.example.moneymanagerpro.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.moneymanagerpro.model.CreditCard;

import java.util.List;

@Dao
public interface CreditCardDao {

    @Insert
    long insert(CreditCard creditCard);

    @Update
    void update(CreditCard creditCard);

    @Query("SELECT * FROM credit_cards WHERE active = 1 ORDER BY name ASC")
    List<CreditCard> getActiveCreditCards();

    @Query("SELECT * FROM credit_cards ORDER BY id ASC")
    List<CreditCard> getAllCreditCards();

    @Query("SELECT * FROM credit_cards WHERE accountName = :accountName LIMIT 1")
    CreditCard findByAccountName(String accountName);
}
