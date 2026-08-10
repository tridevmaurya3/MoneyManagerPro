package com.example.moneymanagerpro.pro;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.moneymanagerpro.backup.BackupIntegrity;
import com.example.moneymanagerpro.cloud.BackupSchedulePreferences;
import com.example.moneymanagerpro.utils.BackupStorageManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Maintains verified local restore points and a compact history of successful
 * offline/cloud backup checkpoints. It never stores cloud recovery secrets.
 */
public final class BackupHistoryManager {

    private static final String RESTORE_PREFIX = "MoneyManagerPro_RestorePoint_";
    private static final String RESTORE_SUFFIX = ".mmpbackup";
    private static final int MAX_RESTORE_POINTS = 8;
    private static final int MAX_STATUS_HISTORY = 20;
    private static final int MAX_READ_BYTES = 25 * 1024 * 1024;

    private static final String PREFS = "backup_security_pro_history";
    private static final String KEY_LAST_LOCAL_SOURCE_MODIFIED = "last_local_source_modified";
    private static final String KEY_STATUS_HISTORY = "status_history";

    private final Context context;
    private final ContentResolver resolver;
    private final BackupStorageManager storage;
    private final BackupSchedulePreferences schedulePreferences;
    private final SharedPreferences preferences;

    public BackupHistoryManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.resolver = this.context.getContentResolver();
        this.storage = new BackupStorageManager(this.context);
        this.schedulePreferences = new BackupSchedulePreferences(this.context);
        this.preferences = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Captures local restore point and technical cloud/offline success history. */
    public synchronized CaptureResult captureCheckpoints(boolean forceLocalRestorePoint) throws Exception {
        boolean localCreated = captureLocalRestorePoint(forceLocalRestorePoint);
        int addedHistory = captureStatusHistory();
        return new CaptureResult(localCreated, addedHistory);
    }

    public synchronized boolean captureLocalRestorePoint(boolean force) throws Exception {
        if (!storage.hasUsableBackupFolder()) {
            return false;
        }

        Uri latest = storage.findLatestBackupUri();
        if (latest == null) {
            return false;
        }

        long modified = storage.getDocumentLastModified(latest);
        if (modified <= 0L) {
            modified = System.currentTimeMillis();
        }

        long previousSource = preferences.getLong(KEY_LAST_LOCAL_SOURCE_MODIFIED, 0L);
        if (!force && previousSource == modified) {
            return false;
        }

        if (!verifyUri(latest)) {
            throw new IllegalStateException("Latest offline backup failed SHA-256 integrity verification.");
        }

        Uri folder = storage.findExistingBackupFolderUri();
        if (folder == null) {
            return false;
        }

        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
                .format(new Date(System.currentTimeMillis()));
        String name = RESTORE_PREFIX + stamp + RESTORE_SUFFIX;

        Uri destination = DocumentsContract.createDocument(
                resolver,
                folder,
                "application/json",
                name
        );

        if (destination == null) {
            throw new IllegalStateException("Restore point file could not be created.");
        }

        try {
            copy(latest, destination);
        } catch (Exception exception) {
            deleteQuietly(destination);
            throw exception;
        }

        if (!verifyUri(destination)) {
            deleteQuietly(destination);
            throw new IllegalStateException("New restore point did not pass integrity verification.");
        }

        preferences.edit().putLong(KEY_LAST_LOCAL_SOURCE_MODIFIED, modified).apply();
        pruneRestorePoints();
        return true;
    }

    public synchronized boolean verifyLatestLocalBackup() throws Exception {
        Uri latest = storage.findLatestBackupUri();
        return latest != null && verifyUri(latest);
    }

    @NonNull
    public synchronized List<RestorePoint> listRestorePoints() {
        try {
            Uri folder = storage.findExistingBackupFolderUri();
            if (folder == null) return new ArrayList<>();
            return queryRestorePoints(folder);
        } catch (Exception exception) {
            return new ArrayList<>();
        }
    }

