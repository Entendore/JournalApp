package com.example.journalapp

import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

fun <T> LiveData<T>.getOrAwaitValue(time: Long = 2, unit: TimeUnit = TimeUnit.SECONDS): T {
    var value: T? = null
    val latch = CountDownLatch(1)
    val observer = Observer<T> { value = it; latch.countDown() }
    observeForever(observer)
    try {
        if (!latch.await(time, unit)) throw TimeoutException("LiveData never emitted")
    } finally {
        removeObserver(observer)
    }
    @Suppress("UNCHECKED_CAST")
    return value as T
}
