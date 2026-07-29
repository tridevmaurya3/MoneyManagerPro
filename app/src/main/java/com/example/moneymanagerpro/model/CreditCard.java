package com.example.moneymanagerpro.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "credit_cards")
public class CreditCard {

    @PrimaryKey(autoGenerate = true)
    private int id;

    @NonNull
    private String name = "";

    @NonNull
    private String lastFour = "";

    @NonNull
    private String accountName = "";

    private double creditLimit;
    private int billingDay;
    private int dueDay;

    @NonNull
    private String paymentAccount = "Cash";

    private int reminderDays = 3;
    private boolean active = true;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public void setName(@NonNull String name) {
        this.name = name;
    }

    @NonNull
    public String getLastFour() {
        return lastFour;
    }

    public void setLastFour(@NonNull String lastFour) {
        this.lastFour = lastFour;
    }

    @NonNull
    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(@NonNull String accountName) {
        this.accountName = accountName;
    }

    public double getCreditLimit() {
        return creditLimit;
    }

    public void setCreditLimit(double creditLimit) {
        this.creditLimit = creditLimit;
    }

    public int getBillingDay() {
        return billingDay;
    }

    public void setBillingDay(int billingDay) {
        this.billingDay = billingDay;
    }

    public int getDueDay() {
        return dueDay;
    }

    public void setDueDay(int dueDay) {
        this.dueDay = dueDay;
    }

    @NonNull
    public String getPaymentAccount() {
        return paymentAccount;
    }

    public void setPaymentAccount(
            @NonNull String paymentAccount
    ) {
        this.paymentAccount = paymentAccount;
    }

    public int getReminderDays() {
        return reminderDays;
    }

    public void setReminderDays(int reminderDays) {
        this.reminderDays = reminderDays;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
