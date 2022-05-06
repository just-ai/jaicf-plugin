package com.justai.jaicf.plugin.trackers

import com.intellij.openapi.util.ModificationTracker
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class HashCodeModificationTracker(private val element: Any) : ModificationTracker {

    private val lastHashCode = AtomicInteger(element.hashCode())
    private val count = AtomicLong()

    override fun getModificationCount(): Long {
        val hashCode = element.hashCode()

        if (lastHashCode.getAndSet(hashCode) != hashCode)
            return count.incrementAndGet()
        return count.get()
    }

    companion object {
        fun hashed(element: Any) = HashCodeModificationTracker(element)
    }
}
