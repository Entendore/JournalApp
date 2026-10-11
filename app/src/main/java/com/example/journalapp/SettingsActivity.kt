package com.example.journalapp

import android.os.Bundle
import android.view.MenuItem
import android.widget.CompoundButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import com.example.journalapp.databinding.ActivitySettingsBinding
import com.example.journalapp.security.BiometricLock
import java.util.Locale

class SettingsActivity : LockAwareActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val languageTags = listOf("", "en", "es", "fr", "de", "pt")   // "" = system

    private val lockToggleListener =
        CompoundButton.OnCheckedChangeListener { _, checked ->
            if (checked) requestEnable() else {
                BiometricLock.disable(this@SettingsActivity)
                refreshUi()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings)
        binding.cardLanguage.setOnClickListener { showLanguagePicker() }
        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun refreshUi() {
        binding.switchLock.setOnCheckedChangeListener(null)
        binding.switchLock.isChecked = BiometricLock.isEnabled(this)
        binding.textLockStatus.text = when {
            BiometricLock.isEnabled(this) -> getString(R.string.lock_on_desc)
            else -> BiometricLock.availabilityProblem(this) ?: getString(R.string.lock_ready)
        }
        binding.switchLock.setOnCheckedChangeListener(lockToggleListener)

        val applied = AppCompatDelegate.getApplicationLocales()
        binding.textLanguageValue.text =
            if (applied.isEmpty || applied.get(0) == null) getString(R.string.language_system)
            else applied.get(0)!!.displayName.replaceFirstChar { it.uppercase(it) }
    }

    private fun requestEnable() {
        val cipher = BiometricLock.prepareEnable()
        if (cipher == null) {
            revert(getString(R.string.lock_key_error))
            return
        }

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (BiometricLock.completeEnable(
                            this@SettingsActivity, result.cryptoObject?.cipher
                        )
                    ) {
                        refreshUi()
                    } else {
                        revert(getString(R.string.lock_key_error))
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    revert(errString.toString())
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.lock_setup_title))
            .setSubtitle(getString(R.string.lock_setup_subtitle))
            .setNegativeButtonText(getString(android.R.string.cancel))
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    private fun revert(message: String) {
        binding.switchLock.setOnCheckedChangeListener(null)
        binding.switchLock.isChecked = false
        binding.switchLock.setOnCheckedChangeListener(lockToggleListener)
        binding.textLockStatus.text = message
    }

    private fun showLanguagePicker() {
        val labels = languageTags.map { tag ->
            if (tag.isEmpty()) getString(R.string.language_system)
            else {
                val locale = Locale.forLanguageTag(tag)
                locale.getDisplayName(locale).replaceFirstChar { it.uppercase(it) }
            }
        }.toTypedArray()

        val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val checked = languageTags.indexOfFirst { it.isNotEmpty() && current.startsWith(it) }
            .let { if (it == -1) 0 else it }

        AlertDialog.Builder(this)
            .setTitle(R.string.language)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                val tag = languageTags[which]
                AppCompatDelegate.setApplicationLocales(
                    if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                    else LocaleListCompat.forLanguageTags(tag)
                )
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        if (item.itemId == android.R.id.home) { finish(); true }
        else super.onOptionsItemSelected(item)
}
