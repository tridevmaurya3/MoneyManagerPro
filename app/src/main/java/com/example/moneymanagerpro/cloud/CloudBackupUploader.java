package com.example.moneymanagerpro.cloud;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Uploads, reads and permanently deletes the latest encrypted
 * Money Manager Pro cloud backup.
 *
 * Firestore structure:
 *
 * users
 *   └── {firebaseUid}
 *       └── money_manager_pro_cloud_backups
 *           └── latest
 *               └── chunks
 *                   ├── chunk_000
 *                   ├── chunk_001
 *                   └── ...
 *
 * Security model:
 *
 * 1. Only email-verified Firebase users can use cloud backup.
 * 2. Payload owner UID must match the currently signed-in account.
 * 3. Only AES-256-GCM encrypted chunks are uploaded.
 * 4. Plain finance data and backup passphrase are never uploaded.
 * 5. Existing cloud backup is replaced using one atomic batch.
 * 6. Metadata is published in the same batch as encrypted chunks.
 * 7. Server data is used for status and replacement checks.
 * 8. Old stale chunks are deleted during replacement.
 */
public final class CloudBackupUploader {

    private static final String COLLECTION_USERS =
            "users";

    private static final String COLLECTION_CLOUD_BACKUPS =
            "money_manager_pro_cloud_backups";

    private static final String COLLECTION_CHUNKS =
            "chunks";

    private static final String DOCUMENT_LATEST_BACKUP =
            "latest";

    private static final String APP_PACKAGE =
            "com.example.moneymanagerpro";

    private static final String STATUS_COMPLETE =
            "complete";

    private static final String STATUS_CHUNK =
            "encrypted_chunk";

    /**
     * Firestore supports 500 writes in one batch.
     *
     * A lower internal limit leaves room for serverTimestamp field
     * transforms and future metadata fields.
     */
    private static final int MAX_SAFE_BATCH_OPERATIONS =
            450;

    private static final int MAX_BACKUP_ID_LENGTH =
            180;

    private final FirebaseFirestore firestore;

    public CloudBackupUploader() {
        firestore =
                FirebaseFirestore.getInstance();
    }

    /**
     * Uploads and atomically replaces the user's latest encrypted backup.
     */
    public void uploadLatestEncryptedBackup(
            @NonNull FirebaseUser firebaseUser,
            @NonNull EncryptedCloudBackupPayloadBuilder
                    .EncryptedCloudBackupPayload payload,
            @NonNull UploadCallback callback
    ) {
        Exception accountError =
                validateVerifiedUser(
                        firebaseUser
                );

        if (accountError != null) {
            callback.onError(
                    accountError
            );

            return;
        }

        if (!firebaseUser
                .getUid()
                .equals(
                        payload.getFirebaseUserId()
                )) {

            callback.onError(
                    new IllegalStateException(
                            "Encrypted backup owner does not match "
                                    + "the signed-in Firebase account."
                    )
            );

            return;
        }

        PreparedUpload preparedUpload;

        try {
            preparedUpload =
                    prepareUpload(
                            firebaseUser,
                            payload
                    );

        } catch (Exception exception) {
            callback.onError(
                    exception
            );

            return;
        }

        DocumentReference latestBackupReference =
                getLatestBackupReference(
                        firebaseUser.getUid()
                );

        CollectionReference chunksReference =
                latestBackupReference.collection(
                        COLLECTION_CHUNKS
                );

        /*
         * DEFAULT first asks the server and safely falls back to Firestore's
         * local cache when Google Play Services/DNS is temporarily resolving.
         * The following atomic batch still has to reach Firestore before its
         * completion task succeeds, so a cached read cannot create a false
         * "backup complete" result.
         */
        chunksReference
                .get(Source.DEFAULT)
                .addOnSuccessListener(
                        existingChunks ->
                                replaceLatestBackup(
                                        latestBackupReference,
                                        chunksReference,
                                        existingChunks,
                                        preparedUpload,
                                        callback
                                )
                )
                .addOnFailureListener(
                        callback::onError
                );
    }

    /**
     * Loads the latest encrypted backup metadata directly from Firestore.
     *
     * A null result means the account currently has no cloud backup.
     */
    public void loadLatestBackupMetadata(
            @NonNull FirebaseUser firebaseUser,
            @NonNull MetadataCallback callback
    ) {
        Exception accountError =
                validateSignedInUser(
                        firebaseUser
                );

        if (accountError != null) {
            callback.onError(
                    accountError
            );

            return;
        }

        getLatestBackupReference(
                firebaseUser.getUid()
        )
                .get(Source.SERVER)
                .addOnSuccessListener(
                        documentSnapshot -> {
                            if (!documentSnapshot.exists()) {
                                callback.onLoaded(null);
                                return;
                            }

                            try {
                                CloudBackupMetadata metadata =
                                        createMetadata(
                                                documentSnapshot
                                        );

                                if (!firebaseUser
                                        .getUid()
                                        .equals(
                                                metadata.getOwnerUserId()
                                        )) {

                                    callback.onError(
                                            new IllegalStateException(
                                                    "Cloud backup owner verification failed."
                                            )
                                    );

                                    return;
                                }

                                callback.onLoaded(
                                        metadata
                                );

                            } catch (Exception exception) {
                                callback.onError(
                                        exception
                                );
                            }
                        }
                )
                .addOnFailureListener(
                        callback::onError
                );
    }

