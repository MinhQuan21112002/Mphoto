package com.sdk.esc;

/**
 * Đánh dấu chuyển Main ↔ Manual để trì hoãn mở camera,
 * animation chạy trước, tránh cảm giác “đứng hình rồi mới chuyển”.
 */
public final class MonoScreenSwitch {

    private static volatile boolean pendingSoftSwitch;

    private MonoScreenSwitch() {
    }

    public static void mark() {
        pendingSoftSwitch = true;
    }

    public static boolean isPending() {
        return pendingSoftSwitch;
    }

    /** Gọi trong onResume của màn sắp hiện — trả về true nếu đây là soft switch. */
    public static boolean consumeSoftResume() {
        if (!pendingSoftSwitch) {
            return false;
        }
        pendingSoftSwitch = false;
        return true;
    }
}
