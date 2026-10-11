package com.example.journalapp.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import com.example.journalapp.R
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

object BiometricLock {

    private const val PREFS_FILE = "app_lock_prefs"
    private const val PREF_ENABLED = "enabled"
    private const val PREF_BLOB = "challenge_blob"
    private const val PREF_HASH = "challenge_hash"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "journal_biometric_key"
    private const val TRANSFORMATION = "AES/CBC/PKCS7Padding"

    object State {
        @Volatile var shouldRelock = false
    }

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(PREF_ENABLED, false)

    /** null = ready; non-null = localized reason it isn't. */
    fun availabilityProblem(context: Context): String? =
        when (BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> null
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                context.getString(R.string.bio_none_enrolled)
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                context.getString(R.string.bio_no_hardware)
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                context.getString(R.string.bio_hw_unavailable)
            else -> context.getString(R.string.bio_unknown)
        }

    fun isAvailable(context: Context): Boolean = availabilityProblem(context) == null

    fun prepareEnable(): Cipher? = try {
        ensureKeyExists()
        Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, loadKey()) }
    } catch (e: Exception) { deleteKey(); null }

    fun completeEnable(context: Context, cipher: Cipher?): Boolean {
        if (cipher == null) return false
        return try {
            val challenge = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val ciphertext = cipher.doFinal(challenge)
            prefs(context).edit()
                .putString(PREF_BLOB, b64(cipher.iv) + ":" + b64(ciphertext))
                .putString(PREF_HASH, sha256Hex(challenge))
                .putBoolean(PREF_ENABLED, true)
                .apply()
            true
        } catch (e: Exception) { false }
    }

    fun prepareUnlock(context: Context): Cipher? = try {
        val (iv, _) = loadBlob(context)
        Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, loadKey(), IvParameterSpec(unb64(iv)))
        }
    } catch (e: Exception) { null }

    fun finishUnlock(context: Context, cipher: Cipher?): Boolean {
        if (cipher == null) return false
        return try {
            val (_, ciphertext) = loadBlob(context)
            sha256Hex(cipher.doFinal(ciphertext)) == prefs(context).getString(PREF_HASH, null)
        } catch (e: Exception) { false }
    }

    fun disable(context: Context) {
        prefs(context).edit()
            .putBoolean(PREF_ENABLED, false)
            .remove(PREF_BLOB)
            .remove(PREF_HASH)
            .apply()
        deleteKey()
    }

    private fun ensureKeyExists() {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) return
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(false)
                .build()
        )
        generator.generateKey()
    }

    private fun loadKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }

    private fun deleteKey() {
        runCatching {
            KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }.deleteEntry(KEY_ALIAS)
        }
    }

    private fun loadBlob(context: Context): Pair<String, ByteArray> {
        val raw = prefs(context).getString(PREF_BLOB, null)
            ?: throw IllegalStateException("No challenge stored")
        val (ivPart, ctPart) = raw.split(":").also { require(it.size == 2) }
        return ivPart to unb64(ctPart)
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(text: String) = Base64.decode(text, Base64.NO_WRAP)

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
