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

class JaicfUnsupportedNotifier(private val project: Project) {

    private val notificationGroup =
        NotificationGroup("Jaicf Plugin Group", NotificationDisplayType.STICKY_BALLOON, true)

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

        notificationGroup
            .createNotification(
                "Incompatible version of JAICF",
                null,
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
            ServiceManager.getService(project, JaicfUnsupportedNotifier::class.java)
    }
}

class JaicfSourcesMissedNotifier(private val project: Project) {

    private val notificationGroup =
        NotificationGroup("Jaicf Plugin Group", NotificationDisplayType.STICKY_BALLOON, true)

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

        notificationGroup
            .createNotification(
                "Missed JAICF sources",
                null,
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
            ServiceManager.getService(project, JaicfSourcesMissedNotifier::class.java)
    }
}

fun checkEnvironmentAndNotify(project: Project): Boolean {
    return JaicfUnsupportedNotifier.getInstance(project).notifyIfInvalid() &&
        JaicfSourcesMissedNotifier.getInstance(project).notifyIfInvalid()
}
