package com.example.moneymanagerpro.activities;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
import com.google.android.material.button.MaterialButton;

public class MoreFeaturesActivity extends AppCompatActivity {

    private LinearLayout featureContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_more_features);

        featureContainer = findViewById(R.id.featureContainer);

        addFeature("🏦  Accounts and Wallets", AccountActivity.class, "#1565C0");
        addFeature("🔄  Transfer Money", TransferActivity.class, "#6A1B9A");
        addFeature("🎯  Savings Goals", GoalActivity.class, "#2E7D32");
        addFeature("📊  Analytics and Smart Insights", AnalyticsActivity.class, "#00838F");
        addFeature("📅  Cash Flow Calendar", CalendarActivity.class, "#3949AB");
        addFeature("🔁  Recurring Entries", RecurringActivity.class, "#EF6C00");
        addFeature("🎯  Category Budgets", BudgetActivity.class, "#C62828");
        addFeature("🤝  Loans and EMI", LoanActivity.class, "#5D4037");
        addFeature("📤  Export Reports", ExportActivity.class, "#37474F");
        addFeature("💾  Full Data Backup", BackupActivity.class, "#455A64");
        addFeature("⚙️  Settings and Security", SettingsActivity.class, "#424242");
    }

    private void addFeature(String title, Class<?> activityClass, String color) {
        MaterialButton button = new MaterialButton(this);

        button.setText(title);
        button.setTextSize(16);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setPadding(28, 0, 20, 0);
        button.setBackgroundTintList(
                ColorStateList.valueOf(Color.parseColor(color))
        );
        button.setCornerRadius(28);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                64
        );
        params.setMargins(0, 0, 0, 14);

        button.setLayoutParams(params);

        BubbleTouchAnimator.apply(button);

        button.setOnClickListener(v ->
                startActivity(new Intent(MoreFeaturesActivity.this, activityClass))
        );

        featureContainer.addView(button);
    }
}