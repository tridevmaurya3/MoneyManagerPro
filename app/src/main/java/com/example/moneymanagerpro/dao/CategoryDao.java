package com.example.moneymanagerpro.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.moneymanagerpro.model.Category;

import java.util.List;

@Dao
public interface CategoryDao {

    @Insert
    void insert(Category category);

    @Update
    void update(Category category);

    @Delete
    void delete(Category category);

    @Query("SELECT * FROM categories ORDER BY type ASC, name ASC")
    List<Category> getAllCategories();

    @Query("SELECT * FROM categories WHERE type = :type ORDER BY name ASC")
    List<Category> getCategoriesByType(String type);

    @Query("SELECT COUNT(*) FROM categories")
    int getCategoryCount();
}