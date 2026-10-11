package com.example.journalapp

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.journalapp.data.JournalDatabase
import com.example.journalapp.data.PhotoStore
import com.example.journalapp.security.BiometricLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class JournalApp : Application() {

    override fun onCreate() {
        super.onCreate()
        SessionManager.onAppForeground(this)

        // Warm the encrypted DB + GC orphaned photo files off the main thread
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val dao = JournalDatabase.getDatabase(this@JournalApp).journalDao()
            val referenced = dao.getAllPhotoJson().flatMap { PhotoStore.decode(it) }.toSet()
            PhotoStore.cleanupOrphans(this@JournalApp, referenced)
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                SessionManager.onAppForeground(this@JournalApp)
            }
            override fun onStop(owner: LifecycleOwner) {
                if (BiometricLock.isEnabled(this@JournalApp)) {
                    BiometricLock.State.shouldRelock = true
                }
                SessionManager.onAppBackground(this@JournalApp)
            }
        })
    }
}
