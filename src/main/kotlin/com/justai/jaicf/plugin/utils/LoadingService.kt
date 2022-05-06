package com.justai.jaicf.plugin.utils

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.rd.util.currentThreadName
import com.justai.jaicf.plugin.scenarios.JaicfService
import java.time.LocalTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.measureTimeMillis

class LoadingService {


    // TODO rename
    fun load() {
    }

    // TODO rename
    fun await() {
    }

    companion object {
        fun getInstance(project: Project): LoadingService =
            project.getService(LoadingService::class.java)

        fun getInstance(element: PsiElement): LoadingService? =
            if (element.isExist) element.project.getService(LoadingService::class.java)
            else null
    }
}

class MeasureService(val project: Project) {

    private val threads = ConcurrentHashMap<String, Int>()
    private val results = ConcurrentHashMap<String, MutableList<String>>()

    private val allowedRoots = listOf<String>("StatePathVisitor")

    fun <T> measure(method: () -> String, block: Project.() -> T): T {
        val result: Result<T>
        val indent: String

        if (allowedRoots.isNotEmpty() &&
            threads.getOrDefault(currentThreadName(), 0) == 0 &&
            allowedRoots.none { method().contains(it) }
        )
            return project.block()

        synchronized(this) {
            threads.getOrDefault(currentThreadName(), 0).also {
                threads[currentThreadName()] = it + 1
                indent = (1..it * 4).joinToString("") { " " }
            }
        }
        val start = LocalTime.now()
        measureTimeMillis { result = runCatching { project.block() } }.also {
            if (it > 10)
            results.getOrPut(
                currentThreadName(),
                { mutableListOf() }) += "$indent|${method()}: $it : $start - ${LocalTime.now()}  return $result"
        }
        synchronized(this) {
            threads[currentThreadName()]?.also {
                threads[currentThreadName()] = it - 1
                if (it == 1) {
//                    results[currentThreadName()]?.asReversed()?.forEach { println(it) }
                    results[currentThreadName()] = mutableListOf()
                }
            }
        }
        return result.getOrThrow()
    }

    companion object {
        fun getInstance(project: Project): MeasureService =
            project.getService(MeasureService::class.java)

        fun getInstance(element: PsiElement): MeasureService? =
            if (element.isExist) element.project.getService(MeasureService::class.java)
            else null
    }
}

fun <T> PsiElement.measure(method: String, block: Project.() -> T): T {
    return MeasureService.getInstance(this)?.measure({ method }, block) ?: project.block()
}

fun <T> PsiElement.measure(method: () -> String, block: Project.() -> T): T {
    return MeasureService.getInstance(this)?.measure(method, block) ?: project.block()
}

fun <T> Project.measure(method: String, block: Project.() -> T): T {
    return MeasureService.getInstance(this).measure({ method }, block) ?: block()
}

fun <T> Project.measure(method: () -> String, block: Project.() -> T): T {
    return MeasureService.getInstance(this).measure(method, block) ?: block()
}

fun <T> Project.blockMeasure(method: String, block: Project.() -> T) {
    MeasureService.getInstance(this).measure({ method }, block) ?: block()
}

fun <T> JaicfService.measure(method: String, block: Project.() -> T): T {
    return MeasureService.getInstance(this.project).measure({ method }, block) ?: project.block()
}
