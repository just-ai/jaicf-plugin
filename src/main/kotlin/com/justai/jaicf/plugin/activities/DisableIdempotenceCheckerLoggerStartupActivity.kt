package com.justai.jaicf.plugin.activities

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.IdempotenceChecker
import org.apache.log4j.Level

// TODO temp solution
class DisableIdempotenceCheckerLoggerStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
        DumbService.getInstance(project).runWhenSmart {
            Logger.getInstance(IdempotenceChecker::class.java).setLevel(Level.OFF)
        }
    }
}
