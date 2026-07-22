package com.example.moneymanagerpro.activities;

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
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Category;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;
import java.util.Locale;

public class CategoryActivity extends AppCompatActivity {

    private TextInputLayout inputCategoryName;
    private TextInputEditText etCategoryName;

    private MaterialAutoCompleteTextView dropdownType;
    private MaterialAutoCompleteTextView dropdownColor;

    private MaterialButton btnSaveCategory;

    private LinearLayout categoryContainer;
    private TextView txtEmptyCategories;

    private final String[] categoryTypes = {
            "Income",
            "Expense"
    };

    private final String[] colorNames = {
            "Green",
            "Red",
            "Blue",
            "Purple",
            "Orange",
            "Teal"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category);

        initializeViews();
        setupDropdowns();
        setupClickListeners();
        applyTouchAnimations();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCategories();
    }

    private void initializeViews() {
        inputCategoryName =
                findViewById(R.id.inputCategoryName);

        etCategoryName =
                findViewById(R.id.etCategoryName);

        dropdownType =
                findViewById(R.id.dropdownType);

        dropdownColor =
                findViewById(R.id.dropdownColor);

        btnSaveCategory =
                findViewById(R.id.btnSaveCategory);

        categoryContainer =
                findViewById(R.id.categoryContainer);

        txtEmptyCategories =
                findViewById(R.id.txtEmptyCategories);

        TextView btnBack =
                findViewById(R.id.btnBack);

        btnBack.setOnClickListener(
                view -> finish()
        );
    }

    private void setupDropdowns() {
        ArrayAdapter<String> typeAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_list_item_1,
                        categoryTypes
                );

        ArrayAdapter<String> colorAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_list_item_1,
                        colorNames
                );

        dropdownType.setAdapter(typeAdapter);
        dropdownColor.setAdapter(colorAdapter);

        dropdownType.setText(
                "Expense",
                false
        );

        dropdownColor.setText(
                "Purple",
                false
        );
    }

    private void setupClickListeners() {
        btnSaveCategory.setOnClickListener(
                view -> saveCategory()
        );
    }

    private void applyTouchAnimations() {
        BubbleTouchAnimator.apply(btnSaveCategory);
    }

    private void saveCategory() {
        String categoryName =
                getEditTextValue(etCategoryName);

        if (categoryName.isEmpty()) {
            inputCategoryName.setError(
                    "Please enter category name"
            );

            etCategoryName.requestFocus();
            return;
        }

        String categoryType =
                dropdownType
                        .getText()
                        .toString()
                        .trim();

        if (categoryType.isEmpty()) {
            Toast.makeText(
                    this,
                    "Please select category type",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        String colorName =
                dropdownColor
                        .getText()
                        .toString()
                        .trim();

        if (colorName.isEmpty()) {
            colorName = "Purple";
        }

        inputCategoryName.setError(null);

        String finalCategoryName =
                categoryName;

        String finalCategoryType =
                categoryType;

        String finalCategoryColor =
                getColorCode(colorName);

        setSaveButtonLoading(true);

        new Thread(() -> {
            List<Category> existingCategories =
                    DatabaseClient
                            .getInstance(
                                    getApplicationContext()
                            )
                            .getAppDatabase()
                            .categoryDao()
                            .getAllCategories();

            for (Category existingCategory :
                    existingCategories) {

                boolean sameName =
                        existingCategory
                                .getName()
                                .equalsIgnoreCase(
                                        finalCategoryName
                                );

                boolean sameType =
                        existingCategory
                                .getType()
                                .equalsIgnoreCase(
                                        finalCategoryType
                                );

                if (sameName && sameType) {
                    runOnUiThread(() -> {
                        setSaveButtonLoading(false);

                        Toast.makeText(
                                CategoryActivity.this,
                                "This category already exists",
                                Toast.LENGTH_SHORT
                        ).show();
                    });

                    return;
                }
            }

            Category category =
                    new Category();

            category.setName(
                    finalCategoryName
            );

            category.setType(
                    finalCategoryType
            );

            category.setColor(
                    finalCategoryColor
            );

            DatabaseClient
                    .getInstance(
                            getApplicationContext()
                    )
                    .getAppDatabase()
                    .categoryDao()
                    .insert(category);

            runOnUiThread(() -> {
                resetCategoryForm();
                setSaveButtonLoading(false);

                Toast.makeText(
                        CategoryActivity.this,
                        "Category saved successfully",
                        Toast.LENGTH_SHORT
                ).show();

                loadCategories();
            });
        }).start();
    }

    private void setSaveButtonLoading(
            boolean loading
    ) {
        btnSaveCategory.setEnabled(!loading);

        btnSaveCategory.setText(
                loading
                        ? "Saving Category..."
                        : "Save Category"
        );
    }

    private void resetCategoryForm() {
        etCategoryName.setText("");

        dropdownType.setText(
                "Expense",
                false
        );

        dropdownColor.setText(
                "Purple",
                false
        );

        inputCategoryName.setError(null);
    }

    private void loadCategories() {
        new Thread(() -> {
            List<Category> categories =
                    DatabaseClient
                            .getInstance(
                                    getApplicationContext()
                            )
                            .getAppDatabase()
                            .categoryDao()
                            .getAllCategories();

            runOnUiThread(() ->
                    showCategories(categories)
            );
        }).start();
    }

    private void showCategories(
            List<Category> categories
    ) {
        categoryContainer.removeAllViews();

        boolean isEmpty =
                categories == null
                        || categories.isEmpty();

        txtEmptyCategories.setVisibility(
                isEmpty
                        ? View.VISIBLE
                        : View.GONE
        );

        if (isEmpty) {
            return;
        }

        for (Category category : categories) {
            addCategoryCard(category);
        }
    }

    private void addCategoryCard(
            Category category
    ) {
        int categoryColor =
                parseCategoryColor(
                        category.getColor()
                );

        MaterialCardView card =
                new MaterialCardView(this);

        card.setCardBackgroundColor(
                getColorValue(
                        R.color.app_surface
                )
        );

        card.setRadius(dpToPx(19));
        card.setCardElevation(dpToPx(1));

        card.setStrokeColor(
                createTranslucentColor(
                        categoryColor,
                        85
                )
        );

        card.setStrokeWidth(dpToPx(1));
        card.setClickable(true);
        card.setFocusable(true);

        card.setRippleColor(
                ColorStateList.valueOf(
                        createTranslucentColor(
                                categoryColor,
                                35
                        )
                )
        );

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        cardParams.setMargins(
                0,
                0,
                0,
                dpToPx(11)
        );

        card.setLayoutParams(cardParams);

        LinearLayout mainContent =
                new LinearLayout(this);

        mainContent.setOrientation(
                LinearLayout.VERTICAL
        );

        mainContent.setPadding(
                dpToPx(15),
                dpToPx(15),
                dpToPx(14),
                dpToPx(13)
        );

        LinearLayout headerRow =
                new LinearLayout(this);

        headerRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        headerRow.setGravity(
                Gravity.CENTER_VERTICAL
        );

        TextView categoryIcon =
                createCategoryIcon(
                        category,
                        categoryColor
                );

        headerRow.addView(categoryIcon);

        LinearLayout detailsContainer =
                new LinearLayout(this);

        detailsContainer.setOrientation(
                LinearLayout.VERTICAL
        );

        LinearLayout.LayoutParams detailsParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        detailsParams.setMargins(
                dpToPx(13),
                0,
                dpToPx(8),
                0
        );

        detailsContainer.setLayoutParams(
                detailsParams
        );

        TextView txtName =
                new TextView(this);

        txtName.setText(
                category.getName()
        );

        txtName.setTextColor(
                getColorValue(
                        R.color.app_text_primary
                )
        );

        txtName.setTextSize(17);
        txtName.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );
        txtName.setMaxLines(2);

        TextView txtType =
                new TextView(this);

        txtType.setText(
                getCategoryTypeLabel(
                        category.getType()
                )
        );

        txtType.setTextColor(
                getColorValue(
                        R.color.app_text_secondary
                )
        );

        txtType.setTextSize(12);

        LinearLayout.LayoutParams typeParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        typeParams.setMargins(
                0,
                dpToPx(3),
                0,
                0
        );

        txtType.setLayoutParams(typeParams);

        detailsContainer.addView(txtName);
        detailsContainer.addView(txtType);

        headerRow.addView(detailsContainer);

        TextView typeBadge =
                createTypeBadge(
                        category.getType()
                );

        headerRow.addView(typeBadge);

        mainContent.addView(headerRow);

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
                dpToPx(14),
                0,
                dpToPx(12)
        );

        divider.setLayoutParams(
                dividerParams
        );

        mainContent.addView(divider);

        LinearLayout bottomRow =
                new LinearLayout(this);

        bottomRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        bottomRow.setGravity(
                Gravity.CENTER_VERTICAL
        );

        LinearLayout colorDetails =
                new LinearLayout(this);

        colorDetails.setOrientation(
                LinearLayout.VERTICAL
        );

        LinearLayout.LayoutParams colorDetailsParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        colorDetails.setLayoutParams(
                colorDetailsParams
        );

        TextView colorLabel =
                new TextView(this);

        colorLabel.setText("Identification Color");
        colorLabel.setTextSize(10);

        colorLabel.setTextColor(
                getColorValue(
                        R.color.app_text_secondary
                )
        );

        TextView colorValue =
                new TextView(this);

        colorValue.setText(
                getColorName(
                        category.getColor()
                )
        );

        colorValue.setTextColor(
                categoryColor
        );

        colorValue.setTextSize(14);
        colorValue.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        LinearLayout.LayoutParams colorValueParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        colorValueParams.setMargins(
                0,
                dpToPx(3),
                0,
                0
        );

        colorValue.setLayoutParams(
                colorValueParams
        );

        colorDetails.addView(colorLabel);
        colorDetails.addView(colorValue);

        bottomRow.addView(colorDetails);

        LinearLayout actionRow =
                new LinearLayout(this);

        actionRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        actionRow.setGravity(Gravity.END);

        MaterialButton btnEdit =
                createActionButton(
                        "Edit",
                        getColorValue(
                                R.color.purple
                        ),
                        getColorValue(
                                R.color.purple_surface
                        ),
                        getColorValue(
                                R.color.purple_outline
                        )
                );

        MaterialButton btnDelete =
                createActionButton(
                        "Delete",
                        getColorValue(
                                R.color.expense
                        ),
                        getColorValue(
                                R.color.error_surface
                        ),
                        getColorValue(
                                R.color.error_outline
                        )
                );

        LinearLayout.LayoutParams editParams =
                new LinearLayout.LayoutParams(
                        dpToPx(72),
                        dpToPx(42)
                );

        btnEdit.setLayoutParams(editParams);

        LinearLayout.LayoutParams deleteParams =
                new LinearLayout.LayoutParams(
                        dpToPx(78),
                        dpToPx(42)
                );

        deleteParams.setMargins(
                dpToPx(7),
                0,
                0,
                0
        );

        btnDelete.setLayoutParams(deleteParams);

        BubbleTouchAnimator.apply(btnEdit);
        BubbleTouchAnimator.apply(btnDelete);

        btnEdit.setOnClickListener(
                view -> showEditDialog(category)
        );

        btnDelete.setOnClickListener(
                view -> confirmDelete(category)
        );

        actionRow.addView(btnEdit);
        actionRow.addView(btnDelete);

        bottomRow.addView(actionRow);

        mainContent.addView(bottomRow);

        card.addView(mainContent);

        BubbleTouchAnimator.apply(card);

        card.setOnClickListener(
                view -> showEditDialog(category)
        );

        categoryContainer.addView(card);
    }

    private TextView createCategoryIcon(
            Category category,
            int categoryColor
    ) {
        TextView iconView =
                new TextView(this);

        iconView.setText(
                getCategoryIconText(
                        category.getType()
                )
        );

        iconView.setTextColor(categoryColor);
        iconView.setTextSize(20);

        iconView.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        iconView.setGravity(Gravity.CENTER);

        GradientDrawable background =
                new GradientDrawable();

        background.setShape(
                GradientDrawable.RECTANGLE
        );

        background.setColor(
                createTranslucentColor(
                        categoryColor,
                        24
                )
        );

        background.setStroke(
                dpToPx(1),
                createTranslucentColor(
                        categoryColor,
                        75
                )
        );

        background.setCornerRadius(
                dpToPx(14)
        );

        iconView.setBackground(background);

        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(
                        dpToPx(48),
                        dpToPx(48)
                );

        iconView.setLayoutParams(iconParams);

        return iconView;
    }

    private TextView createTypeBadge(
            String categoryType
    ) {
        boolean isIncome =
                categoryType != null
                        && categoryType.equalsIgnoreCase(
                        "Income"
                );

        int textColor =
                getColorValue(
                        isIncome
                                ? R.color.success
                                : R.color.expense
                );

        int backgroundColor =
                getColorValue(
                        isIncome
                                ? R.color.success_surface
                                : R.color.error_surface
                );

        int strokeColor =
                getColorValue(
                        isIncome
                                ? R.color.success_outline
                                : R.color.error_outline
                );

        TextView badge =
                new TextView(this);

        badge.setText(
                isIncome
                        ? "Income"
                        : "Expense"
        );

        badge.setTextColor(textColor);
        badge.setTextSize(11);

        badge.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        badge.setGravity(Gravity.CENTER);

        badge.setPadding(
                dpToPx(10),
                0,
                dpToPx(10),
                0
        );

        GradientDrawable background =
                new GradientDrawable();

        background.setColor(backgroundColor);

        background.setStroke(
                dpToPx(1),
                strokeColor
        );

        background.setCornerRadius(
                dpToPx(14)
        );

        badge.setBackground(background);

        LinearLayout.LayoutParams badgeParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        dpToPx(32)
                );

        badge.setLayoutParams(badgeParams);

        return badge;
    }

    private MaterialButton createActionButton(
            String text,
            int textColor,
            int backgroundColor,
            int strokeColor
    ) {
        MaterialButton button =
                new MaterialButton(this);

        button.setText(text);
        button.setTextSize(12);
        button.setTextColor(textColor);
        button.setAllCaps(false);

        button.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        button.setGravity(Gravity.CENTER);
        button.setCornerRadius(dpToPx(13));

        button.setBackgroundTintList(
                ColorStateList.valueOf(
                        backgroundColor
                )
        );

        button.setStrokeColor(
                ColorStateList.valueOf(
                        strokeColor
                )
        );

        button.setStrokeWidth(dpToPx(1));

        button.setInsetTop(0);
        button.setInsetBottom(0);

        button.setPadding(
                dpToPx(6),
                0,
                dpToPx(6),
                0
        );

        return button;
    }

    private void showEditDialog(
            Category category
    ) {
        int categoryColor =
                parseCategoryColor(
                        category.getColor()
                );

        LinearLayout dialogLayout =
                new LinearLayout(this);

        dialogLayout.setOrientation(
                LinearLayout.VERTICAL
        );

        dialogLayout.setPadding(
                dpToPx(22),
                dpToPx(8),
                dpToPx(22),
                dpToPx(8)
        );

        TextView iconView =
                createCategoryIcon(
                        category,
                        categoryColor
                );

        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(
                        dpToPx(54),
                        dpToPx(54)
                );

        iconParams.gravity =
                Gravity.CENTER_HORIZONTAL;

        iconView.setLayoutParams(iconParams);

        dialogLayout.addView(iconView);

        TextView titleView =
                new TextView(this);

        titleView.setText(
                category.getName()
        );

        titleView.setTextColor(
                getColorValue(
                        R.color.app_text_primary
                )
        );

        titleView.setTextSize(20);

        titleView.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        titleView.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        titleParams.setMargins(
                0,
                dpToPx(10),
                0,
                dpToPx(2)
        );

        titleView.setLayoutParams(titleParams);

        dialogLayout.addView(titleView);

        TextView descriptionView =
                new TextView(this);

        descriptionView.setText(
                "Update the category name, type or identification color."
        );

        descriptionView.setTextColor(
                getColorValue(
                        R.color.app_text_secondary
                )
        );

        descriptionView.setTextSize(12);
        descriptionView.setGravity(Gravity.CENTER);

        descriptionView.setLineSpacing(
                dpToPx(2),
                1f
        );

        dialogLayout.addView(descriptionView);

        TextView nameLabel =
                createLabel("Category Name");

        dialogLayout.addView(nameLabel);

        TextInputLayout inputName =
                createDialogInputLayout(
                        "Category name"
                );

        TextInputEditText editName =
                new TextInputEditText(this);

        editName.setText(
                category.getName()
        );

        editName.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_WORDS
        );

        editName.setSingleLine(true);
        editName.setTextSize(15);

        editName.setTextColor(
                getColorValue(
                        R.color.app_text_primary
                )
        );

        inputName.addView(editName);

        dialogLayout.addView(inputName);

        TextView typeLabel =
                createLabel("Category Type");

        dialogLayout.addView(typeLabel);

        TextInputLayout inputType =
                createDialogInputLayout(
                        "Select category type"
                );

        inputType.setEndIconMode(
                TextInputLayout.END_ICON_DROPDOWN_MENU
        );

        MaterialAutoCompleteTextView editType =
                createDialogDropdown();

        ArrayAdapter<String> typeAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_list_item_1,
                        categoryTypes
                );

        editType.setAdapter(typeAdapter);

        editType.setText(
                category.getType(),
                false
        );

        inputType.addView(editType);

        dialogLayout.addView(inputType);

        TextView colorLabel =
                createLabel("Category Color");

        dialogLayout.addView(colorLabel);

        TextInputLayout inputColor =
                createDialogInputLayout(
                        "Select category color"
                );

        inputColor.setEndIconMode(
                TextInputLayout.END_ICON_DROPDOWN_MENU
        );

        MaterialAutoCompleteTextView editColor =
                createDialogDropdown();

        ArrayAdapter<String> colorAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_list_item_1,
                        colorNames
                );

        editColor.setAdapter(colorAdapter);

        editColor.setText(
                getColorName(
                        category.getColor()
                ),
                false
        );

        inputColor.addView(editColor);

        dialogLayout.addView(inputColor);

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle("Edit Category")
                        .setView(dialogLayout)
                        .setNegativeButton(
                                "Cancel",
                                null
                        )
                        .setPositiveButton(
                                "Save",
                                null
                        )
                        .create();

        dialog.setOnShowListener(listener -> {
            dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
            ).setTextColor(
                    getColorValue(
                            R.color.purple
                    )
            );

            dialog.getButton(
                    AlertDialog.BUTTON_NEGATIVE
            ).setTextColor(
                    getColorValue(
                            R.color.app_text_secondary
                    )
            );

            dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener(view -> {
                String newName =
                        getEditTextValue(editName);

                if (newName.isEmpty()) {
                    inputName.setError(
                            "Please enter category name"
                    );

                    editName.requestFocus();
                    return;
                }

                inputName.setError(null);

                String newType =
                        editType
                                .getText()
                                .toString()
                                .trim();

                String newColor =
                        editColor
                                .getText()
                                .toString()
                                .trim();

                if (newType.isEmpty()) {
                    newType = "Expense";
                }

                if (newColor.isEmpty()) {
                    newColor = "Purple";
                }

                category.setName(newName);
                category.setType(newType);

                category.setColor(
                        getColorCode(newColor)
                );

                dialog.getButton(
                        AlertDialog.BUTTON_POSITIVE
                ).setEnabled(false);

                updateCategory(
                        category,
                        dialog
                );
            });
        });

        dialog.show();
    }

    private TextInputLayout createDialogInputLayout(
            String hint
    ) {
        TextInputLayout inputLayout =
                new TextInputLayout(this);

        inputLayout.setHint(hint);

        inputLayout.setBoxBackgroundMode(
                TextInputLayout.BOX_BACKGROUND_OUTLINE
        );

        inputLayout.setBoxBackgroundColor(
                getColorValue(
                        R.color.app_surface
                )
        );

        inputLayout.setBoxStrokeColor(
                getColorValue(
                        R.color.purple
                )
        );

        inputLayout.setBoxCornerRadii(
                dpToPx(14),
                dpToPx(14),
                dpToPx(14),
                dpToPx(14)
        );

        inputLayout.setHintTextColor(
                ColorStateList.valueOf(
                        getColorValue(
                                R.color.app_text_secondary
                        )
                )
        );

        inputLayout.setLayoutParams(
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        return inputLayout;
    }

    private MaterialAutoCompleteTextView
    createDialogDropdown() {
        MaterialAutoCompleteTextView dropdown =
                new MaterialAutoCompleteTextView(this);

        dropdown.setFocusable(false);
        dropdown.setInputType(InputType.TYPE_NULL);
        dropdown.setTextSize(15);

        dropdown.setTextColor(
                getColorValue(
                        R.color.app_text_primary
                )
        );

        dropdown.setPadding(
                dpToPx(16),
                0,
                dpToPx(12),
                0
        );

        dropdown.setMinHeight(
                dpToPx(56)
        );

        return dropdown;
    }

    private void updateCategory(
            Category category,
            AlertDialog dialog
    ) {
        new Thread(() -> {
            DatabaseClient
                    .getInstance(
                            getApplicationContext()
                    )
                    .getAppDatabase()
                    .categoryDao()
                    .update(category);

            runOnUiThread(() -> {
                dialog.dismiss();

                Toast.makeText(
                        CategoryActivity.this,
                        "Category updated",
                        Toast.LENGTH_SHORT
                ).show();

                loadCategories();
            });
        }).start();
    }

    private void confirmDelete(
            Category category
    ) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Category")
                .setMessage(
                        "Delete \""
                                + category.getName()
                                + "\"?\n\n"
                                + "Existing transactions using this category "
                                + "will remain in transaction history."
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .setPositiveButton(
                        "Delete",
                        (dialog, which) ->
                                deleteCategory(category)
                )
                .show();
    }

    private void deleteCategory(
            Category category
    ) {
        new Thread(() -> {
            DatabaseClient
                    .getInstance(
                            getApplicationContext()
                    )
                    .getAppDatabase()
                    .categoryDao()
                    .delete(category);

            runOnUiThread(() -> {
                Toast.makeText(
                        CategoryActivity.this,
                        "Category deleted",
                        Toast.LENGTH_SHORT
                ).show();

                loadCategories();
            });
        }).start();
    }

    private TextView createLabel(
            String text
    ) {
        TextView label =
                new TextView(this);

        label.setText(text);

        label.setTextColor(
                getColorValue(
                        R.color.app_text_primary
                )
        );

        label.setTextSize(14);

        label.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        params.setMargins(
                0,
                dpToPx(16),
                0,
                dpToPx(7)
        );

        label.setLayoutParams(params);

        return label;
    }

    private String getCategoryTypeLabel(
            String categoryType
    ) {
        if (categoryType == null
                || categoryType.trim().isEmpty()) {

            return "Financial Category";
        }

        if (categoryType.equalsIgnoreCase(
                "Income"
        )) {
            return "Money Received Category";
        }

        return "Money Spent Category";
    }

    private String getCategoryIconText(
            String categoryType
    ) {
        if (categoryType != null
                && categoryType.equalsIgnoreCase(
                "Income"
        )) {

            return "+";
        }

        return "−";
    }

    private String getColorCode(
            String colorName
    ) {
        if (colorName == null) {
            return "#6B4FA3";
        }

        switch (colorName) {
            case "Green":
                return "#107C10";

            case "Red":
                return "#C42B1C";

            case "Blue":
                return "#0F6CBD";

            case "Orange":
                return "#A15A00";

            case "Teal":
                return "#087A81";

            case "Purple":
            default:
                return "#6B4FA3";
        }
    }

    private String getColorName(
            String colorCode
    ) {
        if (colorCode == null) {
            return "Purple";
        }

        if (colorCode.equalsIgnoreCase("#107C10")
                || colorCode.equalsIgnoreCase("#2E7D32")) {

            return "Green";
        }

        if (colorCode.equalsIgnoreCase("#C42B1C")
                || colorCode.equalsIgnoreCase("#D32F2F")) {

            return "Red";
        }

        if (colorCode.equalsIgnoreCase("#0F6CBD")
                || colorCode.equalsIgnoreCase("#1565C0")) {

            return "Blue";
        }

        if (colorCode.equalsIgnoreCase("#A15A00")
                || colorCode.equalsIgnoreCase("#EF6C00")) {

            return "Orange";
        }

        if (colorCode.equalsIgnoreCase("#087A81")
                || colorCode.equalsIgnoreCase("#00838F")) {

            return "Teal";
        }

        return "Purple";
    }

    private int parseCategoryColor(
            String colorCode
    ) {
        if (colorCode == null
                || colorCode.trim().isEmpty()) {

            return getColorValue(
                    R.color.purple
            );
        }

        try {
            return Color.parseColor(colorCode);

        } catch (Exception exception) {
            return getColorValue(
                    R.color.purple
            );
        }
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

    private String getEditTextValue(
            TextInputEditText editText
    ) {
        if (editText.getText() == null) {
            return "";
        }

        return editText
                .getText()
                .toString()
                .trim();
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
        float density =
                getResources()
                        .getDisplayMetrics()
                        .density;

        return Math.round(
                dp * density
        );
    }
}