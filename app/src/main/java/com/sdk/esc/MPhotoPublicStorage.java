package com.sdk.esc;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import java.io.File;

/**
 * Bộ nhớ công khai dưới {@code Pictures/M-Photo_Pro/} (cùng cây thư mục với ảnh in / mirror) —
 * dữ liệu dưới đây thường <strong>không</strong> bị xóa khi gỡ app, khác với
 * {@code Context#getFilesDir()}.
 * Khung/ảnh phụ đồng bộ từ server: {@code app_user_assets/frames|subs} (xem {@link UserAssetFileStore}).
 * Bản rất cũ từng dùng {@code M-Photo_Pro/mono_cache/} nội bộ — có bước migrate trong {@link MonoCacheSync}.
 */
public final class MPhotoPublicStorage {
    private static final String TAG = "MPhotoPublicStorage";
    public static final String PUBLIC_FOLDER = "M-Photo_Pro";
    public static final String USER_DATA_FILE = "mphoto_user_data.json";

    public static boolean isExternalAvailable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())
            || Environment.MEDIA_MOUNTED_READ_ONLY.equals(Environment.getExternalStorageState());
    }

    public static File getAppPublicRoot() {
        File base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File root = new File(base, PUBLIC_FOLDER);
        if (isExternalAvailable() && (base == null || !base.exists())) {
            Log.w(TAG, "pictures base missing, still try: " + root);
        }
        if (!root.exists() && !root.mkdirs()) {
            Log.w(TAG, "mkdirs failed: " + root.getAbsolutePath());
        }
        return root;
    }

    public static File getUserDataBackupFile() {
        return new File(getAppPublicRoot(), USER_DATA_FILE);
    }

    /**
     * Một số bản cũ lưu cache tải ở {@code M-Photo_Pro/mono_cache/} — nếu bộ nhớ app trống mà
     * còn dữ liệu công khai, copy về nội bộ (tránh tải lại).
     */
    public static void migratePublicMonoSubdirToFilesDirIfEmpty(Context context, String subPath) {
        File internal = new File(context.getFilesDir(), subPath);
        if (internal.isDirectory() && internal.list() != null && internal.list().length > 0) {
            return;
        }
        File pub = new File(getAppPublicRoot(), subPath);
        if (!pub.isDirectory() || pub.list() == null || pub.list().length == 0) {
            return;
        }
        if (!internal.exists() && !internal.mkdirs()) {
            Log.w(TAG, "mkdirs internal: " + internal);
        }
        copyDirRecursive(pub, internal);
    }

    private static void copyDirRecursive(File from, File to) {
        if (from == null || !from.isDirectory()) {
            return;
        }
        if (to == null) {
            return;
        }
        if (!to.exists() && !to.mkdirs()) {
            Log.w(TAG, "copy mkdirs: " + to);
            return;
        }
        String[] list = from.list();
        if (list == null) {
            return;
        }
        for (String name : list) {
            File a = new File(from, name);
            File b = new File(to, name);
            if (a.isDirectory()) {
                copyDirRecursive(a, b);
            } else {
                if (b.exists() && b.length() > 0) {
                    continue;
                }
                if (a.length() == 0) {
                    continue;
                }
                try (java.io.FileInputStream in = new java.io.FileInputStream(a);
                     java.io.FileOutputStream out = new java.io.FileOutputStream(b)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        out.write(buf, 0, n);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "copy " + a + " -> " + b, e);
                }
            }
        }
    }
}
