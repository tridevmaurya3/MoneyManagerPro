package com.example.moneymanagerpro.account;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.database.AppDatabase;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Account;
import com.example.moneymanagerpro.utils.FinancialDataMergeManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Small, non-destructive recovery surface for archived MoneyManager accounts.
 *
 * The view never creates a replacement account and never changes transactions.
 * It only flips an existing archived account back to active by using the app's
 * existing FinancialDataMergeManager archive/restore path.
 */
public final class ArchivedAccountRecoveryView extends MaterialCardView {

    private TextView titleView;
    private TextView subtitleView;
    private MaterialButton restoreButton;
    private List<Account> archivedAccounts = Collections.emptyList();
    private boolean loading;

    public ArchivedAccountRecoveryView(@NonNull Context context) {
        super(context);
        initialize();
    }

    public ArchivedAccountRecoveryView(
            @NonNull Context context,
            @Nullable AttributeSet attrs
    ) {
        super(context, attrs);
        initialize();
    }

    public ArchivedAccountRecoveryView(
            @NonNull Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr
    ) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    private void initialize() {
        setVisibility(GONE);
        setCardBackgroundColor(color(R.color.info_surface));
        setCardElevation(0f);
        setRadius(dp(16));
        setStrokeWidth(dp(1));
        setStrokeColor(color(R.color.info_outline));
        setUseCompatPadding(false);

        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(14), dp(12), dp(12), dp(12));

        TextView icon = new TextView(getContext());
        icon.setText("↺");
        icon.setGravity(Gravity.CENTER);
        icon.setTextSize(20);
        icon.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        icon.setTextColor(color(R.color.secondary));

        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(dp(38), dp(38));
        iconParams.setMarginEnd(dp(10));
        icon.setLayoutParams(iconParams);
        root.addView(icon);

        LinearLayout textColumn = new LinearLayout(getContext());
        textColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textColumn.setLayoutParams(textParams);

        titleView = new TextView(getContext());
        titleView.setText("Archived Accounts Recovery");
        titleView.setTextColor(color(R.color.app_text_primary));
        titleView.setTextSize(14);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        textColumn.addView(titleView);

        subtitleView = new TextView(getContext());
        subtitleView.setText("Restore a hidden account without changing its history.");
        subtitleView.setTextColor(color(R.color.app_text_secondary));
        subtitleView.setTextSize(10);
        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        subtitleParams.topMargin = dp(2);
        subtitleView.setLayoutParams(subtitleParams);
        textColumn.addView(subtitleView);
        root.addView(textColumn);

        restoreButton = new MaterialButton(getContext());
        restoreButton.setText("Restore");
        restoreButton.setAllCaps(false);
        restoreButton.setTextSize(11);
        restoreButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        restoreButton.setTextColor(color(R.color.secondary));
        restoreButton.setCornerRadius(dp(12));
        restoreButton.setInsetTop(0);
        restoreButton.setInsetBottom(0);
        restoreButton.setBackgroundTintList(
                ColorStateList.valueOf(color(R.color.app_surface))
        );
        restoreButton.setStrokeColor(
                ColorStateList.valueOf(color(R.color.info_outline))
        );
        restoreButton.setStrokeWidth(dp(1));

        LinearLayout.LayoutParams buttonParams =
                new LinearLayout.LayoutParams(dp(86), dp(40));
        buttonParams.setMarginStart(dp(10));
        restoreButton.setLayoutParams(buttonParams);
        restoreButton.setOnClickListener(view -> showArchivedAccountsDialog());
        root.addView(restoreButton);

        addView(root);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        refreshArchivedAccounts();
    }

    private void refreshArchivedAccounts() {
        if (loading) return;
        loading = true;

        new Thread(() -> {
            List<Account> archived = new ArrayList<>();
            try {
                List<Account> all = DatabaseClient
                        .getInstance(getContext().getApplicationContext())
                        .getAppDatabase()
                        .accountDao()
                        .getAllAccountsIncludingArchived();

                if (all != null) {
                    for (Account account : all) {
                        if (account != null && account.isArchived()) {
                            archived.add(account);
                        }
                    }
                }
            } catch (Exception ignored) {
                archived.clear();
            }

            post(() -> {
                loading = false;
                archivedAccounts = archived;
                updateVisibility();
            });
        }, "archived-account-recovery").start();
    }

    private void updateVisibility() {
        if (archivedAccounts.isEmpty()) {
            setVisibility(GONE);
            return;
        }

        setVisibility(VISIBLE);
        int count = archivedAccounts.size();
        subtitleView.setText(
                count == 1
                        ? "1 archived account is hidden. Tap Restore to recover it safely."
                        : count + " archived accounts are hidden. Tap Restore to choose one."
        );
        restoreButton.setEnabled(true);
    }

    private void showArchivedAccountsDialog() {
        if (archivedAccounts.isEmpty()) {
            refreshArchivedAccounts();
            Toast.makeText(
                    getContext(),
                    "Checking archived accounts…",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        String[] labels = new String[archivedAccounts.size()];
        for (int index = 0; index < archivedAccounts.size(); index++) {
            Account account = archivedAccounts.get(index);
            String type = account.getType() == null || account.getType().trim().isEmpty()
                    ? "Account"
                    : account.getType().trim();
            labels[index] = account.getName() + "  •  " + type;
        }

        new AlertDialog.Builder(getContext())
                .setTitle("Archived Accounts")
                .setMessage("Choose the account you want to restore. Existing balances and linked history are kept unchanged.")
                .setItems(labels, (dialog, which) -> {
                    if (which >= 0 && which < archivedAccounts.size()) {
                        confirmRestore(archivedAccounts.get(which));
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmRestore(@NonNull Account account) {
        new AlertDialog.Builder(getContext())
                .setTitle("Restore Account")
                .setMessage(
                        "Restore \"" + account.getName() + "\" to the active Accounts list?\n\n"
                                + "No transaction, balance or loan history will be deleted."
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Restore", (dialog, which) -> restoreAccount(account))
                .show();
    }

    private void restoreAccount(@NonNull Account account) {
        restoreButton.setEnabled(false);

        new Thread(() -> {
            try {
                AppDatabase database = DatabaseClient
                        .getInstance(getContext().getApplicationContext())
                        .getAppDatabase();

                FinancialDataMergeManager.setArchived(database, account, false);

                post(() -> {
                    Toast.makeText(
                            getContext(),
                            "Account restored: " + account.getName(),
                            Toast.LENGTH_SHORT
                    ).show();

                    Context context = getContext();
                    if (context instanceof Activity) {
                        ((Activity) context).recreate();
                    } else {
                        refreshArchivedAccounts();
                    }
                });
            } catch (Exception exception) {
                post(() -> {
                    restoreButton.setEnabled(true);
                    Toast.makeText(
                            getContext(),
                            exception.getMessage() == null
                                    ? "Unable to restore account"
                                    : exception.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        }, "restore-archived-account").start();
    }

    private int color(int resource) {
        return ContextCompat.getColor(getContext(), resource);
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
