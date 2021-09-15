package com.justai.jaicf.plugin.services

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.psi.PsiElement
import com.justai.jaicf.plugin.JAICF_CORE_ARTIFACT_ID
import com.justai.jaicf.plugin.JAICF_GROUP_ID
import com.justai.jaicf.plugin.isExist
import org.jetbrains.kotlin.idea.caches.project.LibraryModificationTracker
import org.jetbrains.kotlin.utils.addToStdlib.measureTimeMillisWithResult


class VersionService(project: Project) : Service(project) {

    val jaicf by cached(LibraryModificationTracker.getInstance(project)) {
        val (l, version) = measureTimeMillisWithResult {
            libraries
                .filter { it.name?.contains("$JAICF_GROUP_ID:$JAICF_CORE_ARTIFACT_ID") == true }
                .mapNotNull { it.name?.split(":")?.last() }
                .firstOrNull()
                ?.let(::Version)
        }

        println("VersionService: jaicf updated: $l")
        version
    }

    private val libraries by cached(LibraryModificationTracker.getInstance(project)) {
        val (l, version) = measureTimeMillisWithResult {
            LibraryTablesRegistrar.getInstance().getLibraryTable(project).libraries.toList()
        }

        println("VersionService: libraries updated: $l")
        version
    }

    companion object {
        operator fun get(project: Project): VersionService =
            project.getService(VersionService::class.java)

        operator fun get(element: PsiElement): VersionService? =
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

val VersionService.isJaicfInclude: Boolean
    get() = this.jaicf?.let { it >= Version("1.1.3") } == true
