package com.example.moneymanagerpro.activities;

import android.app.DatePickerDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Goal;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class GoalActivity extends AppCompatActivity {

    private TextInputLayout inputGoalName;
    private TextInputLayout inputTargetAmount;
    private TextInputLayout inputInitialSaving;

    private TextInputEditText etGoalName;
    private TextInputEditText etTargetAmount;
    private TextInputEditText etInitialSaving;
    private TextInputEditText etTargetDate;

    private MaterialAutoCompleteTextView dropdownGoalColor;
    private MaterialButton btnSaveGoal;
    private LinearLayout goalContainer;
    private TextView txtEmptyGoals;

    private Calendar selectedCalendar;
    private String selectedDate;

    private final String[] colorNames = {
            "Green", "Blue", "Purple", "Orange", "Red", "Teal"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_goal);

        inputGoalName = findViewById(R.id.inputGoalName);
        inputTargetAmount = findViewById(R.id.inputTargetAmount);
        inputInitialSaving = findViewById(R.id.inputInitialSaving);

        etGoalName = findViewById(R.id.etGoalName);
        etTargetAmount = findViewById(R.id.etTargetAmount);
        etInitialSaving = findViewById(R.id.etInitialSaving);
        etTargetDate = findViewById(R.id.etTargetDate);

        dropdownGoalColor = findViewById(R.id.dropdownGoalColor);
        btnSaveGoal = findViewById(R.id.btnSaveGoal);
        goalContainer = findViewById(R.id.goalContainer);
        txtEmptyGoals = findViewById(R.id.txtEmptyGoals);

        TextView btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());

        selectedCalendar = Calendar.getInstance();
        updateDateField();

        ArrayAdapter<String> colorAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                colorNames
        );

        dropdownGoalColor.setAdapter(colorAdapter);
        dropdownGoalColor.setText("Green", false);

        etTargetDate.setOnClickListener(v -> showDatePicker());

        BubbleTouchAnimator.apply(btnSaveGoal);

        btnSaveGoal.setOnClickListener(v -> saveGoal());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadGoals();
    }

    private void showDatePicker() {
        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    selectedCalendar.set(Calendar.YEAR, year);
                    selectedCalendar.set(Calendar.MONTH, month);
                    selectedCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                    updateDateField();
                },
                selectedCalendar.get(Calendar.YEAR),
                selectedCalendar.get(Calendar.MONTH),
                selectedCalendar.get(Calendar.DAY_OF_MONTH)
        );

        dialog.show();
    }

    private void updateDateField() {
        selectedDate = new SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.US
        ).format(selectedCalendar.getTime());

        String visibleDate = new SimpleDateFormat(
                "dd MMMM yyyy",
                Locale.ENGLISH
        ).format(selectedCalendar.getTime());

        etTargetDate.setText(visibleDate);
    }

    private void saveGoal() {
        String goalName = etGoalName.getText() == null
                ? ""
                : etGoalName.getText().toString().trim();

        String targetAmountText = etTargetAmount.getText() == null
                ? ""
                : etTargetAmount.getText().toString().trim();

        String initialSavingText = etInitialSaving.getText() == null
                ? ""
                : etInitialSaving.getText().toString().trim();

        if (goalName.isEmpty()) {
            inputGoalName.setError("Please enter goal name");
            return;
        }

        double targetAmount;

        try {
            targetAmount = Double.parseDouble(targetAmountText);
        } catch (Exception exception) {
            inputTargetAmount.setError("Enter a valid target amount");
            return;
        }

        if (targetAmount <= 0) {
            inputTargetAmount.setError("Target amount must be greater than zero");
            return;
        }

        double initialSaving = 0;

        if (!initialSavingText.isEmpty()) {
            try {
                initialSaving = Double.parseDouble(initialSavingText);
            } catch (Exception exception) {
                inputInitialSaving.setError("Enter a valid saving amount");
                return;
            }
        }

        if (initialSaving < 0) {
            inputInitialSaving.setError("Saving amount cannot be negative");
            return;
        }

        inputGoalName.setError(null);
        inputTargetAmount.setError(null);
        inputInitialSaving.setError(null);

        Goal goal = new Goal();
        goal.setName(goalName);
        goal.setTargetAmount(targetAmount);
        goal.setSavedAmount(initialSaving);
        goal.setTargetDate(selectedDate);
        goal.setColor(getColorCode(
                dropdownGoalColor.getText().toString().trim()
        ));

        btnSaveGoal.setEnabled(false);
        btnSaveGoal.setText("Saving Goal...");

        new Thread(() -> {
            DatabaseClient.getInstance(getApplicationContext())
                    .getAppDatabase()
                    .goalDao()
                    .insert(goal);

            runOnUiThread(() -> {
                etGoalName.setText("");
                etTargetAmount.setText("");
                etInitialSaving.setText("");
                dropdownGoalColor.setText("Green", false);

                btnSaveGoal.setEnabled(true);
                btnSaveGoal.setText("Create Goal");

                Toast.makeText(
                        GoalActivity.this,
                        "Goal created successfully",
                        Toast.LENGTH_SHORT
                ).show();

                loadGoals();
            });
        }).start();
    }

    private void loadGoals() {
        new Thread(() -> {
            List<Goal> goals = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .goalDao()
                    .getAllGoals();

            runOnUiThread(() -> showGoals(goals));
        }).start();
    }

    private void showGoals(List<Goal> goals) {
        goalContainer.removeAllViews();

        txtEmptyGoals.setVisibility(
                goals.isEmpty() ? View.VISIBLE : View.GONE
        );

        for (Goal goal : goals) {
            addGoalCard(goal);
        }
    }

    private void addGoalCard(Goal goal) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(Color.WHITE);
        card.setRadius(dpToPx(22));
        card.setCardElevation(dpToPx(5));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dpToPx(12));
        card.setLayoutParams(cardParams);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));

        TextView txtGoalName = new TextView(this);
        txtGoalName.setText(goal.getName());
        txtGoalName.setTextSize(20);
        txtGoalName.setTextColor(Color.parseColor("#172033"));
        txtGoalName.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        txtGoalName.setGravity(Gravity.CENTER);

        TextView txtDate = new TextView(this);
        txtDate.setText("Target Date: " + goal.getTargetDate());
        txtDate.setTextSize(13);
        txtDate.setTextColor(Color.parseColor("#64748B"));
        txtDate.setGravity(Gravity.CENTER);

        TextView txtAmounts = new TextView(this);
        txtAmounts.setText(
                formatAmount(goal.getSavedAmount())
                        + " saved out of "
                        + formatAmount(goal.getTargetAmount())
        );
        txtAmounts.setTextSize(15);
        txtAmounts.setTextColor(Color.parseColor("#172033"));
        txtAmounts.setGravity(Gravity.CENTER);

        int progress = calculateProgress(
                goal.getSavedAmount(),
                goal.getTargetAmount()
        );

        ProgressBar progressBar = new ProgressBar(
                this,
                null,
                android.R.attr.progressBarStyleHorizontal
        );

        progressBar.setMax(100);
        progressBar.setProgress(progress);
        progressBar.setProgressTintList(
                ColorStateList.valueOf(Color.parseColor(safeColor(goal.getColor())))
        );

        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(10)
        );
        progressParams.setMargins(0, dpToPx(14), 0, 0);
        progressBar.setLayoutParams(progressParams);

        TextView txtProgress = new TextView(this);
        txtProgress.setText(progress + "% Completed");
        txtProgress.setTextSize(14);
        txtProgress.setTextColor(Color.parseColor(safeColor(goal.getColor())));
        txtProgress.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        txtProgress.setGravity(Gravity.CENTER);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams actionRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(50)
        );
        actionRowParams.setMargins(0, dpToPx(14), 0, 0);
        actionRow.setLayoutParams(actionRowParams);

        MaterialButton btnAddSaving = new MaterialButton(this);
        btnAddSaving.setText("Add Saving");
        btnAddSaving.setTextColor(Color.WHITE);
        btnAddSaving.setTextSize(13);
        btnAddSaving.setAllCaps(false);
        btnAddSaving.setCornerRadius(dpToPx(22));
        btnAddSaving.setBackgroundTintList(
                ColorStateList.valueOf(Color.parseColor(safeColor(goal.getColor())))
        );

        MaterialButton btnDelete = new MaterialButton(this);
        btnDelete.setText("Delete");
        btnDelete.setTextColor(Color.WHITE);
        btnDelete.setTextSize(13);
        btnDelete.setAllCaps(false);
        btnDelete.setCornerRadius(dpToPx(22));
        btnDelete.setBackgroundTintList(
                ColorStateList.valueOf(Color.parseColor("#D32F2F"))
        );

        LinearLayout.LayoutParams addSavingParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
        );
        addSavingParams.setMargins(0, 0, dpToPx(6), 0);

        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
        );
        deleteParams.setMargins(dpToPx(6), 0, 0, 0);

        btnAddSaving.setLayoutParams(addSavingParams);
        btnDelete.setLayoutParams(deleteParams);

        BubbleTouchAnimator.apply(card);
        BubbleTouchAnimator.apply(btnAddSaving);
        BubbleTouchAnimator.apply(btnDelete);

        btnAddSaving.setOnClickListener(v -> showAddSavingDialog(goal));
        btnDelete.setOnClickListener(v -> confirmDelete(goal));

        actionRow.addView(btnAddSaving);
        actionRow.addView(btnDelete);

        content.addView(txtGoalName);
        content.addView(txtDate);
        content.addView(txtAmounts);
        content.addView(progressBar);
        content.addView(txtProgress);
        content.addView(actionRow);

        card.addView(content);
        goalContainer.addView(card);
    }

    private void showAddSavingDialog(Goal goal) {
        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(dpToPx(22), dpToPx(8), dpToPx(22), dpToPx(8));

        TextView label = new TextView(this);
        label.setText("Add amount to " + goal.getName());
        label.setTextColor(Color.parseColor("#2E7D32"));
        label.setTextSize(15);
        label.setGravity(Gravity.CENTER);
        label.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        TextInputLayout inputAmount = new TextInputLayout(this);
        inputAmount.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);

        TextInputEditText etAmount = new TextInputEditText(this);
        etAmount.setHint("₹ 0.00");
        etAmount.setGravity(Gravity.CENTER);
        etAmount.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        etAmount.setInputType(
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
        );

        inputAmount.addView(etAmount);

        dialogLayout.addView(label);
        dialogLayout.addView(inputAmount);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Add Saving")
                .setView(dialogLayout)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", null)
                .create();

        dialog.setOnShowListener(listener -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String amountText = etAmount.getText() == null
                        ? ""
                        : etAmount.getText().toString().trim();

                double amount;

                try {
                    amount = Double.parseDouble(amountText);
                } catch (Exception exception) {
                    inputAmount.setError("Enter a valid amount");
                    return;
                }

                if (amount <= 0) {
                    inputAmount.setError("Amount must be greater than zero");
                    return;
                }

                goal.setSavedAmount(goal.getSavedAmount() + amount);

                new Thread(() -> {
                    DatabaseClient.getInstance(getApplicationContext())
                            .getAppDatabase()
                            .goalDao()
                            .update(goal);

                    runOnUiThread(() -> {
                        dialog.dismiss();

                        Toast.makeText(
                                GoalActivity.this,
                                "Saving added successfully",
                                Toast.LENGTH_SHORT
                        ).show();

                        loadGoals();
                    });
                }).start();
            });
        });

        dialog.show();
    }

    private void confirmDelete(Goal goal) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Goal")
                .setMessage("Do you want to delete \"" + goal.getName() + "\"?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    new Thread(() -> {
                        DatabaseClient.getInstance(getApplicationContext())
                                .getAppDatabase()
                                .goalDao()
                                .delete(goal);

                        runOnUiThread(() -> {
                            Toast.makeText(
                                    GoalActivity.this,
                                    "Goal deleted",
                                    Toast.LENGTH_SHORT
                            ).show();

                            loadGoals();
                        });
                    }).start();
                })
                .show();
    }

    private int calculateProgress(double saved, double target) {
        if (target <= 0) {
            return 0;
        }

        int progress = (int) ((saved / target) * 100);

        return Math.min(progress, 100);
    }

    private String getColorCode(String colorName) {
        switch (colorName) {
            case "Blue":
                return "#1565C0";

            case "Purple":
                return "#6A1B9A";

            case "Orange":
                return "#EF6C00";

            case "Red":
                return "#D32F2F";

            case "Teal":
                return "#00838F";

            case "Green":
            default:
                return "#2E7D32";
        }
    }

    private String safeColor(String colorCode) {
        if (colorCode == null || colorCode.trim().isEmpty()) {
            return "#2E7D32";
        }

        return colorCode;
    }

    private String formatAmount(double amount) {
        NumberFormat numberFormat = NumberFormat.getCurrencyInstance(
                new Locale("en", "IN")
        );

        return numberFormat.format(amount);
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density);
    }
}