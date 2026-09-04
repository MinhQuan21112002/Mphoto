package com.sdk.esc;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thống kê lượt in Mono.
 * Ưu tiên SharedPreferences (luôn ghi được) + file ngoài app (bền khi gỡ app) + sidecar gallery.
 */
public final class GalleryPrintStatsStore {
    private static final String TAG = "GalleryPrintStats";
    private static final String DURABLE_FILE = "gallery-print-stats-mono.json";
    private static final String PRODUCT = "mono";
    private static final String PREFS = "GalleryPrintStatsMono";
    private static final String KEY_JSON = "stats_json";

    /** Cache cùng phiên — UI đọc ngay sau khi in. */
    private static final ConcurrentHashMap<String, Integer> MEMORY_TOTALS = new ConcurrentHashMap<>();
    private static volatile int memoryPendingPrints = -1;

    private GalleryPrintStatsStore() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static List<File> durableCandidates() {
        List<File> files = new ArrayList<>();
        try {
            File root = Environment.getExternalStorageDirectory();
            if (root != null) {
                files.add(new File(new File(root, "MPhotoConfig"), DURABLE_FILE));
            }
            File documents = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            if (documents != null) {
                files.add(new File(new File(documents, "M-Photo"), DURABLE_FILE));
            }
            File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (downloads != null) {
                files.add(new File(new File(downloads, "M-Photo"), DURABLE_FILE));
            }
            File pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            if (pictures != null) {
                files.add(new File(new File(pictures, "M-Photo"), DURABLE_FILE));
            }
        } catch (Exception e) {
            Log.e(TAG, "durable paths", e);
        }
        return files;
    }

