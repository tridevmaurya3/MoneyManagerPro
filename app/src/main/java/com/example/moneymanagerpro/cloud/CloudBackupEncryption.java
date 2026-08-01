package com.example.moneymanagerpro.cloud;

import android.util.Base64;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.AEADBadTagException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypts and decrypts Money Manager Pro cloud backups.
 *
 * Security design:
 *
 * 1. Backup data is encrypted before it leaves the device.
 * 2. AES-256-GCM provides confidentiality and integrity verification.
 * 3. The encryption key is derived from the user's passphrase using
 *    PBKDF2-HMAC-SHA256.
 * 4. A new random salt and IV are generated for every backup.
 * 5. The Firebase UID and backup ID are bound to the encrypted payload
 *    through authenticated associated data.
 * 6. The passphrase itself is never stored in Firebase, Room,
 *    SharedPreferences or the encrypted backup metadata.
 * 7. A wrong passphrase or modified cloud backup fails authentication.
 *
 * This class contains a deterministic PBKDF2-HMAC-SHA256 implementation
 * so encrypted backups remain compatible with Android API 24 and above.
 */
public final class CloudBackupEncryption {

    public static final int ENCRYPTION_VERSION = 1;

    public static final String CIPHER_TRANSFORMATION =
            "AES/GCM/NoPadding";

    public static final String KEY_ALGORITHM =
            "AES";

    public static final String KDF_ALGORITHM =
            "PBKDF2-HMAC-SHA256-UTF8";

    public static final String PRF_ALGORITHM =
            "HmacSHA256";

    public static final int KEY_LENGTH_BITS =
            256;

    public static final int KEY_LENGTH_BYTES =
            KEY_LENGTH_BITS / 8;

    public static final int GCM_TAG_LENGTH_BITS =
            128;

    public static final int KDF_ITERATIONS =
            210_000;

    public static final int SALT_LENGTH_BYTES =
            16;

    public static final int IV_LENGTH_BYTES =
            12;

    public static final int MINIMUM_PASSPHRASE_LENGTH =
            8;

    public static final int MAXIMUM_PASSPHRASE_LENGTH =
            128;

    /**
     * Current maximum unencrypted backup size supported by this engine.
     *
     * The future cloud uploader will split the encrypted result into
     * smaller Firestore chunks.
     */
    private static final int MAX_PLAINTEXT_BYTES =
            25 * 1024 * 1024;

    private static final int MAX_ENCRYPTED_BYTES =
            MAX_PLAINTEXT_BYTES + 1024;

    private static final int MAX_ASSOCIATED_DATA_LENGTH =
            1024;

    private static final int MAX_BINDING_VALUE_LENGTH =
            256;

    private static final SecureRandom SECURE_RANDOM =
            new SecureRandom();

    private CloudBackupEncryption() {
        // Utility class.
    }

