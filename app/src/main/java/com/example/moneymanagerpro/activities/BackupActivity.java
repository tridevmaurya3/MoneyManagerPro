package com.example.moneymanagerpro.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.moneymanagerpro.R;
import com.example.moneymanagerpro.backup.BackupIntegrity;
import com.example.moneymanagerpro.backup.OfflineBackupEngine;
import com.example.moneymanagerpro.backup.OfflineBackupSettingsController;
import com.example.moneymanagerpro.cloud.BackupSchedulePreferences;
import com.example.moneymanagerpro.cloud.CloudBackupSettingsController;
import com.example.moneymanagerpro.database.AppDatabase;
import com.example.moneymanagerpro.database.DatabaseClient;
import com.example.moneymanagerpro.utils.BackupStorageManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Manual offline backup, restore, automatic offline backup settings
 * and encrypted cloud-backup settings.
 *
 * Offline backup creation:
 *
 * - Uses OfflineBackupEngine.
 * - Creates version-5 verified backups.
 * - Includes all Room tables and investments.
 * - Writes to a temporary document first.
 * - Verifies SHA-256 before replacing the latest backup.
 *
 * Restore support:
 *
 * - Version 1 legacy backups
 * - Version 2 full-data backups
 * - Version 3 credit-card backups
 * - Version 4 expense-item backups
 * - Version 5 investment-aware backups
 *
 * Automatic offline backup:
 *
 * - Off
 * - Manual only
 * - Daily
 * - Weekly
 * - Monthly
 * - Preferred time
 * - Charging-only condition
 *
 * Encrypted cloud backup settings:
 *
 * - Firebase account status
 * - Email verification
 * - Secure recovery passphrase
 * - Daily, Weekly and Monthly schedule
 * - Wi-Fi-only and charging-only constraints
 */