    /**
     * Permanently deletes the latest cloud backup metadata and all of
     * its encrypted chunk documents.
     *
     * This does not delete the Firebase account.
     */
    public void deleteLatestBackup(
            @NonNull FirebaseUser firebaseUser,
            @NonNull DeleteCallback callback
    ) {
        Exception accountError =
                validateVerifiedUser(
                        firebaseUser
                );

        if (accountError != null) {
            callback.onError(
                    accountError
            );

            return;
        }

        DocumentReference latestBackupReference =
                getLatestBackupReference(
                        firebaseUser.getUid()
                );

        latestBackupReference
                .collection(
                        COLLECTION_CHUNKS
                )
                .get(Source.SERVER)
                .addOnSuccessListener(
                        chunkSnapshot ->
                                permanentlyDeleteBackupDocuments(
                                        latestBackupReference,
                                        chunkSnapshot,
                                        callback
                                )
                )
                .addOnFailureListener(
                        callback::onError
                );
    }

    /**
     * Returns true only when the server contains a completed,
     * client-side encrypted Money Manager Pro backup.
     */
    public void hasUsableEncryptedBackup(
            @NonNull FirebaseUser firebaseUser,
            @NonNull AvailabilityCallback callback
    ) {
        loadLatestBackupMetadata(
                firebaseUser,
                new MetadataCallback() {
                    @Override
                    public void onLoaded(
                            @Nullable CloudBackupMetadata metadata
                    ) {
                        callback.onResult(
                                metadata != null
                                        && metadata
                                        .isClientSideEncrypted()
                                        && metadata
                                        .isComplete()
                                        && metadata
                                        .getChunkCount() > 0
                        );
                    }

                    @Override
                    public void onError(
                            @NonNull Exception exception
                    ) {
                        callback.onError(
                                exception
                        );
                    }
                }
        );
    }

    @NonNull
    private PreparedUpload prepareUpload(
            @NonNull FirebaseUser firebaseUser,
            @NonNull EncryptedCloudBackupPayloadBuilder
                    .EncryptedCloudBackupPayload payload
    ) {
        validateBackupIdentity(
                payload.getBackupId(),
                payload.getCreatedAtMillis()
        );

        validateEncryptedPayload(
                payload
        );

        List<Map<String, Object>> chunkDocuments =
                new ArrayList<>(
                        payload.getChunkCount()
                );

        for (int chunkIndex = 0;
             chunkIndex < payload.getChunkCount();
             chunkIndex++) {

            String encryptedChunk =
                    payload.getEncryptedChunk(
                            chunkIndex
                    );

            chunkDocuments.add(
                    createChunkDocument(
                            firebaseUser,
                            payload,
                            chunkIndex,
                            encryptedChunk
                    )
            );
        }

        Map<String, Object> metadata =
                createMetadataDocument(
                        firebaseUser,
                        payload
                );

        return new PreparedUpload(
                payload.getBackupId(),
                payload.getCreatedAtMillis(),
                payload.getEncryptedByteCount(),
                payload.getCompressedByteCount(),
                payload.getEncryptedSha256(),
                chunkDocuments,
                metadata
        );
    }

