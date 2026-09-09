package com.bizard.homesmokemqtt;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.view.View;

/** Presentation-only theme state and palette for the HomeSmoke Android app. */
final class HomeSmokeTheme {
    private static final String PREFS = "homesmoke_full";
    private static final String KEY_THEME = "theme_mode";

    enum Mode {
        SYSTEM("system"),
        LIGHT("light"),
        DARK("dark");

        final String value;

        Mode(String value) {
            this.value = value;
        }

        static Mode from(String value) {
            if (LIGHT.value.equalsIgnoreCase(value)) return LIGHT;
            if (DARK.value.equalsIgnoreCase(value)) return DARK;
            return SYSTEM;
        }
    }

    static final class Palette {
        final boolean dark;
        final int appBar;
        final int primary;
        final int primaryText;
        final int background;
        final int surface;
        final int surfaceAlt;
        final int border;
        final int text;
        final int muted;
        final int success;
        final int warning;
        final int danger;
        final int dangerSoft;
        final int neutralSoft;
        final int editSoft;
        final int editText;
        final int copySoft;
        final int copyText;
        final int hint;
        final int unchecked;

        Palette(
                boolean dark,
                int appBar,
                int primary,
                int primaryText,
                int background,
                int surface,
                int surfaceAlt,
                int border,
                int text,
                int muted,
                int success,
                int warning,
                int danger,
                int dangerSoft,
                int neutralSoft,
                int editSoft,
                int editText,
                int copySoft,
                int copyText,
                int hint,
                int unchecked) {
            this.dark = dark;
            this.appBar = appBar;
            this.primary = primary;
            this.primaryText = primaryText;
            this.background = background;
            this.surface = surface;
            this.surfaceAlt = surfaceAlt;
            this.border = border;
            this.text = text;
            this.muted = muted;
            this.success = success;
            this.warning = warning;
            this.danger = danger;
            this.dangerSoft = dangerSoft;
            this.neutralSoft = neutralSoft;
            this.editSoft = editSoft;
            this.editText = editText;
            this.copySoft = copySoft;
            this.copyText = copyText;
            this.hint = hint;
            this.unchecked = unchecked;
        }
    }

    private HomeSmokeTheme() {}

    static Mode mode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return Mode.from(prefs.getString(KEY_THEME, Mode.SYSTEM.value));
    }

    static void setMode(Context context, Mode mode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_THEME, mode == null ? Mode.SYSTEM.value : mode.value)
                .apply();
    }

    static boolean isDark(Context context) {
        Mode mode = mode(context);
        if (mode == Mode.DARK) return true;
        if (mode == Mode.LIGHT) return false;
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    static Palette palette(Context context) {
        return palette(isDark(context));
    }

    static Palette palette(boolean dark) {
        if (dark) {
            return new Palette(
                    true,
                    0xFF0F172A,
                    0xFF2563EB,
                    0xFF93C5FD,
                    0xFF0B1220,
                    0xFF111827,
                    0xFF1E293B,
                    0xFF334155,
                    0xFFF8FAFC,
                    0xFF94A3B8,
                    0xFF16A34A,
                    0xFFF59E0B,
                    0xFFDC2626,
                    0xFF3A171C,
                    0xFF1E293B,
                    0xFF172554,
                    0xFFBFDBFE,
                    0xFF342713,
                    0xFFFDE68A,
                    0xFF64748B,
                    0xFF64748B);
        }
        return new Palette(
                false,
                0xFF0F2744,
                0xFF2563EB,
                0xFF1D4ED8,
                0xFFF5F7FA,
                0xFFFFFFFF,
                0xFFF8FAFC,
                0xFFE2E8F0,
                0xFF0F172A,
                0xFF64748B,
                0xFF16A34A,
                0xFFF59E0B,
                0xFFDC2626,
                0xFFFEF2F2,
                0xFFF1F5F9,
                0xFFDBEAFE,
                0xFF1E40AF,
                0xFFFEF3C7,
                0xFF92400E,
                0xFF94A3B8,
                0xFF94A3B8);
    }

    static void applyActivityTheme(Activity activity) {
        activity.setTheme(isDark(activity) ? R.style.AppThemeDark : R.style.AppTheme);
    }

    @SuppressWarnings("deprecation")
    static void applySystemBars(Activity activity, boolean dark) {
        Palette palette = palette(dark);
        activity.getWindow().setStatusBarColor(palette.appBar);
        activity.getWindow().setNavigationBarColor(palette.background);

        if (Build.VERSION.SDK_INT >= 23) {
            View decor = activity.getWindow().getDecorView();
            int flags = decor.getSystemUiVisibility();
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) {
                if (dark) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                else flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            decor.setSystemUiVisibility(flags);
        }
    }
}