    private static String todayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private static JSONObject emptyData() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("product", PRODUCT);
            obj.put("totals", new JSONObject());
            obj.put("pendingByDay", new JSONObject());
        } catch (Exception ignored) {}
        return obj;
    }

    private static JSONObject loadDurable(Context context) {
        // 1) SharedPreferences (đáng tin nhất)
        if (context != null) {
            try {
                String raw = prefs(context).getString(KEY_JSON, null);
                if (raw != null && !raw.isEmpty()) {
                    return new JSONObject(raw);
                }
            } catch (Exception e) {
                Log.w(TAG, "prefs read: " + e.getMessage());
            }
        }
        // 2) File ngoài app
        for (File file : durableCandidates()) {
            if (file == null || !file.exists()) continue;
            try {
                FileInputStream fis = new FileInputStream(file);
                byte[] buf = new byte[(int) file.length()];
                int read = fis.read(buf);
                fis.close();
                if (read > 0) {
                    JSONObject obj = new JSONObject(new String(buf, 0, read, StandardCharsets.UTF_8));
                    if (context != null) {
                        try {
                            prefs(context).edit().putString(KEY_JSON, obj.toString()).apply();
                        } catch (Exception ignored) {}
                    }
                    return obj;
                }
            } catch (Exception e) {
                Log.w(TAG, "read " + file + ": " + e.getMessage());
            }
        }
        return emptyData();
    }

    private static void saveDurable(Context context, JSONObject obj) {
        try {
            obj.put("product", PRODUCT);
            obj.put("lastUpdated", System.currentTimeMillis());
            String json = obj.toString();
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

            if (context != null) {
                try {
                    prefs(context).edit().putString(KEY_JSON, json).apply();
                } catch (Exception e) {
                    Log.e(TAG, "prefs write failed", e);
                }
            }

            for (File file : durableCandidates()) {
                if (file == null) continue;
                try {
                    File parent = file.getParentFile();
                    if (parent != null && !parent.exists()) {
                        //noinspection ResultOfMethodCallIgnored
                        parent.mkdirs();
                    }
                    FileOutputStream fos = new FileOutputStream(file);
                    fos.write(bytes);
                    fos.flush();
                    fos.close();
                } catch (Exception e) {
                    Log.w(TAG, "write " + file + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "saveDurable", e);
        }
    }

    private static File localSidecar(Context context, String galleryId) {
        if (context == null || galleryId == null || galleryId.trim().isEmpty()) return null;
        try {
            File pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            File dir = new File(pictures, MonoFolderImages.getFolderName(context));
            if (!dir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
            }
            return new File(dir, galleryId.trim() + "_print_stats.json");
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeSidecar(File file, int printCount) {
        if (file == null) return;
        try {
            JSONObject obj = new JSONObject();
            obj.put("printCount", printCount);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(obj.toString().getBytes(StandardCharsets.UTF_8));
            fos.flush();
            fos.close();
        } catch (Exception e) {
            Log.w(TAG, "sidecar: " + e.getMessage());
        }
    }

    private static void refreshMemoryPending(JSONObject data) {
        try {
            JSONObject pendingByDay = data.optJSONObject("pendingByDay");
            int total = 0;
            if (pendingByDay != null) {
                Iterator<String> days = pendingByDay.keys();
                while (days.hasNext()) {
                    JSONObject dayMap = pendingByDay.optJSONObject(days.next());
                    if (dayMap == null) continue;
                    Iterator<String> ids = dayMap.keys();
                    while (ids.hasNext()) {
                        total += Math.max(0, dayMap.optInt(ids.next(), 0));
                    }
                }
            }
            memoryPendingPrints = total;
        } catch (Exception e) {
            memoryPendingPrints = 0;
        }
    }

    public static synchronized void record(Context context, String galleryId, int count) {
        if (galleryId == null || galleryId.trim().isEmpty() || count <= 0) return;
        galleryId = galleryId.trim();
        Context app = context != null ? context.getApplicationContext() : null;

        JSONObject data = loadDurable(app);
        try {
            JSONObject totals = data.optJSONObject("totals");
            if (totals == null) {
                totals = new JSONObject();
                data.put("totals", totals);
            }
            int newTotal = totals.optInt(galleryId, 0) + count;
            totals.put(galleryId, newTotal);
            MEMORY_TOTALS.put(galleryId, newTotal);

            JSONObject pendingByDay = data.optJSONObject("pendingByDay");
            if (pendingByDay == null) {
                pendingByDay = new JSONObject();
                data.put("pendingByDay", pendingByDay);
            }
            String day = todayKey();
            JSONObject dayMap = pendingByDay.optJSONObject(day);
            if (dayMap == null) {
                dayMap = new JSONObject();
                pendingByDay.put(day, dayMap);
            }
            dayMap.put(galleryId, dayMap.optInt(galleryId, 0) + count);
            saveDurable(app, data);
            refreshMemoryPending(data);

            writeSidecar(localSidecar(app, galleryId), newTotal);
            Log.d(TAG, "+" + count + " → " + galleryId + " (total=" + newTotal + ")");
        } catch (Exception e) {
            Log.e(TAG, "record", e);
        }
    }

    public static int getDisplayCount(Context context, String galleryId) {
        if (galleryId == null || galleryId.trim().isEmpty()) return 0;
        galleryId = galleryId.trim();

        Integer mem = MEMORY_TOTALS.get(galleryId);
        if (mem != null && mem > 0) return mem;

        File sidecar = localSidecar(context, galleryId);
        if (sidecar != null && sidecar.exists()) {
            try {
                FileInputStream fis = new FileInputStream(sidecar);
                byte[] buf = new byte[(int) sidecar.length()];
                int read = fis.read(buf);
                fis.close();
                if (read > 0) {
                    int n = new JSONObject(new String(buf, 0, read, StandardCharsets.UTF_8)).optInt("printCount", 0);
                    if (n > 0) {
                        MEMORY_TOTALS.put(galleryId, n);
                        return n;
                    }
                }
            } catch (Exception ignored) {}
        }
        try {
            JSONObject totals = loadDurable(context).optJSONObject("totals");
            if (totals != null) {
                int n = Math.max(0, totals.optInt(galleryId, 0));
                if (n > 0) MEMORY_TOTALS.put(galleryId, n);
                return n;
            }
        } catch (Exception ignored) {}
        return 0;
    }

    public static void trySyncPending(Context context, String token) {
        syncPending(context, token, false);
    }

    public static final class PendingSummary {
        public final int galleryCount;
        public final int printCount;

        public PendingSummary(int galleryCount, int printCount) {
            this.galleryCount = galleryCount;
            this.printCount = printCount;
        }
    }

    public static final class SyncResult {
        public final boolean ok;
        public final int galleryCount;
        public final int printCount;
        public final String message;

        public SyncResult(boolean ok, int galleryCount, int printCount, String message) {
            this.ok = ok;
            this.galleryCount = galleryCount;
            this.printCount = printCount;
            this.message = message;
        }
    }

    public static PendingSummary getPendingSummary(boolean includeToday) {
        return getPendingSummary(null, includeToday);
    }

    public static PendingSummary getPendingSummary(Context context, boolean includeToday) {
        JSONObject data = loadDurable(context);
        JSONObject pendingByDay = data.optJSONObject("pendingByDay");
        if (pendingByDay == null || pendingByDay.length() == 0) {
            return new PendingSummary(0, 0);
        }
        String today = todayKey();
        JSONObject merged = new JSONObject();
        try {
            Iterator<String> days = pendingByDay.keys();
            while (days.hasNext()) {
                String day = days.next();
                if (!includeToday && day.compareTo(today) >= 0) continue;
                JSONObject dayMap = pendingByDay.optJSONObject(day);
                if (dayMap == null) continue;
                Iterator<String> ids = dayMap.keys();
                while (ids.hasNext()) {
                    String id = ids.next();
                    int c = dayMap.optInt(id, 0);
                    if (c <= 0) continue;
                    merged.put(id, merged.optInt(id, 0) + c);
                }
            }
            int prints = 0;
            Iterator<String> ids = merged.keys();
            while (ids.hasNext()) {
                prints += merged.optInt(ids.next(), 0);
            }
            return new PendingSummary(merged.length(), prints);
        } catch (Exception e) {
            return new PendingSummary(0, 0);
        }
    }

    public static SyncResult syncPending(Context context, String token, boolean includeToday) {
        if (context == null || token == null || token.isEmpty()) {
            return new SyncResult(false, 0, 0, "Chưa đăng nhập");
        }
        Context app = context.getApplicationContext();

        JSONObject data = loadDurable(app);
        JSONObject pendingByDay = data.optJSONObject("pendingByDay");
        if (pendingByDay == null || pendingByDay.length() == 0) {
            return new SyncResult(true, 0, 0, "Không có lượt in chờ đồng bộ");
        }

        String today = todayKey();
        JSONObject merged = new JSONObject();
        List<String> daysToClear = new ArrayList<>();
        try {
            Iterator<String> days = pendingByDay.keys();
            while (days.hasNext()) {
                String day = days.next();
                if (!includeToday && day.compareTo(today) >= 0) continue;
                daysToClear.add(day);
                JSONObject dayMap = pendingByDay.optJSONObject(day);
                if (dayMap == null) continue;
                Iterator<String> ids = dayMap.keys();
                while (ids.hasNext()) {
                    String id = ids.next();
                    int c = dayMap.optInt(id, 0);
                    if (c <= 0) continue;
                    merged.put(id, merged.optInt(id, 0) + c);
                }
            }
            if (merged.length() == 0) {
                return new SyncResult(true, 0, 0, "Không có lượt in chờ đồng bộ");
            }

            int printTotal = 0;
            JSONArray items = new JSONArray();
            Iterator<String> ids = merged.keys();
            while (ids.hasNext()) {
                String id = ids.next();
                int c = merged.getInt(id);
                printTotal += c;
                JSONObject row = new JSONObject();
                row.put("folderId", id);
                row.put("count", c);
                items.put(row);
            }
            JSONObject body = new JSONObject();
            body.put("items", items);

            ApiService.syncGalleryPrintStats("/mono-results/stats/prints/sync", token, body);

            for (String day : daysToClear) {
                pendingByDay.remove(day);
            }
            data.put("lastSyncedAt", System.currentTimeMillis());
            saveDurable(app, data);
            refreshMemoryPending(data);
            Log.d(TAG, "synced " + items.length() + " galleries");
            return new SyncResult(true, items.length(), printTotal, "Đồng bộ thành công");
        } catch (Exception e) {
            Log.e(TAG, "syncPending", e);
            return new SyncResult(false, merged.length(), 0, e.getMessage() != null ? e.getMessage() : "Lỗi đồng bộ");
        }
    }
}
