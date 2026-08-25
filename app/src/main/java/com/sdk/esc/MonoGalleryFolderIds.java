package com.sdk.esc;

import android.content.Context;

import java.util.Locale;
import java.util.Set;

/**
 * folderId gallery Mono: {@code yyyyMMddHHmmss} + mã máy (giống Lite).
 * Ví dụ: {@code 20260825123045RF8ZY4}
 * <p>Bản cũ có thể còn suffix random 10 ký tự — vẫn khớp khi sync.
 */
public final class MonoGalleryFolderIds {

    private MonoGalleryFolderIds() {}

    /** Tạo folderId mới: chỉ ngày giờ + mã máy (không random). */
    public static String generate(Context context) {
        String datePart = new java.text.SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
                .format(new java.util.Date());
        String machinePart = resolveMachinePart(context);
        if (machinePart.isEmpty()) {
            machinePart = "000000";
        }
        return datePart + machinePart;
    }

    public static String localPhotoFileName(String folderId) {
        return folderId + "_1.jpg";
    }

    private static String resolveMachinePart(Context context) {
        try {
            MachineManager mm = MachineManager.getInstance(context);
            String mc = mm.ensureLocalMachineCode();
            if (mc != null && !mc.isEmpty()) {
                return mc.trim().toUpperCase(Locale.US);
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    /**
     * Local cũ (14+10, không mã máy) khớp server (14+6+10) nếu cùng timestamp + random suffix.
     * Format mới (14+máy) khớp exact hoặc cùng prefix ngày+máy với bản cũ có random.
     */
    public static boolean matchesServerGalleryId(String localFolderId, String serverFolderId) {
        if (localFolderId == null || serverFolderId == null) {
            return false;
        }
        if (localFolderId.equals(serverFolderId)) {
            return true;
        }
        // Local 24 (date+random) ↔ server 30 (date+machine+random)
        if (localFolderId.length() == 24 && serverFolderId.length() == 30) {
            String date = localFolderId.substring(0, 14);
            String random = localFolderId.substring(14);
            return serverFolderId.startsWith(date) && serverFolderId.endsWith(random);
        }
        // Local mới 20 (date+machine) ↔ server cũ 30 (date+machine+random)
        if (localFolderId.length() >= 20 && serverFolderId.length() == 30
                && serverFolderId.startsWith(localFolderId.substring(0, Math.min(20, localFolderId.length())))) {
            String localHead = localFolderId.length() >= 20 ? localFolderId.substring(0, 20) : localFolderId;
            return serverFolderId.startsWith(localHead);
        }
        return false;
    }

    public static boolean isSyncedOnServer(String localFolderId, Set<String> serverIds) {
        if (localFolderId == null || localFolderId.isEmpty() || serverIds == null || serverIds.isEmpty()) {
            return false;
        }
        if (serverIds.contains(localFolderId)) {
            return true;
        }
        for (String sid : serverIds) {
            if (matchesServerGalleryId(localFolderId, sid)) {
                return true;
            }
        }
        return false;
    }

    /** folderId server tương ứng (ưu tiên id đã có trên server). */
    public static String resolveCanonicalServerId(String localFolderId, Set<String> serverIds, Context context) {
        if (localFolderId == null || serverIds == null) {
            return upgradeLocalFolderId(localFolderId, context);
        }
        if (serverIds.contains(localFolderId)) {
            return localFolderId;
        }
        for (String sid : serverIds) {
            if (matchesServerGalleryId(localFolderId, sid)) {
                return sid;
            }
        }
        return upgradeLocalFolderId(localFolderId, context);
    }

    /** @deprecated dùng {@link #resolveCanonicalServerId(String, Set, Context)} */
    public static String resolveCanonicalServerId(String localFolderId, Set<String> serverIds) {
        return resolveCanonicalServerId(localFolderId, serverIds, null);
    }

    /**
     * Bản local cũ thiếu mã máy → thêm mã máy (không thêm random).
     * Bản đã đủ date+máy giữ nguyên.
     */
    public static String upgradeLocalFolderId(String localFolderId, Context context) {
        if (localFolderId == null || localFolderId.isEmpty()) {
            return localFolderId;
        }
        // Format mới hoặc đã có máy: 14+máy (+ optional random cũ)
        if (localFolderId.length() >= 20) {
            return localFolderId;
        }
        // 24 = date(14)+random(10) không mã máy → date+máy
        if (localFolderId.length() == 24) {
            String date = localFolderId.substring(0, 14);
            String machine = context != null ? resolveMachinePart(context) : "";
            if (!machine.isEmpty()) {
                return date + machine;
            }
        }
        return localFolderId;
    }

    /** Tên file local thực tế trên máy (có thể khác folderId hiển thị). */
    public static String localFileNameForItem(String localFolderId, String displayFolderId) {
        String id = localFolderId != null && !localFolderId.isEmpty() ? localFolderId : displayFolderId;
        return localPhotoFileName(id);
    }
}
