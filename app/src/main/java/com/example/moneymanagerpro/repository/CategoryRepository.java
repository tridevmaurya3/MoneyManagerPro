package com.example.moneymanagerpro.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.moneymanagerpro.dao.CategoryDao;
import com.example.moneymanagerpro.database.AppDatabase;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Category;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CategoryRepository {

    private final CategoryDao categoryDao;
    private final ExecutorService executorService;
    private final Handler mainHandler;

    public interface CategoryListCallback {
        void onLoaded(List<Category> categories);
    }

    public interface OperationCallback {
        void onComplete();
    }

    public CategoryRepository(Context context) {
        AppDatabase database = DatabaseClient.getInstance(context).getAppDatabase();

        categoryDao = database.categoryDao();
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public void insert(Category category, OperationCallback callback) {
        executorService.execute(() -> {
            categoryDao.insert(category);

            if (callback != null) {
                mainHandler.post(callback::onComplete);
            }
        });
    }

    public void update(Category category, OperationCallback callback) {
        executorService.execute(() -> {
            categoryDao.update(category);

            if (callback != null) {
                mainHandler.post(callback::onComplete);
            }
        });
    }

    public void delete(Category category, OperationCallback callback) {
        executorService.execute(() -> {
            categoryDao.delete(category);

            if (callback != null) {
                mainHandler.post(callback::onComplete);
            }
        });
    }

    public void getAllCategories(CategoryListCallback callback) {
        executorService.execute(() -> {
            List<Category> categories = categoryDao.getAllCategories();

            mainHandler.post(() -> callback.onLoaded(categories));
        });
    }

    public void getCategoriesByType(
            String type,
            CategoryListCallback callback
    ) {
        executorService.execute(() -> {
            List<Category> categories = categoryDao.getCategoriesByType(type);

            mainHandler.post(() -> callback.onLoaded(categories));
        });
    }
}