package com.justai.jaicf.plugin.providers

import com.intellij.openapi.components.ServiceManager
import com.intellij.psi.PsiElement
import com.justai.jaicf.plugin.utils.isExist

class ReferenceContributorsAvailabilityService {

    @Volatile
    var available = true
        private set

    fun disable() {
        available = false
    }

    fun enable() {
        available = true
    }

    companion object {
        fun getInstance(element: PsiElement) =
            if (element.isExist) ServiceManager.getService(element.project, ReferenceContributorsAvailabilityService::class.java)
            else null
    }
}
