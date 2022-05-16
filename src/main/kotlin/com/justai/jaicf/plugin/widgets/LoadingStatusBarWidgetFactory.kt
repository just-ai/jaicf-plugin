package com.justai.jaicf.plugin.widgets

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget.MultipleTextValuesPresentation
import com.intellij.openapi.wm.StatusBarWidget.WidgetPresentation
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.EditorBasedWidget
import com.intellij.util.Consumer
import com.justai.jaicf.plugin.utils.VersionService
import com.justai.jaicf.plugin.utils.isSupportedJaicfInclude
import java.awt.event.MouseEvent


class LoadingStatusBarWidgetFactory : StatusBarWidgetFactory {

    override fun getId() = "JAICF plugin status"

    override fun getDisplayName() = "JAICF plugin status"

    override fun isAvailable(project: Project) = VersionService.getInstance(project).isSupportedJaicfInclude

    override fun createWidget(project: Project): StatusBarWidget {
        return MyWidget(project)
    }

    override fun disposeWidget(widget: StatusBarWidget) {}

    override fun canBeEnabledOn(statusBar: StatusBar) = true

}

class MyWidget(project: Project?) : EditorBasedWidget(project!!) {
    override fun ID(): String {
        return "MyWidget"
    }

    override fun getPresentation(): WidgetPresentation {
        return MyPresentation()
    }
}

class MyPresentation : MultipleTextValuesPresentation {
    override fun getPopupStep(): ListPopup? {
        return null
    }

    override fun getSelectedValue(): String {
        return "Selected value"
    }

    override fun getTooltipText(): String {
        return "Tooltip text"
    }

    override fun getClickConsumer(): Consumer<MouseEvent>? {
        return null
    }
}