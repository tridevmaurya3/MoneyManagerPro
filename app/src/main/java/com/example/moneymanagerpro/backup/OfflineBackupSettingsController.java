package com.example.moneymanagerpro.backup;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Context;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.cloud.BackupSchedulePreferences;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Controls the Automatic Offline Backup section shown inside BackupActivity.
 *
 * Responsibilities:
 *
 * 1. Load saved offline backup settings.
 * 2. Display frequency, preferred time, weekly day and monthly date.
 * 3. Show only the controls relevant to Weekly or Monthly schedules.
 * 4. Save updated settings.
 * 5. Apply or cancel the unique WorkManager schedule.
 * 6. Display last backup and next scheduled backup status.
 *
 * This controller does not create a backup directly. Manual backups are
 * handled by BackupActivity and automatic backups are handled by
 * OfflineAutomaticBackupWorker.
 */
public final class OfflineBackupSettingsController {

    private static final String[] FREQUENCY_LABELS = {
            "Off",
            "Manual only",
            "Daily",
            "Weekly",
            "Monthly"
    };

    private static final String[] WEEK_DAY_LABELS = {
            "Sunday",
            "Monday",
            "Tuesday",
            "Wednesday",
            "Thursday",
            "Friday",
            "Saturday"
    };

    private final Activity activity;

    private final Context applicationContext;

    private final BackupSchedulePreferences schedulePreferences;

    private final MaterialAutoCompleteTextView
            dropdownOfflineFrequency;

    private final MaterialAutoCompleteTextView
            dropdownOfflineWeeklyDay;

    private final MaterialAutoCompleteTextView
            dropdownOfflineMonthlyDay;

    private final MaterialButton btnOfflineBackupTime;

    private final MaterialButton btnSaveOfflineSchedule;

    private final MaterialSwitch switchOfflineChargingOnly;

    private final LinearLayout groupOfflineWeeklyDay;

    private final LinearLayout groupOfflineMonthlyDay;

    private final TextView txtOfflineScheduleStatus;

    private final TextView txtOfflineNextBackup;

    private int selectedHour =
            BackupSchedulePreferences.DEFAULT_PREFERRED_HOUR;

    private int selectedMinute =
            BackupSchedulePreferences.DEFAULT_PREFERRED_MINUTE;

    private boolean listenersAttached =
            false;

    public OfflineBackupSettingsController(
            @NonNull Activity activity
    ) {
        this.activity =
                activity;

        applicationContext =
                activity.getApplicationContext();

        schedulePreferences =
                new BackupSchedulePreferences(
                        applicationContext
                );

        dropdownOfflineFrequency =
                requireView(
                        R.id.dropdownOfflineFrequency
                );

        dropdownOfflineWeeklyDay =
                requireView(
                        R.id.dropdownOfflineWeeklyDay
                );

        dropdownOfflineMonthlyDay =
                requireView(
                        R.id.dropdownOfflineMonthlyDay
                );

        btnOfflineBackupTime =
                requireView(
                        R.id.btnOfflineBackupTime
                );

        btnSaveOfflineSchedule =
                requireView(
                        R.id.btnSaveOfflineSchedule
                );

        switchOfflineChargingOnly =
                requireView(
                        R.id.switchOfflineChargingOnly
                );

        groupOfflineWeeklyDay =
                requireView(
                        R.id.groupOfflineWeeklyDay
                );

        groupOfflineMonthlyDay =
                requireView(
                        R.id.groupOfflineMonthlyDay
                );

        txtOfflineScheduleStatus =
                requireView(
                        R.id.txtOfflineScheduleStatus
                );

        txtOfflineNextBackup =
                requireView(
                        R.id.txtOfflineNextBackup
                );
    }

    /**
     * Initializes dropdowns, listeners and saved values.
     *
     * Call once from BackupActivity.onCreate().
     */
    public void initialize() {
        setupFrequencyDropdown();

        setupWeeklyDayDropdown();

        setupMonthlyDayDropdown();

        attachListeners();

        refresh();
    }

    /**
     * Reloads saved settings and status.
     *
     * BackupActivity can call this after:
     *
     * - returning to the screen,
     * - selecting a new backup folder,
     * - completing a manual backup,
     * - restoring a backup.
     */
    public void refresh() {
        BackupSchedulePreferences.ScheduleSettings settings =
                schedulePreferences.getOfflineSchedule();

        selectedHour =
                settings.getPreferredHour();

        selectedMinute =
                settings.getPreferredMinute();

        dropdownOfflineFrequency.setText(
                settings
                        .getFrequency()
                        .getDisplayName(),
                false
        );

        dropdownOfflineWeeklyDay.setText(
                getWeekDayLabel(
                        settings.getWeeklyDayOfWeek()
                ),
                false
        );

        dropdownOfflineMonthlyDay.setText(
                String.valueOf(
                        settings.getMonthlyDayOfMonth()
                ),
                false
        );

        switchOfflineChargingOnly.setChecked(
                settings.isChargingOnly()
        );

        updateTimeButtonText();

        updateConditionalGroups(
                settings.getFrequency()
        );

        updateStatusViews(
                settings,
                schedulePreferences.getOfflineStatus()
        );
    }

