package com.example.moneymanagerpro.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.moneymanagerpro.dao.TransactionDao;
import com.example.moneymanagerpro.database.AppDatabase;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.CategoryTotal;
import com.example.moneymanagerpro.model.ExpenseItem;
import com.example.moneymanagerpro.model.Transaction;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TransactionRepository {

    private final AppDatabase database;
    private final TransactionDao transactionDao;
    private final ExecutorService executorService;
    private final Handler mainHandler;

    public interface OperationCallback {
        void onComplete();
    }

    public interface SummaryCallback {
        void onLoaded(double totalIncome, double totalExpense);
    }

    public interface TransactionListCallback {
        void onLoaded(List<Transaction> transactions);
    }

    public interface PeriodSummaryCallback {
        void onLoaded(double totalIncome, double totalExpense);
    }

    public interface CategoryTotalListCallback {
        void onLoaded(List<CategoryTotal> categoryTotals);
    }

    public TransactionRepository(Context context) {
        database = DatabaseClient.getInstance(context).getAppDatabase();

        transactionDao = database.transactionDao();
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public void insert(Transaction transaction, OperationCallback callback) {
        executorService.execute(() -> {
            transactionDao.insert(transaction);

            if (callback != null) {
                mainHandler.post(callback::onComplete);
            }
        });
    }

    public void insertTransfer(
            Transaction transferOut,
            Transaction transferIn,
            OperationCallback callback
    ) {
        executorService.execute(() -> {
            transactionDao.insert(transferOut);
            transactionDao.insert(transferIn);

            if (callback != null) {
                mainHandler.post(callback::onComplete);
            }
        });
    }

    public void update(Transaction transaction, OperationCallback callback) {
        executorService.execute(() -> {
            database.runInTransaction(() -> {
                transactionDao.update(transaction);

                if (!"EXPENSE".equalsIgnoreCase(
                        transaction.getType()
                )) {
                    database.expenseItemDao()
                            .deleteItemsForTransaction(
                                    transaction.getId()
                            );
                }
            });

            if (callback != null) {
                mainHandler.post(callback::onComplete);
            }
        });
    }

    public void updateWithExpenseItems(
            Transaction transaction,
            List<ExpenseItem> expenseItems,
            OperationCallback callback
    ) {
        executorService.execute(() -> {
            database.runInTransaction(() -> {
                transactionDao.update(transaction);
                database.expenseItemDao()
                        .deleteItemsForTransaction(
                                transaction.getId()
                        );

                if ("EXPENSE".equalsIgnoreCase(
                        transaction.getType()
                )
                        && expenseItems != null
                        && !expenseItems.isEmpty()) {
                    database.expenseItemDao()
                            .insertAll(expenseItems);
                }
            });

            if (callback != null) {
                mainHandler.post(callback::onComplete);
            }
        });
    }

    public void delete(Transaction transaction, OperationCallback callback) {
        executorService.execute(() -> {
            transactionDao.delete(transaction);

            if (callback != null) {
                mainHandler.post(callback::onComplete);
            }
        });
    }

    public void getAllTransactions(TransactionListCallback callback) {
        executorService.execute(() -> {
            List<Transaction> transactions = transactionDao.getAllTransactions();

            mainHandler.post(() -> callback.onLoaded(transactions));
        });
    }

    public void loadSummary(SummaryCallback callback) {
        executorService.execute(() -> {
            double totalIncome = transactionDao.getTotalAmountByType("INCOME");
            double totalExpense = transactionDao.getTotalAmountByType("EXPENSE");

            mainHandler.post(() ->
                    callback.onLoaded(totalIncome, totalExpense)
            );
        });
    }

    public void loadPeriodSummary(
            String startDate,
            String endDate,
            PeriodSummaryCallback callback
    ) {
        executorService.execute(() -> {
            double totalIncome = transactionDao.getTotalAmountByTypeForPeriod(
                    "INCOME",
                    startDate,
                    endDate
            );

            double totalExpense = transactionDao.getTotalAmountByTypeForPeriod(
                    "EXPENSE",
                    startDate,
                    endDate
            );

            mainHandler.post(() ->
                    callback.onLoaded(totalIncome, totalExpense)
            );
        });
    }

    public void loadCategoryTotals(
            String type,
            String startDate,
            String endDate,
            CategoryTotalListCallback callback
    ) {
        executorService.execute(() -> {
            List<CategoryTotal> categoryTotals =
                    transactionDao.getCategoryTotalsForPeriod(
                            type,
                            startDate,
                            endDate
                    );

            mainHandler.post(() -> callback.onLoaded(categoryTotals));
        });
    }
}
