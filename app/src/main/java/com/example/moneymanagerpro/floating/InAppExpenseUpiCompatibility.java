package com.example.moneymanagerpro.floating;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.activities.AddExpenseActivity;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Keeps the normal in-app Add Expense form visually consistent while replacing
 * its old third-party UPI-ID / Money-Manager QR payment flow with one compact
 * native Scan & Pay action.
 *
 * The original AddExpenseActivity save, item, category, account, date, note,
 * receipt and database logic is not changed here.
 */
final class InAppExpenseUpiCompatibility {

    private static final Map<Activity, Controller> CONTROLLERS =
            new WeakHashMap<>();
    private static boolean registered;

    private InAppExpenseUpiCompatibility() {
    }

    static synchronized void register(Application application) {
        if (registered || application == null) {
            return;
        }
        registered = true;

        application.registerActivityLifecycleCallbacks(
                new Application.ActivityLifecycleCallbacks() {
                    @Override
                    public void onActivityCreated(
                            @NonNull Activity activity,
                            @Nullable Bundle state
                    ) {
                        attachExpenseController(activity);
                    }

                    @Override
                    public void onActivityStarted(@NonNull Activity activity) {
                        attachExpenseController(activity);
                    }

                    @Override
                    public void onActivityResumed(@NonNull Activity activity) {
                        // Re-bind on every resume as well. This intentionally
                        // wins over the legacy AddExpenseActivity UPI click
                        // listener, so OEM/activity timing can never fall back
                        // to the old full-screen Android chooser flow.
                        attachExpenseController(activity);
                    }

                    @Override
                    public void onActivityDestroyed(@NonNull Activity activity) {
                        CONTROLLERS.remove(activity);
                    }

                    @Override public void onActivityPaused(@NonNull Activity a) {}
                    @Override public void onActivityStopped(@NonNull Activity a) {}
                    @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle b) {}
                }
        );
    }

    private static void attachExpenseController(Activity activity) {
        if (!(activity instanceof AddExpenseActivity)) {
            return;
        }

        Controller controller = CONTROLLERS.get(activity);
        if (controller == null) {
            controller = new Controller((AddExpenseActivity) activity);
            CONTROLLERS.put(activity, controller);
        }
        controller.attach();
    }

    private static final class Controller {
        private final AddExpenseActivity activity;
        private boolean presentationApplied;

        Controller(AddExpenseActivity activity) {
            this.activity = activity;
        }

        void attach() {
            if (activity.isFinishing()) {
                return;
            }

            View payButton = activity.findViewById(R.id.btnPayWithUpi);
            if (payButton == null) {
                return;
            }

            if (!presentationApplied) {
                presentationApplied = true;

                // Old fields are not needed when the selected UPI app owns QR
                // scan and payment. Hiding the containers removes their space.
                hideFieldWithContainer(
                        activity.findViewById(R.id.dropdownUpiEntryMode)
                );
                hide(activity.findViewById(R.id.inputUpiPayeeId));
                hide(activity.findViewById(R.id.inputUpiPayeeName));
                hide(activity.findViewById(R.id.upiPaymentResultCard));

                View root = activity.findViewById(android.R.id.content);
                TextView title = findTextView(root, "Pay with UPI App");
                if (title != null) {
                    title.setText("Scan & Pay with UPI App");
                }

                TextView subtitle = findTextView(
                        root,
                        "Enter a UPI ID manually or scan a payment QR code to fill the receiver details automatically."
                );
                if (subtitle != null) {
                    subtitle.setText(
                            "Choose your UPI app, then use that app's own Scan & Pay scanner."
                    );
                }

                if (payButton instanceof TextView) {
                    TextView button = (TextView) payButton;
                    button.setText("Scan & Pay");
                    button.setContentDescription(
                            "Choose a UPI app and use its native QR scanner"
                    );
                }
            }

            // Always bind the compact in-place PopupWindow. The Add Expense
            // screen stays visible behind it. Only after the user taps an app
            // does Android switch to that selected UPI application.
            payButton.setOnClickListener(
                    view -> NativeUpiAppPopup.show(activity, payButton)
            );
        }
    }

    private static void hide(@Nullable View view) {
        if (view != null) {
            view.setVisibility(View.GONE);
        }
    }

    private static void hideFieldWithContainer(@Nullable View field) {
        if (field == null) {
            return;
        }
        if (field.getParent() instanceof View) {
            ((View) field.getParent()).setVisibility(View.GONE);
        } else {
            field.setVisibility(View.GONE);
        }
    }

    private static TextView findTextView(
            @Nullable View view,
            String expectedText
    ) {
        if (view instanceof TextView) {
            CharSequence value = ((TextView) view).getText();
            if (value != null && expectedText.contentEquals(value)) {
                return (TextView) view;
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                TextView match = findTextView(
                        group.getChildAt(index),
                        expectedText
                );
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }
}
