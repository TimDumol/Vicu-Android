package com.rendyhd.vicu.util

import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.AtomicLong as KotlinAtomicLong

@OptIn(ExperimentalAtomicApi::class)
actual class AtomicLong actual constructor(initialValue: Long) {
    private val value = KotlinAtomicLong(initialValue)

    actual fun get(): Long = value.load()

    actual fun set(newValue: Long) = value.store(newValue)

    actual fun getAndIncrement(): Long = value.fetchAndAdd(1)

    actual fun decrementAndGet(): Long = value.addAndFetch(-1)
}
