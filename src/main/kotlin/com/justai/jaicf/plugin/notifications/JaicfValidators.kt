package com.justai.jaicf.plugin.notifications

import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.justai.jaicf.plugin.scenarios.psi.PathValueMethodsService
import com.justai.jaicf.plugin.utils.VersionService
import com.justai.jaicf.plugin.utils.isJaicfInclude
import com.justai.jaicf.plugin.utils.isSupportedJaicfInclude

class JaicfUnsupportedValidator(private val project: Project) {

    private val notificationGroup =
        NotificationGroup("Jaicf Plugin Group", NotificationDisplayType.BALLOON, true)

    private var showed = false

    private val versionService = VersionService.getInstance(project)

    fun validateAndNotify(): Boolean {
        if (versionService.isJaicfInclude && !versionService.isSupportedJaicfInclude) {
            showIfNotShowed()
            return false
        }

        clear()
        return true
    }

    private fun showIfNotShowed() {
        if (showed)
            return

        notificationGroup
            .createNotification(
                "Incompatible version of Jaicf",
                null,
                "Plugin 'Jaicf' is incompatible with old versions of Jaicf. Use version 1.1.3 or newer",
                NotificationType.WARNING
            )
            .notify(project)
        showed = true
    }

    private fun clear() {
        showed = false
    }

    companion object {
        fun getInstance(project: Project): JaicfUnsupportedValidator =
            ServiceManager.getService(project, JaicfUnsupportedValidator::class.java)
    }
}

class JaicfSourcesMissedValidator(private val project: Project) {

    private val notificationGroup =
        NotificationGroup("Jaicf Plugin Group", NotificationDisplayType.BALLOON, true)

    private var showed = false

    private val versionService = VersionService.getInstance(project)
    private val valueMethodsService = PathValueMethodsService.getInstance(project)

    fun validateAndNotify(): Boolean {
        if (versionService.isSupportedJaicfInclude && valueMethodsService.jaicfMethods.isEmpty()) {
            showIfNotShowed()
            return false
        }

        clear()
        return true
    }

    private fun showIfNotShowed() {
        if (showed) return

        notificationGroup
            .createNotification(
                "Missed Jaicf sources",
                null,
                "Plugin 'Jaicf' cannot find sources of Jaicf. Download sources of Jaicf",
                NotificationType.ERROR
            )
            .notify(project)
        showed = true
    }

    private fun clear() {
        showed = false
    }

    companion object {
        fun getInstance(project: Project): JaicfSourcesMissedValidator =
            ServiceManager.getService(project, JaicfSourcesMissedValidator::class.java)
    }
}

fun checkAndNotify(project: Project): Boolean {
    return JaicfUnsupportedValidator.getInstance(project).validateAndNotify() &&
        JaicfSourcesMissedValidator.getInstance(project).validateAndNotify()
}
