package com.justai.jaicf.plugin.trackers

import com.intellij.openapi.util.ModificationTracker
import java.lang.System.currentTimeMillis

class TimeModificationTracker(private val intervalMs: Int) : ModificationTracker {
    override fun getModificationCount() = currentTimeMillis() / intervalMs

    companion object {
        fun timed(intervalMs: Int) = TimeModificationTracker(intervalMs)
    }
}
