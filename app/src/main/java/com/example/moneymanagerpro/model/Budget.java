package com.example.moneymanagerpro.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "category_budgets")
public class Budget {

    @PrimaryKey(autoGenerate = true)
    private int id;

    @NonNull
    private String category = "";

    @NonNull
    private String period = "Monthly";

    private double limitAmount;

    public Budget() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @NonNull
    public String getCategory() {
        return category;
    }

    public void setCategory(@NonNull String category) {
        this.category = category;
    }

    @NonNull
    public String getPeriod() {
        return period;
    }

    public void setPeriod(@NonNull String period) {
        this.period = period;
    }

    public double getLimitAmount() {
        return limitAmount;
    }

    public void setLimitAmount(double limitAmount) {
        this.limitAmount = limitAmount;
    }
}