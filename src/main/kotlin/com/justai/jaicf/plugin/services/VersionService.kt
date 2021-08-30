package com.justai.jaicf.plugin.services

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import org.jetbrains.kotlin.js.inline.util.zipWithDefault

class VersionService(private val project: Project) {

    fun getJaicfVersion(): Version? {
        val version = getDependencies().filter { it.name?.contains("com.just-ai.jaicf:core") == true }
            .mapNotNull { it.name?.split(":")?.last() }.firstOrNull() ?: return null

        return Version(version)
    }

    private fun getDependencies(): List<Library> {
        return LibraryTablesRegistrar.getInstance().getLibraryTable(project).libraries.toList()
    }

    companion object {
        fun get(project: Project): VersionService? = ServiceManager.getService(project, VersionService::class.java)

        fun usedAnnotations(project: Project): Boolean {
            val jaicfVersion = get(project)?.getJaicfVersion() ?: return false
            return jaicfVersion >= Version("1.1.3")
        }
    }
}

data class Version(val version: String) {

    private val components = version.split(".", "-")

    operator fun compareTo(other: Version) =
        components.zipWithDefault(other.components, "").fold(0) { prev, (left, right) ->
            if (prev != 0) {
                prev
            } else {
                left.toIntOrNull()?.let { leftNumber ->
                    right.toIntOrNull()?.let { rightNumber -> leftNumber.compareTo(rightNumber) }
                } ?: left.compareTo(right)
            }
        }
}