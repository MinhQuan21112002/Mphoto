package com.sdk.esc;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.TextureView;

import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.net.URISyntaxException;

import io.socket.client.IO;
import io.socket.client.Socket;

/**
 * Socket presence cho Device Manager — join machine-app + machine room (giống Lite/mlite).
 * Product cố định: mono; platform: android.
 * Control Page: live view + remote ISO/exposure/khung/ảnh phụ/chụp/in.
 */
public class SocketService {
    private static final String TAG = "SocketService";
    private static final String APP_PRODUCT = "mono";

    private static SocketService instance;
    private Socket socket;
    private android.content.Context appContext;
    private String boundSocketUrl;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String pendingMachineRoomId;
    private String pendingMachineAppRoomId;
    private String pendingAppPlatform;
    private String pendingAppProduct;
    private String pendingUserToken;

    private FrontCameraEvfStreamer evfStreamer;
    private volatile boolean evfWanted;
    private WeakReference<TextureView> previewTextureRef = new WeakReference<>(null);
    private WeakReference<ControlPageCommandHost> commandHostRef = new WeakReference<>(null);
    private Runnable evfTeardownRunnable;
    private Runnable evfStopStreamingRunnable;

    /**
     * GetRealTimeStatus (USB) không được chạy trên main — sẽ đứng preview/app từng nhịp.
     * Cache + poll trên background thread.
     */
    private static final long PRINTER_STATUS_POLL_MS = 5000L;
    private HandlerThread printerIoThread;
    private Handler printerIoHandler;
    private volatile MonoPrinterStatusHelper.Snapshot cachedPrinter =
            new MonoPrinterStatusHelper.Snapshot(false, "Disconnected", false);
    private volatile String lastEmittedPrinterKey = "";
    private final Runnable printerStatusPollRunnable = new Runnable() {
        @Override
        public void run() {
            refreshPrinterCacheAndEmitIfChanged(false);
            Handler h = printerIoHandler;
            if (h != null) {
                h.postDelayed(this, PRINTER_STATUS_POLL_MS);
            }
        }
    };

    private SocketService() {
        ensureSocket();
    }

    public static synchronized SocketService getInstance() {
        if (instance == null) {
            instance = new SocketService();
        }
        return instance;
    }

    private void ensureSocket() {
        String url = ApiConfig.getBaseUrl();
        if (socket != null && url.equals(boundSocketUrl)) {
            return;
        }
        try {
            if (socket != null) {
                try {
                    socket.off();
                    socket.disconnect();
                } catch (Exception ignored) {
                }
            }
            IO.Options opts = new IO.Options();
            opts.forceNew = true;
            opts.reconnection = true;
            opts.reconnectionDelay = 1000;
            opts.transports = new String[]{"websocket", "polling"};
            socket = IO.socket(url, opts);
            boundSocketUrl = url;
            setupListeners();
            Log.d(TAG, "Socket bound to " + url);
        } catch (URISyntaxException e) {
            Log.e(TAG, "Socket init error", e);
        }
    }

    /** Gọi sau khi đổi Local/Railway (debug). */
    public void reconnectForApiConfig() {
        ensureSocket();
        connect();
    }

