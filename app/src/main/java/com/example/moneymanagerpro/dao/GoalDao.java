package com.example.moneymanagerpro.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.moneymanagerpro.model.Goal;

import java.util.List;

@Dao
public interface GoalDao {

    @Insert
    long insert(Goal goal);

    @Update
    void update(Goal goal);

    @Delete
    void delete(Goal goal);

    @Query("SELECT * FROM goals ORDER BY id DESC")
    List<Goal> getAllGoals();
}