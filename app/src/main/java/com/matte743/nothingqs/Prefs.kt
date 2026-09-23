package com.matte743.nothingqs

import android.content.Context
import android.content.SharedPreferences

/** Small wrapper around the app's shared preferences. */
object Prefs {
    private const val FILE = "qs_prefs"
    private const val KEY_HAPTICS = "haptics"
    private const val KEY_SHIZUKU = "use_shizuku"
    private const val KEY_TORCH = "torch_on"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun haptics(context: Context): Boolean = prefs(context).getBoolean(KEY_HAPTICS, true)
    fun setHaptics(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_HAPTICS, value).apply()

    fun useShizuku(context: Context): Boolean = prefs(context).getBoolean(KEY_SHIZUKU, false)
    fun setUseShizuku(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_SHIZUKU, value).apply()

    fun torchCache(context: Context): Boolean = prefs(context).getBoolean(KEY_TORCH, false)
    fun setTorchCache(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_TORCH, value).apply()
}
