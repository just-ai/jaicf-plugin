package com.justai.jaicf.plugin.services.managers

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.justai.jaicf.plugin.services.AvailabilityService
import org.jetbrains.kotlin.idea.search.projectScope
import org.jetbrains.kotlin.resolve.jvm.KotlinJavaPsiFacade

fun PsiElement.search(scope: SearchScope = this.project.projectScope()): Collection<PsiReference> {
    if (DumbService.getInstance(project).isDumb)
        throw ProcessCanceledException()

    val service = AvailabilityService[this]
    return try {
        service?.disableReferenceContributors()
        ReferencesSearch.search(this, scope, true).findAll()
    } finally {
        service?.enableReferenceContributors()
    }
}

fun findClass(packageFq: String, className: String, project: Project): PsiClass? {
    if (DumbService.getInstance(project).isDumb)
        return null // TODO кидать что то другое

    val kotlinPsiFacade = KotlinJavaPsiFacade.getInstance(project)
    val projectScope = GlobalSearchScope.allScope(project)
    return kotlinPsiFacade.findPackage(packageFq, projectScope)?.classes?.firstOrNull { it.name == className }
}

fun PsiClass.getMethods(vararg methods: String): List<PsiMethod> =
    this.allMethods.filter { it.name in methods }
