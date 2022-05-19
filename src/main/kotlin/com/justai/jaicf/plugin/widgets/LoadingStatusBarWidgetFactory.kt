package com.justai.jaicf.plugin.widgets

import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget.MultipleTextValuesPresentation
import com.intellij.openapi.wm.StatusBarWidget.WidgetPresentation
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.EditorBasedWidget
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.intellij.ui.AnimatedIcon
import com.intellij.util.Consumer
import com.justai.jaicf.plugin.utils.VersionService
import com.justai.jaicf.plugin.utils.isSupportedJaicfInclude
import javax.swing.Icon
import kotlin.random.Random
import org.jetbrains.kotlin.idea.debugger.getService
import java.awt.event.MouseEvent
import java.lang.Thread.sleep


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

    init {
        Thread {
            while (true) {
                sleep(1000)
                myStatusBar.updateWidget(ID())
            }
        }.start()
    }

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
        return "Selected value ${Random.nextInt()}"
    }

    override fun getTooltipText(): String {
        return "Tooltip text"
    }

    override fun getClickConsumer(): Consumer<MouseEvent>? {
        return null
    }

    override fun getIcon(): Icon {
        val icon = AllIcons.Toolbar.Locale
        return AnimatedIcon(1000, icon, IconLoader.getDisabledIcon(icon))
    }
}