    /**
     * Replaces old chunks and metadata in one Firestore batch.
     */
    private void replaceLatestBackup(
            @NonNull DocumentReference latestBackupReference,
            @NonNull CollectionReference chunksReference,
            @NonNull QuerySnapshot existingChunkSnapshot,
            @NonNull PreparedUpload preparedUpload,
            @NonNull UploadCallback callback
    ) {
        Set<String> newChunkDocumentIds =
                new HashSet<>();

        for (int chunkIndex = 0;
             chunkIndex
                     < preparedUpload
                     .chunkDocuments
                     .size();
             chunkIndex++) {

            newChunkDocumentIds.add(
                    createChunkDocumentId(
                            chunkIndex
                    )
            );
        }

        int staleChunkCount = 0;

        for (DocumentSnapshot existingChunk
                : existingChunkSnapshot
                .getDocuments()) {

            if (!newChunkDocumentIds.contains(
                    existingChunk.getId()
            )) {
                staleChunkCount++;
            }
        }

        /*
         * Batch operations:
         *
         * - One set for each new encrypted chunk
         * - One delete for each stale old chunk
         * - One set for latest metadata
         * - One allowance for serverTimestamp transform
         */
        int requiredOperations =
                preparedUpload
                        .chunkDocuments
                        .size()
                        + staleChunkCount
                        + 2;

        if (requiredOperations
                > MAX_SAFE_BATCH_OPERATIONS) {

            callback.onError(
                    new IllegalStateException(
                            "Cloud backup replacement requires "
                                    + requiredOperations
                                    + " operations, which exceeds the "
                                    + "safe Firestore batch limit."
                    )
            );

            return;
        }

        WriteBatch batch =
                firestore.batch();

        /*
         * Delete only old chunks that are not required by the new backup.
         *
         * Matching chunk IDs will be overwritten using set().
         */
        for (DocumentSnapshot existingChunk
                : existingChunkSnapshot
                .getDocuments()) {

            if (!newChunkDocumentIds.contains(
                    existingChunk.getId()
            )) {

                batch.delete(
                        existingChunk.getReference()
                );
            }
        }

        for (int chunkIndex = 0;
             chunkIndex
                     < preparedUpload
                     .chunkDocuments
                     .size();
             chunkIndex++) {

            DocumentReference chunkReference =
                    chunksReference.document(
                            createChunkDocumentId(
                                    chunkIndex
                            )
                    );

            batch.set(
                    chunkReference,
                    preparedUpload
                            .chunkDocuments
                            .get(chunkIndex)
            );
        }

        long clientUploadTime =
                System.currentTimeMillis();

        Map<String, Object> finalMetadata =
                new HashMap<>(
                        preparedUpload.metadata
                );

        finalMetadata.put(
                "uploaded_at",
                FieldValue.serverTimestamp()
        );

        finalMetadata.put(
                "uploaded_at_client",
                clientUploadTime
        );

        batch.set(
                latestBackupReference,
                finalMetadata
        );

        batch.commit()
                .addOnSuccessListener(
                        unused ->
                                callback.onSuccess(
                                        new UploadResult(
                                                preparedUpload.backupId,
                                                preparedUpload
                                                        .createdAtMillis,
                                                clientUploadTime,
                                                preparedUpload
                                                        .chunkDocuments
                                                        .size(),
                                                preparedUpload
                                                        .compressedByteCount,
                                                preparedUpload
                                                        .encryptedByteCount,
                                                preparedUpload
                                                        .encryptedSha256
                                        )
                                )
                )
                .addOnFailureListener(
                        callback::onError
                );
    }

    private void permanentlyDeleteBackupDocuments(
            @NonNull DocumentReference latestBackupReference,
            @NonNull QuerySnapshot chunkSnapshot,
            @NonNull DeleteCallback callback
    ) {
        /*
         * All chunks + latest metadata document.
         */
        int requiredOperations =
                chunkSnapshot.size() + 1;

        if (requiredOperations
                > MAX_SAFE_BATCH_OPERATIONS) {

            callback.onError(
                    new IllegalStateException(
                            "Cloud backup contains too many parts "
                                    + "to delete safely."
                    )
            );

            return;
        }

        WriteBatch batch =
                firestore.batch();

        for (DocumentSnapshot chunkDocument
                : chunkSnapshot.getDocuments()) {

            batch.delete(
                    chunkDocument.getReference()
            );
        }

        batch.delete(
                latestBackupReference
        );

        batch.commit()
                .addOnSuccessListener(
                        unused ->
                                callback.onSuccess()
                )
                .addOnFailureListener(
                        callback::onError
                );
    }

    @NonNull
    private Map<String, Object> createChunkDocument(
            @NonNull FirebaseUser firebaseUser,
            @NonNull EncryptedCloudBackupPayloadBuilder
                    .EncryptedCloudBackupPayload payload,
            int chunkIndex,
            @NonNull String encryptedChunk
    ) {
        Map<String, Object> chunkData =
                new HashMap<>();

        chunkData.put(
                "owner_uid",
                firebaseUser.getUid()
        );

        chunkData.put(
                "app_package",
                APP_PACKAGE
        );

        chunkData.put(
                "status",
                STATUS_CHUNK
        );

        chunkData.put(
                "backup_id",
                payload.getBackupId()
        );

        chunkData.put(
                "backup_created_at",
                payload.getCreatedAtMillis()
        );

        chunkData.put(
                "chunk_index",
                chunkIndex
        );

        chunkData.put(
                "chunk_count",
                payload.getChunkCount()
        );

        chunkData.put(
                "payload",
                encryptedChunk
        );

        chunkData.put(
                "payload_format",
                payload.getEncryptedPayloadFormat()
        );

        chunkData.put(
                "payload_format_version",
                payload.getEncryptedPayloadFormatVersion()
        );

        chunkData.put(
                "is_client_side_encrypted",
                true
        );

        chunkData.put(
                "encoding",
                payload.getEncodingType()
        );

        chunkData.put(
                "compression",
                payload.getCompressionType()
        );

        chunkData.put(
                "encryption_version",
                payload.getEncryptionVersion()
        );

        chunkData.put(
                "encrypted_checksum_sha256",
                payload.getEncryptedSha256()
        );

        return chunkData;
    }

