package com.justai.jaicf.plugin.services

import com.intellij.openapi.project.Project
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import kotlin.reflect.KProperty
import org.jetbrains.kotlin.idea.caches.project.LibraryModificationTracker
import org.jetbrains.kotlin.psi.KtFile

abstract class Service(protected val project: Project) {

    protected val manager: CachedValuesManager = CachedValuesManager.getManager(project)

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

class CachedValueDelegate<T>(private val cachedValue: CachedValue<T>) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = cachedValue.value
}
