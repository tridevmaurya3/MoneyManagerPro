package com.example.moneymanagerpro.cloud;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

import java.util.HashMap;
import java.util.Map;

/**
 * STEP 13 - encrypted Firestore transport for integration recovery state.
 *
 * Firestore structure:
 * users/{firebaseUid}/money_manager_pro_integration/latest
 *
 * Only encrypted ciphertext plus technical encryption metadata is uploaded.
 * The recovery passphrase is never sent to Firestore. It is read from the same
 * Android-Keystore protected vault used by MoneyManagerPro's encrypted finance
 * cloud backup.
 */
public final class TridevIntegrationCloudManager {

    private static final String COLLECTION_USERS = "users";
    private static final String COLLECTION_INTEGRATION = "money_manager_pro_integration";
    private static final String DOCUMENT_LATEST = "latest";
    private static final String APP_PACKAGE = "com.example.moneymanagerpro";
    private static final String STATUS_COMPLETE = "complete";
    private static final int CLOUD_SCHEMA_VERSION = 1;
    private static final int MAX_CIPHERTEXT_BASE64_LENGTH = 800_000;

    private static final String LOCAL_PREFS = "tridev_integration_cloud_status_v1";
    private static final String KEY_LAST_SYNC = "last_sync";
    private static final String KEY_LAST_RESTORE = "last_restore";
    private static final String KEY_LAST_ERROR = "last_error";

    public interface StatusCallback {
        void onLoaded(@NonNull CloudStatus status);
    }

    public interface SyncCallback {
        void onSuccess(@NonNull CloudStatus status);
        void onError(@NonNull Exception exception);
    }

    public interface RestoreCallback {
        void onSuccess(
                @NonNull TridevIntegrationCloudSnapshot.RestoreResult result,
                @NonNull CloudStatus status);
        void onError(@NonNull Exception exception);
    }

    public static final class CloudStatus {
        public final boolean signedIn;
        public final boolean emailVerified;
        public final boolean passphraseReady;
        public final boolean remoteSnapshotExists;
        public final long remoteCreatedAt;
        public final int remoteEventCount;
        public final int remoteMappingCount;
        public final boolean remoteTruncated;
        public final long lastLocalSyncAt;
        public final long lastLocalRestoreAt;
        public final String lastError;
        public final String message;

        private CloudStatus(
                boolean signedIn,
                boolean emailVerified,
                boolean passphraseReady,
                boolean remoteSnapshotExists,
                long remoteCreatedAt,
                int remoteEventCount,
                int remoteMappingCount,
                boolean remoteTruncated,
                long lastLocalSyncAt,
                long lastLocalRestoreAt,
                String lastError,
                String message) {
            this.signedIn = signedIn;
            this.emailVerified = emailVerified;
            this.passphraseReady = passphraseReady;
            this.remoteSnapshotExists = remoteSnapshotExists;
            this.remoteCreatedAt = remoteCreatedAt;
            this.remoteEventCount = remoteEventCount;
            this.remoteMappingCount = remoteMappingCount;
            this.remoteTruncated = remoteTruncated;
            this.lastLocalSyncAt = lastLocalSyncAt;
            this.lastLocalRestoreAt = lastLocalRestoreAt;
            this.lastError = safe(lastError);
            this.message = safe(message);
        }
    }

    private final Context appContext;
    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;
    private final CloudBackupKeyVault keyVault;
    private final SharedPreferences localStatus;

