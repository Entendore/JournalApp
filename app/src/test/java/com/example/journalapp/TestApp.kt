package com.example.journalapp

import android.app.Application

/** Replaces JournalApp under Robolectric so the SQLCipher native lib never loads on the JVM. */
class TestApp : Application()
