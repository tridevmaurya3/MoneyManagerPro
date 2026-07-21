package com.example.moneymanagerpro.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "goals")
public class Goal {

    @PrimaryKey(autoGenerate = true)
    private int id;

    @NonNull
    private String name = "";

    private double targetAmount;
    private double savedAmount;

    @NonNull
    private String targetDate = "";

    @NonNull
    private String color = "#6C63FF";

    public Goal() {
    }

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

    public double getTargetAmount() {
        return targetAmount;
    }

    public void setTargetAmount(double targetAmount) {
        this.targetAmount = targetAmount;
    }

    public double getSavedAmount() {
        return savedAmount;
    }

    public void setSavedAmount(double savedAmount) {
        this.savedAmount = savedAmount;
    }

    @NonNull
    public String getTargetDate() {
        return targetDate;
    }

    public void setTargetDate(@NonNull String targetDate) {
        this.targetDate = targetDate;
    }

    @NonNull
    public String getColor() {
        return color;
    }

    public void setColor(@NonNull String color) {
        this.color = color;
    }
}