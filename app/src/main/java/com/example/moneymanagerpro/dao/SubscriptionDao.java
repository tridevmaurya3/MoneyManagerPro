package com.example.moneymanagerpro.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.moneymanagerpro.model.Subscription;

import java.util.List;

@Dao
public interface SubscriptionDao {

    @Insert
    void insert(Subscription subscription);

    @Update
    void update(Subscription subscription);

    @Delete
    void delete(Subscription subscription);

    @Query("SELECT * FROM subscriptions ORDER BY nextDueDate ASC")
    List<Subscription> getAllSubscriptions();

    @Query("SELECT * FROM subscriptions WHERE active = 1 ORDER BY nextDueDate ASC")
    List<Subscription> getActiveSubscriptions();

    @Query("SELECT * FROM subscriptions WHERE active = 1 AND nextDueDate <= :date ORDER BY nextDueDate ASC")
    List<Subscription> getDueSubscriptions(String date);
}