    /**
     * Re-applies an existing automatic schedule after the user selects
     * or changes the backup folder.
     */
    public void onBackupFolderChanged() {
        BackupSchedulePreferences.ScheduleSettings settings =
                schedulePreferences.getOfflineSchedule();

        if (!settings.isAutomaticEnabled()) {
            refresh();

            return;
        }

        try {
            OfflineBackupScheduler.ScheduleResult scheduleResult =
                    OfflineBackupScheduler.onBackupFolderChanged(
                            applicationContext
                    );

            Toast.makeText(
                    activity,
                    scheduleResult.getMessage(),
                    Toast.LENGTH_LONG
            ).show();

        } catch (Exception exception) {
            Toast.makeText(
                    activity,
                    safeMessage(
                            exception,
                            "Offline backup schedule could not be applied."
                    ),
                    Toast.LENGTH_LONG
            ).show();
        }

        refresh();
    }

    private void setupFrequencyDropdown() {
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        activity,
                        android.R.layout.simple_list_item_1,
                        FREQUENCY_LABELS
                );

        dropdownOfflineFrequency.setAdapter(
                adapter
        );
    }

    private void setupWeeklyDayDropdown() {
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        activity,
                        android.R.layout.simple_list_item_1,
                        WEEK_DAY_LABELS
                );

        dropdownOfflineWeeklyDay.setAdapter(
                adapter
        );
    }

    private void setupMonthlyDayDropdown() {
        List<String> monthlyDates =
                new ArrayList<>();

        for (int day = 1;
             day <= 28;
             day++) {

            monthlyDates.add(
                    String.valueOf(
                            day
                    )
            );
        }

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        activity,
                        android.R.layout.simple_list_item_1,
                        monthlyDates
                );

        dropdownOfflineMonthlyDay.setAdapter(
                adapter
        );
    }

    private void attachListeners() {
        if (listenersAttached) {
            return;
        }

        listenersAttached =
                true;

        dropdownOfflineFrequency.setOnItemClickListener(
                (parent, view, position, id) -> {
                    BackupSchedulePreferences.BackupFrequency frequency =
                            frequencyFromDisplayName(
                                    String.valueOf(
                                            parent.getItemAtPosition(
                                                    position
                                            )
                                    )
                            );

                    updateConditionalGroups(
                            frequency
                    );
                }
        );

        btnOfflineBackupTime.setOnClickListener(
                view -> showTimePicker()
        );

        btnSaveOfflineSchedule.setOnClickListener(
                view -> saveSchedule()
        );
    }

    private void showTimePicker() {
        TimePickerDialog timePickerDialog =
                new TimePickerDialog(
                        activity,
                        (view, hourOfDay, minute) -> {
                            selectedHour =
                                    hourOfDay;

                            selectedMinute =
                                    minute;

                            updateTimeButtonText();
                        },
                        selectedHour,
                        selectedMinute,
                        DateFormat.is24HourFormat(
                                activity
                        )
                );

        timePickerDialog.setTitle(
                "Preferred backup time"
        );

        timePickerDialog.show();
    }

    private void saveSchedule() {
        btnSaveOfflineSchedule.setEnabled(
                false
        );

        try {
            BackupSchedulePreferences.BackupFrequency frequency =
                    frequencyFromDisplayName(
                            dropdownOfflineFrequency
                                    .getText()
                                    .toString()
                    );

            int weeklyDay =
                    getCalendarDay(
                            dropdownOfflineWeeklyDay
                                    .getText()
                                    .toString()
                    );

            int monthlyDay =
                    parseMonthlyDay(
                            dropdownOfflineMonthlyDay
                                    .getText()
                                    .toString()
                    );

            BackupSchedulePreferences.ScheduleSettings settings =
                    new BackupSchedulePreferences.ScheduleSettings(
                            frequency,
                            false,
                            switchOfflineChargingOnly.isChecked(),
                            selectedHour,
                            selectedMinute,
                            weeklyDay,
                            monthlyDay
                    );

            schedulePreferences.saveOfflineSchedule(
                    settings
            );

            OfflineBackupScheduler.ScheduleResult scheduleResult =
                    OfflineBackupScheduler.applySavedSchedule(
                            applicationContext
                    );

            Toast.makeText(
                    activity,
                    createSaveSuccessMessage(
                            scheduleResult
                    ),
                    Toast.LENGTH_LONG
            ).show();

        } catch (OfflineBackupScheduler
                         .BackupFolderNotReadyException exception) {

            Toast.makeText(
                    activity,
                    "Schedule save हो गया, लेकिन automatic backup शुरू "
                            + "करने के लिए पहले backup folder चुनें।",
                    Toast.LENGTH_LONG
            ).show();

        } catch (Exception exception) {
            Toast.makeText(
                    activity,
                    safeMessage(
                            exception,
                            "Offline backup schedule could not be saved."
                    ),
                    Toast.LENGTH_LONG
            ).show();

        } finally {
            btnSaveOfflineSchedule.setEnabled(
                    true
            );

            refresh();
        }
    }

    @NonNull
    private String createSaveSuccessMessage(
            @NonNull OfflineBackupScheduler.ScheduleResult result
    ) {
        if (!result.isScheduled()) {
            return "Offline backup setting saved: "
                    + result.getFrequencyDisplayName();
        }

        return "Automatic offline backup scheduled. Next check: "
                + formatDateTime(
                result.getNextPreferredRunAtMillis()
        );
    }

    private void updateConditionalGroups(
            @NonNull BackupSchedulePreferences.BackupFrequency frequency
    ) {
        boolean showWeekly =
                frequency
                        == BackupSchedulePreferences
                        .BackupFrequency
                        .WEEKLY;

        boolean showMonthly =
                frequency
                        == BackupSchedulePreferences
                        .BackupFrequency
                        .MONTHLY;

        groupOfflineWeeklyDay.setVisibility(
                showWeekly
                        ? View.VISIBLE
                        : View.GONE
        );

        groupOfflineMonthlyDay.setVisibility(
                showMonthly
                        ? View.VISIBLE
                        : View.GONE
        );

        boolean automatic =
                frequency.isAutomatic();

        btnOfflineBackupTime.setEnabled(
                automatic
        );

        switchOfflineChargingOnly.setEnabled(
                automatic
        );
    }

    private void updateTimeButtonText() {
        Calendar calendar =
                Calendar.getInstance();

        calendar.set(
                Calendar.HOUR_OF_DAY,
                selectedHour
        );

        calendar.set(
                Calendar.MINUTE,
                selectedMinute
        );

        String formattedTime =
                new SimpleDateFormat(
                        "hh:mm a",
                        Locale.getDefault()
                ).format(
                        calendar.getTime()
                );

        btnOfflineBackupTime.setText(
                "Preferred time: "
                        + formattedTime
        );
    }

    private void updateStatusViews(
            @NonNull BackupSchedulePreferences.ScheduleSettings settings,
            @NonNull BackupSchedulePreferences.BackupStatus status
    ) {
        StringBuilder scheduleStatus =
                new StringBuilder();

        scheduleStatus.append(
                settings
                        .getFrequency()
                        .getDisplayName()
        );

        if (settings.isAutomaticEnabled()) {
            scheduleStatus.append(
                    " • "
            );

            scheduleStatus.append(
                    formatSelectedTime(
                            settings.getPreferredHour(),
                            settings.getPreferredMinute()
                    )
            );

            if (settings.getFrequency()
                    == BackupSchedulePreferences
                    .BackupFrequency
                    .WEEKLY) {

                scheduleStatus.append(
                        " • "
                );

                scheduleStatus.append(
                        getWeekDayLabel(
                                settings.getWeeklyDayOfWeek()
                        )
                );
            }

            if (settings.getFrequency()
                    == BackupSchedulePreferences
                    .BackupFrequency
                    .MONTHLY) {

                scheduleStatus.append(
                        " • Date "
                );

                scheduleStatus.append(
                        settings.getMonthlyDayOfMonth()
                );
            }

            if (settings.isChargingOnly()) {
                scheduleStatus.append(
                        " • Charging only"
                );
            }
        }

        if (status.hasSuccessfulBackup()) {
            scheduleStatus.append(
                    "\nLast backup: "
            );

            scheduleStatus.append(
                    formatDateTime(
                            status.getLastSuccessAtMillis()
                    )
            );

            scheduleStatus.append(
                    " • "
            );

            scheduleStatus.append(
                    status.getLastRecordCount()
            );

            scheduleStatus.append(
                    " records"
            );
        }

        if (status.hasFailureAfterLastSuccess()) {
            scheduleStatus.append(
                    "\nLast error: "
            );

            scheduleStatus.append(
                    status.getLastFailureMessage()
            );
        }

        txtOfflineScheduleStatus.setText(
                scheduleStatus.toString()
        );

        if (status.getNextScheduledAtMillis() > 0L
                && settings.isAutomaticEnabled()) {

            txtOfflineNextBackup.setText(
                    "Next: "
                            + formatCompactDateTime(
                            status.getNextScheduledAtMillis()
                    )
            );

        } else {
            txtOfflineNextBackup.setText(
                    "Not scheduled"
            );
        }
    }

    @NonNull
    private BackupSchedulePreferences.BackupFrequency
    frequencyFromDisplayName(
            @NonNull String value
    ) {
        String cleanValue =
                value.trim();

        for (BackupSchedulePreferences.BackupFrequency frequency :
                BackupSchedulePreferences
                        .BackupFrequency
                        .values()) {

            if (frequency
                    .getDisplayName()
                    .equalsIgnoreCase(
                            cleanValue
                    )) {

                return frequency;
            }
        }

        return BackupSchedulePreferences
                .BackupFrequency
                .MANUAL_ONLY;
    }

    private int getCalendarDay(
            @NonNull String displayName
    ) {
        String cleanName =
                displayName.trim();

        for (int index = 0;
             index < WEEK_DAY_LABELS.length;
             index++) {

            if (WEEK_DAY_LABELS[index]
                    .equalsIgnoreCase(
                            cleanName
                    )) {

                return Calendar.SUNDAY
                        + index;
            }
        }

        return BackupSchedulePreferences
                .DEFAULT_WEEKLY_DAY;
    }

    @NonNull
    private String getWeekDayLabel(
            int calendarDay
    ) {
        int index =
                calendarDay
                        - Calendar.SUNDAY;

        if (index < 0
                || index >= WEEK_DAY_LABELS.length) {

            return "Sunday";
        }

        return WEEK_DAY_LABELS[index];
    }

    private int parseMonthlyDay(
            @NonNull String value
    ) {
        try {
            int day =
                    Integer.parseInt(
                            value.trim()
                    );

            if (day >= 1
                    && day <= 28) {

                return day;
            }

        } catch (NumberFormatException ignored) {
            // Default value is returned below.
        }

        return BackupSchedulePreferences
                .DEFAULT_MONTHLY_DAY;
    }

    @NonNull
    private String formatSelectedTime(
            int hour,
            int minute
    ) {
        Calendar calendar =
                Calendar.getInstance();

        calendar.set(
                Calendar.HOUR_OF_DAY,
                hour
        );

        calendar.set(
                Calendar.MINUTE,
                minute
        );

        return new SimpleDateFormat(
                "hh:mm a",
                Locale.getDefault()
        ).format(
                calendar.getTime()
        );
    }

    @NonNull
    private String formatDateTime(
            long timestamp
    ) {
        if (timestamp <= 0L) {
            return "Not available";
        }

        return new SimpleDateFormat(
                "dd MMM yyyy, hh:mm a",
                Locale.getDefault()
        ).format(
                new Date(
                        timestamp
                )
        );
    }

    @NonNull
    private String formatCompactDateTime(
            long timestamp
    ) {
        if (timestamp <= 0L) {
            return "Not scheduled";
        }

        return new SimpleDateFormat(
                "dd MMM, hh:mm a",
                Locale.getDefault()
        ).format(
                new Date(
                        timestamp
                )
        );
    }

    @NonNull
    private String safeMessage(
            @NonNull Throwable throwable,
            @NonNull String fallback
    ) {
        Throwable current =
                throwable;

        String usefulMessage =
                "";

        int inspectedCauses =
                0;

        while (current != null
                && inspectedCauses < 12) {

            String message =
                    current.getMessage();

            if (message != null
                    && !message.trim().isEmpty()) {

                usefulMessage =
                        message.trim();
            }

            current =
                    current.getCause();

            inspectedCauses++;
        }

        if (usefulMessage.isEmpty()) {
            return fallback;
        }

        usefulMessage =
                usefulMessage
                        .replace(
                                '\n',
                                ' '
                        )
                        .replace(
                                '\r',
                                ' '
                        )
                        .replace(
                                '\0',
                                ' '
                        )
                        .trim()
                        .replaceAll(
                                "\\s+",
                                " "
                        );

        if (usefulMessage.length() > 500) {
            usefulMessage =
                    usefulMessage.substring(
                            0,
                            500
                    );
        }

        return usefulMessage;
    }

    @NonNull
    @SuppressWarnings("unchecked")
    private <T extends View> T requireView(
            int viewId
    ) {
        View view =
                activity.findViewById(
                        viewId
                );

        if (view == null) {
            throw new IllegalStateException(
                    "Required offline backup view is missing: "
                            + activity
                            .getResources()
                            .getResourceEntryName(
                                    viewId
                            )
            );
        }

        return (T) view;
    }
}