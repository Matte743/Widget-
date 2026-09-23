package com.matte743.nothingqs

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

class QsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        refresh(context)
    }

    override fun onEnabled(context: Context) {
        StateSyncJob.schedule(context)
    }

    override fun onDisabled(context: Context) {
        StateSyncJob.cancel(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager, id: Int, options: Bundle,
    ) {
        refresh(context)
    }

    private fun refresh(context: Context) {
        val app = context.applicationContext
        val pending = goAsync()
        Thread {
            try {
                StateSyncJob.schedule(app)
                WidgetRenderer.updateAll(app)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
