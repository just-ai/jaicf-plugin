package com.justai.jaicf.plugin

import java.util.concurrent.atomic.AtomicBoolean

class LazySafeValue<T>(initValue: T, @Volatile private var lazyUpdater: ((T) -> T)? = null) {

    val value: T
        get() = lazyUpdater?.let { safeValue.tryToUpdate(it) }?.also { lazyUpdater = null }
            ?: safeValue.value

    private val safeValue = SafeValue(initValue)

    fun update(updater: (T) -> T) = safeValue.tryToUpdate(updater)

    fun lazyUpdate(updater: (T) -> T) {
        lazyUpdater = updater
    }
}

class SafeValue<T>(initValue: T) {

    @Volatile
    var value: T = initValue
        private set

    private val isUpdating: AtomicBoolean = AtomicBoolean(false)

    fun tryToUpdate(updater: (T) -> T): T {
        if (isUpdating.compareAndSet(false, true)) {
            try {
                value = updater.invoke(value)
                return value
            } finally {
                isUpdating.set(false)
            }
        }
        return value
    }
}
