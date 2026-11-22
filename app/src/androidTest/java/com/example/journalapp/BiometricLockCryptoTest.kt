package com.example.journalapp

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.IvParameterSpec

@RunWith(AndroidJUnit4::class)
class BiometricLockCryptoTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val alias = "test_biometric_roundtrip_key"

    @Test
    fun challengeVerifiesOnlyWithTheRightCipher() {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (ks.containsAlias(alias)) ks.deleteEntry(alias)

        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .build()
        )
        val key = ks.getKey(alias, null) as javax.crypto.SecretKey

        val enc = Cipher.getInstance("AES/CBC/PKCS7Padding")
            .apply { init(Cipher.ENCRYPT_MODE, key) }
        assertTrue(BiometricLock.completeEnable(context, enc))

        val dec = Cipher.getInstance("AES/CBC/PKCS7Padding")
            .apply { init(Cipher.DECRYPT_MODE, key, IvParameterSpec(enc.iv)) }
        assertTrue(BiometricLock.finishUnlock(context, dec))

        val enc2 = Cipher.getInstance("AES/CBC/PKCS7Padding")
            .apply { init(Cipher.ENCRYPT_MODE, key) }
        val wrong = Cipher.getInstance("AES/CBC/PKCS7Padding")
            .apply { init(Cipher.DECRYPT_MODE, key, IvParameterSpec(enc2.iv)) }
        assertFalse(BiometricLock.finishUnlock(context, wrong))

        BiometricLock.disable(context)
        ks.deleteEntry(alias)
    }
}
