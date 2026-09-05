package com.example.moneymanagerpro.floating;

import android.os.Bundle;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.activities.AddExpenseActivity;

/**
 * Reuses the existing AddExpenseActivity form, UPI flow, receipt handling,
 * item details and transaction save logic inside a translucent floating window.
 */
public class FloatingAddExpenseActivity extends AddExpenseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_MoneyManagerPro_FloatingEntry);
        super.onCreate(savedInstanceState);
        FloatingEntryWindow.apply(this);
    }
}
