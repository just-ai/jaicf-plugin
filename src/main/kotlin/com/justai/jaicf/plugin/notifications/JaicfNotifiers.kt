package com.justai.jaicf.plugin.notifications

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.justai.jaicf.plugin.scenarios.psi.PathValueMethodsService
import com.justai.jaicf.plugin.utils.VersionService
import com.justai.jaicf.plugin.utils.isJaicfInclude
import com.justai.jaicf.plugin.utils.isSupportedJaicfInclude

class JaicfUnsupportedNotifier(private val project: Project) {

    private var showed = false

    private val versionService = VersionService.getInstance(project)

    fun notifyIfInvalid(): Boolean {
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

        NotificationGroupManager.getInstance()
            .getNotificationGroup("Incompatible Versions Jaicf Plugin Group")
            .createNotification(
                "Incompatible version of JAICF",
                "JAICF Plugin is incompatible with old versions of JAICF. Use version 1.1.3 or newer",
                NotificationType.WARNING
            )
            .notify(project)
        showed = true
    }

    private fun clear() {
        showed = false
    }

    companion object {
        fun getInstance(project: Project): JaicfUnsupportedNotifier =
            project.getService(JaicfUnsupportedNotifier::class.java)
    }
}

class JaicfSourcesMissedNotifier(private val project: Project) {

    private var showed = false

    private val versionService = VersionService.getInstance(project)
    private val valueMethodsService = PathValueMethodsService.getInstance(project)

    fun notifyIfInvalid(): Boolean {
        if (versionService.isSupportedJaicfInclude && valueMethodsService.jaicfMethods.isEmpty()) {
            showIfNotShowed()
            return false
        }

        clear()
        return true
    }

    private fun showIfNotShowed() {
        if (showed) return

        NotificationGroupManager.getInstance()
            .getNotificationGroup("Missed Sources Jaicf Plugin Group")
            .createNotification(
                "Missed JAICF sources",
                "JAICF Plugin cannot find JAICF sources. Download JAICF sources manually",
                NotificationType.ERROR
            )
            .notify(project)
        showed = true
    }

    private fun clear() {
        showed = false
    }

    companion object {
        fun getInstance(project: Project): JaicfSourcesMissedNotifier =
            project.getService(JaicfSourcesMissedNotifier::class.java)
    }
}

fun checkEnvironmentAndNotify(project: Project): Boolean {
    return JaicfUnsupportedNotifier.getInstance(project).notifyIfInvalid() &&
        JaicfSourcesMissedNotifier.getInstance(project).notifyIfInvalid()
}
