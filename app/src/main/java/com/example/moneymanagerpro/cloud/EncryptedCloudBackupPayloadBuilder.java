package com.example.moneymanagerpro.cloud;

import android.content.Context;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Creates a complete encrypted and Firestore-ready cloud-backup package.
 *
 * Processing flow:
 *
 * 1. Read complete local finance data.
 * 2. Create JSON backup payload.
 * 3. Compress the payload using GZIP.
 * 4. Verify the compressed payload checksum.
 * 5. Encrypt it using AES-256-GCM.
 * 6. Encode encrypted bytes using Base64.
 * 7. Split the encoded content into Firestore-safe chunks.
 *
 * Important security rules:
 *
 * 1. The cloud-backup passphrase is never stored.
 * 2. Plain backup bytes are never uploaded.
 * 3. Raw finance data is never written to Firestore by this class.
 * 4. Firebase UID and backup ID are authenticated through AES-GCM
 *    associated data.
 * 5. Sensitive temporary byte and character arrays are cleared.
 * 6. This class does not connect to Firebase.
 */
public final class EncryptedCloudBackupPayloadBuilder {

    public static final String ENCRYPTED_PAYLOAD_FORMAT =
            "MONEY_MANAGER_PRO_ENCRYPTED_CLOUD_BACKUP";

    public static final int ENCRYPTED_PAYLOAD_FORMAT_VERSION =
            1;

    public static final String ENCODING_TYPE =
            "BASE64_NO_WRAP";

    public static final String ASSOCIATED_DATA_SCHEME =
            "FIREBASE_UID_AND_BACKUP_ID_V1";

    /**
     * Firestore document maximum size is limited.
     *
     * Each encrypted content chunk is kept around 600,000 characters,
     * leaving sufficient space for document fields and metadata.
     */
    public static final int CHUNK_CHARACTER_LIMIT =
            600_000;

    /**
     * 64 chunks support encrypted backups considerably larger than the
     * current 25 MB local payload limit.
     */
    public static final int MAX_CHUNK_COUNT =
            64;

    private static final int MAX_ENCODED_CHARACTERS =
            CHUNK_CHARACTER_LIMIT
                    * MAX_CHUNK_COUNT;

    private static final int MAX_FIREBASE_UID_LENGTH =
            256;

    private final Context applicationContext;

    public EncryptedCloudBackupPayloadBuilder(
            @NonNull Context context
    ) {
        applicationContext =
                context.getApplicationContext();
    }

