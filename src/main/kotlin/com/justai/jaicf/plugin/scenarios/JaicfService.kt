package com.justai.jaicf.plugin.scenarios

import com.intellij.openapi.project.Project
import com.justai.jaicf.plugin.trackers.JaicfVersionTracker
import com.justai.jaicf.plugin.utils.VersionService
import com.justai.jaicf.plugin.utils.cached
import com.justai.jaicf.plugin.utils.isSupportedJaicfInclude

/**
 * Базовый класс для всех сервисов плагина. Такое решение позволяет включать и выключать все сервисы разом,
 * в зависимости от того, содержит ли проект поддерживаемую версию JAICF. А также позволяет упростить использование кешей.
 */
abstract class JaicfService(protected val project: Project) {

    protected val enabled: Boolean by cached(JaicfVersionTracker.getInstance(project)) {
        VersionService.getInstance(project).isSupportedJaicfInclude
    }

    fun <T> cached(vararg dependencies: Any, valueProvider: () -> (T)) =
        project.cached(*dependencies) { valueProvider() }

    fun <T> cachedIfEnabled(vararg dependencies: Any, valueProvider: () -> (T)) =
        cached(*(dependencies.asList() + JaicfVersionTracker.getInstance(project)).toTypedArray()) {
            if (enabled) valueProvider()
            else null
        }
}
