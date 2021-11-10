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

class JaicfUnsupportedNotifier(private val project: Project) : ValidatingNotifier() {

    private val notificationGroup =
        NotificationGroup("Jaicf Plugin Group", NotificationDisplayType.STICKY_BALLOON, true)

    private val versionService = VersionService.getInstance(project)

    override fun isValid() = versionService.isSupportedJaicfInclude || !versionService.isJaicfInclude

    override fun showNotification() {
        notificationGroup
            .createNotification(
                "Incompatible version of JAICF",
                null,
                "JAICF Plugin is incompatible with old versions of JAICF. Use version 1.1.3 or newer",
                NotificationType.WARNING
            )
            .notify(project)
    }

    companion object {
        fun getInstance(project: Project): JaicfUnsupportedNotifier =
            ServiceManager.getService(project, JaicfUnsupportedNotifier::class.java)
    }
}

class JaicfSourcesMissedNotifier(private val project: Project) : ValidatingNotifier() {

    private val notificationGroup =
        NotificationGroup("Jaicf Plugin Group", NotificationDisplayType.STICKY_BALLOON, true)

    private val versionService = VersionService.getInstance(project)
    private val valueMethodsService = PathValueMethodsService.getInstance(project)

    override fun isValid() = valueMethodsService.jaicfMethods.isNotEmpty() || !versionService.isSupportedJaicfInclude

    override fun showNotification() {
        notificationGroup
            .createNotification(
                "Missed JAICF sources",
                null,
                "JAICF Plugin cannot find JAICF sources. Download JAICF sources manually",
                NotificationType.ERROR
            )
            .notify(project)
    }

    companion object {
        fun getInstance(project: Project): JaicfSourcesMissedNotifier =
            ServiceManager.getService(project, JaicfSourcesMissedNotifier::class.java)
    }
}

abstract class ValidatingNotifier {

    private var notified = false

    fun checkAndNotifyIfInvalid() = isValid().also { if (it) clear() else notifyOnce() }

    abstract fun isValid(): Boolean

    abstract fun showNotification()

    private fun notifyOnce() {
        synchronized(this) {
            if (notified) return
            showNotification()
            notified = true
        }
    }

    private fun clear() {
        notified = false
    }
}

fun checkEnvironmentAndNotify(project: Project): Boolean {
    return JaicfUnsupportedNotifier.getInstance(project).checkAndNotifyIfInvalid() &&
        JaicfSourcesMissedNotifier.getInstance(project).checkAndNotifyIfInvalid()
}
