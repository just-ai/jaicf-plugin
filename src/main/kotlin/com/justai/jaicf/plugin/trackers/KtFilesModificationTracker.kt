package com.justai.jaicf.plugin.trackers

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty

class KtFilesModificationTracker(project: Project) : SimpleModificationTracker() {
    init {
        project.messageBus.connect(project).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: MutableList<out VFileEvent>) {
                events
                    .filter { it !is VFileContentChangeEvent }
                    .mapNotNull { it.file }
                    .filter { it.fileType == KotlinFileType.INSTANCE && it.extension == "kt" }
                    .ifNotEmpty {
                        incModificationCount()
                    }
            }
        })
    }
}