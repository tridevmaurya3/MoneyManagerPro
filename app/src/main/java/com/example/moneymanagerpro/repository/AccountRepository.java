package com.example.moneymanagerpro.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.moneymanagerpro.dao.AccountDao;
import com.example.moneymanagerpro.database.AppDatabase;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Account;
import com.example.moneymanagerpro.model.AccountBalance;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AccountRepository {

    private final AccountDao accountDao;
    private final ExecutorService executorService;
    private final Handler mainHandler;

    public interface AccountListCallback {
        void onLoaded(List<Account> accounts);
    }

    public interface AccountBalanceListCallback {
        void onLoaded(List<AccountBalance> accountBalances);
    }

    public interface OperationCallback {
        void onComplete();
    }

    public AccountRepository(Context context) {
        AppDatabase database = DatabaseClient.getInstance(context).getAppDatabase();

        accountDao = database.accountDao();
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public void ensureDefaultCashAccount(OperationCallback callback) {
        executorService.execute(() -> {
            if (accountDao.getAccountCount() == 0) {
                Account cashAccount = new Account();
                cashAccount.setName("Cash");
                cashAccount.setType("CASH");
                cashAccount.setOpeningBalance(0);
                cashAccount.setColor("#2563EB");

                accountDao.insert(cashAccount);
            }

            if (callback != null) {
                mainHandler.post(callback::onComplete);
            }
        });
    }

    public void insert(Account account, OperationCallback callback) {
        executorService.execute(() -> {
            accountDao.insert(account);

            if (callback != null) {
                mainHandler.post(callback::onComplete);
            }
        });
    }

    public void update(Account account, OperationCallback callback) {
        executorService.execute(() -> {
            accountDao.update(account);

            if (callback != null) {
                mainHandler.post(callback::onComplete);
            }
        });
    }

    public void delete(Account account, OperationCallback callback) {
        executorService.execute(() -> {
            accountDao.delete(account);

            if (callback != null) {
                mainHandler.post(callback::onComplete);
            }
        });
    }

    public void getAllAccounts(AccountListCallback callback) {
        executorService.execute(() -> {
            List<Account> accounts = accountDao.getAllAccounts();

            mainHandler.post(() -> callback.onLoaded(accounts));
        });
    }

    public void getAccountBalances(AccountBalanceListCallback callback) {
        executorService.execute(() -> {
            List<AccountBalance> accountBalances =
                    accountDao.getAccountBalances();

            mainHandler.post(() -> callback.onLoaded(accountBalances));
        });
    }
}