    @NonNull
    private Map<String, Object> createMetadataDocument(
            @NonNull FirebaseUser firebaseUser,
            @NonNull EncryptedCloudBackupPayloadBuilder
                    .EncryptedCloudBackupPayload payload
    ) {
        CloudBackupPayloadBuilder.RecordCounts counts =
                payload.getRecordCounts();

        Map<String, Object> metadata =
                new HashMap<>();

        metadata.put(
                "owner_uid",
                firebaseUser.getUid()
        );

        metadata.put(
                "app_package",
                APP_PACKAGE
        );

        metadata.put(
                "status",
                STATUS_COMPLETE
        );

        metadata.put(
                "backup_id",
                payload.getBackupId()
        );

        metadata.put(
                "backup_created_at",
                payload.getCreatedAtMillis()
        );

        metadata.put(
                "backup_created_at_utc",
                payload.getCreatedAtUtc()
        );

        metadata.put(
                "source_payload_type",
                payload.getSourcePayloadType()
        );

        metadata.put(
                "source_payload_version",
                payload.getSourcePayloadVersion()
        );

        metadata.put(
                "payload_format",
                payload.getEncryptedPayloadFormat()
        );

        metadata.put(
                "payload_format_version",
                payload.getEncryptedPayloadFormatVersion()
        );

        metadata.put(
                "database_version",
                payload.getDatabaseVersion()
        );

        metadata.put(
                "app_version_name",
                payload.getAppVersionName()
        );

        metadata.put(
                "app_version_code",
                payload.getAppVersionCode()
        );

        metadata.put(
                "is_client_side_encrypted",
                true
        );

        metadata.put(
                "compression",
                payload.getCompressionType()
        );

        metadata.put(
                "encoding",
                payload.getEncodingType()
        );

        metadata.put(
                "associated_data_scheme",
                payload.getAssociatedDataScheme()
        );

        metadata.put(
                "encryption_version",
                payload.getEncryptionVersion()
        );

        metadata.put(
                "cipher_transformation",
                payload.getCipherTransformation()
        );

        metadata.put(
                "kdf_algorithm",
                payload.getKdfAlgorithm()
        );

        metadata.put(
                "prf_algorithm",
                payload.getPrfAlgorithm()
        );

        metadata.put(
                "kdf_iterations",
                payload.getKdfIterations()
        );

        metadata.put(
                "key_length_bits",
                payload.getKeyLengthBits()
        );

        metadata.put(
                "gcm_tag_length_bits",
                payload.getGcmTagLengthBits()
        );

        metadata.put(
                "salt_base64",
                payload.getSaltBase64()
        );

        metadata.put(
                "initialization_vector_base64",
                payload.getInitializationVectorBase64()
        );

        metadata.put(
                "hash_algorithm",
                payload.getHashAlgorithm()
        );

        metadata.put(
                "uncompressed_checksum_sha256",
                payload.getUncompressedSha256()
        );

        metadata.put(
                "compressed_checksum_sha256",
                payload.getCompressedSha256()
        );

        metadata.put(
                "encrypted_checksum_sha256",
                payload.getEncryptedSha256()
        );

        /*
         * Common checksum always represents uploaded ciphertext.
         */
        metadata.put(
                "checksum_sha256",
                payload.getEncryptedSha256()
        );

        metadata.put(
                "uncompressed_bytes",
                payload.getUncompressedByteCount()
        );

        metadata.put(
                "compressed_bytes",
                payload.getCompressedByteCount()
        );

        metadata.put(
                "encrypted_bytes",
                payload.getEncryptedByteCount()
        );

        metadata.put(
                "encoded_characters",
                payload.getEncodedCharacterCount()
        );

        metadata.put(
                "chunk_count",
                payload.getChunkCount()
        );

        metadata.put(
                "estimated_document_count",
                payload.getEstimatedFirestoreDocumentCount()
        );

        metadata.put(
                "transaction_count",
                counts.getTransactions()
        );

        metadata.put(
                "expense_item_count",
                counts.getExpenseItems()
        );

        metadata.put(
                "category_count",
                counts.getCategories()
        );

        metadata.put(
                "account_count",
                counts.getAccounts()
        );

        metadata.put(
                "goal_count",
                counts.getGoals()
        );

        metadata.put(
                "recurring_transaction_count",
                counts.getRecurringTransactions()
        );

        metadata.put(
                "budget_count",
                counts.getBudgets()
        );

        metadata.put(
                "loan_count",
                counts.getLoans()
        );

        metadata.put(
                "loan_payment_count",
                counts.getLoanPayments()
        );

        metadata.put(
                "subscription_count",
                counts.getSubscriptions()
        );

        metadata.put(
                "credit_card_count",
                counts.getCreditCards()
        );

        metadata.put(
                "credit_card_payment_count",
                counts.getCreditCardPayments()
        );

        metadata.put(
                "investment_count",
                counts.getInvestments()
        );

        metadata.put(
                "total_record_count",
                counts.getTotalRecords()
        );

        return metadata;
    }

