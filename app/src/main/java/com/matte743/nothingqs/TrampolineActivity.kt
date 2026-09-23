package com.matte743.nothingqs

import android.app.Activity
import android.os.Bundle

/**
 * Invisible activity started by widget buttons that may need to open a system
 * panel. Being in the foreground lets it open other activities and vibrate.
 */
class TrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent?.action
        if (action != null) {
            try {
                Actions.handleInActivity(this, action, intent.getIntExtra(Actions.EXTRA_LEVEL, 0))
            } catch (_: Exception) {
            }
        }
        WidgetRenderer.updateAllDelayed(applicationContext)
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
