package com.example.moneymanagerpro.cloud;

import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.zip.GZIPInputStream;

/**
 * Downloads, verifies and decrypts the latest Money Manager Pro
 * encrypted cloud backup.
 *
 * Security flow:
 *
 * 1. Metadata is read directly from the Firestore server.
 * 2. Every encrypted chunk is read directly from the server.
 * 3. Chunk owner, backup ID, index, count and checksum are verified.
 * 4. The complete encrypted payload SHA-256 is verified.
 * 5. AES-256-GCM authenticates the encrypted backup.
 * 6. The decrypted compressed payload SHA-256 is verified.
 * 7. GZIP output size and SHA-256 are verified.
 * 8. Firebase UID and backup ID inside the JSON are verified.
 * 9. The passphrase is never stored.
 * 10. No local Room data is changed by this class.
 */
public final class EncryptedCloudBackupDownloader {

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

    private static final int MAX_UNCOMPRESSED_BYTES =
            25 * 1024 * 1024;

    private static final int MAX_COMPRESSED_BYTES =
            25 * 1024 * 1024;

    private static final int MAX_ENCRYPTED_BYTES =
            25 * 1024 * 1024 + 1024;

    private static final int MAX_ENCODED_CHARACTERS =
            EncryptedCloudBackupPayloadBuilder
                    .CHUNK_CHARACTER_LIMIT
                    * EncryptedCloudBackupPayloadBuilder
                    .MAX_CHUNK_COUNT;

    private static final int BUFFER_SIZE =
            8 * 1024;

    private static final ExecutorService DECRYPTION_EXECUTOR =
            Executors.newSingleThreadExecutor();

    private static final Handler MAIN_HANDLER =
            new Handler(
                    Looper.getMainLooper()
            );

    private final FirebaseFirestore firestore;

    public EncryptedCloudBackupDownloader() {
        firestore =
                FirebaseFirestore.getInstance();
    }

    /**
     * Downloads and decrypts the latest cloud backup.
     *
     * The supplied passphrase is copied internally and the internal copy
     * is cleared after success or failure.
     *
     * The caller should also clear its original passphrase char[] after
     * calling this method.
     */
    public void downloadAndDecryptLatestBackup(
            @NonNull FirebaseUser firebaseUser,
            @NonNull char[] passphrase,
            @NonNull DownloadCallback callback
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

        char[] passphraseCopy =
                Arrays.copyOf(
                        passphrase,
                        passphrase.length
                );

        String firebaseUserId =
                firebaseUser
                        .getUid()
                        .trim();

        DocumentReference metadataReference =
                getLatestBackupReference(
                        firebaseUserId
                );

        metadataReference
                .get(Source.SERVER)
                .addOnSuccessListener(
                        metadataSnapshot -> {
                            if (!metadataSnapshot.exists()) {
                                clearPassphraseAndPostError(
                                        passphraseCopy,
                                        callback,
                                        new CloudDownloadException(
                                                "No encrypted cloud backup "
                                                        + "was found for this account."
                                        )
                                );

                                return;
                            }

                            DownloadMetadata metadata;

                            try {
                                metadata =
                                        readAndValidateMetadata(
                                                metadataSnapshot,
                                                firebaseUserId
                                        );

                            } catch (Exception exception) {
                                clearPassphraseAndPostError(
                                        passphraseCopy,
                                        callback,
                                        exception
                                );

                                return;
                            }

                            CollectionReference chunksReference =
                                    metadataReference.collection(
                                            COLLECTION_CHUNKS
                                    );

                            chunksReference
                                    .get(Source.SERVER)
                                    .addOnSuccessListener(
                                            chunkSnapshot ->
                                                    submitDecryptionTask(
                                                            firebaseUserId,
                                                            metadata,
                                                            chunkSnapshot,
                                                            passphraseCopy,
                                                            callback
                                                    )
                                    )
                                    .addOnFailureListener(
                                            exception ->
                                                    clearPassphraseAndPostError(
                                                            passphraseCopy,
                                                            callback,
                                                            new CloudDownloadException(
                                                                    "Unable to download encrypted "
                                                                            + "cloud backup parts.",
                                                                    exception
                                                            )
                                                    )
                                    );
                        }
                )
                .addOnFailureListener(
                        exception ->
                                clearPassphraseAndPostError(
                                        passphraseCopy,
                                        callback,
                                        new CloudDownloadException(
                                                "Unable to read cloud backup metadata "
                                                        + "from the server.",
                                                exception
                                        )
                                )
                );
    }

    private void submitDecryptionTask(
            @NonNull String firebaseUserId,
            @NonNull DownloadMetadata metadata,
            @NonNull QuerySnapshot chunkSnapshot,
            @NonNull char[] passphraseCopy,
            @NonNull DownloadCallback callback
    ) {
        try {
            DECRYPTION_EXECUTOR.execute(
                    () -> {
                        DecryptedCloudBackup decryptedBackup = null;
                        Exception failure = null;

                        try {
                            decryptedBackup =
                                    decryptAndVerify(
                                            firebaseUserId,
                                            metadata,
                                            chunkSnapshot,
                                            passphraseCopy
                                    );

                        } catch (Exception exception) {
                            failure =
                                    exception;

                        } finally {
                            CloudBackupEncryption
                                    .clearSensitiveCharacters(
                                            passphraseCopy
                                    );
                        }

                        DecryptedCloudBackup finalBackup =
                                decryptedBackup;

                        Exception finalFailure =
                                failure;

                        MAIN_HANDLER.post(
                                () -> {
                                    if (finalFailure != null) {
                                        callback.onError(
                                                finalFailure
                                        );

                                        return;
                                    }

                                    callback.onSuccess(
                                            finalBackup
                                    );
                                }
                        );
                    }
            );

        } catch (RejectedExecutionException exception) {
            clearPassphraseAndPostError(
                    passphraseCopy,
                    callback,
                    new CloudDownloadException(
                            "Cloud backup decryption could not be started.",
                            exception
                    )
            );
        }
    }