    private void validateEncryptedPayload(
            @NonNull EncryptedCloudBackupPayloadBuilder
                    .EncryptedCloudBackupPayload payload
    ) {
        if (!EncryptedCloudBackupPayloadBuilder
                .ENCRYPTED_PAYLOAD_FORMAT
                .equals(
                        payload.getEncryptedPayloadFormat()
                )) {

            throw new IllegalArgumentException(
                    "Unsupported encrypted cloud backup format."
            );
        }

        if (payload.getEncryptedPayloadFormatVersion()
                != EncryptedCloudBackupPayloadBuilder
                .ENCRYPTED_PAYLOAD_FORMAT_VERSION) {

            throw new IllegalArgumentException(
                    "Unsupported encrypted cloud backup format version."
            );
        }

        if (payload.getChunkCount() <= 0
                || payload.getChunkCount()
                > EncryptedCloudBackupPayloadBuilder
                .MAX_CHUNK_COUNT) {

            throw new IllegalArgumentException(
                    "Encrypted cloud backup chunk count is invalid."
            );
        }

        if (payload.getEncryptedByteCount() <= 0
                || payload.getCompressedByteCount() <= 0
                || payload.getUncompressedByteCount() <= 0) {

            throw new IllegalArgumentException(
                    "Encrypted cloud backup size metadata is invalid."
            );
        }

        if (payload.getEncodedCharacterCount() <= 0) {
            throw new IllegalArgumentException(
                    "Encrypted cloud backup encoded size is invalid."
            );
        }

        validateSha256(
                payload.getUncompressedSha256(),
                "uncompressed checksum"
        );

        validateSha256(
                payload.getCompressedSha256(),
                "compressed checksum"
        );

        validateSha256(
                payload.getEncryptedSha256(),
                "encrypted checksum"
        );

        long actualEncodedCharacters = 0L;

        List<String> chunks =
                payload.getEncryptedChunks();

        if (chunks.size()
                != payload.getChunkCount()) {

            throw new IllegalArgumentException(
                    "Encrypted cloud backup chunk count "
                            + "does not match its metadata."
            );
        }

        for (String chunk : chunks) {
            if (chunk == null
                    || chunk.isEmpty()) {

                throw new IllegalArgumentException(
                        "Encrypted cloud backup contains an empty chunk."
                );
            }

            if (chunk.length()
                    > EncryptedCloudBackupPayloadBuilder
                    .CHUNK_CHARACTER_LIMIT) {

                throw new IllegalArgumentException(
                        "Encrypted cloud backup chunk exceeds "
                                + "the supported size."
                );
            }

            actualEncodedCharacters +=
                    chunk.length();
        }

        if (actualEncodedCharacters
                != payload.getEncodedCharacterCount()) {

            throw new IllegalArgumentException(
                    "Encrypted cloud backup encoded size "
                            + "does not match its metadata."
            );
        }
    }

    private void validateBackupIdentity(
            @NonNull String backupId,
            long createdAtMillis
    ) {
        String cleanBackupId =
                backupId.trim();

        if (cleanBackupId.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cloud backup ID is unavailable."
            );
        }

        if (cleanBackupId.length()
                > MAX_BACKUP_ID_LENGTH) {

            throw new IllegalArgumentException(
                    "Cloud backup ID exceeds the supported length."
            );
        }

        if (!cleanBackupId.matches(
                "[A-Za-z0-9_-]+"
        )) {

            throw new IllegalArgumentException(
                    "Cloud backup ID contains unsupported characters."
            );
        }

