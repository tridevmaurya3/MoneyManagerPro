package com.example.moneymanagerpro.model;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "loans")
public class Loan {

    @PrimaryKey(autoGenerate = true)
    private int id;

    @NonNull
    private String personName = "";

    @NonNull
    private String loanType = "Loan Taken";

    // Total amount that must be repaid or received.
    private double totalAmount;

    // This stays as the live remaining balance.
    private double outstandingAmount;

    private double interestRate;
    private double emiAmount;

    @NonNull
    private String dueDate = "";

    @NonNull
    private String note = "";

    private boolean active = true;

    // Smart tracker fields.
    @NonNull
    @ColumnInfo(defaultValue = "''")
    private String startDate = "";

    @ColumnInfo(defaultValue = "0")
    private int tenureMonths;

    // Amount paid before the user started tracking this loan in the app.
    // This amount never creates a current expense transaction.
    @ColumnInfo(defaultValue = "0")
    private double historicalPaidAmount;

    @ColumnInfo(defaultValue = "0")
    private int historicalInstallments;

    public Loan() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @NonNull
    public String getPersonName() {
        return personName;
    }

    public void setPersonName(@NonNull String personName) {
        this.personName = personName;
    }

    @NonNull
    public String getLoanType() {
        return loanType;
    }

    public void setLoanType(@NonNull String loanType) {
        this.loanType = loanType;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public double getOutstandingAmount() {
        return outstandingAmount;
    }

    public void setOutstandingAmount(double outstandingAmount) {
        this.outstandingAmount = outstandingAmount;
    }

    public double getInterestRate() {
        return interestRate;
    }

    public void setInterestRate(double interestRate) {
        this.interestRate = interestRate;
    }

    public double getEmiAmount() {
        return emiAmount;
    }

    public void setEmiAmount(double emiAmount) {
        this.emiAmount = emiAmount;
    }

    @NonNull
    public String getDueDate() {
        return dueDate;
    }

    public void setDueDate(@NonNull String dueDate) {
        this.dueDate = dueDate;
    }

    @NonNull
    public String getNote() {
        return note;
    }

    public void setNote(@NonNull String note) {
        this.note = note;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @NonNull
    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(@NonNull String startDate) {
        this.startDate = startDate;
    }

    public int getTenureMonths() {
        return tenureMonths;
    }

    public void setTenureMonths(int tenureMonths) {
        this.tenureMonths = tenureMonths;
    }

    public double getHistoricalPaidAmount() {
        return historicalPaidAmount;
    }

    public void setHistoricalPaidAmount(double historicalPaidAmount) {
        this.historicalPaidAmount = historicalPaidAmount;
    }

    public int getHistoricalInstallments() {
        return historicalInstallments;
    }

    public void setHistoricalInstallments(int historicalInstallments) {
        this.historicalInstallments = historicalInstallments;
    }
}