    @NonNull
    private DecryptedCloudBackup decryptAndVerify(
            @NonNull String firebaseUserId,
            @NonNull DownloadMetadata metadata,
            @NonNull QuerySnapshot chunkSnapshot,
            @NonNull char[] passphrase
    ) throws Exception {

        List<DownloadedChunk> chunks =
                readAndValidateChunks(
                        firebaseUserId,
                        metadata,
                        chunkSnapshot
                );

        String encryptedPayloadBase64 =
                joinEncryptedChunks(
                        metadata,
                        chunks
                );

        byte[] encryptedBytes = null;
        byte[] compressedBytes = null;
        byte[] uncompressedJsonBytes = null;

        try {
            try {
                encryptedBytes =
                        Base64.decode(
                                encryptedPayloadBase64,
                                Base64.NO_WRAP
                        );

            } catch (IllegalArgumentException exception) {
                throw new CloudDownloadException(
                        "Encrypted cloud backup contains invalid "
                                + "Base64 content.",
                        exception
                );
            }

            validateExactSize(
                    encryptedBytes.length,
                    metadata.encryptedByteCount,
                    MAX_ENCRYPTED_BYTES,
                    "Encrypted cloud backup"
            );

            String calculatedEncryptedSha256 =
                    sha256Hex(
                            encryptedBytes
                    );

            if (!constantTimeEquals(
                    calculatedEncryptedSha256,
                    metadata.encryptedSha256
            )) {
                throw new CloudDownloadException(
                        "Encrypted cloud backup integrity verification failed."
                );
            }

            CloudBackupEncryption.EncryptedPayload encryptedPayload =
                    CloudBackupEncryption.fromBase64(
                            metadata.encryptionVersion,
                            metadata.cipherTransformation,
                            metadata.kdfAlgorithm,
                            metadata.prfAlgorithm,
                            metadata.kdfIterations,
                            metadata.keyLengthBits,
                            metadata.gcmTagLengthBits,
                            metadata.saltBase64,
                            metadata.initializationVectorBase64,
                            encryptedPayloadBase64,
                            metadata.compressedByteCount
                    );

            String associatedData =
                    CloudBackupEncryption
                            .createAssociatedData(
                                    firebaseUserId,
                                    metadata.backupId
                            );

            compressedBytes =
                    CloudBackupEncryption.decrypt(
                            encryptedPayload,
                            passphrase,
                            associatedData
                    );

            validateExactSize(
                    compressedBytes.length,
                    metadata.compressedByteCount,
                    MAX_COMPRESSED_BYTES,
                    "Decrypted compressed backup"
            );

            String calculatedCompressedSha256 =
                    sha256Hex(
                            compressedBytes
                    );

            if (!constantTimeEquals(
                    calculatedCompressedSha256,
                    metadata.compressedSha256
            )) {
                throw new CloudDownloadException(
                        "Decrypted cloud backup checksum verification failed."
                );
            }

            uncompressedJsonBytes =
                    gunzip(
                            compressedBytes,
                            metadata.uncompressedByteCount
                    );

            validateExactSize(
                    uncompressedJsonBytes.length,
                    metadata.uncompressedByteCount,
                    MAX_UNCOMPRESSED_BYTES,
                    "Uncompressed cloud backup"
            );

            String calculatedUncompressedSha256 =
                    sha256Hex(
                            uncompressedJsonBytes
                    );

            if (!constantTimeEquals(
                    calculatedUncompressedSha256,
                    metadata.uncompressedSha256
            )) {
                throw new CloudDownloadException(
                        "Original cloud backup integrity verification failed."
                );
            }

            VerifiedJsonInformation jsonInformation =
                    verifyJsonPayload(
                            uncompressedJsonBytes,
                            firebaseUserId,
                            metadata
                    );

            return new DecryptedCloudBackup(
                    metadata.backupId,
                    firebaseUserId,
                    metadata.backupCreatedAt,
                    metadata.uploadedAtClient,
                    metadata.databaseVersion,
                    metadata.appVersionName,
                    metadata.appVersionCode,
                    metadata.totalRecordCount,
                    jsonInformation.payloadVersion,
                    jsonInformation.verifiedTotalRecordCount,
                    metadata.uncompressedSha256,
                    uncompressedJsonBytes
            );

        } catch (CloudBackupEncryption
                         .InvalidPassphraseException exception) {

            throw new InvalidRecoveryPassphraseException(
                    "Cloud backup could not be unlocked. "
                            + "Check the recovery passphrase and try again.",
                    exception
            );

        } catch (CloudDownloadException exception) {
            throw exception;

        } catch (Exception exception) {
            throw new CloudDownloadException(
                    "Encrypted cloud backup could not be verified "
                            + "and decrypted.",
                    exception
            );

        } finally {
            CloudBackupEncryption
                    .clearSensitiveBytes(
                            encryptedBytes
                    );

            CloudBackupEncryption
                    .clearSensitiveBytes(
                            compressedBytes
                    );

            CloudBackupEncryption
                    .clearSensitiveBytes(
                            uncompressedJsonBytes
                    );
        }
    }