        if (createdAtMillis <= 0L) {
            throw new IllegalArgumentException(
                    "Cloud backup creation time is invalid."
            );
        }
    }

    private void validateSha256(
            @NonNull String checksum,
            @NonNull String description
    ) {
        if (!checksum.matches(
                "[0-9a-fA-F]{64}"
        )) {

            throw new IllegalArgumentException(
                    "Cloud backup "
                            + description
                            + " is invalid."
            );
        }
    }

    @Nullable
    private Exception validateVerifiedUser(
            @NonNull FirebaseUser firebaseUser
    ) {
        Exception signedInError =
                validateSignedInUser(
                        firebaseUser
                );

        if (signedInError != null) {
            return signedInError;
        }

        if (!firebaseUser.isEmailVerified()) {
            return new IllegalStateException(
                    "Cloud backup requires a verified email account."
            );
        }

        return null;
    }

    @Nullable
    private Exception validateSignedInUser(
            @NonNull FirebaseUser firebaseUser
    ) {
        String userId =
                firebaseUser
                        .getUid()
                        .trim();

        if (userId.isEmpty()) {
            return new IllegalStateException(
                    "Firebase user ID is unavailable."
            );
        }

        return null;
    }

    @NonNull
    private DocumentReference getLatestBackupReference(
            @NonNull String firebaseUserId
    ) {
        return firestore
                .collection(
                        COLLECTION_USERS
                )
                .document(
                        firebaseUserId
                )
                .collection(
                        COLLECTION_CLOUD_BACKUPS
                )
                .document(
                        DOCUMENT_LATEST_BACKUP
                );
    }

    @NonNull
    private String createChunkDocumentId(
            int chunkIndex
    ) {
        return String.format(
                Locale.US,
                "chunk_%03d",
                chunkIndex
        );
    }

    @NonNull
    private CloudBackupMetadata createMetadata(
            @NonNull DocumentSnapshot snapshot
    ) {
        String ownerUserId =
                requireString(
                        snapshot,
                        "owner_uid"
                );

        String backupId =
                requireString(
                        snapshot,
                        "backup_id"
                );

        String status =
                requireString(
                        snapshot,
                        "status"
                );

        String appPackage =
                requireString(
                        snapshot,
                        "app_package"
                );

        if (!APP_PACKAGE.equals(
                appPackage
        )) {
            throw new IllegalStateException(
                    "Cloud backup belongs to another application."
            );
        }

        boolean clientSideEncrypted =
                getBoolean(
                        snapshot,
                        "is_client_side_encrypted",
                        false
                );

        String payloadFormat =
                getString(
                        snapshot,
                        "payload_format",
                        ""
                );

        int payloadFormatVersion =
                getInt(
                        snapshot,
                        "payload_format_version",
                        0
                );

        long backupCreatedAt =
                getLong(
                        snapshot,
                        "backup_created_at",
                        0L
                );

        long uploadedAtClient =
                getLong(
                        snapshot,
                        "uploaded_at_client",
                        0L
                );

        int chunkCount =
                getInt(
                        snapshot,
                        "chunk_count",
                        0
                );

        int databaseVersion =
                getInt(
                        snapshot,
                        "database_version",
                        0
                );

        String appVersionName =
                getString(
                        snapshot,
                        "app_version_name",
                        ""
                );

        long appVersionCode =
                getLong(
                        snapshot,
                        "app_version_code",
                        0L
                );

        int uncompressedBytes =
                getInt(
                        snapshot,
                        "uncompressed_bytes",
                        0
                );

        int compressedBytes =
                getInt(
                        snapshot,
                        "compressed_bytes",
                        0
                );

        int encryptedBytes =
                getInt(
                        snapshot,
                        "encrypted_bytes",
                        0
                );

        int totalRecordCount =
                getInt(
                        snapshot,
                        "total_record_count",
                        0
                );

        String encryptedSha256 =
                getString(
                        snapshot,
                        "encrypted_checksum_sha256",
                        ""
                );

        return new CloudBackupMetadata(
                ownerUserId,
                backupId,
                status,
                payloadFormat,
                payloadFormatVersion,
                clientSideEncrypted,
                backupCreatedAt,
                uploadedAtClient,
                chunkCount,
                databaseVersion,
                appVersionName,
                appVersionCode,
                uncompressedBytes,
                compressedBytes,
                encryptedBytes,
                encryptedSha256,

                getInt(
                        snapshot,
                        "transaction_count",
                        0
                ),

                getInt(
                        snapshot,
                        "expense_item_count",
                        0
                ),

                getInt(
                        snapshot,
                        "category_count",
                        0
                ),

                getInt(
                        snapshot,
                        "account_count",
                        0
                ),

                getInt(
                        snapshot,
                        "goal_count",
                        0
                ),

                getInt(
                        snapshot,
                        "recurring_transaction_count",
                        0
                ),

                getInt(
                        snapshot,
                        "budget_count",
                        0
                ),

                getInt(
                        snapshot,
                        "loan_count",
                        0
                ),

                getInt(
                        snapshot,
                        "loan_payment_count",
                        0
                ),

                getInt(
                        snapshot,
                        "subscription_count",
                        0
                ),

                getInt(
                        snapshot,
                        "credit_card_count",
                        0
                ),

                getInt(
                        snapshot,
                        "credit_card_payment_count",
                        0
                ),

                getInt(
                        snapshot,
                        "investment_count",
                        0
                ),

                totalRecordCount
        );
    }

    @NonNull
    private String requireString(
            @NonNull DocumentSnapshot snapshot,
            @NonNull String field
    ) {
        String value =
                snapshot.getString(
                        field
                );

        if (value == null
                || value.trim().isEmpty()) {

            throw new IllegalStateException(
                    "Cloud backup metadata field \""
                            + field
                            + "\" is missing."
            );
        }

        return value.trim();
    }

    @NonNull
    private String getString(
            @NonNull DocumentSnapshot snapshot,
            @NonNull String field,
            @NonNull String fallback
    ) {
        String value =
                snapshot.getString(
                        field
                );

        return value == null
                ? fallback
                : value.trim();
    }

    private long getLong(
            @NonNull DocumentSnapshot snapshot,
            @NonNull String field,
            long fallback
    ) {
        Long value =
                snapshot.getLong(
                        field
                );

        return value == null
                ? fallback
                : value;
    }

    private int getInt(
            @NonNull DocumentSnapshot snapshot,
            @NonNull String field,
            int fallback
    ) {
        long value =
                getLong(
                        snapshot,
                        field,
                        fallback
                );

        if (value < Integer.MIN_VALUE
                || value > Integer.MAX_VALUE) {

            return fallback;
        }

        return (int) value;
    }

    private boolean getBoolean(
            @NonNull DocumentSnapshot snapshot,
            @NonNull String field,
            boolean fallback
    ) {
        Boolean value =
                snapshot.getBoolean(
                        field
                );

        return value == null
                ? fallback
                : value;
    }

    private static final class PreparedUpload {

        private final String backupId;

        private final long createdAtMillis;

        private final int encryptedByteCount;

        private final int compressedByteCount;

        private final String encryptedSha256;

        private final List<Map<String, Object>>
                chunkDocuments;

        private final Map<String, Object> metadata;

        private PreparedUpload(
                @NonNull String backupId,
                long createdAtMillis,
                int encryptedByteCount,
                int compressedByteCount,
                @NonNull String encryptedSha256,
                @NonNull List<Map<String, Object>>
                        chunkDocuments,
                @NonNull Map<String, Object> metadata
        ) {
            this.backupId =
                    backupId;

            this.createdAtMillis =
                    createdAtMillis;

            this.encryptedByteCount =
                    encryptedByteCount;

            this.compressedByteCount =
                    compressedByteCount;

            this.encryptedSha256 =
                    encryptedSha256;

            this.chunkDocuments =
                    Collections.unmodifiableList(
                            new ArrayList<>(
                                    chunkDocuments
                            )
                    );

            this.metadata =
                    Collections.unmodifiableMap(
                            new HashMap<>(
                                    metadata
                            )
                    );
        }
    }

    public static final class UploadResult {

        private final String backupId;

        private final long backupCreatedAt;

        private final long uploadedAtClient;

        private final int chunkCount;

        private final int compressedByteCount;

        private final int encryptedByteCount;

        private final String encryptedSha256;

        private UploadResult(
                @NonNull String backupId,
                long backupCreatedAt,
                long uploadedAtClient,
                int chunkCount,
                int compressedByteCount,
                int encryptedByteCount,
                @NonNull String encryptedSha256
        ) {
            this.backupId =
                    backupId;

            this.backupCreatedAt =
                    backupCreatedAt;

            this.uploadedAtClient =
                    uploadedAtClient;

            this.chunkCount =
                    chunkCount;

            this.compressedByteCount =
                    compressedByteCount;

            this.encryptedByteCount =
                    encryptedByteCount;

            this.encryptedSha256 =
                    encryptedSha256;
        }

        @NonNull
        public String getBackupId() {
            return backupId;
        }

        public long getBackupCreatedAt() {
            return backupCreatedAt;
        }

        public long getUploadedAtClient() {
            return uploadedAtClient;
        }

        public int getChunkCount() {
            return chunkCount;
        }

        public int getCompressedByteCount() {
            return compressedByteCount;
        }

        public int getEncryptedByteCount() {
            return encryptedByteCount;
        }

        @NonNull
        public String getEncryptedSha256() {
            return encryptedSha256;
        }
    }

    public static final class CloudBackupMetadata {

        private final String ownerUserId;

        private final String backupId;

        private final String status;

        private final String payloadFormat;

        private final int payloadFormatVersion;

        private final boolean clientSideEncrypted;

        private final long backupCreatedAt;

        private final long uploadedAtClient;

        private final int chunkCount;

        private final int databaseVersion;

        private final String appVersionName;

        private final long appVersionCode;

        private final int uncompressedByteCount;

        private final int compressedByteCount;

        private final int encryptedByteCount;

        private final String encryptedSha256;

        private final int transactionCount;

        private final int expenseItemCount;

        private final int categoryCount;

        private final int accountCount;

        private final int goalCount;

        private final int recurringTransactionCount;

        private final int budgetCount;

        private final int loanCount;

        private final int loanPaymentCount;

        private final int subscriptionCount;

        private final int creditCardCount;

        private final int creditCardPaymentCount;

        private final int investmentCount;

        private final int totalRecordCount;

        private CloudBackupMetadata(
                @NonNull String ownerUserId,
                @NonNull String backupId,
                @NonNull String status,
                @NonNull String payloadFormat,
                int payloadFormatVersion,
                boolean clientSideEncrypted,
                long backupCreatedAt,
                long uploadedAtClient,
                int chunkCount,
                int databaseVersion,
                @NonNull String appVersionName,
                long appVersionCode,
                int uncompressedByteCount,
                int compressedByteCount,
                int encryptedByteCount,
                @NonNull String encryptedSha256,
                int transactionCount,
                int expenseItemCount,
                int categoryCount,
                int accountCount,
                int goalCount,
                int recurringTransactionCount,
                int budgetCount,
                int loanCount,
                int loanPaymentCount,
                int subscriptionCount,
                int creditCardCount,
                int creditCardPaymentCount,
                int investmentCount,
                int totalRecordCount
        ) {
            this.ownerUserId =
                    ownerUserId;

            this.backupId =
                    backupId;

            this.status =
                    status;

            this.payloadFormat =
                    payloadFormat;

            this.payloadFormatVersion =
                    payloadFormatVersion;

            this.clientSideEncrypted =
                    clientSideEncrypted;

            this.backupCreatedAt =
                    backupCreatedAt;

            this.uploadedAtClient =
                    uploadedAtClient;

            this.chunkCount =
                    chunkCount;

            this.databaseVersion =
                    databaseVersion;

            this.appVersionName =
                    appVersionName;

            this.appVersionCode =
                    appVersionCode;

            this.uncompressedByteCount =
                    uncompressedByteCount;

            this.compressedByteCount =
                    compressedByteCount;

            this.encryptedByteCount =
                    encryptedByteCount;

            this.encryptedSha256 =
                    encryptedSha256;

            this.transactionCount =
                    transactionCount;

            this.expenseItemCount =
                    expenseItemCount;

            this.categoryCount =
                    categoryCount;

            this.accountCount =
                    accountCount;

            this.goalCount =
                    goalCount;

            this.recurringTransactionCount =
                    recurringTransactionCount;

            this.budgetCount =
                    budgetCount;

            this.loanCount =
                    loanCount;

            this.loanPaymentCount =
                    loanPaymentCount;

            this.subscriptionCount =
                    subscriptionCount;

            this.creditCardCount =
                    creditCardCount;

            this.creditCardPaymentCount =
                    creditCardPaymentCount;

            this.investmentCount =
                    investmentCount;

            this.totalRecordCount =
                    totalRecordCount;
        }

        @NonNull
        public String getOwnerUserId() {
            return ownerUserId;
        }

        @NonNull
        public String getBackupId() {
            return backupId;
        }

        @NonNull
        public String getStatus() {
            return status;
        }

        public boolean isComplete() {
            return STATUS_COMPLETE.equalsIgnoreCase(
                    status
            );
        }

        @NonNull
        public String getPayloadFormat() {
            return payloadFormat;
        }

        public int getPayloadFormatVersion() {
            return payloadFormatVersion;
        }

        public boolean isClientSideEncrypted() {
            return clientSideEncrypted;
        }

        public long getBackupCreatedAt() {
            return backupCreatedAt;
        }

        public long getUploadedAtClient() {
            return uploadedAtClient;
        }

        public int getChunkCount() {
            return chunkCount;
        }

        public int getDatabaseVersion() {
            return databaseVersion;
        }

        @NonNull
        public String getAppVersionName() {
            return appVersionName;
        }

        public long getAppVersionCode() {
            return appVersionCode;
        }

        public int getUncompressedByteCount() {
            return uncompressedByteCount;
        }

        public int getCompressedByteCount() {
            return compressedByteCount;
        }

        public int getEncryptedByteCount() {
            return encryptedByteCount;
        }

        @NonNull
        public String getEncryptedSha256() {
            return encryptedSha256;
        }

        public int getTransactionCount() {
            return transactionCount;
        }

        public int getExpenseItemCount() {
            return expenseItemCount;
        }

        public int getCategoryCount() {
            return categoryCount;
        }

        public int getAccountCount() {
            return accountCount;
        }

        public int getGoalCount() {
            return goalCount;
        }

        public int getRecurringTransactionCount() {
            return recurringTransactionCount;
        }

        public int getBudgetCount() {
            return budgetCount;
        }

        public int getLoanCount() {
            return loanCount;
        }

        public int getLoanPaymentCount() {
            return loanPaymentCount;
        }

        public int getSubscriptionCount() {
            return subscriptionCount;
        }

        public int getCreditCardCount() {
            return creditCardCount;
        }

        public int getCreditCardPaymentCount() {
            return creditCardPaymentCount;
        }

        public int getInvestmentCount() {
            return investmentCount;
        }

        public int getTotalRecordCount() {
            return totalRecordCount;
        }
    }

    public interface UploadCallback {

        void onSuccess(
                @NonNull UploadResult result
        );

        void onError(
                @NonNull Exception exception
        );
    }

    public interface MetadataCallback {

        void onLoaded(
                @Nullable CloudBackupMetadata metadata
        );

        void onError(
                @NonNull Exception exception
        );
    }

    public interface DeleteCallback {

        void onSuccess();

        void onError(
                @NonNull Exception exception
        );
    }

    public interface AvailabilityCallback {

        void onResult(
                boolean available
        );

        void onError(
                @NonNull Exception exception
        );
    }
}
