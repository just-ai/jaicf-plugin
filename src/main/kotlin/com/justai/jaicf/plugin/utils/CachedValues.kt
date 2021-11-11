package com.justai.jaicf.plugin.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker.NEVER_CHANGED
import com.intellij.psi.PsiElement
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.justai.jaicf.plugin.trackers.FileModificationTracker
import com.justai.jaicf.plugin.trackers.KtFilesModificationTracker
import kotlin.reflect.KProperty
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.core.script.configuration.utils.getKtFile
import org.jetbrains.kotlin.idea.search.projectScope
import org.jetbrains.kotlin.psi.KtFile

// There can be Service for storage for all PsiElements CachedValues
fun <C : PsiElement, T> cached(
    element: C,
    vararg dependencies: Any = arrayOf(PsiModificationTracker.MODIFICATION_COUNT),
    valueProvider: C.() -> (T)
) = CachedValueDelegate(
    CachedValuesManager.getManager(element.project)
        .createCachedValue { Result.create(valueProvider(element), *dependencies) }
)

fun <T> Project.cached(vararg dependencies: Any, valueProvider: () -> (T)) = CachedValueDelegate(
    CachedValuesManager.getManager(this).createCachedValue { Result.create(valueProvider(), *dependencies) }
)

class CachedValueDelegate<T>(private val cachedValue: CachedValue<T>) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = cachedValue.value
}

class LiveMapByFiles<T>(val project: Project, private val valueProvider: (KtFile) -> (T)) {

    private val cachedMap by project.cached(KtFilesModificationTracker(project)) {
        val allFiles =
            FileTypeIndex.getFiles(KotlinFileType.INSTANCE, project.projectScope()).mapNotNull { project.getKtFile(it) }
                .mapNotNull { it.originalFile as? KtFile }
        val mapSnapshot = mapOfCachedValue
        val newMap = allFiles.associateWith { mapSnapshot[it] ?: createCachedValue(it) }
        mapOfCachedValue = newMap
        newMap
    }

    @Volatile
    private var mapOfCachedValue = mapOf<KtFile, CachedValue<T>>()

    operator fun get(file: KtFile): T? = cachedMap[file]?.value

    fun getValues() = cachedMap.values.map { it.value }

    private fun createCachedValue(file: KtFile) =
        CachedValuesManager.getManager(project).createCachedValue {
            val value = if (file.isExist) valueProvider(file) else null
            val tracker = FileModificationTracker.getInstance(file) ?: NEVER_CHANGED
            Result.create(value, tracker)
        }
}
