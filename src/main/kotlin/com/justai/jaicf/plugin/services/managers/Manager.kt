package com.justai.jaicf.plugin.services.managers

import com.intellij.openapi.project.Project
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import com.justai.jaicf.plugin.services.CachedValueDelegate
import com.justai.jaicf.plugin.services.VersionService
import com.justai.jaicf.plugin.services.isJaicfInclude
import org.jetbrains.kotlin.idea.caches.project.LibraryModificationTracker

open class Manager(protected val project: Project) {

    private val manager: CachedValuesManager = CachedValuesManager.getManager(project)

    fun <T> cached(vararg dependencies: Any?, value: () -> (T)) =
        CachedValueDelegate(manager.createCachedValue { Result.create(value(), *dependencies) })

    fun <T> cachedIfEnabled(vararg dependencies: Any?, value: () -> (T)) =
        cached(*dependencies) {
            if (enabled) value()
            else null
        }

    protected val enabled: Boolean by cached(LibraryModificationTracker.getInstance(project)) {
        VersionService[project].isJaicfInclude
    }
}