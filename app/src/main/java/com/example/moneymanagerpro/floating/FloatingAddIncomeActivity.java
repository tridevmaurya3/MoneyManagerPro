package com.example.moneymanagerpro.floating;

import android.os.Bundle;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.activities.AddIncomeActivity;

/**
 * Reuses the existing AddIncomeActivity form and save flow, but presents it
 * as a translucent floating window when launched from the always-on bubble.
 */
public class FloatingAddIncomeActivity extends AddIncomeActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_MoneyManagerPro_FloatingEntry);
        super.onCreate(savedInstanceState);
        FloatingEntryWindow.apply(this);
    }
}
