package com.justai.jaicf.plugin.utils

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.rd.util.currentThreadName
import com.justai.jaicf.plugin.scenarios.JaicfService
import kotlin.system.measureTimeMillis
import java.util.concurrent.ConcurrentHashMap

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

    private val allowedRoots = listOf("StateContext.isInContext")

    fun <T> measure(method: () -> String, block: Project.() -> T): T {
        val result: Result<T>
        val indent: String

        if (threads.getOrDefault(currentThreadName(), 0) == 0 && method() !in allowedRoots)
            return project.block()

        synchronized(this) {
            threads.getOrDefault(currentThreadName(), 0).also {
                threads[currentThreadName()] = it + 1
                indent = (1..it * 4).joinToString("") { " " }
            }
        }
        measureTimeMillis { result = runCatching { project.block() } }.also {
            if (it > 2) println("$indent|${method()}: $it")
        }
        synchronized(this) {
            threads[currentThreadName()]?.also { threads[currentThreadName()] = it - 1 }
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
    return MeasureService.getInstance(this)?.measure({method}, block) ?: project.block()
}
fun <T> PsiElement.measure(method: () -> String, block: Project.() -> T): T {
    return MeasureService.getInstance(this)?.measure(method, block) ?: project.block()
}

fun <T> Project.measure(method: String, block: Project.() -> T): T {
    return MeasureService.getInstance(this).measure({ method }, block) ?: block()
}
 fun <T> Project.blockMeasure(method: String, block: Project.() -> T) {
    MeasureService.getInstance(this).measure({ method }, block) ?: block()
}

fun <T> JaicfService.measure(method: String, block: Project.() -> T): T {
    return MeasureService.getInstance(this.project).measure({ method }, block) ?: project.block()
}