package com.example.moneymanagerpro.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.moneymanagerpro.model.CreditCardPayment;

import java.util.List;

@Dao
public interface CreditCardPaymentDao {

    @Insert
    long insert(CreditCardPayment payment);

    @Query("SELECT * FROM credit_card_payments ORDER BY id ASC")
    List<CreditCardPayment> getAllPayments();

    @Query("SELECT * FROM credit_card_payments WHERE creditCardId = :creditCardId ORDER BY paymentDate DESC, id DESC")
    List<CreditCardPayment> getPaymentsForCard(int creditCardId);

    @Query("SELECT COALESCE(SUM(amount), 0) FROM credit_card_payments WHERE creditCardId = :creditCardId AND statementEndDate = :statementEndDate")
    double getPaidForStatement(
            int creditCardId,
            String statementEndDate
    );
}
