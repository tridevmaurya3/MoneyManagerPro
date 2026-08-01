package com.example.moneymanagerpro.cloud;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.InputType;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.example.moneymanagerpro.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Controls the Encrypted Cloud Backup section.
 *
 * Responsibilities:
 *
 * 1. Display the current Firebase account.
 * 2. Display email-verification status.
 * 3. Store the recovery passphrase securely through CloudBackupKeyVault.
 * 4. Load and save account-specific cloud-backup settings.
 * 5. Apply or cancel CloudBackupScheduler.
 * 6. Run an immediate encrypted cloud backup through WorkManager.
 * 7. Download, verify, decrypt, preview and restore the latest backup.
 * 8. Display manual backup and cloud-restore progress.
 * 9. Display last success, last failure and next scheduled backup.
 *
 * Security:
 *
 * - Recovery passphrase is never saved as plain text.
 * - Recovery passphrase is never uploaded to Firebase.
 * - Recovery passphrase is never placed in WorkManager Data.
 * - Manual work is bound to the Firebase UID active at enqueue time.
 * - Restore callbacks are bound to the Firebase UID active at start.
 * - Decrypted backup bytes are cleared after restore, failure or cancel.
 * - Temporary character arrays are cleared after use.
 */
public final class CloudBackupSettingsController {

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

    private static final int ACTION_REFRESH_ACCOUNT =
            1;

    private static final int ACTION_SEND_VERIFICATION =
            2;

    private static final int ACTION_SAVE_PASSPHRASE =
            3;

    private static final int ACTION_REMOVE_PASSPHRASE =
            4;

    private static final int AFTER_PASSPHRASE_NONE =
            0;

    private static final int AFTER_PASSPHRASE_APPLY_SCHEDULE =
            1;

    private static final int AFTER_PASSPHRASE_START_MANUAL_BACKUP =
            2;

    private static final String UNIQUE_MANUAL_WORK_PREFIX =
            "money_manager_manual_cloud_backup_";

    private static final String TAG_MANUAL_CLOUD_BACKUP =
            "money_manager_manual_cloud_backup";

    private static final long MANUAL_BACKOFF_SECONDS =
            30L;

    private final Activity activity;

    private final LifecycleOwner lifecycleOwner;

    private final Context applicationContext;

    private final FirebaseAuth firebaseAuth;

    private final WorkManager workManager;

    private final BackupSchedulePreferences schedulePreferences;

    private final CloudBackupKeyVault keyVault;

    private final MaterialAutoCompleteTextView
            dropdownCloudFrequency;

    private final MaterialAutoCompleteTextView
            dropdownCloudWeeklyDay;

    private final MaterialAutoCompleteTextView
            dropdownCloudMonthlyDay;

    private final MaterialButton btnCloudBackupTime;

    private final MaterialButton btnSaveCloudSchedule;

    private final MaterialButton btnCloudAccountAction;

    private final MaterialButton btnCloudBackupNow;

    private final MaterialButton btnCloudRestore;

    private final MaterialButton btnDeleteCloudBackup;

    private final MaterialButton btnDeleteCloudAccount;

    private final MaterialSwitch switchCloudWifiOnly;

    private final MaterialSwitch switchCloudChargingOnly;

    private final LinearLayout groupCloudWeeklyDay;

    private final LinearLayout groupCloudMonthlyDay;

    private final TextView txtCloudAccountStatus;

    private final TextView txtCloudBackupAvailability;

    private final TextView txtCloudBackupStatus;

    private int selectedHour =
            BackupSchedulePreferences.DEFAULT_PREFERRED_HOUR;

    private int selectedMinute =
            BackupSchedulePreferences.DEFAULT_PREFERRED_MINUTE;

    private boolean listenersAttached =
            false;

    private boolean manualBackupRunning =
            false;

    private boolean cloudRestoreRunning =
            false;

    private long restoreOperationToken =
            0L;

    @NonNull
    private String activeRestoreFirebaseUserId =
            "";

    @Nullable
    private EncryptedCloudBackupDownloader.DecryptedCloudBackup
            pendingRestoreBackup;

    @Nullable
    private char[] pendingRestorePassphraseToSave;

    @Nullable
    private UUID activeManualWorkId;

    @Nullable
    private UUID lastHandledFinishedWorkId;

    @NonNull
    private String observedManualWorkName =
            "";

    @NonNull
    private String observedManualFirebaseUserId =
            "";

    @Nullable
    private LiveData<List<WorkInfo>>
            observedManualWorkLiveData;

    @Nullable
    private Observer<List<WorkInfo>>
            manualWorkObserver;

    public CloudBackupSettingsController(
            @NonNull Activity activity
    ) {
        this.activity =
                activity;

        if (!(activity
                instanceof LifecycleOwner)) {

            throw new IllegalStateException(
                    "CloudBackupSettingsController requires "
                            + "a LifecycleOwner Activity."
            );
        }

        lifecycleOwner =
                (LifecycleOwner) activity;

        applicationContext =
                activity.getApplicationContext();

        firebaseAuth =
                FirebaseAuth.getInstance();

        workManager =
                WorkManager.getInstance(
                        applicationContext
                );

        schedulePreferences =
                new BackupSchedulePreferences(
                        applicationContext
                );

        keyVault =
                new CloudBackupKeyVault(
                        applicationContext
                );

        dropdownCloudFrequency =
                requireView(
                        R.id.dropdownCloudFrequency
                );

        dropdownCloudWeeklyDay =
                requireView(
                        R.id.dropdownCloudWeeklyDay
                );

        dropdownCloudMonthlyDay =
                requireView(
                        R.id.dropdownCloudMonthlyDay
                );

        btnCloudBackupTime =
                requireView(
                        R.id.btnCloudBackupTime
                );

        btnSaveCloudSchedule =
                requireView(
                        R.id.btnSaveCloudSchedule
                );

        btnCloudAccountAction =
                requireView(
                        R.id.btnCloudAccountAction
                );

        btnCloudBackupNow =
                requireView(
                        R.id.btnCloudBackupNow
                );

        btnCloudRestore =
                requireView(
                        R.id.btnCloudRestore
                );

        btnDeleteCloudBackup =
                requireView(
                        R.id.btnDeleteCloudBackup
                );

        btnDeleteCloudAccount =
                requireView(
                        R.id.btnDeleteCloudAccount
                );

        switchCloudWifiOnly =
                requireView(
                        R.id.switchCloudWifiOnly
                );

        switchCloudChargingOnly =
                requireView(
                        R.id.switchCloudChargingOnly
                );

        groupCloudWeeklyDay =
                requireView(
                        R.id.groupCloudWeeklyDay
                );

        groupCloudMonthlyDay =
                requireView(
                        R.id.groupCloudMonthlyDay
                );

        txtCloudAccountStatus =
                requireView(
                        R.id.txtCloudAccountStatus
                );

        txtCloudBackupAvailability =
                requireView(
                        R.id.txtCloudBackupAvailability
                );

        txtCloudBackupStatus =
                requireView(
                        R.id.txtCloudBackupStatus
                );
    }

    /**
     * Initializes dropdowns, listeners and saved account settings.
     *
     * Call once from BackupActivity.onCreate().
     */
    public void initialize() {
        setupFrequencyDropdown();

        setupWeeklyDayDropdown();

        setupMonthlyDayDropdown();

        attachListeners();

        configureCloudOperationButtons();

        refresh();
    }

    /**
     * Reloads account, recovery-key, manual-work and schedule status.
     */
    public void refresh() {
        FirebaseUser firebaseUser =
                firebaseAuth.getCurrentUser();

        if (firebaseUser == null) {
            showSignedOutState();

            return;
        }

        String firebaseUserId =
                firebaseUser.getUid();

        if (cloudRestoreRunning
                && !firebaseUserId.equals(
                activeRestoreFirebaseUserId
        )) {

            invalidateRestoreOperation();
        }

        observeManualBackupForAccount(
                firebaseUserId
        );

        BackupSchedulePreferences.ScheduleSettings settings;

        try {
            settings =
                    schedulePreferences.getCloudSchedule(
                            firebaseUserId
                    );

        } catch (Exception exception) {
            showAccountErrorState(
                    firebaseUser,
                    safeMessage(
                            exception,
                            "Cloud schedule could not be read."
                    )
            );

            return;
        }

        selectedHour =
                settings.getPreferredHour();

        selectedMinute =
                settings.getPreferredMinute();

        dropdownCloudFrequency.setText(
                settings
                        .getFrequency()
                        .getDisplayName(),
                false
        );

        dropdownCloudWeeklyDay.setText(
                getWeekDayLabel(
                        settings.getWeeklyDayOfWeek()
                ),
                false
        );

        dropdownCloudMonthlyDay.setText(
                String.valueOf(
                        settings.getMonthlyDayOfMonth()
                ),
                false
        );

        switchCloudWifiOnly.setChecked(
                settings.isWifiOnly()
        );

        switchCloudChargingOnly.setChecked(
                settings.isChargingOnly()
        );

        updateTimeButtonText();

        updateConditionalGroups(
                settings.getFrequency()
        );

        updateFirebaseAccountViews(
                firebaseUser
        );

        updateRecoveryKeyStatus(
                firebaseUserId
        );

        if (!manualBackupRunning
                && !cloudRestoreRunning) {

            updateCloudScheduleStatus(
                    firebaseUserId,
                    settings
            );
        }

        setScheduleControlsEnabled(
                true
        );

        updateCloudOperationButtonAvailability(
                firebaseUser
        );
    }

    private void setupFrequencyDropdown() {
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        activity,
                        android.R.layout.simple_list_item_1,
                        FREQUENCY_LABELS
                );

        dropdownCloudFrequency.setAdapter(
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

        dropdownCloudWeeklyDay.setAdapter(
                adapter
        );
    }

    private void setupMonthlyDayDropdown() {
        List<String> monthlyDays =
                new ArrayList<>();

        for (int day = 1;
             day <= 28;
             day++) {

            monthlyDays.add(
                    String.valueOf(
                            day
                    )
            );
        }

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        activity,
                        android.R.layout.simple_list_item_1,
                        monthlyDays
                );

