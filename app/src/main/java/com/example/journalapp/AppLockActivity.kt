package com.example.journalapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.example.journalapp.databinding.ActivityAppLockBinding
import com.example.journalapp.security.BiometricLock
import javax.crypto.Cipher

/** The gate. Does NOT extend LockAwareActivity — it IS the lock. */
class AppLockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppLockBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppLockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.buttonUnlock.setOnClickListener { attemptUnlock() }
    }

    override fun onResume() {
        super.onResume()
        BiometricLock.State.shouldRelock = false
        if (!BiometricLock.isEnabled(this)) {
            proceed()
            return
        }
        attemptUnlock()
    }

    private fun attemptUnlock() {
        val problem = BiometricLock.availabilityProblem(this)
        if (problem != null) {
            failOpen(getString(R.string.lock_fail_biometrics, problem))
            return
        }
        val cipher = BiometricLock.prepareUnlock(this)
        if (cipher == null) {
            failOpen(getString(R.string.lock_fail_key))
            return
        }
        showPrompt(cipher)
    }

    private fun showPrompt(cipher: Cipher) {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (BiometricLock.finishUnlock(this@AppLockActivity, result.cryptoObject?.cipher)) {
                        proceed()
                    } else {
                        failOpen(getString(R.string.lock_fail_verify))
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    when (errorCode) {
                        BiometricPrompt.ERROR_NO_BIOMETRICS,
                        BiometricPrompt.ERROR_HW_NOT_PRESENT,
                        BiometricPrompt.ERROR_HW_UNAVAILABLE ->
                            failOpen(getString(R.string.lock_fail_generic, errString))
                        else -> {
                            binding.textStatus.text = errString
                            binding.buttonUnlock.visibility = View.VISIBLE
                        }
                    }
                }

                override fun onAuthenticationFailed() {
                    binding.textStatus.text = getString(R.string.lock_not_recognized)
                    binding.buttonUnlock.visibility = View.VISIBLE
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.unlock_title))
            .setSubtitle(getString(R.string.unlock_subtitle))
            .setNegativeButtonText(getString(android.R.string.cancel))
            .build()

        binding.textStatus.text = ""
        binding.buttonUnlock.visibility = View.GONE
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    private fun failOpen(message: String) {
        BiometricLock.disable(this)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        proceed()
    }

    private fun proceed() {
        if (isTaskRoot) {
            // Cold start: nothing beneath us — bring up the app
            startActivity(Intent(this, MainActivity::class.java))
        }
        // Re-lock overlay: finish() reveals the activity that was backgrounded
        finish()
    }
}
