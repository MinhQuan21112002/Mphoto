package com.sdk.esc;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.mphoto.mono.R;

/**
 * Chế độ chuyển ảnh xám → đen trắng khi in ({@code Print.PrintBitmap} tham số HalftoneMode).
 * ESC SDK hỗ trợ 3 chế độ: 0 Threshold, 1 Dither/Shake, 2 Bayer.
 */
public final class PrintBitmapMode {
    public static final String PREF = "settings";
    public static final String KEY = "bitmap_halftone_mode";

    /** Ngưỡng cố định — nét logo/text, ảnh dễ mất xám. */
    public static final int THRESHOLD = 0;
    /** Dither / Shake (Floyd–Steinberg) — mặc định, tốt cho ảnh. */
    public static final int DITHER = 1;
    /** Bayer ordered dither (HTBayerCluster). */
    public static final int BAYER = 2;

    private static final int[] MODES = { THRESHOLD, DITHER, BAYER };

    private PrintBitmapMode() {
    }

    public static int get(@NonNull Context c) {
        int mode = c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(KEY, DITHER);
        return isValid(mode) ? mode : DITHER;
    }

    public static void set(@NonNull Context c, int mode) {
        if (!isValid(mode)) {
            mode = DITHER;
        }
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY, mode)
                .apply();
    }

    public static boolean isValid(int mode) {
        return mode == THRESHOLD || mode == DITHER || mode == BAYER;
    }

    /** Thứ tự hiển thị trong Spinner — khớp {@link #MODES}. */
    @NonNull
    public static int[] modes() {
        return MODES.clone();
    }

    public static int indexOf(int mode) {
        for (int i = 0; i < MODES.length; i++) {
            if (MODES[i] == mode) {
                return i;
            }
        }
        return indexOf(DITHER);
    }

    public static int modeAt(int spinnerIndex) {
        if (spinnerIndex < 0 || spinnerIndex >= MODES.length) {
            return DITHER;
        }
        return MODES[spinnerIndex];
    }

    @NonNull
    public static String[] labels(@NonNull Context c) {
        return new String[]{
                c.getString(R.string.print_mode_threshold),
                c.getString(R.string.print_mode_dither),
                c.getString(R.string.print_mode_bayer)
        };
    }
}