    /**
     * Creates one encrypted cloud-backup package.
     *
     * This method performs database, compression, hashing and encryption
     * operations. Always call it from a background thread.
     *
     * The caller must also clear its original passphrase char[] after
     * this method returns.
     */
    @NonNull
    public EncryptedCloudBackupPayload build(
            @NonNull String firebaseUserId,
            @NonNull char[] passphrase
    ) throws EncryptedPayloadBuildException {

        String verifiedUserId =
                validateFirebaseUserId(
                        firebaseUserId
                );

        char[] passphraseCopy =
                Arrays.copyOf(
                        passphrase,
                        passphrase.length
                );

        CloudBackupPayloadBuilder.Payload sourcePayload =
                null;

        byte[] compressedBackupBytes =
                null;

        byte[] encryptedBackupBytes =
                null;

        try {
            /*
             * Creates the complete compressed finance backup.
             */
            sourcePayload =
                    CloudBackupPayloadBuilder.build(
                            applicationContext,
                            verifiedUserId
                    );

            compressedBackupBytes =
                    sourcePayload.getCompressedBytes();

            validateCompressedSource(
                    sourcePayload,
                    compressedBackupBytes
            );

            /*
             * Binds the encrypted content to:
             *
             * - current Firebase account UID
             * - current cloud backup ID
             *
             * The same encrypted backup cannot be silently moved to
             * another account or another backup ID.
             */
            String associatedData =
                    CloudBackupEncryption
                            .createAssociatedData(
                                    verifiedUserId,
                                    sourcePayload.getBackupId()
                            );

            CloudBackupEncryption.EncryptedPayload
                    encryptedPayload =
                    CloudBackupEncryption.encrypt(
                            compressedBackupBytes,
                            passphraseCopy,
                            associatedData
                    );

            encryptedBackupBytes =
                    encryptedPayload.getEncryptedBytes();

            if (encryptedBackupBytes.length <= 0) {
                throw new EncryptedPayloadBuildException(
                        "Encrypted cloud backup is empty."
                );
            }

            String encryptedPayloadBase64 =
                    Base64.encodeToString(
                            encryptedBackupBytes,
                            Base64.NO_WRAP
                    );

            if (encryptedPayloadBase64.isEmpty()) {
                throw new EncryptedPayloadBuildException(
                        "Encoded encrypted cloud backup is empty."
                );
            }

            if (encryptedPayloadBase64.length()
                    > MAX_ENCODED_CHARACTERS) {

                throw new EncryptedPayloadBuildException(
                        "Encrypted cloud backup exceeds the supported "
                                + "Firestore size."
                );
            }

            List<String> encryptedChunks =
                    splitIntoChunks(
                            encryptedPayloadBase64
                    );

            if (encryptedChunks.isEmpty()) {
                throw new EncryptedPayloadBuildException(
                        "Encrypted cloud backup chunks are unavailable."
                );
            }

            if (encryptedChunks.size()
                    > MAX_CHUNK_COUNT) {

                throw new EncryptedPayloadBuildException(
                        "Encrypted cloud backup requires "
                                + encryptedChunks.size()
                                + " chunks. The maximum supported count is "
                                + MAX_CHUNK_COUNT
                                + "."
                );
            }

            String encryptedSha256 =
                    sha256Hex(
                            encryptedBackupBytes
                    );

            return new EncryptedCloudBackupPayload(
                    verifiedUserId,
                    sourcePayload.getBackupId(),
                    sourcePayload.getCreatedAtMillis(),
                    sourcePayload.getCreatedAtUtc(),

                    CloudBackupPayloadBuilder.PAYLOAD_TYPE,
                    sourcePayload.getPayloadVersion(),

                    ENCRYPTED_PAYLOAD_FORMAT,
                    ENCRYPTED_PAYLOAD_FORMAT_VERSION,

                    sourcePayload.getDatabaseVersion(),
                    sourcePayload.getAppVersionName(),
                    sourcePayload.getAppVersionCode(),

                    sourcePayload.getCompressionType(),
                    ENCODING_TYPE,
                    ASSOCIATED_DATA_SCHEME,

                    encryptedPayload.getEncryptionVersion(),
                    encryptedPayload.getCipherTransformation(),
                    encryptedPayload.getKdfAlgorithm(),
                    encryptedPayload.getPrfAlgorithm(),
                    encryptedPayload.getKdfIterations(),
                    encryptedPayload.getKeyLengthBits(),
                    encryptedPayload.getGcmTagLengthBits(),

                    encryptedPayload.getSaltBase64(),
                    encryptedPayload
                            .getInitializationVectorBase64(),

                    sourcePayload.getHashAlgorithm(),
                    sourcePayload.getUncompressedSha256(),
                    sourcePayload.getCompressedSha256(),
                    encryptedSha256,

                    sourcePayload.getUncompressedByteCount(),
                    sourcePayload.getCompressedByteCount(),
                    encryptedPayload.getEncryptedByteCount(),
                    encryptedPayloadBase64.length(),

                    encryptedChunks,
                    sourcePayload.getRecordCounts()
            );

        } catch (EncryptedPayloadBuildException exception) {
            throw exception;

        } catch (CloudBackupPayloadBuilder
                         .PayloadBuildException exception) {

            throw new EncryptedPayloadBuildException(
                    safeErrorMessage(
                            exception,
                            "Unable to prepare the complete finance backup."
                    ),
                    exception
            );

        } catch (CloudBackupEncryption
                         .CloudEncryptionException exception) {

            throw new EncryptedPayloadBuildException(
                    safeErrorMessage(
                            exception,
                            "Unable to encrypt the cloud backup."
                    ),
                    exception
            );

        } catch (Exception exception) {
            throw new EncryptedPayloadBuildException(
                    "Encrypted Money Manager Pro cloud backup "
                            + "could not be prepared.",
                    exception
            );

        } finally {
            CloudBackupEncryption
                    .clearSensitiveCharacters(
                            passphraseCopy
                    );

            CloudBackupEncryption
                    .clearSensitiveBytes(
                            compressedBackupBytes
                    );

            CloudBackupEncryption
                    .clearSensitiveBytes(
                            encryptedBackupBytes
                    );

            if (sourcePayload != null) {
                sourcePayload.clearSensitiveData();
            }
        }
    }

