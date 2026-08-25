package com.sdk.esc;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Tự xóa gallery Mono quá hạn (mặc định 7 ngày): local + server.
 * <p>Luồng an toàn:
 * <ul>
 *   <li>Còn trên server → xóa server trước; chỉ xóa local khi server đã xóa OK.</li>
 *   <li>Lần mở app sau: nếu local còn mà server đã không còn → xóa local luôn.</li>
 *   <li>Server xóa lỗi → giữ local, lần sau thử lại.</li>
 * </ul>
 * Server: gom {@code folderId} quá hạn — gọi {@code POST /mono-results/delete-galleries} theo batch.
 */
public final class MonoGalleryCleanup {
    private static final String TAG = "MonoGalleryCleanup";
    /** Giữ gallery tối đa 1 tuần trên máy Mono. */
    public static final int RETENTION_DAYS = 7;
    /** Tối đa ID mỗi request batch (tránh body/timeout khi Mono có rất nhiều gallery). */
    private static final int DELETE_BATCH_SIZE = 200;

    private static volatile boolean running;

    private MonoGalleryCleanup() {}

    public static void runInBackground(Context context) {
        if (running) {
            return;
        }
        Context app = context.getApplicationContext();
        new Thread(() -> {
            running = true;
            try {
                runCleanup(app);
            } catch (Exception e) {
                Log.w(TAG, "runCleanup failed", e);
            } finally {
                running = false;
            }
        }, "mono-gallery-cleanup").start();
    }

    private static void runCleanup(Context context) {
        long cutoffMs = System.currentTimeMillis() - RETENTION_DAYS * 24L * 60 * 60 * 1000L;
        TokenManager tm = TokenManager.getInstance(context);
        String token = tm.getToken();
        boolean canDeleteServer = tm.canUseCloudFeatures() && token != null && !token.isEmpty();

        Set<String> serverIds = new HashSet<>();
        if (canDeleteServer) {
            try {
                JSONArray arr = ApiService.getMonoAllGalleryIds(token);
                for (int i = 0; i < arr.length(); i++) {
                    String id = arr.optString(i, "").trim();
                    if (!id.isEmpty()) {
                        serverIds.add(id);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Không tải được danh sách gallery server, chỉ xóa local chưa sync", e);
                canDeleteServer = false;
            }
        }

        List<MonoFolderImages.LocalGalleryItem> localItems =
            MonoFolderImages.loadAllLocalGalleryItems(context);
        Map<String, MonoFolderImages.LocalGalleryItem> localByFolderId = new HashMap<>();
        Set<String> serverIdsToDelete = new HashSet<>();

        for (MonoFolderImages.LocalGalleryItem item : localItems) {
            if (item == null || item.folderId == null || item.folderId.isEmpty()) {
                continue;
            }
            localByFolderId.put(item.folderId, item);
            if (!isExpired(item.folderId, item.time, cutoffMs)) {
                continue;
            }
            if (MonoGalleryFolderIds.isSyncedOnServer(item.folderId, serverIds)) {
                serverIdsToDelete.add(
                    MonoGalleryFolderIds.resolveCanonicalServerId(item.folderId, serverIds));
            }
        }

        if (canDeleteServer) {
            for (String folderId : serverIds) {
                boolean hasLocal = localByFolderId.containsKey(folderId);
                if (!hasLocal) {
                    for (MonoFolderImages.LocalGalleryItem local : localByFolderId.values()) {
                        if (MonoGalleryFolderIds.matchesServerGalleryId(local.folderId, folderId)) {
                            hasLocal = true;
                            break;
                        }
                    }
                }
                if (hasLocal) {
                    continue;
                }
                if (isExpired(folderId, 0L, cutoffMs)) {
                    serverIdsToDelete.add(folderId);
                }
            }
        }

        Set<String> serverDeleted = new HashSet<>();
        if (canDeleteServer && !serverIdsToDelete.isEmpty()) {
            List<String> idList = new ArrayList<>(serverIdsToDelete);
            for (int i = 0; i < idList.size(); i += DELETE_BATCH_SIZE) {
                int end = Math.min(i + DELETE_BATCH_SIZE, idList.size());
                List<String> chunk = idList.subList(i, end);
                try {
                    ApiService.MonoDeleteGalleriesResult batch =
                        ApiService.deleteMonoGalleries(token, chunk);
                    if (batch != null && batch.deleted != null) {
                        serverDeleted.addAll(batch.deleted);
                    }
                    if (batch != null && batch.failedCount > 0) {
                        Log.w(TAG, "Batch xóa server có failed: " + batch.failedCount
                            + " (batch " + (i / DELETE_BATCH_SIZE + 1) + ")");
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Batch xóa server thất bại, batch bắt đầu index " + i, e);
                }
            }
        }

        int localDeleted = 0;
        int localSkipped = 0;
        for (MonoFolderImages.LocalGalleryItem item : localItems) {
            if (item == null || item.folderId == null || item.folderId.isEmpty()) {
                continue;
            }
            if (!isExpired(item.folderId, item.time, cutoffMs)) {
                continue;
            }
            // Còn trên server + đang sync được → chỉ xóa local sau khi server đã xóa OK.
            // Không còn trên server (hoặc chưa sync) → xóa local luôn (kể cả lần mở app sau).
            boolean onServer = MonoGalleryFolderIds.isSyncedOnServer(item.folderId, serverIds);
            String canonicalId = MonoGalleryFolderIds.resolveCanonicalServerId(item.folderId, serverIds);
            if (onServer && canDeleteServer) {
                if (!serverDeleted.contains(canonicalId) && !serverDeleted.contains(item.folderId)) {
                    localSkipped++;
                    continue;
                }
            }
            if (MonoFolderImages.deleteImage(context, item.previewUri)) {
                localDeleted++;
            } else {
                localSkipped++;
            }
        }

        if (localDeleted > 0 || serverDeleted.size() > 0 || localSkipped > 0) {
            Log.i(TAG, "Cleanup xong: localDeleted=" + localDeleted
                + " serverDeleted=" + serverDeleted.size()
                + " skipped=" + localSkipped);
        }
    }

    static boolean isExpired(String folderId, long fileTimeFallbackMs, long cutoffMs) {
        long created = resolveCreatedTimeMillis(folderId, fileTimeFallbackMs);
        return created > 0L && created < cutoffMs;
    }

    /**
     * {@code folderId} chuẩn: {@code yyyyMMddHHmmss} + mã máy.
     * Bản cũ: thêm 10 random; hoặc không mã máy.
     * Chỉ cần 14 ký tự đầu để suy ra thời gian tạo.
     */
    static long resolveCreatedTimeMillis(String folderId, long fileTimeFallbackMs) {
        if (folderId == null || folderId.length() < 14) {
            return fileTimeFallbackMs > 0L ? fileTimeFallbackMs : 0L;
        }
        String head = folderId.substring(0, 14);
        Long parsed = parseWithFormat(head, "yyyyMMddHHmmss");
        if (parsed == null) {
            parsed = parseWithFormat(head, "ddMMyyyyHHmmss");
        }
        if (parsed != null) {
            return parsed;
        }
        return fileTimeFallbackMs > 0L ? fileTimeFallbackMs : 0L;
    }

    private static Long parseWithFormat(String text, String pattern) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.US);
            sdf.setLenient(false);
            ParsePosition pos = new ParsePosition(0);
            Date d = sdf.parse(text, pos);
            if (d == null || pos.getIndex() != text.length()) {
                return null;
            }
            return d.getTime();
        } catch (Exception e) {
            return null;
        }
    }
}
