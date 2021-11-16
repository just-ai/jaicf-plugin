package com.justai.jaicf.plugin.scenarios.psi

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.justai.jaicf.plugin.providers.ReferenceContributorsAvailabilityService
import org.jetbrains.kotlin.idea.search.projectScope
import org.jetbrains.kotlin.resolve.jvm.KotlinJavaPsiFacade

fun PsiElement.search(scope: SearchScope = project.projectScope()): Collection<PsiReference> {
    if (DumbService.getInstance(project).isDumb)
        return emptyList()

    val referenceContributorService = ReferenceContributorsAvailabilityService.getInstance(this)
    return try {
        referenceContributorService?.disable()
        ReferencesSearch.search(this, scope, true).findAll()
    } finally {
        referenceContributorService?.enable()
    }
}

fun findClass(packageFq: String, className: String, project: Project): PsiClass? {
    if (DumbService.getInstance(project).isDumb)
        return null

    val kotlinPsiFacade = KotlinJavaPsiFacade.getInstance(project)
    val projectScope = GlobalSearchScope.allScope(project)
    return kotlinPsiFacade.findPackage(packageFq, projectScope)?.classes?.firstOrNull { it.name == className }
}

fun PsiClass.getMethods(vararg methods: String) =
    allMethods.filter { it.name in methods }