    /**
     * Verifies that the compressed bytes returned by the source payload
     * match their recorded SHA-256 checksum and size.
     */
    private static void validateCompressedSource(
            @NonNull CloudBackupPayloadBuilder.Payload sourcePayload,
            @NonNull byte[] compressedBytes
    ) throws EncryptedPayloadBuildException {

        if (compressedBytes.length <= 0) {
            throw new EncryptedPayloadBuildException(
                    "Compressed finance backup is empty."
            );
        }

        if (compressedBytes.length
                != sourcePayload.getCompressedByteCount()) {

            throw new EncryptedPayloadBuildException(
                    "Compressed finance backup size verification failed."
            );
        }

        String calculatedChecksum;

        try {
            calculatedChecksum =
                    sha256Hex(
                            compressedBytes
                    );

        } catch (Exception exception) {
            throw new EncryptedPayloadBuildException(
                    "Unable to verify the compressed finance backup.",
                    exception
            );
        }

        if (!constantTimeEquals(
                calculatedChecksum,
                sourcePayload.getCompressedSha256()
        )) {
            throw new EncryptedPayloadBuildException(
                    "Compressed finance backup integrity verification failed."
            );
        }
    }

    /**
     * Splits Base64 encrypted content into independent Firestore-safe
     * string chunks.
     */
    @NonNull
    private static List<String> splitIntoChunks(
            @NonNull String encodedPayload
    ) throws EncryptedPayloadBuildException {

        if (encodedPayload.isEmpty()) {
            return Collections.emptyList();
        }

        int requiredChunkCount =
                (
                        encodedPayload.length()
                                + CHUNK_CHARACTER_LIMIT
                                - 1
                )
                        / CHUNK_CHARACTER_LIMIT;

        if (requiredChunkCount
                > MAX_CHUNK_COUNT) {

            throw new EncryptedPayloadBuildException(
                    "Encrypted backup requires too many cloud chunks."
            );
        }

        List<String> chunks =
                new ArrayList<>(
                        requiredChunkCount
                );

        int startIndex = 0;

        while (startIndex
                < encodedPayload.length()) {

            int endIndex =
                    Math.min(
                            startIndex
                                    + CHUNK_CHARACTER_LIMIT,
                            encodedPayload.length()
                    );

            String chunk =
                    encodedPayload.substring(
                            startIndex,
                            endIndex
                    );

            if (chunk.isEmpty()) {
                throw new EncryptedPayloadBuildException(
                        "An empty encrypted cloud chunk was generated."
                );
            }

            chunks.add(chunk);

            startIndex =
                    endIndex;
        }

        return Collections.unmodifiableList(
                new ArrayList<>(
                        chunks
                )
        );
    }

