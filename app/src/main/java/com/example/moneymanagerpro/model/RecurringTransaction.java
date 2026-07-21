package com.example.moneymanagerpro.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "recurring_transactions")
public class RecurringTransaction {

    @PrimaryKey(autoGenerate = true)
    private int id;

    @NonNull
    private String type = "";

    private double amount;

    @NonNull
    private String category = "";

    @NonNull
    private String account = "Cash";

    @NonNull
    private String note = "";

    @NonNull
    private String frequency = "Monthly";

    @NonNull
    private String startDate = "";

    @NonNull
    private String nextRunDate = "";

    private boolean active = true;

    public RecurringTransaction() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @NonNull
    public String getType() {
        return type;
    }

    public void setType(@NonNull String type) {
        this.type = type;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    @NonNull
    public String getCategory() {
        return category;
    }

    public void setCategory(@NonNull String category) {
        this.category = category;
    }

    @NonNull
    public String getAccount() {
        return account;
    }

    public void setAccount(@NonNull String account) {
        this.account = account;
    }

    @NonNull
    public String getNote() {
        return note;
    }

    public void setNote(@NonNull String note) {
        this.note = note;
    }

    @NonNull
    public String getFrequency() {
        return frequency;
    }

    public void setFrequency(@NonNull String frequency) {
        this.frequency = frequency;
    }

    @NonNull
    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(@NonNull String startDate) {
        this.startDate = startDate;
    }

    @NonNull
    public String getNextRunDate() {
        return nextRunDate;
    }

    public void setNextRunDate(@NonNull String nextRunDate) {
        this.nextRunDate = nextRunDate;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}