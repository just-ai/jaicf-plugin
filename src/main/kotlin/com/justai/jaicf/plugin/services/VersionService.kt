package com.justai.jaicf.plugin.services

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.psi.PsiElement
import com.justai.jaicf.plugin.JAICF_CORE_ARTIFACT_ID
import com.justai.jaicf.plugin.JAICF_GROUP_ID
import com.justai.jaicf.plugin.RecursiveSafeValue
import com.justai.jaicf.plugin.isExist
import org.jetbrains.kotlin.psi.KtFile

class VersionService(private val project: Project) : Service {

    val jaicf: Version?
        get() = jaicfVersionValue.value

    private val jaicfVersionValue = RecursiveSafeValue<Version?>(null) {
        val libraries = LibraryTablesRegistrar.getInstance().getLibraryTable(project).libraries.toList()
        val version = libraries
            .filter { it.name?.contains("$JAICF_GROUP_ID:$JAICF_CORE_ARTIFACT_ID") == true }
            .mapNotNull { it.name?.split(":")?.last() }
            .firstOrNull()

        version?.let(::Version)
    }

    override fun markFileAsModified(file: KtFile) {
        jaicfVersionValue.invalid()
    }

    companion object {
        operator fun get(project: Project): VersionService =
            ServiceManager.getService(project, VersionService::class.java)

        operator fun get(element: PsiElement): VersionService? =
            if (element.isExist) ServiceManager.getService(element.project, VersionService::class.java)
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

val VersionService.isJaicfSupportsAnnotations: Boolean
    get() = this.jaicf?.let { it >= Version("1.1.3") } == true

val VersionService.isJaicfInclude: Boolean
    get() = this.jaicf != null
