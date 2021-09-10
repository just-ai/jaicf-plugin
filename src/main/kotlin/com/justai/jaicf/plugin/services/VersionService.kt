package com.justai.jaicf.plugin.services

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.justai.jaicf.plugin.RecursiveSafeValue
import org.jetbrains.kotlin.psi.KtFile

class VersionService(private val project: Project) : Service {

    private val libraries
        get() = LibraryTablesRegistrar.getInstance().getLibraryTable(project).libraries.toList()

    val jaicfVersion = RecursiveSafeValue<Version?>(null) {
        val version = libraries
            .filter { it.name?.contains("com.just-ai.jaicf:core") == true }
            .mapNotNull { it.name?.split(":")?.last() }
            .firstOrNull()
            ?: return@RecursiveSafeValue null

        return@RecursiveSafeValue Version(version)
    }

    override fun markFileAsModified(file: KtFile) {
        jaicfVersion.invalid()
    }

    companion object {
        fun get(project: Project): VersionService? = ServiceManager.getService(project, VersionService::class.java)

        fun isAnnotationsUnsupported(project: Project) = !isAnnotationsSupported(project)

        fun isAnnotationsSupported(project: Project): Boolean {
            val jaicfVersion = get(project)?.jaicfVersion?.value ?: return false
            return jaicfVersion >= Version("1.1.3")
        }
    }
}

data class Version(val version: String) {

    private val components = version.split(".", "-")

    operator fun compareTo(other: Version) =
        components.zip(other.components).fold(0) { prev, (left, right) ->
            if (prev != 0) return@fold prev
            else left.toIntOrNull()?.let { leftNumber ->
                right.toIntOrNull()?.let { rightNumber -> leftNumber.compareTo(rightNumber) }
            } ?: left.compareTo(right)
        }.let {
            if (it != 0) it
            else components.size.compareTo(other.components.size)
        }
}
