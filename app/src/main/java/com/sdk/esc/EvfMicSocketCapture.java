package com.sdk.esc;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Thu mic booth — ưu tiên WebRTC RTP; fallback PCM qua socket khi P2P chưa sẵn sàng. */
public final class EvfMicSocketCapture {
    private static final String TAG = "EvfMicSocket";
    private static final int SAMPLE_RATE = 16000;
    private static final int BUFFER_MS = 40;
    private static final int BYTES_PER_SAMPLE = 2;
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private static volatile EvfMicSocketCapture instance;

    public static EvfMicSocketCapture getInstance() {
        if (instance == null) {
            synchronized (EvfMicSocketCapture.class) {
                if (instance == null) instance = new EvfMicSocketCapture();
            }
        }
        return instance;
    }

    public interface Emitter {
        void emitMicPacket(byte[] packet);
    }

    private Context appContext;
    private Emitter emitter;
    private volatile boolean streamActive;
    private volatile boolean captureWanted = true;
    private Thread captureThread;
    private AudioRecord audioRecord;
    private final Object sync = new Object();

    private EvfMicSocketCapture() {}

    public void init(Context ctx) {
        if (ctx != null) appContext = ctx.getApplicationContext();
    }

    public void setEmitter(Emitter e) {
        emitter = e;
    }

    public void setStreamActive(boolean active) {
        streamActive = active;
        syncCaptureState();
    }

    public void setCaptureWanted(boolean wanted) {
        captureWanted = wanted;
        syncCaptureState();
    }

    public void syncCaptureState() {
        EvfWebRtcService rtc = EvfWebRtcService.peek();
        if (streamActive && captureWanted && (rtc == null || !rtc.isMicRtcActive())) {
            startLocked();
        } else {
            stopLocked();
        }
    }

    private boolean hasMicPermission() {
        return appContext != null
                && ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startLocked() {
        synchronized (sync) {
            if (captureThread != null) return;
            if (!hasMicPermission()) {
                Log.w(TAG, "no RECORD_AUDIO permission");
                return;
            }
            int minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING);
            if (minBuf <= 0) return;
            int frameSamples = SAMPLE_RATE * BUFFER_MS / 1000;
            int frameBytes = frameSamples * BYTES_PER_SAMPLE;
            int bufSize = Math.max(minBuf, frameBytes * 2);
            try {
                audioRecord = new AudioRecord(
                        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        SAMPLE_RATE, CHANNEL, ENCODING, bufSize);
                if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                    releaseRecordLocked();
                    return;
                }
                audioRecord.startRecording();
                Thread t = new Thread(this::captureLoop, "EvfMicSocket");
                t.setDaemon(true);
                captureThread = t;
                t.start();
                Log.d(TAG, "AudioRecord started @16kHz");
            } catch (Exception e) {
                Log.w(TAG, "start failed: " + e.getMessage());
                releaseRecordLocked();
            }
        }
    }

    private void captureLoop() {
        int frameSamples = SAMPLE_RATE * BUFFER_MS / 1000;
        int frameBytes = frameSamples * BYTES_PER_SAMPLE;
        byte[] pcm = new byte[frameBytes];
        while (streamActive && captureWanted) {
            Thread t;
            synchronized (sync) {
                t = captureThread;
            }
            if (t == null || Thread.currentThread() != t || t.isInterrupted()) break;

            AudioRecord rec;
            synchronized (sync) {
                rec = audioRecord;
            }
            if (rec == null) break;

            int read = rec.read(pcm, 0, frameBytes);
            if (read <= 0) continue;

            byte[] packet = new byte[4 + read];
            packet[0] = 'M';
            packet[1] = '1';
            ByteBuffer.wrap(packet, 2, 2).order(ByteOrder.LITTLE_ENDIAN).putShort((short) SAMPLE_RATE);
            System.arraycopy(pcm, 0, packet, 4, read);

            EvfWebRtcService rtc = EvfWebRtcService.peek();
            if (rtc != null && rtc.trySendMicPcm(packet)) continue;

            Emitter em = emitter;
            if (em != null) em.emitMicPacket(packet);
        }
    }

    private void stopLocked() {
        synchronized (sync) {
            Thread t = captureThread;
            captureThread = null;
            if (t != null) t.interrupt();
            releaseRecordLocked();
            Log.d(TAG, "AudioRecord stopped");
        }
    }

    private void releaseRecordLocked() {
        if (audioRecord != null) {
            try {
                audioRecord.stop();
            } catch (Exception ignored) {
            }
            try {
                audioRecord.release();
            } catch (Exception ignored) {
            }
            audioRecord = null;
        }
    }
}
