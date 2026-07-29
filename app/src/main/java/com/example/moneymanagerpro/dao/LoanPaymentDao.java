package com.example.moneymanagerpro.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.moneymanagerpro.model.LoanPayment;

import java.util.List;

@Dao
public interface LoanPaymentDao {

    @Insert
    long insert(LoanPayment payment);

    @Query("SELECT * FROM loan_payments ORDER BY id ASC")
    List<LoanPayment> getAllLoanPayments();

    @Query("SELECT * FROM loan_payments WHERE loanId = :loanId ORDER BY paymentDate DESC, id DESC")
    List<LoanPayment> getPaymentsForLoan(int loanId);

    @Query("SELECT COALESCE(SUM(amount), 0) FROM loan_payments WHERE loanId = :loanId")
    double getTotalPaidForLoan(int loanId);

    @Query("SELECT COALESCE(SUM(amount), 0) FROM loan_payments WHERE loanId = :loanId AND paymentType = 'EXTRA'")
    double getExtraPaidForLoan(int loanId);
}