    private void setupListeners() {
        if (socket == null) {
            return;
        }
        socket.on(Socket.EVENT_CONNECT, args -> {
            Log.d(TAG, "Socket connected id=" + socket.id());
            if (pendingMachineRoomId != null && !pendingMachineRoomId.isEmpty()) {
                try {
                    socket.emit("join-machine-room", pendingMachineRoomId);
                } catch (Exception e) {
                    Log.e(TAG, "rejoin machine-room failed", e);
                }
            }
            emitJoinMachineAppRoomIfPending();
            emitJoinUserRoomIfPending();
            startPrinterStatusPoll();
            if (evfWanted) {
                startEvfStreaming();
                emitCameraSettingsForControlPage(true);
            }
        });
        socket.on(Socket.EVENT_DISCONNECT, args -> Log.d(TAG, "Socket disconnected"));
        socket.on(Socket.EVENT_CONNECT_ERROR, args -> {
            if (args != null && args.length > 0) {
                Log.e(TAG, "Socket connect error: " + args[0]);
            }
        });
        socket.on("gallery:upload-method-changed", args -> {
            try {
                if (args == null || args.length == 0 || !(args[0] instanceof JSONObject)) {
                    return;
                }
                JSONObject data = (JSONObject) args[0];
                String method = data.optString("galleryUploadMethod", "server");
                if (appContext != null) {
                    GalleryUploadMethodService.getInstance(appContext).apply(method);
                    Log.d(TAG, "gallery:upload-method-changed → " + method);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error handling gallery:upload-method-changed", e);
            }
        });
        socket.on("auth:session-policy-changed", args -> handleSessionPolicySocket(args));
        socket.on("auth:force-logout", args -> handleForceLogoutSocket(args));

        socket.on("evf-stream-subscribe", args -> {
            if (!machineMatches(coerceJsonObject(args))) return;
            if (evfTeardownRunnable != null) {
                mainHandler.removeCallbacks(evfTeardownRunnable);
                evfTeardownRunnable = null;
            }
            if (evfStopStreamingRunnable != null) {
                mainHandler.removeCallbacks(evfStopStreamingRunnable);
                evfStopStreamingRunnable = null;
            }
            Log.d(TAG, "evf-stream-subscribe");
            evfWanted = true;
            // Không reset WebRTC ở đây — F5/resubscribe không làm app đứng.
            if (evfStreamer != null && evfStreamer.isRunning()) {
                Log.d(TAG, "evf-stream-subscribe — already streaming");
            } else {
                startEvfStreaming();
            }
            EvfMicSocketCapture.getInstance().setStreamActive(true);
            emitCameraSettingsForControlPage(true);
        });
        socket.on("evf-stream-unsubscribe", args -> {
            if (!machineMatches(coerceJsonObject(args))) return;
            Log.d(TAG, "evf-stream-unsubscribe");
            evfWanted = false;
            EvfMicSocketCapture.getInstance().setStreamActive(false);
            scheduleEvfStopStreaming();
            scheduleEvfTeardownReset();
        });
        socket.on("evf-stream-resync", args -> {
            if (!machineMatches(coerceJsonObject(args))) return;
            if (evfStopStreamingRunnable != null) {
                mainHandler.removeCallbacks(evfStopStreamingRunnable);
                evfStopStreamingRunnable = null;
            }
            Log.d(TAG, "evf-stream-resync");
            evfWanted = true;
            // Resync layout/WebRTC — không restart camera nếu stream đang chạy (F5).
            if (evfStreamer == null || !evfStreamer.isRunning()) {
                startEvfStreaming();
            }
            EvfMicSocketCapture.getInstance().setStreamActive(true);
            emitCameraSettingsForControlPage(true);
        });
        socket.on("evf-webrtc-signal", args -> {
            try {
                JSONObject data = coerceJsonObject(args);
                Log.d(TAG, "evf-webrtc-signal rawType="
                        + (args != null && args.length > 0 && args[0] != null
                        ? args[0].getClass().getName() : "null")
                        + " parsed=" + (data != null)
                        + " type=" + (data != null ? data.optString("type") : "-"));
                if (!machineMatches(data)) {
                    Log.w(TAG, "evf-webrtc-signal machine mismatch");
                    return;
                }
                String mc = resolveMachineCode();
                emitWebRtcDiag(mc, "offer-seen", data != null ? data.optString("type", "") : "null-data");
                EvfWebRtcService rtc = EvfWebRtcService.peek();
                if (rtc == null && appContext != null) {
                    EvfWebRtcService.init(appContext, this::emitWebRtcSignal);
                    rtc = EvfWebRtcService.peek();
                }
                if (rtc == null) {
                    Log.e(TAG, "evf-webrtc-signal: EvfWebRtcService null");
                    emitWebRtcDiag(mc, "error", "rtc-null");
                    return;
                }
                if (data == null) {
                    emitWebRtcDiag(mc, "error", "parse-null");
                    return;
                }
                rtc.handleSignal(mc, data);
            } catch (Throwable e) {
                Log.e(TAG, "evf-webrtc-signal", e);
                emitWebRtcDiag(resolveMachineCode(), "error", e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
        socket.on("request-camera-settings", args -> {
            if (!machineMatches(coerceJsonObject(args))) return;
            startPrinterStatusPoll();
            ensurePrinterIoThread();
            Handler h = printerIoHandler;
            if (h != null) {
                h.post(() -> refreshPrinterCacheAndEmitIfChanged(true));
            } else {
                emitCameraSettingsForControlPage(evfWanted || (evfStreamer != null && evfStreamer.isRunning()));
            }
        });
        socket.on("camera-command", args -> {
            JSONObject data = coerceJsonObject(args);
            if (!machineMatches(data)) return;
            handleCameraCommand(data);
        });
    }

    private boolean machineMatches(JSONObject data) {
        if (data == null) return true;
        String mc = data.optString("machineCode", "").trim();
        if (mc.isEmpty()) return true;
        String local = resolveMachineCode();
        return local != null && mc.equalsIgnoreCase(local);
    }

    private String resolveMachineCode() {
        if (pendingMachineAppRoomId != null && !pendingMachineAppRoomId.isEmpty()) {
            return pendingMachineAppRoomId;
        }
        if (appContext == null) return null;
        try {
            return MachineManager.getInstance(appContext).getMachineCode();
        } catch (Exception e) {
            return null;
        }
    }

    public void attachControlPageBridge(Activity activity, TextureView previewTexture) {
        if (activity != null) {
            attachAppContext(activity);
            DeviceWakeHelper.applyScreenOnFlags(activity);
        }
        previewTextureRef = new WeakReference<>(previewTexture);
        if (activity instanceof ControlPageCommandHost) {
            commandHostRef = new WeakReference<>((ControlPageCommandHost) activity);
        }
        if (evfWanted) {
            startEvfStreaming();
        }
        startPrinterStatusPoll();
        notifyControlPageSettingsChanged();
    }

    public void attachControlPageHost(ControlPageCommandHost host) {
        commandHostRef = new WeakReference<>(host);
        if (host instanceof Activity) {
            attachAppContext((Activity) host);
        }
        startPrinterStatusPoll();
        notifyControlPageSettingsChanged();
    }

    public void clearControlPageBridge() {
        previewTextureRef = new WeakReference<>(null);
        commandHostRef = new WeakReference<>(null);
        stopEvfStreaming();
    }

    private void ensurePrinterIoThread() {
        if (printerIoThread != null && printerIoThread.isAlive()) return;
        printerIoThread = new HandlerThread("mono-printer-io");
        printerIoThread.start();
        printerIoHandler = new Handler(printerIoThread.getLooper());
    }

    private void startPrinterStatusPoll() {
        ensurePrinterIoThread();
        Handler h = printerIoHandler;
        if (h == null) return;
        h.removeCallbacks(printerStatusPollRunnable);
        h.post(printerStatusPollRunnable);
    }

    private void stopPrinterStatusPoll() {
        Handler h = printerIoHandler;
        if (h != null) {
            h.removeCallbacks(printerStatusPollRunnable);
        }
    }

    /** USB attach/detach / đổi setting — refresh printer trên bg rồi emit. */
    public void notifyControlPageSettingsChanged() {
        ensurePrinterIoThread();
        Handler h = printerIoHandler;
        if (h != null) {
            h.post(() -> refreshPrinterCacheAndEmitIfChanged(true));
        } else {
            emitCameraSettingsForControlPage(evfWanted || (evfStreamer != null && evfStreamer.isRunning()));
        }
    }

    /**
     * Đọc USB trên background. Chỉ emit socket khi status đổi, hoặc force=true.
     */
    private void refreshPrinterCacheAndEmitIfChanged(boolean forceEmit) {
        Context ctx = appContext;
        if (ctx == null) return;
        MonoPrinterStatusHelper.Snapshot snap;
        try {
            snap = MonoPrinterStatusHelper.refresh(ctx);
        } catch (Exception e) {
            Log.w(TAG, "printer refresh: " + e.getMessage());
            return;
        }
        cachedPrinter = snap;
        String key = (snap.connected ? "1" : "0") + "|" + snap.status;
        if (!forceEmit && key.equals(lastEmittedPrinterKey)) {
            return;
        }
        lastEmittedPrinterKey = key;
        boolean camOn = evfWanted || (evfStreamer != null && evfStreamer.isRunning());
        mainHandler.post(() -> emitCameraSettingsForControlPage(camOn));
    }

    public void emitCaptureCountdown(int remaining, int total) {
        if (socket == null || !socket.connected()) return;
        String mc = resolveMachineCode();
        if (mc == null || mc.isEmpty()) return;
        try {
            JSONObject payload = new JSONObject();
            payload.put("machineCode", mc.toUpperCase());
            payload.put("remaining", remaining);
            payload.put("total", total);
            socket.emit("capture-countdown-update", payload);
        } catch (Exception e) {
            Log.w(TAG, "emitCaptureCountdown: " + e.getMessage());
        }
    }

    private void applyEvfStreamProfile() {
        if (evfStreamer == null) return;
        EvfWebRtcService rtc = EvfWebRtcService.peek();
        boolean rtcActive = rtc != null && rtc.isVideoRtcActive();
        if (rtcActive) {
            evfStreamer.setStreamProfile(320, 32, 50);
        } else {
            evfStreamer.setStreamProfile(280, 28, 100);
        }
    }

    private void startEvfStreaming() {
        if (appContext == null || !evfWanted) return;
        try {
            if (evfStreamer == null) {
                evfStreamer = new FrontCameraEvfStreamer(appContext, this::emitEvfFrameUpdate);
            }
            applyEvfStreamProfile();
            TextureView tv = previewTextureRef.get();
            if (evfStreamer.isRunning() && tv != null) {
                Log.d(TAG, "EVF already streaming — skip restart");
                return;
            }
            // Gửi đúng khung ngang như tablet (TextureView + transform). Không xoay 90° → không thành dọc.
            evfStreamer.setControlPageTransform(0, false);
            evfStreamer.setApplySensorOrientation(false);
            evfStreamer.setCoverToLandscape32(false);
            if (tv == null) {
                Log.w(TAG, "EVF: chưa có TextureView — đợi attachControlPageBridge");
                return;
            }
            if (tv.isAvailable()) {
                evfStreamer.startFromTextureView(tv);
                return;
            }
            tv.post(() -> {
                if (!evfWanted) return;
                TextureView again = previewTextureRef.get();
                if (again != null && again.isAvailable()) {
                    if (evfStreamer != null) {
                        evfStreamer.startFromTextureView(again);
                    }
                } else {
                    Log.w(TAG, "EVF: TextureView chưa ready — bỏ qua (không mở camera 2)");
                }
            });
        } catch (Throwable t) {
            Log.e(TAG, "startEvfStreaming", t);
        }
    }

    private void stopEvfStreaming() {
        if (evfStreamer != null) {
            evfStreamer.stop();
        }
    }

    private void scheduleEvfStopStreaming() {
        if (evfStopStreamingRunnable != null) {
            mainHandler.removeCallbacks(evfStopStreamingRunnable);
        }
        evfStopStreamingRunnable = () -> {
            evfStopStreamingRunnable = null;
            if (evfWanted) return;
            stopEvfStreaming();
            Log.d(TAG, "EVF stop streaming (debounced unsubscribe)");
        };
        mainHandler.postDelayed(evfStopStreamingRunnable, 1500);
    }

    private void scheduleEvfTeardownReset() {
        if (evfTeardownRunnable != null) {
            mainHandler.removeCallbacks(evfTeardownRunnable);
        }
        evfTeardownRunnable = () -> {
            evfTeardownRunnable = null;
            if (evfWanted) return;
            EvfWebRtcService rtc = EvfWebRtcService.peek();
            if (rtc != null) rtc.reset();
            Log.d(TAG, "EVF teardown reset (debounced unsubscribe)");
        };
        mainHandler.postDelayed(evfTeardownRunnable, 1500);
    }

    private void emitEvfMicAudioUpdate(byte[] packet) {
        if (packet == null || packet.length < 6) return;
        if (socket == null || !socket.connected()) return;
        String mc = resolveMachineCode();
        if (mc == null || mc.isEmpty()) return;
        try {
            JSONObject payload = new JSONObject();
            payload.put("machineCode", mc.toUpperCase());
            payload.put("encoding", "bin");
            payload.put("ts", System.currentTimeMillis());
            socket.emit("evf-mic-audio-update", payload, packet);
        } catch (Exception e) {
            Log.w(TAG, "emitEvfMicAudioUpdate: " + e.getMessage());
        }
    }

    private void emitEvfFrameUpdate(byte[] jpeg, long tsMs, long seq) {
        if (jpeg == null || jpeg.length == 0) return;
        applyEvfStreamProfile();
        try {
            byte[] packet = EvfFramePacket.pack(jpeg, seq, tsMs);
            EvfWebRtcService rtc = EvfWebRtcService.peek();
            if (rtc != null && rtc.isVideoRtcActive() && rtc.trySendFrame(packet)) {
                return;
            }
        } catch (Exception e) {
            Log.w(TAG, "WebRTC frame send: " + e.getMessage());
        }
        if (socket == null || !socket.connected()) return;
        String mc = resolveMachineCode();
        if (mc == null || mc.isEmpty()) return;
        try {
            JSONObject payload = new JSONObject();
            payload.put("machineCode", mc.toUpperCase());
            payload.put("mime", "image/jpeg");
            payload.put("frame", android.util.Base64.encodeToString(jpeg, android.util.Base64.NO_WRAP));
            payload.put("encoding", "bin");
            payload.put("ts", tsMs);
            payload.put("seq", seq);
            payload.put("liveViewSource", "front");
            payload.put("appProduct", APP_PRODUCT);
            payload.put("appPlatform", "android");
            socket.emit("evf-frame-update", payload, jpeg);
        } catch (Exception e) {
            Log.w(TAG, "emitEvfFrameUpdate: " + e.getMessage());
        }
    }

    private void emitWebRtcSignal(JSONObject payload) {
        if (socket == null || !socket.connected() || payload == null) return;
        try {
            String mc = payload.optString("machineCode", "").trim();
            if (mc.isEmpty()) {
                String local = resolveMachineCode();
                if (local != null) payload.put("machineCode", local.toUpperCase());
            }
            socket.emit("evf-webrtc-signal", payload);
        } catch (Exception e) {
            Log.w(TAG, "emitWebRtcSignal: " + e.getMessage());
        }
    }

    private void emitWebRtcDiag(String machineCode, String phase, String detail) {
        if (socket == null || !socket.connected()) return;
        try {
            String mc = machineCode != null ? machineCode : resolveMachineCode();
            if (mc == null || mc.isEmpty()) return;
            JSONObject payload = new JSONObject();
            payload.put("machineCode", mc.toUpperCase());
            payload.put("target", "control");
            payload.put("type", "diag");
            payload.put("phase", phase != null ? phase : "");
            payload.put("detail", detail != null ? detail : "");
            socket.emit("evf-webrtc-signal", payload);
            Log.d(TAG, "WebRTC diag " + phase + " " + detail);
        } catch (Exception e) {
            Log.w(TAG, "emitWebRtcDiag: " + e.getMessage());
        }
    }

    public void emitCameraSettingsForControlPage(boolean cameraConnected) {
        if (socket == null || !socket.connected()) return;
        String mc = resolveMachineCode();
        if (mc == null || mc.isEmpty()) return;
        Context ctx = appContext;
        try {
            JSONObject payload = new JSONObject();
            payload.put("machineCode", mc.toUpperCase());
            payload.put("isCameraConnected", cameraConnected);
            payload.put("appProduct", APP_PRODUCT);
            payload.put("appPlatform", "android");
            payload.put("liveViewSource", "front");
            payload.put("controlPageLiveViewSource", "Webcam");
            payload.put("evfWebRtc", true);
            payload.put("evfWebRtcVer", 2);

            ControlPageCommandHost host = commandHostRef.get();
            String windowState = host != null ? host.getControlPageWindowState() : "main";
            if (windowState == null || windowState.isEmpty()) windowState = "main";
            payload.put("appWindowState", windowState);
            payload.put("monoPostCapturePending", host != null && host.isMonoPostCapturePending());

            MonoPrinterStatusHelper.Snapshot printer = null;
            if (ctx != null) {
                SharedPreferences cam = ctx.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE);
                String iso = cam.getString("isovalue", "400");
                String exposure = cam.getString("epxvalue", "30000000");
                payload.put("monoIso", iso);
                payload.put("monoIsoLabel", iso);
                payload.put("monoExposure", exposure);
                payload.put("monoExposureLabel", exposureLabel(exposure));

                SharedPreferences framePrefs = ctx.getSharedPreferences("FrameImage", Context.MODE_PRIVATE);
                payload.put("monoFrameIndex", framePrefs.getInt("current_index", 0));
                SharedPreferences subPrefs = ctx.getSharedPreferences("MyAppPrefs2", Context.MODE_PRIVATE);
                payload.put("monoSubIndex", subPrefs.getInt("indexImageView2", 0));

                int printMode = PrintBitmapMode.get(ctx);
                payload.put("monoPrintMode", printMode);
                String[] labels = PrintBitmapMode.labels(ctx);
                int li = PrintBitmapMode.indexOf(printMode);
                payload.put("monoPrintModeLabel",
                        (li >= 0 && li < labels.length) ? labels[li] : String.valueOf(printMode));
                payload.put("monoPrinterTest", PrinterTestMode.isEnabled(ctx));
                payload.put("monoQrPrint", ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
                        .getBoolean("Download", false));
                payload.put("monoClickButtonHidden", ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
                        .getBoolean("click_button_hidden", false));

                // Dùng cache — không gọi GetRealTimeStatus trên thread gọi (thường là main/UI).
                printer = cachedPrinter;
                if (printer == null) {
                    printer = new MonoPrinterStatusHelper.Snapshot(false, "Disconnected", false);
                }
                payload.put("isPrinterConnected", printer.connected);
                payload.put("printerStatus", printer.status);
                payload.put("isPrinterStatusGood",
                        printer.connected && MonoPrinterStatusHelper.classifyTone(printer.status) != MonoPrinterStatusHelper.Tone.BAD);
                payload.put("printerPaperUsed", -1);
                payload.put("printerPaperTotal", -1);
                payload.put("printerPaperRemaining", -1);
            }

            socket.emit("camera-settings-update", payload);
            Log.d(TAG, "camera-settings-update connected=" + cameraConnected
                    + " state=" + windowState
                    + " printer=" + (printer != null ? printer.status : "—"));
        } catch (Exception e) {
            Log.e(TAG, "emitCameraSettingsForControlPage", e);
        }
    }

    private static String exposureLabel(String ns) {
        if (ns == null) return "—";
        try {
            long v = Long.parseLong(ns.trim());
            if (v <= 0) return "—";
            double sec = v / 1_000_000_000.0;
            if (sec < 1.0) return String.format(java.util.Locale.US, "%.1fs", sec);
            if (Math.abs(sec - Math.rint(sec)) < 0.05) return ((int) Math.rint(sec)) + "s";
            return String.format(java.util.Locale.US, "%.1fs", sec);
        } catch (Exception e) {
            return ns;
        }
    }

    private void handleCameraCommand(JSONObject data) {
        if (data == null) return;
        String property = data.optString("property", "").trim();
        if (property.isEmpty()) return;
        int value = data.optInt("value", 0);
        String stringValue = data.optString("stringValue", "");
        String prop = property.toLowerCase(java.util.Locale.ROOT);
        Log.d(TAG, "camera-command " + prop + " value=" + value + " str=" + stringValue);

        mainHandler.post(() -> {
            ControlPageCommandHost host = commandHostRef.get();
            Context ctx = appContext;
            try {
                switch (prop) {
                    case "set-iso":
                    case "mono-set-iso": {
                        String iso = !stringValue.isEmpty() ? stringValue : String.valueOf(value);
                        if (host != null) host.onControlPageSetIso(iso);
                        else if (ctx != null) {
                            ctx.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
                                    .edit().putString("isovalue", iso).apply();
                        }
                        break;
                    }
                    case "set-exposure":
                    case "mono-set-exposure": {
                        String exp = !stringValue.isEmpty() ? stringValue : String.valueOf(value);
                        if (host != null) host.onControlPageSetExposure(exp);
                        else if (ctx != null) {
                            ctx.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
                                    .edit().putString("epxvalue", exp).apply();
                        }
                        break;
                    }
                    case "mono-set-print-mode":
                        if (host != null) host.onControlPageSetPrintMode(value);
                        else if (ctx != null) PrintBitmapMode.set(ctx, value);
                        break;
                    case "mono-set-printer-test":
                        if (host != null) host.onControlPageSetPrinterTest(value != 0);
                        else if (ctx != null) PrinterTestMode.setEnabled(ctx, value != 0);
                        break;
                    case "mono-set-qr-print":
                        if (host != null) host.onControlPageSetQrPrint(value != 0);
                        else if (ctx != null) {
                            ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
                                    .edit().putBoolean("Download", value != 0).apply();
                        }
                        break;
                    case "mono-set-click-button-hidden":
                        if (host != null) host.onControlPageSetClickButtonHidden(value != 0);
                        else if (ctx != null) {
                            ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
                                    .edit().putBoolean("click_button_hidden", value != 0).apply();
                        }
                        break;
                    case "web-apply-mono-frame":
                    case "mono-apply-frame":
                        // Async (có thể sync rồi mới apply) — host tự notify sau onApplied
                        if (host != null) host.onControlPageSelectFrame(stringValue);
                        else Log.w(TAG, "web-apply-mono-frame: no command host");
                        return;
                    case "web-apply-mono-sub":
                    case "mono-apply-sub":
                        if (host != null) host.onControlPageSelectSubPhoto(stringValue);
                        else Log.w(TAG, "web-apply-mono-sub: no command host");
                        return;
                    case "capture":
                        if (host != null) host.onControlPageCapture();
                        break;
                    case "mono-print":
                    case "web-print":
                        if (host != null) host.onControlPagePrint();
                        break;
                    case "mono-cancel":
                        if (host != null) host.onControlPageCancelPostCapture();
                        break;
                    case "navigate-back-to-main":
                    case "mono-back-to-main":
                        if (host != null) host.onControlPageNavigateBackToMain();
                        break;
                    case "wake-device":
                    case "wake-screen": {
                        Activity act = host instanceof Activity ? (Activity) host : null;
                        DeviceWakeHelper.wake(ctx, act);
                        if (host != null) host.onControlPageWakeDevice();
                        break;
                    }
                    case "evf-mic":
                    case "evf-mic-enable": {
                        boolean on = value != 0
                                || "on".equalsIgnoreCase(stringValue)
                                || "1".equals(stringValue)
                                || "true".equalsIgnoreCase(stringValue);
                        EvfWebRtcService rtc = EvfWebRtcService.peek();
                        if (rtc != null) rtc.setMicEnabled(on);
                        EvfMicSocketCapture.getInstance().setCaptureWanted(on);
                        Log.d(TAG, "evf-mic → " + on);
                        break;
                    }
                    case "web-request-mono-frames":
                        syncMonoFramesThenNotify();
                        return;
                    case "web-request-mono-subs":
                        syncMonoSubsThenNotify();
                        return;
                    default:
                        Log.d(TAG, "camera-command ignored: " + prop);
                        return;
                }
                emitCameraSettingsForControlPage(true);
            } catch (Exception e) {
                Log.e(TAG, "handleCameraCommand " + prop, e);
            }
        });
    }

    private void syncMonoFramesThenNotify() {
        if (appContext == null) return;
        String token = TokenManager.getInstance(appContext).getToken();
        if (token == null || token.isEmpty()) {
            emitCameraSettingsForControlPage(true);
            return;
        }
        MonoCacheSync.syncFramesInBackground(appContext, token, new MonoCacheSync.Listener() {
            @Override
            public void onSuccess() {
                ControlPageCommandHost host = commandHostRef.get();
                if (host instanceof Activity) {
                    ((Activity) host).runOnUiThread(() -> {
                        // refresh UI if host implements optional refresh via select current
                        emitCameraSettingsForControlPage(true);
                    });
                } else {
                    emitCameraSettingsForControlPage(true);
                }
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "sync frames: " + message);
                emitCameraSettingsForControlPage(true);
            }
        });
    }

    private void syncMonoSubsThenNotify() {
        if (appContext == null) return;
        String token = TokenManager.getInstance(appContext).getToken();
        if (token == null || token.isEmpty()) {
            emitCameraSettingsForControlPage(true);
            return;
        }
        MonoCacheSync.syncSubPhotosInBackground(appContext, token, new MonoCacheSync.Listener() {
            @Override
            public void onSuccess() {
                emitCameraSettingsForControlPage(true);
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "sync subs: " + message);
                emitCameraSettingsForControlPage(true);
            }
        });
    }

    private void handleSessionPolicySocket(Object[] args) {
        try {
            if (appContext == null || args == null || args.length == 0 || !(args[0] instanceof JSONObject)) {
                return;
            }
            JSONObject data = (JSONObject) args[0];
            String channel = data.optString("channel", "");
            if (!"mono".equalsIgnoreCase(channel)) {
                return;
            }
            Integer days = null;
            if (!data.isNull("loginDurationDays")) {
                int n = data.optInt("loginDurationDays", -1);
                days = n < 1 ? null : n;
            }
            SessionPolicyService.getInstance(appContext).applyLoginDuration(channel, days);
        } catch (Exception e) {
            Log.e(TAG, "auth:session-policy-changed", e);
        }
    }

    private void handleForceLogoutSocket(Object[] args) {
        try {
            if (appContext == null) {
                return;
            }
            JSONObject data = coerceJsonObject(args);
            if (data == null) {
                return;
            }
            String targetMachine = data.optString("machineCode", "").trim();
            if (!targetMachine.isEmpty()) {
                String local = "";
                try {
                    local = MachineManager.getInstance(appContext).getMachineCode();
                } catch (Exception ignored) {
                }
                if (local == null || local.isEmpty()
                        || !targetMachine.equalsIgnoreCase(local.trim())) {
                    return;
                }
            } else {
                String channel = data.optString("channel", "");
                if (!"mono".equalsIgnoreCase(channel)) {
                    return;
                }
            }
            evfWanted = false;
            stopEvfStreaming();
            String iso = data.optString("forceLogoutAt", "");
            long ms = SessionPolicyService.parseIsoToMsPublic(iso);
            if (ms <= 0) {
                ms = System.currentTimeMillis();
            }
            SessionPolicyService.getInstance(appContext).applyForceLogout("mono", ms);
        } catch (Exception e) {
            Log.e(TAG, "auth:force-logout", e);
        }
    }

    private static JSONObject coerceJsonObject(Object[] args) {
        if (args == null || args.length == 0 || args[0] == null) {
            return null;
        }
        Object raw = args[0];
        if (raw instanceof JSONObject) {
            return (JSONObject) raw;
        }
        if (raw instanceof java.util.Map) {
            try {
                return mapToJsonObject((java.util.Map<?, ?>) raw);
            } catch (Exception e) {
                return null;
            }
        }
        try {
            String s = String.valueOf(raw);
            if (s.startsWith("{")) return new JSONObject(s);
        } catch (Exception ignored) {
        }
        return null;
    }

    private static JSONObject mapToJsonObject(java.util.Map<?, ?> map) throws org.json.JSONException {
        JSONObject out = new JSONObject();
        for (java.util.Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getKey() == null) continue;
            out.put(String.valueOf(e.getKey()), wrapJsonValue(e.getValue()));
        }
        return out;
    }

    private static Object wrapJsonValue(Object v) throws org.json.JSONException {
        if (v == null || v == JSONObject.NULL) return JSONObject.NULL;
        if (v instanceof JSONObject || v instanceof org.json.JSONArray) return v;
        if (v instanceof java.util.Map) return mapToJsonObject((java.util.Map<?, ?>) v);
        if (v instanceof java.util.Collection) {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (Object item : (java.util.Collection<?>) v) arr.put(wrapJsonValue(item));
            return arr;
        }
        return v;
    }

    /** Gắn Application context để nhận realtime galleryUploadMethod. */
    public void attachAppContext(android.content.Context context) {
        if (context != null) {
            this.appContext = context.getApplicationContext();
            EvfWebRtcService.init(appContext, this::emitWebRtcSignal);
            EvfMicSocketCapture.getInstance().init(appContext);
            EvfMicSocketCapture.getInstance().setEmitter(this::emitEvfMicAudioUpdate);
            if (socket != null && socket.connected()) {
                startPrinterStatusPoll();
            }
        }
    }

    public void connect() {
        ensureSocket();
        if (socket != null && !socket.connected()) {
            socket.connect();
        }
    }

    public boolean isConnected() {
        return socket != null && socket.connected();
    }

    public void disconnect() {
        try {
            evfWanted = false;
            stopPrinterStatusPoll();
            stopEvfStreaming();
            if (socket != null && socket.connected()) {
                socket.disconnect();
            }
        } catch (Exception e) {
            Log.e(TAG, "disconnect", e);
        }
    }

    public void joinMachineRoom(String machineId) {
        if (machineId == null || machineId.trim().isEmpty()) {
            return;
        }
        pendingMachineRoomId = machineId.trim().toUpperCase();
        if (socket != null && socket.connected()) {
            try {
                socket.emit("join-machine-room", pendingMachineRoomId);
            } catch (Exception e) {
                Log.e(TAG, "joinMachineRoom error", e);
            }
        } else if (socket != null) {
            socket.connect();
        }
    }

    public void joinMachineAppRoom(String machineId, String platform, String product) {
        if (machineId == null || machineId.trim().isEmpty()) {
            return;
        }
        pendingMachineAppRoomId = machineId.trim().toUpperCase();
        pendingAppPlatform = platform != null ? platform.trim().toLowerCase() : null;
        pendingAppProduct = product != null ? product.trim().toLowerCase() : null;
        if (socket != null && socket.connected()) {
            emitJoinMachineAppRoomIfPending();
        } else if (socket != null) {
            socket.connect();
        }
    }

    public void joinUserRoom(String token) {
        if (token == null || token.trim().isEmpty()) {
            return;
        }
        pendingUserToken = token.trim();
        emitJoinUserRoomIfPending();
    }

    private void emitJoinUserRoomIfPending() {
        if (socket == null || !socket.connected()
                || pendingUserToken == null || pendingUserToken.isEmpty()) {
            return;
        }
        try {
            socket.emit("join-user-room", pendingUserToken);
            Log.d(TAG, "join-user-room");
        } catch (Exception e) {
            Log.e(TAG, "join-user-room error", e);
        }
    }

    private void emitJoinMachineAppRoomIfPending() {
        if (socket == null || !socket.connected()
                || pendingMachineAppRoomId == null || pendingMachineAppRoomId.isEmpty()) {
            return;
        }
        try {
            JSONObject payload = new JSONObject();
            payload.put("machineCode", pendingMachineAppRoomId);
            if (pendingAppPlatform != null && !pendingAppPlatform.isEmpty()) {
                payload.put("platform", pendingAppPlatform);
            }
            if (pendingAppProduct != null && !pendingAppProduct.isEmpty()) {
                payload.put("product", pendingAppProduct);
            }
            socket.emit("join-machine-app-room", payload);
            Log.d(TAG, "join-machine-app-room " + payload);
        } catch (Exception e) {
            Log.e(TAG, "emitJoinMachineAppRoomIfPending error", e);
        }
    }
}
