package com.justai.jaicf.plugin.services

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.justai.jaicf.plugin.isExist

class AvailabilityService(private val project: Project) {

    @Volatile
    var referenceContributorAvailable = true
        private set

    fun disableReferenceContributors() {
        referenceContributorAvailable = false
    }

    fun enableReferenceContributors() {
        referenceContributorAvailable = true
    }

    companion object {
        operator fun get(element: PsiElement): AvailabilityService? =
            if (element.isExist) element.project.getService(AvailabilityService::class.java)
            else null
    }
}