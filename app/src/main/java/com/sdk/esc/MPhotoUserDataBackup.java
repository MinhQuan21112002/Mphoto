package com.sdk.esc;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Sao lưu {@code FrameImage}, {@code MyAppPrefs2}, {@code GoogleDrive} ra
 * bộ nhớ công khai; sau khi cài lại app, khôi phục nếu SharedPreferences còn trống
 * mà vẫn còn file.
 */
public final class MPhotoUserDataBackup {
    private static final String TAG = "MPhotoUserDataBackup";
    private static final int VERSION = 1;
    private static final String PREF_FRAME = "FrameImage";
    private static final String PREF_SUBS = "MyAppPrefs2";
    private static final String PREF_DRIVE = "GoogleDrive";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Object LOCK = new Object();
    /** Khi ghi {@code Pictures/M-…} bị từ chối, ghi tạm ở nội bộ (không sống sau gỡ app). */
    private static File getInternalBackupFile(Context c) {
        return new File(c.getFilesDir(), "mphoto_user_data.json");
    }

    public static void scheduleSave(Context appContext) {
        Context c = appContext.getApplicationContext();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                saveToPublicFile(c);
            } catch (Exception e) {
                Log.w(TAG, "save", e);
            }
        });
    }

    public static void restoreIfPrefsEmpty(Context appContext) {
        Context c = appContext.getApplicationContext();
        try {
            restoreFromPublicFileIfEmpty(c);
        } catch (Exception e) {
            Log.w(TAG, "restore", e);
        }
    }

    public static void saveToPublicFile(Context c) {
        synchronized (LOCK) {
            JSONObject root = new JSONObject();
            try {
                root.put("version", VERSION);
                root.put("frame", new JSONObject(
                    mapToJsonString(c.getSharedPreferences(PREF_FRAME, Context.MODE_PRIVATE).getAll())
                ));
                root.put("subs", new JSONObject(
                    mapToJsonString(c.getSharedPreferences(PREF_SUBS, Context.MODE_PRIVATE).getAll())
                ));
                root.put("drive", new JSONObject(
                    mapToJsonString(c.getSharedPreferences(PREF_DRIVE, Context.MODE_PRIVATE).getAll())
                ));
            } catch (Exception e) {
                Log.w(TAG, "build json", e);
                return;
            }
            String payload = root.toString();
            boolean pubOk = false;
            if (MPhotoPublicStorage.isExternalAvailable()) {
                File out = MPhotoPublicStorage.getUserDataBackupFile();
                File parent = out.getParentFile();
                if (parent == null || parent.exists() || parent.mkdirs()) {
                    try (FileOutputStream fos = new FileOutputStream(out);
                         OutputStreamWriter w = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                        w.write(payload);
                        w.flush();
                        pubOk = true;
                    } catch (Exception e) {
                        Log.w(TAG, "write public " + out, e);
                    }
                }
            }
            if (pubOk) {
                return;
            }
            try (FileOutputStream fos = new FileOutputStream(getInternalBackupFile(c));
                 OutputStreamWriter w = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                w.write(payload);
                w.flush();
            } catch (Exception e) {
                Log.w(TAG, "write internal backup", e);
            }
        }
    }

    public static void restoreFromPublicFileIfEmpty(Context c) {
        File f = findReadableBackupFile(c);
        if (f == null) {
            return;
        }
        SharedPreferences sFrame = c.getSharedPreferences(PREF_FRAME, Context.MODE_PRIVATE);
        SharedPreferences sSubs = c.getSharedPreferences(PREF_SUBS, Context.MODE_PRIVATE);
        SharedPreferences sDrive = c.getSharedPreferences(PREF_DRIVE, Context.MODE_PRIVATE);
        if (!looksLikeFreshData(sFrame, sSubs, sDrive)) {
            return;
        }
        String json;
        try (FileInputStream in = new FileInputStream(f);
             InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            char[] buf = new char[8192];
            StringBuilder sb = new StringBuilder();
            int n;
            while ((n = r.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
            json = sb.toString();
        } catch (Exception e) {
            Log.w(TAG, "read backup", e);
            return;
        }
        if (json.isEmpty()) {
            return;
        }
        try {
            JSONObject root = new JSONObject(json);
            applyAll(sFrame, root.optJSONObject("frame"));
            applyAll(sSubs, root.optJSONObject("subs"));
            applyAll(sDrive, root.optJSONObject("drive"));
            Log.d(TAG, "restored user prefs from " + f.getAbsolutePath());
        } catch (Exception e) {
            Log.w(TAG, "apply backup", e);
        }
    }

    private static File findReadableBackupFile(Context c) {
        File a = MPhotoPublicStorage.getUserDataBackupFile();
        if (a.isFile() && a.length() > 0) {
            return a;
        }
        File b = getInternalBackupFile(c);
        if (b.isFile() && b.length() > 0) {
            return b;
        }
        return null;
    }

    /**
     * Chỉ khôi phục khi chưa có dữ liệu thực (cài lại / xóa dữ liệu app) — còn file trên ổ.
     */
    private static boolean looksLikeFreshData(SharedPreferences frame, SharedPreferences subs, SharedPreferences drive) {
        if (!jsonListEmpty(frame, "bitmap_list")) {
            return false;
        }
        if (!jsonListEmpty(subs, "ImageViewList")) {
            return false;
        }
        return drive.getAll().isEmpty();
    }

    private static boolean jsonListEmpty(SharedPreferences p, String key) {
        String s = p.getString(key, "[]");
        return s == null || s.trim().isEmpty() || "[]".equals(s.trim());
    }

    private static void applyAll(SharedPreferences p, JSONObject o) {
        if (o == null || p == null) {
            return;
        }
        SharedPreferences.Editor ed = p.edit();
        try {
            JSONArray names = o.names();
            if (names == null) {
                return;
            }
            for (int i = 0; i < names.length(); i++) {
                String key = names.getString(i);
                Object v = o.get(key);
                putValue(ed, key, v);
            }
        } catch (Exception e) {
            Log.w(TAG, "apply", e);
        }
        ed.apply();
    }

    private static void putValue(SharedPreferences.Editor ed, String k, Object v) {
        if (v == null) {
            return;
        }
        if (v instanceof String) {
            ed.putString(k, (String) v);
        } else if (v instanceof Boolean) {
            ed.putBoolean(k, (Boolean) v);
        } else if (v instanceof Integer) {
            ed.putInt(k, (Integer) v);
        } else if (v instanceof Long) {
            ed.putLong(k, (Long) v);
        } else if (v instanceof Float) {
            ed.putFloat(k, (Float) v);
        } else if (v instanceof Double) {
            double d = (Double) v;
            if (d == Math.rint(d) && d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE) {
                ed.putInt(k, (int) d);
            } else {
                ed.putString(k, String.valueOf(d));
            }
        } else if (v instanceof Number) {
            double d = ((Number) v).doubleValue();
            if (d == Math.rint(d) && d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE) {
                ed.putInt(k, (int) d);
            } else {
                ed.putString(k, String.valueOf(v));
            }
        } else if (v instanceof org.json.JSONArray) {
            ed.putString(k, v.toString());
        } else if (v instanceof org.json.JSONObject) {
            ed.putString(k, v.toString());
        } else {
            ed.putString(k, String.valueOf(v));
        }
    }

    private static String mapToJsonString(Map<String, ?> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        return GSON.toJson(map);
    }
}
