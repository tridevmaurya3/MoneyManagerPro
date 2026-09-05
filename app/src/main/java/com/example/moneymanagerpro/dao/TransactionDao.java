package com.example.moneymanagerpro.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.moneymanagerpro.model.CategoryTotal;
import com.example.moneymanagerpro.model.Transaction;

import java.util.List;

@Dao
public interface TransactionDao {

    @Insert
    long insert(Transaction transaction);

    @Update
    void update(Transaction transaction);

    @Delete
    void delete(Transaction transaction);

    @Query("SELECT * FROM transactions ORDER BY id DESC")
    List<Transaction> getAllTransactions();

    /**
     * Updates only the exact integration-owned Family Hub row. The marker and
     * provenance guards intentionally prevent any manual/reconciled MoneyManager
     * transaction from being rewritten by a companion app edit.
     */
    @Query("UPDATE transactions SET amount = :amount, type = :type, "
            + "category = :category, account = :account, date = :date, note = :note "
            + "WHERE id = :transactionId "
            + "AND instr(note, :marker) > 0 "
            + "AND instr(note, 'Synced from Family Hub') > 0")
    int updateLinkedFamilyHubTransaction(
            long transactionId,
            double amount,
            String type,
            String category,
            String account,
            String date,
            String note,
            String marker
    );

    @Query("SELECT COALESCE(SUM(CASE " +
            "WHEN type = 'EXPENSE' THEN amount " +
            "WHEN type = 'INCOME' THEN -amount " +
            "ELSE 0 END), 0) " +
            "FROM transactions " +
            "WHERE account = :account " +
            "AND date BETWEEN :startDate AND :endDate")
    double getNetCardSpendForPeriod(
            String account,
            String startDate,
            String endDate
    );

    @Query("SELECT COALESCE(SUM(CASE " +
            "WHEN type = 'EXPENSE' THEN amount " +
            "WHEN type = 'INCOME' THEN -amount " +
            "ELSE 0 END), 0) " +
            "FROM transactions " +
            "WHERE (account IN (:accounts) " +
            "OR LOWER(TRIM(category)) = " +
            "LOWER(TRIM(:cardCategory))) " +
            "AND date BETWEEN :startDate AND :endDate")
    double getNetCardSpendForPeriodFromSources(
            List<String> accounts,
            String cardCategory,
            String startDate,
            String endDate
    );

    @Query("SELECT COALESCE(SUM(CASE " +
            "WHEN type = 'EXPENSE' THEN amount " +
            "WHEN type = 'INCOME' THEN -amount " +
            "ELSE 0 END), 0) " +
            "FROM transactions " +
            "WHERE account NOT IN (:accounts) " +
            "AND LOWER(TRIM(category)) = " +
            "LOWER(TRIM(:cardCategory))")
    double getNetCardCategorySpendOutsideAccounts(
            List<String> accounts,
            String cardCategory
    );

    @Query("SELECT COUNT(*) FROM transactions " +
            "WHERE type IN ('EXPENSE', 'INCOME') " +
            "AND (account IN (:accounts) " +
            "OR LOWER(TRIM(category)) = " +
            "LOWER(TRIM(:cardCategory)))")
    int getCardTransactionCountFromSources(
            List<String> accounts,
            String cardCategory
    );

    @Query("SELECT COALESCE(SUM(amount), 0) " +
            "FROM transactions WHERE type = :type")
    double getTotalAmountByType(String type);

    @Query("SELECT COALESCE(SUM(amount), 0) " +
            "FROM transactions " +
            "WHERE type = :type " +
            "AND date BETWEEN :startDate AND :endDate")
    double getTotalAmountByTypeForPeriod(
            String type,
            String startDate,
            String endDate
    );

    @Query("SELECT category AS category, " +
            "COALESCE(SUM(amount), 0) AS total " +
            "FROM transactions " +
            "WHERE type = :type " +
            "AND date BETWEEN :startDate AND :endDate " +
            "GROUP BY category " +
            "ORDER BY total DESC")
    List<CategoryTotal> getCategoryTotalsForPeriod(
            String type,
            String startDate,
            String endDate
    );

    /**
     * Budget matching is intentionally source-agnostic. A budget keyword can
     * match the expense category, payment account, transaction note/merchant,
     * or any attached expense item's name/unit. EXISTS keeps one transaction
     * from being counted more than once when several item rows match.
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) " +
            "FROM transactions AS t " +
            "WHERE UPPER(TRIM(t.type)) = 'EXPENSE' " +
            "AND t.date BETWEEN :startDate AND :endDate " +
            "AND (" +
            "instr(LOWER(COALESCE(t.category, '')), LOWER(TRIM(:category))) > 0 " +
            "OR instr(LOWER(COALESCE(t.account, '')), LOWER(TRIM(:category))) > 0 " +
            "OR instr(LOWER(COALESCE(t.note, '')), LOWER(TRIM(:category))) > 0 " +
            "OR EXISTS (" +
            "SELECT 1 FROM expense_items AS ei " +
            "WHERE ei.transactionId = t.id " +
            "AND (" +
            "instr(LOWER(COALESCE(ei.itemName, '')), LOWER(TRIM(:category))) > 0 " +
            "OR instr(LOWER(COALESCE(ei.unit, '')), LOWER(TRIM(:category))) > 0" +
            ")" +
            ")" +
            ")")
    double getExpenseTotalForCategoryPeriod(
            String category,
            String startDate,
            String endDate
    );
}
