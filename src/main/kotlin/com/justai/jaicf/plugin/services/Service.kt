package com.justai.jaicf.plugin.services

import org.jetbrains.kotlin.psi.KtFile

interface Service {
    fun markFileAsModified(file: KtFile)
}