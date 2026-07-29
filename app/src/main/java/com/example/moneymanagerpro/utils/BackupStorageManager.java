package com.example.moneymanagerpro.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class BackupStorageManager {

    public static final String APP_FOLDER_NAME =
            "MoneyManagerPro";

    public static final String BACKUP_FOLDER_NAME =
            "Backup";

    public static final String LATEST_BACKUP_FILE_NAME =
            "MoneyManagerPro_Latest.mmpbackup";

    public static final String TEMP_BACKUP_FILE_NAME =
            "MoneyManagerPro_Temporary.tmp";

    private static final String PREVIOUS_BACKUP_FILE_NAME =
            "MoneyManagerPro_Previous.tmp";

    private static final String BACKUP_MIME_TYPE =
            "application/json";

    private static final String PREFERENCES_NAME =
            "money_manager_backup_storage";

    private static final String KEY_BACKUP_TREE_URI =
            "backup_tree_uri";

    private final Context appContext;
    private final ContentResolver contentResolver;
    private final SharedPreferences preferences;

    public BackupStorageManager(Context context) {
        appContext = context.getApplicationContext();
        contentResolver = appContext.getContentResolver();

        preferences = appContext.getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE
        );
    }

    /**
     * पहली बार parent folder चुनने के लिए Intent देता है।
     *
     * User Documents या किसी दूसरे सामान्य folder को चुन सकता है।
     * उसके अंदर app स्वयं:
     *
     * MoneyManagerPro/Backup
     *
     * folder बनाएगा।
     */
    public Intent createFolderPickerIntent() {
        Intent intent = new Intent(
                Intent.ACTION_OPEN_DOCUMENT_TREE
        );

        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
        );

        intent.addFlags(
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        );

        intent.addFlags(
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );

        intent.addFlags(
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        );

        return intent;
    }

    /**
     * User द्वारा चुने गए folder की permanent URI permission save करता है।
     */
    public synchronized void saveSelectedFolder(
            Uri selectedTreeUri,
            int returnedIntentFlags
    ) throws IOException {

        if (selectedTreeUri == null) {
            throw new IOException(
                    "Selected backup folder is invalid."
            );
        }

        int permissionFlags =
                returnedIntentFlags
                        & (
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                );

        boolean hasReadPermission =
                (
                        permissionFlags
                                & Intent.FLAG_GRANT_READ_URI_PERMISSION
                ) != 0;

        boolean hasWritePermission =
                (
                        permissionFlags
                                & Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                ) != 0;

        if (!hasReadPermission || !hasWritePermission) {
            throw new IOException(
                    "Read and write access was not granted."
            );
        }

        Uri oldTreeUri = getSavedTreeUri();

        try {
            contentResolver.takePersistableUriPermission(
                    selectedTreeUri,
                    permissionFlags
            );
        } catch (SecurityException exception) {
            throw new IOException(
                    "Permanent folder permission could not be saved.",
                    exception
            );
        }

        verifySelectedFolderCanBeUsed(
                selectedTreeUri
        );

        boolean saved = preferences
                .edit()
                .putString(
                        KEY_BACKUP_TREE_URI,
                        selectedTreeUri.toString()
                )
                .commit();

        if (!saved) {
            releasePersistedPermissionQuietly(
                    selectedTreeUri
            );

            throw new IOException(
                    "Backup folder setting could not be saved."
            );
        }

        try {
            getOrCreateBackupFolderUri();

        } catch (Exception exception) {
            if (oldTreeUri == null) {
                preferences
                        .edit()
                        .remove(KEY_BACKUP_TREE_URI)
                        .apply();
            } else {
                preferences
                        .edit()
                        .putString(
                                KEY_BACKUP_TREE_URI,
                                oldTreeUri.toString()
                        )
                        .apply();
            }

            if (oldTreeUri == null
                    || !oldTreeUri.equals(selectedTreeUri)) {
                releasePersistedPermissionQuietly(
                        selectedTreeUri
                );
            }

            throw new IOException(
                    "MoneyManagerPro backup folder could not be created.",
                    exception
            );
        }

        if (oldTreeUri != null
                && !oldTreeUri.equals(selectedTreeUri)) {
            releasePersistedPermissionQuietly(
                    oldTreeUri
            );
        }
    }

    /**
     * Saved parent folder URI देता है।
     */
    public Uri getSavedTreeUri() {
        String savedUri = preferences.getString(
                KEY_BACKUP_TREE_URI,
                null
        );

        if (savedUri == null || savedUri.trim().isEmpty()) {
            return null;
        }

        try {
            return Uri.parse(savedUri);

        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * Check करता है कि folder selected है और permission valid है।
     */
    public boolean hasUsableBackupFolder() {
        return getSavedTreeUri() != null
                && isSavedPermissionValid();
    }

    /**
     * Persisted read/write permission check करता है।
     */
    public boolean isSavedPermissionValid() {
        Uri savedTreeUri = getSavedTreeUri();

        if (savedTreeUri == null) {
            return false;
        }

        boolean readPermissionFound = false;
        boolean writePermissionFound = false;

        for (UriPermission permission :
                contentResolver.getPersistedUriPermissions()) {

            if (!savedTreeUri.equals(permission.getUri())) {
                continue;
            }

            readPermissionFound =
                    permission.isReadPermission();

            writePermissionFound =
                    permission.isWritePermission();

            break;
        }

        /*
         * Provider query को permission check का हिस्सा न बनाएं।
         * USB/cloud/offline document providers कभी-कभी temporary query
         * failure देते हैं। ऐसी स्थिति में persisted location हटाना गलत
         * है; actual backup/restore operation उपयोगी error देगा।
         */
        return readPermissionFound
                && writePermissionFound;
    }

    /**
     * Change Backup Folder के लिए पुरानी setting हटाता है।
     *
     * यह actual backup file delete नहीं करता।
     */
    public synchronized void clearSavedFolder() {
        Uri savedTreeUri = getSavedTreeUri();

        preferences
                .edit()
                .remove(KEY_BACKUP_TREE_URI)
                .apply();

        if (savedTreeUri != null) {
            releasePersistedPermissionQuietly(
                    savedTreeUri
            );
        }
    }

    /**
     * MoneyManagerPro/Backup folder खोजता या बनाता है।
     */
    public Uri getOrCreateBackupFolderUri()
            throws IOException {

        Uri appFolderUri =
                getAppFolderUri(true);

        Uri backupFolderUri = findChildDocument(
                appFolderUri,
                BACKUP_FOLDER_NAME,
                DocumentsContract.Document.MIME_TYPE_DIR
        );

        if (backupFolderUri != null) {
            return backupFolderUri;
        }

        return createDirectory(
                appFolderUri,
                BACKUP_FOLDER_NAME
        );
    }

    /**
     * केवल existing Backup folder खोजता है।
     * Folder न मिले तो null देता है।
     */
    public Uri findExistingBackupFolderUri()
            throws IOException {

        Uri appFolderUri =
                getAppFolderUri(false);

        if (appFolderUri == null) {
            return null;
        }

        return findChildDocument(
                appFolderUri,
                BACKUP_FOLDER_NAME,
                DocumentsContract.Document.MIME_TYPE_DIR
        );
    }

    /**
     * Latest backup मौजूद है तो उसका URI देता है।
     */
    public Uri findLatestBackupUri()
            throws IOException {

        Uri backupFolderUri =
                findExistingBackupFolderUri();

        if (backupFolderUri == null) {
            return null;
        }

        return findChildDocument(
                backupFolderUri,
                LATEST_BACKUP_FILE_NAME,
                null
        );
    }

    public boolean latestBackupExists() {
        try {
            Uri latestBackupUri =
                    findLatestBackupUri();

            return latestBackupUri != null
                    && documentExists(latestBackupUri);

        } catch (Exception exception) {
            return false;
        }
    }

    /**
     * नया backup पहले temporary file में लिखा जाएगा।
     */
    public Uri createTemporaryBackupUri()
            throws IOException {

        Uri backupFolderUri =
                getOrCreateBackupFolderUri();

        Uri oldTemporaryUri = findChildDocument(
                backupFolderUri,
                TEMP_BACKUP_FILE_NAME,
                null
        );

        if (oldTemporaryUri != null) {
            deleteDocumentQuietly(
                    oldTemporaryUri
            );
        }

        return createFile(
                backupFolderUri,
                TEMP_BACKUP_FILE_NAME,
                BACKUP_MIME_TYPE
        );
    }

    /**
     * Temporary backup को Latest backup बनाता है।
     *
     * पहले पुराने backup को सुरक्षित नाम दिया जाता है।
     * नया backup सफल होने के बाद पुराना temporary backup हटाया जाता है।
     */
    public synchronized Uri commitTemporaryBackup(
            Uri temporaryBackupUri
    ) throws IOException {

        if (temporaryBackupUri == null
                || !documentExists(temporaryBackupUri)) {
            throw new IOException(
                    "Temporary backup file does not exist."
            );
        }

        Uri backupFolderUri =
                getOrCreateBackupFolderUri();

        Uri existingLatestUri = findChildDocument(
                backupFolderUri,
                LATEST_BACKUP_FILE_NAME,
                null
        );

        Uri existingPreviousUri = findChildDocument(
                backupFolderUri,
                PREVIOUS_BACKUP_FILE_NAME,
                null
        );

        if (existingPreviousUri != null) {
            deleteDocumentQuietly(
                    existingPreviousUri
            );
        }

        /*
         * पहली बार backup बन रहा है।
         */
        if (existingLatestUri == null) {
            Uri renamedTemporaryUri = renameDocumentQuietly(
                    temporaryBackupUri,
                    LATEST_BACKUP_FILE_NAME
            );

            if (renamedTemporaryUri != null) {
                return renamedTemporaryUri;
            }

            Uri newLatestUri = createFile(
                    backupFolderUri,
                    LATEST_BACKUP_FILE_NAME,
                    BACKUP_MIME_TYPE
            );

            try {
                copyDocument(
                        temporaryBackupUri,
                        newLatestUri
                );

                deleteDocumentQuietly(
                        temporaryBackupUri
                );

                return newLatestUri;

            } catch (Exception exception) {
                deleteDocumentQuietly(
                        newLatestUri
                );

                throw new IOException(
                        "New backup could not be saved.",
                        exception
                );
            }
        }

        /*
         * पुराने latest backup का नाम temporary previous किया जाता है।
         */
        Uri previousBackupUri = renameDocumentQuietly(
                existingLatestUri,
                PREVIOUS_BACKUP_FILE_NAME
        );

        /*
         * Provider rename support नहीं करता तो पहले पुराने latest की
         * safety copy बनाई जाती है। नया data लिखने में failure होने पर
         * उसी copy से latest file rollback की जाती है।
         */
        if (previousBackupUri == null) {
            Uri safetyCopyUri = null;

            try {
                safetyCopyUri = createFile(
                        backupFolderUri,
                        PREVIOUS_BACKUP_FILE_NAME,
                        BACKUP_MIME_TYPE
                );

                copyDocument(
                        existingLatestUri,
                        safetyCopyUri
                );

                copyDocument(
                        temporaryBackupUri,
                        existingLatestUri
                );

                deleteDocumentQuietly(
                        temporaryBackupUri
                );

                deleteDocumentQuietly(
                        safetyCopyUri
                );

                return existingLatestUri;

            } catch (Exception exception) {
                if (safetyCopyUri != null) {
                    try {
                        copyDocument(
                                safetyCopyUri,
                                existingLatestUri
                        );

                        deleteDocumentQuietly(
                                safetyCopyUri
                        );

                    } catch (Exception rollbackException) {
                        exception.addSuppressed(
                                rollbackException
                        );
                    }
                }

                throw new IOException(
                        "Existing backup could not be safely replaced.",
                        exception
                );
            }
        }

        /*
         * अब नए temporary backup को latest नाम दिया जाता है।
         */
        Uri newLatestUri = renameDocumentQuietly(
                temporaryBackupUri,
                LATEST_BACKUP_FILE_NAME
        );

        if (newLatestUri != null) {
            deleteDocumentQuietly(
                    previousBackupUri
            );

            return newLatestUri;
        }

        /*
         * Rename fail होने पर नई latest file बनाकर copy की जाती है।
         * पुराना backup तब तक previous नाम से सुरक्षित रहता है।
         */
        Uri copiedLatestUri = null;

        try {
            copiedLatestUri = createFile(
                    backupFolderUri,
                    LATEST_BACKUP_FILE_NAME,
                    BACKUP_MIME_TYPE
            );

            copyDocument(
                    temporaryBackupUri,
                    copiedLatestUri
            );

            deleteDocumentQuietly(
                    temporaryBackupUri
            );

            deleteDocumentQuietly(
                    previousBackupUri
            );

            return copiedLatestUri;

        } catch (Exception exception) {
            if (copiedLatestUri != null) {
                deleteDocumentQuietly(
                        copiedLatestUri
                );
            }

            renameDocumentQuietly(
                    previousBackupUri,
                    LATEST_BACKUP_FILE_NAME
            );

            throw new IOException(
                    "Backup replacement failed. Previous backup was preserved.",
                    exception
            );
        }
    }

    /**
     * Backup file पढ़ने के लिए InputStream देता है।
     */
    public InputStream openBackupInputStream(
            Uri backupUri
    ) throws IOException {

        if (backupUri == null) {
            throw new IOException(
                    "Backup URI is invalid."
            );
        }

        InputStream inputStream =
                contentResolver.openInputStream(
                        backupUri
                );

        if (inputStream == null) {
            throw new IOException(
                    "Backup file could not be opened."
            );
        }

        return inputStream;
    }

    /**
     * Temporary file में नया data लिखने के लिए OutputStream देता है।
     */
    public OutputStream openBackupOutputStream(
            Uri backupUri
    ) throws IOException {

        if (backupUri == null) {
            throw new IOException(
                    "Backup URI is invalid."
            );
        }

        Exception firstFailure = null;

        /*
         * "rwt" local Android Documents provider पर सबसे सुरक्षित है,
         * लेकिन सभी OEM/cloud providers इसे implement नहीं करते।
         * क्रमशः कम strict modes पर fallback करने से वही selected folder
         * दोबारा पूछे बिना इस्तेमाल किया जा सकता है।
         */
        String[] supportedModes = {
                "rwt",
                "wt",
                "w"
        };

        for (String mode : supportedModes) {
            try {
                OutputStream outputStream =
                        contentResolver.openOutputStream(
                                backupUri,
                                mode
                        );

                if (outputStream != null) {
                    return outputStream;
                }

            } catch (Exception exception) {
                if (firstFailure == null) {
                    firstFailure = exception;
                }
            }
        }

        try {
            OutputStream outputStream =
                    contentResolver.openOutputStream(
                            backupUri
                    );

            if (outputStream != null) {
                return outputStream;
            }

        } catch (Exception exception) {
            if (firstFailure == null) {
                firstFailure = exception;
            }
        }

        IOException writeFailure =
                new IOException(
                        "Selected folder provider did not allow the backup file to be written."
                );

        if (firstFailure != null) {
            writeFailure.initCause(firstFailure);
        }

        throw writeFailure;
    }

    public long getDocumentSize(Uri documentUri) {
        return queryLongValue(
                documentUri,
                DocumentsContract.Document.COLUMN_SIZE,
                -1L
        );
    }

    public long getDocumentLastModified(Uri documentUri) {
        return queryLongValue(
                documentUri,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                0L
        );
    }

    public String getDocumentDisplayName(
            Uri documentUri
    ) {
        return queryStringValue(
                documentUri,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                ""
        );
    }

    /**
     * Backup screen पर दिखाने के लिए location text देता है।
     */
    public String getBackupLocationLabel() {
        Uri treeUri = getSavedTreeUri();

        if (treeUri == null) {
            return "Backup folder selected नहीं है";
        }

        try {
            Uri rootDocumentUri =
                    buildRootDocumentUri(treeUri);

            String selectedFolderName =
                    getDocumentDisplayName(
                            rootDocumentUri
                    );

            if (selectedFolderName == null
                    || selectedFolderName.trim().isEmpty()) {
                selectedFolderName =
                        "Selected Folder";
            }

            if (APP_FOLDER_NAME.equalsIgnoreCase(
                    selectedFolderName
            )) {
                return selectedFolderName
                        + "/"
                        + BACKUP_FOLDER_NAME;
            }

            return selectedFolderName
                    + "/"
                    + APP_FOLDER_NAME
                    + "/"
                    + BACKUP_FOLDER_NAME;

        } catch (Exception exception) {
            return APP_FOLDER_NAME
                    + "/"
                    + BACKUP_FOLDER_NAME;
        }
    }

    private Uri getAppFolderUri(
            boolean createIfMissing
    ) throws IOException {

        Uri treeUri =
                requireSavedTreeUri();

        Uri rootDocumentUri =
                buildRootDocumentUri(treeUri);

        String rootFolderName =
                getDocumentDisplayName(
                        rootDocumentUri
                );

        /*
         * User ने पहले से MoneyManagerPro folder ही चुना है।
         */
        if (APP_FOLDER_NAME.equalsIgnoreCase(
                rootFolderName
        )) {
            return rootDocumentUri;
        }

        Uri appFolderUri = findChildDocument(
                rootDocumentUri,
                APP_FOLDER_NAME,
                DocumentsContract.Document.MIME_TYPE_DIR
        );

        if (appFolderUri != null) {
            return appFolderUri;
        }

        if (!createIfMissing) {
            return null;
        }

        return createDirectory(
                rootDocumentUri,
                APP_FOLDER_NAME
        );
    }

    private Uri requireSavedTreeUri()
            throws IOException {

        Uri savedTreeUri =
                getSavedTreeUri();

        if (savedTreeUri == null) {
            throw new IOException(
                    "Backup folder has not been selected."
            );
        }

        if (!isSavedPermissionValid()) {
            throw new IOException(
                    "Backup folder permission is no longer valid."
            );
        }

        return savedTreeUri;
    }

    private void verifySelectedFolderCanBeUsed(
            Uri selectedTreeUri
    ) throws IOException {

        Uri rootDocumentUri =
                buildRootDocumentUri(
                        selectedTreeUri
                );

        if (!documentExists(rootDocumentUri)) {
            throw new IOException(
                    "Selected folder cannot be accessed."
            );
        }

        long folderFlags = queryLongValue(
                rootDocumentUri,
                DocumentsContract.Document.COLUMN_FLAGS,
                -1L
        );

        boolean supportsCreatingFiles =
                (
                        folderFlags
                                & DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
                ) != 0;

        /*
         * कुछ valid providers COLUMN_FLAGS return नहीं करते। उस स्थिति
         * में actual MoneyManagerPro folder creation निर्णायक check है।
         */
        if (folderFlags >= 0L
                && !supportsCreatingFiles) {
            throw new IOException(
                    "Selected folder does not allow creating backup files."
            );
        }
    }

    private Uri buildRootDocumentUri(
            Uri treeUri
    ) throws IOException {

        try {
            String treeDocumentId =
                    DocumentsContract.getTreeDocumentId(
                            treeUri
                    );

            return DocumentsContract
                    .buildDocumentUriUsingTree(
                            treeUri,
                            treeDocumentId
                    );

        } catch (Exception exception) {
            throw new IOException(
                    "Invalid folder URI.",
                    exception
            );
        }
    }

    private Uri findChildDocument(
            Uri parentDocumentUri,
            String requiredName,
            String requiredMimeType
    ) throws IOException {

        if (parentDocumentUri == null) {
            return null;
        }

        Uri treeUri =
                requireSavedTreeUri();

        String parentDocumentId;

        try {
            parentDocumentId =
                    DocumentsContract.getDocumentId(
                            parentDocumentUri
                    );

        } catch (Exception exception) {
            throw new IOException(
                    "Parent folder is invalid.",
                    exception
            );
        }

        Uri childrenUri =
                DocumentsContract
                        .buildChildDocumentsUriUsingTree(
                                treeUri,
                                parentDocumentId
                        );

        String[] projection = new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };

        try (
                Cursor cursor =
                        contentResolver.query(
                                childrenUri,
                                projection,
                                null,
                                null,
                                null
                        )
        ) {
            if (cursor == null) {
                return null;
            }

            int idColumn =
                    cursor.getColumnIndex(
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID
                    );

            int nameColumn =
                    cursor.getColumnIndex(
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME
                    );

            int mimeColumn =
                    cursor.getColumnIndex(
                            DocumentsContract.Document.COLUMN_MIME_TYPE
                    );

            while (cursor.moveToNext()) {
                String documentName =
                        nameColumn >= 0
                                ? cursor.getString(nameColumn)
                                : null;

                String documentMimeType =
                        mimeColumn >= 0
                                ? cursor.getString(mimeColumn)
                                : null;

                if (!requiredName.equals(
                        documentName
                )) {
                    continue;
                }

                if (requiredMimeType != null
                        && !requiredMimeType.equals(
                        documentMimeType
                )) {
                    continue;
                }

                if (idColumn < 0) {
                    continue;
                }

                String childDocumentId =
                        cursor.getString(idColumn);

                return DocumentsContract
                        .buildDocumentUriUsingTree(
                                treeUri,
                                childDocumentId
                        );
            }

            return null;

        } catch (SecurityException exception) {
            throw new IOException(
                    "Backup folder access was denied.",
                    exception
            );
        }
    }

    private Uri createDirectory(
            Uri parentDocumentUri,
            String directoryName
    ) throws IOException {

        try {
            Uri newDirectoryUri =
                    DocumentsContract.createDocument(
                            contentResolver,
                            parentDocumentUri,
                            DocumentsContract.Document.MIME_TYPE_DIR,
                            directoryName
                    );

            if (newDirectoryUri == null) {
                throw new IOException(
                        "Folder could not be created: "
                                + directoryName
                );
            }

            return newDirectoryUri;

        } catch (Exception exception) {
            throw new IOException(
                    "Folder could not be created: "
                            + directoryName,
                    exception
            );
        }
    }

    private Uri createFile(
            Uri parentDocumentUri,
            String fileName,
            String mimeType
    ) throws IOException {

        try {
            Uri newFileUri =
                    DocumentsContract.createDocument(
                            contentResolver,
                            parentDocumentUri,
                            mimeType,
                            fileName
                    );

            if (newFileUri == null) {
                throw new IOException(
                        "Backup file could not be created."
                );
            }

            return newFileUri;

        } catch (Exception exception) {
            throw new IOException(
                    "Backup file could not be created.",
                    exception
            );
        }
    }

    private void copyDocument(
            Uri sourceUri,
            Uri destinationUri
    ) throws IOException {

        try (
                InputStream inputStream =
                        openBackupInputStream(sourceUri);

                OutputStream outputStream =
                        openBackupOutputStream(destinationUri)
        ) {
            byte[] buffer =
                    new byte[8192];

            int bytesRead;

            while (
                    (
                            bytesRead =
                                    inputStream.read(buffer)
                    ) != -1
            ) {
                outputStream.write(
                        buffer,
                        0,
                        bytesRead
                );
            }

            outputStream.flush();
        }
    }

    private Uri renameDocumentQuietly(
            Uri documentUri,
            String newDisplayName
    ) {
        try {
            return DocumentsContract.renameDocument(
                    contentResolver,
                    documentUri,
                    newDisplayName
            );

        } catch (Exception exception) {
            return null;
        }
    }

    private void deleteDocumentQuietly(
            Uri documentUri
    ) {
        if (documentUri == null) {
            return;
        }

        try {
            DocumentsContract.deleteDocument(
                    contentResolver,
                    documentUri
            );
        } catch (Exception ignored) {
            // File पहले से delete हो सकती है।
        }
    }

    private boolean documentExists(
            Uri documentUri
    ) {
        if (documentUri == null) {
            return false;
        }

        String[] projection = new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID
        };

        try (
                Cursor cursor =
                        contentResolver.query(
                                documentUri,
                                projection,
                                null,
                                null,
                                null
                        )
        ) {
            return cursor != null
                    && cursor.moveToFirst();

        } catch (Exception exception) {
            return false;
        }
    }

    private String queryStringValue(
            Uri documentUri,
            String columnName,
            String defaultValue
    ) {
        if (documentUri == null) {
            return defaultValue;
        }

        String[] projection =
                new String[]{columnName};

        try (
                Cursor cursor =
                        contentResolver.query(
                                documentUri,
                                projection,
                                null,
                                null,
                                null
                        )
        ) {
            if (cursor == null
                    || !cursor.moveToFirst()) {
                return defaultValue;
            }

            int columnIndex =
                    cursor.getColumnIndex(
                            columnName
                    );

            if (columnIndex < 0
                    || cursor.isNull(columnIndex)) {
                return defaultValue;
            }

            return cursor.getString(
                    columnIndex
            );

        } catch (Exception exception) {
            return defaultValue;
        }
    }

    private long queryLongValue(
            Uri documentUri,
            String columnName,
            long defaultValue
    ) {
        if (documentUri == null) {
            return defaultValue;
        }

        String[] projection =
                new String[]{columnName};

        try (
                Cursor cursor =
                        contentResolver.query(
                                documentUri,
                                projection,
                                null,
                                null,
                                null
                        )
        ) {
            if (cursor == null
                    || !cursor.moveToFirst()) {
                return defaultValue;
            }

            int columnIndex =
                    cursor.getColumnIndex(
                            columnName
                    );

            if (columnIndex < 0
                    || cursor.isNull(columnIndex)) {
                return defaultValue;
            }

            return cursor.getLong(
                    columnIndex
            );

        } catch (Exception exception) {
            return defaultValue;
        }
    }

    private void releasePersistedPermissionQuietly(
            Uri treeUri
    ) {
        try {
            contentResolver.releasePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            );
        } catch (Exception ignored) {
            // Permission पहले ही remove हो सकती है।
        }
    }
}
