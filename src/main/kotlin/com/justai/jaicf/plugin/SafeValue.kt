package com.justai.jaicf.plugin

class RecursiveSafeValue<T>(initValue: T, private val failFast: Boolean = false, private val updater: (T) -> T) {

    private val isUpdatingByThread = ThreadLocal.withInitial { false }

    @Volatile
    private var isUpdating = false

    @Volatile
    private var safeValue: T = initValue

    @Volatile
    private var valid = false

    val value: T
        get() {
            if (valid ||
                isUpdatingByThread.get() ||
                (failFast && isUpdating)
            )
                return safeValue

            try {
                synchronized(this) {
                    if (valid)
                        return safeValue

                    isUpdatingByThread.set(true)
                    isUpdating = true

                    return updater(safeValue).also {
                        safeValue = it
                        valid = true
                    }
                }
            } finally {
                isUpdatingByThread.set(false)
                isUpdating = false
            }
        }

    fun invalid() {
        valid = false
    }
}
