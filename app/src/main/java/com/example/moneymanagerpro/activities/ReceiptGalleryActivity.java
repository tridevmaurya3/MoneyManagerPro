package com.example.moneymanagerpro.activities;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Transaction;
import com.example.moneymanagerpro.utils.ReceiptStore;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ReceiptGalleryActivity extends AppCompatActivity {

    private TextView txtReceiptCount;
    private TextView txtEmptyReceipts;
    private LinearLayout receiptContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receipt_gallery);

        TextView btnBack = findViewById(R.id.btnBack);
        txtReceiptCount = findViewById(R.id.txtReceiptCount);
        txtEmptyReceipts = findViewById(R.id.txtEmptyReceipts);
        receiptContainer = findViewById(R.id.receiptContainer);

        btnBack.setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadReceipts();
    }

    private void loadReceipts() {
        new Thread(() -> {
            List<Transaction> transactions = DatabaseClient
                    .getInstance(getApplicationContext())
                    .getAppDatabase()
                    .transactionDao()
                    .getAllTransactions();

            List<ReceiptItem> receiptItems = new ArrayList<>();

            for (Transaction transaction : transactions) {
                String receiptUri = ReceiptStore.getReceiptUri(
                        getApplicationContext(),
                        transaction.getId()
                );

                if (receiptUri != null && !receiptUri.trim().isEmpty()) {
                    receiptItems.add(new ReceiptItem(transaction, receiptUri));
                }
            }

            runOnUiThread(() -> showReceipts(receiptItems));
        }).start();
    }

    private void showReceipts(List<ReceiptItem> receiptItems) {
        receiptContainer.removeAllViews();

        txtReceiptCount.setText(
                receiptItems.size() + " bill photo"
                        + (receiptItems.size() == 1 ? "" : "s")
        );

        if (receiptItems.isEmpty()) {
            txtEmptyReceipts.setVisibility(View.VISIBLE);
            return;
        }

        txtEmptyReceipts.setVisibility(View.GONE);

        for (ReceiptItem item : receiptItems) {
            receiptContainer.addView(createReceiptCard(item));
        }
    }

    private MaterialCardView createReceiptCard(ReceiptItem item) {
        MaterialCardView cardView = new MaterialCardView(this);
        cardView.setCardBackgroundColor(Color.WHITE);
        cardView.setRadius(dp(22));
        cardView.setCardElevation(dp(5));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );

        cardParams.setMargins(0, 0, 0, dp(14));
        cardView.setLayoutParams(cardParams);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(12), dp(12), dp(12));

        ImageView imagePreview = new ImageView(this);
        imagePreview.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(180)
        ));

        imagePreview.setBackgroundColor(Color.parseColor("#F1F5F9"));
        imagePreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imagePreview.setContentDescription("Saved bill photo");

        try {
            imagePreview.setImageURI(Uri.parse(item.receiptUri));
        } catch (Exception exception) {
            imagePreview.setImageResource(android.R.drawable.ic_menu_report_image);
        }

        imagePreview.setOnClickListener(v -> showFullReceipt(item.receiptUri));

        TextView category = new TextView(this);
        category.setText(
                item.transaction.getCategory() == null
                        ? "Expense Receipt"
                        : item.transaction.getCategory()
        );

        category.setTextColor(Color.parseColor("#172033"));
        category.setTextSize(18);
        category.setTypeface(null, android.graphics.Typeface.BOLD);
        category.setPadding(dp(4), dp(13), dp(4), 0);

        TextView details = new TextView(this);
        details.setText(
                formatMoney(item.transaction.getAmount())
                        + "  •  "
                        + visibleDate(item.transaction.getDate())
        );

        details.setTextColor(Color.parseColor("#64748B"));
        details.setTextSize(14);
        details.setPadding(dp(4), dp(4), dp(4), 0);

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setPadding(0, dp(12), 0, 0);

        MaterialButton btnView = new MaterialButton(this);
        btnView.setText("View Photo");
        btnView.setTextColor(Color.WHITE);
        btnView.setTextSize(13);
        btnView.setAllCaps(false);
        btnView.setBackgroundTintList(
                ColorStateList.valueOf(Color.parseColor("#D32F2F"))
        );

        LinearLayout.LayoutParams viewParams = new LinearLayout.LayoutParams(
                0,
                dp(48),
                1
        );

        viewParams.setMargins(0, 0, dp(5), 0);
        btnView.setLayoutParams(viewParams);
        btnView.setOnClickListener(v -> showFullReceipt(item.receiptUri));

        MaterialButton btnRemove = new MaterialButton(this);
        btnRemove.setText("Remove");
        btnRemove.setTextColor(Color.parseColor("#B91C1C"));
        btnRemove.setTextSize(13);
        btnRemove.setAllCaps(false);
        btnRemove.setBackgroundTintList(
                ColorStateList.valueOf(Color.parseColor("#FFF1F2"))
        );

        LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(
                0,
                dp(48),
                1
        );

        removeParams.setMargins(dp(5), 0, 0, 0);
        btnRemove.setLayoutParams(removeParams);

        btnRemove.setOnClickListener(v -> removeReceipt(item));

        buttonRow.addView(btnView);
        buttonRow.addView(btnRemove);

        content.addView(imagePreview);
        content.addView(category);
        content.addView(details);
        content.addView(buttonRow);

        cardView.addView(content);

        return cardView;
    }

    private void showFullReceipt(String receiptUri) {
        ImageView fullImage = new ImageView(this);
        fullImage.setAdjustViewBounds(true);
        fullImage.setPadding(dp(8), dp(8), dp(8), dp(8));
        fullImage.setBackgroundColor(Color.WHITE);

        try {
            fullImage.setImageURI(Uri.parse(receiptUri));
        } catch (Exception exception) {
            fullImage.setImageResource(android.R.drawable.ic_menu_report_image);
        }

        new AlertDialog.Builder(this)
                .setView(fullImage)
                .setNegativeButton("Close", null)
                .show();
    }

    private void removeReceipt(ReceiptItem item) {
        new AlertDialog.Builder(this)
                .setTitle("Remove bill photo?")
                .setMessage("The expense entry will remain. Only its attached photo will be removed.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (dialog, which) -> {
                    ReceiptStore.removeReceiptUri(
                            getApplicationContext(),
                            item.transaction.getId()
                    );

                    Toast.makeText(
                            this,
                            "Bill photo removed",
                            Toast.LENGTH_SHORT
                    ).show();

                    loadReceipts();
                })
                .show();
    }

    private String visibleDate(String date) {
        if (date == null || date.trim().isEmpty()) {
            return "No date";
        }

        if (date.length() >= 10) {
            return date.substring(0, 10);
        }

        return date;
    }

    private String formatMoney(double amount) {
        NumberFormat formatter = NumberFormat.getNumberInstance(new Locale("en", "IN"));
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);

        return "₹" + formatter.format(amount);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private static class ReceiptItem {
        Transaction transaction;
        String receiptUri;

        ReceiptItem(Transaction transaction, String receiptUri) {
            this.transaction = transaction;
            this.receiptUri = receiptUri;
        }
    }
}