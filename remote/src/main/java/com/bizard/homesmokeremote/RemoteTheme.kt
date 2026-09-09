package com.bizard.homesmokeremote

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.view.View

internal enum class RemoteThemeMode(val value: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun from(value: String?): RemoteThemeMode =
            entries.firstOrNull { it.value == value } ?: SYSTEM
    }
}

internal data class RemotePalette(
    val dark: Boolean,
    val background: Int,
    val surface: Int,
    val surfaceVariant: Int,
    val topBar: Int,
    val ink: Int,
    val muted: Int,
    val outline: Int,
    val primary: Int,
    val green: Int,
    val red: Int,
    val orange: Int,
    val off: Int,
    val infoSurface: Int,
    val warningSurface: Int,
)

/** Shared theme preference and colors for both Compose and classic Android screens. */
internal object RemoteTheme {
    const val PREF_KEY = "theme_mode"

    private val light =
        RemotePalette(
            dark = false,
            background = Color.rgb(245, 244, 240),
            surface = Color.WHITE,
            surfaceVariant = Color.rgb(239, 240, 236),
            topBar = Color.WHITE,
            ink = Color.rgb(32, 42, 39),
            muted = Color.rgb(98, 110, 104),
            outline = Color.rgb(218, 223, 217),
            primary = Color.rgb(165, 72, 34),
            green = Color.rgb(40, 101, 76),
            red = Color.rgb(168, 52, 52),
            orange = Color.rgb(197, 101, 16),
            off = Color.rgb(116, 129, 145),
            infoSurface = Color.rgb(234, 240, 246),
            warningSurface = Color.rgb(255, 246, 232),
        )

    private val dark =
        RemotePalette(
            dark = true,
            background = Color.rgb(15, 21, 19),
            surface = Color.rgb(25, 33, 30),
            surfaceVariant = Color.rgb(35, 46, 42),
            topBar = Color.rgb(20, 28, 25),
            ink = Color.rgb(236, 242, 239),
            muted = Color.rgb(171, 184, 178),
            outline = Color.rgb(68, 82, 76),
            primary = Color.rgb(232, 137, 95),
            green = Color.rgb(116, 198, 157),
            red = Color.rgb(255, 139, 139),
            orange = Color.rgb(240, 163, 94),
            off = Color.rgb(142, 156, 150),
            infoSurface = Color.rgb(24, 42, 48),
            warningSurface = Color.rgb(52, 38, 29),
        )

    fun mode(context: Context): RemoteThemeMode =
        RemoteThemeMode.from(
            context
                .getSharedPreferences("homesmoke_remote", Context.MODE_PRIVATE)
                .getString(PREF_KEY, RemoteThemeMode.SYSTEM.value)
        )

    fun setMode(context: Context, mode: RemoteThemeMode) {
        context
            .getSharedPreferences("homesmoke_remote", Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_KEY, mode.value)
            .apply()
    }

    fun isDark(context: Context): Boolean =
        when (mode(context)) {
            RemoteThemeMode.DARK -> true
            RemoteThemeMode.LIGHT -> false
            RemoteThemeMode.SYSTEM ->
                context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                    Configuration.UI_MODE_NIGHT_YES
        }

    fun palette(context: Context): RemotePalette = palette(isDark(context))

    fun palette(darkTheme: Boolean): RemotePalette = if (darkTheme) dark else light

    fun dialogTheme(context: Context): Int =
        if (isDark(context)) AlertDialog.THEME_DEVICE_DEFAULT_DARK
        else AlertDialog.THEME_DEVICE_DEFAULT_LIGHT

    fun applySystemBars(activity: Activity, darkTheme: Boolean = isDark(activity)) {
        if (Build.VERSION.SDK_INT < 21) return
        val colors = palette(darkTheme)
        activity.window.statusBarColor = colors.topBar
        activity.window.navigationBarColor = colors.background

        var flags = activity.window.decorView.systemUiVisibility
        if (Build.VERSION.SDK_INT >= 23) {
            flags =
                if (darkTheme) flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                else flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        if (Build.VERSION.SDK_INT >= 26) {
            flags =
                if (darkTheme) flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                else flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        activity.window.decorView.systemUiVisibility = flags
    }
}
