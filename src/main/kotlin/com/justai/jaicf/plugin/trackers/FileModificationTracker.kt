package com.justai.jaicf.plugin.trackers

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.util.EventDispatcher
import com.jetbrains.rd.util.concurrentMapOf
import com.justai.jaicf.plugin.isRemoved
import org.jetbrains.kotlin.psi.KtFile

class FileModificationTrackerProvider(private val project: Project) {

    fun getTracker(file: KtFile): SimpleModificationTracker? {
        if (file.isRemoved)
            return null

        val originalFile = file.originalFile as? KtFile ?: return null
        return MyTracker(project, originalFile)
    }

    companion object {
        operator fun get(project: Project): FileModificationTrackerProvider =
            project.getService(FileModificationTrackerProvider::class.java)
    }
}

class MyTracker(project: Project, val originalFile: KtFile) : SimpleModificationTracker() {

    init {
        PsiManager.getInstance(project).addPsiTreeChangeListener(PsiChangeListener()) { }
    }

    private inner class PsiChangeListener : PsiTreeChangeAdapter() {
        override fun childrenChanged(event: PsiTreeChangeEvent) {
            (event.file?.originalFile as? KtFile)?.let { file ->
                if (file == originalFile)
                    incModificationCount()
            }
        }
    }
}
