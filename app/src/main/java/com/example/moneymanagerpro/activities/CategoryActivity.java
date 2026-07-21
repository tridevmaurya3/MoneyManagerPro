package com.example.moneymanagerpro.activities;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Category;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Arrays;
import java.util.List;

public class CategoryActivity extends AppCompatActivity {

    private TextInputLayout inputCategoryName;
    private TextInputEditText etCategoryName;
    private MaterialAutoCompleteTextView dropdownType;
    private MaterialAutoCompleteTextView dropdownColor;
    private MaterialButton btnSaveCategory;
    private LinearLayout categoryContainer;
    private TextView txtEmptyCategories;

    private final String[] categoryTypes = {"Income", "Expense"};

    private final String[] colorNames = {
            "Green", "Red", "Blue", "Purple", "Orange", "Teal"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category);

        inputCategoryName = findViewById(R.id.inputCategoryName);
        etCategoryName = findViewById(R.id.etCategoryName);
        dropdownType = findViewById(R.id.dropdownType);
        dropdownColor = findViewById(R.id.dropdownColor);
        btnSaveCategory = findViewById(R.id.btnSaveCategory);
        categoryContainer = findViewById(R.id.categoryContainer);
        txtEmptyCategories = findViewById(R.id.txtEmptyCategories);