    public TridevIntegrationCloudManager(@NonNull Context context) {
        appContext = context.getApplicationContext();
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        keyVault = new CloudBackupKeyVault(appContext);
        localStatus = appContext.getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE);
    }

    /** Loads account readiness plus latest encrypted remote snapshot metadata. */
    public void loadStatus(@NonNull StatusCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onLoaded(localOnlyStatus(
                    false,
                    false,
                    false,
                    "Sign in to Firebase before using integration cloud recovery."));
            return;
        }

        boolean verified = user.isEmailVerified();
        boolean passphraseReady = keyVault.hasSavedPassphrase(user.getUid());
        if (!verified) {
            callback.onLoaded(localOnlyStatus(
                    true,
                    false,
                    passphraseReady,
                    "Verify the Firebase email before using integration cloud recovery."));
            return;
        }

        latestReference(user.getUid())
                .get(Source.DEFAULT)
                .addOnSuccessListener(snapshot -> callback.onLoaded(
                        statusFromDocument(user, passphraseReady, snapshot, null)))
                .addOnFailureListener(error -> callback.onLoaded(
                        statusFromDocument(user, passphraseReady, null, error)));
    }

    /** Creates and uploads one encrypted recovery snapshot. */
    public void syncNow(@NonNull SyncCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        Exception accountError = validateReadyUser(user);
        if (accountError != null) {
            saveError(accountError.getMessage());
            callback.onError(accountError);
            return;
        }
        if (!keyVault.hasSavedPassphrase(user.getUid())) {
            Exception error = new IllegalStateException(
                    "Cloud recovery passphrase is not saved on this device. Open Backup and unlock cloud backup first.");
            saveError(error.getMessage());
            callback.onError(error);
            return;
        }

        final TridevIntegrationCloudSnapshot.BuiltSnapshot snapshot;
        char[] passphrase = null;
        byte[] plaintext = null;
        try {
            snapshot = TridevIntegrationCloudSnapshot.build(appContext);
            plaintext = snapshot.plaintextBytes;
            String backupId = createBackupId(snapshot.createdAt);
            String associatedData = CloudBackupEncryption.createAssociatedData(
                    user.getUid(),
                    backupId);
            passphrase = keyVault.readPassphrase(user.getUid());
            CloudBackupEncryption.EncryptedPayload encrypted = CloudBackupEncryption.encrypt(
                    plaintext,
                    passphrase,
                    associatedData);

            String ciphertext = encrypted.getEncryptedPayloadBase64();
            if (ciphertext.length() <= 0 || ciphertext.length() > MAX_CIPHERTEXT_BASE64_LENGTH) {
                throw new IllegalStateException("Encrypted integration recovery snapshot exceeds Firestore-safe size.");
            }

            Map<String, Object> document = new HashMap<>();
            document.put("schemaVersion", CLOUD_SCHEMA_VERSION);
            document.put("ownerUid", user.getUid());
            document.put("appPackage", APP_PACKAGE);
            document.put("status", STATUS_COMPLETE);
            document.put("backupId", backupId);
            document.put("createdAt", snapshot.createdAt);
            document.put("eventCount", snapshot.eventCount);
            document.put("mappingCount", snapshot.mappingCount);
            document.put("truncated", snapshot.truncated);
            document.put("encryptionVersion", encrypted.getEncryptionVersion());
            document.put("cipher", encrypted.getCipherTransformation());
            document.put("kdf", encrypted.getKdfAlgorithm());
            document.put("prf", encrypted.getPrfAlgorithm());
            document.put("kdfIterations", encrypted.getKdfIterations());
            document.put("keyLengthBits", encrypted.getKeyLengthBits());
            document.put("gcmTagLengthBits", encrypted.getGcmTagLengthBits());
            document.put("salt", encrypted.getSaltBase64());
            document.put("iv", encrypted.getInitializationVectorBase64());
            document.put("ciphertext", ciphertext);
            document.put("originalPlaintextBytes", encrypted.getOriginalPlaintextBytes());
            document.put("serverUpdatedAt", FieldValue.serverTimestamp());

            final long createdAt = snapshot.createdAt;
            latestReference(user.getUid())
                    .set(document)
                    .addOnSuccessListener(ignored -> {
                        saveSuccessSync(createdAt);
                        loadStatus(callback::onSuccess);
                    })
                    .addOnFailureListener(error -> {
                        saveError(error.getMessage());
                        callback.onError(error);
                    });
        } catch (Exception error) {
            saveError(error.getMessage());
            callback.onError(asException(error));
        } finally {
            CloudBackupEncryption.clearSensitiveCharacters(passphrase);
            CloudBackupEncryption.clearSensitiveBytes(plaintext);
        }
    }

    /**
     * Downloads, authenticates, decrypts and merges the latest integration state.
     * Restored events are never force-posted; they return to queue/review safety.
     */
    public void restoreNow(@NonNull RestoreCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        Exception accountError = validateReadyUser(user);
        if (accountError != null) {
            saveError(accountError.getMessage());
            callback.onError(accountError);
            return;
        }
        if (!keyVault.hasSavedPassphrase(user.getUid())) {
            Exception error = new IllegalStateException(
                    "Recovery passphrase is not available on this device. Unlock encrypted cloud backup first.");
            saveError(error.getMessage());
            callback.onError(error);
            return;
        }

        latestReference(user.getUid())
                .get(Source.SERVER)
                .addOnSuccessListener(document -> {
                    if (!document.exists()) {
                        Exception error = new IllegalStateException("No integration cloud recovery snapshot exists yet.");
                        saveError(error.getMessage());
                        callback.onError(error);
                        return;
                    }

                    // Firestore listeners are commonly delivered on the Android main thread.
                    // PBKDF2 (210k rounds), AES-GCM and queue restore must never block it.
                    new Thread(
                            () -> restoreDocument(user, document, callback),
                            "IntegrationCloudRestoreCrypto")
                            .start();
                })
                .addOnFailureListener(error -> {
                    saveError(error.getMessage());
                    callback.onError(error);
                });
    }

    private void restoreDocument(
            FirebaseUser user,
            DocumentSnapshot document,
            RestoreCallback callback) {
        char[] passphrase = null;
        byte[] plaintext = null;
        try {
            validateRemoteDocument(user, document);
            String backupId = requireText(document, "backupId");
            String associatedData = CloudBackupEncryption.createAssociatedData(
                    user.getUid(),
                    backupId);

            CloudBackupEncryption.EncryptedPayload encrypted = CloudBackupEncryption.fromBase64(
                    intField(document, "encryptionVersion"),
                    requireText(document, "cipher"),
                    requireText(document, "kdf"),
                    requireText(document, "prf"),
                    intField(document, "kdfIterations"),
                    intField(document, "keyLengthBits"),
                    intField(document, "gcmTagLengthBits"),
                    requireText(document, "salt"),
                    requireText(document, "iv"),
                    requireText(document, "ciphertext"),
                    intField(document, "originalPlaintextBytes"));

            passphrase = keyVault.readPassphrase(user.getUid());
            plaintext = CloudBackupEncryption.decrypt(
                    encrypted,
                    passphrase,
                    associatedData);

            TridevIntegrationCloudSnapshot.RestoreResult result =
                    TridevIntegrationCloudSnapshot.restore(appContext, plaintext);
            long restoredAt = System.currentTimeMillis();
            saveSuccessRestore(restoredAt);
            loadStatus(status -> callback.onSuccess(result, status));
        } catch (Exception error) {
            saveError(error.getMessage());
            callback.onError(asException(error));
        } finally {
            CloudBackupEncryption.clearSensitiveCharacters(passphrase);
            CloudBackupEncryption.clearSensitiveBytes(plaintext);
        }
    }

    private void validateRemoteDocument(FirebaseUser user, DocumentSnapshot document) {
        if (intField(document, "schemaVersion") != CLOUD_SCHEMA_VERSION) {
            throw new IllegalStateException("Unsupported integration cloud schema version.");
        }
        if (!user.getUid().equals(requireText(document, "ownerUid"))) {
            throw new IllegalStateException("Integration cloud snapshot owner verification failed.");
        }
        if (!APP_PACKAGE.equals(requireText(document, "appPackage"))) {
            throw new IllegalStateException("Integration cloud snapshot belongs to another app.");
        }
        if (!STATUS_COMPLETE.equals(requireText(document, "status"))) {
            throw new IllegalStateException("Integration cloud snapshot is incomplete.");
        }
        String ciphertext = requireText(document, "ciphertext");
        if (ciphertext.length() > MAX_CIPHERTEXT_BASE64_LENGTH) {
            throw new IllegalStateException("Integration cloud ciphertext exceeds the supported size.");
        }
    }

    private CloudStatus statusFromDocument(
            FirebaseUser user,
            boolean passphraseReady,
            @Nullable DocumentSnapshot document,
            @Nullable Exception loadError) {
        boolean exists = false;
        long createdAt = 0L;
        int events = 0;
        int mappings = 0;
        boolean truncated = false;
        String message;

        if (document != null && document.exists()) {
            try {
                validateRemoteDocument(user, document);
                exists = true;
                createdAt = longField(document, "createdAt");
                events = intField(document, "eventCount");
                mappings = intField(document, "mappingCount");
                Boolean truncatedField = document.getBoolean("truncated");
                truncated = truncatedField != null && truncatedField;
                message = passphraseReady
                        ? "Encrypted integration recovery is ready."
                        : "Encrypted snapshot exists. Unlock cloud backup passphrase on this device to restore it.";
            } catch (RuntimeException invalid) {
                message = "Remote integration snapshot exists but failed metadata validation.";
            }
        } else if (loadError != null) {
            message = "Cloud status is temporarily unavailable; local integration continues safely.";
        } else {
            message = passphraseReady
                    ? "Cloud recovery is ready for the first encrypted sync."
                    : "Unlock cloud backup in Backup settings to enable encrypted integration sync.";
        }

        return new CloudStatus(
                true,
                true,
                passphraseReady,
                exists,
                createdAt,
                events,
                mappings,
                truncated,
                localStatus.getLong(KEY_LAST_SYNC, 0L),
                localStatus.getLong(KEY_LAST_RESTORE, 0L),
                localStatus.getString(KEY_LAST_ERROR, ""),
                message);
    }

    private CloudStatus localOnlyStatus(
            boolean signedIn,
            boolean verified,
            boolean passphraseReady,
            String message) {
        return new CloudStatus(
                signedIn,
                verified,
                passphraseReady,
                false,
                0L,
                0,
                0,
                false,
                localStatus.getLong(KEY_LAST_SYNC, 0L),
                localStatus.getLong(KEY_LAST_RESTORE, 0L),
                localStatus.getString(KEY_LAST_ERROR, ""),
                message);
    }

    private Exception validateReadyUser(@Nullable FirebaseUser user) {
        if (user == null) {
            return new IllegalStateException("Sign in to Firebase before using integration cloud recovery.");
        }
        if (!user.isEmailVerified()) {
            return new IllegalStateException("Verify the Firebase email before using integration cloud recovery.");
        }
        return null;
    }

    private DocumentReference latestReference(String uid) {
        return firestore
                .collection(COLLECTION_USERS)
                .document(uid)
                .collection(COLLECTION_INTEGRATION)
                .document(DOCUMENT_LATEST);
    }

    private String createBackupId(long createdAt) {
        return "integration_state_v1_" + Math.max(1L, createdAt);
    }

    private void saveSuccessSync(long at) {
        localStatus.edit()
                .putLong(KEY_LAST_SYNC, at)
                .remove(KEY_LAST_ERROR)
                .apply();
    }

    private void saveSuccessRestore(long at) {
        localStatus.edit()
                .putLong(KEY_LAST_RESTORE, at)
                .remove(KEY_LAST_ERROR)
                .apply();
    }

    private void saveError(@Nullable String error) {
        String safeError = safe(error);
        if (safeError.length() > 240) safeError = safeError.substring(0, 240).trim();
        localStatus.edit().putString(KEY_LAST_ERROR, safeError).apply();
    }

    private static String requireText(DocumentSnapshot document, String field) {
        String value = document.getString(field);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Integration cloud field is missing: " + field);
        }
        return value.trim();
    }

    private static long longField(DocumentSnapshot document, String field) {
        Long value = document.getLong(field);
        return value == null ? 0L : value;
    }

    private static int intField(DocumentSnapshot document, String field) {
        long value = longField(document, field);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalStateException("Integration cloud numeric field is invalid: " + field);
        }
        return (int) value;
    }

    private static Exception asException(Throwable error) {
        return error instanceof Exception
                ? (Exception) error
                : new IllegalStateException("Integration cloud operation failed.", error);
    }

    private static String safe(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
