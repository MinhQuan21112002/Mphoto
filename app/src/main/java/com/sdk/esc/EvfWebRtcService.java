package com.sdk.esc;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebRTC answerer cho EVF — giống mlite {@code EvfWebRtcService}.
 * Web tạo offer + data channel {@code evf}; app trả answer và gửi JPEG packet P2P.
 */
public final class EvfWebRtcService {
    private static final String TAG = "EvfWebRtc";
    private static final long MAX_FRAME_BUFFERED = 512L * 1024L;

    public interface SignalEmitter {
        void emitWebRtcSignal(JSONObject payload);
    }

    private static EvfWebRtcService instance;
    private static volatile boolean factoryReady;

    private final Context appContext;
    private final SignalEmitter signalEmitter;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "evf-webrtc");
        t.setDaemon(true);
        return t;
    });
    private final Object sync = new Object();
    private final AtomicInteger offerGen = new AtomicInteger();
    private final List<IceCandidate> pendingRemoteIce = new ArrayList<>();

    private PeerConnectionFactory factory;
    private PeerConnection peerConnection;
    private DataChannel frameChannel;
    private AudioSource micAudioSource;
    private AudioTrack micAudioTrack;
    private volatile boolean frameChannelOpen;
    private volatile boolean canAddRemoteIce;
    private String machineCode;

    public static synchronized EvfWebRtcService getInstance(Context context, SignalEmitter emitter) {
        if (instance == null) {
            instance = new EvfWebRtcService(context.getApplicationContext(), emitter);
        } else if (emitter != null) {
            // keep latest emitter via field replace if needed — singleton uses constructor emitter
        }
        return instance;
    }

    /** Singleton đã tạo (SocketService gắn emitter lúc connect). */
    public static synchronized EvfWebRtcService peek() {
        return instance;
    }

    public static synchronized void init(Context context, SignalEmitter emitter) {
        instance = new EvfWebRtcService(context.getApplicationContext(), emitter);
        // Warm-up native factory sớm — offer đầu không bị chậm / bị retry giết giữa chừng.
        try {
            instance.getOrCreateFactory();
        } catch (Throwable t) {
            Log.e(TAG, "WebRTC factory warm-up failed", t);
        }
    }

    private EvfWebRtcService(Context appContext, SignalEmitter signalEmitter) {
        this.appContext = appContext;
        this.signalEmitter = signalEmitter;
        ensureFactory(appContext);
    }

    private static synchronized void ensureFactory(Context context) {
        if (factoryReady) return;
        PeerConnectionFactory.InitializationOptions opts =
                PeerConnectionFactory.InitializationOptions.builder(context)
                        .setEnableInternalTracer(false)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(opts);
        factoryReady = true;
    }

    public void reset() {
        worker.execute(this::resetInternal);
    }

    private void resetInternal() {
        offerGen.incrementAndGet();
        synchronized (sync) {
            pendingOfferAfterClose = null;
            resetInternalLocked();
        }
        Log.d(TAG, "reset");
    }

    public void handleSignal(String mid, JSONObject payload) {
        if (payload == null) return;
        final String mc = mid != null ? mid.trim().toUpperCase() : "";
        worker.execute(() -> {
            try {
                String target = payload.optString("target", "app").trim().toLowerCase();
                if (!target.isEmpty() && !"app".equals(target)) return;
                String type = payload.optString("type", "").trim().toLowerCase();
                switch (type) {
                    case "offer":
                        emitDiag(mc, "offer-handling", "");
                        handleOffer(mc, payload);
                        break;
                    case "ice":
                        handleIce(payload);
                        break;
                    case "hangup":
                        resetInternal();
                        break;
                    default:
                        break;
                }
            } catch (Exception e) {
                Log.e(TAG, "handleSignal", e);
                emitDiag(mc, "error", e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    private PeerConnectionFactory getOrCreateFactory() {
        synchronized (sync) {
            if (factory == null) {
                ensureFactory(appContext);
                factory = PeerConnectionFactory.builder().createPeerConnectionFactory();
            }
            return factory;
        }
    }

    private volatile long lastOfferHandledMs;
    private volatile boolean closingPeerConnection;
    private PendingOffer pendingOfferAfterClose;

    private static final class PendingOffer {
        final String machineCode;
        final JSONObject payload;
        final int generation;

        PendingOffer(String machineCode, JSONObject payload, int generation) {
            this.machineCode = machineCode;
            this.payload = payload;
            this.generation = generation;
        }
    }

    private void handleOffer(String machineCode, JSONObject payload) {
        long now = System.currentTimeMillis();
        synchronized (sync) {
            // Chỉ gộp burst offer khi đang CONNECTING — không chặn offer mới sau F5 (PC cũ còn CONNECTED).
            if (peerConnection != null && now - lastOfferHandledMs < 500) {
                PeerConnection.PeerConnectionState st = peerConnection.connectionState();
                if (st == PeerConnection.PeerConnectionState.CONNECTING) {
                    Log.d(TAG, "handleOffer coalesced — still connecting");
                    return;
                }
            }
            lastOfferHandledMs = now;
        }
        int generation = offerGen.incrementAndGet();
        String sdpText = normalizeSdp(extractSdp(payload));
        if (sdpText == null || sdpText.isEmpty()) {
            Log.e(TAG, "handleOffer empty sdp keys=" + (payload != null ? payload.keys() : "null"));
            emitDiag(machineCode, "error", "empty-sdp");
            return;
        }

        PendingOffer task = new PendingOffer(machineCode, payload, generation);
        synchronized (sync) {
            if (generation != offerGen.get()) return;
            if (peerConnection != null || closingPeerConnection) {
                pendingOfferAfterClose = task;
                if (!closingPeerConnection) {
                    closingPeerConnection = true;
                    closePeerConnectionAsyncLocked(this::runPendingOfferAfterClose);
                }
                return;
            }
        }
        processOfferInternal(task, sdpText);
    }

    private void runPendingOfferAfterClose() {
        PendingOffer task;
        synchronized (sync) {
            closingPeerConnection = false;
            task = pendingOfferAfterClose;
            pendingOfferAfterClose = null;
        }
        if (task == null) return;
        if (task.generation != offerGen.get()) return;
        String sdpText = normalizeSdp(extractSdp(task.payload));
        if (sdpText == null || sdpText.isEmpty()) return;
        processOfferInternal(task, sdpText);
    }

    /** Close PC trên worker thread — tránh native crash khi offer mới đến lúc PC cũ còn CONNECTED. */
    private void closePeerConnectionAsyncLocked(Runnable then) {
        canAddRemoteIce = false;
        pendingRemoteIce.clear();
        frameChannelOpen = false;
        final DataChannel fc = frameChannel;
        frameChannel = null;
        final PeerConnection pc = peerConnection;
        peerConnection = null;
        machineCode = null;
        worker.execute(() -> {
            try {
                if (fc != null) fc.close();
            } catch (Exception ignored) {
            }
            try {
                if (pc != null) pc.close();
            } catch (Exception ignored) {
            }
            try {
                Thread.sleep(150);
            } catch (InterruptedException ignored) {
            }
            synchronized (sync) {
                releaseMicLocked();
            }
            EvfMicSocketCapture.getInstance().syncCaptureState();
            if (then != null) then.run();
        });
    }

    private void processOfferInternal(PendingOffer task, String sdpText) {
        final String machineCode = task.machineCode;
        final int generation = task.generation;
        try {
            emitDiag(machineCode, "sdp-ok", "len=" + sdpText.length()
                    + " head=" + sdpText.substring(0, Math.min(24, sdpText.length())).replace('\r', ' ').replace('\n', '/'));

            PeerConnectionFactory f = getOrCreateFactory();
            if (f == null) {
                emitDiag(machineCode, "error", "factory-null");
                return;
            }
            List<PeerConnection.IceServer> iceServers = buildIceServers();
            PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(iceServers);
            config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

            final PeerConnection pc;
            synchronized (sync) {
                if (generation != offerGen.get()) return;
                if (peerConnection != null || closingPeerConnection) return;
                this.machineCode = machineCode;
                pc = f.createPeerConnection(config, new PeerObserver(machineCode, generation));
                if (pc == null) {
                    Log.e(TAG, "createPeerConnection null");
                    emitDiag(machineCode, "error", "pc-null");
                    return;
                }
                peerConnection = pc;
            }

            SessionDescription offer = new SessionDescription(SessionDescription.Type.OFFER, sdpText);
            if (offer.description == null || offer.description.isEmpty()) {
                emitDiag(machineCode, "error", "offer-desc-null");
                return;
            }
            pc.setRemoteDescription(new SimpleSdpObserver() {
                @Override
                public void onSetSuccess() {
                    if (generation != offerGen.get()) return;
                    attachMicTrack(f, pc, machineCode);
                    pc.createAnswer(new SimpleSdpObserver() {
                        @Override
                        public void onCreateSuccess(SessionDescription answer) {
                            if (generation != offerGen.get()) return;
                            pc.setLocalDescription(new SimpleSdpObserver() {
                                @Override
                                public void onSetSuccess() {
                                    if (generation != offerGen.get()) return;
                                    synchronized (sync) {
                                        canAddRemoteIce = true;
                                        flushPendingIceLocked(pc);
                                    }
                                    emitAnswer(machineCode, answer.description);
                                    emitDiag(machineCode, "answer-sent", "");
                                    Log.d(TAG, "answer sent");
                                }

                                @Override
                                public void onSetFailure(String s) {
                                    Log.e(TAG, "setLocalDescription fail: " + s);
                                    emitDiag(machineCode, "error", "setLocal: " + s);
                                }
                            }, answer);
                        }

                        @Override
                        public void onCreateFailure(String s) {
                            Log.e(TAG, "createAnswer fail: " + s);
                            emitDiag(machineCode, "error", "createAnswer: " + s);
                        }
                    }, new MediaConstraints());
                }

                @Override
                public void onSetFailure(String s) {
                    Log.e(TAG, "setRemoteDescription fail: " + s);
                    emitDiag(machineCode, "error", "setRemote: " + s);
                }
            }, offer);
        } catch (Exception e) {
            Log.e(TAG, "handleOffer", e);
            emitDiag(machineCode, "error", e.getClass().getSimpleName() + ": " + e.getMessage());
            if (generation == offerGen.get()) resetInternal();
        }
    }

    /** Gắn mic send-only nếu đã có RECORD_AUDIO; thiếu quyền thì vẫn EVF JPEG bình thường. */
    private void attachMicTrack(PeerConnectionFactory f, PeerConnection pc, String mid) {
        if (f == null || pc == null) return;
        synchronized (sync) {
            releaseMicLocked();
        }
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            emitDiag(mid, "mic-skip", "no-permission");
            return;
        }
        try {
            MediaConstraints ac = new MediaConstraints();
            AudioSource src = f.createAudioSource(ac);
            AudioTrack track = f.createAudioTrack("evf_mic", src);
            track.setEnabled(true);
            pc.addTrack(track, Collections.singletonList("evf_stream"));
            synchronized (sync) {
                micAudioSource = src;
                micAudioTrack = track;
            }
            emitDiag(mid, "mic-attached", "");
            Log.d(TAG, "mic track attached");
            // Áp dụng trạng thái bật/tắt thu đã lưu (Control Page có thể tắt trước).
            if (micAudioTrack != null) micAudioTrack.setEnabled(micCaptureWanted);
            EvfMicSocketCapture.getInstance().syncCaptureState();
        } catch (Throwable t) {
            Log.w(TAG, "attachMicTrack: " + t.getMessage());
            emitDiag(mid, "mic-skip", String.valueOf(t.getMessage()));
        }
    }

    private volatile boolean micCaptureWanted = true;

    /** Control Page bật/tắt thu mic (không teardown PeerConnection). */
    public void setMicEnabled(boolean enabled) {
        micCaptureWanted = enabled;
        EvfMicSocketCapture.getInstance().setCaptureWanted(enabled);
        synchronized (sync) {
            if (micAudioTrack != null) {
                try {
                    micAudioTrack.setEnabled(enabled);
                    Log.d(TAG, "mic enabled=" + enabled);
                } catch (Exception e) {
                    Log.w(TAG, "setMicEnabled: " + e.getMessage());
                }
            }
        }
    }

    /** @return true nếu mic đang đi WebRTC RTP ổn định (video P2P cũng phải OK). */
    public boolean trySendMicPcm(byte[] packet) {
        return isMicRtcActive();
    }

    /** Video EVF đang P2P — dùng chọn profile stream (socket fallback dùng profile thấp hơn). */
    public boolean isVideoRtcActive() {
        synchronized (sync) {
            return isVideoRtcPathReadyLocked();
        }
    }

    public boolean isMicRtcActive() {
        if (!micCaptureWanted) return false;
        synchronized (sync) {
            return isVideoRtcPathReadyLocked() && isMicRtpReadyLocked();
        }
    }

    private boolean isVideoRtcPathReadyLocked() {
        if (!frameChannelOpen || frameChannel == null || peerConnection == null) return false;
        if (peerConnection.connectionState() != PeerConnection.PeerConnectionState.CONNECTED) return false;
        return frameChannel.state() == DataChannel.State.OPEN;
    }

    private boolean isMicRtpReadyLocked() {
        if (micAudioTrack == null || peerConnection == null) return false;
        if (peerConnection.connectionState() != PeerConnection.PeerConnectionState.CONNECTED) return false;
        return micAudioTrack.enabled();
    }

    private void releaseMicLocked() {
        if (micAudioTrack != null) {
            try {
                micAudioTrack.setEnabled(false);
                micAudioTrack.dispose();
            } catch (Exception ignored) {
            }
            micAudioTrack = null;
        }
        if (micAudioSource != null) {
            try {
                micAudioSource.dispose();
            } catch (Exception ignored) {
            }
            micAudioSource = null;
        }
    }

    private void resetInternalLocked() {
        synchronized (sync) {
            if (peerConnection == null && frameChannel == null && !closingPeerConnection) {
                canAddRemoteIce = false;
                pendingRemoteIce.clear();
                frameChannelOpen = false;
                releaseMicLocked();
                machineCode = null;
                EvfMicSocketCapture.getInstance().syncCaptureState();
                return;
            }
            pendingOfferAfterClose = null;
            if (!closingPeerConnection) {
                closingPeerConnection = true;
                closePeerConnectionAsyncLocked(() -> {
                    synchronized (sync) {
                        closingPeerConnection = false;
                    }
                });
            }
        }
    }

    private void handleIce(JSONObject payload) {
        try {
            JSONObject cand = payload.optJSONObject("candidate");
            if (cand == null) return;
            String candidate = cand.optString("candidate", "");
            if (candidate.isEmpty()) return;
            String sdpMid = cand.optString("sdpMid", "0");
            int sdpMLineIndex = cand.optInt("sdpMLineIndex", 0);
            IceCandidate ice = new IceCandidate(sdpMid, sdpMLineIndex, candidate);
            synchronized (sync) {
                if (!canAddRemoteIce || peerConnection == null) {
                    pendingRemoteIce.add(ice);
                    return;
                }
                peerConnection.addIceCandidate(ice);
            }
        } catch (Exception e) {
            Log.w(TAG, "handleIce: " + e.getMessage());
        }
    }

    private void flushPendingIceLocked(PeerConnection pc) {
        for (IceCandidate ice : pendingRemoteIce) {
            try {
                pc.addIceCandidate(ice);
            } catch (Exception ignored) {
            }
        }
        pendingRemoteIce.clear();
    }

    private void emitAnswer(String machineCode, String sdp) {
        try {
            JSONObject sdpObj = new JSONObject();
            sdpObj.put("type", "answer");
            sdpObj.put("sdp", sdp);
            JSONObject payload = new JSONObject();
            payload.put("machineCode", machineCode);
            payload.put("target", "control");
            payload.put("type", "answer");
            payload.put("sdp", sdpObj);
            if (signalEmitter != null) signalEmitter.emitWebRtcSignal(payload);
        } catch (Exception e) {
            Log.e(TAG, "emitAnswer", e);
        }
    }

    private void emitIce(String machineCode, IceCandidate candidate) {
        try {
            JSONObject cand = new JSONObject();
            cand.put("candidate", candidate.sdp);
            cand.put("sdpMid", candidate.sdpMid);
            cand.put("sdpMLineIndex", candidate.sdpMLineIndex);
            JSONObject payload = new JSONObject();
            payload.put("machineCode", machineCode);
            payload.put("target", "control");
            payload.put("type", "ice");
            payload.put("candidate", cand);
            if (signalEmitter != null) signalEmitter.emitWebRtcSignal(payload);
        } catch (Exception e) {
            Log.w(TAG, "emitIce: " + e.getMessage());
        }
    }

    private void emitDiag(String machineCode, String phase, String detail) {
        try {
            JSONObject payload = new JSONObject();
            if (machineCode != null) payload.put("machineCode", machineCode);
            payload.put("target", "control");
            payload.put("type", "diag");
            payload.put("phase", phase != null ? phase : "");
            payload.put("detail", detail != null ? detail : "");
            if (signalEmitter != null) signalEmitter.emitWebRtcSignal(payload);
        } catch (Exception e) {
            Log.w(TAG, "emitDiag: " + e.getMessage());
        }
    }

    /** @return true nếu đã gửi qua P2P (không cần socket fallback). */
    public boolean trySendFrame(byte[] packet) {
        if (packet == null || packet.length == 0) return false;
        DataChannel dc;
        synchronized (sync) {
            if (!frameChannelOpen || frameChannel == null || peerConnection == null) return false;
            PeerConnection.PeerConnectionState st = peerConnection.connectionState();
            // DC có thể OPEN trước khi state = CONNECTED — đừng clear flag (sẽ kẹt Socket mãi).
            if (st != PeerConnection.PeerConnectionState.CONNECTED
                    && st != PeerConnection.PeerConnectionState.CONNECTING) {
                return false;
            }
            if (frameChannel.state() != DataChannel.State.OPEN) return false;
            if (st != PeerConnection.PeerConnectionState.CONNECTED) return false;
            if (frameChannel.bufferedAmount() > MAX_FRAME_BUFFERED) return false;
            dc = frameChannel;
        }
        try {
            ByteBuffer buf = ByteBuffer.wrap(packet);
            return dc.send(new DataChannel.Buffer(buf, true));
        } catch (Exception e) {
            Log.w(TAG, "trySendFrame: " + e.getMessage());
            frameChannelOpen = false;
            EvfMicSocketCapture.getInstance().syncCaptureState();
            return false;
        }
    }

    private List<PeerConnection.IceServer> buildIceServers() {
        // Phone ≠ máy chạy browser — luôn STUN (host candidate vẫn có). Không dùng iceServers rỗng
        // như mlite localhost cùng máy (tránh hairpin).
        List<PeerConnection.IceServer> list = new ArrayList<>();
        list.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        list.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer());
        return list;
    }

    private static String extractSdp(JSONObject payload) {
        if (payload == null) return "";
        Object sdp = payload.opt("sdp");
        if (sdp == null || sdp == JSONObject.NULL) return "";
        if (sdp instanceof String) return ((String) sdp).trim();
        if (sdp instanceof JSONObject) {
            return ((JSONObject) sdp).optString("sdp", "").trim();
        }
        if (sdp instanceof java.util.Map) {
            Object inner = ((java.util.Map<?, ?>) sdp).get("sdp");
            return inner != null ? String.valueOf(inner).trim() : "";
        }
        // Một số bản socket.io để nested object thành string JSON
        String asStr = String.valueOf(sdp).trim();
        if (asStr.startsWith("{")) {
            try {
                return new JSONObject(asStr).optString("sdp", "").trim();
            } catch (Exception ignored) {
            }
        }
        return asStr;
    }

    /** Android native WebRTC bắt buộc SDP dùng CRLF — thiếu sẽ lỗi "SessionDescription is NULL". */
    private static String normalizeSdp(String sdp) {
        if (sdp == null) return "";
        String s = sdp.trim();
        if (s.isEmpty()) return "";
        if (s.startsWith("{")) {
            try {
                String inner = new JSONObject(s).optString("sdp", "").trim();
                if (!inner.isEmpty()) s = inner;
            } catch (Exception ignored) {
            }
        }
        s = s.replace("\r\n", "\n").replace('\r', '\n');
        s = s.replace("\n", "\r\n");
        if (!s.endsWith("\r\n")) s = s + "\r\n";
        return s;
    }

    private final class PeerObserver implements PeerConnection.Observer {
        private final String mid;
        private final int generation;

        PeerObserver(String mid, int generation) {
            this.mid = mid;
            this.generation = generation;
        }

        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
            Log.d(TAG, "ICE connection: " + iceConnectionState);
        }

        @Override
        public void onConnectionChange(PeerConnection.PeerConnectionState newState) {
            Log.d(TAG, "connection: " + newState);
            if (newState == PeerConnection.PeerConnectionState.FAILED
                    || newState == PeerConnection.PeerConnectionState.CLOSED
                    || newState == PeerConnection.PeerConnectionState.DISCONNECTED) {
                frameChannelOpen = false;
            }
            EvfMicSocketCapture.getInstance().syncCaptureState();
        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {
        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
        }

        @Override
        public void onIceCandidate(IceCandidate iceCandidate) {
            if (generation != offerGen.get() || iceCandidate == null) return;
            emitIce(mid, iceCandidate);
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
        }

        @Override
        public void onAddStream(MediaStream mediaStream) {
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {
        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {
            if (dataChannel == null) return;
            String label = dataChannel.label() != null ? dataChannel.label() : "";
            boolean isFrame = "evf".equalsIgnoreCase(label);
            Log.d(TAG, "onDataChannel label=" + label + " frame=" + isFrame);
            if (!isFrame) return;
            synchronized (sync) {
                frameChannel = dataChannel;
            }
            dataChannel.registerObserver(new DataChannel.Observer() {
                @Override
                public void onBufferedAmountChange(long l) {
                }

                @Override
                public void onStateChange() {
                    DataChannel.State st = dataChannel.state();
                    frameChannelOpen = st == DataChannel.State.OPEN;
                    Log.d(TAG, "frame channel state=" + st);
                    EvfMicSocketCapture.getInstance().syncCaptureState();
                }

                @Override
                public void onMessage(DataChannel.Buffer buffer) {
                }
            });
            if (dataChannel.state() == DataChannel.State.OPEN) {
                frameChannelOpen = true;
            }
        }

        @Override
        public void onRenegotiationNeeded() {
        }

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
        }
    }

    private static class SimpleSdpObserver implements SdpObserver {
        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {
        }

        @Override
        public void onSetSuccess() {
        }

        @Override
        public void onCreateFailure(String s) {
            Log.w(TAG, "SDP create fail: " + s);
        }

        @Override
        public void onSetFailure(String s) {
            Log.w(TAG, "SDP set fail: " + s);
        }
    }
}