        TextView btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());

        setupDropdowns();

        BubbleTouchAnimator.apply(btnSaveCategory);

        btnSaveCategory.setOnClickListener(v -> saveCategory());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCategories();
    }

    private void setupDropdowns() {
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                categoryTypes
        );

        ArrayAdapter<String> colorAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                colorNames
        );

        dropdownType.setAdapter(typeAdapter);
        dropdownColor.setAdapter(colorAdapter);

        dropdownType.setText("Expense", false);
        dropdownColor.setText("Purple", false);
    }

    private void saveCategory() {
        String name = etCategoryName.getText() == null
                ? ""
                : etCategoryName.getText().toString().trim();

        if (name.isEmpty()) {
            inputCategoryName.setError("Please enter category name");
            return;
        }

        inputCategoryName.setError(null);

        String type = dropdownType.getText().toString().trim();
        String colorName = dropdownColor.getText().toString().trim();

        Category category = new Category();
        category.setName(name);
        category.setType(type);
        category.setColor(getColorCode(colorName));

        btnSaveCategory.setEnabled(false);
        btnSaveCategory.setText("Saving Category...");

        new Thread(() -> {
            DatabaseClient.getInstance(getApplicationContext())
                    .getAppDatabase()
                    .categoryDao()
                    .insert(category);

            runOnUiThread(() -> {
                etCategoryName.setText("");
                dropdownType.setText("Expense", false);
                dropdownColor.setText("Purple", false);

                btnSaveCategory.setEnabled(true);
                btnSaveCategory.setText("Save Category");

                Toast.makeText(
                        CategoryActivity.this,
                        "Category saved successfully",
                        Toast.LENGTH_SHORT
                ).show();

                loadCategories();
            });
        }).start();
    }

    private void loadCategories() {
        new Thread(() -> {
            List<Category> categories = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .categoryDao()
                    .getAllCategories();

            runOnUiThread(() -> showCategories(categories));
        }).start();
    }

    private void showCategories(List<Category> categories) {
        categoryContainer.removeAllViews();

        txtEmptyCategories.setVisibility(
                categories.isEmpty() ? View.VISIBLE : View.GONE
        );

        for (Category category : categories) {
            addCategoryCard(category);
        }
    }

    private void addCategoryCard(Category category) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(Color.WHITE);
        card.setRadius(dpToPx(20));
        card.setCardElevation(dpToPx(4));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dpToPx(10));
        card.setLayoutParams(cardParams);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dpToPx(14), dpToPx(12), dpToPx(12), dpToPx(12));

        View colorDot = new View(this);

        GradientDrawable colorBackground = new GradientDrawable();
        colorBackground.setShape(GradientDrawable.OVAL);
        colorBackground.setColor(Color.parseColor(category.getColor()));
        colorDot.setBackground(colorBackground);

        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(
                dpToPx(42),
                dpToPx(42)
        );
        colorDot.setLayoutParams(dotParams);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        );
        detailsParams.setMargins(dpToPx(12), 0, dpToPx(6), 0);
        details.setLayoutParams(detailsParams);

        TextView txtName = new TextView(this);
        txtName.setText(category.getName());
        txtName.setTextSize(17);
        txtName.setTextColor(Color.parseColor("#172033"));
        txtName.setTypeface(Typeface.DEFAULT_BOLD);

        TextView txtType = new TextView(this);
        txtType.setText(category.getType() + " Category");
        txtType.setTextSize(13);
        txtType.setTextColor(Color.parseColor("#64748B"));

        details.addView(txtName);
        details.addView(txtType);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);

        MaterialButton btnEdit = new MaterialButton(this);
        btnEdit.setText("Edit");
        btnEdit.setTextSize(12);
        btnEdit.setTextColor(Color.WHITE);
        btnEdit.setAllCaps(false);
        btnEdit.setBackgroundColor(Color.parseColor("#3949AB"));
        btnEdit.setCornerRadius(dpToPx(16));

        MaterialButton btnDelete = new MaterialButton(this);
        btnDelete.setText("Delete");
        btnDelete.setTextSize(12);
        btnDelete.setTextColor(Color.WHITE);
        btnDelete.setAllCaps(false);
        btnDelete.setBackgroundColor(Color.parseColor("#D32F2F"));
        btnDelete.setCornerRadius(dpToPx(16));

        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                dpToPx(74),
                dpToPx(38)
        );

        btnEdit.setLayoutParams(actionParams);

        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                dpToPx(74),
                dpToPx(38)
        );
        deleteParams.setMargins(0, dpToPx(5), 0, 0);

        btnDelete.setLayoutParams(deleteParams);

        BubbleTouchAnimator.apply(btnEdit);
        BubbleTouchAnimator.apply(btnDelete);
        BubbleTouchAnimator.apply(card);

        btnEdit.setOnClickListener(v -> showEditDialog(category));
        btnDelete.setOnClickListener(v -> confirmDelete(category));

        actions.addView(btnEdit);
        actions.addView(btnDelete);

        row.addView(colorDot);
        row.addView(details);
        row.addView(actions);

        card.addView(row);
        categoryContainer.addView(card);
    }

    private void showEditDialog(Category category) {
        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(dpToPx(22), dpToPx(10), dpToPx(22), dpToPx(8));

        TextView txtNameLabel = createLabel("Category Name");
        dialogLayout.addView(txtNameLabel);

        TextInputLayout inputName = new TextInputLayout(this);
        inputName.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);

        TextInputEditText editName = new TextInputEditText(this);
        editName.setText(category.getName());
        editName.setGravity(Gravity.CENTER);
        editName.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);

        inputName.addView(editName);
        dialogLayout.addView(inputName);

        TextView txtTypeLabel = createLabel("Category Type");
        dialogLayout.addView(txtTypeLabel);

        TextInputLayout inputType = new TextInputLayout(this);
        inputType.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        inputType.setEndIconMode(TextInputLayout.END_ICON_DROPDOWN_MENU);

        MaterialAutoCompleteTextView editType = new MaterialAutoCompleteTextView(this);
        editType.setGravity(Gravity.CENTER);
        editType.setFocusable(false);
        editType.setInputType(0);
        editType.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);

        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                categoryTypes
        );

        editType.setAdapter(typeAdapter);
        editType.setText(category.getType(), false);

        inputType.addView(editType);
        dialogLayout.addView(inputType);

        TextView txtColorLabel = createLabel("Category Color");
        dialogLayout.addView(txtColorLabel);

        TextInputLayout inputColor = new TextInputLayout(this);
        inputColor.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        inputColor.setEndIconMode(TextInputLayout.END_ICON_DROPDOWN_MENU);

        MaterialAutoCompleteTextView editColor = new MaterialAutoCompleteTextView(this);
        editColor.setGravity(Gravity.CENTER);
        editColor.setFocusable(false);
        editColor.setInputType(0);
        editColor.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);

        ArrayAdapter<String> colorAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                colorNames
        );

        editColor.setAdapter(colorAdapter);
        editColor.setText(getColorName(category.getColor()), false);

        inputColor.addView(editColor);
        dialogLayout.addView(inputColor);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Edit Category")
                .setView(dialogLayout)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();

        dialog.setOnShowListener(listener -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String newName = editName.getText() == null
                        ? ""
                        : editName.getText().toString().trim();

                if (newName.isEmpty()) {
                    inputName.setError("Please enter category name");
                    return;
                }

                category.setName(newName);
                category.setType(editType.getText().toString().trim());
                category.setColor(getColorCode(editColor.getText().toString().trim()));

                new Thread(() -> {
                    DatabaseClient.getInstance(getApplicationContext())
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
            });
        });

        dialog.show();
    }

    private void confirmDelete(Category category) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Category")
                .setMessage("Do you want to delete \"" + category.getName() + "\"?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    new Thread(() -> {
                        DatabaseClient.getInstance(getApplicationContext())
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
                })
                .show();
    }

    private TextView createLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(Color.parseColor("#6A1B9A"));
        label.setTextSize(14);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dpToPx(14), 0, dpToPx(5));

        label.setLayoutParams(params);

        return label;
    }

    private String getColorCode(String colorName) {
        switch (colorName) {
            case "Green":
                return "#2E7D32";

            case "Red":
                return "#D32F2F";

            case "Blue":
                return "#1565C0";

            case "Orange":
                return "#EF6C00";

            case "Teal":
                return "#00838F";

            case "Purple":
            default:
                return "#6A1B9A";
        }
    }

    private String getColorName(String colorCode) {
        if (colorCode == null) {
            return "Purple";
        }

        if (colorCode.equalsIgnoreCase("#2E7D32")) {
            return "Green";
        }

        if (colorCode.equalsIgnoreCase("#D32F2F")) {
            return "Red";
        }

        if (colorCode.equalsIgnoreCase("#1565C0")) {
            return "Blue";
        }

        if (colorCode.equalsIgnoreCase("#EF6C00")) {
            return "Orange";
        }

        if (colorCode.equalsIgnoreCase("#00838F")) {
            return "Teal";
        }

        return "Purple";
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density);
    }
}