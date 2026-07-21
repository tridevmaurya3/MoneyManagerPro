package com.example.moneymanagerpro.database;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.moneymanagerpro.dao.AccountDao;
import com.example.moneymanagerpro.dao.BudgetDao;
import com.example.moneymanagerpro.dao.CategoryDao;
import com.example.moneymanagerpro.dao.GoalDao;
import com.example.moneymanagerpro.dao.LoanDao;
import com.example.moneymanagerpro.dao.LoanPaymentDao;
import com.example.moneymanagerpro.dao.RecurringTransactionDao;
import com.example.moneymanagerpro.dao.SubscriptionDao;
import com.example.moneymanagerpro.dao.TransactionDao;
import com.example.moneymanagerpro.model.Account;
import com.example.moneymanagerpro.model.Budget;
import com.example.moneymanagerpro.model.Category;
import com.example.moneymanagerpro.model.Goal;
import com.example.moneymanagerpro.model.Loan;
import com.example.moneymanagerpro.model.LoanPayment;
import com.example.moneymanagerpro.model.RecurringTransaction;
import com.example.moneymanagerpro.model.Subscription;
import com.example.moneymanagerpro.model.Transaction;

@Database(
        entities = {
                Transaction.class,
                Category.class,
                Account.class,
                Goal.class,
                RecurringTransaction.class,
                Budget.class,
                Loan.class,
                Subscription.class,
                LoanPayment.class
        },
        version = 9,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    public abstract TransactionDao transactionDao();

    public abstract CategoryDao categoryDao();

    public abstract AccountDao accountDao();

    public abstract GoalDao goalDao();

    public abstract RecurringTransactionDao recurringTransactionDao();

    public abstract BudgetDao budgetDao();

    public abstract LoanDao loanDao();

    public abstract LoanPaymentDao loanPaymentDao();

    public abstract SubscriptionDao subscriptionDao();

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `categories` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT, " +
                            "`type` TEXT, " +
                            "`color` TEXT)"
            );
        }
    };

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `accounts` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT, " +
                            "`type` TEXT, " +
                            "`openingBalance` REAL NOT NULL, " +
                            "`color` TEXT)"
            );

            database.execSQL(
                    "ALTER TABLE `transactions` " +
                            "ADD COLUMN `account` TEXT NOT NULL DEFAULT 'Cash'"
            );

            database.execSQL(
                    "INSERT INTO `accounts` (`name`, `type`, `openingBalance`, `color`) " +
                            "VALUES ('Cash', 'Cash', 0, '#1565C0')"
            );
        }
    };

    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `goals` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`targetAmount` REAL NOT NULL, " +
                            "`savedAmount` REAL NOT NULL, " +
                            "`targetDate` TEXT NOT NULL, " +
                            "`color` TEXT NOT NULL)"
            );
        }
    };

    public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recurring_transactions` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`type` TEXT NOT NULL, " +
                            "`amount` REAL NOT NULL, " +
                            "`category` TEXT NOT NULL, " +
                            "`account` TEXT NOT NULL, " +
                            "`note` TEXT NOT NULL, " +
                            "`frequency` TEXT NOT NULL, " +
                            "`startDate` TEXT NOT NULL, " +
                            "`nextRunDate` TEXT NOT NULL, " +
                            "`active` INTEGER NOT NULL)"
            );
        }
    };

    public static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `category_budgets` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`category` TEXT NOT NULL, " +
                            "`period` TEXT NOT NULL, " +
                            "`limitAmount` REAL NOT NULL)"
            );
        }
    };

    public static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `loans` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`personName` TEXT NOT NULL, " +
                            "`loanType` TEXT NOT NULL, " +
                            "`totalAmount` REAL NOT NULL, " +
                            "`outstandingAmount` REAL NOT NULL, " +
                            "`interestRate` REAL NOT NULL, " +
                            "`emiAmount` REAL NOT NULL, " +
                            "`dueDate` TEXT NOT NULL, " +
                            "`note` TEXT NOT NULL, " +
                            "`active` INTEGER NOT NULL)"
            );
        }
    };

    public static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `subscriptions` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT, " +
                            "`amount` REAL NOT NULL, " +
                            "`billingCycle` TEXT, " +
                            "`nextDueDate` TEXT, " +
                            "`account` TEXT, " +
                            "`category` TEXT, " +
                            "`remindDays` INTEGER NOT NULL, " +
                            "`note` TEXT, " +
                            "`active` INTEGER NOT NULL)"
            );
        }
    };

    public static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "ALTER TABLE `loans` " +
                            "ADD COLUMN `startDate` TEXT NOT NULL DEFAULT ''"
            );

            database.execSQL(
                    "ALTER TABLE `loans` " +
                            "ADD COLUMN `tenureMonths` INTEGER NOT NULL DEFAULT 0"
            );

            database.execSQL(
                    "ALTER TABLE `loans` " +
                            "ADD COLUMN `historicalPaidAmount` REAL NOT NULL DEFAULT 0"
            );

            database.execSQL(
                    "ALTER TABLE `loans` " +
                            "ADD COLUMN `historicalInstallments` INTEGER NOT NULL DEFAULT 0"
            );

            database.execSQL(
                    "UPDATE `loans` SET `historicalPaidAmount` = " +
                            "CASE " +
                            "WHEN `totalAmount` > `outstandingAmount` " +
                            "THEN `totalAmount` - `outstandingAmount` " +
                            "ELSE 0 END"
            );

            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `loan_payments` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`loanId` INTEGER NOT NULL, " +
                            "`amount` REAL NOT NULL, " +
                            "`paymentType` TEXT NOT NULL, " +
                            "`account` TEXT NOT NULL, " +
                            "`paymentDate` TEXT NOT NULL, " +
                            "`note` TEXT NOT NULL)"
            );
        }
    };
}