    @NonNull
    public synchronized List<HistoryEntry> getStatusHistory() {
        List<HistoryEntry> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences.getString(KEY_STATUS_HISTORY, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                result.add(new HistoryEntry(
                        item.optString("type", "Backup"),
                        item.optLong("time", 0L),
                        item.optString("id", ""),
                        item.optInt("records", 0),
                        item.optLong("bytes", 0L)
                ));
            }
        } catch (Exception ignored) {
        }
        Collections.sort(result, (first, second) -> Long.compare(second.timeMillis, first.timeMillis));
        return result;
    }

    public boolean hasUsableBackupFolder() {
        return storage.hasUsableBackupFolder();
    }

    @NonNull
    public String getBackupLocationLabel() {
        return storage.getBackupLocationLabel();
    }

    private int captureStatusHistory() {
        int added = 0;
        try {
            BackupSchedulePreferences.BackupStatus offline = schedulePreferences.getOfflineStatus();
            if (offline.hasSuccessfulBackup()) {
                if (appendHistoryIfNew(
                        "Offline verified",
                        offline.getLastSuccessAtMillis(),
                        offline.getLastBackupId(),
                        offline.getLastRecordCount(),
                        offline.getLastByteCount()
                )) {
                    added++;
                }
            }
        } catch (Exception ignored) {
        }

        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                BackupSchedulePreferences.BackupStatus cloud =
                        schedulePreferences.getCloudStatus(user.getUid());
                if (cloud.hasSuccessfulBackup()) {
                    if (appendHistoryIfNew(
                            "Encrypted cloud",
                            cloud.getLastSuccessAtMillis(),
                            cloud.getLastBackupId(),
                            cloud.getLastRecordCount(),
                            cloud.getLastByteCount()
                    )) {
                        added++;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return added;
    }

    private boolean appendHistoryIfNew(
            @NonNull String type,
            long time,
            @Nullable String backupId,
            int records,
            long bytes
    ) throws Exception {
        if (time <= 0L) return false;

        JSONArray existing = new JSONArray(preferences.getString(KEY_STATUS_HISTORY, "[]"));
        String safeId = backupId == null ? "" : backupId.trim();

        for (int i = 0; i < existing.length(); i++) {
            JSONObject item = existing.optJSONObject(i);
            if (item == null) continue;
            if (type.equals(item.optString("type"))
                    && time == item.optLong("time")
                    && safeId.equals(item.optString("id"))) {
                return false;
            }
        }

        JSONArray next = new JSONArray();
        JSONObject fresh = new JSONObject();
        fresh.put("type", type);
        fresh.put("time", time);
        fresh.put("id", safeId);
        fresh.put("records", Math.max(0, records));
        fresh.put("bytes", Math.max(0L, bytes));
        next.put(fresh);

        for (int i = 0; i < existing.length() && next.length() < MAX_STATUS_HISTORY; i++) {
            JSONObject item = existing.optJSONObject(i);
            if (item != null) next.put(item);
        }

        preferences.edit().putString(KEY_STATUS_HISTORY, next.toString()).apply();
        return true;
    }

    private boolean verifyUri(@NonNull Uri uri) throws Exception {
        long size = storage.getDocumentSize(uri);
        if (size > MAX_READ_BYTES) {
            return false;
        }

        byte[] bytes;
        try (InputStream input = storage.openBackupInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_READ_BYTES) return false;
                output.write(buffer, 0, read);
            }
            bytes = output.toByteArray();
        }

        JSONObject root = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        String checksum = root.optString("integritySha256", "");
        return BackupIntegrity.verify(root, checksum);
    }

    private void copy(@NonNull Uri source, @NonNull Uri destination) throws Exception {
        try (InputStream input = storage.openBackupInputStream(source);
             OutputStream output = openOutput(destination)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
        }
    }

    @NonNull
    private OutputStream openOutput(@NonNull Uri uri) throws Exception {
        OutputStream output = null;
        try {
            output = resolver.openOutputStream(uri, "rwt");
        } catch (Exception ignored) {
        }
        if (output == null) {
            output = resolver.openOutputStream(uri, "w");
        }
        if (output == null) {
            throw new IllegalStateException("Restore point output stream is unavailable.");
        }
        return output;
    }

    @NonNull
    private List<RestorePoint> queryRestorePoints(@NonNull Uri folder) throws Exception {
        List<RestorePoint> result = new ArrayList<>();
        String folderId = DocumentsContract.getDocumentId(folder);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(folder, folderId);

        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_SIZE
        };

        try (Cursor cursor = resolver.query(children, projection, null, null, null)) {
            if (cursor != null) {
                int idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
                int nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
                int modifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED);
                int sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE);

                while (cursor.moveToNext()) {
                    String name = nameIndex >= 0 ? cursor.getString(nameIndex) : "";
                    if (name == null || !name.startsWith(RESTORE_PREFIX) || !name.endsWith(RESTORE_SUFFIX)) {
                        continue;
                    }
                    String documentId = idIndex >= 0 ? cursor.getString(idIndex) : null;
                    if (documentId == null) continue;
                    Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(folder, documentId);
                    long modified = modifiedIndex >= 0 ? cursor.getLong(modifiedIndex) : 0L;
                    long size = sizeIndex >= 0 ? cursor.getLong(sizeIndex) : 0L;
                    result.add(new RestorePoint(documentUri, name, modified, size));
                }
            }
        }

        result.sort((first, second) -> Long.compare(second.lastModified, first.lastModified));
        return result;
    }

    private void pruneRestorePoints() {
        List<RestorePoint> points = listRestorePoints();
        for (int i = MAX_RESTORE_POINTS; i < points.size(); i++) {
            deleteQuietly(points.get(i).uri);
        }
    }

    private void deleteQuietly(@Nullable Uri uri) {
        if (uri == null) return;
        try {
            DocumentsContract.deleteDocument(resolver, uri);
        } catch (Exception ignored) {
        }
    }

    @NonNull
    public static String formatSize(long bytes) {
        if (bytes < 1024L) return Math.max(0L, bytes) + " B";
        double kb = bytes / 1024d;
        if (kb < 1024d) return String.format(Locale.ENGLISH, "%.1f KB", kb);
        return String.format(Locale.ENGLISH, "%.1f MB", kb / 1024d);
    }

    @NonNull
    public static String formatTime(long millis) {
        if (millis <= 0L) return "Unknown time";
        return new SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.ENGLISH)
                .format(new Date(millis));
    }

    public static final class CaptureResult {
        public final boolean localRestorePointCreated;
        public final int statusHistoryAdded;

        CaptureResult(boolean localRestorePointCreated, int statusHistoryAdded) {
            this.localRestorePointCreated = localRestorePointCreated;
            this.statusHistoryAdded = statusHistoryAdded;
        }
    }

    public static final class RestorePoint {
        public final Uri uri;
        public final String name;
        public final long lastModified;
        public final long sizeBytes;

        RestorePoint(Uri uri, String name, long lastModified, long sizeBytes) {
            this.uri = uri;
            this.name = name;
            this.lastModified = lastModified;
            this.sizeBytes = sizeBytes;
        }
    }

    public static final class HistoryEntry {
        public final String type;
        public final long timeMillis;
        public final String backupId;
        public final int recordCount;
        public final long byteCount;

        HistoryEntry(String type, long timeMillis, String backupId, int recordCount, long byteCount) {
            this.type = type;
            this.timeMillis = timeMillis;
            this.backupId = backupId;
            this.recordCount = recordCount;
            this.byteCount = byteCount;
        }
    }
}
