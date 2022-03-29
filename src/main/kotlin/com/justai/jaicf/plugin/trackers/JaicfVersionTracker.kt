package com.justai.jaicf.plugin.trackers

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.justai.jaicf.plugin.utils.Version
import com.justai.jaicf.plugin.utils.VersionService

/**
 * Трекер обновления версии JAICF в зависимостях. Используется собственный сервис VersionService.
 * Этот класс позволяет упростить использование встроенных кешей и оптимизировать работу плагина.
 */
class JaicfVersionTracker(project: Project) : SimpleModificationTracker() {

    private val versionService = VersionService.getInstance(project)

    private var lastVersion: Version? = null

    override fun getModificationCount(): Long {
        versionService.jaicf.let {
            if (it != lastVersion) {
                incModificationCount()
                lastVersion = it
            }
        }

        return super.getModificationCount()
    }

    companion object {
        fun getInstance(project: Project) = JaicfVersionTracker(project)
    }
}
