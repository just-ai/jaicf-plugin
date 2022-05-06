package com.justai.jaicf.plugin.services

import com.google.common.cache.CacheBuilder.newBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiModificationTracker.SERVICE.getInstance
import com.justai.jaicf.plugin.utils.measure
import kotlin.reflect.KProperty

//class CachingService(val project: Project) {
//
//    private val caches = newBuilder().weakKeys().concurrencyLevel(4).build<Any, SynchronizedCachedValue<*>>()
//
//    fun <T> getOrCreateCachedValue(
//        element: Any,
//        name: String,
//        producer: () -> SynchronizedCachedValue<T>
//    ): SynchronizedCachedValue<T> {
//
//        return caches.get(element) { producer() }
//    }
//}

//fun <R : PsiElement, T> updatingCache(lambda: R.(T?) -> T) =
//    CachingValueDelegate({ arrayOf(TimeModificationTracker(10000)) }, lambda)
//
//fun <R : PsiElement, T> caching(lambda: R.() -> T) =
//    CachingValueDelegate<R, T>({ arrayOf(getInstance(project)) }) { lambda() }

//fun <R : PsiElement, T> cachingWith(lambda: R.() -> T) =
//    CachingValueDelegate<R, T>({ arrayOf(getInstance(project)) }) { lambda() }
//
//fun <R : PsiElement, T> caching(vararg trackers: ModificationTracker, lambda: R.() -> T) =
//    CachingValueDelegate<R, T>({ trackers }) { lambda() }
//
//fun <R : PsiElement, T> caching(trackers: R.() -> Array<ModificationTracker>, lambda: R.() -> T) =
//    CachingValueDelegate<R, T>(trackers) { lambda() }

fun <R : PsiElement, T> cachingField(trackers: R.() -> Array<ModificationTracker>, lambda: R.() -> T) =
    CachingValueDelegateForPsi<R, T>(trackers) { lambda() }

fun <R : PsiElement, T> cachingFieldOne(tracker: R.() -> ModificationTracker, lambda: R.() -> T) =
    CachingValueDelegateForPsi<R, T>({ arrayOf(tracker()) }) { lambda() }

fun <R : Any, T> caching(project: R.() -> Project, trackers: R.() -> Array<ModificationTracker>, lambda: R.() -> T) =
    CachingValueDelegate<R, T>(project, trackers) { lambda() }

fun <R : Any, T> caching(project: R.() -> Project, lambda: R.() -> T) =
    CachingValueDelegate<R, T>(project, { arrayOf(getInstance(project())) }) { lambda() }
//
//fun <R : PsiElement, T> cachingOne(trackers: R.() -> ModificationTracker, lambda: R.() -> T) =
//    CachingValueDelegate<R, T>({ arrayOf(trackers()) }) { lambda() }

class CachingValueDelegate<R : Any, T>(
    val project: R.() -> Project,
    val trackers: R.() -> Array<out ModificationTracker>,
    val lambda: R.(T?) -> T
) {

    private val caches = newBuilder().weakKeys().build<R, SynchronizedCachedValue<R, T>>()

    operator fun getValue(thisRef: R, property: KProperty<*>): T {
        return thisRef.project().measure({ "${thisRef.javaClass}.${property.name}" }) {
            caches.get(thisRef) {
                measure("calc") {
                    SynchronizedCachedValue(
                        trackers(thisRef),
                        thisRef.project(),
                        thisRef,
                        lambda
                    )
                }
            }
                .getValue()
        }
    }
}

class CachingValueDelegateForPsi<R : PsiElement, T>(
    val trackers: R.() -> Array<out ModificationTracker>,
    val lambda: R.(T?) -> T
) {

    private val caches = newBuilder().weakKeys().build<R, SynchronizedCachedValue<R, T>>()

    operator fun getValue(thisRef: R, property: KProperty<*>): T {
        return thisRef.project.measure({ "${thisRef.javaClass}.${property.name}" }) {
            caches.get(thisRef) {
                measure("calc") {
                    SynchronizedCachedValue(
                        trackers(thisRef),
                        thisRef.project,
                        thisRef,
                        lambda
                    )
                }
            }
                .getValue()
        }
    }
}

class SynchronizedCachedValue<R, T>(
    private val trackers: Array<out ModificationTracker>,
    private val project: Project,
    private val receiver: R,
    private val producer: R.(T?) -> T
) {

    @Volatile
    private var lastResult: Result? = null

    fun getValue(): T {
        lastResult?.let {
            if (it.modificationCountSum == trackers.sumOf { tracker -> tracker.modificationCount }) return it.value
        }


        return project.measure("update value") { updateValue().value }
    }

    private fun updateValue(): Result {
        synchronized(this) {
            val startModificationCount = trackers.sumOf { tracker -> tracker.modificationCount }

            lastResult?.let {
                if (it.modificationCountSum == startModificationCount) return it
            }

            val value = receiver.producer(lastResult?.value)
            val endModificationCount = trackers.sumOf { tracker -> tracker.modificationCount }
            return Result(value, startModificationCount, startModificationCount == endModificationCount)
                .also { lastResult = it }
        }
    }

    private inner class Result(val value: T, val modificationCountSum: Long, val consistent: Boolean)
}
