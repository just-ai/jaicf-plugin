package com.justai.jaicf.plugin.providers

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
            if (element.isExist) element.project.getService(ReferenceContributorsAvailabilityService::class.java)
            else null
    }
}
