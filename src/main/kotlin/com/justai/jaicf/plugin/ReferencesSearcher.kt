package com.justai.jaicf.plugin

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Query
import com.justai.jaicf.plugin.services.AvailabilityService
import org.jetbrains.kotlin.idea.search.projectScope

fun PsiElement.search(scope: SearchScope = this.project.projectScope()): Query<PsiReference> {
    val service = AvailabilityService[this]
    return try {
        service?.disableReferenceContributors()
        ReferencesSearch.search(this, scope, true)
    } finally {
        service?.enableReferenceContributors()
    }
}