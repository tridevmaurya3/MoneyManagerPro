package com.example.moneymanagerpro.activities;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.core.widget.NestedScrollView;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.auth.AuthNavigator;
import com.example.moneymanagerpro.auth.LocalProfilePhotoStore;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.AccountBalance;
import com.example.moneymanagerpro.navigation.DashboardDrawerController;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
import com.example.moneymanagerpro.ui.VisibleDataToolsController;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class DashboardActivity extends AppCompatActivity {

    private static final int EARLIEST_SELECTABLE_YEAR = 2000;

    private TextView txtBalance;
    private TextView txtIncome;
    private TextView txtExpense;
    private TextView txtCash;
    private TextView txtCardPayments;
    private TextView txtNetAvailableCash;

    private TextView txtSelectedPeriod;
    private TextView txtOverviewMonthLabel;
    private TextView txtSummarySubtitle;

    private TextView txtMonth1Title;
    private TextView txtMonth1Amount;
    private TextView txtMonth2Title;
    private TextView txtMonth2Amount;
    private TextView txtMonth3Title;
    private TextView txtMonth3Amount;

    private View cardBalance;
    private View cardIncome;
    private View cardExpense;
    private View cardCash;

    private View cardMonth1;
    private View cardMonth2;
    private View cardMonth3;

    private View btnAddIncome;
    private View btnAddExpense;
    private View btnAccounts;
    private View btnCategories;
    private View btnTransactions;
    private View btnReports;
    private View btnMoreFeatures;
    private View btnShareDashboard;
    private View btnOpenDrawer;
    private View btnUserMenu;

    private View btnPreviousMonth;
    private View btnNextMonth;
    private View btnChoosePeriod;

    private ImageView imgDashboardProfile;
    private TextView txtDashboardProfileInitial;

    private DrawerLayout dashboardDrawerLayout;
    private LinearLayout dashboardDrawerMenuContainer;

    private final Calendar selectedPeriod =
            Calendar.getInstance();

    private int lastObservedCurrentYear;
    private int lastObservedCurrentMonth;
    private int dashboardLoadVersion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        initializeCurrentPeriod();
        initializeViews();
        setupNavigationDrawer();
        applyTouchAnimations();
        setupClickListeners();
        updatePeriodLabelsAndButtons();
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (FirebaseAuth
                .getInstance()
                .getCurrentUser() == null) {

            AuthNavigator.logout(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        resetToCurrentMonthWhenCalendarMonthChanges();
        loadDashboardData();
        loadUserProfile();
    }

    private void initializeCurrentPeriod() {
        Calendar current =
                Calendar.getInstance();

        lastObservedCurrentYear =
                current.get(Calendar.YEAR);

        lastObservedCurrentMonth =
                current.get(Calendar.MONTH);

        setCalendarToMonth(
                selectedPeriod,
                lastObservedCurrentYear,
                lastObservedCurrentMonth
        );
    }

    /**
     * Phone के नए calendar month में आने पर Dashboard उस नए महीने
     * पर अपने-आप वापस आ जाएगा।
     *
     * इसलिए पहली तारीख को Overview और Summary में केवल नए महीने
     * की Income और Expense दिखाई देगी।
     */
    private void resetToCurrentMonthWhenCalendarMonthChanges() {
        Calendar current =
                Calendar.getInstance();

        int currentYear =
                current.get(Calendar.YEAR);

        int currentMonth =
                current.get(Calendar.MONTH);

        boolean monthChanged =
                currentYear != lastObservedCurrentYear
                        || currentMonth != lastObservedCurrentMonth;

        if (!monthChanged) {
            return;
        }

        lastObservedCurrentYear =
                currentYear;

        lastObservedCurrentMonth =
                currentMonth;

        setCalendarToMonth(
                selectedPeriod,
                currentYear,
                currentMonth
        );

        updatePeriodLabelsAndButtons();
    }

    private void initializeViews() {
        txtBalance =
                findViewById(R.id.txtBalance);

        txtIncome =
                findViewById(R.id.txtIncome);

        txtExpense =
                findViewById(R.id.txtExpense);

        txtCash =
                findViewById(R.id.txtCash);

        txtCardPayments =
                findViewById(R.id.txtCardPayments);

        txtNetAvailableCash =
                findViewById(R.id.txtNetAvailableCash);

        txtSelectedPeriod =
                findViewById(R.id.txtSelectedPeriod);

        txtOverviewMonthLabel =
                findViewById(R.id.txtOverviewMonthLabel);

        txtSummarySubtitle =
                findViewById(R.id.txtSummarySubtitle);

        txtMonth1Title =
                findViewById(R.id.txtMonth1Title);

        txtMonth1Amount =
                findViewById(R.id.txtMonth1Amount);

        txtMonth2Title =
                findViewById(R.id.txtMonth2Title);

        txtMonth2Amount =
                findViewById(R.id.txtMonth2Amount);

        txtMonth3Title =
                findViewById(R.id.txtMonth3Title);

        txtMonth3Amount =
                findViewById(R.id.txtMonth3Amount);

        cardBalance =
                findViewById(R.id.cardBalance);

        cardIncome =
                findViewById(R.id.cardIncome);

        cardExpense =
                findViewById(R.id.cardExpense);

        cardCash =
                findViewById(R.id.cardCash);

        cardMonth1 =
                findViewById(R.id.cardMonth1);

        cardMonth2 =
                findViewById(R.id.cardMonth2);

        cardMonth3 =
                findViewById(R.id.cardMonth3);

        btnAddIncome =
                findViewById(R.id.btnAddIncome);

        btnAddExpense =
                findViewById(R.id.btnAddExpense);

        btnAccounts =
                findViewById(R.id.btnAccounts);

        btnCategories =
                findViewById(R.id.btnCategories);

        btnTransactions =
                findViewById(R.id.btnTransactions);

        btnReports =
                findViewById(R.id.btnReports);

        btnMoreFeatures =
                findViewById(R.id.btnMoreFeatures);
        btnShareDashboard = findViewById(R.id.btnShareDashboard);

        btnOpenDrawer =
                findViewById(R.id.btnOpenDrawer);

        btnUserMenu =
                findViewById(R.id.btnUserMenu);

        btnPreviousMonth =
                findViewById(R.id.btnPreviousMonth);

        btnNextMonth =
                findViewById(R.id.btnNextMonth);

        btnChoosePeriod =
                findViewById(R.id.btnChoosePeriod);

        imgDashboardProfile =
                findViewById(R.id.imgDashboardProfile);

        txtDashboardProfileInitial =
                findViewById(R.id.txtDashboardProfileInitial);

        dashboardDrawerLayout =
                findViewById(R.id.dashboardDrawerLayout);

        dashboardDrawerMenuContainer =
                findViewById(
                        R.id.dashboardDrawerMenuContainer
                );
    }

    private void setupNavigationDrawer() {
        DashboardDrawerController drawerController =
                new DashboardDrawerController(
                        this,
                        dashboardDrawerLayout,
                        dashboardDrawerMenuContainer
                );

        drawerController.buildMenu();

        btnOpenDrawer.setOnClickListener(
                view -> dashboardDrawerLayout.openDrawer(
                        GravityCompat.START
                )
        );
    }

    private void applyTouchAnimations() {
        BubbleTouchAnimator.apply(cardBalance);
        BubbleTouchAnimator.apply(cardIncome);
        BubbleTouchAnimator.apply(cardExpense);
        BubbleTouchAnimator.apply(cardCash);

        BubbleTouchAnimator.apply(cardMonth1);
        BubbleTouchAnimator.apply(cardMonth2);
        BubbleTouchAnimator.apply(cardMonth3);

        BubbleTouchAnimator.apply(btnAddIncome);
        BubbleTouchAnimator.apply(btnAddExpense);
        BubbleTouchAnimator.apply(btnAccounts);
        BubbleTouchAnimator.apply(btnCategories);
        BubbleTouchAnimator.apply(btnTransactions);
        BubbleTouchAnimator.apply(btnReports);
        BubbleTouchAnimator.apply(btnMoreFeatures);
        BubbleTouchAnimator.apply(btnShareDashboard);
        BubbleTouchAnimator.apply(btnOpenDrawer);
        BubbleTouchAnimator.apply(btnUserMenu);

        BubbleTouchAnimator.apply(btnPreviousMonth);
        BubbleTouchAnimator.apply(btnNextMonth);
        BubbleTouchAnimator.apply(btnChoosePeriod);
    }

    private void setupClickListeners() {
        btnAddIncome.setOnClickListener(
                view -> openActivity(
                        AddIncomeActivity.class
                )
        );

        btnAddExpense.setOnClickListener(
                view -> openActivity(
                        AddExpenseActivity.class
                )
        );

        btnAccounts.setOnClickListener(
                view -> openActivity(
                        AccountActivity.class
                )
        );

        btnCategories.setOnClickListener(
                view -> openActivity(
                        CategoryActivity.class
                )
        );

        btnTransactions.setOnClickListener(
                view -> openActivity(
                        TransactionsActivity.class
                )
        );

        btnReports.setOnClickListener(
                view -> openActivity(
                        ReportActivity.class
                )
        );

        btnMoreFeatures.setOnClickListener(
                view -> showMoreToolsMenu()
        );
        btnShareDashboard.setOnClickListener(view ->
                new VisibleDataToolsController(this).shareDashboardMonth(selectedPeriod)
        );

        cardBalance.setOnClickListener(
                view -> openActivity(
                        AccountActivity.class
                )
        );

        cardCash.setOnClickListener(
                view -> openActivity(
                        AccountActivity.class
                )
        );

        cardIncome.setOnClickListener(
                view -> openActivity(
                        TransactionsActivity.class
                )
        );

        cardExpense.setOnClickListener(
                view -> openActivity(
                        TransactionsActivity.class
                )
        );

        btnUserMenu.setOnClickListener(
                this::showUserMenu
        );

        btnPreviousMonth.setOnClickListener(
                view -> changeSelectedMonth(-1)
        );

        btnNextMonth.setOnClickListener(
                view -> changeSelectedMonth(1)
        );

        btnChoosePeriod.setOnClickListener(
                view -> showMonthYearPicker()
        );

        cardMonth1.setOnClickListener(
                view -> choosePreviousMonthCard(1)
        );

        cardMonth2.setOnClickListener(
                view -> choosePreviousMonthCard(2)
        );

        cardMonth3.setOnClickListener(
                view -> choosePreviousMonthCard(3)
        );
    }

    private void openActivity(
            Class<?> activityClass
    ) {
        startActivity(
                new Intent(
                        DashboardActivity.this,
                        activityClass
                )
        );
    }

    private void loadUserProfile() {
        FirebaseUser user =
                FirebaseAuth
                        .getInstance()
                        .getCurrentUser();

        if (user == null) {
            return;
        }

        String name =
                user.getDisplayName() == null
                        ? ""
                        : user
                        .getDisplayName()
                        .trim();

        String email =
                user.getEmail() == null
                        ? ""
                        : user
                        .getEmail()
                        .trim();

        String source =
                name.isEmpty()
                        ? email
                        : name;

        txtDashboardProfileInitial.setText(
                source.isEmpty()
                        ? "U"
                        : source
                        .substring(0, 1)
                        .toUpperCase(
                                Locale.getDefault()
                        )
        );

        Uri photoUri =
                LocalProfilePhotoStore.get(this);

        if (photoUri == null) {
            imgDashboardProfile.setImageDrawable(null);
            imgDashboardProfile.setVisibility(View.GONE);

            txtDashboardProfileInitial.setVisibility(
                    View.VISIBLE
            );

            return;
        }

        try {
            imgDashboardProfile.setImageURI(null);
            imgDashboardProfile.setImageURI(photoUri);
            imgDashboardProfile.setVisibility(View.VISIBLE);

            txtDashboardProfileInitial.setVisibility(
                    View.GONE
            );

        } catch (Exception exception) {
            LocalProfilePhotoStore.clear(this);

            imgDashboardProfile.setVisibility(
                    View.GONE
            );

            txtDashboardProfileInitial.setVisibility(
                    View.VISIBLE
            );
        }
    }

    private void showUserMenu(
            View anchor
    ) {
        PopupMenu popupMenu =
                new PopupMenu(
                        this,
                        anchor
                );

        popupMenu
                .getMenu()
                .add(
                        0,
                        1,
                        0,
                        "User Profile"
                );

        popupMenu
                .getMenu()
                .add(
                        0,
                        2,
                        1,
                        "Change Password"
                );

        popupMenu
                .getMenu()
                .add(
                        0,
                        3,
                        2,
                        "Logout"
                );

        popupMenu.setOnMenuItemClickListener(
                item -> {
                    if (item.getItemId() == 1) {
                        openActivity(
                                UserProfileActivity.class
                        );

                        return true;
                    }

                    if (item.getItemId() == 2) {
                        startActivity(
                                AuthenticationActivity
                                        .createIntent(
                                                this,
                                                AuthenticationActivity
                                                        .MODE_CHANGE_PASSWORD
                                        )
                        );

                        return true;
                    }

                    if (item.getItemId() == 3) {
                        confirmLogout();
                        return true;
                    }

                    return false;
                }
        );

        popupMenu.show();
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage(
                        "Do you want to logout from Money Manager Pro?"
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .setPositiveButton(
                        "Logout",
                        (dialog, which) ->
                                AuthNavigator.logout(this)
                )
                .show();
    }

    @Override
    public void onBackPressed() {
        if (dashboardDrawerLayout.isDrawerOpen(
                GravityCompat.START
        )) {
            dashboardDrawerLayout.closeDrawer(
                    GravityCompat.START
            );

            return;
        }

        super.onBackPressed();
    }

    private void changeSelectedMonth(
            int monthChange
    ) {
        Calendar requested =
                copyMonth(selectedPeriod);

        requested.add(
                Calendar.MONTH,
                monthChange
        );

        if (isAfterCurrentMonth(requested)) {
            Toast.makeText(
                    this,
                    "Future month cannot be selected",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (requested.get(Calendar.YEAR)
                < EARLIEST_SELECTABLE_YEAR) {

            Toast.makeText(
                    this,
                    "No earlier period is available",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        setCalendarToMonth(
                selectedPeriod,
                requested.get(Calendar.YEAR),
                requested.get(Calendar.MONTH)
        );

        updatePeriodLabelsAndButtons();
        loadDashboardData();
    }

    private void choosePreviousMonthCard(
            int monthsBack
    ) {
        Calendar requested =
                copyMonth(selectedPeriod);

        requested.add(
                Calendar.MONTH,
                -monthsBack
        );

        if (requested.get(Calendar.YEAR)
                < EARLIEST_SELECTABLE_YEAR) {

            return;
        }

        setCalendarToMonth(
                selectedPeriod,
                requested.get(Calendar.YEAR),
                requested.get(Calendar.MONTH)
        );

        updatePeriodLabelsAndButtons();
        loadDashboardData();
    }

    private void showMonthYearPicker() {
        Calendar current =
                Calendar.getInstance();

        LinearLayout pickerContainer =
                new LinearLayout(this);

        pickerContainer.setOrientation(
                LinearLayout.HORIZONTAL
        );

        pickerContainer.setGravity(
                Gravity.CENTER
        );

        pickerContainer.setPadding(
                dpToPx(18),
                dpToPx(8),
                dpToPx(18),
                dpToPx(8)
        );

        NumberPicker monthPicker =
                new NumberPicker(this);

        monthPicker.setMinValue(0);
        monthPicker.setMaxValue(11);

        monthPicker.setDisplayedValues(
                new String[]{
                        "January",
                        "February",
                        "March",
                        "April",
                        "May",
                        "June",
                        "July",
                        "August",
                        "September",
                        "October",
                        "November",
                        "December"
                }
        );

        monthPicker.setValue(
                selectedPeriod.get(
                        Calendar.MONTH
                )
        );

        monthPicker.setWrapSelectorWheel(
                false
        );

        NumberPicker yearPicker =
                new NumberPicker(this);

        yearPicker.setMinValue(
                EARLIEST_SELECTABLE_YEAR
        );

        yearPicker.setMaxValue(
                current.get(Calendar.YEAR)
        );

        yearPicker.setValue(
                selectedPeriod.get(
                        Calendar.YEAR
                )
        );

        yearPicker.setWrapSelectorWheel(
                false
        );

        LinearLayout.LayoutParams monthParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1.35f
                );

        LinearLayout.LayoutParams yearParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        monthPicker.setLayoutParams(
                monthParams
        );

        yearPicker.setLayoutParams(
                yearParams
        );

        pickerContainer.addView(
                monthPicker
        );

        pickerContainer.addView(
                yearPicker
        );

        updateMonthPickerMaximum(
                monthPicker,
                yearPicker.getValue(),
                current
        );

        yearPicker.setOnValueChangedListener(
                (picker, oldValue, newValue) ->
                        updateMonthPickerMaximum(
                                monthPicker,
                                newValue,
                                current
                        )
        );

        new AlertDialog.Builder(this)
                .setTitle(
                        "Choose Month and Year"
                )
                .setView(
                        pickerContainer
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .setPositiveButton(
                        "Apply",
                        (dialog, which) -> {
                            int selectedYear =
                                    yearPicker.getValue();

                            int selectedMonth =
                                    monthPicker.getValue();

                            setCalendarToMonth(
                                    selectedPeriod,
                                    selectedYear,
                                    selectedMonth
                            );

                            updatePeriodLabelsAndButtons();
                            loadDashboardData();
                        }
                )
                .show();
    }

    private void updateMonthPickerMaximum(
            NumberPicker monthPicker,
            int selectedYear,
            Calendar current
    ) {
        int maximumMonth =
                selectedYear
                        == current.get(Calendar.YEAR)
                        ? current.get(Calendar.MONTH)
                        : 11;

        int currentPickerValue =
                monthPicker.getValue();

        monthPicker.setDisplayedValues(null);

        monthPicker.setMaxValue(
                maximumMonth
        );

        monthPicker.setDisplayedValues(
                createMonthNames(
                        maximumMonth
                )
        );

        if (currentPickerValue > maximumMonth) {
            monthPicker.setValue(
                    maximumMonth
            );
        }
    }

    private String[] createMonthNames(
            int maximumMonth
    ) {
        String[] allMonths = {
                "January",
                "February",
                "March",
                "April",
                "May",
                "June",
                "July",
                "August",
                "September",
                "October",
                "November",
                "December"
        };

        String[] availableMonths =
                new String[
                        maximumMonth + 1
                        ];

        System.arraycopy(
                allMonths,
                0,
                availableMonths,
                0,
                availableMonths.length
        );

        return availableMonths;
    }

    private void updatePeriodLabelsAndButtons() {
        String periodLabel =
                formatMonthYear(
                        selectedPeriod
                );

        txtSelectedPeriod.setText(
                periodLabel
        );

        txtOverviewMonthLabel.setText(
                periodLabel
                        + " net balance"
        );

        txtSummarySubtitle.setText(
                periodLabel
                        + " income and expenses"
        );

        Calendar month1 =
                copyMonth(selectedPeriod);

        Calendar month2 =
                copyMonth(selectedPeriod);

        Calendar month3 =
                copyMonth(selectedPeriod);

        month1.add(
                Calendar.MONTH,
                -1
        );

        month2.add(
                Calendar.MONTH,
                -2
        );

        month3.add(
                Calendar.MONTH,
                -3
        );

        txtMonth1Title.setText(
                formatMonthYear(month1)
        );

        txtMonth2Title.setText(
                formatMonthYear(month2)
        );

        txtMonth3Title.setText(
                formatMonthYear(month3)
        );

        boolean canMoveForward =
                !isCurrentMonth(
                        selectedPeriod
                );

        btnNextMonth.setEnabled(
                canMoveForward
        );

        btnNextMonth.setAlpha(
                canMoveForward
                        ? 1f
                        : 0.42f
        );

        boolean canMoveBackward =
                selectedPeriod.get(Calendar.YEAR)
                        > EARLIEST_SELECTABLE_YEAR
                        || selectedPeriod.get(
                        Calendar.MONTH
                ) > Calendar.JANUARY;

        btnPreviousMonth.setEnabled(
                canMoveBackward
        );

        btnPreviousMonth.setAlpha(
                canMoveBackward
                        ? 1f
                        : 0.42f
        );
    }

    private void loadDashboardData() {
        final int loadVersion =
                ++dashboardLoadVersion;

        final Calendar requestedPeriod =
                copyMonth(selectedPeriod);

        new Thread(() -> {
            String[] selectedRange =
                    getMonthRange(
                            requestedPeriod
                    );

            double income =
                    DatabaseClient
                            .getInstance(
                                    getApplicationContext()
                            )
                            .getAppDatabase()
                            .transactionDao()
                            .getTotalAmountByTypeForPeriod(
                                    "INCOME",
                                    selectedRange[0],
                                    selectedRange[1]
                            );

            double expense =
                    DatabaseClient
                            .getInstance(
                                    getApplicationContext()
                            )
                            .getAppDatabase()
                            .transactionDao()
                            .getTotalAmountByTypeForPeriod(
                                    "EXPENSE",
                                    selectedRange[0],
                                    selectedRange[1]
                            );

            double selectedNet =
                    income - expense;

            double cardPayments =
                    DatabaseClient
                            .getInstance(
                                    getApplicationContext()
                            )
                            .getAppDatabase()
                            .creditCardPaymentDao()
                            .getTotalPaidForPeriod(
                                    selectedRange[0],
                                    selectedRange[1]
                            );

            double netAvailableCash =
                    income - expense - cardPayments;

            Calendar month1 =
                    copyMonth(
                            requestedPeriod
                    );

            Calendar month2 =
                    copyMonth(
                            requestedPeriod
                    );

            Calendar month3 =
                    copyMonth(
                            requestedPeriod
                    );

            month1.add(
                    Calendar.MONTH,
                    -1
            );

            month2.add(
                    Calendar.MONTH,
                    -2
            );

            month3.add(
                    Calendar.MONTH,
                    -3
            );

            double month1Net =
                    getNetAmountForMonth(
                            month1
                    );

            double month2Net =
                    getNetAmountForMonth(
                            month2
                    );

            double month3Net =
                    getNetAmountForMonth(
                            month3
                    );

            List<AccountBalance> accountBalances =
                    DatabaseClient
                            .getInstance(
                                    getApplicationContext()
                            )
                            .getAppDatabase()
                            .accountDao()
                            .getAccountBalances();

            double cashBalance = 0;

            if (accountBalances != null) {
                for (AccountBalance accountBalance
                        : accountBalances) {

                    if (accountBalance != null
                            && accountBalance.name != null
                            && accountBalance.name
                            .equalsIgnoreCase(
                                    "Cash"
                            )) {

                        cashBalance =
                                accountBalance
                                        .currentBalance;

                        break;
                    }
                }
            }

            double finalIncome =
                    income;

            double finalExpense =
                    expense;

            double finalSelectedNet =
                    selectedNet;

            double finalCardPayments =
                    cardPayments;

            double finalNetAvailableCash =
                    netAvailableCash;

            double finalMonth1Net =
                    month1Net;

            double finalMonth2Net =
                    month2Net;

            double finalMonth3Net =
                    month3Net;

            double finalCashBalance =
                    cashBalance;

            runOnUiThread(() -> {
                if (loadVersion
                        != dashboardLoadVersion) {

                    return;
                }

                txtIncome.setText(
                        formatAmount(
                                finalIncome
                        )
                );

                txtExpense.setText(
                        formatAmount(
                                finalExpense
                        )
                );

                txtCardPayments.setText(
                        "−" + formatAmount(
                                finalCardPayments
                        )
                );

                applySignedAmount(
                        txtNetAvailableCash,
                        finalNetAvailableCash
                );

                txtCash.setText(
                        formatAmount(
                                finalCashBalance
                        )
                );

                applySignedAmount(
                        txtBalance,
                        finalSelectedNet
                );

                applySignedAmount(
                        txtMonth1Amount,
                        finalMonth1Net
                );

                applySignedAmount(
                        txtMonth2Amount,
                        finalMonth2Net
                );

                applySignedAmount(
                        txtMonth3Amount,
                        finalMonth3Net
                );
            });
        }).start();
    }

    private double getNetAmountForMonth(
            Calendar month
    ) {
        String[] range =
                getMonthRange(month);

        double income =
                DatabaseClient
                        .getInstance(
                                getApplicationContext()
                        )
                        .getAppDatabase()
                        .transactionDao()
                        .getTotalAmountByTypeForPeriod(
                                "INCOME",
                                range[0],
                                range[1]
                        );

        double expense =
                DatabaseClient
                        .getInstance(
                                getApplicationContext()
                        )
                        .getAppDatabase()
                        .transactionDao()
                        .getTotalAmountByTypeForPeriod(
                                "EXPENSE",
                                range[0],
                                range[1]
                        );

        return income - expense;
    }

    private String[] getMonthRange(
            Calendar month
    ) {
        Calendar start =
                copyMonth(month);

        start.set(
                Calendar.DAY_OF_MONTH,
                1
        );

        start.set(
                Calendar.HOUR_OF_DAY,
                0
        );

        start.set(
                Calendar.MINUTE,
                0
        );

        start.set(
                Calendar.SECOND,
                0
        );

        start.set(
                Calendar.MILLISECOND,
                0
        );

        Calendar end =
                copyMonth(month);

        end.set(
                Calendar.DAY_OF_MONTH,
                end.getActualMaximum(
                        Calendar.DAY_OF_MONTH
                )
        );

        end.set(
                Calendar.HOUR_OF_DAY,
                23
        );

        end.set(
                Calendar.MINUTE,
                59
        );

        end.set(
                Calendar.SECOND,
                59
        );

        end.set(
                Calendar.MILLISECOND,
                999
        );

        SimpleDateFormat databaseDateFormat =
                new SimpleDateFormat(
                        "yyyy-MM-dd HH:mm",
                        Locale.US
                );

        return new String[]{
                databaseDateFormat.format(
                        start.getTime()
                ),
                databaseDateFormat.format(
                        end.getTime()
                )
        };
    }

    private void applySignedAmount(
            TextView textView,
            double amount
    ) {
        textView.setText(
                formatSignedAmount(
                        amount
                )
        );

        int colorResource;

        if (amount > 0.0001d) {
            colorResource =
                    R.color.success;

        } else if (amount < -0.0001d) {
            colorResource =
                    R.color.expense;

        } else {
            colorResource =
                    R.color.app_text_secondary;
        }

        textView.setTextColor(
                getColorValue(
                        colorResource
                )
        );
    }

    private String formatSignedAmount(
            double amount
    ) {
        if (amount > 0.0001d) {
            return "+"
                    + formatAmount(
                    amount
            );
        }

        return formatAmount(amount);
    }

    private String formatMonthYear(
            Calendar calendar
    ) {
        SimpleDateFormat formatter =
                new SimpleDateFormat(
                        "MMMM yyyy",
                        Locale.ENGLISH
                );

        return formatter.format(
                calendar.getTime()
        );
    }

    private boolean isCurrentMonth(
            Calendar calendar
    ) {
        Calendar current =
                Calendar.getInstance();

        return calendar.get(Calendar.YEAR)
                == current.get(Calendar.YEAR)
                && calendar.get(Calendar.MONTH)
                == current.get(Calendar.MONTH);
    }

    private boolean isAfterCurrentMonth(
            Calendar calendar
    ) {
        Calendar current =
                Calendar.getInstance();

        int requestedValue =
                calendar.get(Calendar.YEAR)
                        * 12
                        + calendar.get(
                        Calendar.MONTH
                );

        int currentValue =
                current.get(Calendar.YEAR)
                        * 12
                        + current.get(
                        Calendar.MONTH
                );

        return requestedValue > currentValue;
    }

    private Calendar copyMonth(
            Calendar source
    ) {
        Calendar copy =
                Calendar.getInstance();

        setCalendarToMonth(
                copy,
                source.get(Calendar.YEAR),
                source.get(Calendar.MONTH)
        );

        return copy;
    }

    private void setCalendarToMonth(
            Calendar calendar,
            int year,
            int month
    ) {
        calendar.clear();

        calendar.set(
                Calendar.YEAR,
                year
        );

        calendar.set(
                Calendar.MONTH,
                month
        );

        calendar.set(
                Calendar.DAY_OF_MONTH,
                1
        );

        calendar.set(
                Calendar.HOUR_OF_DAY,
                0
        );

        calendar.set(
                Calendar.MINUTE,
                0
        );

        calendar.set(
                Calendar.SECOND,
                0
        );

        calendar.set(
                Calendar.MILLISECOND,
                0
        );
    }

    private void showMoreToolsMenu() {
        BottomSheetDialog dialog =
                new BottomSheetDialog(this);

        NestedScrollView scrollView =
                new NestedScrollView(this);

        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);

        scrollView.setOverScrollMode(
                View.OVER_SCROLL_NEVER
        );

        scrollView.setBackgroundColor(
                Color.TRANSPARENT
        );

        scrollView.setNestedScrollingEnabled(
                true
        );

        scrollView.setSmoothScrollingEnabled(
                true
        );

        scrollView.setDescendantFocusability(
                ViewGroup.FOCUS_AFTER_DESCENDANTS
        );

        LinearLayout mainLayout =
                new LinearLayout(this);

        mainLayout.setOrientation(
                LinearLayout.VERTICAL
        );

        mainLayout.setPadding(
                dpToPx(16),
                dpToPx(10),
                dpToPx(16),
                dpToPx(34)
        );

        GradientDrawable sheetBackground =
                new GradientDrawable();

        sheetBackground.setColor(
                getColorValue(
                        R.color.app_background
                )
        );

        sheetBackground.setCornerRadii(
                new float[]{
                        dpToPx(28),
                        dpToPx(28),
                        dpToPx(28),
                        dpToPx(28),
                        0,
                        0,
                        0,
                        0
                }
        );

        mainLayout.setBackground(
                sheetBackground
        );

        scrollView.addView(
                mainLayout,
                new NestedScrollView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        addSheetHandle(
                mainLayout
        );

        addSheetHeader(
                mainLayout,
                dialog
        );

        addSectionHeader(
                mainLayout,
                "Planning & Money",
                "Manage transfers, goals, budgets and repayments"
        );

        GridLayout planningGrid =
                createToolGrid();

        mainLayout.addView(
                planningGrid
        );

        addTool(
                dialog,
                planningGrid,
                "Transfer",
                "Move money between accounts",
                "↔",
                TransferActivity.class,
                R.color.purple,
                R.color.purple_surface,
                R.color.purple_outline
        );

        addTool(
                dialog,
                planningGrid,
                "Goals",
                "Track your savings targets",
                "◎",
                GoalActivity.class,
                R.color.success,
                R.color.success_surface,
                R.color.success_outline
        );

        addTool(
                dialog,
                planningGrid,
                "Recurring",
                "Regular income and expenses",
                "↻",
                RecurringActivity.class,
                R.color.orange,
                R.color.warning_surface,
                R.color.warning_outline
        );

        addTool(
                dialog,
                planningGrid,
                "Budgets",
                "Control category spending",
                "%",
                BudgetActivity.class,
                R.color.expense,
                R.color.error_surface,
                R.color.error_outline
        );

        addTool(
                dialog,
                planningGrid,
                "Loans",
                "Track lending and EMI",
                "₹",
                LoanActivity.class,
                R.color.pink,
                R.color.pink_surface,
                R.color.pink_outline
        );

        addTool(
                dialog,
                planningGrid,
                "Investments",
                "Manage saved investments",
                "↗",
                InvestmentActivity.class,
                R.color.purple,
                R.color.purple_surface,
                R.color.purple_outline
        );

        addSectionHeader(
                mainLayout,
                "Insights & Tracking",
                "Understand financial activity and upcoming payments"
        );

        GridLayout insightsGrid =
                createToolGrid();

        mainLayout.addView(
                insightsGrid
        );

        addTool(
                dialog,
                insightsGrid,
                "Analytics",
                "View spending insights",
                "◔",
                AnalyticsActivity.class,
                R.color.teal,
                R.color.teal_surface,
                R.color.teal_outline
        );

        addTool(
                dialog,
                insightsGrid,
                "Charts & Trends",
                "Visual financial reports",
                "▥",
                ChartsActivity.class,
                R.color.teal,
                R.color.teal_surface,
                R.color.teal_outline
        );

        addTool(
                dialog,
                insightsGrid,
                "Smart Advisor",
                "Personal finance guidance",
                "✦",
                FinanceAdvisorActivity.class,
                R.color.purple,
                R.color.purple_surface,
                R.color.purple_outline
        );

        addTool(
                dialog,
                insightsGrid,
                "Calendar",
                "Daily cash-flow view",
                "▦",
                CalendarActivity.class,
                R.color.secondary,
                R.color.info_surface,
                R.color.info_outline
        );

        addTool(
                dialog,
                insightsGrid,
                "Bills & Plans",
                "Subscriptions and bills",
                "□",
                SubscriptionActivity.class,
                R.color.purple,
                R.color.purple_surface,
                R.color.purple_outline
        );

        addTool(
                dialog,
                insightsGrid,
                "Bill Photos",
                "Saved receipt images",
                "▣",
                ReceiptGalleryActivity.class,
                R.color.expense,
                R.color.error_surface,
                R.color.error_outline
        );

        addSectionHeader(
                mainLayout,
                "Data & App",
                "Export, backup, import and configure the app"
        );

        GridLayout dataGrid =
                createToolGrid();

        mainLayout.addView(
                dataGrid
        );

        addTool(
                dialog,
                dataGrid,
                "Export",
                "Create reports and files",
                "⇩",
                ExportActivity.class,
                R.color.app_text_secondary,
                R.color.app_surface_soft,
                R.color.app_outline
        );

        addTool(
                dialog,
                dataGrid,
                "Backup",
                "Protect and restore data",
                "B",
                BackupActivity.class,
                R.color.secondary,
                R.color.info_surface,
                R.color.info_outline
        );

        addTool(
                dialog,
                dataGrid,
                "Import CSV",
                "Import transaction records",
                "CSV",
                CsvImportActivity.class,
                R.color.secondary,
                R.color.info_surface,
                R.color.info_outline
        );

        addTool(
                dialog,
                dataGrid,
                "Settings",
                "Privacy and preferences",
                "⚙",
                SettingsActivity.class,
                R.color.app_text_primary,
                R.color.app_surface_muted,
                R.color.app_outline
        );

        addTool(
                dialog,
                dataGrid,
                "Help Guide",
                "Learn how features work",
                "?",
                HelpActivity.class,
                R.color.teal,
                R.color.teal_surface,
                R.color.teal_outline
        );

        dialog.setContentView(
                scrollView
        );

        dialog.setOnShowListener(
                dialogInterface -> {
                    FrameLayout bottomSheet =
                            dialog.findViewById(
                                    com.google.android.material
                                            .R.id.design_bottom_sheet
                            );

                    if (bottomSheet == null) {
                        return;
                    }

                    bottomSheet.setBackgroundColor(
                            Color.TRANSPARENT
                    );

                    ViewGroup.LayoutParams params =
                            bottomSheet
                                    .getLayoutParams();

                    params.height =
                            (int) (
                                    getResources()
                                            .getDisplayMetrics()
                                            .heightPixels
                                            * 0.94f
                            );

                    bottomSheet.setLayoutParams(
                            params
                    );

                    BottomSheetBehavior<FrameLayout> behavior =
                            BottomSheetBehavior.from(
                                    bottomSheet
                            );

                    behavior.setSkipCollapsed(
                            true
                    );

                    behavior.setFitToContents(
                            true
                    );

                    behavior.setDraggable(
                            false
                    );

                    behavior.setState(
                            BottomSheetBehavior
                                    .STATE_EXPANDED
                    );

                    scrollView.post(
                            () -> scrollView.scrollTo(
                                    0,
                                    0
                            )
                    );
                }
        );

        dialog.show();
    }

    private void addSheetHandle(
            LinearLayout mainLayout
    ) {
        View handle =
                new View(this);

        GradientDrawable handleBackground =
                new GradientDrawable();

        handleBackground.setColor(
                getColorValue(
                        R.color.app_outline
                )
        );

        handleBackground.setCornerRadius(
                dpToPx(10)
        );

        handle.setBackground(
                handleBackground
        );

        LinearLayout.LayoutParams handleParams =
                new LinearLayout.LayoutParams(
                        dpToPx(42),
                        dpToPx(5)
                );

        handleParams.gravity =
                Gravity.CENTER_HORIZONTAL;

        handleParams.setMargins(
                0,
                dpToPx(2),
                0,
                dpToPx(18)
        );

        handle.setLayoutParams(
                handleParams
        );

        mainLayout.addView(
                handle
        );
    }

    private void addSheetHeader(
            LinearLayout mainLayout,
            BottomSheetDialog dialog
    ) {
        LinearLayout headerLayout =
                new LinearLayout(this);

        headerLayout.setOrientation(
                LinearLayout.HORIZONTAL
        );

        headerLayout.setGravity(
                Gravity.CENTER_VERTICAL
        );

        headerLayout.setPadding(
                dpToPx(2),
                0,
                0,
                dpToPx(4)
        );

        LinearLayout titleContainer =
                new LinearLayout(this);

        titleContainer.setOrientation(
                LinearLayout.VERTICAL
        );

        LinearLayout.LayoutParams titleContainerParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        titleContainer.setLayoutParams(
                titleContainerParams
        );

        TextView title =
                new TextView(this);

        title.setText(
                "More Tools"
        );

        title.setTextSize(25);

        title.setTextColor(
                getColorValue(
                        R.color.app_text_primary
                )
        );

        title.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        titleContainer.addView(
                title
        );

        TextView subtitle =
                new TextView(this);

        subtitle.setText(
                "Advanced finance features in one place"
        );

        subtitle.setTextSize(13);

        subtitle.setTextColor(
                getColorValue(
                        R.color.app_text_secondary
                )
        );

        subtitle.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        subtitleParams.setMargins(
                0,
                dpToPx(3),
                0,
                0
        );

        subtitle.setLayoutParams(
                subtitleParams
        );

        titleContainer.addView(
                subtitle
        );

        headerLayout.addView(
                titleContainer
        );

        TextView closeButton =
                new TextView(this);

        closeButton.setText("×");
        closeButton.setTextSize(27);

        closeButton.setTextColor(
                getColorValue(
                        R.color.app_text_secondary
                )
        );

        closeButton.setGravity(
                Gravity.CENTER
        );

        closeButton.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        closeButton.setClickable(true);
        closeButton.setFocusable(true);

        GradientDrawable closeBackground =
                new GradientDrawable();

        closeBackground.setColor(
                getColorValue(
                        R.color.app_surface_soft
                )
        );

        closeBackground.setCornerRadius(
                dpToPx(14)
        );

        closeButton.setBackground(
                closeBackground
        );

        LinearLayout.LayoutParams closeParams =
                new LinearLayout.LayoutParams(
                        dpToPx(44),
                        dpToPx(44)
                );

        closeParams.setMargins(
                dpToPx(10),
                0,
                0,
                0
        );

        closeButton.setLayoutParams(
                closeParams
        );

        BubbleTouchAnimator.apply(
                closeButton
        );

        closeButton.setOnClickListener(
                view -> dialog.dismiss()
        );

        headerLayout.addView(
                closeButton
        );

        mainLayout.addView(
                headerLayout
        );
    }

    private void addSectionHeader(
            LinearLayout mainLayout,
            String titleText,
            String subtitleText
    ) {
        LinearLayout sectionLayout =
                new LinearLayout(this);

        sectionLayout.setOrientation(
                LinearLayout.VERTICAL
        );

        LinearLayout.LayoutParams sectionParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        sectionParams.setMargins(
                0,
                dpToPx(23),
                0,
                dpToPx(11)
        );

        sectionLayout.setLayoutParams(
                sectionParams
        );

        TextView title =
                new TextView(this);

        title.setText(
                titleText
        );

        title.setTextColor(
                getColorValue(
                        R.color.app_text_primary
                )
        );

        title.setTextSize(18);

        title.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        sectionLayout.addView(
                title
        );

        TextView subtitle =
                new TextView(this);

        subtitle.setText(
                subtitleText
        );

        subtitle.setTextColor(
                getColorValue(
                        R.color.app_text_secondary
                )
        );

        subtitle.setTextSize(11);

        subtitle.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        subtitleParams.setMargins(
                0,
                dpToPx(3),
                0,
                0
        );

        subtitle.setLayoutParams(
                subtitleParams
        );

        sectionLayout.addView(
                subtitle
        );

        mainLayout.addView(
                sectionLayout
        );
    }

    private GridLayout createToolGrid() {
        GridLayout gridLayout =
                new GridLayout(this);

        gridLayout.setColumnCount(2);

        gridLayout.setAlignmentMode(
                GridLayout.ALIGN_MARGINS
        );

        gridLayout.setUseDefaultMargins(
                false
        );

        gridLayout.setLayoutParams(
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        return gridLayout;
    }

    private void addTool(
            BottomSheetDialog dialog,
            GridLayout toolGrid,
            String title,
            String subtitle,
            String iconText,
            Class<?> activityClass,
            @ColorRes int iconColorResource,
            @ColorRes int surfaceColorResource,
            @ColorRes int outlineColorResource
    ) {
        int iconColor =
                getColorValue(
                        iconColorResource
                );

        int surfaceColor =
                getColorValue(
                        surfaceColorResource
                );

        int outlineColor =
                getColorValue(
                        outlineColorResource
                );

        MaterialCardView card =
                new MaterialCardView(this);

        card.setCardBackgroundColor(
                surfaceColor
        );

        card.setRadius(
                dpToPx(16)
        );

        card.setCardElevation(
                dpToPx(1)
        );

        card.setStrokeColor(
                outlineColor
        );

        card.setStrokeWidth(
                dpToPx(1)
        );

        card.setClickable(true);
        card.setFocusable(true);

        card.setRippleColor(
                ColorStateList.valueOf(
                        createRippleColor(
                                iconColor
                        )
                )
        );

        GridLayout.LayoutParams cardParams =
                new GridLayout.LayoutParams();

        cardParams.rowSpec =
                GridLayout.spec(
                        GridLayout.UNDEFINED
                );

        cardParams.columnSpec =
                GridLayout.spec(
                        GridLayout.UNDEFINED,
                        1f
                );

        cardParams.width = 0;
        cardParams.height = dpToPx(96);

        cardParams.setMargins(
                dpToPx(5),
                dpToPx(5),
                dpToPx(5),
                dpToPx(5)
        );

        card.setLayoutParams(
                cardParams
        );

        LinearLayout contentLayout =
                new LinearLayout(this);

        contentLayout.setOrientation(
                LinearLayout.VERTICAL
        );

        contentLayout.setGravity(
                Gravity.START
        );

        contentLayout.setPadding(
                dpToPx(12),
                dpToPx(11),
                dpToPx(12),
                dpToPx(10)
        );

        LinearLayout topRow =
                new LinearLayout(this);

        topRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        topRow.setGravity(
                Gravity.CENTER_VERTICAL
        );

        TextView iconView =
                new TextView(this);

        iconView.setText(
                iconText
        );

        iconView.setTextColor(
                iconColor
        );

        if ("CSV".equals(iconText)) {
            iconView.setTextSize(10);
        } else {
            iconView.setTextSize(18);
        }

        iconView.setGravity(
                Gravity.CENTER
        );

        iconView.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        GradientDrawable iconBackground =
                new GradientDrawable();

        iconBackground.setShape(
                GradientDrawable.RECTANGLE
        );

        iconBackground.setColor(
                createIconBackgroundColor(
                        iconColor
                )
        );

        iconBackground.setCornerRadius(
                dpToPx(12)
        );

        iconView.setBackground(
                iconBackground
        );

        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(
                        dpToPx(34),
                        dpToPx(34)
                );

        iconView.setLayoutParams(
                iconParams
        );

        topRow.addView(
                iconView
        );

        TextView titleView =
                new TextView(this);

        titleView.setText(
                title
        );

        titleView.setTextColor(
                getColorValue(
                        R.color.app_text_primary
                )
        );

        titleView.setTextSize(15);

        titleView.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        titleView.setMaxLines(2);

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        titleParams.setMargins(
                dpToPx(10),
                0,
                0,
                0
        );

        titleView.setLayoutParams(
                titleParams
        );

        topRow.addView(
                titleView
        );

        contentLayout.addView(
                topRow
        );

        TextView subtitleView =
                new TextView(this);

        subtitleView.setText(
                subtitle
        );

        subtitleView.setTextColor(
                getColorValue(
                        R.color.app_text_secondary
                )
        );

        subtitleView.setTextSize(11);

        subtitleView.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        subtitleView.setMaxLines(2);

        subtitleView.setLineSpacing(
                0,
                1f
        );

        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        subtitleParams.setMargins(
                0,
                dpToPx(10),
                0,
                0
        );

        subtitleView.setLayoutParams(
                subtitleParams
        );

        contentLayout.addView(
                subtitleView
        );

        card.addView(
                contentLayout
        );

        BubbleTouchAnimator.apply(
                card
        );

        card.setOnClickListener(
                view -> {
                    dialog.dismiss();

                    startActivity(
                            new Intent(
                                    DashboardActivity.this,
                                    activityClass
                            )
                    );
                }
        );

        toolGrid.addView(
                card
        );
    }

    private int getColorValue(
            @ColorRes int colorResource
    ) {
        return ContextCompat.getColor(
                this,
                colorResource
        );
    }

    private int createIconBackgroundColor(
            int baseColor
    ) {
        return Color.argb(
                24,
                Color.red(baseColor),
                Color.green(baseColor),
                Color.blue(baseColor)
        );
    }

    private int createRippleColor(
            int baseColor
    ) {
        return Color.argb(
                38,
                Color.red(baseColor),
                Color.green(baseColor),
                Color.blue(baseColor)
        );
    }

    private String formatAmount(
            double amount
    ) {
        NumberFormat numberFormat =
                NumberFormat.getCurrencyInstance(
                        new Locale(
                                "en",
                                "IN"
                        )
                );

        return numberFormat.format(
                amount
        );
    }

    private int dpToPx(
            int dp
    ) {
        float density =
                getResources()
                        .getDisplayMetrics()
                        .density;

        return Math.round(
                dp * density
        );
    }
}
