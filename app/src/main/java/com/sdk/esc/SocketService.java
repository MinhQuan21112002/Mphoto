package com.sdk.esc;

import android.app.Activity;
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
 * Control Page: stream live view camera trước qua evf-frame-update.
 */
public class SocketService {
    private static final String TAG = "SocketService";
    private static final String APP_PRODUCT = "mono";

    private static SocketService instance;
    private Socket socket;
    private android.content.Context appContext;
    private String boundSocketUrl;

    private String pendingMachineRoomId;
    private String pendingMachineAppRoomId;
    private String pendingAppPlatform;
    private String pendingAppProduct;
    private String pendingUserToken;

    private FrontCameraEvfStreamer evfStreamer;
    private volatile boolean evfWanted;
    private WeakReference<TextureView> previewTextureRef = new WeakReference<>(null);

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
        if (evfWanted) {
            startEvfStreaming();
        }
    }

    public void clearControlPageBridge() {
        previewTextureRef = new WeakReference<>(null);
        stopEvfStreaming();
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
        try {
            JSONObject payload = new JSONObject();
            payload.put("machineCode", mc.toUpperCase());
            payload.put("isCameraConnected", cameraConnected);
            payload.put("appWindowState", "capture");
            payload.put("appProduct", APP_PRODUCT);
            payload.put("appPlatform", "android");
            payload.put("liveViewSource", "front");
            payload.put("controlPageLiveViewSource", "Webcam");
            socket.emit("camera-settings-update", payload);
            Log.d(TAG, "camera-settings-update connected=" + cameraConnected);
        } catch (Exception e) {
            Log.e(TAG, "emitCameraSettingsForControlPage", e);
        }
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
