/*
 * Notaria: shows a readable crash report instead of silently closing, so a startup crash can
 * be copied and sent for diagnosis. The report screen runs in its own process.
 */

package me.zhanghai.android.files.app

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import kotlin.system.exitProcess

object CrashHandler {
    fun install(application: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val versionName = runCatching {
                    application.packageManager.getPackageInfo(application.packageName, 0).versionName
                }.getOrNull()
                val report = buildString {
                    appendLine("Notaria $versionName")
                    appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    appendLine("${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("Thread: ${thread.name}")
                    appendLine()
                    append(Log.getStackTraceString(throwable))
                }
                application.startActivity(
                    Intent(application, CrashActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        .putExtra(CrashActivity.EXTRA_REPORT, report)
                )
            } catch (t: Throwable) {
                previous?.uncaughtException(thread, throwable)
            }
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }
}
