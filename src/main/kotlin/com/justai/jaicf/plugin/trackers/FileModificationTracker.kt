package com.justai.jaicf.plugin.trackers

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import com.justai.jaicf.plugin.isRemoved
import org.jetbrains.kotlin.psi.KtFile

class FileModificationTracker(project: Project, val originalFile: KtFile) : SimpleModificationTracker() {

    init {
        PsiManager.getInstance(project).addPsiTreeChangeListener(PsiChangeListener()) { }
    }

    private inner class PsiChangeListener : PsiTreeChangeAdapter() {
        override fun childrenChanged(event: PsiTreeChangeEvent) {
            val file = event.file?.originalFile as? KtFile ?: return
            if (file == originalFile) incModificationCount()
        }
    }

    companion object {
        fun getInstance(file: KtFile): FileModificationTracker? {
            if (file.isRemoved) return null

            val originalFile = file.originalFile as? KtFile ?: return null
            return FileModificationTracker(originalFile.project, originalFile)
        }
    }
}