public class BackupActivity
        extends AppCompatActivity {

    private static final String TAG =
            "BackupActivity";

    private static final int REQUEST_PICK_BACKUP_FOLDER =
            401;

    private static final int BACKUP_VERSION_LEGACY =
            1;

    private static final int BACKUP_VERSION_FULL_DATA =
            2;

    private static final int BACKUP_VERSION_CREDIT_CARDS =
            3;

    private static final int BACKUP_VERSION_EXPENSE_ITEMS =
            4;

    private static final int BACKUP_VERSION_INVESTMENTS =
            OfflineBackupEngine.BACKUP_VERSION;

    private static final int PENDING_ACTION_NONE =
            0;

    private static final int PENDING_ACTION_CREATE_BACKUP =
            1;

    private static final int PENDING_ACTION_RESTORE_BACKUP =
            2;

    private static final int PENDING_ACTION_CHANGE_FOLDER =
            3;

    private static final String STATE_PENDING_ACTION =
            "backup_pending_action";

    private static final String INVESTMENT_PREFERENCES_NAME =
            "investment_tracker_storage";

    private static final String INVESTMENT_PREFERENCES_KEY =
            "saved_investments";

    private TextView txtBackupStatus;

    private Button btnCreateBackup;

    private Button btnRestoreBackup;

    private Button btnChangeBackupFolder;

    private BackupStorageManager backupStorageManager;

    private OfflineBackupEngine offlineBackupEngine;

    private BackupSchedulePreferences schedulePreferences;

    private OfflineBackupSettingsController
            offlineBackupSettingsController;

    private CloudBackupSettingsController
            cloudBackupSettingsController;

    private int pendingFolderAction =
            PENDING_ACTION_NONE;

    @Override
    protected void onCreate(
            @Nullable Bundle savedInstanceState
    ) {
        super.onCreate(
                savedInstanceState
        );

        setContentView(
                R.layout.activity_backup
        );

        txtBackupStatus =
                findViewById(
                        R.id.txtBackupStatus
                );

        btnCreateBackup =
                findViewById(
                        R.id.btnCreateBackup
                );

        btnRestoreBackup =
                findViewById(
                        R.id.btnRestoreBackup
                );

        btnChangeBackupFolder =
                findViewById(
                        R.id.btnChangeBackupFolder
                );

        backupStorageManager =
                new BackupStorageManager(
                        getApplicationContext()
                );

        offlineBackupEngine =
                new OfflineBackupEngine(
                        getApplicationContext()
                );

        schedulePreferences =
                new BackupSchedulePreferences(
                        getApplicationContext()
                );

        /*
         * Connects the Automatic Offline Backup section with its
         * saved schedule settings and WorkManager scheduler.
         */
        offlineBackupSettingsController =
                new OfflineBackupSettingsController(
                        this
                );

        offlineBackupSettingsController.initialize();

        /*
         * Connects the Encrypted Cloud Backup section with Firebase
         * account status, secure recovery passphrase and cloud schedule.
         *
         * Manual cloud backup, restore and permanent deletion buttons
         * remain disabled until their dedicated verified steps are added.
         */
        cloudBackupSettingsController =
                new CloudBackupSettingsController(
                        this
                );

        cloudBackupSettingsController.initialize();

        if (savedInstanceState != null) {
            pendingFolderAction =
                    savedInstanceState.getInt(
                            STATE_PENDING_ACTION,
                            PENDING_ACTION_NONE
                    );
        }

        btnCreateBackup.setOnClickListener(
                view -> startCreateBackupFlow()
        );

        btnRestoreBackup.setOnClickListener(
                view -> startRestoreBackupFlow()
        );

        btnChangeBackupFolder.setOnClickListener(
                view -> requestBackupFolder(
                        PENDING_ACTION_CHANGE_FOLDER
                )
        );

        updateBackupStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (offlineBackupSettingsController
                != null) {

            offlineBackupSettingsController.refresh();
        }

        if (cloudBackupSettingsController
                != null) {

            cloudBackupSettingsController.refresh();
        }
    }

    @Override
    protected void onSaveInstanceState(
            @NonNull Bundle outState
    ) {
        outState.putInt(
                STATE_PENDING_ACTION,
                pendingFolderAction
        );

        super.onSaveInstanceState(
                outState
        );
    }

    private void startCreateBackupFlow() {
        if (!backupStorageManager
                .hasUsableBackupFolder()) {

            requestBackupFolder(
                    PENDING_ACTION_CREATE_BACKUP
            );

            return;
        }

        createBackup();
    }

    private void startRestoreBackupFlow() {
        if (!backupStorageManager
                .hasUsableBackupFolder()) {

            requestBackupFolder(
                    PENDING_ACTION_RESTORE_BACKUP
            );

            return;
        }

        checkBackupAndConfirmRestore();
    }

    private void requestBackupFolder(
            int requestedAction
    ) {
        if (backupStorageManager.getSavedTreeUri() != null
                && !backupStorageManager
                .isSavedPermissionValid()) {

            backupStorageManager.clearSavedFolder();
        }

        pendingFolderAction =
                requestedAction;

        String title;

        String message;

        if (requestedAction
                == PENDING_ACTION_RESTORE_BACKUP) {

            title =
                    "Backup Folder चुनें";

            message =
                    "Backup restore करने के लिए उस folder को चुनें "
                            + "जहाँ पुराना MoneyManagerPro backup मौजूद है।\n\n"
                            + "सही विकल्प:\n"
                            + "• Documents folder\n"
                            + "या\n"
                            + "• पहले से मौजूद MoneyManagerPro folder\n\n"
                            + "Backup नाम वाला अंदर का subfolder सीधे न चुनें।\n\n"
                            + "यह चयन केवल एक बार करना होगा।";

        } else if (requestedAction
                == PENDING_ACTION_CHANGE_FOLDER) {

            title =
                    "Backup Folder बदलें";

            message =
                    "नया parent folder चुनें। App उसके अंदर "
                            + "MoneyManagerPro/Backup folder खोजेगा या बनाएगा।\n\n"
                            + "पुरानी backup file delete नहीं होगी। "
                            + "केवल app की saved location बदलेगी।";

        } else {
            title =
                    "Backup Folder चुनें";

            message =
                    "पहली बार backup location चुनना आवश्यक है।\n\n"
                            + "Documents folder चुनना सबसे अच्छा रहेगा। "
                            + "App उसके अंदर स्वयं यह folder बनाएगा:\n\n"
                            + "MoneyManagerPro/Backup\n\n"
                            + "यह चयन केवल एक बार करना होगा। "
                            + "इसके बाद backup location दोबारा नहीं पूछी जाएगी।";
        }

        new AlertDialog.Builder(
                this
        )
                .setTitle(
                        title
                )
                .setMessage(
                        message
                )
                .setPositiveButton(
                        "Choose Folder",
                        (dialog, which) ->
                                openBackupFolderPicker()
                )
                .setNegativeButton(
                        "Cancel",
                        (dialog, which) ->
                                pendingFolderAction =
                                        PENDING_ACTION_NONE
                )
                .show();
    }

    private void openBackupFolderPicker() {
        try {
            Intent folderPickerIntent =
                    backupStorageManager
                            .createFolderPickerIntent();

            startActivityForResult(
                    folderPickerIntent,
                    REQUEST_PICK_BACKUP_FOLDER
            );

        } catch (Exception exception) {
            Log.e(
                    TAG,
                    "Unable to open folder picker",
                    exception
            );

            pendingFolderAction =
                    PENDING_ACTION_NONE;

            Toast.makeText(
                    this,
                    "Folder picker नहीं खुल सका",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            @Nullable Intent data
    ) {
        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode
                != REQUEST_PICK_BACKUP_FOLDER) {

            return;
        }

        if (resultCode != RESULT_OK
                || data == null
                || data.getData() == null) {

            pendingFolderAction =
                    PENDING_ACTION_NONE;

            txtBackupStatus.setText(
                    "Backup folder नहीं चुना गया।"
            );

            if (offlineBackupSettingsController
                    != null) {

                offlineBackupSettingsController.refresh();
            }

            return;
        }

        Uri selectedFolderUri =
                data.getData();

        int returnedFlags =
                data.getFlags();

        try {
            backupStorageManager.saveSelectedFolder(
                    selectedFolderUri,
                    returnedFlags
            );

            txtBackupStatus.setText(
                    "Backup folder तैयार है\n"
                            + backupStorageManager
                            .getBackupLocationLabel()
            );

            /*
             * If an automatic offline schedule was already saved,
             * reapply it after receiving permanent folder permission.
             */
            if (offlineBackupSettingsController
                    != null) {

                offlineBackupSettingsController
                        .onBackupFolderChanged();
            }

            int actionToContinue =
                    pendingFolderAction;

            pendingFolderAction =
                    PENDING_ACTION_NONE;

            if (actionToContinue
                    == PENDING_ACTION_CREATE_BACKUP) {

                createBackup();

            } else if (actionToContinue
                    == PENDING_ACTION_RESTORE_BACKUP) {

                checkBackupAndConfirmRestore();

            } else {
                updateBackupStatus();
            }

        } catch (Exception exception) {
            Log.e(
                    TAG,
                    "Unable to save backup folder",
                    exception
            );

            pendingFolderAction =
                    PENDING_ACTION_NONE;

            txtBackupStatus.setText(
                    "चुना गया folder इस्तेमाल नहीं किया जा सका।"
            );

            if (offlineBackupSettingsController
                    != null) {

                offlineBackupSettingsController.refresh();
            }

            new AlertDialog.Builder(
                    this
            )
                    .setTitle(
                            "Folder Access Failed"
                    )
                    .setMessage(
                            "चुने गए folder की permanent read/write "
                                    + "permission save नहीं हो सकी।\n\n"
                                    + "Documents folder चुनकर दोबारा कोशिश करें।"
                    )
                    .setPositiveButton(
                            "OK",
                            null
                    )
                    .show();
        }
    }

    /**
     * Creates the new version-5 offline backup.
     */
    private void createBackup() {
        setBackupButtonsEnabled(
                false
        );

        txtBackupStatus.setText(
                "पूरा offline backup बनाया जा रहा है..."
        );

        new Thread(
                () -> {
                    try {
                        OfflineBackupEngine.BackupResult result =
                                offlineBackupEngine
                                        .createVerifiedBackup();

                        safelyRecordOfflineSuccess(
                                result
                        );

                        String status =
                                "Latest backup सफलतापूर्वक बन गया\n"
                                        + "Backup Version: "
                                        + OfflineBackupEngine.BACKUP_VERSION
                                        + "\n"
                                        + "SHA-256 verification सफल\n"
                                        + "Investments सहित पूरा data सुरक्षित\n"
                                        + result.getCreatedAtText()
                                        + "\n"
                                        + formatFileSize(
                                        result.getBackupByteCount()
                                )
                                        + "\nRecords: "
                                        + result
                                        .getRecordCounts()
                                        .getTotalRecords()
                                        + "\n"
                                        + result
                                        .getBackupLocationLabel();

                        runOnUiThread(
                                () -> {
                                    setBackupButtonsEnabled(
                                            true
                                    );

                                    txtBackupStatus.setText(
                                            status
                                    );

                                    if (offlineBackupSettingsController
                                            != null) {

                                        offlineBackupSettingsController
                                                .refresh();
                                    }

                                    Toast.makeText(
                                            BackupActivity.this,
                                            "नया verified offline backup बन गया",
                                            Toast.LENGTH_LONG
                                    ).show();
                                }
                        );

                    } catch (Exception exception) {
                        Log.e(
                                TAG,
                                "Offline backup creation failed",
                                exception
                        );

                        safelyRecordOfflineFailure(
                                exception
                        );

                        String failedStage =
                                exception
                                        instanceof OfflineBackupEngine
                                        .OfflineBackupException
                                        ? ((OfflineBackupEngine
                                            .OfflineBackupException) exception)
                                        .getStage()
                                        : "Offline backup";

                        String failureReason =
                                getUsefulErrorMessage(
                                        exception
                                );

                        runOnUiThread(
                                () -> {
                                    setBackupButtonsEnabled(
                                            true
                                    );

                                    txtBackupStatus.setText(
                                            "Backup नहीं बन सका\n"
                                                    + "रुका: "
                                                    + failedStage
                                    );

                                    if (offlineBackupSettingsController
                                            != null) {

                                        offlineBackupSettingsController
                                                .refresh();
                                    }

                                    new AlertDialog.Builder(
                                            BackupActivity.this
                                    )
                                            .setTitle(
                                                    "Backup Failed"
                                            )
                                            .setMessage(
                                                    "Backup इस चरण पर रुका:\n"
                                                            + failedStage
                                                            + "\n\nकारण:\n"
                                                            + failureReason
                                                            + "\n\nSaved location हटाई नहीं गई है। "
                                                            + "पहले Retry करें। यदि folder access "
                                                            + "स्थायी रूप से हट गया है, तभी "
                                                            + "Change Folder चुनें।"
                                            )
                                            .setPositiveButton(
                                                    "Retry",
                                                    (dialog, which) ->
                                                            createBackup()
                                            )
                                            .setNegativeButton(
                                                    "Cancel",
                                                    null
                                            )
                                            .show();
                                }
                        );
                    }
                }
        ).start();
    }

    private void checkBackupAndConfirmRestore() {
        setBackupButtonsEnabled(
                false
        );

        txtBackupStatus.setText(
                "Backup की जानकारी जाँची जा रही है..."
        );

        new Thread(
                () -> {
                    try {
                        Uri latestBackupUri =
                                backupStorageManager
                                        .findLatestBackupUri();

                        if (latestBackupUri == null) {
                            runOnUiThread(
                                    () -> {
                                        setBackupButtonsEnabled(
                                                true
                                        );

                                        txtBackupStatus.setText(
                                                "चुने हुए folder में कोई backup नहीं मिला।"
                                        );

                                        new AlertDialog.Builder(
                                                BackupActivity.this
                                        )
                                                .setTitle(
                                                        "No Backup Found"
                                                )
                                                .setMessage(
                                                        "MoneyManagerPro_Latest.mmpbackup "
                                                                + "file नहीं मिली।\n\n"
                                                                + "सही Documents या MoneyManagerPro "
                                                                + "folder चुना गया है या नहीं, जाँच करें।"
                                                )
                                                .setPositiveButton(
                                                        "OK",
                                                        null
                                                )
                                                .show();
                                    }
                            );

                            return;
                        }

                        JSONObject backupRoot =
                                readBackupJson(
                                        latestBackupUri
                                );

                        validateBackupFile(
                                backupRoot,
                                latestBackupUri
                        );

                        BackupSummary backupSummary =
                                createBackupSummary(
                                        backupRoot,
                                        latestBackupUri
                                );

                        runOnUiThread(
                                () -> {
                                    setBackupButtonsEnabled(
                                            true
                                    );

                                    showRestoreConfirmation(
                                            latestBackupUri,
                                            backupSummary
                                    );
                                }
                        );

                    } catch (Exception exception) {
                        Log.e(
                                TAG,
                                "Unable to inspect backup",
                                exception
                        );

                        String reason =
                                getUsefulErrorMessage(
                                        exception
                                );

                        runOnUiThread(
                                () -> {
                                    setBackupButtonsEnabled(
                                            true
                                    );

                                    txtBackupStatus.setText(
                                            "Backup file मौजूद है, लेकिन पढ़ी नहीं जा सकी।"
                                    );

                                    new AlertDialog.Builder(
                                            BackupActivity.this
                                    )
                                            .setTitle(
                                                    "Invalid Backup"
                                            )
                                            .setMessage(
                                                    "Backup file खराब है या यह सही "
                                                            + "Money Manager Pro backup नहीं है।\n\n"
                                                            + "कारण:\n"
                                                            + reason
                                            )
                                            .setPositiveButton(
                                                    "OK",
                                                    null
                                            )
                                            .show();
                                }
                        );
                    }
                }
        ).start();
    }

    private void showRestoreConfirmation(
            @NonNull Uri backupUri,
            @NonNull BackupSummary summary
    ) {
        StringBuilder message =
                new StringBuilder();

        message.append(
                "Backup मौजूद है\n\n"
        );

        message.append(
                "दिनांक एवं समय:\n"
        );

        message.append(
                summary.createdAt
        );

        message.append(
                "\n\nFile Size: "
        );

        message.append(
                summary.fileSize
        );

        message.append(
                "\nBackup Version: "
        );

        message.append(
                summary.backupVersion
        );

        message.append(
                "\nApp Version: "
        );

        message.append(
                summary.appVersion
        );

        message.append(
                "\n\nTransactions: "
        );

        message.append(
                summary.transactionCount
        );

        message.append(
                "\nExpense Item Details: "
        );

        message.append(
                summary.expenseItemCount
        );

        message.append(
                "\nAccounts: "
        );

        message.append(
                summary.accountCount
        );

        message.append(
                "\nCategories: "
        );

        message.append(
                summary.categoryCount
        );

        message.append(
                "\nBudgets: "
        );

        message.append(
                summary.budgetCount
        );

        message.append(
                "\nGoals: "
        );

        message.append(
                summary.goalCount
        );

        message.append(
                "\nLoans: "
        );

        message.append(
                summary.loanCount
        );

        message.append(
                "\nLoan Payments: "
        );

        message.append(
                summary.loanPaymentCount
        );

        message.append(
                "\nSubscriptions: "
        );

        message.append(
                summary.subscriptionCount
        );

        message.append(
                "\nCredit Cards: "
        );

        message.append(
                summary.creditCardCount
        );

        message.append(
                "\nCard Payments: "
        );

        message.append(
                summary.creditCardPaymentCount
        );

        message.append(
                "\nRecurring Transactions: "
        );

        message.append(
                summary.recurringCount
        );

        if (summary.backupVersion
                >= BACKUP_VERSION_INVESTMENTS) {

            message.append(
                    "\nInvestments: "
            );

            message.append(
                    summary.investmentCount
            );
        }

        message.append(
                "\n\nIntegrity: "
        );

        message.append(
                summary.integrityStatus
        );

        message.append(
                "\n\nRestore करने पर वर्तमान app data हट जाएगा "
                        + "और इस backup का data वापस आ जाएगा।"
        );

        if (summary.backupVersion
                < BACKUP_VERSION_INVESTMENTS) {

            message.append(
                    "\n\nयह पुराना backup है। इसमें investments शामिल "
                            + "नहीं हैं, इसलिए वर्तमान investments बदले नहीं जाएँगे।"
            );
        }

        new AlertDialog.Builder(
                this
        )
                .setTitle(
                        "Restore Backup"
                )
                .setMessage(
                        message.toString()
                )
                .setPositiveButton(
                        "OK, Restore",
                        (dialog, which) ->
                                restoreBackup(
                                        backupUri
                                )
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .show();
    }

    private void restoreBackup(
            @NonNull Uri backupUri
    ) {
        setBackupButtonsEnabled(
                false
        );

        txtBackupStatus.setText(
                "Backup restore किया जा रहा है..."
        );

        new Thread(
                () -> {
                    try {
                        JSONObject root =
                                readBackupJson(
                                        backupUri
                                );

                        validateBackupFile(
                                root,
                                backupUri
                        );

                        RestorePayload restorePayload =
                                createRestorePayload(
                                        root
                                );

                        restorePayload(
                                restorePayload
                        );

                        String restoredDate =
                                getBackupDateTime(
                                        root,
                                        backupUri
                                );

                        runOnUiThread(
                                () -> {
                                    setBackupButtonsEnabled(
                                            true
                                    );

                                    txtBackupStatus.setText(
                                            "Backup सफलतापूर्वक restore हो गया\n"
                                                    + restoredDate
                                    );

                                    if (offlineBackupSettingsController
                                            != null) {

                                        offlineBackupSettingsController
                                                .refresh();
                                    }

                                    new AlertDialog.Builder(
                                            BackupActivity.this
                                    )
                                            .setTitle(
                                                    "Restore Complete"
                                            )
                                            .setMessage(
                                                    "Backup का data सफलतापूर्वक restore हो गया है।\n\n"
                                                            + "Restore verification भी सफल रही।\n\n"
                                                            + (
                                                            restorePayload.backupVersion
                                                                    >= BACKUP_VERSION_INVESTMENTS
                                                                    ? "Investments भी restore हो गए हैं।\n\n"
                                                                    : ""
                                                    )
                                                            + "Dashboard पर वापस जाने के बाद "
                                                            + "restore किया गया data दिखाई देगा।"
                                            )
                                            .setPositiveButton(
                                                    "OK",
                                                    (dialog, which) ->
                                                            finish()
                                            )
                                            .setCancelable(
                                                    false
                                            )
                                            .show();
                                }
                        );

                    } catch (Exception exception) {
                        Log.e(
                                TAG,
                                "Backup restore failed",
                                exception
                        );

                        String reason =
                                getUsefulErrorMessage(
                                        exception
                                );

                        runOnUiThread(
                                () -> {
                                    setBackupButtonsEnabled(
                                            true
                                    );

                                    txtBackupStatus.setText(
                                            "Restore नहीं हो सका।"
                                    );

                                    if (offlineBackupSettingsController
                                            != null) {

                                        offlineBackupSettingsController
                                                .refresh();
                                    }

                                    new AlertDialog.Builder(
                                            BackupActivity.this
                                    )
                                            .setTitle(
                                                    "Restore Failed"
                                            )
                                            .setMessage(
                                                    "Backup restore नहीं हो सका।\n\n"
                                                            + "कारण:\n"
                                                            + reason
                                                            + "\n\nRoom transaction fail होने पर "
                                                            + "पुराना database data वापस सुरक्षित रखा गया है।"
                                            )
                                            .setPositiveButton(
                                                    "OK",
                                                    null
                                            )
                                            .show();
                                }
                        );
                    }
                }
        ).start();
    }

    private void restorePayload(
            @NonNull RestorePayload payload
    ) throws Exception {

        AppDatabase database =
                DatabaseClient
                        .getInstance(
                                getApplicationContext()
                        )
                        .getAppDatabase();

        SharedPreferences investmentPreferences =
                getSharedPreferences(
                        INVESTMENT_PREFERENCES_NAME,
                        Context.MODE_PRIVATE
                );

        String previousInvestments =
                investmentPreferences.getString(
                        INVESTMENT_PREFERENCES_KEY,
                        "[]"
                );

        if (previousInvestments == null) {
            previousInvestments =
                    "[]";
        }

        boolean investmentDataChanged =
                payload.backupVersion
                        >= BACKUP_VERSION_INVESTMENTS;

        if (investmentDataChanged) {
            boolean saved =
                    investmentPreferences
                            .edit()
                            .putString(
                                    INVESTMENT_PREFERENCES_KEY,
                                    payload.investments.toString()
                            )
                            .commit();

            if (!saved) {
                throw new IllegalStateException(
                        "Investment data could not be prepared for restore."
                );
            }

            String storedInvestments =
                    investmentPreferences.getString(
                            INVESTMENT_PREFERENCES_KEY,
                            "[]"
                    );

            JSONArray storedArray =
                    new JSONArray(
                            storedInvestments == null
                                    ? "[]"
                                    : storedInvestments
                    );

            if (storedArray.length()
                    != payload.investments.length()) {

                investmentPreferences
                        .edit()
                        .putString(
                                INVESTMENT_PREFERENCES_KEY,
                                previousInvestments
                        )
                        .commit();

                throw new IllegalStateException(
                        "Investment restore verification failed."
                );
            }
        }

        String previousInvestmentValue =
                previousInvestments;

        try {
            database.runInTransaction(
                    () -> {
                        SupportSQLiteDatabase sqlDatabase =
                                database
                                        .getOpenHelper()
                                        .getWritableDatabase();

                        clearFinanceTables(
                                sqlDatabase
                        );

                        insertRows(
                                sqlDatabase,
                                "INSERT INTO `categories` "
                                        + "(`id`,`name`,`type`,`color`) "
                                        + "VALUES (?,?,?,?)",
                                payload.categories,
                                (object, index) ->
                                        new Object[]{
                                                object.optInt(
                                                        "id",
                                                        0
                                                ),
                                                text(
                                                        object,
                                                        "name",
                                                        ""
                                                ),
                                                text(
                                                        object,
                                                        "type",
                                                        ""
                                                ),
                                                text(
                                                        object,
                                                        "color",
                                                        "#1565C0"
                                                )
                                        }
                        );

                        if (payload.accounts.length() == 0) {
                            sqlDatabase.execSQL(
                                    "INSERT INTO `accounts` "
                                            + "(`name`,`type`,`openingBalance`,`color`) "
                                            + "VALUES (?,?,?,?)",
                                    new Object[]{
                                            "Cash",
                                            "Cash",
                                            0D,
                                            "#2E7D32"
                                    }
                            );

                        } else {
                            insertRows(
                                    sqlDatabase,
                                    "INSERT INTO `accounts` "
                                            + "(`id`,`name`,`type`,`openingBalance`,`color`) "
                                            + "VALUES (?,?,?,?,?)",
                                    payload.accounts,
                                    (object, index) ->
                                            new Object[]{
                                                    object.optInt(
                                                            "id",
                                                            0
                                                    ),
                                                    text(
                                                            object,
                                                            "name",
                                                            "Cash"
                                                    ),
                                                    text(
                                                            object,
                                                            "type",
                                                            "Cash"
                                                    ),
                                                    object.optDouble(
                                                            "openingBalance",
                                                            0D
                                                    ),
                                                    text(
                                                            object,
                                                            "color",
                                                            "#2E7D32"
                                                    )
                                            }
                            );
                        }

                        insertRows(
                                sqlDatabase,
                                "INSERT INTO `goals` "
                                        + "(`id`,`name`,`targetAmount`,`savedAmount`,"
                                        + "`targetDate`,`color`) "
                                        + "VALUES (?,?,?,?,?,?)",
                                payload.goals,
                                (object, index) ->
                                        new Object[]{
                                                object.optInt(
                                                        "id",
                                                        0
                                                ),
                                                text(
                                                        object,
                                                        "name",
                                                        ""
                                                ),
                                                object.optDouble(
                                                        "targetAmount",
                                                        0D
                                                ),
                                                object.optDouble(
                                                        "savedAmount",
                                                        0D
                                                ),
                                                text(
                                                        object,
                                                        "targetDate",
                                                        ""
                                                ),
                                                text(
                                                        object,
                                                        "color",
                                                        "#6C63FF"
                                                )
                                        }
                        );

                        insertRows(
                                sqlDatabase,
                                "INSERT INTO `recurring_transactions` "
                                        + "(`id`,`type`,`amount`,`category`,`account`,"
                                        + "`note`,`frequency`,`startDate`,`nextRunDate`,`active`) "
                                        + "VALUES (?,?,?,?,?,?,?,?,?,?)",
                                payload.recurringTransactions,
                                (object, index) ->
                                        new Object[]{
                                                object.optInt(
                                                        "id",
                                                        0
                                                ),
                                                text(
                                                        object,
                                                        "type",
                                                        ""
                                                ),
                                                object.optDouble(
                                                        "amount",
                                                        0D
                                                ),
                                                text(
                                                        object,
                                                        "category",
                                                        ""
                                                ),
                                                text(
                                                        object,
                                                        "account",
                                                        "Cash"
                                                ),
                                                text(
                                                        object,
                                                        "note",
                                                        ""
                                                ),
                                                text(
                                                        object,
                                                        "frequency",
                                                        "Monthly"
                                                ),
                                                text(
                                                        object,
                                                        "startDate",
                                                        ""
                                                ),
                                                text(
                                                        object,
                                                        "nextRunDate",
                                                        ""
                                                ),
                                                booleanInteger(
                                                        object,
                                                        "active",
                                                        true
                                                )
                                        }
                        );

                        insertRows(
                                sqlDatabase,
                                "INSERT INTO `category_budgets` "
                                        + "(`id`,`category`,`period`,`limitAmount`) "
                                        + "VALUES (?,?,?,?)",
                                payload.budgets,
                                (object, index) ->
                                        new Object[]{
                                                object.optInt(
                                                        "id",
                                                        0
                                                ),
                                                text(
                                                        object,
                                                        "category",
                                                        ""
                                                ),
                                                text(
                                                        object,
                                                        "period",
                                                        "Monthly"
                                                ),
                                                object.optDouble(
                                                        "limitAmount",
                                                        0D
                                                )
                                        }
                        );

                        insertRows(
                                sqlDatabase,
                                "INSERT INTO `loans` "
                                        + "(`id`,`personName`,`loanType`,`totalAmount`,"
                                        + "`outstandingAmount`,`interestRate`,`emiAmount`,"
                                        + "`dueDate`,`note`,`active`,`startDate`,"
                                        + "`tenureMonths`,`historicalPaidAmount`,"
                                        + "`historicalInstallments`) "
                                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                                payload.loans,
                                (object, index) ->
                                        new Object[]{
                                                object.optInt(
                                                        "id",
                                                        0
                                                ),
                                                text(
                                                        object,
                                                        "personName",
                                                        ""
                                                ),
                                                text(
                                                        object,
                                                        "loanType",
                                                        "Loan Taken"
                                                ),
                                                object.optDouble(
                                                        "totalAmount",
                                                        0D
                                                ),
                                                object.optDouble(
                                                        "outstandingAmount",
                                                        0D
                                                ),
                                                object.optDouble(
                                                        "interestRate",
                                                        0D
                                                ),
                                                object.optDouble(
                                                        "emiAmount",
                                                        0D
                                                ),
                                                text(
                                                        object,
                                                        "dueDate",
                                                        ""
                                                ),
                                                text(
                                                        object,
                                                        "note",
                                                        ""
                                                ),
                                                booleanInteger(
                                                        object,
                                                        "active",
                                                        true
                                                ),
                                                text(
                                                        object,
                                                        "startDate",
                                                        ""
                                                ),
                                                object.optInt(
                                                        "tenureMonths",
                                                        0
                                                ),
                                                object.optDouble(
                                                        "historicalPaidAmount",
                                                        0D
                                                ),
                                                object.optInt(
                                                        "historicalInstallments",
                                                        0
                                                )
                                        }
                        );

                        insertRows(
                                sqlDatabase,
                                "INSERT INTO `subscriptions` "
                                        + "(`id`,`name`,`amount`,`billingCycle`,"
                                        + "`nextDueDate`,`account`,`category`,"
                                        + "`remindDays`,`note`,`active`) "
                                        + "VALUES (?,?,?,?,?,?,?,?,?,?)",
                                payload.subscriptions,
                                (object, index) ->
                                        new Object[]{
                                                object.optInt(
                                                        "id",
                                                        0
                                                ),
                                                text(
                                                        object,
                                                        "name",
                                                        ""
                                                ),
                                                object.optDouble(
                                                        "amount",
                                                        0D
                                                ),
                                                text(
                                                        object,
                                                        "billingCycle",
                                                        "Monthly"
                                                ),
                                                text(
                                                        object,
                                                        "nextDueDate",
                                                        ""
                                                ),
                                                text(
                                                        object,
                                                        "account",
                                                        "Cash"
                                                ),
                                                text(
                                                        object,
                                                        "category",
                                                        ""
                                                ),
                                                object.optInt(
                                                        "remindDays",
                                                        0
                                                ),
                                                text(
                                                        object,
                                                        "note",
                                                        ""
                                                ),
                                                booleanInteger(
                                                        object,
                                                        "active",
                                                        true
                                                )
                                        }
                        );

                        insertRows(
                                sqlDatabase,
                                "INSERT INTO `credit_cards` "
                                        + "(`id`,`name`,`lastFour`,`accountName`,"
                                        + "`creditLimit`,`billingDay`,`dueDay`,"
                                        + "`paymentAccount`,`reminderDays`,`active`) "
                                        + "VALUES (?,?,?,?,?,?,?,?,?,?)",
                                payload.creditCards,
                                (object, index) ->
                                        new Object[]{
                                                object.optInt(
                                                        "id",
                                                        0
                                                ),
                                                text(
                                                        object,
                                                        "name",
                                                        ""
                                                ),
                                                text(
                                                        object,
                                                        "lastFour",
                                                        ""
                                                ),
                                                text(
                                                        object,
                                                        "accountName",
                                                        ""
                                                ),
                                                object.optDouble(
                                                        "creditLimit",
                                                        0D
                                                ),
                                                object.optInt(
                                                        "billingDay",
                                                        1
                                                ),
                                                object.optInt(
                                                        "dueDay",
                                                        1
                                                ),
                                                text(
                                                        object,
                                                        "paymentAccount",
                                                        "Cash"
                                                ),
                                                object.optInt(
                                                        "reminderDays",
                                                        3
                                                ),
                                                booleanInteger(
                                                        object,
                                                        "active",
                                                        true
                                                )
                                        }
                        );

                        insertRows(
                                sqlDatabase,
                                "INSERT INTO `transactions` "
                                        + "(`id`,`type`,`amount`,`category`,"
                                        + "`account`,`note`,`date`) "
                                        + "VALUES (?,?,?,?,?,?,?)",
                                payload.transactions,
                                (object, index) ->
                                        new Object[]{
                                                object.optInt(
                                                        "id",
                                                        0
                                                ),
                                                text(
                                                        object,
                                                        "type",
                                                        ""
                                                ),
                                                object.optDouble(
                                                        "amount",
                                                        0D
                                                ),
                                                text(
                                                        object,
                                                        "category",
                                                        ""
                                                ),
                                                text(
                                                        object,
                                                        "account",
                                                        "Cash"
                                                ),
                                                text(
                                                        object,
                                                        "note",
                                                        ""
                                                ),
                                                text(
                                                        object,
                                                        "date",
                                                        ""
                                                )
                                        }
                        );

                        insertRows(
                                sqlDatabase,
                                "INSERT INTO `loan_payments` "
                                        + "(`id`,`loanId`,`amount`,`paymentType`,"
                                        + "`account`,`paymentDate`,`note`) "
                                        + "VALUES (?,?,?,?,?,?,?)",
                                payload.loanPayments,
                                (object, index) ->
                                        new Object[]{
                                                object.optInt(
                                                        "id",
                                                        0
                                                ),
                                                object.optInt(
                                                        "loanId",
                                                        0
                                                ),
                                                object.optDouble(
                                                        "amount",
                                                        0D
                                                ),
                                                text(
                                                        object,
                                                        "paymentType",
                                                        "EMI"
                                                ),
                                                text(
                                                        object,
                                                        "account",
                                                        "Cash"
                                                ),
                                                text(
                                                        object,
                                                        "paymentDate",
                                                        ""
                                                ),
                                                text(
                                                        object,
                                                        "note",
                                                        ""
                                                )
                                        }
                        );

                        insertRows(
                                sqlDatabase,
                                "INSERT INTO `credit_card_payments` "
                                        + "(`id`,`creditCardId`,`statementEndDate`,"
                                        + "`amount`,`paymentDate`,`sourceAccount`,`note`) "
                                        + "VALUES (?,?,?,?,?,?,?)",
                                payload.creditCardPayments,
                                (object, index) ->
                                        new Object[]{
                                                object.optInt(
                                                        "id",
                                                        0
                                                ),
                                                object.optInt(
                                                        "creditCardId",
                                                        0
                                                ),
                                                text(
                                                        object,
                                                        "statementEndDate",
                                                        ""
                                                ),
                                                object.optDouble(
                                                        "amount",
                                                        0D
                                                ),
                                                text(
                                                        object,
                                                        "paymentDate",
                                                        ""
                                                ),
                                                text(
                                                        object,
                                                        "sourceAccount",
                                                        "Cash"
                                                ),
                                                text(
                                                        object,
                                                        "note",
                                                        ""
                                                )
                                        }
                        );

                        insertRows(
                                sqlDatabase,
                                "INSERT INTO `expense_items` "
                                        + "(`id`,`transactionId`,`itemName`,"
                                        + "`quantity`,`unit`,`price`,`total`,`sortOrder`) "
                                        + "VALUES (?,?,?,?,?,?,?,?)",
                                payload.expenseItems,
                                (object, index) ->
                                        new Object[]{
                                                object.optInt(
                                                        "id",
                                                        0
                                                ),
                                                object.optInt(
                                                        "transactionId",
                                                        0
                                                ),
                                                text(
                                                        object,
                                                        "itemName",
                                                        ""
                                                ),
                                                object.optDouble(
                                                        "quantity",
                                                        0D
                                                ),
                                                text(
                                                        object,
                                                        "unit",
                                                        ""
                                                ),
                                                object.optDouble(
                                                        "price",
                                                        0D
                                                ),
                                                object.optDouble(
                                                        "total",
                                                        0D
                                                ),
                                                object.optInt(
                                                        "sortOrder",
                                                        index
                                                )
                                        }
                        );

                        verifyRestoredTableCounts(
                                sqlDatabase,
                                payload
                        );
                    }
            );

        } catch (Exception databaseException) {
            if (investmentDataChanged) {
                boolean rollbackSucceeded =
                        investmentPreferences
                                .edit()
                                .putString(
                                        INVESTMENT_PREFERENCES_KEY,
                                        previousInvestmentValue
                                )
                                .commit();

                if (!rollbackSucceeded) {
                    databaseException.addSuppressed(
                            new IllegalStateException(
                                    "Investment rollback could not be completed."
                            )
                    );
                }
            }

            throw databaseException;
        }
    }

    private void clearFinanceTables(
            @NonNull SupportSQLiteDatabase database
    ) {
        database.execSQL(
                "DELETE FROM `expense_items`"
        );

        database.execSQL(
                "DELETE FROM `credit_card_payments`"
        );

        database.execSQL(
                "DELETE FROM `loan_payments`"
        );

        database.execSQL(
                "DELETE FROM `transactions`"
        );

        database.execSQL(
                "DELETE FROM `credit_cards`"
        );

        database.execSQL(
                "DELETE FROM `loans`"
        );

        database.execSQL(
                "DELETE FROM `subscriptions`"
        );

        database.execSQL(
                "DELETE FROM `category_budgets`"
        );

        database.execSQL(
                "DELETE FROM `recurring_transactions`"
        );

        database.execSQL(
                "DELETE FROM `goals`"
        );

        database.execSQL(
                "DELETE FROM `accounts`"
        );

        database.execSQL(
                "DELETE FROM `categories`"
        );

        database.execSQL(
                "DELETE FROM `sqlite_sequence` "
                        + "WHERE `name` IN ("
                        + "'transactions',"
                        + "'expense_items',"
                        + "'credit_card_payments',"
                        + "'loan_payments',"
                        + "'credit_cards',"
                        + "'loans',"
                        + "'subscriptions',"
                        + "'category_budgets',"
                        + "'recurring_transactions',"
                        + "'goals',"
                        + "'accounts',"
                        + "'categories'"
                        + ")"
        );
    }

    private void insertRows(
            @NonNull SupportSQLiteDatabase database,
            @NonNull String sql,
            @NonNull JSONArray array,
            @NonNull RowArgumentsFactory factory
    ) {
        for (int index = 0;
             index < array.length();
             index++) {

            JSONObject object =
                    array.optJSONObject(
                            index
                    );

            if (object == null) {
                throw new IllegalStateException(
                        "Invalid restore record at position "
                                + index
                );
            }

            database.execSQL(
                    sql,
                    factory.createArguments(
                            object,
                            index
                    )
            );
        }
    }

    private void verifyRestoredTableCounts(
            @NonNull SupportSQLiteDatabase database,
            @NonNull RestorePayload payload
    ) {
        verifyTableCount(
                database,
                "transactions",
                payload.transactions.length()
        );

        verifyTableCount(
                database,
                "expense_items",
                payload.expenseItems.length()
        );

        verifyTableCount(
                database,
                "categories",
                payload.categories.length()
        );

        verifyTableCount(
                database,
                "accounts",
                payload.accounts.length() == 0
                        ? 1
                        : payload.accounts.length()
        );

        verifyTableCount(
                database,
                "goals",
                payload.goals.length()
        );

        verifyTableCount(
                database,
                "recurring_transactions",
                payload.recurringTransactions.length()
        );

        verifyTableCount(
                database,
                "category_budgets",
                payload.budgets.length()
        );

        verifyTableCount(
                database,
                "loans",
                payload.loans.length()
        );

        verifyTableCount(
                database,
                "loan_payments",
                payload.loanPayments.length()
        );

        verifyTableCount(
                database,
                "subscriptions",
                payload.subscriptions.length()
        );

        verifyTableCount(
                database,
                "credit_cards",
                payload.creditCards.length()
        );

        verifyTableCount(
                database,
                "credit_card_payments",
                payload.creditCardPayments.length()
        );
    }

    private void verifyTableCount(
            @NonNull SupportSQLiteDatabase database,
            @NonNull String table,
            int expectedCount
    ) {
        int actualCount =
                queryTableCount(
                        database,
                        table
                );

        if (actualCount != expectedCount) {
            throw new IllegalStateException(
                    "Restore verification failed for "
                            + table
                            + ". Expected "
                            + expectedCount
                            + " but found "
                            + actualCount
                            + "."
            );
        }
    }

    private int queryTableCount(
            @NonNull SupportSQLiteDatabase database,
            @NonNull String table
    ) {
        try (
                Cursor cursor =
                        database.query(
                                "SELECT COUNT(*) FROM `"
                                        + table
                                        + "`"
                        )
        ) {
            if (!cursor.moveToFirst()) {
                return 0;
            }

            return cursor.getInt(
                    0
            );
        }
    }

    @NonNull
    private RestorePayload createRestorePayload(
            @NonNull JSONObject root
    ) {
        RestorePayload payload =
                new RestorePayload();

        payload.backupVersion =
                root.optInt(
                        "backupVersion",
                        BACKUP_VERSION_LEGACY
                );

        payload.transactions =
                arrayOrEmpty(
                        root,
                        "transactions"
                );

        payload.expenseItems =
                arrayOrEmpty(
                        root,
                        "expenseItems"
                );

        payload.categories =
                arrayOrEmpty(
                        root,
                        "categories"
                );

        payload.accounts =
                arrayOrEmpty(
                        root,
                        "accounts"
                );

        payload.goals =
                arrayOrEmpty(
                        root,
                        "goals"
                );

        payload.recurringTransactions =
                arrayOrEmpty(
                        root,
                        "recurringTransactions"
                );

        payload.budgets =
                arrayOrEmpty(
                        root,
                        "budgets"
                );

        payload.loans =
                arrayOrEmpty(
                        root,
                        "loans"
                );

        payload.loanPayments =
                arrayOrEmpty(
                        root,
                        "loanPayments"
                );

        payload.subscriptions =
                arrayOrEmpty(
                        root,
                        "subscriptions"
                );

        payload.creditCards =
                arrayOrEmpty(
                        root,
                        "creditCards"
                );

        payload.creditCardPayments =
                arrayOrEmpty(
                        root,
                        "creditCardPayments"
                );

        payload.investments =
                arrayOrEmpty(
                        root,
                        "investments"
                );

        return payload;
    }

    private void validateBackupFile(
            @NonNull JSONObject root,
            @NonNull Uri backupUri
    ) throws Exception {

        String appName =
                root.optString(
                        "appName",
                        ""
                );

        if (!OfflineBackupEngine.APP_NAME.equals(
                appName
        )) {
            throw new IllegalStateException(
                    "Invalid backup app name."
            );
        }

        int backupVersion =
                root.optInt(
                        "backupVersion",
                        0
                );

        if (backupVersion
                == BACKUP_VERSION_INVESTMENTS) {

            offlineBackupEngine
                    .inspectAndValidateBackup(
                            backupUri
                    );

            return;
        }

        if (backupVersion
                == BACKUP_VERSION_LEGACY) {

            validateLegacyBackup(
                    root
            );

            return;
        }

        if (backupVersion
                == BACKUP_VERSION_FULL_DATA
                || backupVersion
                == BACKUP_VERSION_CREDIT_CARDS
                || backupVersion
                == BACKUP_VERSION_EXPENSE_ITEMS) {

            validateVersionTwoToFourBackup(
                    root,
                    backupVersion
            );

            return;
        }

        throw new IllegalStateException(
                "Unsupported backup version."
        );
    }

    private void validateLegacyBackup(
            @NonNull JSONObject root
    ) throws Exception {

        String[] requiredArrays = {
                "transactions",
                "categories",
                "accounts",
                "goals",
                "recurringTransactions",
                "budgets",
                "loans"
        };

        verifyRequiredArrays(
                root,
                requiredArrays
        );

        JSONObject recordCounts =
                root.optJSONObject(
                        "recordCounts"
                );

        if (recordCounts != null) {
            verifyDeclaredCounts(
                    root,
                    recordCounts,
                    requiredArrays
            );
        }

        validateUniquePositiveIds(
                root,
                requiredArrays
        );
    }

    private void validateVersionTwoToFourBackup(
            @NonNull JSONObject root,
            int backupVersion
    ) throws Exception {

        int databaseVersion =
                root.optInt(
                        "databaseVersion",
                        0
                );

        int currentDatabaseVersion =
                DatabaseClient
                        .getInstance(
                                getApplicationContext()
                        )
                        .getAppDatabase()
                        .getOpenHelper()
                        .getReadableDatabase()
                        .getVersion();

        if (databaseVersion <= 0
                || databaseVersion > currentDatabaseVersion) {

            throw new IllegalStateException(
                    "Unsupported backup database version."
            );
        }

        List<String> requiredArrayList =
                new ArrayList<>();

        requiredArrayList.add(
                "transactions"
        );

        requiredArrayList.add(
                "categories"
        );

        requiredArrayList.add(
                "accounts"
        );

        requiredArrayList.add(
                "goals"
        );

        requiredArrayList.add(
                "recurringTransactions"
        );

        requiredArrayList.add(
                "budgets"
        );

        requiredArrayList.add(
                "loans"
        );

        requiredArrayList.add(
                "loanPayments"
        );

        requiredArrayList.add(
                "subscriptions"
        );

        if (backupVersion
                >= BACKUP_VERSION_CREDIT_CARDS) {

            requiredArrayList.add(
                    "creditCards"
            );

            requiredArrayList.add(
                    "creditCardPayments"
            );
        }

        if (backupVersion
                >= BACKUP_VERSION_EXPENSE_ITEMS) {

            requiredArrayList.add(
                    "expenseItems"
            );
        }

        String[] requiredArrays =
                requiredArrayList.toArray(
                        new String[0]
                );

        verifyRequiredArrays(
                root,
                requiredArrays
        );

        JSONObject recordCounts =
                root.optJSONObject(
                        "recordCounts"
                );

        if (recordCounts == null) {
            throw new IllegalStateException(
                    "Backup record counts are missing."
            );
        }

        verifyDeclaredCounts(
                root,
                recordCounts,
                requiredArrays
        );

        validateUniquePositiveIds(
                root,
                requiredArrays
        );

        validateLoanPaymentReferences(
                root
        );

        if (backupVersion
                >= BACKUP_VERSION_CREDIT_CARDS) {

            validateCreditCardAccounts(
                    root
            );

            validateCreditCardPaymentReferences(
                    root
            );
        }

        if (backupVersion
                >= BACKUP_VERSION_EXPENSE_ITEMS) {

            validateExpenseItemReferences(
                    root
            );
        }

        String storedChecksum =
                root.optString(
                                "integritySha256",
                                ""
                        )
                        .trim()
                        .toLowerCase(
                                Locale.US
                        );

        if (!storedChecksum.matches(
                "[0-9a-f]{64}"
        )) {
            throw new IllegalStateException(
                    "Backup checksum is invalid."
            );
        }

        if (!BackupIntegrity.verify(
                root,
                storedChecksum
        )) {
            throw new IllegalStateException(
                    "Backup integrity verification failed."
            );
        }
    }

    private void verifyRequiredArrays(
            @NonNull JSONObject root,
            @NonNull String[] requiredArrays
    ) {
        for (String arrayName :
                requiredArrays) {

            if (root.optJSONArray(
                    arrayName
            ) == null) {

                throw new IllegalStateException(
                        "Backup section is missing: "
                                + arrayName
                );
            }
        }
    }

    private void verifyDeclaredCounts(
            @NonNull JSONObject root,
            @NonNull JSONObject recordCounts,
            @NonNull String[] requiredArrays
    ) throws Exception {

        for (String arrayName :
                requiredArrays) {

            int expectedCount =
                    recordCounts.optInt(
                            arrayName,
                            -1
                    );

            int actualCount =
                    root.getJSONArray(
                            arrayName
                    ).length();

            if (expectedCount != actualCount) {
                throw new IllegalStateException(
                        "Backup record count mismatch: "
                                + arrayName
                );
            }
        }
    }

    private void validateUniquePositiveIds(
            @NonNull JSONObject root,
            @NonNull String[] arrayNames
    ) throws Exception {

        for (String arrayName :
                arrayNames) {

            JSONArray array =
                    root.getJSONArray(
                            arrayName
                    );

            Set<Integer> ids =
                    new HashSet<>();

            for (int index = 0;
                 index < array.length();
                 index++) {

                JSONObject item =
                        array.optJSONObject(
                                index
                        );

                if (item == null) {
                    throw new IllegalStateException(
                            "Invalid record in "
                                    + arrayName
                    );
                }

                int id =
                        item.optInt(
                                "id",
                                0
                        );

                if (id <= 0
                        || !ids.add(
                        id
                )) {

                    throw new IllegalStateException(
                            "Invalid or duplicate ID in "
                                    + arrayName
                    );
                }
            }
        }
    }

    private void validateLoanPaymentReferences(
            @NonNull JSONObject root
    ) throws Exception {

        Set<Integer> loanIds =
                collectIds(
                        root.getJSONArray(
                                "loans"
                        )
                );

        JSONArray payments =
                root.getJSONArray(
                        "loanPayments"
                );

        for (int index = 0;
             index < payments.length();
             index++) {

            int loanId =
                    payments
                            .getJSONObject(
                                    index
                            )
                            .optInt(
                                    "loanId",
                                    0
                            );

            if (!loanIds.contains(
                    loanId
            )) {
                throw new IllegalStateException(
                        "Loan payment references a missing loan."
                );
            }
        }
    }

    private void validateExpenseItemReferences(
            @NonNull JSONObject root
    ) throws Exception {

        Set<Integer> transactionIds =
                collectIds(
                        root.getJSONArray(
                                "transactions"
                        )
                );

        JSONArray expenseItems =
                root.getJSONArray(
                        "expenseItems"
                );

        for (int index = 0;
             index < expenseItems.length();
             index++) {

            int transactionId =
                    expenseItems
                            .getJSONObject(
                                    index
                            )
                            .optInt(
                                    "transactionId",
                                    0
                            );

            if (!transactionIds.contains(
                    transactionId
            )) {
                throw new IllegalStateException(
                        "Expense item references a missing transaction."
                );
            }
        }
    }

    private void validateCreditCardPaymentReferences(
            @NonNull JSONObject root
    ) throws Exception {

        Set<Integer> cardIds =
                collectIds(
                        root.getJSONArray(
                                "creditCards"
                        )
                );

        JSONArray payments =
                root.getJSONArray(
                        "creditCardPayments"
                );

        for (int index = 0;
             index < payments.length();
             index++) {

            int creditCardId =
                    payments
                            .getJSONObject(
                                    index
                            )
                            .optInt(
                                    "creditCardId",
                                    0
                            );

            if (!cardIds.contains(
                    creditCardId
            )) {
                throw new IllegalStateException(
                        "Card payment references a missing credit card."
                );
            }
        }
    }

    private void validateCreditCardAccounts(
            @NonNull JSONObject root
    ) throws Exception {

        Set<String> accountNames =
                new HashSet<>();

        JSONArray accounts =
                root.getJSONArray(
                        "accounts"
                );

        for (int index = 0;
             index < accounts.length();
             index++) {

            accountNames.add(
                    accounts
                            .getJSONObject(
                                    index
                            )
                            .optString(
                                    "name",
                                    ""
                            )
            );
        }

        JSONArray creditCards =
                root.getJSONArray(
                        "creditCards"
                );

        for (int index = 0;
             index < creditCards.length();
             index++) {

            String cardAccount =
                    creditCards
                            .getJSONObject(
                                    index
                            )
                            .optString(
                                    "accountName",
                                    ""
                            );

            if (!accountNames.contains(
                    cardAccount
            )) {
                throw new IllegalStateException(
                        "Credit-card account is missing from backup."
                );
            }
        }
    }

    @NonNull
    private Set<Integer> collectIds(
            @NonNull JSONArray array
    ) throws Exception {

        Set<Integer> ids =
                new HashSet<>();

        for (int index = 0;
             index < array.length();
             index++) {

            ids.add(
                    array
                            .getJSONObject(
                                    index
                            )
                            .getInt(
                                    "id"
                            )
            );
        }

        return ids;
    }

    @NonNull
    private BackupSummary createBackupSummary(
            @NonNull JSONObject root,
            @NonNull Uri backupUri
    ) {
        BackupSummary summary =
                new BackupSummary();

        summary.createdAt =
                getBackupDateTime(
                        root,
                        backupUri
                );

        summary.fileSize =
                formatFileSize(
                        backupStorageManager
                                .getDocumentSize(
                                        backupUri
                                )
                );

        summary.backupVersion =
                root.optInt(
                        "backupVersion",
                        BACKUP_VERSION_LEGACY
                );

        summary.appVersion =
                root.optString(
                        "appVersion",
                        "Unknown"
                );

        summary.transactionCount =
                getArrayLength(
                        root.optJSONArray(
                                "transactions"
                        )
                );

        summary.expenseItemCount =
                getArrayLength(
                        root.optJSONArray(
                                "expenseItems"
                        )
                );

        summary.categoryCount =
                getArrayLength(
                        root.optJSONArray(
                                "categories"
                        )
                );

        summary.accountCount =
                getArrayLength(
                        root.optJSONArray(
                                "accounts"
                        )
                );

        summary.goalCount =
                getArrayLength(
                        root.optJSONArray(
                                "goals"
                        )
                );

        summary.recurringCount =
                getArrayLength(
                        root.optJSONArray(
                                "recurringTransactions"
                        )
                );

        summary.budgetCount =
                getArrayLength(
                        root.optJSONArray(
                                "budgets"
                        )
                );

        summary.loanCount =
                getArrayLength(
                        root.optJSONArray(
                                "loans"
                        )
                );

        summary.loanPaymentCount =
                getArrayLength(
                        root.optJSONArray(
                                "loanPayments"
                        )
                );

        summary.subscriptionCount =
                getArrayLength(
                        root.optJSONArray(
                                "subscriptions"
                        )
                );

        summary.creditCardCount =
                getArrayLength(
                        root.optJSONArray(
                                "creditCards"
                        )
                );

        summary.creditCardPaymentCount =
                getArrayLength(
                        root.optJSONArray(
                                "creditCardPayments"
                        )
                );

        summary.investmentCount =
                getArrayLength(
                        root.optJSONArray(
                                "investments"
                        )
                );

        summary.integrityStatus =
                summary.backupVersion
                        >= BACKUP_VERSION_FULL_DATA
                        ? "SHA-256 Verified"
                        : "Legacy v1 structure verified";

        return summary;
    }

    private void updateBackupStatus() {
        if (!backupStorageManager
                .hasUsableBackupFolder()) {

            if (backupStorageManager
                    .getSavedTreeUri() == null) {

                txtBackupStatus.setText(
                        "Backup folder अभी चुना नहीं गया है।\n"
                                + "Create Backup दबाने पर पहली बार folder चुनें।"
                );

            } else {
                txtBackupStatus.setText(
                        "Backup folder की permission उपलब्ध नहीं है।\n"
                                + "Create Backup या Restore Backup दबाकर "
                                + "folder दोबारा चुनें।"
                );
            }

            return;
        }

        txtBackupStatus.setText(
                "Backup की स्थिति जाँची जा रही है..."
        );

        new Thread(
                () -> {
                    String statusText;

                    try {
                        Uri latestBackupUri =
                                backupStorageManager
                                        .findLatestBackupUri();

                        if (latestBackupUri == null) {
                            statusText =
                                    "Backup folder तैयार है\n"
                                            + backupStorageManager
                                            .getBackupLocationLabel()
                                            + "\nअभी कोई backup उपलब्ध नहीं है।";

                        } else {
                            JSONObject root =
                                    readBackupJson(
                                            latestBackupUri
                                    );

                            validateBackupFile(
                                    root,
                                    latestBackupUri
                            );

                            int backupVersion =
                                    root.optInt(
                                            "backupVersion",
                                            BACKUP_VERSION_LEGACY
                                    );

                            String createdAt =
                                    getBackupDateTime(
                                            root,
                                            latestBackupUri
                                    );

                            long size =
                                    backupStorageManager
                                            .getDocumentSize(
                                                    latestBackupUri
                                            );

                            String verificationLabel =
                                    backupVersion
                                            >= BACKUP_VERSION_FULL_DATA
                                            ? "SHA-256 integrity verified"
                                            : "Legacy v1 structure verified";

                            int totalRecords =
                                    calculateTotalRecords(
                                            root
                                    );

                            statusText =
                                    "Latest backup उपलब्ध है\n"
                                            + "Version: "
                                            + backupVersion
                                            + "\n"
                                            + verificationLabel
                                            + "\n"
                                            + createdAt
                                            + "\n"
                                            + formatFileSize(
                                            size
                                    )
                                            + "\nRecords: "
                                            + totalRecords
                                            + (
                                            backupVersion
                                                    >= BACKUP_VERSION_INVESTMENTS
                                                    ? "\nInvestments included"
                                                    : "\nLegacy backup: Investments not included"
                                    )
                                            + "\n"
                                            + backupStorageManager
                                            .getBackupLocationLabel();
                        }

                    } catch (Exception exception) {
                        statusText =
                                "Backup file मौजूद है, लेकिन इसकी जानकारी "
                                        + "पढ़ी नहीं जा सकी।";
                    }

                    String finalStatusText =
                            statusText;

                    runOnUiThread(
                            () -> txtBackupStatus.setText(
                                    finalStatusText
                            )
                    );
                }
        ).start();
    }

    @NonNull
    private JSONObject readBackupJson(
            @NonNull Uri backupUri
    ) throws Exception {

        try (
                InputStream inputStream =
                        backupStorageManager
                                .openBackupInputStream(
                                        backupUri
                                );

                ByteArrayOutputStream outputStream =
                        new ByteArrayOutputStream()
        ) {
            byte[] buffer =
                    new byte[8192];

            int totalBytes =
                    0;

            int bytesRead;

            while ((bytesRead =
                    inputStream.read(
                            buffer
                    )) != -1) {

                totalBytes +=
                        bytesRead;

                if (totalBytes
                        > OfflineBackupEngine.MAX_BACKUP_BYTES) {

                    throw new IllegalStateException(
                            "Backup file exceeds the supported size."
                    );
                }

                outputStream.write(
                        buffer,
                        0,
                        bytesRead
                );
            }

            if (totalBytes <= 0) {
                throw new IllegalStateException(
                        "Backup file is empty."
                );
            }

            return new JSONObject(
                    outputStream.toString(
                            StandardCharsets.UTF_8.name()
                    )
            );
        }
    }

    private int calculateTotalRecords(
            @NonNull JSONObject root
    ) {
        return getArrayLength(
                root.optJSONArray(
                        "transactions"
                )
        )
                + getArrayLength(
                root.optJSONArray(
                        "expenseItems"
                )
        )
                + getArrayLength(
                root.optJSONArray(
                        "categories"
                )
        )
                + getArrayLength(
                root.optJSONArray(
                        "accounts"
                )
        )
                + getArrayLength(
                root.optJSONArray(
                        "goals"
                )
        )
                + getArrayLength(
                root.optJSONArray(
                        "recurringTransactions"
                )
        )
                + getArrayLength(
                root.optJSONArray(
                        "budgets"
                )
        )
                + getArrayLength(
                root.optJSONArray(
                        "loans"
                )
        )
                + getArrayLength(
                root.optJSONArray(
                        "loanPayments"
                )
        )
                + getArrayLength(
                root.optJSONArray(
                        "subscriptions"
                )
        )
                + getArrayLength(
                root.optJSONArray(
                        "creditCards"
                )
        )
                + getArrayLength(
                root.optJSONArray(
                        "creditCardPayments"
                )
        )
                + getArrayLength(
                root.optJSONArray(
                        "investments"
                )
        );
    }

    @NonNull
    private JSONArray arrayOrEmpty(
            @NonNull JSONObject root,
            @NonNull String field
    ) {
        JSONArray array =
                root.optJSONArray(
                        field
                );

        return array == null
                ? new JSONArray()
                : array;
    }

    private int getArrayLength(
            @Nullable JSONArray array
    ) {
        return array == null
                ? 0
                : array.length();
    }

    @NonNull
    private String getBackupDateTime(
            @NonNull JSONObject root,
            @NonNull Uri backupUri
    ) {
        String createdAt =
                root.optString(
                        "createdAt",
                        ""
                );

        if (!createdAt.trim().isEmpty()) {
            return createdAt;
        }

        long createdAtMillis =
                root.optLong(
                        "createdAtMillis",
                        0L
                );

        if (createdAtMillis > 0L) {
            return formatDateTime(
                    createdAtMillis
            );
        }

        long modifiedTime =
                backupStorageManager
                        .getDocumentLastModified(
                                backupUri
                        );

        if (modifiedTime > 0L) {
            return formatDateTime(
                    modifiedTime
            );
        }

        return "दिनांक एवं समय उपलब्ध नहीं";
    }

    private void safelyRecordOfflineSuccess(
            @NonNull OfflineBackupEngine.BackupResult result
    ) {
        try {
            schedulePreferences.recordOfflineBackupSuccess(
                    result.getCreatedAtMillis(),
                    result.getBackupId(),
                    result
                            .getRecordCounts()
                            .getTotalRecords(),
                    result.getBackupByteCount()
            );

        } catch (Exception exception) {
            Log.w(
                    TAG,
                    "Offline backup succeeded, but status could not be saved.",
                    exception
            );
        }
    }

    private void safelyRecordOfflineFailure(
            @NonNull Exception exception
    ) {
        try {
            schedulePreferences.recordOfflineBackupFailure(
                    System.currentTimeMillis(),
                    getUsefulErrorMessage(
                            exception
                    )
            );

        } catch (Exception statusException) {
            Log.w(
                    TAG,
                    "Offline backup failure status could not be saved.",
                    statusException
            );
        }
    }

    @NonNull
    private String text(
            @NonNull JSONObject object,
            @NonNull String field,
            @NonNull String fallback
    ) {
        if (!object.has(
                field
        )
                || object.isNull(
                field
        )) {

            return fallback;
        }

        String value =
                object.optString(
                        field,
                        fallback
                );

        return value == null
                ? fallback
                : value;
    }

    private int booleanInteger(
            @NonNull JSONObject object,
            @NonNull String field,
            boolean fallback
    ) {
        if (!object.has(
                field
        )
                || object.isNull(
                field
        )) {

            return fallback
                    ? 1
                    : 0;
        }

        Object rawValue =
                object.opt(
                        field
                );

        if (rawValue
                instanceof Boolean) {

            return (Boolean) rawValue
                    ? 1
                    : 0;
        }

        if (rawValue
                instanceof Number) {

            return ((Number) rawValue)
                    .intValue() != 0
                    ? 1
                    : 0;
        }

        String textValue =
                String.valueOf(
                        rawValue
                );

        return "true".equalsIgnoreCase(
                textValue
        )
                || "1".equals(
                textValue
        )
                ? 1
                : 0;
    }

    @NonNull
    private String getUsefulErrorMessage(
            @NonNull Throwable throwable
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
            return "Storage provider ने operation पूरा नहीं किया।";
        }

        return usefulMessage;
    }

    private void setBackupButtonsEnabled(
            boolean enabled
    ) {
        btnCreateBackup.setEnabled(
                enabled
        );

        btnRestoreBackup.setEnabled(
                enabled
        );

        btnChangeBackupFolder.setEnabled(
                enabled
        );
    }

    @NonNull
    private String formatDateTime(
            long timestamp
    ) {
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
            long sizeInBytes
    ) {
        if (sizeInBytes < 0L) {
            return "File size उपलब्ध नहीं";
        }

        if (sizeInBytes < 1024L) {
            return sizeInBytes
                    + " Bytes";
        }

        double sizeInKb =
                sizeInBytes / 1024D;

        if (sizeInKb < 1024D) {
            return String.format(
                    Locale.getDefault(),
                    "%.2f KB",
                    sizeInKb
            );
        }

        double sizeInMb =
                sizeInKb / 1024D;

        return String.format(
                Locale.getDefault(),
                "%.2f MB",
                sizeInMb
        );
    }

    private interface RowArgumentsFactory {

        @NonNull
        Object[] createArguments(
                @NonNull JSONObject object,
                int index
        );
    }

    private static final class RestorePayload {

        private int backupVersion;

        private JSONArray transactions =
                new JSONArray();

        private JSONArray expenseItems =
                new JSONArray();

        private JSONArray categories =
                new JSONArray();

        private JSONArray accounts =
                new JSONArray();

        private JSONArray goals =
                new JSONArray();

        private JSONArray recurringTransactions =
                new JSONArray();

        private JSONArray budgets =
                new JSONArray();

        private JSONArray loans =
                new JSONArray();

        private JSONArray loanPayments =
                new JSONArray();

        private JSONArray subscriptions =
                new JSONArray();

        private JSONArray creditCards =
                new JSONArray();

        private JSONArray creditCardPayments =
                new JSONArray();

        private JSONArray investments =
                new JSONArray();
    }

    private static final class BackupSummary {

        private String createdAt =
                "";

        private String fileSize =
                "";

        private String appVersion =
                "";

        private String integrityStatus =
                "";

        private int backupVersion;

        private int transactionCount;

        private int expenseItemCount;

        private int categoryCount;

        private int accountCount;

        private int goalCount;

        private int recurringCount;

        private int budgetCount;

        private int loanCount;

        private int loanPaymentCount;

        private int subscriptionCount;

        private int creditCardCount;

        private int creditCardPaymentCount;

        private int investmentCount;
    }
}