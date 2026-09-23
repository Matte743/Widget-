package com.matte743.nothingqs

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.telephony.SubscriptionManager

/**
 * Keeps the widget in sync when settings change elsewhere (Quick Settings,
 * Settings app, volume keys). The job wakes up whenever one of the watched
 * settings changes, redraws the widget, then re-arms itself.
 */
class StateSyncJob : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        val app = applicationContext
        Thread {
            try {
                WidgetRenderer.updateAll(app)
            } finally {
                jobFinished(params, false)
                schedule(app)
            }
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = false

    companion object {
        private const val JOB_ID = 7301

        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            val builder = JobInfo.Builder(JOB_ID, ComponentName(context, StateSyncJob::class.java))
            val descendants = JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS
            // Brightness and every volume stream live in Settings.System.
            builder.addTriggerContentUri(JobInfo.TriggerContentUri(Settings.System.CONTENT_URI, descendants))
            val globals = mutableListOf("wifi_on", "bluetooth_on", "mobile_data", "airplane_mode_on")
            val subId = SubscriptionManager.getDefaultDataSubscriptionId()
            if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) globals += "mobile_data$subId"
            for (name in globals) {
                builder.addTriggerContentUri(JobInfo.TriggerContentUri(Settings.Global.getUriFor(name), 0))
            }
            builder.setTriggerContentUpdateDelay(300)
            builder.setTriggerContentMaxDelay(1500)
            try {
                scheduler.schedule(builder.build())
            } catch (_: RuntimeException) {
            }
        }

        fun cancel(context: Context) {
            context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
        }
    }
}