    @NonNull
    private DownloadMetadata readAndValidateMetadata(
            @NonNull DocumentSnapshot snapshot,
            @NonNull String expectedFirebaseUserId
    ) throws CloudDownloadException {

        String ownerUserId =
                requireString(
                        snapshot,
                        "owner_uid"
                );

        if (!expectedFirebaseUserId.equals(
                ownerUserId
        )) {
            throw new CloudDownloadException(
                    "Cloud backup owner verification failed."
            );
        }

        String appPackage =
                requireString(
                        snapshot,
                        "app_package"
                );

        if (!APP_PACKAGE.equals(
                appPackage
        )) {
            throw new CloudDownloadException(
                    "This cloud backup belongs to another application."
            );
        }

        String status =
                requireString(
                        snapshot,
                        "status"
                );

        if (!STATUS_COMPLETE.equalsIgnoreCase(
                status
        )) {
            throw new CloudDownloadException(
                    "Cloud backup upload is incomplete."
            );
        }

        boolean clientSideEncrypted =
                requireBoolean(
                        snapshot,
                        "is_client_side_encrypted"
                );

        if (!clientSideEncrypted) {
            throw new CloudDownloadException(
                    "The cloud backup is not marked as "
                            + "client-side encrypted."
            );
        }

        String backupId =
                requireString(
                        snapshot,
                        "backup_id"
                );

        validateBackupId(
                backupId
        );

        String sourcePayloadType =
                requireString(
                        snapshot,
                        "source_payload_type"
                );

        if (!CloudBackupPayloadBuilder
                .PAYLOAD_TYPE
                .equals(
                        sourcePayloadType
                )) {
            throw new CloudDownloadException(
                    "Unsupported cloud backup source format."
            );
        }

        int sourcePayloadVersion =
                requirePositiveInt(
                        snapshot,
                        "source_payload_version"
                );

        if (sourcePayloadVersion
                != CloudBackupPayloadBuilder.PAYLOAD_VERSION) {
            throw new CloudDownloadException(
                    "Unsupported cloud backup payload version."
            );
        }

        String payloadFormat =
                requireString(
                        snapshot,
                        "payload_format"
                );

        if (!EncryptedCloudBackupPayloadBuilder
                .ENCRYPTED_PAYLOAD_FORMAT
                .equals(
                        payloadFormat
                )) {
            throw new CloudDownloadException(
                    "Unsupported encrypted cloud backup format."
            );
        }

        int payloadFormatVersion =
                requirePositiveInt(
                        snapshot,
                        "payload_format_version"
                );

        if (payloadFormatVersion
                != EncryptedCloudBackupPayloadBuilder
                .ENCRYPTED_PAYLOAD_FORMAT_VERSION) {

            throw new CloudDownloadException(
                    "Unsupported encrypted backup format version."
            );
        }

        String compression =
                requireString(
                        snapshot,
                        "compression"
                );

        if (!CloudBackupPayloadBuilder
                .COMPRESSION_TYPE
                .equalsIgnoreCase(
                        compression
                )) {
            throw new CloudDownloadException(
                    "Unsupported cloud backup compression format."
            );
        }

        String encoding =
                requireString(
                        snapshot,
                        "encoding"
                );

        if (!EncryptedCloudBackupPayloadBuilder
                .ENCODING_TYPE
                .equals(
                        encoding
                )) {
            throw new CloudDownloadException(
                    "Unsupported encrypted backup encoding."
            );
        }

        String associatedDataScheme =
                requireString(
                        snapshot,
                        "associated_data_scheme"
                );

        if (!EncryptedCloudBackupPayloadBuilder
                .ASSOCIATED_DATA_SCHEME
                .equals(
                        associatedDataScheme
                )) {
            throw new CloudDownloadException(
                    "Unsupported encrypted backup account-binding scheme."
            );
        }

        int encryptionVersion =
                requirePositiveInt(
                        snapshot,
                        "encryption_version"
                );

        if (encryptionVersion
                != CloudBackupEncryption.ENCRYPTION_VERSION) {
            throw new CloudDownloadException(
                    "Unsupported cloud backup encryption version."
            );
        }

        String cipherTransformation =
                requireString(
                        snapshot,
                        "cipher_transformation"
                );

        if (!CloudBackupEncryption
                .CIPHER_TRANSFORMATION
                .equals(
                        cipherTransformation
                )) {
            throw new CloudDownloadException(
                    "Unsupported cloud backup cipher."
            );
        }

        String kdfAlgorithm =
                requireString(
                        snapshot,
                        "kdf_algorithm"
                );

        if (!CloudBackupEncryption
                .KDF_ALGORITHM
                .equals(
                        kdfAlgorithm
                )) {
            throw new CloudDownloadException(
                    "Unsupported cloud backup key derivation algorithm."
            );
        }

        String prfAlgorithm =
                requireString(
                        snapshot,
                        "prf_algorithm"
                );

        if (!CloudBackupEncryption
                .PRF_ALGORITHM
                .equals(
                        prfAlgorithm
                )) {
            throw new CloudDownloadException(
                    "Unsupported cloud backup HMAC algorithm."
            );
        }

        int kdfIterations =
                requirePositiveInt(
                        snapshot,
                        "kdf_iterations"
                );

        if (kdfIterations
                != CloudBackupEncryption.KDF_ITERATIONS) {
            throw new CloudDownloadException(
                    "Unsupported cloud backup key derivation settings."
            );
        }

        int keyLengthBits =
                requirePositiveInt(
                        snapshot,
                        "key_length_bits"
                );

        if (keyLengthBits
                != CloudBackupEncryption.KEY_LENGTH_BITS) {
            throw new CloudDownloadException(
                    "Unsupported cloud backup encryption key size."
            );
        }

        int gcmTagLengthBits =
                requirePositiveInt(
                        snapshot,
                        "gcm_tag_length_bits"
                );

        if (gcmTagLengthBits
                != CloudBackupEncryption.GCM_TAG_LENGTH_BITS) {
            throw new CloudDownloadException(
                    "Unsupported cloud backup authentication tag size."
            );
        }

        String saltBase64 =
                requireString(
                        snapshot,
                        "salt_base64"
                );

        String initializationVectorBase64 =
                requireString(
                        snapshot,
                        "initialization_vector_base64"
                );

        validateBase64TechnicalValue(
                saltBase64,
                CloudBackupEncryption.SALT_LENGTH_BYTES,
                "Cloud backup encryption salt"
        );

        validateBase64TechnicalValue(
                initializationVectorBase64,
                CloudBackupEncryption.IV_LENGTH_BYTES,
                "Cloud backup initialization vector"
        );

        String hashAlgorithm =
                requireString(
                        snapshot,
                        "hash_algorithm"
                );

        if (!CloudBackupPayloadBuilder
                .HASH_ALGORITHM
                .equalsIgnoreCase(
                        hashAlgorithm
                )) {
            throw new CloudDownloadException(
                    "Unsupported cloud backup hash algorithm."
            );
        }

        String uncompressedSha256 =
                requireSha256(
                        snapshot,
                        "uncompressed_checksum_sha256"
                );

        String compressedSha256 =
                requireSha256(
                        snapshot,
                        "compressed_checksum_sha256"
                );

        String encryptedSha256 =
                requireSha256(
                        snapshot,
                        "encrypted_checksum_sha256"
                );

        int uncompressedByteCount =
                requirePositiveInt(
                        snapshot,
                        "uncompressed_bytes"
                );

        int compressedByteCount =
                requirePositiveInt(
                        snapshot,
                        "compressed_bytes"
                );

        int encryptedByteCount =
                requirePositiveInt(
                        snapshot,
                        "encrypted_bytes"
                );

        int encodedCharacterCount =
                requirePositiveInt(
                        snapshot,
                        "encoded_characters"
                );

        int chunkCount =
                requirePositiveInt(
                        snapshot,
                        "chunk_count"
                );

        if (chunkCount
                > EncryptedCloudBackupPayloadBuilder
                .MAX_CHUNK_COUNT) {

            throw new CloudDownloadException(
                    "Cloud backup contains too many encrypted parts."
            );
        }

        if (uncompressedByteCount
                > MAX_UNCOMPRESSED_BYTES
                || compressedByteCount
                > MAX_COMPRESSED_BYTES
                || encryptedByteCount
                > MAX_ENCRYPTED_BYTES
                || encodedCharacterCount
                > MAX_ENCODED_CHARACTERS) {

            throw new CloudDownloadException(
                    "Cloud backup size exceeds the supported limit."
            );
        }

        int databaseVersion =
                requirePositiveInt(
                        snapshot,
                        "database_version"
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

        long backupCreatedAt =
                requirePositiveLong(
                        snapshot,
                        "backup_created_at"
                );

        long uploadedAtClient =
                getLong(
                        snapshot,
                        "uploaded_at_client",
                        0L
                );

        int totalRecordCount =
                getNonNegativeInt(
                        snapshot,
                        "total_record_count",
                        0
                );

        return new DownloadMetadata(
                ownerUserId,
                backupId,
                sourcePayloadVersion,
                payloadFormatVersion,
                backupCreatedAt,
                uploadedAtClient,
                chunkCount,
                encodedCharacterCount,
                uncompressedByteCount,
                compressedByteCount,
                encryptedByteCount,
                databaseVersion,
                appVersionName,
                appVersionCode,
                totalRecordCount,
                encryptionVersion,
                cipherTransformation,
                kdfAlgorithm,
                prfAlgorithm,
                kdfIterations,
                keyLengthBits,
                gcmTagLengthBits,
                saltBase64,
                initializationVectorBase64,
                uncompressedSha256,
                compressedSha256,
                encryptedSha256
        );
    }

    @NonNull
    private List<DownloadedChunk> readAndValidateChunks(
            @NonNull String expectedFirebaseUserId,
            @NonNull DownloadMetadata metadata,
            @NonNull QuerySnapshot chunkSnapshot
    ) throws CloudDownloadException {

        if (chunkSnapshot.isEmpty()) {
            throw new CloudDownloadException(
                    "Encrypted cloud backup parts are missing."
            );
        }

        if (chunkSnapshot.size()
                != metadata.chunkCount) {

            throw new CloudDownloadException(
                    "Cloud backup part count does not match its metadata."
            );
        }

        List<DownloadedChunk> chunks =
                new ArrayList<>(
                        chunkSnapshot.size()
                );

        Set<Integer> usedIndexes =
                new HashSet<>();

        for (DocumentSnapshot snapshot
                : chunkSnapshot.getDocuments()) {

            String ownerUserId =
                    requireString(
                            snapshot,
                            "owner_uid"
                    );

            if (!expectedFirebaseUserId.equals(
                    ownerUserId
            )) {
                throw new CloudDownloadException(
                        "A cloud backup part belongs to another account."
                );
            }

            String appPackage =
                    requireString(
                            snapshot,
                            "app_package"
                    );

            if (!APP_PACKAGE.equals(
                    appPackage
            )) {
                throw new CloudDownloadException(
                        "A cloud backup part belongs to another application."
                );
            }

            String status =
                    requireString(
                            snapshot,
                            "status"
                    );

            if (!STATUS_CHUNK.equalsIgnoreCase(
                    status
            )) {
                throw new CloudDownloadException(
                        "An invalid cloud backup part was found."
                );
            }

            String backupId =
                    requireString(
                            snapshot,
                            "backup_id"
                    );

            if (!metadata.backupId.equals(
                    backupId
            )) {
                throw new CloudDownloadException(
                        "Cloud backup parts do not belong to "
                                + "the same backup."
                );
            }

            int chunkIndex =
                    getNonNegativeInt(
                            snapshot,
                            "chunk_index",
                            -1
                    );

            if (chunkIndex < 0
                    || chunkIndex
                    >= metadata.chunkCount) {

                throw new CloudDownloadException(
                        "Cloud backup contains an invalid part index."
                );
            }

            if (!usedIndexes.add(
                    chunkIndex
            )) {
                throw new CloudDownloadException(
                        "Cloud backup contains a duplicate part."
                );
            }

            int declaredChunkCount =
                    requirePositiveInt(
                            snapshot,
                            "chunk_count"
                    );

            if (declaredChunkCount
                    != metadata.chunkCount) {

                throw new CloudDownloadException(
                        "Cloud backup part-count verification failed."
                );
            }

            boolean clientSideEncrypted =
                    requireBoolean(
                            snapshot,
                            "is_client_side_encrypted"
                    );

            if (!clientSideEncrypted) {
                throw new CloudDownloadException(
                        "A cloud backup part is not marked as encrypted."
                );
            }

            String encryptedChecksum =
                    requireSha256(
                            snapshot,
                            "encrypted_checksum_sha256"
                    );

            if (!constantTimeEquals(
                    encryptedChecksum,
                    metadata.encryptedSha256
            )) {
                throw new CloudDownloadException(
                        "Cloud backup part checksum metadata is inconsistent."
                );
            }

            String encryptedPayload =
                    requireString(
                            snapshot,
                            "payload"
                    );

            if (encryptedPayload.length()
                    > EncryptedCloudBackupPayloadBuilder
                    .CHUNK_CHARACTER_LIMIT) {

                throw new CloudDownloadException(
                        "A cloud backup part exceeds the supported size."
                );
            }

            chunks.add(
                    new DownloadedChunk(
                            chunkIndex,
                            encryptedPayload
                    )
            );
        }

        Collections.sort(
                chunks,
                Comparator.comparingInt(
                        DownloadedChunk::getIndex
                )
        );

        for (int expectedIndex = 0;
             expectedIndex < chunks.size();
             expectedIndex++) {

            if (chunks
                    .get(expectedIndex)
                    .getIndex()
                    != expectedIndex) {

                throw new CloudDownloadException(
                        "One or more cloud backup parts are missing."
                );
            }
        }

        return chunks;
    }

    @NonNull
    private String joinEncryptedChunks(
            @NonNull DownloadMetadata metadata,
            @NonNull List<DownloadedChunk> chunks
    ) throws CloudDownloadException {

        StringBuilder builder =
                new StringBuilder(
                        metadata.encodedCharacterCount
                );

        for (DownloadedChunk chunk : chunks) {
            builder.append(
                    chunk.getPayload()
            );

            if (builder.length()
                    > MAX_ENCODED_CHARACTERS) {

                throw new CloudDownloadException(
                        "Encoded cloud backup exceeds the supported size."
                );
            }
        }

        if (builder.length()
                != metadata.encodedCharacterCount) {

            throw new CloudDownloadException(
                    "Encoded cloud backup size does not match its metadata."
            );
        }

        return builder.toString();
    }

    @NonNull
    private byte[] gunzip(
            @NonNull byte[] compressedBytes,
            int expectedUncompressedBytes
    ) throws CloudDownloadException {

        int initialCapacity =
                Math.min(
                        Math.max(
                                expectedUncompressedBytes,
                                BUFFER_SIZE
                        ),
                        1024 * 1024
                );

        ByteArrayOutputStream outputStream =
                new ByteArrayOutputStream(
                        initialCapacity
                );

        byte[] buffer =
                new byte[BUFFER_SIZE];

        int totalBytes = 0;

        try (
                ByteArrayInputStream inputStream =
                        new ByteArrayInputStream(
                                compressedBytes
                        );

                GZIPInputStream gzipInputStream =
                        new GZIPInputStream(
                                inputStream
                        )
        ) {
            int bytesRead;

            while ((bytesRead =
                    gzipInputStream.read(
                            buffer
                    )) != -1) {

                totalBytes +=
                        bytesRead;

                if (totalBytes
                        > MAX_UNCOMPRESSED_BYTES) {

                    throw new CloudDownloadException(
                            "Uncompressed cloud backup exceeds "
                                    + "the supported size."
                    );
                }

                outputStream.write(
                        buffer,
                        0,
                        bytesRead
                );
            }

            byte[] result =
                    outputStream.toByteArray();

            if (expectedUncompressedBytes > 0
                    && result.length
                    != expectedUncompressedBytes) {

                CloudBackupEncryption
                        .clearSensitiveBytes(
                                result
                        );

                throw new CloudDownloadException(
                        "Uncompressed cloud backup size verification failed."
                );
            }

            return result;

        } catch (CloudDownloadException exception) {
            throw exception;

        } catch (Exception exception) {
            throw new CloudDownloadException(
                    "Cloud backup compression data is invalid.",
                    exception
            );

        } finally {
            CloudBackupEncryption
                    .clearSensitiveBytes(
                            buffer
                    );

            try {
                outputStream.close();

            } catch (Exception ignored) {
                // ByteArrayOutputStream close does not require recovery.
            }
        }
    }

    @NonNull
    private VerifiedJsonInformation verifyJsonPayload(
            @NonNull byte[] jsonBytes,
            @NonNull String expectedFirebaseUserId,
            @NonNull DownloadMetadata metadata
    ) throws CloudDownloadException {

        try {
            String jsonText =
                    new String(
                            jsonBytes,
                            StandardCharsets.UTF_8
                    );

            JSONObject root =
                    new JSONObject(
                            jsonText
                    );

            /*
             * Current payload uses snake_case metadata.
             * Legacy encrypted payloads used camelCase metadata.
             */
            String payloadType =
                    firstNonEmptyJsonString(
                            root,
                            "backup_type",
                            "payloadType"
                    );

            if (payloadType.isEmpty()) {
                payloadType =
                        firstNonEmptyJsonString(
                                root,
                                "backup_format"
                        );
            }

            if (!CloudBackupPayloadBuilder
                    .PAYLOAD_TYPE
                    .equals(
                            payloadType
                    )) {

                throw new CloudDownloadException(
                        "Decrypted data is not a Money Manager Pro backup."
                );
            }

            int payloadVersion =
                    firstPositiveJsonInt(
                            root,
                            "backup_format_version",
                            "payloadVersion"
                    );

            if (payloadVersion
                    != CloudBackupPayloadBuilder.PAYLOAD_VERSION) {

                throw new CloudDownloadException(
                        "Unsupported decrypted backup payload version."
                );
            }

            String packageName =
                    firstNonEmptyJsonString(
                            root,
                            "package_name",
                            "packageName"
                    );

            if (!APP_PACKAGE.equals(
                    packageName
            )) {
                throw new CloudDownloadException(
                        "Decrypted backup belongs to another application."
                );
            }

            String cloudOwnerUserId =
                    firstNonEmptyJsonString(
                            root,
                            "firebase_user_id",
                            "cloudOwnerUid"
                    );

            if (!expectedFirebaseUserId.equals(
                    cloudOwnerUserId
            )) {
                throw new CloudDownloadException(
                        "Decrypted backup owner verification failed."
                );
            }

            String backupId =
                    firstNonEmptyJsonString(
                            root,
                            "backup_id",
                            "backupId"
                    );

            if (!metadata.backupId.equals(
                    backupId
            )) {
                throw new CloudDownloadException(
                        "Decrypted backup ID verification failed."
                );
            }

            String compression =
                    firstNonEmptyJsonString(
                            root,
                            "compression"
                    );

            if (!CloudBackupPayloadBuilder
                    .COMPRESSION_TYPE
                    .equalsIgnoreCase(
                            compression
                    )) {
                throw new CloudDownloadException(
                        "Decrypted backup compression metadata "
                                + "is inconsistent."
                );
            }

            String hashAlgorithm =
                    firstNonEmptyJsonString(
                            root,
                            "hash_algorithm",
                            "hashAlgorithm"
                    );

            if (!hashAlgorithm.isEmpty()
                    && !CloudBackupPayloadBuilder
                    .HASH_ALGORITHM
                    .equalsIgnoreCase(
                            hashAlgorithm
                    )) {

                throw new CloudDownloadException(
                        "Decrypted backup hash metadata is inconsistent."
                );
            }

            int databaseVersion =
                    firstPositiveJsonInt(
                            root,
                            "database_version",
                            "databaseVersion"
                    );

            if (databaseVersion
                    != metadata.databaseVersion) {

                throw new CloudDownloadException(
                        "Decrypted backup database version does not match "
                                + "its cloud metadata."
                );
            }

            JSONObject countsObject =
                    firstJsonObject(
                            root,
                            "record_counts",
                            "recordCounts"
                    );

            if (countsObject == null) {
                throw new CloudDownloadException(
                        "Decrypted backup record counts are missing."
                );
            }

            int verifiedTotalRecordCount =
                    firstNonNegativeJsonInt(
                            countsObject,
                            "total_records",
                            "totalRecords"
                    );

            if (verifiedTotalRecordCount < 0) {
                throw new CloudDownloadException(
                        "Decrypted backup record count is invalid."
                );
            }

            if (verifiedTotalRecordCount
                    != metadata.totalRecordCount) {

                throw new CloudDownloadException(
                        "Decrypted backup record count does not match "
                                + "its cloud metadata."
                );
            }

            return new VerifiedJsonInformation(
                    payloadVersion,
                    verifiedTotalRecordCount
            );

        } catch (CloudDownloadException exception) {
            throw exception;

        } catch (Exception exception) {
            throw new CloudDownloadException(
                    "Decrypted cloud backup JSON is invalid.",
                    exception
            );
        }
    }

    @NonNull
    private String firstNonEmptyJsonString(
            @NonNull JSONObject object,
            @NonNull String... fields
    ) {
        for (String field : fields) {
            if (field == null
                    || field.trim().isEmpty()
                    || !object.has(
                    field
            )
                    || object.isNull(
                    field
            )) {

                continue;
            }

            String value =
                    String.valueOf(
                            object.opt(
                                    field
                            )
                    ).trim();

            if (!value.isEmpty()
                    && !"null".equalsIgnoreCase(
                    value
            )) {

                return value;
            }
        }

        return "";
    }

    private int firstPositiveJsonInt(
            @NonNull JSONObject object,
            @NonNull String... fields
    ) {
        for (String field : fields) {
            if (field == null
                    || field.trim().isEmpty()
                    || !object.has(
                    field
            )
                    || object.isNull(
                    field
            )) {

                continue;
            }

            Object rawValue =
                    object.opt(
                            field
                    );

            long parsedValue;

            if (rawValue instanceof Number) {
                parsedValue =
                        ((Number) rawValue)
                                .longValue();

            } else {
                try {
                    parsedValue =
                            Long.parseLong(
                                    String.valueOf(
                                            rawValue
                                    ).trim()
                            );

                } catch (Exception ignored) {
                    continue;
                }
            }

            if (parsedValue > 0L
                    && parsedValue <= Integer.MAX_VALUE) {

                return (int) parsedValue;
            }
        }

        return 0;
    }

    private int firstNonNegativeJsonInt(
            @NonNull JSONObject object,
            @NonNull String... fields
    ) {
        for (String field : fields) {
            if (field == null
                    || field.trim().isEmpty()
                    || !object.has(
                    field
            )
                    || object.isNull(
                    field
            )) {

                continue;
            }

            Object rawValue =
                    object.opt(
                            field
                    );

            long parsedValue;

            if (rawValue instanceof Number) {
                parsedValue =
                        ((Number) rawValue)
                                .longValue();

            } else {
                try {
                    parsedValue =
                            Long.parseLong(
                                    String.valueOf(
                                            rawValue
                                    ).trim()
                            );

                } catch (Exception ignored) {
                    continue;
                }
            }

            if (parsedValue >= 0L
                    && parsedValue <= Integer.MAX_VALUE) {

                return (int) parsedValue;
            }
        }

        return -1;
    }

    @Nullable
    private JSONObject firstJsonObject(
            @NonNull JSONObject object,
            @NonNull String... fields
    ) {
        for (String field : fields) {
            if (field == null
                    || field.trim().isEmpty()) {

                continue;
            }

            JSONObject value =
                    object.optJSONObject(
                            field
                    );

            if (value != null) {
                return value;
            }
        }

        return null;
    }

    private void validateExactSize(
            int actualBytes,
            int expectedBytes,
            int maximumBytes,
            @NonNull String description
    ) throws CloudDownloadException {

        if (actualBytes <= 0
                || actualBytes > maximumBytes) {

            throw new CloudDownloadException(
                    description
                            + " size is invalid."
            );
        }

        if (expectedBytes <= 0
                || actualBytes
                != expectedBytes) {

            throw new CloudDownloadException(
                    description
                            + " size does not match its metadata."
            );
        }
    }

    private void validateBase64TechnicalValue(
            @NonNull String encodedValue,
            int expectedByteCount,
            @NonNull String description
    ) throws CloudDownloadException {

        byte[] decodedBytes = null;

        try {
            decodedBytes =
                    Base64.decode(
                            encodedValue,
                            Base64.DEFAULT
                    );

            if (decodedBytes.length
                    != expectedByteCount) {

                throw new CloudDownloadException(
                        description
                                + " is invalid."
                );
            }

        } catch (IllegalArgumentException exception) {
            throw new CloudDownloadException(
                    description
                            + " contains invalid Base64 data.",
                    exception
            );

        } finally {
            CloudBackupEncryption
                    .clearSensitiveBytes(
                            decodedBytes
                    );
        }
    }

    @NonNull
    private String sha256Hex(
            @NonNull byte[] bytes
    ) throws Exception {

        MessageDigest digest =
                MessageDigest.getInstance(
                        CloudBackupPayloadBuilder
                                .HASH_ALGORITHM
                );

        byte[] hashBytes =
                digest.digest(
                        bytes
                );

        try {
            StringBuilder result =
                    new StringBuilder(
                            hashBytes.length * 2
                    );

            for (byte hashByte : hashBytes) {
                result.append(
                        String.format(
                                Locale.US,
                                "%02x",
                                hashByte & 0xff
                        )
                );
            }

            return result.toString();

        } finally {
            CloudBackupEncryption
                    .clearSensitiveBytes(
                            hashBytes
                    );
        }
    }

    private boolean constantTimeEquals(
            @Nullable String first,
            @Nullable String second
    ) {
        if (first == null
                || second == null) {

            return false;
        }

        byte[] firstBytes =
                first.getBytes(
                        StandardCharsets.UTF_8
                );

        byte[] secondBytes =
                second.getBytes(
                        StandardCharsets.UTF_8
                );

        try {
            return MessageDigest.isEqual(
                    firstBytes,
                    secondBytes
            );

        } finally {
            CloudBackupEncryption
                    .clearSensitiveBytes(
                            firstBytes
                    );

            CloudBackupEncryption
                    .clearSensitiveBytes(
                            secondBytes
                    );
        }
    }

    @Nullable
    private Exception validateVerifiedUser(
            @NonNull FirebaseUser firebaseUser
    ) {
        String userId =
                firebaseUser
                        .getUid()
                        .trim();

        if (userId.isEmpty()) {
            return new IllegalStateException(
                    "Firebase cloud account UID is unavailable."
            );
        }

        if (!firebaseUser.isEmailVerified()) {
            return new IllegalStateException(
                    "Cloud restore requires a verified email account."
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

    private void validateBackupId(
            @NonNull String backupId
    ) throws CloudDownloadException {

        if (backupId.length() > 180
                || !backupId.matches(
                "[A-Za-z0-9_-]+"
        )) {
            throw new CloudDownloadException(
                    "Cloud backup ID is invalid."
            );
        }
    }

    @NonNull
    private String requireString(
            @NonNull DocumentSnapshot snapshot,
            @NonNull String field
    ) throws CloudDownloadException {

        String value =
                snapshot.getString(
                        field
                );

        if (value == null
                || value.trim().isEmpty()) {

            throw new CloudDownloadException(
                    "Cloud backup field \""
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

    private long requirePositiveLong(
            @NonNull DocumentSnapshot snapshot,
            @NonNull String field
    ) throws CloudDownloadException {

        Long value =
                snapshot.getLong(
                        field
                );

        if (value == null
                || value <= 0L) {

            throw new CloudDownloadException(
                    "Cloud backup numeric field \""
                            + field
                            + "\" is invalid."
            );
        }

        return value;
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

    private int requirePositiveInt(
            @NonNull DocumentSnapshot snapshot,
            @NonNull String field
    ) throws CloudDownloadException {

        long value =
                requirePositiveLong(
                        snapshot,
                        field
                );

        if (value > Integer.MAX_VALUE) {
            throw new CloudDownloadException(
                    "Cloud backup numeric field \""
                            + field
                            + "\" exceeds the supported range."
            );
        }

        return (int) value;
    }

    private int getNonNegativeInt(
            @NonNull DocumentSnapshot snapshot,
            @NonNull String field,
            int fallback
    ) {
        Long value =
                snapshot.getLong(
                        field
                );

        if (value == null
                || value < 0L
                || value > Integer.MAX_VALUE) {

            return fallback;
        }

        return value.intValue();
    }

    private boolean requireBoolean(
            @NonNull DocumentSnapshot snapshot,
            @NonNull String field
    ) throws CloudDownloadException {

        Boolean value =
                snapshot.getBoolean(
                        field
                );

        if (value == null) {
            throw new CloudDownloadException(
                    "Cloud backup boolean field \""
                            + field
                            + "\" is missing."
            );
        }

        return value;
    }

    @NonNull
    private String requireSha256(
            @NonNull DocumentSnapshot snapshot,
            @NonNull String field
    ) throws CloudDownloadException {

        String checksum =
                requireString(
                        snapshot,
                        field
                );

        if (!checksum.matches(
                "[0-9a-fA-F]{64}"
        )) {
            throw new CloudDownloadException(
                    "Cloud backup checksum field \""
                            + field
                            + "\" is invalid."
            );
        }

        return checksum.toLowerCase(
                Locale.US
        );
    }

    private void clearPassphraseAndPostError(
            @NonNull char[] passphrase,
            @NonNull DownloadCallback callback,
            @NonNull Exception exception
    ) {
        CloudBackupEncryption
                .clearSensitiveCharacters(
                        passphrase
                );

        MAIN_HANDLER.post(
                () -> callback.onError(
                        exception
                )
        );
    }

    private static final class DownloadMetadata {

        private final String ownerUserId;
        private final String backupId;

        private final int sourcePayloadVersion;
        private final int payloadFormatVersion;

        private final long backupCreatedAt;
        private final long uploadedAtClient;

        private final int chunkCount;
        private final int encodedCharacterCount;

        private final int uncompressedByteCount;
        private final int compressedByteCount;
        private final int encryptedByteCount;

        private final int databaseVersion;
        private final String appVersionName;
        private final long appVersionCode;

        private final int totalRecordCount;

        private final int encryptionVersion;
        private final String cipherTransformation;
        private final String kdfAlgorithm;
        private final String prfAlgorithm;
        private final int kdfIterations;
        private final int keyLengthBits;
        private final int gcmTagLengthBits;

        private final String saltBase64;
        private final String initializationVectorBase64;

        private final String uncompressedSha256;
        private final String compressedSha256;
        private final String encryptedSha256;

        private DownloadMetadata(
                @NonNull String ownerUserId,
                @NonNull String backupId,
                int sourcePayloadVersion,
                int payloadFormatVersion,
                long backupCreatedAt,
                long uploadedAtClient,
                int chunkCount,
                int encodedCharacterCount,
                int uncompressedByteCount,
                int compressedByteCount,
                int encryptedByteCount,
                int databaseVersion,
                @NonNull String appVersionName,
                long appVersionCode,
                int totalRecordCount,
                int encryptionVersion,
                @NonNull String cipherTransformation,
                @NonNull String kdfAlgorithm,
                @NonNull String prfAlgorithm,
                int kdfIterations,
                int keyLengthBits,
                int gcmTagLengthBits,
                @NonNull String saltBase64,
                @NonNull String initializationVectorBase64,
                @NonNull String uncompressedSha256,
                @NonNull String compressedSha256,
                @NonNull String encryptedSha256
        ) {
            this.ownerUserId =
                    ownerUserId;

            this.backupId =
                    backupId;

            this.sourcePayloadVersion =
                    sourcePayloadVersion;

            this.payloadFormatVersion =
                    payloadFormatVersion;

            this.backupCreatedAt =
                    backupCreatedAt;

            this.uploadedAtClient =
                    uploadedAtClient;

            this.chunkCount =
                    chunkCount;

            this.encodedCharacterCount =
                    encodedCharacterCount;

            this.uncompressedByteCount =
                    uncompressedByteCount;

            this.compressedByteCount =
                    compressedByteCount;

            this.encryptedByteCount =
                    encryptedByteCount;

            this.databaseVersion =
                    databaseVersion;

            this.appVersionName =
                    appVersionName;

            this.appVersionCode =
                    appVersionCode;

            this.totalRecordCount =
                    totalRecordCount;

            this.encryptionVersion =
                    encryptionVersion;

            this.cipherTransformation =
                    cipherTransformation;

            this.kdfAlgorithm =
                    kdfAlgorithm;

            this.prfAlgorithm =
                    prfAlgorithm;

            this.kdfIterations =
                    kdfIterations;

            this.keyLengthBits =
                    keyLengthBits;

            this.gcmTagLengthBits =
                    gcmTagLengthBits;

            this.saltBase64 =
                    saltBase64;

            this.initializationVectorBase64 =
                    initializationVectorBase64;

            this.uncompressedSha256 =
                    uncompressedSha256;

            this.compressedSha256 =
                    compressedSha256;

            this.encryptedSha256 =
                    encryptedSha256;
        }
    }

    private static final class DownloadedChunk {

        private final int index;
        private final String payload;

        private DownloadedChunk(
                int index,
                @NonNull String payload
        ) {
            this.index =
                    index;

            this.payload =
                    payload;
        }

        private int getIndex() {
            return index;
        }

        @NonNull
        private String getPayload() {
            return payload;
        }
    }

    private static final class VerifiedJsonInformation {

        private final int payloadVersion;
        private final int verifiedTotalRecordCount;

        private VerifiedJsonInformation(
                int payloadVersion,
                int verifiedTotalRecordCount
        ) {
            this.payloadVersion =
                    payloadVersion;

            this.verifiedTotalRecordCount =
                    verifiedTotalRecordCount;
        }
    }

    /**
     * Immutable verified backup returned to the future restore layer.
     *
     * The JSON byte getter returns a defensive copy.
     * Call clearSensitiveData() after restore finishes.
     */
    public static final class DecryptedCloudBackup {

        private final String backupId;
        private final String firebaseUserId;

        private final long backupCreatedAt;
        private final long uploadedAtClient;

        private final int databaseVersion;
        private final String appVersionName;
        private final long appVersionCode;

        private final int metadataRecordCount;
        private final int payloadVersion;
        private final int verifiedRecordCount;

        private final String uncompressedSha256;

        private final byte[] jsonBytes;

        private DecryptedCloudBackup(
                @NonNull String backupId,
                @NonNull String firebaseUserId,
                long backupCreatedAt,
                long uploadedAtClient,
                int databaseVersion,
                @NonNull String appVersionName,
                long appVersionCode,
                int metadataRecordCount,
                int payloadVersion,
                int verifiedRecordCount,
                @NonNull String uncompressedSha256,
                @NonNull byte[] jsonBytes
        ) {
            this.backupId =
                    backupId;

            this.firebaseUserId =
                    firebaseUserId;

            this.backupCreatedAt =
                    backupCreatedAt;

            this.uploadedAtClient =
                    uploadedAtClient;

            this.databaseVersion =
                    databaseVersion;

            this.appVersionName =
                    appVersionName;

            this.appVersionCode =
                    appVersionCode;

            this.metadataRecordCount =
                    metadataRecordCount;

            this.payloadVersion =
                    payloadVersion;

            this.verifiedRecordCount =
                    verifiedRecordCount;

            this.uncompressedSha256 =
                    uncompressedSha256;

            this.jsonBytes =
                    Arrays.copyOf(
                            jsonBytes,
                            jsonBytes.length
                    );
        }

        @NonNull
        public String getBackupId() {
            return backupId;
        }

        @NonNull
        public String getFirebaseUserId() {
            return firebaseUserId;
        }

        public long getBackupCreatedAt() {
            return backupCreatedAt;
        }

        public long getUploadedAtClient() {
            return uploadedAtClient;
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

        public int getMetadataRecordCount() {
            return metadataRecordCount;
        }

        public int getPayloadVersion() {
            return payloadVersion;
        }

        public int getVerifiedRecordCount() {
            return verifiedRecordCount;
        }

        @NonNull
        public String getUncompressedSha256() {
            return uncompressedSha256;
        }

        public int getJsonByteCount() {
            return jsonBytes.length;
        }

        @NonNull
        public byte[] getJsonBytes() {
            return Arrays.copyOf(
                    jsonBytes,
                    jsonBytes.length
            );
        }

        /**
         * Use only when a JSONObject must be created.
         */
        @NonNull
        public String getJsonText() {
            return new String(
                    jsonBytes,
                    StandardCharsets.UTF_8
            );
        }

        /**
         * Clears the verified plain finance backup from this object.
         */
        public void clearSensitiveData() {
            Arrays.fill(
                    jsonBytes,
                    (byte) 0
            );
        }
    }

    public interface DownloadCallback {

        void onSuccess(
                @NonNull DecryptedCloudBackup backup
        );

        void onError(
                @NonNull Exception exception
        );
    }

    public static class CloudDownloadException
            extends Exception {

        public CloudDownloadException(
                @NonNull String message
        ) {
            super(message);
        }

        public CloudDownloadException(
                @NonNull String message,
                @NonNull Throwable cause
        ) {
            super(
                    message,
                    cause
            );
        }
    }

    public static final class InvalidRecoveryPassphraseException
            extends CloudDownloadException {

        public InvalidRecoveryPassphraseException(
                @NonNull String message,
                @NonNull Throwable cause
        ) {
            super(
                    message,
                    cause
            );
        }
    }
}