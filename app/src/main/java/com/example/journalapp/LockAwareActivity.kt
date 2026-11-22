package com.example.journalapp

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.example.journalapp.security.BiometricLock

/** Base for every activity that shows journal content. */
abstract class LockAwareActivity : AppCompatActivity() {

    override fun onStart() {
        super.onStart()
        if (BiometricLock.State.shouldRelock) {
            BiometricLock.State.shouldRelock = false
            startActivity(Intent(this, AppLockActivity::class.java))
        }
    }
}