    /**
     * Encrypts backup bytes using a user-provided passphrase.
     *
     * The caller should clear its own passphrase char[] after this method
     * finishes by calling clearSensitiveCharacters(passphrase).
     */
    @NonNull
    public static EncryptedPayload encrypt(
            @NonNull byte[] plaintextBytes,
            @NonNull char[] passphrase,
            @NonNull String associatedData
    ) throws CloudEncryptionException {

        validatePlaintext(
                plaintextBytes
        );

        validatePassphrase(
                passphrase
        );

        validateAssociatedData(
                associatedData
        );

        byte[] salt =
                createRandomBytes(
                        SALT_LENGTH_BYTES
                );

        byte[] initializationVector =
                createRandomBytes(
                        IV_LENGTH_BYTES
                );

        byte[] derivedKeyBytes = null;

        try {
            derivedKeyBytes =
                    deriveKeyBytes(
                            passphrase,
                            salt,
                            KDF_ITERATIONS
                    );

            SecretKeySpec secretKey =
                    new SecretKeySpec(
                            derivedKeyBytes,
                            KEY_ALGORITHM
                    );

            Cipher cipher =
                    Cipher.getInstance(
                            CIPHER_TRANSFORMATION
                    );

            GCMParameterSpec parameterSpec =
                    new GCMParameterSpec(
                            GCM_TAG_LENGTH_BITS,
                            initializationVector
                    );

            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    secretKey,
                    parameterSpec
            );

            cipher.updateAAD(
                    associatedData.getBytes(
                            StandardCharsets.UTF_8
                    )
            );

            byte[] encryptedBytes =
                    cipher.doFinal(
                            plaintextBytes
                    );

            if (encryptedBytes.length <= 0
                    || encryptedBytes.length
                    > MAX_ENCRYPTED_BYTES) {

                clearSensitiveBytes(
                        encryptedBytes
                );

                throw new CloudEncryptionException(
                        "Encrypted cloud backup size is invalid."
                );
            }

            return new EncryptedPayload(
                    ENCRYPTION_VERSION,
                    CIPHER_TRANSFORMATION,
                    KDF_ALGORITHM,
                    PRF_ALGORITHM,
                    KDF_ITERATIONS,
                    KEY_LENGTH_BITS,
                    GCM_TAG_LENGTH_BITS,
                    salt,
                    initializationVector,
                    encryptedBytes,
                    plaintextBytes.length
            );

        } catch (CloudEncryptionException exception) {
            throw exception;

        } catch (GeneralSecurityException exception) {
            throw new CloudEncryptionException(
                    "Cloud backup encryption failed.",
                    exception
            );

        } finally {
            clearSensitiveBytes(
                    derivedKeyBytes
            );
        }
    }

    /**
     * Decrypts and authenticates an encrypted cloud backup.
     *
     * A wrong passphrase, changed associated data or modified encrypted
     * content causes authentication to fail.
     */
    @NonNull
    public static byte[] decrypt(
            @NonNull EncryptedPayload encryptedPayload,
            @NonNull char[] passphrase,
            @NonNull String associatedData
    ) throws CloudEncryptionException {

        validatePassphrase(
                passphrase
        );

        validateAssociatedData(
                associatedData
        );

        validateEncryptedPayload(
                encryptedPayload
        );

        byte[] derivedKeyBytes = null;

        try {
            derivedKeyBytes =
                    deriveKeyBytes(
                            passphrase,
                            encryptedPayload.getSalt(),
                            encryptedPayload.getKdfIterations()
                    );

            SecretKeySpec secretKey =
                    new SecretKeySpec(
                            derivedKeyBytes,
                            KEY_ALGORITHM
                    );

            Cipher cipher =
                    Cipher.getInstance(
                            encryptedPayload.getCipherTransformation()
                    );

            GCMParameterSpec parameterSpec =
                    new GCMParameterSpec(
                            encryptedPayload.getGcmTagLengthBits(),
                            encryptedPayload.getInitializationVector()
                    );

            cipher.init(
                    Cipher.DECRYPT_MODE,
                    secretKey,
                    parameterSpec
            );

            cipher.updateAAD(
                    associatedData.getBytes(
                            StandardCharsets.UTF_8
                    )
            );

            byte[] decryptedBytes =
                    cipher.doFinal(
                            encryptedPayload.getEncryptedBytes()
                    );

            if (decryptedBytes.length <= 0
                    || decryptedBytes.length
                    > MAX_PLAINTEXT_BYTES) {

                clearSensitiveBytes(
                        decryptedBytes
                );

                throw new CloudEncryptionException(
                        "Decrypted cloud backup size is invalid."
                );
            }

            int expectedPlaintextBytes =
                    encryptedPayload.getOriginalPlaintextBytes();

            if (expectedPlaintextBytes > 0
                    && decryptedBytes.length
                    != expectedPlaintextBytes) {

                clearSensitiveBytes(
                        decryptedBytes
                );

                throw new CloudEncryptionException(
                        "Decrypted cloud backup size does not match "
                                + "its verified metadata."
                );
            }

            return decryptedBytes;

        } catch (AEADBadTagException exception) {
            throw new InvalidPassphraseException(
                    "Cloud backup could not be unlocked. "
                            + "The passphrase may be incorrect or "
                            + "the cloud backup may be corrupted.",
                    exception
            );

        } catch (BadPaddingException exception) {
            /*
             * Some Android crypto providers report an invalid GCM tag
             * as BadPaddingException instead of AEADBadTagException.
             */
            throw new InvalidPassphraseException(
                    "Cloud backup could not be unlocked. "
                            + "The passphrase may be incorrect or "
                            + "the cloud backup may be corrupted.",
                    exception
            );

        } catch (CloudEncryptionException exception) {
            throw exception;

        } catch (GeneralSecurityException exception) {
            throw new CloudEncryptionException(
                    "Cloud backup decryption failed.",
                    exception
            );

        } finally {
            clearSensitiveBytes(
                    derivedKeyBytes
            );
        }
    }

    /**
     * Recreates encrypted payload metadata after reading it from
     * Firebase Firestore.
     */
    @NonNull
    public static EncryptedPayload fromBase64(
            int encryptionVersion,
            @NonNull String cipherTransformation,
            @NonNull String kdfAlgorithm,
            @NonNull String prfAlgorithm,
            int kdfIterations,
            int keyLengthBits,
            int gcmTagLengthBits,
            @NonNull String saltBase64,
            @NonNull String initializationVectorBase64,
            @NonNull String encryptedPayloadBase64,
            int originalPlaintextBytes
    ) throws CloudEncryptionException {

        byte[] salt;
        byte[] initializationVector;
        byte[] encryptedBytes;

        try {
            salt =
                    Base64.decode(
                            requireBase64Value(
                                    saltBase64,
                                    "Encryption salt"
                            ),
                            Base64.DEFAULT
                    );

            initializationVector =
                    Base64.decode(
                            requireBase64Value(
                                    initializationVectorBase64,
                                    "Initialization vector"
                            ),
                            Base64.DEFAULT
                    );

            encryptedBytes =
                    Base64.decode(
                            requireBase64Value(
                                    encryptedPayloadBase64,
                                    "Encrypted payload"
                            ),
                            Base64.DEFAULT
                    );

        } catch (IllegalArgumentException exception) {
            throw new CloudEncryptionException(
                    "Encrypted cloud backup contains invalid Base64 data.",
                    exception
            );
        }

        EncryptedPayload encryptedPayload =
                new EncryptedPayload(
                        encryptionVersion,
                        cipherTransformation,
                        kdfAlgorithm,
                        prfAlgorithm,
                        kdfIterations,
                        keyLengthBits,
                        gcmTagLengthBits,
                        salt,
                        initializationVector,
                        encryptedBytes,
                        originalPlaintextBytes
                );

        validateEncryptedPayload(
                encryptedPayload
        );

        return encryptedPayload;
    }

    /**
     * Creates deterministic authenticated data binding one encrypted
     * backup to one Firebase account and one backup ID.
     *
     * This data is authenticated by AES-GCM but is not encrypted.
     * It contains only technical identifiers.
     */
    @NonNull
    public static String createAssociatedData(
            @NonNull String firebaseUserId,
            @NonNull String backupId
    ) throws CloudEncryptionException {

        String safeUserId =
                validateBindingValue(
                        firebaseUserId,
                        "Cloud account UID"
                );

        String safeBackupId =
                validateBindingValue(
                        backupId,
                        "Cloud backup ID"
                );

        String associatedData =
                "money_manager_pro"
                        + "|cloud_backup"
                        + "|encryption_version="
                        + ENCRYPTION_VERSION
                        + "|uid_length="
                        + safeUserId.length()
                        + "|uid="
                        + safeUserId
                        + "|backup_id_length="
                        + safeBackupId.length()
                        + "|backup_id="
                        + safeBackupId;

        validateAssociatedData(
                associatedData
        );

        return associatedData;
    }

    /**
     * Clears a passphrase supplied as a mutable character array.
     */
    public static void clearSensitiveCharacters(
            char[] characters
    ) {
        if (characters == null) {
            return;
        }

        Arrays.fill(
                characters,
                '\0'
        );
    }

    /**
     * Clears a mutable byte array containing sensitive information.
     */
    public static void clearSensitiveBytes(
            byte[] bytes
    ) {
        if (bytes == null) {
            return;
        }

        Arrays.fill(
                bytes,
                (byte) 0
        );
    }

    /**
     * Deterministic PBKDF2-HMAC-SHA256 implementation.
     *
     * Password characters are converted to UTF-8 bytes. The same
     * implementation is used on every Android version so a backup
     * created on one supported phone can be decrypted on another.
     */
    @NonNull
    private static byte[] deriveKeyBytes(
            @NonNull char[] passphrase,
            @NonNull byte[] salt,
            int iterationCount
    ) throws GeneralSecurityException {

        if (iterationCount <= 0) {
            throw new GeneralSecurityException(
                    "Invalid key derivation iteration count."
            );
        }

        byte[] passwordBytes = null;
        byte[] saltAndBlockIndex = null;
        byte[] derivedKey =
                new byte[KEY_LENGTH_BYTES];

        try {
            passwordBytes =
                    encodePassphraseAsUtf8(
                            passphrase
                    );

            Mac mac =
                    Mac.getInstance(
                            PRF_ALGORITHM
                    );

            SecretKeySpec passwordKey =
                    new SecretKeySpec(
                            passwordBytes,
                            PRF_ALGORITHM
                    );

            mac.init(
                    passwordKey
            );

            int hashLength =
                    mac.getMacLength();

            if (hashLength <= 0) {
                throw new GeneralSecurityException(
                        "Invalid HMAC output length."
                );
            }

            int requiredBlocks =
                    (KEY_LENGTH_BYTES
                            + hashLength
                            - 1)
                            / hashLength;

            saltAndBlockIndex =
                    new byte[
                            salt.length + 4
                            ];

            System.arraycopy(
                    salt,
                    0,
                    saltAndBlockIndex,
                    0,
                    salt.length
            );

            int destinationOffset = 0;

            for (int blockIndex = 1;
                 blockIndex <= requiredBlocks;
                 blockIndex++) {

                writeBigEndianInteger(
                        saltAndBlockIndex,
                        salt.length,
                        blockIndex
                );

                byte[] currentU =
                        new byte[hashLength];

                byte[] nextU =
                        new byte[hashLength];

                byte[] blockResult =
                        new byte[hashLength];

                try {
                    mac.update(
                            saltAndBlockIndex
                    );

                    mac.doFinal(
                            currentU,
                            0
                    );

                    System.arraycopy(
                            currentU,
                            0,
                            blockResult,
                            0,
                            hashLength
                    );

                    for (int iteration = 1;
                         iteration < iterationCount;
                         iteration++) {

                        mac.update(
                                currentU
                        );

                        mac.doFinal(
                                nextU,
                                0
                        );

                        for (int byteIndex = 0;
                             byteIndex < hashLength;
                             byteIndex++) {

                            blockResult[byteIndex] ^=
                                    nextU[byteIndex];
                        }

                        byte[] swap =
                                currentU;

                        currentU =
                                nextU;

                        nextU =
                                swap;
                    }

                    int bytesToCopy =
                            Math.min(
                                    hashLength,
                                    KEY_LENGTH_BYTES
                                            - destinationOffset
                            );

                    System.arraycopy(
                            blockResult,
                            0,
                            derivedKey,
                            destinationOffset,
                            bytesToCopy
                    );

                    destinationOffset +=
                            bytesToCopy;

                } finally {
                    clearSensitiveBytes(
                            currentU
                    );

                    clearSensitiveBytes(
                            nextU
                    );

                    clearSensitiveBytes(
                            blockResult
                    );
                }
            }

            return derivedKey;

        } catch (GeneralSecurityException exception) {
            clearSensitiveBytes(
                    derivedKey
            );

            throw exception;

        } finally {
            clearSensitiveBytes(
                    passwordBytes
            );

            clearSensitiveBytes(
                    saltAndBlockIndex
            );
        }
    }

    /**
     * Converts passphrase characters to UTF-8 without creating an
     * immutable Java String containing the passphrase.
     */
    @NonNull
    private static byte[] encodePassphraseAsUtf8(
            @NonNull char[] passphrase
    ) throws GeneralSecurityException {

        char[] passphraseCopy =
                Arrays.copyOf(
                        passphrase,
                        passphrase.length
                );

        ByteBuffer encodedBuffer = null;

        try {
            encodedBuffer =
                    StandardCharsets.UTF_8
                            .newEncoder()
                            .encode(
                                    CharBuffer.wrap(
                                            passphraseCopy
                                    )
                            );

            byte[] encodedBytes =
                    new byte[
                            encodedBuffer.remaining()
                            ];

            encodedBuffer.get(
                    encodedBytes
            );

            return encodedBytes;

        } catch (CharacterCodingException exception) {
            throw new GeneralSecurityException(
                    "Cloud backup passphrase encoding failed.",
                    exception
            );

        } finally {
            Arrays.fill(
                    passphraseCopy,
                    '\0'
            );

            if (encodedBuffer != null
                    && encodedBuffer.hasArray()) {

                Arrays.fill(
                        encodedBuffer.array(),
                        (byte) 0
                );
            }
        }
    }

    private static void writeBigEndianInteger(
            @NonNull byte[] target,
            int offset,
            int value
    ) {
        target[offset] =
                (byte) (
                        value >>> 24
                );

        target[offset + 1] =
                (byte) (
                        value >>> 16
                );

        target[offset + 2] =
                (byte) (
                        value >>> 8
                );

        target[offset + 3] =
                (byte) value;
    }

    @NonNull
    private static byte[] createRandomBytes(
            int byteCount
    ) {
        byte[] randomBytes =
                new byte[byteCount];

        SECURE_RANDOM.nextBytes(
                randomBytes
        );

        return randomBytes;
    }

    private static void validatePlaintext(
            @NonNull byte[] plaintextBytes
    ) throws CloudEncryptionException {

        if (plaintextBytes.length <= 0) {
            throw new CloudEncryptionException(
                    "Cloud backup data is empty."
            );
        }

        if (plaintextBytes.length
                > MAX_PLAINTEXT_BYTES) {

            throw new CloudEncryptionException(
                    "Cloud backup data exceeds the supported 25 MB limit."
            );
        }
    }

    private static void validatePassphrase(
            @NonNull char[] passphrase
    ) throws CloudEncryptionException {

        if (passphrase.length
                < MINIMUM_PASSPHRASE_LENGTH) {

            throw new CloudEncryptionException(
                    "Cloud backup passphrase must contain at least "
                            + MINIMUM_PASSPHRASE_LENGTH
                            + " characters."
            );
        }

        if (passphrase.length
                > MAXIMUM_PASSPHRASE_LENGTH) {

            throw new CloudEncryptionException(
                    "Cloud backup passphrase exceeds the supported length."
            );
        }

        boolean containsVisibleCharacter =
                false;

        for (char character : passphrase) {
            if (!Character.isWhitespace(
                    character
            )) {
                containsVisibleCharacter =
                        true;

                break;
            }
        }

        if (!containsVisibleCharacter) {
            throw new CloudEncryptionException(
                    "Cloud backup passphrase cannot contain only spaces."
            );
        }
    }

    private static void validateAssociatedData(
            @NonNull String associatedData
    ) throws CloudEncryptionException {

        String cleanAssociatedData =
                associatedData.trim();

        if (cleanAssociatedData.isEmpty()) {
            throw new CloudEncryptionException(
                    "Cloud backup associated data is unavailable."
            );
        }

        byte[] associatedDataBytes =
                cleanAssociatedData.getBytes(
                        StandardCharsets.UTF_8
                );

        if (associatedDataBytes.length
                > MAX_ASSOCIATED_DATA_LENGTH) {

            throw new CloudEncryptionException(
                    "Cloud backup associated data exceeds "
                            + "the supported size."
            );
        }
    }

    @NonNull
    private static String validateBindingValue(
            @NonNull String value,
            @NonNull String label
    ) throws CloudEncryptionException {

        String cleanValue =
                value.trim();

        if (cleanValue.isEmpty()) {
            throw new CloudEncryptionException(
                    label + " is unavailable."
            );
        }

        if (cleanValue.length()
                > MAX_BINDING_VALUE_LENGTH) {

            throw new CloudEncryptionException(
                    label + " exceeds the supported length."
            );
        }

        if (cleanValue.indexOf('\n') >= 0
                || cleanValue.indexOf('\r') >= 0
                || cleanValue.indexOf('\0') >= 0) {

            throw new CloudEncryptionException(
                    label + " contains unsupported characters."
            );
        }

        return cleanValue;
    }

    @NonNull
    private static String requireBase64Value(
            @NonNull String value,
            @NonNull String label
    ) throws CloudEncryptionException {

        String cleanValue =
                value.trim();

        if (cleanValue.isEmpty()) {
            throw new CloudEncryptionException(
                    label + " is missing."
            );
        }

        return cleanValue;
    }

    private static void validateEncryptedPayload(
            @NonNull EncryptedPayload encryptedPayload
    ) throws CloudEncryptionException {

        if (encryptedPayload.getEncryptionVersion()
                != ENCRYPTION_VERSION) {

            throw new CloudEncryptionException(
                    "Unsupported cloud backup encryption version."
            );
        }

        if (!CIPHER_TRANSFORMATION.equals(
                encryptedPayload.getCipherTransformation()
        )) {
            throw new CloudEncryptionException(
                    "Unsupported cloud backup cipher."
            );
        }

        if (!KDF_ALGORITHM.equals(
                encryptedPayload.getKdfAlgorithm()
        )) {
            throw new CloudEncryptionException(
                    "Unsupported cloud backup key derivation algorithm."
            );
        }

        if (!PRF_ALGORITHM.equals(
                encryptedPayload.getPrfAlgorithm()
        )) {
            throw new CloudEncryptionException(
                    "Unsupported cloud backup HMAC algorithm."
            );
        }

        if (encryptedPayload.getKdfIterations()
                != KDF_ITERATIONS) {

            throw new CloudEncryptionException(
                    "Unsupported cloud backup key derivation settings."
            );
        }

        if (encryptedPayload.getKeyLengthBits()
                != KEY_LENGTH_BITS) {

            throw new CloudEncryptionException(
                    "Unsupported cloud backup key size."
            );
        }

        if (encryptedPayload.getGcmTagLengthBits()
                != GCM_TAG_LENGTH_BITS) {

            throw new CloudEncryptionException(
                    "Unsupported cloud backup authentication tag size."
            );
        }

        byte[] salt =
                encryptedPayload.getSalt();

        try {
            if (salt.length
                    != SALT_LENGTH_BYTES) {

                throw new CloudEncryptionException(
                        "Cloud backup encryption salt is invalid."
                );
            }

        } finally {
            clearSensitiveBytes(
                    salt
            );
        }

        byte[] initializationVector =
                encryptedPayload.getInitializationVector();

        try {
            if (initializationVector.length
                    != IV_LENGTH_BYTES) {

                throw new CloudEncryptionException(
                        "Cloud backup initialization vector is invalid."
                );
            }

        } finally {
            clearSensitiveBytes(
                    initializationVector
            );
        }

        byte[] encryptedBytes =
                encryptedPayload.getEncryptedBytes();

        try {
            int encryptedByteCount =
                    encryptedBytes.length;

            if (encryptedByteCount <= 0
                    || encryptedByteCount
                    > MAX_ENCRYPTED_BYTES) {

                throw new CloudEncryptionException(
                        "Encrypted cloud backup size is invalid."
                );
            }

        } finally {
            clearSensitiveBytes(
                    encryptedBytes
            );
        }

        int originalByteCount =
                encryptedPayload.getOriginalPlaintextBytes();

        if (originalByteCount <= 0
                || originalByteCount
                > MAX_PLAINTEXT_BYTES) {

            throw new CloudEncryptionException(
                    "Original cloud backup size is invalid."
            );
        }
    }

    /**
     * Immutable encrypted backup container.
     *
     * All byte-array getters return defensive copies.
     */
    public static final class EncryptedPayload {

        private final int encryptionVersion;

        private final String cipherTransformation;

        private final String kdfAlgorithm;

        private final String prfAlgorithm;

        private final int kdfIterations;

        private final int keyLengthBits;

        private final int gcmTagLengthBits;

        private final byte[] salt;

        private final byte[] initializationVector;

        private final byte[] encryptedBytes;

        private final int originalPlaintextBytes;

        private EncryptedPayload(
                int encryptionVersion,
                @NonNull String cipherTransformation,
                @NonNull String kdfAlgorithm,
                @NonNull String prfAlgorithm,
                int kdfIterations,
                int keyLengthBits,
                int gcmTagLengthBits,
                @NonNull byte[] salt,
                @NonNull byte[] initializationVector,
                @NonNull byte[] encryptedBytes,
                int originalPlaintextBytes
        ) {
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

            this.salt =
                    Arrays.copyOf(
                            salt,
                            salt.length
                    );

            this.initializationVector =
                    Arrays.copyOf(
                            initializationVector,
                            initializationVector.length
                    );

            this.encryptedBytes =
                    Arrays.copyOf(
                            encryptedBytes,
                            encryptedBytes.length
                    );

            this.originalPlaintextBytes =
                    originalPlaintextBytes;
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
        public byte[] getSalt() {
            return Arrays.copyOf(
                    salt,
                    salt.length
            );
        }

        @NonNull
        public String getSaltBase64() {
            return Base64.encodeToString(
                    salt,
                    Base64.NO_WRAP
            );
        }

        @NonNull
        public byte[] getInitializationVector() {
            return Arrays.copyOf(
                    initializationVector,
                    initializationVector.length
            );
        }

        @NonNull
        public String getInitializationVectorBase64() {
            return Base64.encodeToString(
                    initializationVector,
                    Base64.NO_WRAP
            );
        }

        @NonNull
        public byte[] getEncryptedBytes() {
            return Arrays.copyOf(
                    encryptedBytes,
                    encryptedBytes.length
            );
        }

        @NonNull
        public String getEncryptedPayloadBase64() {
            return Base64.encodeToString(
                    encryptedBytes,
                    Base64.NO_WRAP
            );
        }

        public int getEncryptedByteCount() {
            return encryptedBytes.length;
        }

        public int getOriginalPlaintextBytes() {
            return originalPlaintextBytes;
        }
    }

    public static class CloudEncryptionException
            extends Exception {

        public CloudEncryptionException(
                @NonNull String message
        ) {
            super(message);
        }

        public CloudEncryptionException(
                @NonNull String message,
                @NonNull Throwable cause
        ) {
            super(
                    message,
                    cause
            );
        }
    }

    public static final class InvalidPassphraseException
            extends CloudEncryptionException {

        public InvalidPassphraseException(
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