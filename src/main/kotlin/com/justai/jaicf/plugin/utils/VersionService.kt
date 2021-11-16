package com.justai.jaicf.plugin.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.caches.project.LibraryModificationTracker

class VersionService(project: Project) {

    val jaicf by project.cached(LibraryModificationTracker.getInstance(project)) {
        libraries
            .filter { it.name?.contains("$JAICF_GROUP_ID:$JAICF_CORE_ARTIFACT_ID") == true }
            .mapNotNull { it.name?.split(":")?.last() }
            .firstOrNull()
            ?.let(::Version)
    }

    private val libraries by project.cached(LibraryModificationTracker.getInstance(project)) {
        LibraryTablesRegistrar.getInstance().getLibraryTable(project).libraries.toList()
    }

    companion object {
        fun getInstance(project: Project): VersionService =
            project.getService(VersionService::class.java)

        fun getInstance(element: PsiElement): VersionService? =
            if (element.isExist) element.project.getService(VersionService::class.java)
            else null
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

val VersionService.isSupportedJaicfInclude: Boolean
    get() = jaicf?.let { it >= Version("1.1.3") } == true

val VersionService.isJaicfInclude: Boolean
    get() = jaicf != null
