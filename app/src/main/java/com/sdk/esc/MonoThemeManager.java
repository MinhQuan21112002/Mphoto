package com.sdk.esc;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;

/** Persists the Mono app's light/dark appearance choice. */
public final class MonoThemeManager {
    private static final String PREFS = "settings";
    private static final String KEY_DARK = "dark_mode";

    private MonoThemeManager() { }

    public static boolean isDark(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_DARK, false);
    }

    public static void applySavedTheme(Context context) {
        AppCompatDelegate.setDefaultNightMode(isDark(context)
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);
    }

    public static void setDark(Context context, boolean dark) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_DARK, dark).apply();
        AppCompatDelegate.setDefaultNightMode(dark
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);
    }
}
