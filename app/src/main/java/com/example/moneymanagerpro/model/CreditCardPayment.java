package com.example.moneymanagerpro.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "credit_card_payments")
public class CreditCardPayment {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private int creditCardId;

    @NonNull
    private String statementEndDate = "";

    private double amount;

    @NonNull
    private String paymentDate = "";

    @NonNull
    private String sourceAccount = "Cash";

    @NonNull
    private String note = "";

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getCreditCardId() {
        return creditCardId;
    }

    public void setCreditCardId(int creditCardId) {
        this.creditCardId = creditCardId;
    }

    @NonNull
    public String getStatementEndDate() {
        return statementEndDate;
    }

    public void setStatementEndDate(
            @NonNull String statementEndDate
    ) {
        this.statementEndDate = statementEndDate;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    @NonNull
    public String getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(
            @NonNull String paymentDate
    ) {
        this.paymentDate = paymentDate;
    }

    @NonNull
    public String getSourceAccount() {
        return sourceAccount;
    }

    public void setSourceAccount(
            @NonNull String sourceAccount
    ) {
        this.sourceAccount = sourceAccount;
    }

    @NonNull
    public String getNote() {
        return note;
    }

    public void setNote(@NonNull String note) {
        this.note = note;
    }
}
