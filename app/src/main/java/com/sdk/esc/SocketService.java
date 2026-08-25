package com.sdk.esc;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
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
            Log.d(TAG, "evf-stream-subscribe");
            evfWanted = true;
            startEvfStreaming();
            emitCameraSettingsForControlPage(true);
        });
        socket.on("evf-stream-unsubscribe", args -> {
            if (!machineMatches(coerceJsonObject(args))) return;
            Log.d(TAG, "evf-stream-unsubscribe");
            evfWanted = false;
            stopEvfStreaming();
        });
        socket.on("evf-stream-resync", args -> {
            if (!machineMatches(coerceJsonObject(args))) return;
            Log.d(TAG, "evf-stream-resync");
            evfWanted = true;
            startEvfStreaming();
            emitCameraSettingsForControlPage(true);
        });
        socket.on("request-camera-settings", args -> {
            if (!machineMatches(coerceJsonObject(args))) return;
            emitCameraSettingsForControlPage(evfWanted || (evfStreamer != null && evfStreamer.isRunning()));
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
        }
        previewTextureRef = new WeakReference<>(previewTexture);
        if (activity instanceof ControlPageCommandHost) {
            commandHostRef = new WeakReference<>((ControlPageCommandHost) activity);
        }
        if (evfWanted) {
            startEvfStreaming();
        }
        emitCameraSettingsForControlPage(true);
    }

    public void attachControlPageHost(ControlPageCommandHost host) {
        commandHostRef = new WeakReference<>(host);
        if (host instanceof Activity) {
            attachAppContext((Activity) host);
        }
        emitCameraSettingsForControlPage(true);
    }

    public void clearControlPageBridge() {
        previewTextureRef = new WeakReference<>(null);
        commandHostRef = new WeakReference<>(null);
        stopEvfStreaming();
    }

    public void notifyControlPageSettingsChanged() {
        emitCameraSettingsForControlPage(evfWanted || (evfStreamer != null && evfStreamer.isRunning()));
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

    private void startEvfStreaming() {
        if (appContext == null || !evfWanted) return;
        try {
            if (evfStreamer == null) {
                evfStreamer = new FrontCameraEvfStreamer(appContext, this::emitEvfFrameUpdate);
            }
            // Gửi đúng khung ngang như tablet (TextureView + transform). Không xoay 90° → không thành dọc.
            evfStreamer.setControlPageTransform(0, false);
            evfStreamer.setStreamProfile(/*maxEdge*/ 320, /*jpegQ*/ 32, /*intervalMs*/ 50);
            evfStreamer.setApplySensorOrientation(false);
            evfStreamer.setCoverToLandscape32(false);
            TextureView tv = previewTextureRef.get();
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

    private void emitEvfFrameUpdate(byte[] jpeg, long tsMs, long seq) {
        if (socket == null || !socket.connected() || jpeg == null || jpeg.length == 0) return;
        String mc = resolveMachineCode();
        if (mc == null || mc.isEmpty()) return;
        try {
            JSONObject payload = new JSONObject();
            payload.put("machineCode", mc.toUpperCase());
            payload.put("frame", Base64.encodeToString(jpeg, Base64.NO_WRAP));
            payload.put("mime", "image/jpeg");
            payload.put("ts", tsMs);
            payload.put("seq", seq);
            payload.put("liveViewSource", "front");
            payload.put("appProduct", APP_PRODUCT);
            payload.put("appPlatform", "android");
            socket.emit("evf-frame-update", payload);
        } catch (Exception e) {
            Log.w(TAG, "emitEvfFrameUpdate: " + e.getMessage());
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

            ControlPageCommandHost host = commandHostRef.get();
            String windowState = host != null ? host.getControlPageWindowState() : "main";
            if (windowState == null || windowState.isEmpty()) windowState = "main";
            payload.put("appWindowState", windowState);
            payload.put("monoPostCapturePending", host != null && host.isMonoPostCapturePending());

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
            }

            socket.emit("camera-settings-update", payload);
            Log.d(TAG, "camera-settings-update connected=" + cameraConnected
                    + " state=" + windowState);
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
        try {
            return new JSONObject(String.valueOf(raw));
        } catch (Exception e) {
            return null;
        }
    }

    /** Gắn Application context để nhận realtime galleryUploadMethod. */
    public void attachAppContext(android.content.Context context) {
        if (context != null) {
            this.appContext = context.getApplicationContext();
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
