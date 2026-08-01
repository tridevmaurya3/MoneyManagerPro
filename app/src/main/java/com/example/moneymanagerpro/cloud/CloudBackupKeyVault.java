package com.example.moneymanagerpro.cloud;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Locale;

import javax.crypto.AEADBadTagException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Securely stores the cloud-backup recovery passphrase on one Android
 * device using a non-exportable Android Keystore AES key.
 *
 * Security design:
 *
 * 1. The recovery passphrase is never stored as plain text.
 * 2. A separate Android Keystore AES key is created for each Firebase UID.
 * 3. The Keystore key cannot be exported from the Android Keystore.
 * 4. AES-GCM protects both confidentiality and integrity.
 * 5. The encrypted value is bound to:
 *
 *    - Money Manager Pro
 *    - the current Firebase UID
 *    - the current key-vault format version
 *
 * 6. A backup worker can read the passphrase without opening the UI.
 * 7. Another Firebase account cannot reuse the saved encrypted value.
 * 8. Uninstalling the app may delete the Keystore key, while the encrypted
 *    cloud backup remains safe and can be unlocked again by entering the
 *    recovery passphrase.
 *
 * Important:
 *
 * This vault is intended for background automatic backup.
 * Therefore the Keystore key does not require fingerprint or device
 * authentication every time it is used.
 */
public final class CloudBackupKeyVault {

    public static final int VAULT_VERSION = 1;

    private static final String ANDROID_KEYSTORE =
            "AndroidKeyStore";

    private static final String CIPHER_TRANSFORMATION =
            "AES/GCM/NoPadding";

    private static final String KEY_ALGORITHM =
            KeyProperties.KEY_ALGORITHM_AES;

    private static final int KEY_SIZE_BITS =
            256;

    private static final int GCM_TAG_LENGTH_BITS =
            128;

    private static final int IV_LENGTH_BYTES =
            12;

    private static final int MINIMUM_PASSPHRASE_LENGTH =
            CloudBackupEncryption.MINIMUM_PASSPHRASE_LENGTH;

    private static final int MAXIMUM_PASSPHRASE_LENGTH =
            CloudBackupEncryption.MAXIMUM_PASSPHRASE_LENGTH;

    private static final int MAX_ENCODED_VALUE_LENGTH =
            4096;

    private static final String PREFERENCES_NAME =
            "money_manager_cloud_key_vault";

    private static final String KEY_PREFIX_VERSION =
            "vault_version_";

    private static final String KEY_PREFIX_OWNER_HASH =
            "owner_hash_";

    private static final String KEY_PREFIX_IV =
            "encrypted_iv_";

    private static final String KEY_PREFIX_CIPHERTEXT =
            "encrypted_passphrase_";

    private static final String KEY_PREFIX_SAVED_AT =
            "saved_at_";

    private static final String KEY_ALIAS_PREFIX =
            "money_manager_cloud_backup_key_";

    private static final String ASSOCIATED_DATA_PREFIX =
            "money_manager_pro"
                    + "|cloud_passphrase_vault"
                    + "|version=";

    private static final SecureRandom SECURE_RANDOM =
            new SecureRandom();

    private final Context applicationContext;

    private final SharedPreferences preferences;

    public CloudBackupKeyVault(
            @NonNull Context context
    ) {
        applicationContext =
                context.getApplicationContext();

        preferences =
                applicationContext.getSharedPreferences(
                        PREFERENCES_NAME,
                        Context.MODE_PRIVATE
                );
    }

