package com.matte743.nothingqs

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receives widget taps that can be handled without showing any UI. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val level = intent.getIntExtra(Actions.EXTRA_LEVEL, 0)
        val app = context.applicationContext
        val pending = goAsync()
        Thread {
            try {
                val handled = Actions.handleInBackground(app, action, level)
                if (!handled) {
                    // Direct toggle not possible (e.g. Shizuku stopped): open the panel instead.
                    try {
                        Actions.activity(app, action, level).send()
                    } catch (_: Exception) {
                    }
                }
                WidgetRenderer.updateAll(app)
                // Some settings apply asynchronously: refresh once more shortly after.
                Thread.sleep(700)
                WidgetRenderer.updateAll(app)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
