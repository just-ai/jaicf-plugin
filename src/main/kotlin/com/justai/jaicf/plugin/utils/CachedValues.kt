package com.justai.jaicf.plugin.utils

import com.intellij.openapi.project.Project
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import com.justai.jaicf.plugin.isExist
import com.justai.jaicf.plugin.trackers.FileModificationTracker
import com.justai.jaicf.plugin.trackers.KtFilesModificationTracker
import kotlin.reflect.KProperty
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.core.script.configuration.utils.getKtFile
import org.jetbrains.kotlin.idea.search.projectScope
import org.jetbrains.kotlin.psi.KtFile

fun <T> Project.cached(vararg dependencies: Any, valueProvider: () -> (T)) = CachedValueDelegate(
    CachedValuesManager.getManager(this).createCachedValue { Result.create(valueProvider(), *dependencies) }
)

class CachedValueDelegate<T>(private val cachedValue: CachedValue<T>) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = cachedValue.value
}

class LiveMapByFiles<T>(val project: Project, private val lambda: (KtFile) -> (T)) {

    private val cachedMap by project.cached(KtFilesModificationTracker(project)) {
        val allFiles =
            FileTypeIndex.getFiles(KotlinFileType.INSTANCE, project.projectScope()).mapNotNull { project.getKtFile(it) }
                .mapNotNull { it.originalFile as? KtFile }
        val mapSnapshot = mapOfCachedValue
        val newMap = allFiles.associateWith { mapSnapshot[it] ?: cachedVal(it) }
        mapOfCachedValue = newMap
        newMap
    }

    @Volatile
    private var mapOfCachedValue = mapOf<KtFile, CachedValue<T>>()

    operator fun get(file: KtFile): T? = cachedMap[file]?.value

    fun getValues() = cachedMap.values.map { it.value }

    private fun cachedVal(file: KtFile) =
        CachedValuesManager.getManager(project).createCachedValue {
            val value = if (file.isExist) lambda(file) else null
            val tracker = FileModificationTracker.getInstance(file)
            Result.create(value, tracker)
        }
}
