package com.justai.jaicf.plugin.notifications

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.justai.jaicf.plugin.scenarios.psi.PathValueMethodsService
import com.justai.jaicf.plugin.utils.VersionService
import com.justai.jaicf.plugin.utils.isJaicfSupported

class JaicfUnsupportedValidator(private val project: Project) {

    private var showed = false

    private val versionService = VersionService.getInstance(project)

    fun validateAndNotify(): Boolean {
        if (versionService.isJaicfSupported) {
            clear()
            return true
        }

        if (!showed) {
            show()
            showed = true
        }

        return false
    }

    private fun show() {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Jaicf Plugin Group")
            .createNotification(
                "Incompatible version of Jaicf",
                "Plugin 'Jaicf' is incompatible with old versions of Jaicf. Use version 1.1.3 or newer",
                NotificationType.WARNING
            )
            .notify(project)
    }

    private fun clear() {
        showed = false
    }

    companion object {
        fun getInstance(project: Project): JaicfUnsupportedValidator =
            project.getService(JaicfUnsupportedValidator::class.java)
    }
}

class JaicfSourcesMissedValidator(private val project: Project) {

    private var showed = false

    private val service = PathValueMethodsService.getInstance(project)

    fun validateAndNotify(): Boolean {
        if (service.jaicfMethods.isNotEmpty()) {
            clear()
            return true
        }

        if (!showed) {
            show()
            showed = true
        }

        return false
    }

    private fun show() {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Jaicf Plugin Group")
            .createNotification(
                "Missed Jaicf sources",
                "Plugin 'Jaicf' cannot find sources of Jaicf. Download sources of Jaicf",
                NotificationType.ERROR
            )
            .notify(project)
    }

    private fun clear() {
        showed = false
    }

    companion object {
        fun getInstance(project: Project): JaicfSourcesMissedValidator =
            project.getService(JaicfSourcesMissedValidator::class.java)
    }
}

fun checkAndNotify(project: Project): Boolean {
    return JaicfUnsupportedValidator.getInstance(project).validateAndNotify() &&
        JaicfSourcesMissedValidator.getInstance(project).validateAndNotify()
}
