package com.sdk.esc;

import android.content.Context;
import android.util.Log;

import print.Print;

/**
 * Trạng thái máy in Mono (ESC SDK {@link Print}) — gửi lên Control Page.
 * Idle=xanh, Printing/Paper Near End=vàng, Paper End/lỗi=đỏ.
 *
 * ESC/POS DLE EOT n=4 (paper roll):
 *   bits 2–3 (0x0C) = sắp hết giấy
 *   bits 5–6 (0x60) = hết giấy
 */
public final class MonoPrinterStatusHelper {
    private static final String TAG = "MonoPrinterStatus";

    private MonoPrinterStatusHelper() {
    }

    public enum Tone {
        GOOD, WARN, BAD
    }

    public static final class Snapshot {
        public final boolean connected;
        public final String status;
        public final boolean statusGood;

        Snapshot(boolean connected, String status, boolean statusGood) {
            this.connected = connected;
            this.status = status;
            this.statusGood = statusGood;
        }
    }

    public static Snapshot refresh(Context ctx) {
        if (ctx != null && PrinterTestMode.isEnabled(ctx)) {
            return new Snapshot(true, "Idle", true);
        }
        if (!Print.IsOpened()) {
            return new Snapshot(false, "Disconnected", false);
        }
        String status = readEscStatus();
        Tone tone = classifyTone(status);
        boolean good = tone == Tone.GOOD || tone == Tone.WARN;
        return new Snapshot(true, status, good);
    }

    private static String readEscStatus() {
        try {
            byte[] paper = safeStatus((byte) 4);
            byte[] err = safeStatus((byte) 3);
            byte[] offline = safeStatus((byte) 2);
            byte[] printer = safeStatus((byte) 1);

            int p = (paper != null && paper.length > 0) ? (paper[0] & 0xFF) : 0;
            int o = (offline != null && offline.length > 0) ? (offline[0] & 0xFF) : 0;
            int e = (err != null && err.length > 0) ? (err[0] & 0xFF) : 0;
            int pr = (printer != null && printer.length > 0) ? (printer[0] & 0xFF) : 0;

            Log.d(TAG, String.format(
                    "ESC status paper=0x%02X offline=0x%02X err=0x%02X printer=0x%02X",
                    p, o, e, pr));

            // 1) Hết giấy — ưu tiên cao nhất khi user rút hết giấy
            // n=4 bits 5–6; n=2 bit5 = dừng in vì hết giấy
            if ((p & 0x60) != 0) return "Paper End";
            if ((o & 0x20) != 0) return "Paper End";

            // 2) Nắp mở
            if ((o & 0x04) != 0) return "Cover Open";

            // 3) Lỗi phần cứng / kẹt giấy
            if ((e & 0x20) != 0) return "Hardware Error";
            if ((e & 0x08) != 0) return "Paper Jam";
            if ((e & 0x04) != 0 || (e & 0x40) != 0) return "Error";

            // 4) Sắp hết giấy (bits 2–3)
            if ((p & 0x0C) != 0) return "Paper Near End";

            // 5) Đang in
            if ((pr & 0x20) != 0) return "Printing";

            // Offline không rõ nguyên nhân — không báo Idle xanh
            if ((pr & 0x08) != 0) return "Error";

            return "Idle";
        } catch (Exception ex) {
            Log.w(TAG, "readEscStatus: " + ex.getMessage());
            return "Connected";
        }
    }

    private static byte[] safeStatus(byte n) {
        try {
            return Print.GetRealTimeStatus(n);
        } catch (Exception e) {
            return null;
        }
    }

    public static Tone classifyTone(String status) {
        if (status == null) return Tone.BAD;
        if ("Idle".equalsIgnoreCase(status)
                || "Standby".equalsIgnoreCase(status)
                || "Connected".equalsIgnoreCase(status)) {
            return Tone.GOOD;
        }
        if ("Printing".equalsIgnoreCase(status)
                || "Cooling".equalsIgnoreCase(status)
                || "Motor Cooling".equalsIgnoreCase(status)
                || "Standstill".equalsIgnoreCase(status)
                || "Paper Near End".equalsIgnoreCase(status)) {
            return Tone.WARN;
        }
        return Tone.BAD;
    }
}