    @NonNull
    private static String sha256Hex(
            @NonNull byte[] bytes
    ) throws Exception {

        MessageDigest digest =
                MessageDigest.getInstance(
                        CloudBackupPayloadBuilder.HASH_ALGORITHM
                );

        byte[] hashBytes =
                digest.digest(bytes);

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

    /**
     * Reduces checksum timing differences when comparing integrity hashes.
     */
    private static boolean constantTimeEquals(
            @Nullable String first,
            @Nullable String second
    ) {
        if (first == null
                || second == null) {

            return false;
        }

        byte[] firstBytes =
                first.getBytes();

        byte[] secondBytes =
                second.getBytes();

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

    @NonNull
    private static String validateFirebaseUserId(
            @NonNull String firebaseUserId
    ) throws EncryptedPayloadBuildException {

        String cleanUserId =
                firebaseUserId.trim();

        if (cleanUserId.isEmpty()) {
            throw new EncryptedPayloadBuildException(
                    "Firebase cloud account UID is unavailable."
            );
        }

        if (cleanUserId.length()
                > MAX_FIREBASE_UID_LENGTH) {

            throw new EncryptedPayloadBuildException(
                    "Firebase cloud account UID exceeds "
                            + "the supported length."
            );
        }

        if (cleanUserId.indexOf('\n') >= 0
                || cleanUserId.indexOf('\r') >= 0
                || cleanUserId.indexOf('\0') >= 0) {

            throw new EncryptedPayloadBuildException(
                    "Firebase cloud account UID contains "
                            + "unsupported characters."
            );
        }

        return cleanUserId;
    }

    @NonNull
    private static String safeErrorMessage(
            @NonNull Throwable throwable,
            @NonNull String fallback
    ) {
        String message =
                throwable.getMessage();

        if (message == null
                || message.trim().isEmpty()) {

            return fallback;
        }

        return message.trim();
    }

    /**
     * Immutable upload-ready encrypted cloud-backup package.
     *
     * It contains encrypted chunks and non-sensitive technical metadata.
     * It does not contain the passphrase or plain finance data.
     */
    public static final class EncryptedCloudBackupPayload {

        private final String firebaseUserId;

        private final String backupId;

        private final long createdAtMillis;

        private final String createdAtUtc;

        private final String sourcePayloadType;

        private final int sourcePayloadVersion;

        private final String encryptedPayloadFormat;

        private final int encryptedPayloadFormatVersion;

        private final int databaseVersion;

        private final String appVersionName;

        private final long appVersionCode;

        private final String compressionType;

        private final String encodingType;

        private final String associatedDataScheme;

        private final int encryptionVersion;

        private final String cipherTransformation;

        private final String kdfAlgorithm;

        private final String prfAlgorithm;

        private final int kdfIterations;

        private final int keyLengthBits;

        private final int gcmTagLengthBits;

        private final String saltBase64;

        private final String initializationVectorBase64;

        private final String hashAlgorithm;

        private final String uncompressedSha256;

        private final String compressedSha256;

        private final String encryptedSha256;

        private final int uncompressedByteCount;

        private final int compressedByteCount;

        private final int encryptedByteCount;

        private final int encodedCharacterCount;

        private final List<String> encryptedChunks;

        private final CloudBackupPayloadBuilder.RecordCounts
                recordCounts;

        private EncryptedCloudBackupPayload(
                @NonNull String firebaseUserId,
                @NonNull String backupId,
                long createdAtMillis,
                @NonNull String createdAtUtc,

                @NonNull String sourcePayloadType,
                int sourcePayloadVersion,

                @NonNull String encryptedPayloadFormat,
                int encryptedPayloadFormatVersion,

                int databaseVersion,
                @NonNull String appVersionName,
                long appVersionCode,

                @NonNull String compressionType,
                @NonNull String encodingType,
                @NonNull String associatedDataScheme,

                int encryptionVersion,
                @NonNull String cipherTransformation,
                @NonNull String kdfAlgorithm,
                @NonNull String prfAlgorithm,
                int kdfIterations,
                int keyLengthBits,
                int gcmTagLengthBits,

                @NonNull String saltBase64,
                @NonNull String initializationVectorBase64,

                @NonNull String hashAlgorithm,
                @NonNull String uncompressedSha256,
                @NonNull String compressedSha256,
                @NonNull String encryptedSha256,

                int uncompressedByteCount,
                int compressedByteCount,
                int encryptedByteCount,
                int encodedCharacterCount,

                @NonNull List<String> encryptedChunks,
                @NonNull CloudBackupPayloadBuilder.RecordCounts
                        recordCounts
        ) {
            this.firebaseUserId =
                    firebaseUserId;

            this.backupId =
                    backupId;

            this.createdAtMillis =
                    createdAtMillis;

            this.createdAtUtc =
                    createdAtUtc;

            this.sourcePayloadType =
                    sourcePayloadType;

            this.sourcePayloadVersion =
                    sourcePayloadVersion;

            this.encryptedPayloadFormat =
                    encryptedPayloadFormat;

            this.encryptedPayloadFormatVersion =
                    encryptedPayloadFormatVersion;

            this.databaseVersion =
                    databaseVersion;

            this.appVersionName =
                    appVersionName;

            this.appVersionCode =
                    appVersionCode;

            this.compressionType =
                    compressionType;

            this.encodingType =
                    encodingType;

            this.associatedDataScheme =
                    associatedDataScheme;

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

            this.hashAlgorithm =
                    hashAlgorithm;

            this.uncompressedSha256 =
                    uncompressedSha256;

            this.compressedSha256 =
                    compressedSha256;

            this.encryptedSha256 =
                    encryptedSha256;

            this.uncompressedByteCount =
                    uncompressedByteCount;

            this.compressedByteCount =
                    compressedByteCount;

            this.encryptedByteCount =
                    encryptedByteCount;

            this.encodedCharacterCount =
                    encodedCharacterCount;

            this.encryptedChunks =
                    Collections.unmodifiableList(
                            new ArrayList<>(
                                    encryptedChunks
                            )
                    );

            this.recordCounts =
                    recordCounts;
        }

        @NonNull
        public String getFirebaseUserId() {
            return firebaseUserId;
        }

        @NonNull
        public String getBackupId() {
            return backupId;
        }

        public long getCreatedAtMillis() {
            return createdAtMillis;
        }

        @NonNull
        public String getCreatedAtUtc() {
            return createdAtUtc;
        }

        @NonNull
        public String getSourcePayloadType() {
            return sourcePayloadType;
        }

        public int getSourcePayloadVersion() {
            return sourcePayloadVersion;
        }

        @NonNull
        public String getEncryptedPayloadFormat() {
            return encryptedPayloadFormat;
        }

        public int getEncryptedPayloadFormatVersion() {
            return encryptedPayloadFormatVersion;
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

        @NonNull
        public String getCompressionType() {
            return compressionType;
        }

        @NonNull
        public String getEncodingType() {
            return encodingType;
        }

        @NonNull
        public String getAssociatedDataScheme() {
            return associatedDataScheme;
        }

        public int getEncryptionVersion() {
            return encryptionVersion;
        }

        @NonNull
        public String getCipherTransformation() {
            return cipherTransformation;
        }

        @NonNull
        public String getKdfAlgorithm() {
            return kdfAlgorithm;
        }

        @NonNull
        public String getPrfAlgorithm() {
            return prfAlgorithm;
        }

        public int getKdfIterations() {
            return kdfIterations;
        }

        public int getKeyLengthBits() {
            return keyLengthBits;
        }

        public int getGcmTagLengthBits() {
            return gcmTagLengthBits;
        }

        @NonNull
        public String getSaltBase64() {
            return saltBase64;
        }

        @NonNull
        public String getInitializationVectorBase64() {
            return initializationVectorBase64;
        }

        @NonNull
        public String getHashAlgorithm() {
            return hashAlgorithm;
        }

        @NonNull
        public String getUncompressedSha256() {
            return uncompressedSha256;
        }

        @NonNull
        public String getCompressedSha256() {
            return compressedSha256;
        }

        @NonNull
        public String getEncryptedSha256() {
            return encryptedSha256;
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

        public int getEncodedCharacterCount() {
            return encodedCharacterCount;
        }

        public int getChunkCount() {
            return encryptedChunks.size();
        }

        @NonNull
        public List<String> getEncryptedChunks() {
            return encryptedChunks;
        }

        @NonNull
        public String getEncryptedChunk(
                int chunkIndex
        ) {
            if (chunkIndex < 0
                    || chunkIndex
                    >= encryptedChunks.size()) {

                throw new IndexOutOfBoundsException(
                        "Invalid encrypted cloud chunk index: "
                                + chunkIndex
                );
            }

            return encryptedChunks.get(
                    chunkIndex
            );
        }

        @NonNull
        public CloudBackupPayloadBuilder.RecordCounts
        getRecordCounts() {

            return recordCounts;
        }

        /**
         * Metadata document + encrypted chunk documents.
         */
        public int getEstimatedFirestoreDocumentCount() {
            return 1
                    + encryptedChunks.size();
        }
    }

    public static final class EncryptedPayloadBuildException
            extends Exception {

        public EncryptedPayloadBuildException(
                @NonNull String message
        ) {
            super(message);
        }

        public EncryptedPayloadBuildException(
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