package com.matte743.nothingqs

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Optional Shizuku support. Shizuku runs commands with shell privileges, which
 * lets the widget switch Wi-Fi, mobile data and Bluetooth directly instead of
 * opening a system panel.
 */
object ShizukuShell {
    const val PACKAGE = "moe.shizuku.privileged.api"

    enum class Status { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, READY }

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun status(context: Context): Status = when {
        !isInstalled(context) -> Status.NOT_INSTALLED
        !binderAlive() -> Status.NOT_RUNNING
        !hasPermission() -> Status.NO_PERMISSION
        else -> Status.READY
    }

    private fun binderAlive(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    fun hasPermission(): Boolean = try {
        binderAlive() && !Shizuku.isPreV11() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    /**
     * Waits for the Shizuku binder, which arrives shortly after the app process
     * starts. Must not be called on the main thread.
     */
    fun awaitReady(timeoutMs: Long = 1500): Boolean {
        if (hasPermission()) return true
        val latch = CountDownLatch(1)
        val listener = Shizuku.OnBinderReceivedListener { latch.countDown() }
        return try {
            Shizuku.addBinderReceivedListenerSticky(listener)
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            hasPermission()
        } catch (_: Throwable) {
            false
        } finally {
            try {
                Shizuku.removeBinderReceivedListener(listener)
            } catch (_: Throwable) {
            }
        }
    }

    /** Runs a shell command through Shizuku. Returns true when it exits with 0. */
    fun exec(vararg command: String): Boolean {
        if (!hasPermission()) return false
        return try {
            // newProcess is hidden in the public API but still the simplest way
            // to run a one-shot shell command without a bound user service.
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            )
            method.isAccessible = true
            val process = method.invoke(null, arrayOf(*command), null, null) as Process
            val finished = process.waitFor(4, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                false
            } else {
                process.exitValue() == 0
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun setWifi(on: Boolean): Boolean = exec("svc", "wifi", if (on) "enable" else "disable")

    fun setMobileData(on: Boolean): Boolean = exec("svc", "data", if (on) "enable" else "disable")

    fun setBluetooth(on: Boolean): Boolean =
        exec("svc", "bluetooth", if (on) "enable" else "disable") ||
            exec("cmd", "bluetooth_manager", if (on) "enable" else "disable")
}