    /**
     * Encrypts and saves a recovery passphrase for one Firebase account.
     *
     * Call this only after:
     *
     * 1. the Firebase account has been verified,
     * 2. the user has entered the passphrase,
     * 3. the passphrase has successfully created or unlocked a cloud backup.
     *
     * The caller should clear its original char[] after this method returns.
     */
    public synchronized void savePassphrase(
            @NonNull String firebaseUserId,
            @NonNull char[] passphrase
    ) throws KeyVaultException {

        String verifiedUserId =
                validateFirebaseUserId(
                        firebaseUserId
                );

        validatePassphrase(
                passphrase
        );

        String accountHash =
                createAccountHash(
                        verifiedUserId
                );

        String keyAlias =
                createKeyAlias(
                        accountHash
                );

        char[] passphraseCopy =
                Arrays.copyOf(
                        passphrase,
                        passphrase.length
                );

        byte[] passphraseBytes = null;
        byte[] initializationVector = null;
        byte[] encryptedBytes = null;

        try {
            passphraseBytes =
                    encodeCharactersAsUtf8(
                            passphraseCopy
                    );

            SecretKey secretKey =
                    getOrCreateSecretKey(
                            keyAlias
                    );

            Cipher cipher =
                    Cipher.getInstance(
                            CIPHER_TRANSFORMATION
                    );

            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    secretKey
            );

            initializationVector =
                    cipher.getIV();

            if (initializationVector == null
                    || initializationVector.length
                    != IV_LENGTH_BYTES) {

                throw new KeyVaultException(
                        "Android Keystore returned an invalid "
                                + "encryption initialization vector."
                );
            }

            cipher.updateAAD(
                    createAssociatedData(
                            verifiedUserId
                    )
            );

            encryptedBytes =
                    cipher.doFinal(
                            passphraseBytes
                    );

            if (encryptedBytes.length <= 0) {
                throw new KeyVaultException(
                        "Encrypted cloud-backup passphrase is empty."
                );
            }

            String encodedIv =
                    Base64.encodeToString(
                            initializationVector,
                            Base64.NO_WRAP
                    );

            String encodedCiphertext =
                    Base64.encodeToString(
                            encryptedBytes,
                            Base64.NO_WRAP
                    );

            if (encodedIv.length()
                    > MAX_ENCODED_VALUE_LENGTH
                    || encodedCiphertext.length()
                    > MAX_ENCODED_VALUE_LENGTH) {

                throw new KeyVaultException(
                        "Encrypted cloud-backup passphrase "
                                + "exceeds the supported size."
                );
            }

            boolean saved =
                    preferences
                            .edit()
                            .putInt(
                                    createPreferenceKey(
                                            KEY_PREFIX_VERSION,
                                            accountHash
                                    ),
                                    VAULT_VERSION
                            )
                            .putString(
                                    createPreferenceKey(
                                            KEY_PREFIX_OWNER_HASH,
                                            accountHash
                                    ),
                                    accountHash
                            )
                            .putString(
                                    createPreferenceKey(
                                            KEY_PREFIX_IV,
                                            accountHash
                                    ),
                                    encodedIv
                            )
                            .putString(
                                    createPreferenceKey(
                                            KEY_PREFIX_CIPHERTEXT,
                                            accountHash
                                    ),
                                    encodedCiphertext
                            )
                            .putLong(
                                    createPreferenceKey(
                                            KEY_PREFIX_SAVED_AT,
                                            accountHash
                                    ),
                                    System.currentTimeMillis()
                            )
                            .commit();

            if (!saved) {
                throw new KeyVaultException(
                        "Encrypted cloud-backup passphrase "
                                + "could not be saved on this device."
                );
            }

        } catch (KeyVaultException exception) {
            throw exception;

        } catch (GeneralSecurityException exception) {
            throw new KeyVaultException(
                    "Android Keystore could not encrypt the "
                            + "cloud-backup passphrase.",
                    exception
            );

        } finally {
            CloudBackupEncryption.clearSensitiveCharacters(
                    passphraseCopy
            );

            CloudBackupEncryption.clearSensitiveBytes(
                    passphraseBytes
            );

            CloudBackupEncryption.clearSensitiveBytes(
                    initializationVector
            );

            CloudBackupEncryption.clearSensitiveBytes(
                    encryptedBytes
            );
        }
    }

    /**
     * Decrypts the saved passphrase for one Firebase account.
     *
     * The returned char[] must be cleared by the caller after backup or
     * restore finishes:
     *
     * CloudBackupEncryption.clearSensitiveCharacters(passphrase);
     */
    @NonNull
    public synchronized char[] readPassphrase(
            @NonNull String firebaseUserId
    ) throws KeyVaultException {

        String verifiedUserId =
                validateFirebaseUserId(
                        firebaseUserId
                );

        String accountHash =
                createAccountHash(
                        verifiedUserId
                );

        VaultRecord vaultRecord =
                readAndValidateVaultRecord(
                        accountHash
                );

        String keyAlias =
                createKeyAlias(
                        accountHash
                );

        byte[] initializationVector = null;
        byte[] encryptedBytes = null;
        byte[] decryptedBytes = null;
        char[] decryptedCharacters = null;

        try {
            SecretKey secretKey =
                    getExistingSecretKey(
                            keyAlias
                    );

            initializationVector =
                    decodeBase64(
                            vaultRecord.initializationVectorBase64,
                            "Cloud-backup key-vault initialization vector"
                    );

            encryptedBytes =
                    decodeBase64(
                            vaultRecord.encryptedPassphraseBase64,
                            "Encrypted cloud-backup passphrase"
                    );

            if (initializationVector.length
                    != IV_LENGTH_BYTES) {

                throw new CorruptedKeyVaultException(
                        "Cloud-backup key-vault initialization "
                                + "vector is invalid."
                );
            }

            if (encryptedBytes.length <= 0) {
                throw new CorruptedKeyVaultException(
                        "Encrypted cloud-backup passphrase is empty."
                );
            }

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
                    Cipher.DECRYPT_MODE,
                    secretKey,
                    parameterSpec
            );

            cipher.updateAAD(
                    createAssociatedData(
                            verifiedUserId
                    )
            );

            decryptedBytes =
                    cipher.doFinal(
                            encryptedBytes
                    );

            decryptedCharacters =
                    decodeUtf8AsCharacters(
                            decryptedBytes
                    );

            validatePassphrase(
                    decryptedCharacters
            );

            char[] result =
                    Arrays.copyOf(
                            decryptedCharacters,
                            decryptedCharacters.length
                    );

            return result;

        } catch (AEADBadTagException exception) {
            clearAccountVaultData(
                    accountHash,
                    true
            );

            throw new CorruptedKeyVaultException(
                    "Saved cloud-backup passphrase could not be "
                            + "verified. Enter the recovery passphrase again.",
                    exception
            );

        } catch (BadPaddingException exception) {
            clearAccountVaultData(
                    accountHash,
                    true
            );

            throw new CorruptedKeyVaultException(
                    "Saved cloud-backup passphrase could not be "
                            + "verified. Enter the recovery passphrase again.",
                    exception
            );

        } catch (KeyVaultException exception) {
            throw exception;

        } catch (GeneralSecurityException exception) {
            clearAccountVaultData(
                    accountHash,
                    true
            );

            throw new KeyVaultException(
                    "Android Keystore could not unlock the saved "
                            + "cloud-backup passphrase. Enter it again.",
                    exception
            );

        } finally {
            CloudBackupEncryption.clearSensitiveBytes(
                    initializationVector
            );

            CloudBackupEncryption.clearSensitiveBytes(
                    encryptedBytes
            );

            CloudBackupEncryption.clearSensitiveBytes(
                    decryptedBytes
            );

            CloudBackupEncryption.clearSensitiveCharacters(
                    decryptedCharacters
            );
        }
    }

    /**
     * Returns true only when encrypted vault data and its Android
     * Keystore key both exist.
     */
    public synchronized boolean hasSavedPassphrase(
            @NonNull String firebaseUserId
    ) {
        try {
            String verifiedUserId =
                    validateFirebaseUserId(
                            firebaseUserId
                    );

            String accountHash =
                    createAccountHash(
                            verifiedUserId
                    );

            readAndValidateVaultRecord(
                    accountHash
            );

            return containsSecretKey(
                    createKeyAlias(
                            accountHash
                    )
            );

        } catch (Exception exception) {
            return false;
        }
    }

    /**
     * Returns the time at which the encrypted passphrase was last saved.
     *
     * Zero means no valid timestamp is available.
     */
    public synchronized long getSavedAtMillis(
            @NonNull String firebaseUserId
    ) {
        try {
            String verifiedUserId =
                    validateFirebaseUserId(
                            firebaseUserId
                    );

            String accountHash =
                    createAccountHash(
                            verifiedUserId
                    );

            VaultRecord vaultRecord =
                    readAndValidateVaultRecord(
                            accountHash
                    );

            return vaultRecord.savedAtMillis;

        } catch (Exception exception) {
            return 0L;
        }
    }

    /**
     * Permanently removes the locally remembered recovery passphrase for
     * one Firebase account.
     *
     * This does not delete the encrypted Firestore backup.
     */
    public synchronized void clearPassphrase(
            @NonNull String firebaseUserId
    ) throws KeyVaultException {

        String verifiedUserId =
                validateFirebaseUserId(
                        firebaseUserId
                );

        String accountHash =
                createAccountHash(
                        verifiedUserId
                );

        clearAccountVaultData(
                accountHash,
                true
        );
    }

    /**
     * Removes all locally remembered cloud-backup passphrases created by
     * this app.
     *
     * It does not delete any Firebase cloud backup or Firebase account.
     */
    public synchronized void clearAllLocalVaultData()
            throws KeyVaultException {

        boolean preferencesCleared =
                preferences
                        .edit()
                        .clear()
                        .commit();

        KeyVaultException failure = null;

        if (!preferencesCleared) {
            failure =
                    new KeyVaultException(
                            "Local cloud-backup key-vault settings "
                                    + "could not be cleared."
                    );
        }

        try {
            KeyStore keyStore =
                    loadAndroidKeyStore();

            java.util.Enumeration<String> aliases =
                    keyStore.aliases();

            while (aliases.hasMoreElements()) {
                String alias =
                        aliases.nextElement();

                if (alias != null
                        && alias.startsWith(
                        KEY_ALIAS_PREFIX
                )) {
                    keyStore.deleteEntry(
                            alias
                    );
                }
            }

        } catch (Exception exception) {
            if (failure == null) {
                failure =
                        new KeyVaultException(
                                "Android Keystore cloud-backup keys "
                                        + "could not be cleared.",
                                exception
                        );

            } else {
                failure.addSuppressed(
                        exception
                );
            }
        }

        if (failure != null) {
            throw failure;
        }
    }

    /**
     * Performs a full save/read comparison without exposing the stored
     * value to the caller.
     *
     * The future cloud-backup setup screen can call this immediately
     * after saving a passphrase.
     */
    public synchronized boolean verifySavedPassphrase(
            @NonNull String firebaseUserId,
            @NonNull char[] expectedPassphrase
    ) throws KeyVaultException {

        validatePassphrase(
                expectedPassphrase
        );

        char[] savedPassphrase =
                readPassphrase(
                        firebaseUserId
                );

        try {
            return constantTimeCharactersEqual(
                    expectedPassphrase,
                    savedPassphrase
            );

        } finally {
            CloudBackupEncryption.clearSensitiveCharacters(
                    savedPassphrase
            );
        }
    }

    @NonNull
    private SecretKey getOrCreateSecretKey(
            @NonNull String keyAlias
    ) throws GeneralSecurityException,
            KeyVaultException {

        KeyStore keyStore =
                loadAndroidKeyStore();

        java.security.Key existingKey =
                keyStore.getKey(
                        keyAlias,
                        null
                );

        if (existingKey instanceof SecretKey) {
            return (SecretKey) existingKey;
        }

        if (existingKey != null) {
            keyStore.deleteEntry(
                    keyAlias
            );
        }

        KeyGenerator keyGenerator =
                KeyGenerator.getInstance(
                        KEY_ALGORITHM,
                        ANDROID_KEYSTORE
                );

        KeyGenParameterSpec keySpec =
                new KeyGenParameterSpec.Builder(
                        keyAlias,
                        KeyProperties.PURPOSE_ENCRYPT
                                | KeyProperties.PURPOSE_DECRYPT
                )
                        .setBlockModes(
                                KeyProperties.BLOCK_MODE_GCM
                        )
                        .setEncryptionPaddings(
                                KeyProperties.ENCRYPTION_PADDING_NONE
                        )
                        .setKeySize(
                                KEY_SIZE_BITS
                        )
                        .setRandomizedEncryptionRequired(
                                true
                        )
                        .setUserAuthenticationRequired(
                                false
                        )
                        .build();

        keyGenerator.init(
                keySpec
        );

        SecretKey generatedKey =
                keyGenerator.generateKey();

        if (generatedKey == null) {
            throw new KeyVaultException(
                    "Android Keystore did not create the "
                            + "cloud-backup encryption key."
            );
        }

        return generatedKey;
    }

    @NonNull
    private SecretKey getExistingSecretKey(
            @NonNull String keyAlias
    ) throws GeneralSecurityException,
            KeyVaultException {

        KeyStore keyStore =
                loadAndroidKeyStore();

        java.security.Key key =
                keyStore.getKey(
                        keyAlias,
                        null
                );

        if (!(key instanceof SecretKey)) {
            throw new MissingKeyVaultException(
                    "The saved cloud-backup passphrase cannot be "
                            + "unlocked on this device. Enter the "
                            + "recovery passphrase again."
            );
        }

        return (SecretKey) key;
    }

    private boolean containsSecretKey(
            @NonNull String keyAlias
    ) throws GeneralSecurityException,
            KeyVaultException {

        KeyStore keyStore =
                loadAndroidKeyStore();

        if (!keyStore.containsAlias(
                keyAlias
        )) {
            return false;
        }

        java.security.Key key =
                keyStore.getKey(
                        keyAlias,
                        null
                );

        return key instanceof SecretKey;
    }

    @NonNull
    private KeyStore loadAndroidKeyStore()
            throws GeneralSecurityException,
            KeyVaultException {

        try {
            KeyStore keyStore =
                    KeyStore.getInstance(
                            ANDROID_KEYSTORE
                    );

            keyStore.load(
                    null
            );

            return keyStore;

        } catch (GeneralSecurityException exception) {
            throw exception;

        } catch (Exception exception) {
            throw new KeyVaultException(
                    "Android Keystore is unavailable.",
                    exception
            );
        }
    }

    @NonNull
    private VaultRecord readAndValidateVaultRecord(
            @NonNull String accountHash
    ) throws KeyVaultException {

        int vaultVersion =
                preferences.getInt(
                        createPreferenceKey(
                                KEY_PREFIX_VERSION,
                                accountHash
                        ),
                        0
                );

        if (vaultVersion <= 0) {
            throw new MissingKeyVaultException(
                    "No cloud-backup recovery passphrase "
                            + "is saved on this device."
            );
        }

        if (vaultVersion != VAULT_VERSION) {
            throw new UnsupportedKeyVaultVersionException(
                    "The saved cloud-backup key-vault version "
                            + "is not supported."
            );
        }

        String storedOwnerHash =
                preferences.getString(
                        createPreferenceKey(
                                KEY_PREFIX_OWNER_HASH,
                                accountHash
                        ),
                        null
                );

        if (storedOwnerHash == null
                || !constantTimeStringsEqual(
                storedOwnerHash,
                accountHash
        )) {

            throw new CorruptedKeyVaultException(
                    "Cloud-backup account binding verification failed."
            );
        }

        String encodedIv =
                preferences.getString(
                        createPreferenceKey(
                                KEY_PREFIX_IV,
                                accountHash
                        ),
                        null
                );

        String encodedCiphertext =
                preferences.getString(
                        createPreferenceKey(
                                KEY_PREFIX_CIPHERTEXT,
                                accountHash
                        ),
                        null
                );

        long savedAtMillis =
                preferences.getLong(
                        createPreferenceKey(
                                KEY_PREFIX_SAVED_AT,
                                accountHash
                        ),
                        0L
                );

        if (encodedIv == null
                || encodedIv.trim().isEmpty()
                || encodedCiphertext == null
                || encodedCiphertext.trim().isEmpty()) {

            throw new CorruptedKeyVaultException(
                    "Saved cloud-backup passphrase data is incomplete."
            );
        }

        if (encodedIv.length()
                > MAX_ENCODED_VALUE_LENGTH
                || encodedCiphertext.length()
                > MAX_ENCODED_VALUE_LENGTH) {

            throw new CorruptedKeyVaultException(
                    "Saved cloud-backup passphrase data "
                            + "exceeds the supported size."
            );
        }

        return new VaultRecord(
                vaultVersion,
                accountHash,
                encodedIv,
                encodedCiphertext,
                savedAtMillis
        );
    }

    private void clearAccountVaultData(
            @NonNull String accountHash,
            boolean deleteKeystoreKey
    ) throws KeyVaultException {

        boolean preferencesCleared =
                preferences
                        .edit()
                        .remove(
                                createPreferenceKey(
                                        KEY_PREFIX_VERSION,
                                        accountHash
                                )
                        )
                        .remove(
                                createPreferenceKey(
                                        KEY_PREFIX_OWNER_HASH,
                                        accountHash
                                )
                        )
                        .remove(
                                createPreferenceKey(
                                        KEY_PREFIX_IV,
                                        accountHash
                                )
                        )
                        .remove(
                                createPreferenceKey(
                                        KEY_PREFIX_CIPHERTEXT,
                                        accountHash
                                )
                        )
                        .remove(
                                createPreferenceKey(
                                        KEY_PREFIX_SAVED_AT,
                                        accountHash
                                )
                        )
                        .commit();

        Exception keyDeletionError = null;

        if (deleteKeystoreKey) {
            try {
                KeyStore keyStore =
                        loadAndroidKeyStore();

                String keyAlias =
                        createKeyAlias(
                                accountHash
                        );

                if (keyStore.containsAlias(
                        keyAlias
                )) {
                    keyStore.deleteEntry(
                            keyAlias
                    );
                }

            } catch (Exception exception) {
                keyDeletionError =
                        exception;
            }
        }

        if (!preferencesCleared
                || keyDeletionError != null) {

            KeyVaultException failure =
                    new KeyVaultException(
                            "Local cloud-backup key-vault data "
                                    + "could not be fully cleared."
                    );

            if (keyDeletionError != null) {
                failure.addSuppressed(
                        keyDeletionError
                );
            }

            throw failure;
        }
    }

    @NonNull
    private String createAccountHash(
            @NonNull String firebaseUserId
    ) throws KeyVaultException {

        byte[] sourceBytes = null;
        byte[] hashBytes = null;

        try {
            sourceBytes =
                    firebaseUserId.getBytes(
                            StandardCharsets.UTF_8
                    );

            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            hashBytes =
                    digest.digest(
                            sourceBytes
                    );

            StringBuilder result =
                    new StringBuilder(
                            32
                    );

            /*
             * First 16 SHA-256 bytes produce a 32-character identifier.
             * It is sufficient for local alias separation and avoids
             * storing the Firebase UID in the alias.
             */
            for (int index = 0;
                 index < 16;
                 index++) {

                result.append(
                        String.format(
                                Locale.US,
                                "%02x",
                                hashBytes[index] & 0xff
                        )
                );
            }

            return result.toString();

        } catch (GeneralSecurityException exception) {
            throw new KeyVaultException(
                    "Cloud account identifier could not be protected.",
                    exception
            );

        } finally {
            CloudBackupEncryption.clearSensitiveBytes(
                    sourceBytes
            );

            CloudBackupEncryption.clearSensitiveBytes(
                    hashBytes
            );
        }
    }

    @NonNull
    private String createKeyAlias(
            @NonNull String accountHash
    ) {
        return KEY_ALIAS_PREFIX
                + accountHash;
    }

    @NonNull
    private String createPreferenceKey(
            @NonNull String prefix,
            @NonNull String accountHash
    ) {
        return prefix
                + accountHash;
    }

    @NonNull
    private byte[] createAssociatedData(
            @NonNull String firebaseUserId
    ) {
        String associatedData =
                ASSOCIATED_DATA_PREFIX
                        + VAULT_VERSION
                        + "|uid_length="
                        + firebaseUserId.length()
                        + "|uid="
                        + firebaseUserId;

        return associatedData.getBytes(
                StandardCharsets.UTF_8
        );
    }

    @NonNull
    private byte[] decodeBase64(
            @NonNull String encodedValue,
            @NonNull String description
    ) throws CorruptedKeyVaultException {

        try {
            return Base64.decode(
                    encodedValue,
                    Base64.NO_WRAP
            );

        } catch (IllegalArgumentException exception) {
            throw new CorruptedKeyVaultException(
                    description
                            + " contains invalid encoded data.",
                    exception
            );
        }
    }

    @NonNull
    private byte[] encodeCharactersAsUtf8(
            @NonNull char[] characters
    ) throws KeyVaultException {

        char[] characterCopy =
                Arrays.copyOf(
                        characters,
                        characters.length
                );

        ByteBuffer byteBuffer = null;

        try {
            byteBuffer =
                    StandardCharsets.UTF_8
                            .newEncoder()
                            .encode(
                                    CharBuffer.wrap(
                                            characterCopy
                                    )
                            );

            byte[] result =
                    new byte[
                            byteBuffer.remaining()
                            ];

            byteBuffer.get(
                    result
            );

            return result;

        } catch (CharacterCodingException exception) {
            throw new KeyVaultException(
                    "Cloud-backup passphrase could not be encoded.",
                    exception
            );

        } finally {
            Arrays.fill(
                    characterCopy,
                    '\0'
            );

            if (byteBuffer != null
                    && byteBuffer.hasArray()) {

                Arrays.fill(
                        byteBuffer.array(),
                        (byte) 0
                );
            }
        }
    }

    @NonNull
    private char[] decodeUtf8AsCharacters(
            @NonNull byte[] bytes
    ) throws KeyVaultException {

        byte[] byteCopy =
                Arrays.copyOf(
                        bytes,
                        bytes.length
                );

        CharBuffer charBuffer = null;

        try {
            charBuffer =
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .decode(
                                    ByteBuffer.wrap(
                                            byteCopy
                                    )
                            );

            char[] result =
                    new char[
                            charBuffer.remaining()
                            ];

            charBuffer.get(
                    result
            );

            return result;

        } catch (CharacterCodingException exception) {
            throw new CorruptedKeyVaultException(
                    "Saved cloud-backup passphrase contains "
                            + "invalid encrypted text.",
                    exception
            );

        } finally {
            Arrays.fill(
                    byteCopy,
                    (byte) 0
            );

            if (charBuffer != null
                    && charBuffer.hasArray()) {

                Arrays.fill(
                        charBuffer.array(),
                        '\0'
                );
            }
        }
    }

    private boolean constantTimeCharactersEqual(
            @NonNull char[] first,
            @NonNull char[] second
    ) {
        int difference =
                first.length
                        ^ second.length;

        int maximumLength =
                Math.max(
                        first.length,
                        second.length
                );

        for (int index = 0;
             index < maximumLength;
             index++) {

            char firstCharacter =
                    index < first.length
                            ? first[index]
                            : 0;

            char secondCharacter =
                    index < second.length
                            ? second[index]
                            : 0;

            difference |=
                    firstCharacter
                            ^ secondCharacter;
        }

        return difference == 0;
    }

    private boolean constantTimeStringsEqual(
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
            CloudBackupEncryption.clearSensitiveBytes(
                    firstBytes
            );

            CloudBackupEncryption.clearSensitiveBytes(
                    secondBytes
            );
        }
    }

    @NonNull
    private String validateFirebaseUserId(
            @NonNull String firebaseUserId
    ) throws KeyVaultException {

        String cleanUserId =
                firebaseUserId.trim();

        if (cleanUserId.isEmpty()) {
            throw new KeyVaultException(
                    "Firebase cloud account UID is unavailable."
            );
        }

        if (cleanUserId.length() > 256) {
            throw new KeyVaultException(
                    "Firebase cloud account UID exceeds "
                            + "the supported length."
            );
        }

        if (cleanUserId.indexOf('\n') >= 0
                || cleanUserId.indexOf('\r') >= 0
                || cleanUserId.indexOf('\0') >= 0) {

            throw new KeyVaultException(
                    "Firebase cloud account UID contains "
                            + "unsupported characters."
            );
        }

        return cleanUserId;
    }

    private void validatePassphrase(
            @NonNull char[] passphrase
    ) throws KeyVaultException {

        if (passphrase.length
                < MINIMUM_PASSPHRASE_LENGTH) {

            throw new KeyVaultException(
                    "Cloud-backup recovery passphrase must "
                            + "contain at least "
                            + MINIMUM_PASSPHRASE_LENGTH
                            + " characters."
            );
        }

        if (passphrase.length
                > MAXIMUM_PASSPHRASE_LENGTH) {

            throw new KeyVaultException(
                    "Cloud-backup recovery passphrase exceeds "
                            + "the supported length."
            );
        }

        boolean hasVisibleCharacter =
                false;

        for (char character : passphrase) {
            if (!Character.isWhitespace(
                    character
            )) {
                hasVisibleCharacter =
                        true;

                break;
            }
        }

        if (!hasVisibleCharacter) {
            throw new KeyVaultException(
                    "Cloud-backup recovery passphrase cannot "
                            + "contain only spaces."
            );
        }
    }

    private static final class VaultRecord {

        private final int vaultVersion;

        private final String ownerHash;

        private final String initializationVectorBase64;

        private final String encryptedPassphraseBase64;

        private final long savedAtMillis;

        private VaultRecord(
                int vaultVersion,
                @NonNull String ownerHash,
                @NonNull String initializationVectorBase64,
                @NonNull String encryptedPassphraseBase64,
                long savedAtMillis
        ) {
            this.vaultVersion =
                    vaultVersion;

            this.ownerHash =
                    ownerHash;

            this.initializationVectorBase64 =
                    initializationVectorBase64;

            this.encryptedPassphraseBase64 =
                    encryptedPassphraseBase64;

            this.savedAtMillis =
                    savedAtMillis;
        }
    }

    public static class KeyVaultException
            extends Exception {

        public KeyVaultException(
                @NonNull String message
        ) {
            super(message);
        }

        public KeyVaultException(
                @NonNull String message,
                @NonNull Throwable cause
        ) {
            super(
                    message,
                    cause
            );
        }
    }

    public static final class MissingKeyVaultException
            extends KeyVaultException {

        public MissingKeyVaultException(
                @NonNull String message
        ) {
            super(message);
        }
    }

    public static final class CorruptedKeyVaultException
            extends KeyVaultException {

        public CorruptedKeyVaultException(
                @NonNull String message
        ) {
            super(message);
        }

        public CorruptedKeyVaultException(
                @NonNull String message,
                @NonNull Throwable cause
        ) {
            super(
                    message,
                    cause
            );
        }
    }

    public static final class UnsupportedKeyVaultVersionException
            extends KeyVaultException {

        public UnsupportedKeyVaultVersionException(
                @NonNull String message
        ) {
            super(message);
        }
    }
}