package com.justai.jaicf.plugin.activities

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.IdempotenceChecker
import org.apache.log4j.Level

/**
 * Отключает лоигрование ошибок о нарушении идемпотентности в кешах.
 * Предупреждение бросается, когда идея запускает пересчёт встроенных кешей в несколько потоков и получает в разных потоках разный результат.
 * Разный результат возникает из-за того, что не все элементы ещё просчитаны.
 */
class DisableIdempotenceCheckerLoggerStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
        DumbService.getInstance(project).runWhenSmart {
            Logger.getInstance(IdempotenceChecker::class.java).setLevel(Level.OFF)
        }
    }
}
