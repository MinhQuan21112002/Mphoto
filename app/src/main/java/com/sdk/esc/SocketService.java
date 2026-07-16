package com.sdk.esc;

import android.util.Log;

import org.json.JSONObject;

import java.net.URISyntaxException;

import io.socket.client.IO;
import io.socket.client.Socket;

/**
 * Socket presence cho Device Manager — join machine-app + machine room (giống Lite/mlite).
 * Product cố định: mono; platform: android.
 */
public class SocketService {
    private static final String TAG = "SocketService";
    private static final String SOCKET_URL = "https://mphoto.up.railway.app";

    private static SocketService instance;
    private Socket socket;

    private String pendingMachineRoomId;
    private String pendingMachineAppRoomId;
    private String pendingAppPlatform;
    private String pendingAppProduct;

    private SocketService() {
        try {
            IO.Options opts = new IO.Options();
            opts.forceNew = true;
            opts.reconnection = true;
            opts.reconnectionDelay = 1000;
            opts.transports = new String[]{"websocket", "polling"};
            socket = IO.socket(SOCKET_URL, opts);
            setupListeners();
        } catch (URISyntaxException e) {
            Log.e(TAG, "Socket init error", e);
        }
    }

    public static synchronized SocketService getInstance() {
        if (instance == null) {
            instance = new SocketService();
        }
        return instance;
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
        });
        socket.on(Socket.EVENT_DISCONNECT, args -> Log.d(TAG, "Socket disconnected"));
        socket.on(Socket.EVENT_CONNECT_ERROR, args -> {
            if (args != null && args.length > 0) {
                Log.e(TAG, "Socket connect error: " + args[0]);
            }
        });
    }

    public void connect() {
        if (socket != null && !socket.connected()) {
            socket.connect();
        }
    }

    public boolean isConnected() {
        return socket != null && socket.connected();
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
