package com.example.moneymanagerpro.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "subscriptions")
public class Subscription {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private String name;
    private double amount;
    private String billingCycle;
    private String nextDueDate;
    private String account;
    private String category;
    private int remindDays;
    private String note;
    private boolean active;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getBillingCycle() {
        return billingCycle;
    }

    public void setBillingCycle(String billingCycle) {
        this.billingCycle = billingCycle;
    }

    public String getNextDueDate() {
        return nextDueDate;
    }

    public void setNextDueDate(String nextDueDate) {
        this.nextDueDate = nextDueDate;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public int getRemindDays() {
        return remindDays;
    }

    public void setRemindDays(int remindDays) {
        this.remindDays = remindDays;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}