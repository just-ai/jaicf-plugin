package com.justai.jaicf.plugin.services

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.search.SearchScope
import com.justai.jaicf.plugin.PATH_ARGUMENT_ANNOTATION_NAME
import com.justai.jaicf.plugin.PLUGIN_PACKAGE
import com.justai.jaicf.plugin.REACTIONS_CHANGE_STATE_METHOD_NAME
import com.justai.jaicf.plugin.REACTIONS_CLASS_NAME
import com.justai.jaicf.plugin.REACTIONS_GO_METHOD_NAME
import com.justai.jaicf.plugin.REACTIONS_PACKAGE
import com.justai.jaicf.plugin.RecursiveSafeValue
import com.justai.jaicf.plugin.findClass
import com.justai.jaicf.plugin.isExist
import com.justai.jaicf.plugin.search
import kotlin.reflect.KProperty
import org.jetbrains.kotlin.idea.search.allScope
import org.jetbrains.kotlin.idea.search.minus
import org.jetbrains.kotlin.idea.search.projectScope
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

// TODO разбить на 2 реализации
class JumpMethodsService(private val project: Project) : Service {

    private val versionService = VersionService[project]

    private var annotationClass: PsiClass? = null
        get() = field ?: findClass(PLUGIN_PACKAGE, PATH_ARGUMENT_ANNOTATION_NAME, project).also { field = it }

    private val jaicfMethods by lazy { findUsages(project.allScope() - project.projectScope()) }
    private val projectMethods = RecursiveSafeValue(emptyList<KtFunction>()) { findUsages(project.projectScope()) }
//    private val predefinedJumpReactions by lazy { findPredefinedJumpReactions() }

    val methods
        get() = when {
            !versionService.isJaicfInclude -> emptyList()
            versionService.isJaicfSupportsAnnotations -> (jaicfMethods + projectMethods.value)
            else -> predefinedJumpReactions
        }.filter { it.isExist }


    override fun markFileAsModified(file: KtFile) {
        projectMethods.invalid()
    }

    private fun findUsages(scope: SearchScope): List<KtFunction> {
        return annotationClass?.search(scope)
            ?.mapNotNull { it.element.getParentOfType<KtFunction>(true) }
            ?.distinct() ?: emptyList()
    }

    private fun findPredefinedJumpReactions(): List<PsiElement> {
        val reactionsClass = findClass(REACTIONS_PACKAGE, REACTIONS_CLASS_NAME, project) ?: return emptyList()

        return reactionsClass.allMethods.filter {
            it.name == REACTIONS_GO_METHOD_NAME || it.name == REACTIONS_CHANGE_STATE_METHOD_NAME
        }
    }

    companion object {
        operator fun get(project: Project): JumpMethodsService =
            ServiceManager.getService(project, JumpMethodsService::class.java)
    }
}


class LazySample {
    init {
        println("created!")            // 1
    }

    val lazyStr: String by lazy {
        println("computed!")          // 2
        "my lazy"
    }
}