package com.sdk.esc;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/**
 * Chế độ test: bỏ qua kiểm tra máy in; ở Manual, bấm In chỉ lưu thư viện + upload Drive (tên file theo thời gian + random).
 */
public final class PrinterTestMode {
    public static final String PREF = "settings";
    public static final String KEY = "printer_test_mode";
    private static final Random RND = new Random();

    public static boolean isEnabled(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean(KEY, false);
    }

    public static void setEnabled(Context c, boolean on) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY, on)
            .apply();
    }

    /**
     * Định dạng: {@code yyyyMMdd_HHmmss_1234567890.jpg} (10 số random).
     */
    public static String newTestFileNameJpeg() {
        String t = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return t + "_" + tenRandomDigits() + ".jpg";
    }

    public static String tenRandomDigits() {
        StringBuilder s = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            s.append(RND.nextInt(10));
        }
        return s.toString();
    }

    public static File writeJpegToCacheDir(Context c, Bitmap bitmap, String fileName) throws IOException {
        File dir = new File(c.getCacheDir(), "mphoto_test_upload");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("mkdirs cache");
        }
        File f = new File(dir, fileName);
        try (FileOutputStream out = new FileOutputStream(f)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out);
        }
        return f;
    }
}
