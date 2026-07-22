package com.example.moneymanagerpro.activities;

import android.app.DatePickerDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

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
import java.util.Date;
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
            "Green",
            "Blue",
            "Purple",
            "Orange",
            "Red",
            "Teal"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_goal);

        bindViews();
        prepareScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadGoals();
    }

    private void bindViews() {
        inputGoalName =
                findViewById(R.id.inputGoalName);

        inputTargetAmount =
                findViewById(R.id.inputTargetAmount);

        inputInitialSaving =
                findViewById(R.id.inputInitialSaving);

        etGoalName =
                findViewById(R.id.etGoalName);

        etTargetAmount =
                findViewById(R.id.etTargetAmount);

        etInitialSaving =
                findViewById(R.id.etInitialSaving);

        etTargetDate =
                findViewById(R.id.etTargetDate);

        dropdownGoalColor =
                findViewById(R.id.dropdownGoalColor);

        btnSaveGoal =
                findViewById(R.id.btnSaveGoal);

        goalContainer =
                findViewById(R.id.goalContainer);

        txtEmptyGoals =
                findViewById(R.id.txtEmptyGoals);

        TextView btnBack =
                findViewById(R.id.btnBack);

        btnBack.setOnClickListener(
                view -> finish()
        );
    }

    private void prepareScreen() {
        selectedCalendar =
                Calendar.getInstance();

        updateDateField();

        ArrayAdapter<String> colorAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_list_item_1,
                        colorNames
                );

        dropdownGoalColor.setAdapter(
                colorAdapter
        );

        dropdownGoalColor.setText(
                "Green",
                false
        );

        etTargetDate.setOnClickListener(
                view -> showDatePicker()
        );

        btnSaveGoal.setOnClickListener(
                view -> saveGoal()
        );

        BubbleTouchAnimator.apply(
                btnSaveGoal
        );
    }

    private void showDatePicker() {
        DatePickerDialog dialog =
                new DatePickerDialog(
                        this,
                        (view, year, month, dayOfMonth) -> {
                            selectedCalendar.set(
                                    Calendar.YEAR,
                                    year
                            );

                            selectedCalendar.set(
                                    Calendar.MONTH,
                                    month
                            );

                            selectedCalendar.set(
                                    Calendar.DAY_OF_MONTH,
                                    dayOfMonth
                            );

                            updateDateField();
                        },
                        selectedCalendar.get(
                                Calendar.YEAR
                        ),
                        selectedCalendar.get(
                                Calendar.MONTH
                        ),
                        selectedCalendar.get(
                                Calendar.DAY_OF_MONTH
                        )
                );

        dialog.getDatePicker().setMinDate(
                Calendar.getInstance()
                        .getTimeInMillis()
                        - 1000
        );

        dialog.show();
    }

    private void updateDateField() {
        selectedDate =
                new SimpleDateFormat(
                        "yyyy-MM-dd",
                        Locale.US
                ).format(
                        selectedCalendar.getTime()
                );

        String visibleDate =
                new SimpleDateFormat(
                        "dd MMMM yyyy",
                        Locale.ENGLISH
                ).format(
                        selectedCalendar.getTime()
                );

        etTargetDate.setText(visibleDate);
    }

    private void saveGoal() {
        String goalName =
                etGoalName.getText() == null
                        ? ""
                        : etGoalName
                        .getText()
                        .toString()
                        .trim();

        String targetAmountText =
                etTargetAmount.getText() == null
                        ? ""
                        : etTargetAmount
                        .getText()
                        .toString()
                        .trim();

        String initialSavingText =
                etInitialSaving.getText() == null
                        ? ""
                        : etInitialSaving
                        .getText()
                        .toString()
                        .trim();

        if (goalName.isEmpty()) {
            inputGoalName.setError(
                    "Please enter goal name"
            );

            etGoalName.requestFocus();
            return;
        }

        double targetAmount;

        try {
            targetAmount =
                    Double.parseDouble(
                            targetAmountText
                    );

        } catch (Exception exception) {
            inputTargetAmount.setError(
                    "Enter a valid target amount"
            );

            etTargetAmount.requestFocus();
            return;
        }

        if (targetAmount <= 0) {
            inputTargetAmount.setError(
                    "Target amount must be greater than zero"
            );

            etTargetAmount.requestFocus();
            return;
        }

        double initialSaving = 0;

        if (!initialSavingText.isEmpty()) {
            try {
                initialSaving =
                        Double.parseDouble(
                                initialSavingText
                        );

            } catch (Exception exception) {
                inputInitialSaving.setError(
                        "Enter a valid saving amount"
                );

                etInitialSaving.requestFocus();
                return;
            }
        }

        if (initialSaving < 0) {
            inputInitialSaving.setError(
                    "Saving amount cannot be negative"
            );

            etInitialSaving.requestFocus();
            return;
        }

        if (selectedDate == null
                || selectedDate.trim().isEmpty()) {

            Toast.makeText(
                    this,
                    "Please select target date",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        inputGoalName.setError(null);
        inputTargetAmount.setError(null);
        inputInitialSaving.setError(null);

        String selectedColor =
                dropdownGoalColor
                        .getText()
                        .toString()
                        .trim();

        if (selectedColor.isEmpty()) {
            selectedColor = "Green";
        }

        Goal goal =
                new Goal();

        goal.setName(goalName);
        goal.setTargetAmount(targetAmount);
        goal.setSavedAmount(initialSaving);
        goal.setTargetDate(selectedDate);

        goal.setColor(
                getColorCode(
                        selectedColor
                )
        );

        btnSaveGoal.setEnabled(false);

        btnSaveGoal.setText(
                "Saving Goal..."
        );

        new Thread(() -> {
            try {
                DatabaseClient
                        .getInstance(
                                getApplicationContext()
                        )
                        .getAppDatabase()
                        .goalDao()
                        .insert(goal);

                runOnUiThread(() -> {
                    clearGoalForm();

                    btnSaveGoal.setEnabled(true);

                    btnSaveGoal.setText(
                            "Create Goal"
                    );

                    Toast.makeText(
                            GoalActivity.this,
                            "Goal created successfully",
                            Toast.LENGTH_SHORT
                    ).show();

                    loadGoals();
                });

            } catch (Exception exception) {
                runOnUiThread(() -> {
                    btnSaveGoal.setEnabled(true);

                    btnSaveGoal.setText(
                            "Create Goal"
                    );

                    Toast.makeText(
                            GoalActivity.this,
                            "Unable to create goal",
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }
        }).start();
    }

    private void clearGoalForm() {
        etGoalName.setText("");
        etTargetAmount.setText("");
        etInitialSaving.setText("");

        dropdownGoalColor.setText(
                "Green",
                false
        );

        selectedCalendar =
                Calendar.getInstance();

        updateDateField();
    }

    private void loadGoals() {
        new Thread(() -> {
            try {
                List<Goal> goals =
                        DatabaseClient
                                .getInstance(
                                        getApplicationContext()
                                )
                                .getAppDatabase()
                                .goalDao()
                                .getAllGoals();

                runOnUiThread(
                        () -> showGoals(goals)
                );

            } catch (Exception exception) {
                runOnUiThread(() -> {
                    goalContainer.removeAllViews();
                    txtEmptyGoals.setVisibility(View.VISIBLE);

                    Toast.makeText(
                            GoalActivity.this,
                            "Unable to load goals",
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }
        }).start();
    }

    private void showGoals(
            List<Goal> goals
    ) {
        goalContainer.removeAllViews();

        boolean isEmpty =
                goals == null
                        || goals.isEmpty();

        txtEmptyGoals.setVisibility(
                isEmpty
                        ? View.VISIBLE
                        : View.GONE
        );

        if (isEmpty) {
            return;
        }

        for (Goal goal : goals) {
            if (goal != null) {
                addGoalCard(goal);
            }
        }
    }

    private void addGoalCard(
            Goal goal
    ) {
        double targetAmount =
                Math.max(
                        goal.getTargetAmount(),
                        0
                );

        double savedAmount =
                Math.max(
                        goal.getSavedAmount(),
                        0
                );

        double remainingAmount =
                Math.max(
                        targetAmount - savedAmount,
                        0
                );

        int progress =
                calculateProgress(
                        savedAmount,
                        targetAmount
                );

        boolean isCompleted =
                targetAmount > 0
                        && savedAmount >= targetAmount;

        int goalColor =
                parseGoalColor(
                        goal.getColor()
                );

        int statusColor =
                isCompleted
                        ? getColorValue(
                        R.color.success
                )
                        : goalColor;

        int statusSurface =
                isCompleted
                        ? getColorValue(
                        R.color.success_surface
                )
                        : createTranslucentColor(
                        goalColor,
                        18
                );

        int statusOutline =
                isCompleted
                        ? getColorValue(
                        R.color.success_outline
                )
                        : createTranslucentColor(
                        goalColor,
                        70
                );

        MaterialCardView card =
                new MaterialCardView(this);

        card.setCardBackgroundColor(
                getColorValue(
                        R.color.app_surface
                )
        );

        card.setRadius(
                dpToPx(19)
        );

        card.setCardElevation(
                dpToPx(1)
        );

        card.setStrokeWidth(
                dpToPx(1)
        );

        card.setStrokeColor(
                statusOutline
        );

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        cardParams.setMargins(
                0,
                dpToPx(6),
                0,
                dpToPx(6)
        );

        card.setLayoutParams(
                cardParams
        );

        LinearLayout content =
                new LinearLayout(this);

        content.setOrientation(
                LinearLayout.VERTICAL
        );

        content.setPadding(
                dpToPx(15),
                dpToPx(15),
                dpToPx(15),
                dpToPx(14)
        );

        /*
         * Header
         */

        LinearLayout headerRow =
                new LinearLayout(this);

        headerRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        headerRow.setGravity(
                Gravity.CENTER_VERTICAL
        );

        TextView goalIcon =
                createGoalIcon(
                        goal.getName(),
                        goalColor
                );

        headerRow.addView(
                goalIcon
        );

        LinearLayout titleContainer =
                new LinearLayout(this);

        titleContainer.setOrientation(
                LinearLayout.VERTICAL
        );

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        titleParams.setMargins(
                dpToPx(11),
                0,
                dpToPx(8),
                0
        );

        titleContainer.setLayoutParams(
                titleParams
        );

        TextView txtGoalName =
                createText(
                        safeText(
                                goal.getName(),
                                "Savings Goal"
                        ),
                        16,
                        getColorValue(
                                R.color.app_text_primary
                        ),
                        true
                );

        TextView txtDate =
                createText(
                        "Target: "
                                + formatVisibleDate(
                                goal.getTargetDate()
                        ),
                        11,
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        false
                );

        LinearLayout.LayoutParams dateParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        dateParams.setMargins(
                0,
                dpToPx(3),
                0,
                0
        );

        txtDate.setLayoutParams(
                dateParams
        );

        titleContainer.addView(
                txtGoalName
        );

        titleContainer.addView(
                txtDate
        );

        headerRow.addView(
                titleContainer
        );

        TextView statusBadge =
                createStatusBadge(
                        isCompleted
                                ? "Completed"
                                : "In Progress",
                        statusColor,
                        statusSurface,
                        statusOutline
                );

        headerRow.addView(
                statusBadge
        );

        content.addView(
                headerRow
        );

        /*
         * Divider
         */

        View divider =
                new View(this);

        divider.setBackgroundColor(
                getColorValue(
                        R.color.app_divider
                )
        );

        LinearLayout.LayoutParams dividerParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dpToPx(1)
                );

        dividerParams.setMargins(
                0,
                dpToPx(13),
                0,
                dpToPx(12)
        );

        divider.setLayoutParams(
                dividerParams
        );

        content.addView(
                divider
        );

        /*
         * Saved and Target
         */

        LinearLayout amountRow =
                new LinearLayout(this);

        amountRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        amountRow.setBaselineAligned(
                false
        );

        LinearLayout savedBlock =
                createMetricBlock(
                        "Saved",
                        formatAmount(savedAmount),
                        goalColor,
                        createTranslucentColor(
                                goalColor,
                                16
                        ),
                        createTranslucentColor(
                                goalColor,
                                55
                        )
                );

        LinearLayout.LayoutParams savedParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        savedParams.setMargins(
                0,
                0,
                dpToPx(5),
                0
        );

        savedBlock.setLayoutParams(
                savedParams
        );

        LinearLayout targetBlock =
                createMetricBlock(
                        "Target",
                        formatAmount(targetAmount),
                        getColorValue(
                                R.color.secondary
                        ),
                        getColorValue(
                                R.color.info_surface
                        ),
                        getColorValue(
                                R.color.info_outline
                        )
                );

        LinearLayout.LayoutParams targetParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        targetParams.setMargins(
                dpToPx(5),
                0,
                0,
                0
        );

        targetBlock.setLayoutParams(
                targetParams
        );

        amountRow.addView(
                savedBlock
        );

        amountRow.addView(
                targetBlock
        );

        content.addView(
                amountRow
        );

        /*
         * Progress
         */

        LinearLayout progressHeader =
                new LinearLayout(this);

        progressHeader.setOrientation(
                LinearLayout.HORIZONTAL
        );

        progressHeader.setGravity(
                Gravity.CENTER_VERTICAL
        );

        LinearLayout.LayoutParams progressHeaderParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        progressHeaderParams.setMargins(
                0,
                dpToPx(14),
                0,
                0
        );

        progressHeader.setLayoutParams(
                progressHeaderParams
        );

        TextView progressLabel =
                createText(
                        "Goal progress",
                        11,
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        false
                );

        LinearLayout.LayoutParams progressLabelParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        progressLabel.setLayoutParams(
                progressLabelParams
        );

        TextView progressValue =
                createText(
                        progress + "%",
                        12,
                        statusColor,
                        true
                );

        progressHeader.addView(
                progressLabel
        );

        progressHeader.addView(
                progressValue
        );

        content.addView(
                progressHeader
        );

        ProgressBar progressBar =
                new ProgressBar(
                        this,
                        null,
                        android.R.attr
                                .progressBarStyleHorizontal
                );

        progressBar.setMax(100);
        progressBar.setProgress(progress);

        progressBar.setProgressTintList(
                ColorStateList.valueOf(
                        statusColor
                )
        );

        progressBar.setProgressBackgroundTintList(
                ColorStateList.valueOf(
                        getColorValue(
                                R.color.app_outline_soft
                        )
                )
        );

        LinearLayout.LayoutParams progressParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dpToPx(9)
                );

        progressParams.setMargins(
                0,
                dpToPx(7),
                0,
                0
        );

        progressBar.setLayoutParams(
                progressParams
        );

        content.addView(
                progressBar
        );

        /*
         * Remaining or Completed information
         */

        LinearLayout remainingBox;

        if (isCompleted) {
            double extraSaving =
                    Math.max(
                            savedAmount - targetAmount,
                            0
                    );

            String description =
                    extraSaving > 0
                            ? "Extra saved: "
                              + formatAmount(extraSaving)
                            : "You have reached this savings target.";

            remainingBox =
                    createInformationBox(
                            "Goal Achieved",
                            formatAmount(savedAmount),
                            description,
                            getColorValue(
                                    R.color.success
                            ),
                            getColorValue(
                                    R.color.success_surface
                            ),
                            getColorValue(
                                    R.color.success_outline
                            ),
                            "✓"
                    );

        } else {
            remainingBox =
                    createInformationBox(
                            "Remaining Amount",
                            formatAmount(
                                    remainingAmount
                            ),
                            buildRemainingMessage(
                                    remainingAmount,
                                    goal.getTargetDate()
                            ),
                            goalColor,
                            createTranslucentColor(
                                    goalColor,
                                    15
                            ),
                            createTranslucentColor(
                                    goalColor,
                                    55
                            ),
                            "₹"
                    );
        }

        LinearLayout.LayoutParams remainingParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        remainingParams.setMargins(
                0,
                dpToPx(13),
                0,
                0
        );

        remainingBox.setLayoutParams(
                remainingParams
        );

        content.addView(
                remainingBox
        );

        /*
         * Actions
         */

        LinearLayout actionRow =
                new LinearLayout(this);

        actionRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        LinearLayout.LayoutParams actionRowParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dpToPx(46)
                );

        actionRowParams.setMargins(
                0,
                dpToPx(13),
                0,
                0
        );

        actionRow.setLayoutParams(
                actionRowParams
        );

        MaterialButton btnAddSaving =
                createAddSavingButton(
                        isCompleted,
                        goalColor
                );

        MaterialButton btnDelete =
                createDeleteButton();

        LinearLayout.LayoutParams addParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        1f
                );

        addParams.setMargins(
                0,
                0,
                dpToPx(5),
                0
        );

        btnAddSaving.setLayoutParams(
                addParams
        );

        LinearLayout.LayoutParams deleteParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        1f
                );

        deleteParams.setMargins(
                dpToPx(5),
                0,
                0,
                0
        );

        btnDelete.setLayoutParams(
                deleteParams
        );

        btnAddSaving.setOnClickListener(
                view -> showAddSavingDialog(
                        goal
                )
        );

        btnDelete.setOnClickListener(
                view -> confirmDelete(
                        goal
                )
        );

        BubbleTouchAnimator.apply(
                btnAddSaving
        );

        BubbleTouchAnimator.apply(
                btnDelete
        );

        actionRow.addView(
                btnAddSaving
        );

        actionRow.addView(
                btnDelete
        );

        content.addView(
                actionRow
        );

        card.addView(
                content
        );

        goalContainer.addView(
                card
        );
    }

    private TextView createGoalIcon(
            String goalName,
            int goalColor
    ) {
        String iconText = "G";

        if (goalName != null
                && !goalName.trim().isEmpty()) {

            iconText =
                    goalName.trim()
                            .substring(0, 1)
                            .toUpperCase(
                                    Locale.getDefault()
                            );
        }

        TextView icon =
                new TextView(this);

        icon.setText(iconText);
        icon.setTextColor(goalColor);
        icon.setTextSize(17);

        icon.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        icon.setGravity(
                Gravity.CENTER
        );

        GradientDrawable background =
                new GradientDrawable();

        background.setColor(
                createTranslucentColor(
                        goalColor,
                        18
                )
        );

        background.setStroke(
                dpToPx(1),
                createTranslucentColor(
                        goalColor,
                        65
                )
        );

        background.setCornerRadius(
                dpToPx(14)
        );

        icon.setBackground(
                background
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        dpToPx(46),
                        dpToPx(46)
                );

        icon.setLayoutParams(
                params
        );

        return icon;
    }

    private TextView createStatusBadge(
            String text,
            int textColor,
            int backgroundColor,
            int outlineColor
    ) {
        TextView badge =
                new TextView(this);

        badge.setText(text);
        badge.setTextColor(textColor);
        badge.setTextSize(10);

        badge.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        badge.setGravity(
                Gravity.CENTER
        );

        badge.setPadding(
                dpToPx(10),
                0,
                dpToPx(10),
                0
        );

        GradientDrawable background =
                new GradientDrawable();

        background.setColor(
                backgroundColor
        );

        background.setStroke(
                dpToPx(1),
                outlineColor
        );

        background.setCornerRadius(
                dpToPx(13)
        );

        badge.setBackground(
                background
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        dpToPx(30)
                );

        badge.setLayoutParams(
                params
        );

        return badge;
    }

    private LinearLayout createMetricBlock(
            String label,
            String value,
            int valueColor,
            int backgroundColor,
            int outlineColor
    ) {
        LinearLayout container =
                new LinearLayout(this);

        container.setOrientation(
                LinearLayout.VERTICAL
        );

        container.setPadding(
                dpToPx(12),
                dpToPx(11),
                dpToPx(12),
                dpToPx(11)
        );

        GradientDrawable background =
                new GradientDrawable();

        background.setColor(
                backgroundColor
        );

        background.setStroke(
                dpToPx(1),
                outlineColor
        );

        background.setCornerRadius(
                dpToPx(13)
        );

        container.setBackground(
                background
        );

        TextView labelView =
                createText(
                        label,
                        10,
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        false
                );

        TextView valueView =
                createText(
                        value,
                        14,
                        valueColor,
                        true
                );

        valueView.setMaxLines(1);

        LinearLayout.LayoutParams valueParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        valueParams.setMargins(
                0,
                dpToPx(4),
                0,
                0
        );

        valueView.setLayoutParams(
                valueParams
        );

        container.addView(
                labelView
        );

        container.addView(
                valueView
        );

        return container;
    }

    private LinearLayout createInformationBox(
            String title,
            String value,
            String description,
            int accentColor,
            int backgroundColor,
            int outlineColor,
            String iconText
    ) {
        LinearLayout container =
                new LinearLayout(this);

        container.setOrientation(
                LinearLayout.HORIZONTAL
        );

        container.setGravity(
                Gravity.CENTER_VERTICAL
        );

        container.setPadding(
                dpToPx(12),
                dpToPx(11),
                dpToPx(12),
                dpToPx(11)
        );

        GradientDrawable background =
                new GradientDrawable();

        background.setColor(
                backgroundColor
        );

        background.setStroke(
                dpToPx(1),
                outlineColor
        );

        background.setCornerRadius(
                dpToPx(13)
        );

        container.setBackground(
                background
        );

        TextView icon =
                new TextView(this);

        icon.setText(iconText);
        icon.setTextColor(accentColor);
        icon.setTextSize(16);

        icon.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        icon.setGravity(
                Gravity.CENTER
        );

        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(
                        dpToPx(34),
                        dpToPx(34)
                );

        icon.setLayoutParams(
                iconParams
        );

        container.addView(
                icon
        );

        LinearLayout textContainer =
                new LinearLayout(this);

        textContainer.setOrientation(
                LinearLayout.VERTICAL
        );

        LinearLayout.LayoutParams textParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        textParams.setMargins(
                dpToPx(9),
                0,
                0,
                0
        );

        textContainer.setLayoutParams(
                textParams
        );

        TextView titleView =
                createText(
                        title,
                        10,
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        false
                );

        TextView valueView =
                createText(
                        value,
                        15,
                        accentColor,
                        true
                );

        LinearLayout.LayoutParams valueParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        valueParams.setMargins(
                0,
                dpToPx(2),
                0,
                0
        );

        valueView.setLayoutParams(
                valueParams
        );

        TextView descriptionView =
                createText(
                        description,
                        10,
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        false
                );

        LinearLayout.LayoutParams descriptionParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        descriptionParams.setMargins(
                0,
                dpToPx(3),
                0,
                0
        );

        descriptionView.setLayoutParams(
                descriptionParams
        );

        textContainer.addView(
                titleView
        );

        textContainer.addView(
                valueView
        );

        textContainer.addView(
                descriptionView
        );

        container.addView(
                textContainer
        );

        return container;
    }

    private MaterialButton createAddSavingButton(
            boolean completed,
            int goalColor
    ) {
        MaterialButton button =
                new MaterialButton(this);

        button.setText(
                completed
                        ? "Add More Saving"
                        : "Add Saving"
        );

        button.setTextSize(11);
        button.setTextColor(goalColor);
        button.setAllCaps(false);

        button.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        button.setCornerRadius(
                dpToPx(13)
        );

        button.setInsetTop(0);
        button.setInsetBottom(0);

        button.setBackgroundTintList(
                ColorStateList.valueOf(
                        createTranslucentColor(
                                goalColor,
                                18
                        )
                )
        );

        button.setStrokeColor(
                ColorStateList.valueOf(
                        createTranslucentColor(
                                goalColor,
                                70
                        )
                )
        );

        button.setStrokeWidth(
                dpToPx(1)
        );

        return button;
    }

    private MaterialButton createDeleteButton() {
        MaterialButton button =
                new MaterialButton(this);

        button.setText("Delete Goal");
        button.setTextSize(11);

        button.setTextColor(
                getColorValue(
                        R.color.expense
                )
        );

        button.setAllCaps(false);

        button.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        button.setCornerRadius(
                dpToPx(13)
        );

        button.setInsetTop(0);
        button.setInsetBottom(0);

        button.setBackgroundTintList(
                ColorStateList.valueOf(
                        getColorValue(
                                R.color.error_surface
                        )
                )
        );

        button.setStrokeColor(
                ColorStateList.valueOf(
                        getColorValue(
                                R.color.error_outline
                        )
                )
        );

        button.setStrokeWidth(
                dpToPx(1)
        );

        return button;
    }

    private void showAddSavingDialog(
            Goal goal
    ) {
        int goalColor =
                parseGoalColor(
                        goal.getColor()
                );

        double savedAmount =
                Math.max(
                        goal.getSavedAmount(),
                        0
                );

        double targetAmount =
                Math.max(
                        goal.getTargetAmount(),
                        0
                );

        double remainingAmount =
                Math.max(
                        targetAmount - savedAmount,
                        0
                );

        LinearLayout dialogLayout =
                new LinearLayout(this);

        dialogLayout.setOrientation(
                LinearLayout.VERTICAL
        );

        dialogLayout.setPadding(
                dpToPx(22),
                dpToPx(6),
                dpToPx(22),
                dpToPx(6)
        );

        LinearLayout summaryBox =
                createInformationBox(
                        remainingAmount > 0
                                ? "Remaining Amount"
                                : "Goal Completed",
                        remainingAmount > 0
                                ? formatAmount(
                                remainingAmount
                        )
                                : formatAmount(
                                savedAmount
                        ),
                        remainingAmount > 0
                                ? "Current saving: "
                                  + formatAmount(
                                savedAmount
                        )
                                : "You can still add more saving to this goal.",
                        remainingAmount > 0
                                ? goalColor
                                : getColorValue(
                                R.color.success
                        ),
                        remainingAmount > 0
                                ? createTranslucentColor(
                                goalColor,
                                15
                        )
                                : getColorValue(
                                R.color.success_surface
                        ),
                        remainingAmount > 0
                                ? createTranslucentColor(
                                goalColor,
                                55
                        )
                                : getColorValue(
                                R.color.success_outline
                        ),
                        remainingAmount > 0
                                ? "₹"
                                : "✓"
                );

        dialogLayout.addView(
                summaryBox
        );

        TextInputLayout inputAmount =
                new TextInputLayout(this);

        inputAmount.setBoxBackgroundMode(
                TextInputLayout.BOX_BACKGROUND_OUTLINE
        );

        inputAmount.setHint(
                "Saving amount"
        );

        inputAmount.setPrefixText(
                "₹  "
        );

        inputAmount.setBoxStrokeColor(
                goalColor
        );

        inputAmount.setBoxCornerRadii(
                dpToPx(14),
                dpToPx(14),
                dpToPx(14),
                dpToPx(14)
        );

        LinearLayout.LayoutParams inputParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        inputParams.setMargins(
                0,
                dpToPx(15),
                0,
                0
        );

        inputAmount.setLayoutParams(
                inputParams
        );

        TextInputEditText etAmount =
                new TextInputEditText(this);

        etAmount.setInputType(
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL
        );

        etAmount.setTextSize(17);
        etAmount.setSingleLine(true);
        etAmount.setMinHeight(dpToPx(56));

        inputAmount.addView(
                etAmount
        );

        dialogLayout.addView(
                inputAmount
        );

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(
                                "Add Saving"
                        )
                        .setMessage(
                                safeText(
                                        goal.getName(),
                                        "Savings Goal"
                                )
                        )
                        .setView(dialogLayout)
                        .setNegativeButton(
                                "Cancel",
                                null
                        )
                        .setPositiveButton(
                                "Add Saving",
                                null
                        )
                        .create();

        dialog.setOnShowListener(listener -> {
            android.widget.Button positiveButton =
                    dialog.getButton(
                            AlertDialog.BUTTON_POSITIVE
                    );

            positiveButton.setTextColor(
                    goalColor
            );

            positiveButton.setOnClickListener(view -> {
                String amountText =
                        etAmount.getText() == null
                                ? ""
                                : etAmount
                                .getText()
                                .toString()
                                .trim();

                double amount;

                try {
                    amount =
                            Double.parseDouble(
                                    amountText
                            );

                } catch (Exception exception) {
                    inputAmount.setError(
                            "Enter a valid amount"
                    );

                    etAmount.requestFocus();
                    return;
                }

                if (amount <= 0) {
                    inputAmount.setError(
                            "Amount must be greater than zero"
                    );

                    etAmount.requestFocus();
                    return;
                }

                inputAmount.setError(null);
                positiveButton.setEnabled(false);
                positiveButton.setText("Adding...");

                double previousSaving =
                        goal.getSavedAmount();

                goal.setSavedAmount(
                        previousSaving + amount
                );

                new Thread(() -> {
                    try {
                        DatabaseClient
                                .getInstance(
                                        getApplicationContext()
                                )
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

                    } catch (Exception exception) {
                        goal.setSavedAmount(
                                previousSaving
                        );

                        runOnUiThread(() -> {
                            positiveButton.setEnabled(true);

                            positiveButton.setText(
                                    "Add Saving"
                            );

                            Toast.makeText(
                                    GoalActivity.this,
                                    "Unable to add saving",
                                    Toast.LENGTH_SHORT
                            ).show();
                        });
                    }
                }).start();
            });
        });

        dialog.show();
    }

    private void confirmDelete(
            Goal goal
    ) {
        new AlertDialog.Builder(this)
                .setTitle(
                        "Delete Goal"
                )
                .setMessage(
                        "Delete \""
                                + safeText(
                                goal.getName(),
                                "this goal"
                        )
                                + "\"?\n\n"
                                + "This goal and its saved progress will be removed."
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .setPositiveButton(
                        "Delete",
                        (dialog, which) ->
                                deleteGoal(goal)
                )
                .show();
    }

    private void deleteGoal(
            Goal goal
    ) {
        new Thread(() -> {
            try {
                DatabaseClient
                        .getInstance(
                                getApplicationContext()
                        )
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

            } catch (Exception exception) {
                runOnUiThread(() ->
                        Toast.makeText(
                                GoalActivity.this,
                                "Unable to delete goal",
                                Toast.LENGTH_SHORT
                        ).show()
                );
            }
        }).start();
    }

    private String buildRemainingMessage(
            double remainingAmount,
            String targetDate
    ) {
        if (remainingAmount <= 0) {
            return "Your savings target has been completed.";
        }

        String date =
                formatVisibleDate(
                        targetDate
                );

        return "Save this amount before "
                + date;
    }

    private int calculateProgress(
            double saved,
            double target
    ) {
        if (target <= 0) {
            return 0;
        }

        int progress =
                (int) Math.round(
                        (saved / target) * 100
                );

        return Math.min(
                Math.max(progress, 0),
                100
        );
    }

    private String getColorCode(
            String colorName
    ) {
        if ("Blue".equalsIgnoreCase(
                colorName
        )) {
            return "#1565C0";

        } else if ("Purple".equalsIgnoreCase(
                colorName
        )) {
            return "#6A1B9A";

        } else if ("Orange".equalsIgnoreCase(
                colorName
        )) {
            return "#EF6C00";

        } else if ("Red".equalsIgnoreCase(
                colorName
        )) {
            return "#D32F2F";

        } else if ("Teal".equalsIgnoreCase(
                colorName
        )) {
            return "#00838F";

        } else {
            return "#2E7D32";
        }
    }

    private int parseGoalColor(
            String colorCode
    ) {
        try {
            if (colorCode == null
                    || colorCode.trim().isEmpty()) {

                return getColorValue(
                        R.color.success
                );
            }

            return Color.parseColor(
                    colorCode.trim()
            );

        } catch (Exception exception) {
            return getColorValue(
                    R.color.success
            );
        }
    }

    private String formatVisibleDate(
            String storedDate
    ) {
        if (storedDate == null
                || storedDate.trim().isEmpty()) {

            return "Not selected";
        }

        try {
            SimpleDateFormat databaseFormat =
                    new SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale.US
                    );

            databaseFormat.setLenient(false);

            Date date =
                    databaseFormat.parse(
                            storedDate.trim()
                    );

            if (date == null) {
                return storedDate;
            }

            return new SimpleDateFormat(
                    "dd MMM yyyy",
                    Locale.ENGLISH
            ).format(date);

        } catch (Exception exception) {
            return storedDate;
        }
    }

    private TextView createText(
            String text,
            float size,
            int color,
            boolean bold
    ) {
        TextView textView =
                new TextView(this);

        textView.setText(text);
        textView.setTextSize(size);
        textView.setTextColor(color);

        if (bold) {
            textView.setTypeface(
                    Typeface.DEFAULT,
                    Typeface.BOLD
            );
        }

        return textView;
    }

    private String safeText(
            String value,
            String fallback
    ) {
        if (value == null
                || value.trim().isEmpty()) {

            return fallback;
        }

        return value.trim();
    }

    private String formatAmount(
            double amount
    ) {
        NumberFormat formatter =
                NumberFormat.getNumberInstance(
                        new Locale("en", "IN")
                );

        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);

        return "₹"
                + formatter.format(amount);
    }

    private int createTranslucentColor(
            int baseColor,
            int alpha
    ) {
        return Color.argb(
                alpha,
                Color.red(baseColor),
                Color.green(baseColor),
                Color.blue(baseColor)
        );
    }

    private int getColorValue(
            int colorResource
    ) {
        return ContextCompat.getColor(
                this,
                colorResource
        );
    }

    private int dpToPx(
            int dp
    ) {
        return Math.round(
                dp
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }
}