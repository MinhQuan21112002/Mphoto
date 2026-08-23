package com.sdk.esc;

import android.content.Context;

import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * folderId gallery Mono: {@code yyyyMMddHHmmss} (14) + mã máy (6) + random (10).
 * Ví dụ: {@code 202608151543293VGMHWab12cd34ef}
 */
public final class MonoGalleryFolderIds {
    private static final String RANDOM_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";

    private MonoGalleryFolderIds() {}

    /** Tạo folderId mới — luôn cố gắng có mã máy 6 ký tự. */
    public static String generate(Context context) {
        String datePart = new java.text.SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
                .format(new java.util.Date());
        String machinePart = resolveMachinePart(context);
        Random r = new Random();
        StringBuilder randomPart = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            randomPart.append(RANDOM_CHARS.charAt(r.nextInt(RANDOM_CHARS.length())));
        }
        return datePart + machinePart + randomPart;
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
     */
    public static boolean matchesServerGalleryId(String localFolderId, String serverFolderId) {
        if (localFolderId == null || serverFolderId == null) {
            return false;
        }
        if (localFolderId.equals(serverFolderId)) {
            return true;
        }
        if (localFolderId.length() == 24 && serverFolderId.length() == 30) {
            String date = localFolderId.substring(0, 14);
            String random = localFolderId.substring(14);
            return serverFolderId.startsWith(date) && serverFolderId.endsWith(random);
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

    /** folderId server tương ứng (ưu tiên bản đủ 30 ký tự có mã máy). */
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
     * Bản local 24 ký tự (thiếu mã máy) → 30 ký tự để upload/hiển thị giống server.
     */
    public static String upgradeLocalFolderId(String localFolderId, Context context) {
        if (localFolderId == null || localFolderId.isEmpty()) {
            return localFolderId;
        }
        if (localFolderId.length() == 30) {
            return localFolderId;
        }
        if (localFolderId.length() == 24) {
            String date = localFolderId.substring(0, 14);
            String random = localFolderId.substring(14);
            String machine = context != null ? resolveMachinePart(context) : "";
            if (!machine.isEmpty()) {
                return date + machine + random;
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
