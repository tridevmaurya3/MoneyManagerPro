package com.example.moneymanagerpro.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "loan_payments")
public class LoanPayment {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private int loanId;
    private double amount;

    @NonNull
    private String paymentType = "EMI";

    @NonNull
    private String account = "Cash";

    @NonNull
    private String paymentDate = "";

    @NonNull
    private String note = "";

    public LoanPayment() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getLoanId() {
        return loanId;
    }

    public void setLoanId(int loanId) {
        this.loanId = loanId;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    @NonNull
    public String getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(@NonNull String paymentType) {
        this.paymentType = paymentType;
    }

    @NonNull
    public String getAccount() {
        return account;
    }

    public void setAccount(@NonNull String account) {
        this.account = account;
    }

    @NonNull
    public String getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(@NonNull String paymentDate) {
        this.paymentDate = paymentDate;
    }

    @NonNull
    public String getNote() {
        return note;
    }

    public void setNote(@NonNull String note) {
        this.note = note;
    }
}