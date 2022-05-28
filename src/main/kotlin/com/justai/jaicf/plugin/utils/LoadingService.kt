package com.justai.jaicf.plugin.utils

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.justai.jaicf.plugin.scenarios.JaicfService
import java.lang.System.currentTimeMillis
import org.jetbrains.kotlin.backend.common.peek
import org.jetbrains.kotlin.backend.common.pop
import org.jetbrains.kotlin.backend.common.push

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

    private val indent = ThreadLocal.withInitial { "" }
    private val strings = ThreadLocal.withInitial { mutableListOf<String>() }
    private val overTimesStack = ThreadLocal.withInitial { ArrayDeque<Long>() }

    private val roots = listOf<String>("JaicfSourcesMissedNotifier.isValid()")

    fun <T> measure(method: () -> String, block: Project.() -> T): T {
        val methodStart = currentTimeMillis()

        if (indent.get().isEmpty() && roots.isNotEmpty() && roots.none { method().startsWith(it) })
            return project.block()

        indent.set("${indent.get()}    ")
        overTimesStack.get().push(0)

        var beforeStart: Long = -1
        var afterStart: Long = -1

        val result: Result<T> = runCatching {
            beforeStart = currentTimeMillis()
            project.block().also {
                afterStart = currentTimeMillis()
            }
        }

        val blockTime = afterStart - beforeStart
        val clearTime = blockTime - (overTimesStack.get().peek() ?: 0L)

        indent.set(indent.get().substring(4))

        strings.get() += "${indent.get()}|${method()}: $clearTime : return $result"


        if (indent.get().isEmpty()) {
            if (clearTime > 2)
                strings.get().asReversed().forEach(::println)
            strings.get().clear()
        }

        overTimesStack.get().pop()
        val overTime = currentTimeMillis() - methodStart - blockTime
        if (overTime != 0L)
            overTimesStack.get().indices.forEach { overTimesStack.get()[it] += overTime }
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
