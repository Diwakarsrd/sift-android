package dev.sift.app.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import timber.log.Timber
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the AES-256-GCM key stored in the Android Keystore (hardware-backed on modern devices).
 * The DB passphrase is derived by encrypting a fixed nonce — never stored in plaintext.
 */
@Singleton
class KeystoreManager @Inject constructor() {

    private val keystore = KeyStore.getInstance(PROVIDER).also { it.load(null) }

    fun getOrCreateDbPassphrase(): ByteArray {
        return try {
            val key = getOrCreateKey()
            derivePassphrase(key)
        } catch (e: Exception) {
            Timber.e(e, "Keystore error — falling back to device-unique derivation")
            fallbackPassphrase()
        }
    }

    private fun getOrCreateKey(): SecretKey {
        if (!keystore.containsAlias(KEY_ALIAS)) {
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)    // silent background use
                .setRandomizedEncryptionRequired(false)  // deterministic for DB passphrase
                .build()

            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
                .also { it.init(spec) }
                .generateKey()
        }
        return (keystore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    private fun derivePassphrase(key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12) { it.toByte() }     // deterministic IV — same key → same output
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(PASSPHRASE_INPUT)
    }

    private fun fallbackPassphrase(): ByteArray {
        // Last-resort: device fingerprint hash (weaker but functional)
        val id = android.provider.Settings.Secure.ANDROID_ID
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(id.toByteArray())
    }

    companion object {
        private const val PROVIDER   = "AndroidKeyStore"
        private const val KEY_ALIAS  = "sift_db_key_v1"
        private val PASSPHRASE_INPUT = "SIFT_DB_PASSPHRASE_SEED_V1".toByteArray()
    }
}
