package com.example.moneymanagerpro.activities;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.model.Transaction;
import com.example.moneymanagerpro.utils.BubbleTouchAnimator;
import com.example.moneymanagerpro.utils.ReceiptStore;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReceiptGalleryActivity extends AppCompatActivity {

    private TextView txtReceiptCount;
    private TextView txtEmptyReceipts;

    private LinearLayout receiptContainer;

    private View emptyStateCard;

    private int loadingRequestVersion = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receipt_gallery);

        bindViews();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadReceipts();
    }

    private void bindViews() {
        TextView btnBack =
                findViewById(R.id.btnBack);

        txtReceiptCount =
                findViewById(R.id.txtReceiptCount);

        txtEmptyReceipts =
                findViewById(R.id.txtEmptyReceipts);

        receiptContainer =
                findViewById(R.id.receiptContainer);

        View emptyTextParent =
                (View) txtEmptyReceipts.getParent();

        if (emptyTextParent.getParent() instanceof View) {
            emptyStateCard =
                    (View) emptyTextParent.getParent();
        } else {
            emptyStateCard =
                    emptyTextParent;
        }

        btnBack.setOnClickListener(
                view -> finish()
        );
    }

    private void loadReceipts() {
        int currentRequest =
                ++loadingRequestVersion;

        txtReceiptCount.setText(
                "Loading receipts..."
        );

        new Thread(() -> {
            try {
                List<Transaction> transactions =
                        DatabaseClient
                                .getInstance(
                                        getApplicationContext()
                                )
                                .getAppDatabase()
                                .transactionDao()
                                .getAllTransactions();

                List<ReceiptItem> receiptItems =
                        new ArrayList<>();

                if (transactions != null) {
                    for (Transaction transaction : transactions) {
                        if (transaction == null) {
                            continue;
                        }

                        String receiptUri;

                        try {
                            receiptUri =
                                    ReceiptStore.getReceiptUri(
                                            getApplicationContext(),
                                            transaction.getId()
                                    );

                        } catch (Exception exception) {
                            receiptUri = null;
                        }

                        if (receiptUri != null
                                && !receiptUri.trim().isEmpty()) {

                            receiptItems.add(
                                    new ReceiptItem(
                                            transaction,
                                            receiptUri.trim()
                                    )
                            );
                        }
                    }
                }

                runOnUiThread(() -> {
                    if (currentRequest
                            != loadingRequestVersion) {

                        return;
                    }

                    showReceipts(receiptItems);
                });

            } catch (Exception exception) {
                runOnUiThread(() -> {
                    if (currentRequest
                            != loadingRequestVersion) {

                        return;
                    }

                    showReceipts(
                            new ArrayList<>()
                    );

                    Toast.makeText(
                            ReceiptGalleryActivity.this,
                            "Unable to load receipt photos",
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }
        }).start();
    }

    private void showReceipts(
            List<ReceiptItem> receiptItems
    ) {
        receiptContainer.removeAllViews();

        int receiptCount =
                receiptItems == null
                        ? 0
                        : receiptItems.size();

        txtReceiptCount.setText(
                receiptCount
                        + " bill photo"
                        + (
                        receiptCount == 1
                                ? ""
                                : "s"
                )
        );

        boolean isEmpty =
                receiptCount == 0;

        txtEmptyReceipts.setVisibility(
                isEmpty
                        ? View.VISIBLE
                        : View.GONE
        );

        if (emptyStateCard != null) {
            emptyStateCard.setVisibility(
                    isEmpty
                            ? View.VISIBLE
                            : View.GONE
            );
        }

        if (isEmpty) {
            return;
        }

        for (ReceiptItem item : receiptItems) {
            if (item != null
                    && item.transaction != null) {

                receiptContainer.addView(
                        createReceiptCard(item)
                );
            }
        }
    }

    private MaterialCardView createReceiptCard(
            ReceiptItem item
    ) {
        boolean incomeReceipt =
                "INCOME".equalsIgnoreCase(
                        safeText(
                                item.transaction.getType(),
                                "EXPENSE"
                        )
                );

        int accentColor =
                incomeReceipt
                        ? getColorValue(
                        R.color.success
                )
                        : getColorValue(
                        R.color.expense
                );

        int accentSurface =
                incomeReceipt
                        ? getColorValue(
                        R.color.success_surface
                )
                        : getColorValue(
                        R.color.expense_surface
                );

        int accentOutline =
                incomeReceipt
                        ? getColorValue(
                        R.color.success_outline
                )
                        : getColorValue(
                        R.color.expense_outline
                );

        MaterialCardView cardView =
                new MaterialCardView(this);

        cardView.setCardBackgroundColor(
                getColorValue(
                        R.color.app_surface
                )
        );

        cardView.setRadius(
                dp(20)
        );

        cardView.setCardElevation(
                dp(1)
        );

        cardView.setStrokeWidth(
                dp(1)
        );

        cardView.setStrokeColor(
                accentOutline
        );

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        cardParams.setMargins(
                0,
                dp(6),
                0,
                dp(7)
        );

        cardView.setLayoutParams(
                cardParams
        );

        LinearLayout content =
                new LinearLayout(this);

        content.setOrientation(
                LinearLayout.VERTICAL
        );

        content.setPadding(
                dp(12),
                dp(12),
                dp(12),
                dp(13)
        );

        /*
         * Receipt image
         */

        MaterialCardView imageCard =
                new MaterialCardView(this);

        imageCard.setCardBackgroundColor(
                getColorValue(
                        R.color.app_surface_soft
                )
        );

        imageCard.setRadius(
                dp(16)
        );

        imageCard.setCardElevation(0);

        imageCard.setStrokeWidth(
                dp(1)
        );

        imageCard.setStrokeColor(
                getColorValue(
                        R.color.app_outline_soft
                )
        );

        LinearLayout.LayoutParams imageCardParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(190)
                );

        imageCard.setLayoutParams(
                imageCardParams
        );

        ImageView imagePreview =
                new ImageView(this);

        imagePreview.setScaleType(
                ImageView.ScaleType.CENTER_CROP
        );

        imagePreview.setContentDescription(
                "Saved receipt photo"
        );

        imagePreview.setBackgroundColor(
                getColorValue(
                        R.color.app_surface_soft
                )
        );

        FrameLayout.LayoutParams imageParams =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                );

        imagePreview.setLayoutParams(
                imageParams
        );

        loadReceiptImage(
                imagePreview,
                item.receiptUri
        );

        imagePreview.setOnClickListener(
                view -> showFullReceipt(item)
        );

        imageCard.setOnClickListener(
                view -> showFullReceipt(item)
        );

        imageCard.addView(
                imagePreview
        );

        BubbleTouchAnimator.apply(
                imageCard
        );

        content.addView(
                imageCard
        );

        /*
         * Receipt header
         */

        LinearLayout headerRow =
                new LinearLayout(this);

        headerRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        headerRow.setGravity(
                Gravity.CENTER_VERTICAL
        );

        LinearLayout.LayoutParams headerParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        headerParams.setMargins(
                dp(3),
                dp(13),
                dp(3),
                0
        );

        headerRow.setLayoutParams(
                headerParams
        );

        TextView receiptIcon =
                createReceiptIcon(
                        incomeReceipt,
                        accentColor,
                        accentSurface,
                        accentOutline
                );

        headerRow.addView(
                receiptIcon
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
                dp(10),
                0,
                dp(8),
                0
        );

        titleContainer.setLayoutParams(
                titleParams
        );

        TextView category =
                createText(
                        safeText(
                                item.transaction.getCategory(),
                                incomeReceipt
                                        ? "Income Receipt"
                                        : "Expense Receipt"
                        ),
                        16,
                        getColorValue(
                                R.color.app_text_primary
                        ),
                        true
                );

        category.setMaxLines(2);

        TextView transactionType =
                createText(
                        incomeReceipt
                                ? "Income proof"
                                : "Expense bill",
                        10,
                        getColorValue(
                                R.color.app_text_secondary
                        ),
                        false
                );

        LinearLayout.LayoutParams typeParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        typeParams.setMargins(
                0,
                dp(3),
                0,
                0
        );

        transactionType.setLayoutParams(
                typeParams
        );

        titleContainer.addView(
                category
        );

        titleContainer.addView(
                transactionType
        );

        headerRow.addView(
                titleContainer
        );

        TextView amountBadge =
                createAmountBadge(
                        formatMoney(
                                item.transaction.getAmount()
                        ),
                        accentColor,
                        accentSurface,
                        accentOutline
                );

        headerRow.addView(
                amountBadge
        );

        content.addView(
                headerRow
        );

        /*
         * Receipt details
         */

        LinearLayout detailsBox =
                createDetailsBox(
                        item,
                        accentColor
                );

        LinearLayout.LayoutParams detailsParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        detailsParams.setMargins(
                0,
                dp(12),
                0,
                0
        );

        detailsBox.setLayoutParams(
                detailsParams
        );

        content.addView(
                detailsBox
        );

        /*
         * Action buttons
         */

        LinearLayout buttonRow =
                new LinearLayout(this);

        buttonRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        LinearLayout.LayoutParams buttonRowParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(46)
                );

        buttonRowParams.setMargins(
                0,
                dp(12),
                0,
                0
        );

        buttonRow.setLayoutParams(
                buttonRowParams
        );

        MaterialButton btnView =
                createActionButton(
                        "View Photo",
                        accentColor,
                        accentSurface,
                        accentOutline
                );

        LinearLayout.LayoutParams viewParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        1f
                );

        viewParams.setMargins(
                0,
                0,
                dp(5),
                0
        );

        btnView.setLayoutParams(
                viewParams
        );

        btnView.setOnClickListener(
                view -> showFullReceipt(item)
        );

        MaterialButton btnRemove =
                createActionButton(
                        "Remove Photo",
                        getColorValue(
                                R.color.expense
                        ),
                        getColorValue(
                                R.color.expense_surface
                        ),
                        getColorValue(
                                R.color.expense_outline
                        )
                );

        LinearLayout.LayoutParams removeParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        1f
                );

        removeParams.setMargins(
                dp(5),
                0,
                0,
                0
        );

        btnRemove.setLayoutParams(
                removeParams
        );

        btnRemove.setOnClickListener(
                view -> removeReceipt(item)
        );

        BubbleTouchAnimator.apply(
                btnView
        );

        BubbleTouchAnimator.apply(
                btnRemove
        );

        buttonRow.addView(
                btnView
        );

        buttonRow.addView(
                btnRemove
        );

        content.addView(
                buttonRow
        );

        cardView.addView(
                content
        );

        return cardView;
    }

    private TextView createReceiptIcon(
            boolean incomeReceipt,
            int accentColor,
            int backgroundColor,
            int outlineColor
    ) {
        TextView icon =
                createText(
                        incomeReceipt
                                ? "↑"
                                : "↓",
                        18,
                        accentColor,
                        true
                );

        icon.setGravity(
                Gravity.CENTER
        );

        icon.setBackground(
                createRoundedDrawable(
                        backgroundColor,
                        outlineColor,
                        13
                )
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        dp(42),
                        dp(42)
                );

        icon.setLayoutParams(
                params
        );

        return icon;
    }

    private TextView createAmountBadge(
            String amount,
            int accentColor,
            int backgroundColor,
            int outlineColor
    ) {
        TextView badge =
                createText(
                        amount,
                        11,
                        accentColor,
                        true
                );

        badge.setGravity(
                Gravity.CENTER
        );

        badge.setPadding(
                dp(9),
                0,
                dp(9),
                0
        );

        badge.setMaxLines(1);

        badge.setBackground(
                createRoundedDrawable(
                        backgroundColor,
                        outlineColor,
                        12
                )
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        dp(32)
                );

        badge.setLayoutParams(
                params
        );

        return badge;
    }

    private LinearLayout createDetailsBox(
            ReceiptItem item,
            int accentColor
    ) {
        LinearLayout container =
                new LinearLayout(this);

        container.setOrientation(
                LinearLayout.VERTICAL
        );

        container.setPadding(
                dp(12),
                dp(10),
                dp(12),
                dp(10)
        );

        container.setBackground(
                createRoundedDrawable(
                        getColorValue(
                                R.color.app_surface_soft
                        ),
                        getColorValue(
                                R.color.app_outline_soft
                        ),
                        13
                )
        );

        addDetailRow(
                container,
                "Transaction Date",
                visibleDate(
                        item.transaction.getDate()
                ),
                accentColor
        );

        addDetailRow(
                container,
                "Account",
                safeText(
                        item.transaction.getAccount(),
                        "Not added"
                ),
                getColorValue(
                        R.color.app_text_primary
                )
        );

        String note =
                safeText(
                        item.transaction.getNote(),
                        ""
                );

        if (!note.isEmpty()) {
            addDetailRow(
                    container,
                    "Note",
                    note,
                    getColorValue(
                            R.color.app_text_primary
                    )
            );
        }

        return container;
    }

    private void addDetailRow(
            LinearLayout parent,
            String label,
            String value,
            int valueColor
    ) {
        LinearLayout row =
                new LinearLayout(this);

        row.setOrientation(
                LinearLayout.HORIZONTAL
        );

        row.setGravity(
                Gravity.TOP
        );

        LinearLayout.LayoutParams rowParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        if (parent.getChildCount() > 0) {
            rowParams.setMargins(
                    0,
                    dp(7),
                    0,
                    0
            );
        }

        row.setLayoutParams(
                rowParams
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

        labelView.setLayoutParams(
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        0.9f
                )
        );

        TextView valueView =
                createText(
                        value,
                        10,
                        valueColor,
                        true
                );

        valueView.setGravity(
                Gravity.END
        );

        valueView.setLayoutParams(
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1.4f
                )
        );

        row.addView(
                labelView
        );

        row.addView(
                valueView
        );

        parent.addView(
                row
        );
    }

    private MaterialButton createActionButton(
            String text,
            int textColor,
            int backgroundColor,
            int outlineColor
    ) {
        MaterialButton button =
                new MaterialButton(this);

        button.setText(text);
        button.setTextSize(10);
        button.setTextColor(textColor);
        button.setAllCaps(false);

        button.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        button.setCornerRadius(
                dp(13)
        );

        button.setBackgroundTintList(
                ColorStateList.valueOf(
                        backgroundColor
                )
        );

        button.setStrokeColor(
                ColorStateList.valueOf(
                        outlineColor
                )
        );

        button.setStrokeWidth(
                dp(1)
        );

        button.setPadding(
                dp(2),
                0,
                dp(2),
                0
        );

        return button;
    }

    private void showFullReceipt(
            ReceiptItem item
    ) {
        NestedScrollView scrollView =
                new NestedScrollView(this);

        scrollView.setFillViewport(true);

        LinearLayout dialogContent =
                new LinearLayout(this);

        dialogContent.setOrientation(
                LinearLayout.VERTICAL
        );

        dialogContent.setPadding(
                dp(18),
                dp(6),
                dp(18),
                dp(10)
        );

        MaterialCardView imageCard =
                new MaterialCardView(this);

        imageCard.setCardBackgroundColor(
                getColorValue(
                        R.color.app_surface_soft
                )
        );

        imageCard.setRadius(
                dp(16)
        );

        imageCard.setCardElevation(0);

        imageCard.setStrokeWidth(
                dp(1)
        );

        imageCard.setStrokeColor(
                getColorValue(
                        R.color.app_outline_soft
                )
        );

        ImageView fullImage =
                new ImageView(this);

        fullImage.setAdjustViewBounds(true);

        fullImage.setScaleType(
                ImageView.ScaleType.FIT_CENTER
        );

        fullImage.setMinimumHeight(
                dp(220)
        );

        fullImage.setMaxHeight(
                dp(560)
        );

        fullImage.setPadding(
                dp(6),
                dp(6),
                dp(6),
                dp(6)
        );

        fullImage.setBackgroundColor(
                getColorValue(
                        R.color.app_surface
                )
        );

        loadReceiptImage(
                fullImage,
                item.receiptUri
        );

        imageCard.addView(
                fullImage
        );

        dialogContent.addView(
                imageCard
        );

        LinearLayout informationBox =
                createFullImageInformation(
                        item
                );

        LinearLayout.LayoutParams informationParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        informationParams.setMargins(
                0,
                dp(12),
                0,
                0
        );

        informationBox.setLayoutParams(
                informationParams
        );

        dialogContent.addView(
                informationBox
        );

        scrollView.addView(
                dialogContent
        );

        new AlertDialog.Builder(this)
                .setTitle(
                        safeText(
                                item.transaction.getCategory(),
                                "Receipt Photo"
                        )
                )
                .setView(scrollView)
                .setPositiveButton(
                        "Close",
                        null
                )
                .show();
    }

    private LinearLayout createFullImageInformation(
            ReceiptItem item
    ) {
        LinearLayout container =
                new LinearLayout(this);

        container.setOrientation(
                LinearLayout.VERTICAL
        );

        container.setPadding(
                dp(13),
                dp(11),
                dp(13),
                dp(11)
        );

        container.setBackground(
                createRoundedDrawable(
                        getColorValue(
                                R.color.info_surface
                        ),
                        getColorValue(
                                R.color.info_outline
                        ),
                        13
                )
        );

        TextView amount =
                createText(
                        formatMoney(
                                item.transaction.getAmount()
                        ),
                        17,
                        getColorValue(
                                R.color.app_text_primary
                        ),
                        true
                );

        TextView date =
                createText(
                        visibleDate(
                                item.transaction.getDate()
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
                dp(3),
                0,
                0
        );

        date.setLayoutParams(
                dateParams
        );

        container.addView(
                amount
        );

        container.addView(
                date
        );

        return container;
    }

    private void removeReceipt(
            ReceiptItem item
    ) {
        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(
                                "Remove Receipt Photo"
                        )
                        .setMessage(
                                "Remove the photo attached to "
                                        + safeText(
                                        item.transaction.getCategory(),
                                        "this transaction"
                                )
                                        + "?\n\n"
                                        + "The transaction amount, category and other details will remain safe."
                        )
                        .setNegativeButton(
                                "Cancel",
                                null
                        )
                        .setPositiveButton(
                                "Remove Photo",
                                null
                        )
                        .create();

        dialog.setOnShowListener(listener -> {
            android.widget.Button removeButton =
                    dialog.getButton(
                            AlertDialog.BUTTON_POSITIVE
                    );

            removeButton.setTextColor(
                    getColorValue(
                            R.color.expense
                    )
            );

            removeButton.setOnClickListener(view -> {
                removeButton.setEnabled(false);

                removeButton.setText(
                        "Removing..."
                );

                try {
                    ReceiptStore.removeReceiptUri(
                            getApplicationContext(),
                            item.transaction.getId()
                    );

                    dialog.dismiss();

                    Toast.makeText(
                            ReceiptGalleryActivity.this,
                            "Receipt photo removed",
                            Toast.LENGTH_SHORT
                    ).show();

                    loadReceipts();

                } catch (Exception exception) {
                    removeButton.setEnabled(true);

                    removeButton.setText(
                            "Remove Photo"
                    );

                    Toast.makeText(
                            ReceiptGalleryActivity.this,
                            "Unable to remove receipt photo",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            });
        });

        dialog.show();
    }

    private void loadReceiptImage(
            ImageView imageView,
            String receiptUri
    ) {
        boolean imageLoaded = false;

        if (receiptUri != null
                && !receiptUri.trim().isEmpty()) {

            try {
                Uri uri =
                        Uri.parse(
                                receiptUri.trim()
                        );

                imageView.setImageURI(uri);

                imageLoaded =
                        imageView.getDrawable() != null;

            } catch (Exception ignored) {
                imageLoaded = false;
            }
        }

        if (!imageLoaded) {
            imageView.setScaleType(
                    ImageView.ScaleType.CENTER
            );

            imageView.setImageResource(
                    android.R.drawable.ic_menu_report_image
            );

            imageView.setContentDescription(
                    "Receipt image is unavailable"
            );
        }
    }

    private String visibleDate(
            String storedDate
    ) {
        if (storedDate == null
                || storedDate.trim().isEmpty()) {

            return "Date not available";
        }

        String cleanDate =
                storedDate.trim();

        String[] patterns = {
                "yyyy-MM-dd HH:mm:ss.SSS",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd'T'HH:mm:ss.SSS",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm",
                "yyyy-MM-dd",
                "dd MMMM yyyy HH:mm:ss",
                "dd MMMM yyyy HH:mm",
                "dd MMM yyyy HH:mm:ss",
                "dd MMM yyyy HH:mm",
                "dd MMMM yyyy",
                "dd MMM yyyy",
                "dd/MM/yyyy HH:mm:ss",
                "dd/MM/yyyy HH:mm",
                "dd/MM/yyyy"
        };

        for (String pattern : patterns) {
            try {
                SimpleDateFormat inputFormat =
                        new SimpleDateFormat(
                                pattern,
                                Locale.ENGLISH
                        );

                inputFormat.setLenient(false);

                Date parsedDate =
                        inputFormat.parse(
                                cleanDate
                        );

                if (parsedDate != null) {
                    return new SimpleDateFormat(
                            "dd MMM yyyy, hh:mm a",
                            Locale.ENGLISH
                    ).format(parsedDate);
                }

            } catch (Exception ignored) {
                // Try the next supported format.
            }
        }

        return cleanDate;
    }

    private TextView createText(
            String text,
            float textSize,
            int textColor,
            boolean bold
    ) {
        TextView textView =
                new TextView(this);

        textView.setText(text);
        textView.setTextSize(textSize);
        textView.setTextColor(textColor);

        if (bold) {
            textView.setTypeface(
                    Typeface.DEFAULT,
                    Typeface.BOLD
            );
        }

        return textView;
    }

    private GradientDrawable createRoundedDrawable(
            int backgroundColor,
            int outlineColor,
            int radiusDp
    ) {
        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(
                backgroundColor
        );

        drawable.setStroke(
                dp(1),
                outlineColor
        );

        drawable.setCornerRadius(
                dp(radiusDp)
        );

        return drawable;
    }

    private String formatMoney(
            double amount
    ) {
        NumberFormat formatter =
                NumberFormat.getNumberInstance(
                        new Locale(
                                "en",
                                "IN"
                        )
                );

        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);

        return "₹"
                + formatter.format(amount);
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

    private int getColorValue(
            int colorResource
    ) {
        return ContextCompat.getColor(
                this,
                colorResource
        );
    }

    private int dp(
            int value
    ) {
        return Math.round(
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    private static class ReceiptItem {

        private final Transaction transaction;
        private final String receiptUri;

        private ReceiptItem(
                Transaction transaction,
                String receiptUri
        ) {
            this.transaction =
                    transaction;

            this.receiptUri =
                    receiptUri;
        }
    }
}