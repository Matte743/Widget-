package com.matte743.nothingqs

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews

/** Builds the widget's RemoteViews from the current system state. */
object WidgetRenderer {

    private val briSegments = intArrayOf(
        R.id.bri_seg_1, R.id.bri_seg_2, R.id.bri_seg_3, R.id.bri_seg_4, R.id.bri_seg_5,
        R.id.bri_seg_6, R.id.bri_seg_7, R.id.bri_seg_8, R.id.bri_seg_9, R.id.bri_seg_10,
    )
    private val briDots = intArrayOf(
        R.id.bri_dot_1, R.id.bri_dot_2, R.id.bri_dot_3, R.id.bri_dot_4, R.id.bri_dot_5,
        R.id.bri_dot_6, R.id.bri_dot_7, R.id.bri_dot_8, R.id.bri_dot_9, R.id.bri_dot_10,
    )
    private val volSegments = intArrayOf(
        R.id.vol_seg_1, R.id.vol_seg_2, R.id.vol_seg_3, R.id.vol_seg_4, R.id.vol_seg_5,
        R.id.vol_seg_6, R.id.vol_seg_7, R.id.vol_seg_8, R.id.vol_seg_9, R.id.vol_seg_10,
    )
    private val volDots = intArrayOf(
        R.id.vol_dot_1, R.id.vol_dot_2, R.id.vol_dot_3, R.id.vol_dot_4, R.id.vol_dot_5,
        R.id.vol_dot_6, R.id.vol_dot_7, R.id.vol_dot_8, R.id.vol_dot_9, R.id.vol_dot_10,
    )

    /** Refreshes every placed widget. Call from a worker thread. */
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, QsWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val views = build(context, SystemControls.readState(context))
        manager.updateAppWidget(ids, views)
    }

    /** Refreshes a little later, once a system panel or toggle had time to apply. */
    fun updateAllDelayed(context: Context) {
        val app = context.applicationContext
        Thread {
            for (delay in longArrayOf(600, 2000)) {
                try {
                    Thread.sleep(delay)
                    updateAll(app)
                } catch (_: Exception) {
                }
            }
        }.start()
    }

    fun build(context: Context, state: QsState): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_qs)
        val shizuku = Prefs.useShizuku(context)

        // ---- Brightness bar
        val briLit = Brightness.segments(state)
        bindBar(views, briDots, briLit)
        for (i in briSegments.indices) {
            val pi = if (state.canWriteSettings) Actions.broadcast(context, Actions.BRIGHTNESS_SET, i + 1)
            else Actions.activity(context, Actions.OPEN_APP)
            views.setOnClickPendingIntent(briSegments[i], pi)
        }
        views.setImageViewResource(
            R.id.bri_icon, if (state.autoBrightness) R.drawable.ic_brightness_auto_on else R.drawable.ic_brightness
        )
        views.setInt(
            R.id.bri_icon, "setBackgroundResource",
            if (state.autoBrightness) R.drawable.tile_on_bg else R.drawable.tile_off_bg,
        )
        views.setOnClickPendingIntent(
            R.id.bri_icon,
            if (state.canWriteSettings) Actions.broadcast(context, Actions.BRIGHTNESS_AUTO)
            else Actions.activity(context, Actions.OPEN_APP),
        )
        val briText = if (state.autoBrightness) "AUTO" else "${(state.brightness * 100).toInt()}%"
        views.setImageViewBitmap(R.id.bri_value, DotFont.render(briText))

        // ---- Volume bar
        val volLit = SystemControls.volumeSegments(state)
        bindBar(views, volDots, volLit)
        for (i in volSegments.indices) {
            views.setOnClickPendingIntent(volSegments[i], Actions.broadcast(context, Actions.VOLUME_SET, i + 1))
        }
        views.setImageViewResource(R.id.vol_icon, if (state.muted) R.drawable.ic_volume_off else R.drawable.ic_volume)
        views.setOnClickPendingIntent(R.id.vol_icon, Actions.broadcast(context, Actions.VOLUME_MUTE))
        val volPercent = if (state.muted) 0 else (state.volume * 100f / state.volumeMax).toInt()
        views.setImageViewBitmap(R.id.vol_value, DotFont.render("$volPercent%"))

        // ---- Tiles
        bindTile(views, R.id.tile_wifi_icon, state.wifi, R.drawable.ic_wifi, R.drawable.ic_wifi_on)
        bindTile(views, R.id.tile_data_icon, state.data, R.drawable.ic_data, R.drawable.ic_data_on)
        bindTile(views, R.id.tile_bt_icon, state.bluetooth, R.drawable.ic_bluetooth, R.drawable.ic_bluetooth_on)
        bindTile(views, R.id.tile_hotspot_icon, state.hotspot, R.drawable.ic_hotspot, R.drawable.ic_hotspot_on)
        bindTile(views, R.id.tile_torch_icon, state.torch, R.drawable.ic_torch, R.drawable.ic_torch_on)
        // The Glyph torch uses Nothing red when lit.
        views.setImageViewResource(R.id.tile_glyph_icon, if (state.glyph) R.drawable.ic_glyph_red else R.drawable.ic_glyph)
        views.setInt(
            R.id.tile_glyph_icon, "setBackgroundResource",
            if (state.glyph) R.drawable.tile_red_bg else R.drawable.tile_off_bg,
        )

        fun toggle(action: String) =
            if (shizuku) Actions.broadcast(context, action) else Actions.activity(context, action)
        views.setOnClickPendingIntent(R.id.tile_wifi, toggle(Actions.WIFI))
        views.setOnClickPendingIntent(R.id.tile_data, toggle(Actions.DATA))
        views.setOnClickPendingIntent(R.id.tile_bt, toggle(Actions.BLUETOOTH))
        views.setOnClickPendingIntent(R.id.tile_hotspot, Actions.activity(context, Actions.HOTSPOT))
        views.setOnClickPendingIntent(R.id.tile_torch, Actions.broadcast(context, Actions.TORCH))
        views.setOnClickPendingIntent(R.id.tile_glyph, Actions.glyphService(context))
        return views
    }

    private fun bindBar(views: RemoteViews, dots: IntArray, lit: Int) {
        for (i in dots.indices) {
            val res = when {
                i + 1 == lit -> R.drawable.dot_red // the current level, in Nothing red
                i + 1 < lit -> R.drawable.dot_on
                else -> R.drawable.dot_off
            }
            views.setImageViewResource(dots[i], res)
        }
    }

    private fun bindTile(views: RemoteViews, iconId: Int, on: Boolean, offIcon: Int, onIcon: Int) {
        views.setImageViewResource(iconId, if (on) onIcon else offIcon)
        views.setInt(iconId, "setBackgroundResource", if (on) R.drawable.tile_on_bg else R.drawable.tile_off_bg)
    }
}
