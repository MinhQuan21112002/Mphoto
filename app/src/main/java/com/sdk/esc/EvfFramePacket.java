package com.sdk.esc;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

/** EVF packet: 8-byte seq (BE) + 8-byte Unix-ms (BE) + JPEG — khớp mlite EvfFramePacket. */
public final class EvfFramePacket {
    public static final int HEADER_LENGTH = 16;
    private static final AtomicLong SEQ = new AtomicLong();

    private EvfFramePacket() {
    }

    public static byte[] pack(byte[] jpeg) {
        return pack(jpeg, SEQ.incrementAndGet(), System.currentTimeMillis());
    }

    public static byte[] pack(byte[] jpeg, long seq, long tsMs) {
        if (jpeg == null || jpeg.length == 0) return new byte[0];
        byte[] packet = new byte[HEADER_LENGTH + jpeg.length];
        writeBe64(packet, 0, seq);
        writeBe64(packet, 8, tsMs);
        System.arraycopy(jpeg, 0, packet, HEADER_LENGTH, jpeg.length);
        return packet;
    }

    private static void writeBe64(byte[] dest, int offset, long value) {
        dest[offset] = (byte) ((value >>> 56) & 0xFF);
        dest[offset + 1] = (byte) ((value >>> 48) & 0xFF);
        dest[offset + 2] = (byte) ((value >>> 40) & 0xFF);
        dest[offset + 3] = (byte) ((value >>> 32) & 0xFF);
        dest[offset + 4] = (byte) ((value >>> 24) & 0xFF);
        dest[offset + 5] = (byte) ((value >>> 16) & 0xFF);
        dest[offset + 6] = (byte) ((value >>> 8) & 0xFF);
        dest[offset + 7] = (byte) (value & 0xFF);
    }
}