        dropdownCloudMonthlyDay.setAdapter(
                adapter
        );
    }

    private void attachListeners() {
        if (listenersAttached) {
            return;
        }

        listenersAttached =
                true;

        dropdownCloudFrequency.setOnItemClickListener(
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

        btnCloudBackupTime.setOnClickListener(
                view -> showTimePicker()
        );

        btnSaveCloudSchedule.setOnClickListener(
                view -> saveCloudSchedule()
        );

        btnCloudAccountAction.setOnClickListener(
                view -> showCloudAccountActions()
        );

        btnCloudBackupNow.setOnClickListener(
                view -> startManualCloudBackup()
        );

        btnCloudRestore.setOnClickListener(
                view -> startCloudRestore()
        );
    }

    /**
     * Backup Now and Cloud Restore are active.
     *
     * Permanent cloud-backup deletion and permanent account deletion
     * remain disabled until their independent confirmation and
     * reauthentication flows are added.
     */
    private void configureCloudOperationButtons() {
        btnCloudBackupNow.setEnabled(
                false
        );

        btnCloudBackupNow.setAlpha(
                0.55F
        );

        btnCloudBackupNow.setText(
                "Backup Now"
        );

        btnCloudRestore.setEnabled(
                false
        );

        btnCloudRestore.setAlpha(
                0.55F
        );

        btnCloudRestore.setText(
                "Cloud Restore"
        );

        disablePendingButton(
                btnDeleteCloudBackup
        );

        disablePendingButton(
                btnDeleteCloudAccount
        );
    }

    private void disablePendingButton(
            @NonNull MaterialButton button
    ) {
        button.setEnabled(
                false
        );

        button.setAlpha(
                0.55F
        );
    }

    /**
     * Validates account requirements before enqueueing manual backup.
     */
    private void startManualCloudBackup() {
        if (manualBackupRunning) {
            Toast.makeText(
                    activity,
                    "Encrypted cloud backup पहले से चल रहा है।",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (cloudRestoreRunning) {
            Toast.makeText(
                    activity,
                    "Cloud restore पूरा होने के बाद नया backup बनाएँ।",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        FirebaseUser firebaseUser =
                firebaseAuth.getCurrentUser();

        if (firebaseUser == null) {
            new AlertDialog.Builder(
                    activity
            )
                    .setTitle(
                            "Cloud Account Required"
                    )
                    .setMessage(
                            "Backup Now उपयोग करने के लिए पहले "
                                    + "Firebase account में sign in करें।"
                    )
                    .setPositiveButton(
                            "OK",
                            null
                    )
                    .show();

            return;
        }

        if (!firebaseUser.isEmailVerified()) {
            showEmailVerificationRequiredDialog(
                    firebaseUser
            );

            return;
        }

        String firebaseUserId =
                firebaseUser.getUid();

        if (!keyVault.hasSavedPassphrase(
                firebaseUserId
        )) {
            showRecoveryPassphraseDialog(
                    firebaseUser,
                    AFTER_PASSPHRASE_START_MANUAL_BACKUP
            );

            return;
        }

        enqueueManualCloudBackup(
                firebaseUser
        );
    }

    private void showEmailVerificationRequiredDialog(
            @NonNull FirebaseUser firebaseUser
    ) {
        new AlertDialog.Builder(
                activity
        )
                .setTitle(
                        "Email Verification Required"
                )
                .setMessage(
                        "Encrypted cloud backup बनाने से पहले "
                                + "Firebase email verify करना आवश्यक है।\n\n"
                                + "Verification email भेजने के बाद अपने "
                                + "email inbox में link खोलें। फिर इस screen "
                                + "पर Manage Cloud Account → Refresh Firebase "
                                + "account चुनें।"
                )
                .setPositiveButton(
                        "Send Verification Email",
                        (dialog, which) ->
                                sendVerificationEmail(
                                        firebaseUser
                                )
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .show();
    }

    /**
     * Enqueues a one-time manual encrypted backup.
     *
     * Frequency, preferred time and automatic due checks are bypassed.
     * Internet remains required.
     */
    private void enqueueManualCloudBackup(
            @NonNull FirebaseUser firebaseUser
    ) {
        String firebaseUserId =
                firebaseUser.getUid();

        try {
            observeManualBackupForAccount(
                    firebaseUserId
            );

            Constraints constraints =
                    new Constraints.Builder()
                            .setRequiredNetworkType(
                                    NetworkType.CONNECTED
                            )
                            .setRequiresStorageNotLow(
                                    true
                            )
                            .build();

            OneTimeWorkRequest workRequest =
                    new OneTimeWorkRequest.Builder(
                            CloudAutomaticBackupWorker.class
                    )
                            .setInputData(
                                    CloudAutomaticBackupWorker
                                            .createManualRunInput(
                                                    firebaseUserId
                                            )
                            )
                            .setConstraints(
                                    constraints
                            )
                            .setBackoffCriteria(
                                    BackoffPolicy.EXPONENTIAL,
                                    MANUAL_BACKOFF_SECONDS,
                                    TimeUnit.SECONDS
                            )
                            .addTag(
                                    TAG_MANUAL_CLOUD_BACKUP
                            )
                            .build();

            activeManualWorkId =
                    workRequest.getId();

            lastHandledFinishedWorkId =
                    null;

            manualBackupRunning =
                    true;

            showManualBackupQueuedState();

            workManager.enqueueUniqueWork(
                    getManualWorkName(
                            firebaseUserId
                    ),
                    ExistingWorkPolicy.REPLACE,
                    workRequest
            );

            Toast.makeText(
                    activity,
                    "Encrypted cloud backup शुरू हो गया है।",
                    Toast.LENGTH_SHORT
            ).show();

        } catch (Exception exception) {
            manualBackupRunning =
                    false;

            activeManualWorkId =
                    null;

            updateCloudOperationButtonAvailability(
                    firebaseUser
            );

            String message =
                    safeMessage(
                            exception,
                            "Manual cloud backup शुरू नहीं हो सका।"
                    );

            txtCloudBackupStatus.setText(
                    message
            );

            Toast.makeText(
                    activity,
                    message,
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    /**
     * Observes the unique manual-backup work for the current account.
     *
     * This also reconnects the UI to an unfinished manual backup after
     * screen recreation.
     */
    private void observeManualBackupForAccount(
            @NonNull String firebaseUserId
    ) {
        String uniqueWorkName =
                getManualWorkName(
                        firebaseUserId
                );

        if (uniqueWorkName.equals(
                observedManualWorkName
        )) {
            return;
        }

        detachManualWorkObserver();

        observedManualWorkName =
                uniqueWorkName;

        observedManualFirebaseUserId =
                firebaseUserId;

        observedManualWorkLiveData =
                workManager
                        .getWorkInfosForUniqueWorkLiveData(
                                uniqueWorkName
                        );

        manualWorkObserver =
                workInfos ->
                        handleManualWorkInfos(
                                firebaseUserId,
                                workInfos
                        );

        observedManualWorkLiveData.observe(
                lifecycleOwner,
                manualWorkObserver
        );
    }

    private void detachManualWorkObserver() {
        if (observedManualWorkLiveData != null
                && manualWorkObserver != null) {

            observedManualWorkLiveData
                    .removeObserver(
                            manualWorkObserver
                    );
        }

        observedManualWorkLiveData =
                null;

        manualWorkObserver =
                null;

        observedManualWorkName =
                "";

        observedManualFirebaseUserId =
                "";
    }

    private void handleManualWorkInfos(
            @NonNull String observedFirebaseUserId,
            @Nullable List<WorkInfo> workInfos
    ) {
        FirebaseUser currentUser =
                firebaseAuth.getCurrentUser();

        if (currentUser == null
                || !currentUser
                .getUid()
                .equals(
                        observedFirebaseUserId
                )) {

            return;
        }

        WorkInfo relevantWorkInfo =
                findRelevantManualWork(
                        workInfos
                );

        if (relevantWorkInfo == null) {
            if (manualBackupRunning) {
                manualBackupRunning =
                        false;

                activeManualWorkId =
                        null;

                updateCloudOperationButtonAvailability(
                        currentUser
                );
            }

            return;
        }

        activeManualWorkId =
                relevantWorkInfo.getId();

        WorkInfo.State state =
                relevantWorkInfo.getState();

        switch (state) {
            case ENQUEUED:
                manualBackupRunning =
                        true;

                showManualBackupWaitingState(
                        relevantWorkInfo
                );
                break;

            case BLOCKED:
                manualBackupRunning =
                        true;

                showManualBackupBlockedState();
                break;

            case RUNNING:
                manualBackupRunning =
                        true;

                showManualBackupRunningState(
                        relevantWorkInfo
                );
                break;

            case SUCCEEDED:
            case FAILED:
            case CANCELLED:
                handleFinishedManualWork(
                        currentUser,
                        relevantWorkInfo
                );
                break;

            default:
                break;
        }
    }

    @Nullable
    private WorkInfo findRelevantManualWork(
            @Nullable List<WorkInfo> workInfos
    ) {
        if (workInfos == null
                || workInfos.isEmpty()) {

            return null;
        }

        if (activeManualWorkId != null) {
            for (WorkInfo workInfo :
                    workInfos) {

                if (activeManualWorkId.equals(
                        workInfo.getId()
                )) {
                    return workInfo;
                }
            }
        }

        /*
         * After Activity recreation activeManualWorkId is not available.
         * Reconnect to any unfinished work in the account's unique chain.
         */
        for (WorkInfo workInfo :
                workInfos) {

            if (!workInfo
                    .getState()
                    .isFinished()) {

                return workInfo;
            }
        }

        return null;
    }

    private void disableRestoreDuringManualBackup() {
        btnCloudRestore.setEnabled(
                false
        );

        btnCloudRestore.setAlpha(
                0.55F
        );

        btnCloudRestore.setText(
                "Cloud Restore"
        );
    }

    private void showManualBackupQueuedState() {
        disableRestoreDuringManualBackup();

        btnCloudBackupNow.setEnabled(
                false
        );

        btnCloudBackupNow.setAlpha(
                0.65F
        );

        btnCloudBackupNow.setText(
                "Preparing..."
        );

        txtCloudBackupAvailability.setText(
                "Queued"
        );

        txtCloudBackupStatus.setText(
                "Manual encrypted cloud backup तैयार किया जा रहा है।\n"
                        + "Internet connection उपलब्ध होते ही encryption "
                        + "और upload शुरू होगा।"
        );
    }

    private void showManualBackupWaitingState(
            @NonNull WorkInfo workInfo
    ) {
        disableRestoreDuringManualBackup();

        btnCloudBackupNow.setEnabled(
                false
        );

        btnCloudBackupNow.setAlpha(
                0.65F
        );

        if (workInfo.getRunAttemptCount() > 0) {
            btnCloudBackupNow.setText(
                    "Retry waiting..."
            );

            txtCloudBackupAvailability.setText(
                    "Retry queued"
            );

            txtCloudBackupStatus.setText(
                    "पिछला cloud upload network या temporary server "
                            + "समस्या के कारण पूरा नहीं हुआ।\n"
                            + "WorkManager सुरक्षित रूप से retry करेगा।"
            );

        } else {
            btnCloudBackupNow.setText(
                    "Waiting..."
            );

            txtCloudBackupAvailability.setText(
                    "Waiting"
            );

            txtCloudBackupStatus.setText(
                    "Manual encrypted cloud backup internet connection "
                            + "का इंतजार कर रहा है।"
            );
        }
    }

    private void showManualBackupBlockedState() {
        disableRestoreDuringManualBackup();

        btnCloudBackupNow.setEnabled(
                false
        );

        btnCloudBackupNow.setAlpha(
                0.65F
        );

        btnCloudBackupNow.setText(
                "Waiting..."
        );

        txtCloudBackupAvailability.setText(
                "Blocked"
        );

        txtCloudBackupStatus.setText(
                "Cloud backup किसी आवश्यक WorkManager condition "
                        + "के पूरा होने का इंतजार कर रहा है।"
        );
    }

    private void showManualBackupRunningState(
            @NonNull WorkInfo workInfo
    ) {
        disableRestoreDuringManualBackup();

        btnCloudBackupNow.setEnabled(
                false
        );

        btnCloudBackupNow.setAlpha(
                0.65F
        );

        btnCloudBackupNow.setText(
                workInfo.getRunAttemptCount() > 0
                        ? "Retrying upload..."
                        : "Encrypting & Uploading..."
        );

        txtCloudBackupAvailability.setText(
                "Uploading"
        );

        txtCloudBackupStatus.setText(
                "आपका पूरा financial data device पर encrypt किया जा रहा है।\n"
                        + "केवल encrypted chunks Firebase Cloud Firestore "
                        + "पर upload होंगे। App को बंद न करें।"
        );
    }

    private void handleFinishedManualWork(
            @NonNull FirebaseUser firebaseUser,
            @NonNull WorkInfo workInfo
    ) {
        if (lastHandledFinishedWorkId != null
                && lastHandledFinishedWorkId.equals(
                workInfo.getId()
        )) {

            return;
        }

        lastHandledFinishedWorkId =
                workInfo.getId();

        manualBackupRunning =
                false;

        activeManualWorkId =
                null;

        WorkInfo.State state =
                workInfo.getState();

        String outputMessage =
                workInfo
                        .getOutputData()
                        .getString(
                                CloudAutomaticBackupWorker
                                        .OUTPUT_MESSAGE
                        );

        if (state == WorkInfo.State.SUCCEEDED) {
            String outputStatus =
                    workInfo
                            .getOutputData()
                            .getString(
                                    CloudAutomaticBackupWorker
                                            .OUTPUT_STATUS
                            );

            if (CloudAutomaticBackupWorker
                    .STATUS_SUCCESS
                    .equals(
                            outputStatus
                    )) {

                showManualBackupSuccess(
                        workInfo
                );

            } else {
                String message =
                        outputMessage == null
                                || outputMessage.trim().isEmpty()
                                ? "Cloud backup task पूरा हुआ, लेकिन "
                                  + "नया backup upload नहीं हुआ।"
                                : outputMessage;

                txtCloudBackupAvailability.setText(
                        "Skipped"
                );

                txtCloudBackupStatus.setText(
                        message
                );

                Toast.makeText(
                        activity,
                        message,
                        Toast.LENGTH_LONG
                ).show();
            }

        } else if (state
                == WorkInfo.State.FAILED) {

            String message =
                    outputMessage == null
                            || outputMessage.trim().isEmpty()
                            ? "Encrypted cloud backup पूरा नहीं हो सका।"
                            : outputMessage;

            txtCloudBackupAvailability.setText(
                    "Failed"
            );

            txtCloudBackupStatus.setText(
                    "Cloud backup failed\n"
                            + message
            );

            new AlertDialog.Builder(
                    activity
            )
                    .setTitle(
                            "Cloud Backup Failed"
                    )
                    .setMessage(
                            message
                                    + "\n\nFirebase account, internet, "
                                    + "Firestore rules और recovery passphrase "
                                    + "की जाँच करके Backup Now दोबारा दबाएँ।"
                    )
                    .setPositiveButton(
                            "OK",
                            null
                    )
                    .show();

        } else {
            txtCloudBackupAvailability.setText(
                    "Cancelled"
            );

            txtCloudBackupStatus.setText(
                    "Manual encrypted cloud backup cancel हो गया।"
            );

            Toast.makeText(
                    activity,
                    "Cloud backup cancel हो गया।",
                    Toast.LENGTH_LONG
            ).show();
        }

        updateCloudOperationButtonAvailability(
                firebaseUser
        );

        /*
         * Worker has already saved success/failure information.
         * Reload the account-specific status from preferences.
         */
        BackupSchedulePreferences.ScheduleSettings settings;

        try {
            settings =
                    schedulePreferences.getCloudSchedule(
                            firebaseUser.getUid()
                    );

            updateCloudScheduleStatus(
                    firebaseUser.getUid(),
                    settings
            );

        } catch (Exception ignored) {
            // The work result remains visible if status refresh fails.
        }
    }

    private void showManualBackupSuccess(
            @NonNull WorkInfo workInfo
    ) {
        String backupId =
                workInfo
                        .getOutputData()
                        .getString(
                                CloudAutomaticBackupWorker
                                        .OUTPUT_BACKUP_ID
                        );

        int recordCount =
                workInfo
                        .getOutputData()
                        .getInt(
                                CloudAutomaticBackupWorker
                                        .OUTPUT_RECORD_COUNT,
                                0
                        );

        long encryptedBytes =
                workInfo
                        .getOutputData()
                        .getLong(
                                CloudAutomaticBackupWorker
                                        .OUTPUT_ENCRYPTED_BYTES,
                                0L
                        );

        long completedAt =
                workInfo
                        .getOutputData()
                        .getLong(
                                CloudAutomaticBackupWorker
                                        .OUTPUT_COMPLETED_AT,
                                System.currentTimeMillis()
                        );

        StringBuilder result =
                new StringBuilder();

        result.append(
                "Manual encrypted cloud backup सफल रहा।"
        );

        result.append(
                "\n\nCompleted: "
        );

        result.append(
                formatDateTime(
                        completedAt
                )
        );

        result.append(
                "\nRecords: "
        );

        result.append(
                recordCount
        );

        result.append(
                "\nEncrypted size: "
        );

        result.append(
                formatFileSize(
                        encryptedBytes
                )
        );

        if (backupId != null
                && !backupId.trim().isEmpty()) {

            result.append(
                    "\nBackup ID: "
            );

            result.append(
                    backupId.trim()
            );
        }

        txtCloudBackupAvailability.setText(
                "Backup available"
        );

        txtCloudBackupStatus.setText(
                result.toString()
        );

        new AlertDialog.Builder(
                activity
        )
                .setTitle(
                        "Cloud Backup Complete"
                )
                .setMessage(
                        result.toString()
                                + "\n\nPlain financial data upload नहीं हुआ। "
                                + "Firestore पर केवल encrypted backup सुरक्षित है।"
                )
                .setPositiveButton(
                        "OK",
                        null
                )
                .show();
    }

    private void updateCloudOperationButtonAvailability(
            @Nullable FirebaseUser firebaseUser
    ) {
        boolean accountAvailable =
                firebaseUser != null;

        if (manualBackupRunning) {
            btnCloudRestore.setEnabled(
                    false
            );

            btnCloudRestore.setAlpha(
                    0.55F
            );

            btnCloudRestore.setText(
                    "Cloud Restore"
            );

            return;
        }

        if (cloudRestoreRunning) {
            btnCloudBackupNow.setEnabled(
                    false
            );

            btnCloudBackupNow.setAlpha(
                    0.55F
            );

            btnCloudBackupNow.setText(
                    "Backup Now"
            );

            return;
        }

        btnCloudBackupNow.setText(
                "Backup Now"
        );

        btnCloudBackupNow.setEnabled(
                accountAvailable
        );

        btnCloudBackupNow.setAlpha(
                accountAvailable
                        ? 1F
                        : 0.55F
        );

        boolean restoreEnabled =
                firebaseUser != null
                        && firebaseUser.isEmailVerified();

        btnCloudRestore.setText(
                "Cloud Restore"
        );

        btnCloudRestore.setEnabled(
                restoreEnabled
        );

        btnCloudRestore.setAlpha(
                restoreEnabled
                        ? 1F
                        : 0.55F
        );
    }

    @NonNull
    private String getManualWorkName(
            @NonNull String firebaseUserId
    ) {
        UUID stableAccountIdentifier =
                UUID.nameUUIDFromBytes(
                        firebaseUserId
                                .getBytes(
                                        StandardCharsets.UTF_8
                                )
                );

        return UNIQUE_MANUAL_WORK_PREFIX
                + stableAccountIdentifier;
    }


    /**
     * Starts the complete encrypted Cloud Restore flow.
     */
    private void startCloudRestore() {
        if (manualBackupRunning) {
            Toast.makeText(
                    activity,
                    "Cloud backup पूरा होने के बाद restore शुरू करें।",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        if (cloudRestoreRunning) {
            Toast.makeText(
                    activity,
                    "Cloud restore पहले से चल रहा है।",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        FirebaseUser firebaseUser =
                firebaseAuth.getCurrentUser();

        if (firebaseUser == null) {
            new AlertDialog.Builder(
                    activity
            )
                    .setTitle(
                            "Cloud Account Required"
                    )
                    .setMessage(
                            "Cloud Restore उपयोग करने के लिए उसी Firebase "
                                    + "account में sign in करें जिस account से "
                                    + "backup बनाया गया था।"
                    )
                    .setPositiveButton(
                            "OK",
                            null
                    )
                    .show();

            return;
        }

        if (!firebaseUser.isEmailVerified()) {
            showEmailVerificationRequiredDialog(
                    firebaseUser
            );

            return;
        }

        String firebaseUserId =
                firebaseUser.getUid();

        if (keyVault.hasSavedPassphrase(
                firebaseUserId
        )) {
            char[] savedPassphrase =
                    null;

            try {
                savedPassphrase =
                        keyVault.readPassphrase(
                                firebaseUserId
                        );

                startCloudRestoreDownload(
                        firebaseUser,
                        savedPassphrase,
                        false
                );

            } catch (Exception exception) {
                CloudBackupEncryption
                        .clearSensitiveCharacters(
                                savedPassphrase
                        );

                showRestorePassphraseDialog(
                        firebaseUser,
                        false
                );
            }

            return;
        }

        showRestorePassphraseDialog(
                firebaseUser,
                false
        );
    }

    /**
     * Requests the recovery passphrase when it is not available locally
     * or when the locally saved passphrase cannot unlock the backup.
     */
    private void showRestorePassphraseDialog(
            @NonNull FirebaseUser firebaseUser,
            boolean previousPassphraseFailed
    ) {
        if (!isActivityUsable()) {
            return;
        }

        LinearLayout container =
                new LinearLayout(
                        activity
                );

        container.setOrientation(
                LinearLayout.VERTICAL
        );

        container.setPadding(
                dp(
                        22
                ),
                dp(
                        8
                ),
                dp(
                        22
                ),
                0
        );

        EditText passphraseInput =
                createPasswordInput(
                        "Recovery passphrase"
                );

        container.addView(
                passphraseInput
        );

        String message =
                previousPassphraseFailed
                        ? "Saved recovery passphrase इस cloud backup को "
                          + "unlock नहीं कर सकी। Backup बनाते समय उपयोग की गई "
                          + "सही passphrase दर्ज करें।\n\n"
                          + "सफल verification के बाद यह passphrase Android "
                          + "Keystore में सुरक्षित रूप से replace हो जाएगी।"
                        : "Cloud backup बनाते समय उपयोग की गई recovery "
                          + "passphrase दर्ज करें।\n\n"
                          + "सफल verification के बाद passphrase इस device के "
                          + "Android Keystore में सुरक्षित हो जाएगी।";

        AlertDialog dialog =
                new AlertDialog.Builder(
                        activity
                )
                        .setTitle(
                                previousPassphraseFailed
                                        ? "Enter Correct Passphrase"
                                        : "Unlock Cloud Backup"
                        )
                        .setMessage(
                                message
                        )
                        .setView(
                                container
                        )
                        .setPositiveButton(
                                "Download & Verify",
                                null
                        )
                        .setNegativeButton(
                                "Cancel",
                                null
                        )
                        .create();

        dialog.setOnShowListener(
                ignored -> dialog
                        .getButton(
                                DialogInterface.BUTTON_POSITIVE
                        )
                        .setOnClickListener(
                                view -> submitRestorePassphrase(
                                        dialog,
                                        firebaseUser,
                                        passphraseInput
                                )
                        )
        );

        dialog.show();
    }

    private void submitRestorePassphrase(
            @NonNull AlertDialog dialog,
            @NonNull FirebaseUser firebaseUser,
            @NonNull EditText passphraseInput
    ) {
        char[] passphrase =
                editableToCharacters(
                        passphraseInput.getText()
                );

        if (passphrase.length
                < CloudBackupEncryption
                .MINIMUM_PASSPHRASE_LENGTH) {

            CloudBackupEncryption
                    .clearSensitiveCharacters(
                            passphrase
                    );

            passphraseInput.setError(
                    "कम से कम "
                            + CloudBackupEncryption
                            .MINIMUM_PASSPHRASE_LENGTH
                            + " characters दर्ज करें।"
            );

            return;
        }

        if (passphrase.length
                > CloudBackupEncryption
                .MAXIMUM_PASSPHRASE_LENGTH) {

            CloudBackupEncryption
                    .clearSensitiveCharacters(
                            passphrase
                    );

            passphraseInput.setError(
                    "Passphrase अधिकतम "
                            + CloudBackupEncryption
                            .MAXIMUM_PASSPHRASE_LENGTH
                            + " characters हो सकती है।"
            );

            return;
        }

        passphraseInput.setText(
                ""
        );

        dialog.dismiss();

        startCloudRestoreDownload(
                firebaseUser,
                passphrase,
                true
        );
    }

    /**
     * Downloads the latest Firestore backup and performs all downloader
     * integrity, account-binding, decryption and checksum checks.
     */
    private void startCloudRestoreDownload(
            @NonNull FirebaseUser firebaseUser,
            @NonNull char[] passphrase,
            boolean savePassphraseAfterVerification
    ) {
        String firebaseUserId =
                firebaseUser
                        .getUid()
                        .trim();

        if (firebaseUserId.isEmpty()) {
            CloudBackupEncryption
                    .clearSensitiveCharacters(
                            passphrase
                    );

            showRestoreFailure(
                    "Firebase cloud account UID is unavailable."
            );

            return;
        }

        clearPendingRestoreSensitiveData();

        cloudRestoreRunning =
                true;

        activeRestoreFirebaseUserId =
                firebaseUserId;

        long operationToken =
                ++restoreOperationToken;

        if (savePassphraseAfterVerification) {
            pendingRestorePassphraseToSave =
                    Arrays.copyOf(
                            passphrase,
                            passphrase.length
                    );
        }

        showRestoreProgress(
                "Downloading",
                "Downloading & Decrypting...",
                "Firebase Cloud Firestore से encrypted backup download "
                        + "किया जा रहा है। इसके बाद checksum, account ownership "
                        + "और recovery passphrase verify होगी।"
        );

        EncryptedCloudBackupDownloader downloader =
                new EncryptedCloudBackupDownloader();

        try {
            downloader.downloadAndDecryptLatestBackup(
                    firebaseUser,
                    passphrase,
                    new EncryptedCloudBackupDownloader.DownloadCallback() {
                        @Override
                        public void onSuccess(
                                @NonNull EncryptedCloudBackupDownloader
                                        .DecryptedCloudBackup backup
                        ) {
                            handleDownloadedRestoreBackup(
                                    operationToken,
                                    firebaseUser,
                                    backup
                            );
                        }

                        @Override
                        public void onError(
                                @NonNull Exception exception
                        ) {
                            handleRestoreDownloadError(
                                    operationToken,
                                    firebaseUser,
                                    exception
                            );
                        }
                    }
            );

        } catch (Exception exception) {
            handleRestoreDownloadError(
                    operationToken,
                    firebaseUser,
                    exception
            );

        } finally {
            CloudBackupEncryption
                    .clearSensitiveCharacters(
                            passphrase
                    );
        }
    }

    private void handleDownloadedRestoreBackup(
            long operationToken,
            @NonNull FirebaseUser firebaseUser,
            @NonNull EncryptedCloudBackupDownloader
                    .DecryptedCloudBackup backup
    ) {
        String firebaseUserId =
                firebaseUser.getUid();

        if (!isCurrentRestoreOperation(
                operationToken,
                firebaseUserId
        )) {
            backup.clearSensitiveData();

            return;
        }

        saveVerifiedRestorePassphrase(
                firebaseUserId
        );

        pendingRestoreBackup =
                backup;

        showRestoreProgress(
                "Validating",
                "Checking Backup...",
                "Decrypted backup की सभी tables, record counts, duplicate IDs, "
                        + "parent-child references और database version verify "
                        + "की जा रही है। अभी local data में कोई बदलाव नहीं होगा।"
        );

        CloudBackupRestoreCoordinator coordinator =
                new CloudBackupRestoreCoordinator(
                        applicationContext
                );

        coordinator.inspectBackup(
                firebaseUser,
                backup,
                new CloudBackupRestoreCoordinator.PreviewCallback() {
                    @Override
                    public void onLoaded(
                            @NonNull CloudBackupRestoreCoordinator
                                    .RestorePreview preview
                    ) {
                        if (!isCurrentRestoreOperation(
                                operationToken,
                                firebaseUserId
                        )) {
                            backup.clearSensitiveData();

                            return;
                        }

                        showRestorePreviewDialog(
                                operationToken,
                                firebaseUser,
                                backup,
                                preview
                        );
                    }

                    @Override
                    public void onError(
                            @NonNull Exception exception
                    ) {
                        handleRestoreValidationError(
                                operationToken,
                                firebaseUserId,
                                exception
                        );
                    }
                }
        );
    }

    private void saveVerifiedRestorePassphrase(
            @NonNull String firebaseUserId
    ) {
        char[] passphrase =
                pendingRestorePassphraseToSave;

        pendingRestorePassphraseToSave =
                null;

        if (passphrase == null) {
            return;
        }

        try {
            keyVault.savePassphrase(
                    firebaseUserId,
                    passphrase
            );

        } catch (Exception exception) {
            Toast.makeText(
                    activity,
                    "Backup unlock हो गया, लेकिन recovery passphrase "
                            + "इस device पर save नहीं हो सकी।",
                    Toast.LENGTH_LONG
            ).show();

        } finally {
            CloudBackupEncryption
                    .clearSensitiveCharacters(
                            passphrase
                    );
        }
    }

    private void handleRestoreDownloadError(
            long operationToken,
            @NonNull FirebaseUser firebaseUser,
            @NonNull Exception exception
    ) {
        String firebaseUserId =
                firebaseUser.getUid();

        if (!isCurrentRestoreOperation(
                operationToken,
                firebaseUserId
        )) {
            clearPendingRestorePassphrase();

            return;
        }

        boolean invalidPassphrase =
                isInvalidRecoveryPassphrase(
                        exception
                );

        String message =
                safeMessage(
                        exception,
                        "Encrypted cloud backup download या decrypt नहीं हो सका।"
                );

        invalidateRestoreOperation();

        txtCloudBackupAvailability.setText(
                invalidPassphrase
                        ? "Passphrase required"
                        : "Restore failed"
        );

        txtCloudBackupStatus.setText(
                message
        );

        updateCloudOperationButtonAvailability(
                firebaseAuth.getCurrentUser()
        );

        if (invalidPassphrase) {
            new AlertDialog.Builder(
                    activity
            )
                    .setTitle(
                            "Incorrect Recovery Passphrase"
                    )
                    .setMessage(
                            message
                                    + "\n\nBackup बनाते समय उपयोग की गई "
                                    + "सही recovery passphrase दर्ज करें।"
                    )
                    .setPositiveButton(
                            "Enter Passphrase",
                            (dialog, which) -> {
                                FirebaseUser currentUser =
                                        firebaseAuth.getCurrentUser();

                                if (currentUser != null
                                        && currentUser
                                        .getUid()
                                        .equals(
                                                firebaseUserId
                                        )) {

                                    showRestorePassphraseDialog(
                                            currentUser,
                                            true
                                    );
                                }
                            }
                    )
                    .setNegativeButton(
                            "Cancel",
                            null
                    )
                    .show();

            return;
        }

        new AlertDialog.Builder(
                activity
        )
                .setTitle(
                        "Cloud Restore Failed"
                )
                .setMessage(
                        message
                                + "\n\nInternet connection, Firebase account, "
                                + "Firestore rules और उपलब्ध cloud backup की "
                                + "जाँच करें।"
                )
                .setPositiveButton(
                        "OK",
                        null
                )
                .show();
    }

    private void handleRestoreValidationError(
            long operationToken,
            @NonNull String firebaseUserId,
            @NonNull Exception exception
    ) {
        if (!isCurrentRestoreOperation(
                operationToken,
                firebaseUserId
        )) {
            return;
        }

        String message =
                safeMessage(
                        exception,
                        "Downloaded cloud backup validation failed."
                );

        invalidateRestoreOperation();

        txtCloudBackupAvailability.setText(
                "Invalid backup"
        );

        txtCloudBackupStatus.setText(
                message
        );

        updateCloudOperationButtonAvailability(
                firebaseAuth.getCurrentUser()
        );

        new AlertDialog.Builder(
                activity
        )
                .setTitle(
                        "Backup Validation Failed"
                )
                .setMessage(
                        message
                                + "\n\nLocal database में कोई बदलाव नहीं किया गया।"
                )
                .setPositiveButton(
                        "OK",
                        null
                )
                .show();
    }

    private void showRestorePreviewDialog(
            long operationToken,
            @NonNull FirebaseUser firebaseUser,
            @NonNull EncryptedCloudBackupDownloader
                    .DecryptedCloudBackup backup,
            @NonNull CloudBackupRestoreCoordinator
                    .RestorePreview preview
    ) {
        if (!isCurrentRestoreOperation(
                operationToken,
                firebaseUser.getUid()
        )) {
            backup.clearSensitiveData();

            return;
        }

        txtCloudBackupAvailability.setText(
                "Backup verified"
        );

        txtCloudBackupStatus.setText(
                "Cloud backup पूरी तरह verify हो गया है। Restore confirmation "
                        + "के बाद ही local records replace होंगे।"
        );

        String previewMessage =
                createRestorePreviewMessage(
                        preview
                );

        AlertDialog dialog =
                new AlertDialog.Builder(
                        activity
                )
                        .setTitle(
                                "Verified Cloud Backup"
                        )
                        .setMessage(
                                previewMessage
                        )
                        .setPositiveButton(
                                "Continue",
                                (dialogInterface, which) ->
                                        showFinalRestoreConfirmation(
                                                operationToken,
                                                firebaseUser,
                                                backup,
                                                preview
                                        )
                        )
                        .setNegativeButton(
                                "Cancel",
                                (dialogInterface, which) ->
                                        cancelCloudRestore(
                                                "Cloud restore cancel कर दिया गया।"
                                        )
                        )
                        .create();

        dialog.setOnCancelListener(
                ignored -> cancelCloudRestore(
                        "Cloud restore cancel कर दिया गया।"
                )
        );

        dialog.show();
    }

    @NonNull
    private String createRestorePreviewMessage(
            @NonNull CloudBackupRestoreCoordinator
                    .RestorePreview preview
    ) {
        StringBuilder message =
                new StringBuilder();

        message.append(
                "Backup created: "
        );

        message.append(
                formatDateTime(
                        preview.getBackupCreatedAt()
                )
        );

        if (preview.getUploadedAtClient() > 0L) {
            message.append(
                    "\nUploaded: "
            );

            message.append(
                    formatDateTime(
                            preview.getUploadedAtClient()
                    )
            );
        }

        if (!preview
                .getAppVersionName()
                .trim()
                .isEmpty()) {

            message.append(
                    "\nApp version: "
            );

            message.append(
                    preview.getAppVersionName()
            );
        }

        message.append(
                "\nDatabase version: "
        );

        message.append(
                preview.getDatabaseVersion()
        );

        message.append(
                "\nBackup ID: "
        );

        message.append(
                preview.getBackupId()
        );

        message.append(
                "\n\nTotal records: "
        );

        message.append(
                preview.getTotalRecordCount()
        );

        message.append(
                "\nTransactions: "
        );

        message.append(
                preview.getTransactionCount()
        );

        message.append(
                "\nExpense items: "
        );

        message.append(
                preview.getExpenseItemCount()
        );

        message.append(
                "\nCategories: "
        );

        message.append(
                preview.getCategoryCount()
        );

        message.append(
                "\nAccounts: "
        );

        message.append(
                preview.getAccountCount()
        );

        message.append(
                "\nGoals: "
        );

        message.append(
                preview.getGoalCount()
        );

        message.append(
                "\nRecurring transactions: "
        );

        message.append(
                preview.getRecurringTransactionCount()
        );

        message.append(
                "\nBudgets: "
        );

        message.append(
                preview.getBudgetCount()
        );

        message.append(
                "\nLoans: "
        );

        message.append(
                preview.getLoanCount()
        );

        message.append(
                "\nLoan payments: "
        );

        message.append(
                preview.getLoanPaymentCount()
        );

        message.append(
                "\nSubscriptions: "
        );

        message.append(
                preview.getSubscriptionCount()
        );

        message.append(
                "\nCredit cards: "
        );

        message.append(
                preview.getCreditCardCount()
        );

        message.append(
                "\nCredit-card payments: "
        );

        message.append(
                preview.getCreditCardPaymentCount()
        );

        message.append(
                "\nInvestments: "
        );

        message.append(
                preview.getInvestmentCount()
        );

        message.append(
                "\n\nअगले चरण में वर्तमान local finance records "
                        + "स्थायी रूप से replace होंगे।"
        );

        return message.toString();
    }

    private void showFinalRestoreConfirmation(
            long operationToken,
            @NonNull FirebaseUser firebaseUser,
            @NonNull EncryptedCloudBackupDownloader
                    .DecryptedCloudBackup backup,
            @NonNull CloudBackupRestoreCoordinator
                    .RestorePreview preview
    ) {
        if (!isCurrentRestoreOperation(
                operationToken,
                firebaseUser.getUid()
        )) {
            backup.clearSensitiveData();

            return;
        }

        LinearLayout container =
                new LinearLayout(
                        activity
                );

        container.setOrientation(
                LinearLayout.VERTICAL
        );

        container.setPadding(
                dp(
                        22
                ),
                dp(
                        8
                ),
                dp(
                        22
                ),
                0
        );

        EditText confirmationInput =
                new EditText(
                        activity
                );

        confirmationInput.setSingleLine(
                true
        );

        confirmationInput.setHint(
                "Type RESTORE"
        );

        confirmationInput.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        );

        container.addView(
                confirmationInput
        );

        String warning =
                "यह operation current local transactions, accounts, "
                        + "categories, goals, budgets, loans, subscriptions, "
                        + "credit cards, payments, expense items और investments "
                        + "को cloud backup से replace करेगी।\n\n"
                        + "Total verified records: "
                        + preview.getTotalRecordCount()
                        + "\nBackup date: "
                        + formatDateTime(
                        preview.getBackupCreatedAt()
                )
                        + "\n\nजारी रखने के लिए RESTORE लिखें।";

        AlertDialog dialog =
                new AlertDialog.Builder(
                        activity
                )
                        .setTitle(
                                "Final Restore Confirmation"
                        )
                        .setMessage(
                                warning
                        )
                        .setView(
                                container
                        )
                        .setPositiveButton(
                                "Restore Now",
                                null
                        )
                        .setNegativeButton(
                                "Cancel",
                                (dialogInterface, which) ->
                                        cancelCloudRestore(
                                                "Cloud restore cancel कर दिया गया।"
                                        )
                        )
                        .create();

        dialog.setOnShowListener(
                ignored -> dialog
                        .getButton(
                                DialogInterface.BUTTON_POSITIVE
                        )
                        .setOnClickListener(
                                view -> {
                                    String typedText =
                                            confirmationInput
                                                    .getText()
                                                    .toString()
                                                    .trim();

                                    if (!"RESTORE".equals(
                                            typedText
                                    )) {
                                        confirmationInput.setError(
                                                "RESTORE बड़े अक्षरों में लिखें।"
                                        );

                                        return;
                                    }

                                    dialog.dismiss();

                                    performCloudRestore(
                                            operationToken,
                                            firebaseUser,
                                            backup
                                    );
                                }
                        )
        );

        dialog.setOnCancelListener(
                ignored -> cancelCloudRestore(
                        "Cloud restore cancel कर दिया गया।"
                )
        );

        dialog.show();
    }

    private void performCloudRestore(
            long operationToken,
            @NonNull FirebaseUser firebaseUser,
            @NonNull EncryptedCloudBackupDownloader
                    .DecryptedCloudBackup backup
    ) {
        String firebaseUserId =
                firebaseUser.getUid();

        if (!isCurrentRestoreOperation(
                operationToken,
                firebaseUserId
        )
                || pendingRestoreBackup != backup) {

            backup.clearSensitiveData();

            return;
        }

        showRestoreProgress(
                "Restoring",
                "Restoring Data...",
                "Verified cloud backup से local finance database replace "
                        + "किया जा रहा है। App को बंद न करें। Room transaction "
                        + "fail होने पर पुराने local records rollback होंगे।"
        );

        CloudBackupRestoreCoordinator coordinator =
                new CloudBackupRestoreCoordinator(
                        applicationContext
                );

        coordinator.restoreReplaceAll(
                firebaseUser,
                backup,
                new CloudBackupRestoreCoordinator.RestoreCallback() {
                    @Override
                    public void onSuccess(
                            @NonNull CloudBackupRestoreCoordinator
                                    .RestoreResult result
                    ) {
                        if (!isCurrentRestoreOperation(
                                operationToken,
                                firebaseUserId
                        )) {
                            backup.clearSensitiveData();

                            return;
                        }

                        showCloudRestoreSuccess(
                                result
                        );
                    }

                    @Override
                    public void onError(
                            @NonNull Exception exception
                    ) {
                        if (!isCurrentRestoreOperation(
                                operationToken,
                                firebaseUserId
                        )) {
                            return;
                        }

                        String message =
                                safeMessage(
                                        exception,
                                        "Cloud backup local database में "
                                                + "restore नहीं हो सका।"
                                );

                        invalidateRestoreOperation();

                        txtCloudBackupAvailability.setText(
                                "Restore failed"
                        );

                        txtCloudBackupStatus.setText(
                                message
                        );

                        updateCloudOperationButtonAvailability(
                                firebaseAuth.getCurrentUser()
                        );

                        new AlertDialog.Builder(
                                activity
                        )
                                .setTitle(
                                        "Restore Failed"
                                )
                                .setMessage(
                                        message
                                                + "\n\nRoom transaction fail "
                                                + "होने पर local database rollback "
                                                + "कर दी गई है।"
                                )
                                .setPositiveButton(
                                        "OK",
                                        null
                                )
                                .show();
                    }
                }
        );
    }

    private void showCloudRestoreSuccess(
            @NonNull CloudBackupRestoreCoordinator
                    .RestoreResult result
    ) {
        String resultMessage =
                "Cloud restore सफल रहा।"
                        + "\n\nRestored: "
                        + formatDateTime(
                        result.getRestoredAt()
                )
                        + "\nBackup date: "
                        + formatDateTime(
                        result.getBackupCreatedAt()
                )
                        + "\nTotal records: "
                        + result.getTotalRecordCount()
                        + "\nTransactions: "
                        + result.getTransactionCount()
                        + "\nExpense items: "
                        + result.getExpenseItemCount()
                        + "\nAccounts: "
                        + result.getAccountCount()
                        + "\nCategories: "
                        + result.getCategoryCount()
                        + "\nInvestments: "
                        + result.getInvestmentCount()
                        + "\nBackup ID: "
                        + result.getBackupId();

        invalidateRestoreOperation();

        txtCloudBackupAvailability.setText(
                "Restore complete"
        );

        txtCloudBackupStatus.setText(
                resultMessage
        );

        updateCloudOperationButtonAvailability(
                firebaseAuth.getCurrentUser()
        );

        new AlertDialog.Builder(
                activity
        )
                .setTitle(
                        "Cloud Restore Complete"
                )
                .setMessage(
                        resultMessage
                                + "\n\nUpdated records देखने के लिए संबंधित "
                                + "screen दोबारा खोलें।"
                )
                .setPositiveButton(
                        "OK",
                        null
                )
                .show();
    }

    private void showRestoreProgress(
            @NonNull String availability,
            @NonNull String restoreButtonText,
            @NonNull String message
    ) {
        btnCloudBackupNow.setEnabled(
                false
        );

        btnCloudBackupNow.setAlpha(
                0.55F
        );

        btnCloudBackupNow.setText(
                "Backup Now"
        );

        btnCloudRestore.setEnabled(
                false
        );

        btnCloudRestore.setAlpha(
                0.65F
        );

        btnCloudRestore.setText(
                restoreButtonText
        );

        txtCloudBackupAvailability.setText(
                availability
        );

        txtCloudBackupStatus.setText(
                message
        );
    }

    private void cancelCloudRestore(
            @NonNull String message
    ) {
        invalidateRestoreOperation();

        txtCloudBackupAvailability.setText(
                "Restore cancelled"
        );

        txtCloudBackupStatus.setText(
                message
        );

        updateCloudOperationButtonAvailability(
                firebaseAuth.getCurrentUser()
        );
    }

    private void showRestoreFailure(
            @NonNull String message
    ) {
        invalidateRestoreOperation();

        txtCloudBackupAvailability.setText(
                "Restore failed"
        );

        txtCloudBackupStatus.setText(
                message
        );

        updateCloudOperationButtonAvailability(
                firebaseAuth.getCurrentUser()
        );

        Toast.makeText(
                activity,
                message,
                Toast.LENGTH_LONG
        ).show();
    }

    private boolean isCurrentRestoreOperation(
            long operationToken,
            @NonNull String firebaseUserId
    ) {
        if (!cloudRestoreRunning
                || operationToken
                != restoreOperationToken
                || !firebaseUserId.equals(
                activeRestoreFirebaseUserId
        )
                || !isActivityUsable()) {

            return false;
        }

        FirebaseUser currentUser =
                firebaseAuth.getCurrentUser();

        return currentUser != null
                && firebaseUserId.equals(
                currentUser.getUid()
        );
    }

    private boolean isActivityUsable() {
        return !activity.isFinishing()
                && !activity.isDestroyed();
    }

    private boolean isInvalidRecoveryPassphrase(
            @NonNull Throwable throwable
    ) {
        Throwable current =
                throwable;

        int inspectedCauses =
                0;

        while (current != null
                && inspectedCauses < 12) {

            if (current instanceof EncryptedCloudBackupDownloader
                    .InvalidRecoveryPassphraseException) {

                return true;
            }

            current =
                    current.getCause();

            inspectedCauses++;
        }

        return false;
    }

    private void invalidateRestoreOperation() {
        restoreOperationToken++;

        cloudRestoreRunning =
                false;

        activeRestoreFirebaseUserId =
                "";

        clearPendingRestoreSensitiveData();

        btnCloudRestore.setText(
                "Cloud Restore"
        );
    }

    private void clearPendingRestoreSensitiveData() {
        if (pendingRestoreBackup != null) {
            pendingRestoreBackup
                    .clearSensitiveData();

            pendingRestoreBackup =
                    null;
        }

        clearPendingRestorePassphrase();
    }

    private void clearPendingRestorePassphrase() {
        CloudBackupEncryption
                .clearSensitiveCharacters(
                        pendingRestorePassphraseToSave
                );

        pendingRestorePassphraseToSave =
                null;
    }

    private void showTimePicker() {
        TimePickerDialog dialog =
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

        dialog.setTitle(
                "Preferred cloud backup time"
        );

        dialog.show();
    }

    private void saveCloudSchedule() {
        FirebaseUser firebaseUser =
                firebaseAuth.getCurrentUser();

        if (firebaseUser == null) {
            Toast.makeText(
                    activity,
                    "Cloud schedule save करने के लिए Firebase account में sign in करें।",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        String firebaseUserId =
                firebaseUser.getUid();

        btnSaveCloudSchedule.setEnabled(
                false
        );

        try {
            BackupSchedulePreferences.BackupFrequency frequency =
                    frequencyFromDisplayName(
                            dropdownCloudFrequency
                                    .getText()
                                    .toString()
                    );

            int weeklyDay =
                    getCalendarDay(
                            dropdownCloudWeeklyDay
                                    .getText()
                                    .toString()
                    );

            int monthlyDay =
                    parseMonthlyDay(
                            dropdownCloudMonthlyDay
                                    .getText()
                                    .toString()
                    );

            BackupSchedulePreferences.ScheduleSettings settings =
                    new BackupSchedulePreferences.ScheduleSettings(
                            frequency,
                            switchCloudWifiOnly.isChecked(),
                            switchCloudChargingOnly.isChecked(),
                            selectedHour,
                            selectedMinute,
                            weeklyDay,
                            monthlyDay
                    );

            schedulePreferences.saveCloudSchedule(
                    firebaseUserId,
                    settings
            );

            if (!frequency.isAutomatic()) {
                cancelAutomaticCloudBackup(
                        firebaseUserId
                );

                Toast.makeText(
                        activity,
                        "Cloud backup setting saved: "
                                + frequency.getDisplayName(),
                        Toast.LENGTH_LONG
                ).show();

                return;
            }

            if (!firebaseUser.isEmailVerified()) {
                cancelAutomaticCloudBackup(
                        firebaseUserId
                );

                Toast.makeText(
                        activity,
                        "Automatic cloud backup के लिए email verification आवश्यक है।",
                        Toast.LENGTH_LONG
                ).show();

                return;
            }

            if (!keyVault.hasSavedPassphrase(
                    firebaseUserId
            )) {
                cancelAutomaticCloudBackup(
                        firebaseUserId
                );

                showRecoveryPassphraseDialog(
                        firebaseUser,
                        AFTER_PASSPHRASE_APPLY_SCHEDULE
                );

                return;
            }

            CloudBackupScheduler.ScheduleResult result =
                    CloudBackupScheduler
                            .applySavedScheduleForCurrentUser(
                                    applicationContext
                            );

            Toast.makeText(
                    activity,
                    createScheduleSuccessMessage(
                            result
                    ),
                    Toast.LENGTH_LONG
            ).show();

        } catch (Exception exception) {
            Toast.makeText(
                    activity,
                    safeMessage(
                            exception,
                            "Cloud backup schedule could not be saved."
                    ),
                    Toast.LENGTH_LONG
            ).show();

        } finally {
            btnSaveCloudSchedule.setEnabled(
                    true
            );

            refresh();
        }
    }

    private void cancelAutomaticCloudBackup(
            @NonNull String firebaseUserId
    ) {
        try {
            CloudBackupScheduler.cancelForAccount(
                    applicationContext,
                    firebaseUserId
            );

        } catch (Exception ignored) {
            try {
                schedulePreferences.setCloudNextScheduledAt(
                        firebaseUserId,
                        0L
                );

            } catch (Exception ignoredAgain) {
                // The next refresh will still display saved settings.
            }
        }
    }

    @NonNull
    private String createScheduleSuccessMessage(
            @NonNull CloudBackupScheduler.ScheduleResult result
    ) {
        if (!result.isScheduled()) {
            return "Cloud backup setting saved: "
                    + result.getFrequencyDisplayName();
        }

        return "Encrypted cloud backup scheduled. Next check: "
                + formatDateTime(
                result.getNextPreferredRunAtMillis()
        );
    }

    private void showCloudAccountActions() {
        FirebaseUser firebaseUser =
                firebaseAuth.getCurrentUser();

        if (firebaseUser == null) {
            new AlertDialog.Builder(
                    activity
            )
                    .setTitle(
                            "Cloud Account Required"
                    )
                    .setMessage(
                            "Encrypted cloud backup उपयोग करने के लिए "
                                    + "पहले Money Manager Pro में Firebase "
                                    + "account से sign in करें।"
                    )
                    .setPositiveButton(
                            "OK",
                            null
                    )
                    .show();

            return;
        }

        List<String> actionLabels =
                new ArrayList<>();

        List<Integer> actionIds =
                new ArrayList<>();

        actionLabels.add(
                "Refresh Firebase account"
        );

        actionIds.add(
                ACTION_REFRESH_ACCOUNT
        );

        if (!firebaseUser.isEmailVerified()) {
            actionLabels.add(
                    "Send verification email"
            );

            actionIds.add(
                    ACTION_SEND_VERIFICATION
            );
        }

        actionLabels.add(
                keyVault.hasSavedPassphrase(
                        firebaseUser.getUid()
                )
                        ? "Change recovery passphrase"
                        : "Save recovery passphrase"
        );

        actionIds.add(
                ACTION_SAVE_PASSPHRASE
        );

        if (keyVault.hasSavedPassphrase(
                firebaseUser.getUid()
        )) {
            actionLabels.add(
                    "Remove passphrase from this device"
            );

            actionIds.add(
                    ACTION_REMOVE_PASSPHRASE
            );
        }

        String email =
                firebaseUser.getEmail();

        String dialogTitle =
                email == null
                        || email.trim().isEmpty()
                        ? "Cloud Account"
                        : email.trim();

        new AlertDialog.Builder(
                activity
        )
                .setTitle(
                        dialogTitle
                )
                .setItems(
                        actionLabels.toArray(
                                new String[0]
                        ),
                        (dialog, which) -> {
                            int actionId =
                                    actionIds.get(
                                            which
                                    );

                            performAccountAction(
                                    firebaseUser,
                                    actionId
                            );
                        }
                )
                .setNegativeButton(
                        "Close",
                        null
                )
                .show();
    }

    private void performAccountAction(
            @NonNull FirebaseUser firebaseUser,
            int actionId
    ) {
        switch (actionId) {
            case ACTION_REFRESH_ACCOUNT:
                reloadFirebaseAccount(
                        firebaseUser
                );
                break;

            case ACTION_SEND_VERIFICATION:
                sendVerificationEmail(
                        firebaseUser
                );
                break;

            case ACTION_SAVE_PASSPHRASE:
                showRecoveryPassphraseDialog(
                        firebaseUser,
                        AFTER_PASSPHRASE_NONE
                );
                break;

            case ACTION_REMOVE_PASSPHRASE:
                confirmRemoveRecoveryPassphrase(
                        firebaseUser
                );
                break;

            default:
                break;
        }
    }

    private void reloadFirebaseAccount(
            @NonNull FirebaseUser firebaseUser
    ) {
        btnCloudAccountAction.setEnabled(
                false
        );

        firebaseUser.reload()
                .addOnCompleteListener(
                        activity,
                        task -> {
                            btnCloudAccountAction.setEnabled(
                                    true
                            );

                            if (task.isSuccessful()) {
                                Toast.makeText(
                                        activity,
                                        "Firebase account refreshed.",
                                        Toast.LENGTH_SHORT
                                ).show();

                            } else {
                                Toast.makeText(
                                        activity,
                                        safeMessage(
                                                task.getException(),
                                                "Firebase account refresh failed."
                                        ),
                                        Toast.LENGTH_LONG
                                ).show();
                            }

                            refresh();
                        }
                );
    }

    private void sendVerificationEmail(
            @NonNull FirebaseUser firebaseUser
    ) {
        btnCloudAccountAction.setEnabled(
                false
        );

        firebaseUser.sendEmailVerification()
                .addOnCompleteListener(
                        activity,
                        task -> {
                            btnCloudAccountAction.setEnabled(
                                    true
                            );

                            if (task.isSuccessful()) {
                                Toast.makeText(
                                        activity,
                                        "Verification email भेज दी गई है। "
                                                + "Email खोलकर verify करें।",
                                        Toast.LENGTH_LONG
                                ).show();

                            } else {
                                Toast.makeText(
                                        activity,
                                        safeMessage(
                                                task.getException(),
                                                "Verification email नहीं भेजी जा सकी।"
                                        ),
                                        Toast.LENGTH_LONG
                                ).show();
                            }

                            refresh();
                        }
                );
    }

    private void showRecoveryPassphraseDialog(
            @NonNull FirebaseUser firebaseUser,
            int actionAfterSave
    ) {
        LinearLayout container =
                new LinearLayout(
                        activity
                );

        container.setOrientation(
                LinearLayout.VERTICAL
        );

        int horizontalPadding =
                dp(
                        22
                );

        int verticalPadding =
                dp(
                        8
                );

        container.setPadding(
                horizontalPadding,
                verticalPadding,
                horizontalPadding,
                0
        );

        EditText passphraseInput =
                createPasswordInput(
                        "Recovery passphrase"
                );

        EditText confirmationInput =
                createPasswordInput(
                        "Confirm recovery passphrase"
                );

        container.addView(
                passphraseInput
        );

        LinearLayout.LayoutParams confirmationParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        confirmationParams.topMargin =
                dp(
                        10
                );

        container.addView(
                confirmationInput,
                confirmationParams
        );

        String message =
                "यह passphrase cloud backup encrypt और restore करेगी।\n\n"
                        + "यह Firebase पर save नहीं होगी। नए फोन पर "
                        + "restore करते समय यही passphrase आवश्यक होगी।\n\n"
                        + "Length: "
                        + CloudBackupEncryption.MINIMUM_PASSPHRASE_LENGTH
                        + " से "
                        + CloudBackupEncryption.MAXIMUM_PASSPHRASE_LENGTH
                        + " characters";

        AlertDialog dialog =
                new AlertDialog.Builder(
                        activity
                )
                        .setTitle(
                                keyVault.hasSavedPassphrase(
                                        firebaseUser.getUid()
                                )
                                        ? "Change Recovery Passphrase"
                                        : "Save Recovery Passphrase"
                        )
                        .setMessage(
                                message
                        )
                        .setView(
                                container
                        )
                        .setPositiveButton(
                                "Save",
                                null
                        )
                        .setNegativeButton(
                                "Cancel",
                                null
                        )
                        .create();

        dialog.setOnShowListener(
                ignored -> dialog
                        .getButton(
                                DialogInterface.BUTTON_POSITIVE
                        )
                        .setOnClickListener(
                                view -> saveRecoveryPassphrase(
                                        dialog,
                                        firebaseUser,
                                        passphraseInput,
                                        confirmationInput,
                                        actionAfterSave
                                )
                        )
        );

        dialog.show();
    }

    @NonNull
    private EditText createPasswordInput(
            @NonNull String hint
    ) {
        EditText editText =
                new EditText(
                        activity
                );

        editText.setLayoutParams(
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        editText.setHint(
                hint
        );

        editText.setSingleLine(
                true
        );

        editText.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_VARIATION_PASSWORD
        );

        editText.setPadding(
                dp(
                        12
                ),
                dp(
                        10
                ),
                dp(
                        12
                ),
                dp(
                        10
                )
        );

        return editText;
    }

    private void saveRecoveryPassphrase(
            @NonNull AlertDialog dialog,
            @NonNull FirebaseUser firebaseUser,
            @NonNull EditText passphraseInput,
            @NonNull EditText confirmationInput,
            int actionAfterSave
    ) {
        char[] passphrase =
                editableToCharacters(
                        passphraseInput.getText()
                );

        char[] confirmation =
                editableToCharacters(
                        confirmationInput.getText()
                );

        try {
            if (passphrase.length
                    < CloudBackupEncryption
                    .MINIMUM_PASSPHRASE_LENGTH) {

                passphraseInput.setError(
                        "कम से कम "
                                + CloudBackupEncryption
                                .MINIMUM_PASSPHRASE_LENGTH
                                + " characters दर्ज करें।"
                );

                return;
            }

            if (passphrase.length
                    > CloudBackupEncryption
                    .MAXIMUM_PASSPHRASE_LENGTH) {

                passphraseInput.setError(
                        "Passphrase अधिकतम "
                                + CloudBackupEncryption
                                .MAXIMUM_PASSPHRASE_LENGTH
                                + " characters हो सकती है।"
                );

                return;
            }

            if (!charactersEqual(
                    passphrase,
                    confirmation
            )) {
                confirmationInput.setError(
                        "दोनों passphrases समान नहीं हैं।"
                );

                return;
            }

            keyVault.savePassphrase(
                    firebaseUser.getUid(),
                    passphrase
            );

            passphraseInput.setText(
                    ""
            );

            confirmationInput.setText(
                    ""
            );

            dialog.dismiss();

            if (actionAfterSave
                    == AFTER_PASSPHRASE_APPLY_SCHEDULE) {

                applyAutomaticScheduleAfterPassphrase(
                        firebaseUser
                );

            } else if (actionAfterSave
                    == AFTER_PASSPHRASE_START_MANUAL_BACKUP) {

                enqueueManualCloudBackup(
                        firebaseUser
                );

            } else {
                BackupSchedulePreferences.ScheduleSettings settings =
                        schedulePreferences.getCloudSchedule(
                                firebaseUser.getUid()
                        );

                if (settings.isAutomaticEnabled()
                        && firebaseUser.isEmailVerified()) {

                    applyAutomaticScheduleAfterPassphrase(
                            firebaseUser
                    );
                }
            }

            Toast.makeText(
                    activity,
                    "Recovery passphrase इस device पर सुरक्षित हो गई है।",
                    Toast.LENGTH_LONG
            ).show();

        } catch (Exception exception) {
            Toast.makeText(
                    activity,
                    safeMessage(
                            exception,
                            "Recovery passphrase सुरक्षित नहीं की जा सकी।"
                    ),
                    Toast.LENGTH_LONG
            ).show();

        } finally {
            CloudBackupEncryption
                    .clearSensitiveCharacters(
                            passphrase
                    );

            CloudBackupEncryption
                    .clearSensitiveCharacters(
                            confirmation
                    );

            refresh();
        }
    }

    private void applyAutomaticScheduleAfterPassphrase(
            @NonNull FirebaseUser firebaseUser
    ) {
        if (!firebaseUser.isEmailVerified()) {
            return;
        }

        try {
            CloudBackupScheduler
                    .applySavedScheduleForCurrentUser(
                            applicationContext
                    );

        } catch (Exception exception) {
            Toast.makeText(
                    activity,
                    safeMessage(
                            exception,
                            "Recovery passphrase saved, but cloud "
                                    + "schedule could not be applied."
                    ),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void confirmRemoveRecoveryPassphrase(
            @NonNull FirebaseUser firebaseUser
    ) {
        new AlertDialog.Builder(
                activity
        )
                .setTitle(
                        "Remove Local Recovery Passphrase?"
                )
                .setMessage(
                        "Passphrase केवल इस device से हटेगी। "
                                + "Firebase पर मौजूद encrypted backup delete नहीं होगा।\n\n"
                                + "Automatic cloud backup रुक जाएगा। "
                                + "दोबारा backup या restore करने के लिए "
                                + "passphrase फिर दर्ज करनी होगी।"
                )
                .setPositiveButton(
                        "Remove",
                        (dialog, which) ->
                                removeRecoveryPassphrase(
                                        firebaseUser
                                )
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .show();
    }

    private void removeRecoveryPassphrase(
            @NonNull FirebaseUser firebaseUser
    ) {
        try {
            cancelAutomaticCloudBackup(
                    firebaseUser.getUid()
            );

            keyVault.clearPassphrase(
                    firebaseUser.getUid()
            );

            Toast.makeText(
                    activity,
                    "Recovery passphrase इस device से हट गई है।",
                    Toast.LENGTH_LONG
            ).show();

        } catch (Exception exception) {
            Toast.makeText(
                    activity,
                    safeMessage(
                            exception,
                            "Recovery passphrase हटाई नहीं जा सकी।"
                    ),
                    Toast.LENGTH_LONG
            ).show();

        } finally {
            refresh();
        }
    }

    private void updateFirebaseAccountViews(
            @NonNull FirebaseUser firebaseUser
    ) {
        String email =
                firebaseUser.getEmail();

        if (email == null
                || email.trim().isEmpty()) {

            email =
                    "Firebase account";
        }

        String accountStatus =
                email.trim()
                        + "\n"
                        + (
                        firebaseUser.isEmailVerified()
                                ? "Email verified"
                                : "Email verification required"
                );

        txtCloudAccountStatus.setText(
                accountStatus
        );

        btnCloudAccountAction.setText(
                firebaseUser.isEmailVerified()
                        ? "Manage Cloud Account"
                        : "Verify Cloud Account"
        );
    }

    private void updateRecoveryKeyStatus(
            @NonNull String firebaseUserId
    ) {
        if (manualBackupRunning) {
            txtCloudBackupAvailability.setText(
                    "Uploading"
            );

            return;
        }

        if (cloudRestoreRunning) {
            txtCloudBackupAvailability.setText(
                    "Restore in progress"
            );

            return;
        }

        boolean passphraseSaved =
                keyVault.hasSavedPassphrase(
                        firebaseUserId
                );

        txtCloudBackupAvailability.setText(
                passphraseSaved
                        ? "Key saved"
                        : "Key required"
        );
    }

    private void updateCloudScheduleStatus(
            @NonNull String firebaseUserId,
            @NonNull BackupSchedulePreferences
                    .ScheduleSettings settings
    ) {
        if (manualBackupRunning
                || cloudRestoreRunning) {

            return;
        }

        BackupSchedulePreferences.BackupStatus status;

        try {
            status =
                    schedulePreferences.getCloudStatus(
                            firebaseUserId
                    );

        } catch (Exception exception) {
            txtCloudBackupStatus.setText(
                    safeMessage(
                            exception,
                            "Cloud backup status could not be read."
                    )
            );

            return;
        }

        StringBuilder text =
                new StringBuilder();

        text.append(
                "Schedule: "
        );

        text.append(
                settings
                        .getFrequency()
                        .getDisplayName()
        );

        if (settings.isAutomaticEnabled()) {
            text.append(
                    " • "
            );

            text.append(
                    formatSelectedTime(
                            settings.getPreferredHour(),
                            settings.getPreferredMinute()
                    )
            );

            if (settings.getFrequency()
                    == BackupSchedulePreferences
                    .BackupFrequency
                    .WEEKLY) {

                text.append(
                        " • "
                );

                text.append(
                        getWeekDayLabel(
                                settings.getWeeklyDayOfWeek()
                        )
                );
            }

            if (settings.getFrequency()
                    == BackupSchedulePreferences
                    .BackupFrequency
                    .MONTHLY) {

                text.append(
                        " • Date "
                );

                text.append(
                        settings.getMonthlyDayOfMonth()
                );
            }

            if (settings.isWifiOnly()) {
                text.append(
                        " • Wi-Fi only"
                );
            }

            if (settings.isChargingOnly()) {
                text.append(
                        " • Charging only"
                );
            }
        }

        if (status.hasSuccessfulBackup()) {
            text.append(
                    "\nLast backup: "
            );

            text.append(
                    formatDateTime(
                            status.getLastSuccessAtMillis()
                    )
            );

            text.append(
                    "\nRecords: "
            );

            text.append(
                    status.getLastRecordCount()
            );

            text.append(
                    " • "
            );

            text.append(
                    formatFileSize(
                            status.getLastByteCount()
                    )
            );

        } else {
            text.append(
                    "\nअभी कोई successful cloud backup record नहीं है।"
            );
        }

        if (status.hasFailureAfterLastSuccess()) {
            text.append(
                    "\nLast error: "
            );

            text.append(
                    status.getLastFailureMessage()
            );
        }

        if (settings.isAutomaticEnabled()
                && status.getNextScheduledAtMillis() > 0L) {

            text.append(
                    "\nNext check: "
            );

            text.append(
                    formatDateTime(
                            status.getNextScheduledAtMillis()
                    )
            );
        }

        txtCloudBackupStatus.setText(
                text.toString()
        );
    }

    private void showSignedOutState() {
        invalidateRestoreOperation();

        detachManualWorkObserver();

        manualBackupRunning =
                false;

        activeManualWorkId =
                null;

        txtCloudAccountStatus.setText(
                "Firebase account signed in नहीं है।"
        );

        txtCloudBackupAvailability.setText(
                "Sign in required"
        );

        txtCloudBackupStatus.setText(
                "Encrypted cloud backup उपयोग करने के लिए "
                        + "Firebase account में sign in करें।"
        );

        btnCloudAccountAction.setText(
                "Cloud Account Required"
        );

        dropdownCloudFrequency.setText(
                "Manual only",
                false
        );

        selectedHour =
                BackupSchedulePreferences
                        .DEFAULT_PREFERRED_HOUR;

        selectedMinute =
                BackupSchedulePreferences
                        .DEFAULT_PREFERRED_MINUTE;

        updateTimeButtonText();

        groupCloudWeeklyDay.setVisibility(
                View.GONE
        );

        groupCloudMonthlyDay.setVisibility(
                View.GONE
        );

        setScheduleControlsEnabled(
                false
        );

        btnCloudAccountAction.setEnabled(
                true
        );

        btnCloudAccountAction.setAlpha(
                1F
        );

        updateCloudOperationButtonAvailability(
                null
        );
    }

    private void showAccountErrorState(
            @NonNull FirebaseUser firebaseUser,
            @NonNull String message
    ) {
        updateFirebaseAccountViews(
                firebaseUser
        );

        txtCloudBackupAvailability.setText(
                "Status error"
        );

        txtCloudBackupStatus.setText(
                message
        );

        setScheduleControlsEnabled(
                false
        );

        btnCloudAccountAction.setEnabled(
                true
        );

        updateCloudOperationButtonAvailability(
                firebaseUser
        );
    }

    private void setScheduleControlsEnabled(
            boolean enabled
    ) {
        dropdownCloudFrequency.setEnabled(
                enabled
        );

        dropdownCloudWeeklyDay.setEnabled(
                enabled
        );

        dropdownCloudMonthlyDay.setEnabled(
                enabled
        );

        btnSaveCloudSchedule.setEnabled(
                enabled
        );

        if (!enabled) {
            btnCloudBackupTime.setEnabled(
                    false
            );

            switchCloudWifiOnly.setEnabled(
                    false
            );

            switchCloudChargingOnly.setEnabled(
                    false
            );

            return;
        }

        BackupSchedulePreferences.BackupFrequency frequency =
                frequencyFromDisplayName(
                        dropdownCloudFrequency
                                .getText()
                                .toString()
                );

        updateConditionalGroups(
                frequency
        );
    }

    private void updateConditionalGroups(
            @NonNull BackupSchedulePreferences
                    .BackupFrequency frequency
    ) {
        boolean weekly =
                frequency
                        == BackupSchedulePreferences
                        .BackupFrequency
                        .WEEKLY;

        boolean monthly =
                frequency
                        == BackupSchedulePreferences
                        .BackupFrequency
                        .MONTHLY;

        groupCloudWeeklyDay.setVisibility(
                weekly
                        ? View.VISIBLE
                        : View.GONE
        );

        groupCloudMonthlyDay.setVisibility(
                monthly
                        ? View.VISIBLE
                        : View.GONE
        );

        boolean automatic =
                frequency.isAutomatic();

        btnCloudBackupTime.setEnabled(
                automatic
        );

        switchCloudWifiOnly.setEnabled(
                automatic
        );

        switchCloudChargingOnly.setEnabled(
                automatic
        );
    }

    private void updateTimeButtonText() {
        btnCloudBackupTime.setText(
                "Preferred time: "
                        + formatSelectedTime(
                        selectedHour,
                        selectedMinute
                )
        );
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
    private String formatFileSize(
            long bytes
    ) {
        if (bytes < 0L) {
            return "Unknown size";
        }

        if (bytes < 1024L) {
            return bytes
                    + " Bytes";
        }

        double kilobytes =
                bytes / 1024D;

        if (kilobytes < 1024D) {
            return String.format(
                    Locale.getDefault(),
                    "%.2f KB",
                    kilobytes
            );
        }

        return String.format(
                Locale.getDefault(),
                "%.2f MB",
                kilobytes / 1024D
        );
    }

    private int dp(
            int value
    ) {
        return Math.round(
                value
                        * activity
                        .getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    @NonNull
    private char[] editableToCharacters(
            @Nullable Editable editable
    ) {
        if (editable == null
                || editable.length() == 0) {

            return new char[0];
        }

        char[] result =
                new char[editable.length()];

        for (int index = 0;
             index < editable.length();
             index++) {

            result[index] =
                    editable.charAt(
                            index
                    );
        }

        return result;
    }

    private boolean charactersEqual(
            @NonNull char[] first,
            @NonNull char[] second
    ) {
        if (first.length
                != second.length) {

            return false;
        }

        int difference =
                0;

        for (int index = 0;
             index < first.length;
             index++) {

            difference |=
                    first[index]
                            ^ second[index];
        }

        return difference == 0;
    }

    @NonNull
    private String safeMessage(
            @Nullable Throwable throwable,
            @NonNull String fallback
    ) {
        if (throwable == null) {
            return fallback;
        }

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
                    "Required cloud backup view is missing: "
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