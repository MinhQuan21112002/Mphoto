package com.sdk.esc;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stream JPEG từ camera trước (hoặc TextureView đang preview) lên Control Page.
 * Không mở camera thứ 2 khi đang grab TextureView (tránh crash Mono).
 */
public final class FrontCameraEvfStreamer {
    private static final String TAG = "FrontCamEvf";
    private static final int DEFAULT_MAX_EDGE = 480;
    private static final int DEFAULT_JPEG_QUALITY = 50;
    private static final long DEFAULT_FRAME_INTERVAL_MS = 100L;

    private int targetMaxEdge = DEFAULT_MAX_EDGE;
    private int jpegQuality = DEFAULT_JPEG_QUALITY;
    private long minFrameIntervalMs = DEFAULT_FRAME_INTERVAL_MS;

    /** Extra xoay: Mono = 0 (giữ tỉ lệ ngang tablet). Lite ImageReader = 270 + sensor. */
    private int extraRotationDeg = 0;
    /** Selfie: Lite lật ngang; Mono giữ như màn tablet (không lật thêm). */
    private boolean mirrorX = false;
    /** Xoay theo SENSOR_ORIENTATION trước extra (cùng logic Lite ImageReader). */
    private boolean applySensorOrientation = false;
    /** Mono: crop cover ra 3:2 ngang (tablet landscape), không gửi JPEG dọc. */
    private boolean coverToLandscape32 = false;

    public interface FrameSink {
        void onJpegFrame(byte[] jpeg, long tsMs, long seq);
    }

    private final Context appContext;
    private final FrameSink sink;
    private final Handler mainHandler = new Handler(android.os.Looper.getMainLooper());
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean frameBusy = new AtomicBoolean(false);
    private final AtomicLong seq = new AtomicLong(0);
    private final AtomicLong lastEmitMs = new AtomicLong(0);
    private final ByteArrayOutputStream jpegBos = new ByteArrayOutputStream(48 * 1024);

    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private String cameraId;
    private TextureView textureView;
    private int sensorOrientation = 0;
    private Bitmap grabLayer;
    private final Matrix grabMatrix = new Matrix();
    private final Matrix grabInv = new Matrix();
    private final Paint grabPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

    private final Runnable textureGrabRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running.get()) return;
            try {
                grabFromTextureView();
            } catch (Throwable t) {
                Log.w(TAG, "texture grab: " + t.getMessage());
            }
            if (running.get()) {
                long delay = minFrameIntervalMs;
                mainHandler.postDelayed(this, Math.max(16L, delay));
            }
        }
    };

    public FrontCameraEvfStreamer(@NonNull Context context, @NonNull FrameSink sink) {
        this.appContext = context.getApplicationContext();
        this.sink = sink;
    }

    /** Cấu hình transform trước khi start. Mono: (0, false) giữ khung ngang, không lật gương. Lite: (270, true)+sensor. */
    public void setControlPageTransform(int extraRotationDegrees, boolean flipHorizontal) {
        this.extraRotationDeg = extraRotationDegrees;
        this.mirrorX = flipHorizontal;
    }

    public void setApplySensorOrientation(boolean apply) {
        this.applySensorOrientation = apply;
    }

    /** Crop fill khung 3:2 ngang — dùng khi tablet booth để ngang. */
    public void setCoverToLandscape32(boolean enable) {
        this.coverToLandscape32 = enable;
    }

    /** Giảm độ phân giải / chất lượng JPEG để live view mượt hơn (Lite). */
    public void setStreamProfile(int maxEdge, int jpegQ, long intervalMs) {
        this.targetMaxEdge = Math.max(240, maxEdge);
        this.jpegQuality = Math.max(25, Math.min(80, jpegQ));
        this.minFrameIntervalMs = Math.max(50L, intervalMs);
    }

    public boolean isRunning() {
        return running.get();
    }

    /** Grab preview đang có trên TextureView (Mono) — không mở Camera2 thêm. */
    public void startFromTextureView(TextureView view) {
        stopInternal(false);
        if (view == null) {
            Log.w(TAG, "startFromTextureView: null view — skip (không mở camera 2)");
            return;
        }
        textureView = view;
        running.set(true);
        mainHandler.removeCallbacks(textureGrabRunnable);
        mainHandler.post(textureGrabRunnable);
        Log.d(TAG, "startFromTextureView");
    }

    /** Lite / khi không có TextureView preview. */
    public void startFrontCamera() {
        stopInternal(false);
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "CAMERA permission missing");
            return;
        }
        running.set(true);
        cameraThread = new HandlerThread("FrontCamEvf");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
        cameraHandler.post(() -> {
            try {
                openFrontCamera();
            } catch (Throwable t) {
                Log.e(TAG, "openFrontCamera fatal", t);
                running.set(false);
            }
        });
        Log.d(TAG, "startFrontCamera");
    }

    public void stop() {
        stopInternal(true);
    }

    private void stopInternal(boolean clearTexture) {
        running.set(false);
        mainHandler.removeCallbacks(textureGrabRunnable);
        if (clearTexture) {
            textureView = null;
        }
        closeCamera();
        if (grabLayer != null) {
            safeRecycle(grabLayer, null);
            grabLayer = null;
        }
        HandlerThread thread = cameraThread;
        cameraThread = null;
        cameraHandler = null;
        if (thread != null) {
            thread.quitSafely();
            try {
                thread.join(400);
            } catch (InterruptedException ignored) {
            }
        }
    }

    private void grabFromTextureView() {
        TextureView tv = textureView;
        if (tv == null || !tv.isAvailable()) return;
        long now = System.currentTimeMillis();
        if (now - lastEmitMs.get() < minFrameIntervalMs) return;
        if (!frameBusy.compareAndSet(false, true)) return;

        Bitmap captured = null;
        try {
            captured = captureTextureViewAsDisplayed(tv, targetMaxEdge);
            if (captured == null || captured.getWidth() <= 0 || captured.getHeight() <= 0) return;
            byte[] jpeg = bitmapToJpeg(captured);
            if (jpeg == null || jpeg.length == 0) return;
            lastEmitMs.set(now);
            sink.onJpegFrame(jpeg, now, seq.incrementAndGet());
        } catch (Throwable e) {
            Log.w(TAG, "grabFromTextureView: " + e.getMessage());
        } finally {
            frameBusy.set(false);
            if (captured != null && captured != grabLayer) {
                safeRecycle(captured, grabLayer);
            }
        }
    }

    /**
     * Grab nhỏ theo tỉ lệ tablet + apply setTransform/lật một lần (không chụp full HD).
     */
    private Bitmap captureTextureViewAsDisplayed(TextureView tv, int maxEdge) {
        int vw = tv.getWidth();
        int vh = tv.getHeight();
        if (vw <= 0 || vh <= 0) return tv.getBitmap();

        int outW;
        int outH;
        if (vw >= vh) {
            outW = Math.min(vw, maxEdge);
            outH = Math.max(1, Math.round(outW * (vh / (float) vw)));
        } else {
            outH = Math.min(vh, maxEdge);
            outW = Math.max(1, Math.round(outH * (vw / (float) vh)));
        }

        Bitmap raw = tv.getBitmap(outW, outH);
        if (raw == null) return null;

        tv.getTransform(grabInv);
        boolean hasViewT = !grabInv.isIdentity();
        boolean needLayer = hasViewT || mirrorX || extraRotationDeg != 0;
        if (!needLayer) return raw;

        float sx = outW / (float) vw;
        float sy = outH / (float) vh;
        grabMatrix.reset();
        if (hasViewT && sx != 0f && sy != 0f) {
            grabMatrix.setScale(sx, sy);
            grabMatrix.preConcat(grabInv);
            grabInv.setScale(1f / sx, 1f / sy);
            grabMatrix.preConcat(grabInv);
        }
        if (mirrorX) {
            grabMatrix.postScale(-1f, 1f, outW / 2f, outH / 2f);
        }
        int d = ((extraRotationDeg % 360) + 360) % 360;
        if (d != 0) {
            grabMatrix.postRotate(d);
        }

        Bitmap layer = ensureGrabLayer(outW, outH);
        if (layer == null) return raw;
        Canvas canvas = new Canvas(layer);
        canvas.drawBitmap(raw, grabMatrix, grabPaint);
        safeRecycle(raw, layer);
        return layer;
    }

    private Bitmap ensureGrabLayer(int w, int h) {
        if (grabLayer != null && !grabLayer.isRecycled()
                && grabLayer.getWidth() == w && grabLayer.getHeight() == h) {
            return grabLayer;
        }
        if (grabLayer != null) {
            safeRecycle(grabLayer, null);
            grabLayer = null;
        }
        grabLayer = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        return grabLayer;
    }

    private void openFrontCamera() {
        if (!running.get()) return;
        CameraManager manager = (CameraManager) appContext.getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) return;
        try {
            cameraId = findFrontCameraId(manager);
            if (cameraId == null) {
                Log.w(TAG, "No front camera");
                return;
            }
            CameraCharacteristics chars = manager.getCameraCharacteristics(cameraId);
            Integer so = chars.get(CameraCharacteristics.SENSOR_ORIENTATION);
            sensorOrientation = so != null ? so : 0;
            Size preview = choosePreviewSize(chars);
            // YUV nhỏ — JPEG repeating không ổn định trên nhiều máy Android.
            imageReader = ImageReader.newInstance(
                    preview.getWidth(), preview.getHeight(), ImageFormat.YUV_420_888, 2);
            imageReader.setOnImageAvailableListener(reader -> {
                if (!running.get()) return;
                Image image = null;
                try {
                    image = reader.acquireLatestImage();
                    if (image == null) return;
                    long now = System.currentTimeMillis();
                    if (now - lastEmitMs.get() < minFrameIntervalMs) return;
                    if (!frameBusy.compareAndSet(false, true)) return;

                    Bitmap decoded = null;
                    Bitmap oriented = null;
                    Bitmap prepared = null;
                    try {
                        decoded = yuv420ToScaledBitmap(image, targetMaxEdge);
                        if (decoded == null) return;
                        Bitmap base = decoded;
                        if (applySensorOrientation && sensorOrientation != 0) {
                            oriented = rotateBitmap(decoded, sensorOrientation);
                            if (oriented != null) base = oriented;
                        }
                        prepared = prepareBitmapForControlPage(base);
                        byte[] jpeg = bitmapToJpeg(prepared);
                        if (jpeg == null || jpeg.length == 0) return;
                        lastEmitMs.set(now);
                        sink.onJpegFrame(jpeg, now, seq.incrementAndGet());
                    } finally {
                        frameBusy.set(false);
                        safeRecycle(prepared, oriented, decoded);
                        safeRecycle(oriented, decoded);
                        safeRecycle(decoded, null);
                    }
                } catch (Throwable e) {
                    Log.w(TAG, "onImageAvailable: " + e.getMessage());
                } finally {
                    if (image != null) {
                        try {
                            image.close();
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }, cameraHandler);

            if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    if (!running.get()) {
                        camera.close();
                        return;
                    }
                    cameraDevice = camera;
                    createSession();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    try {
                        camera.close();
                    } catch (Throwable ignored) {
                    }
                    cameraDevice = null;
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    Log.e(TAG, "camera error " + error);
                    try {
                        camera.close();
                    } catch (Throwable ignored) {
                    }
                    cameraDevice = null;
                }
            }, cameraHandler);
        } catch (SecurityException | CameraAccessException e) {
            Log.e(TAG, "openFrontCamera", e);
        } catch (Throwable t) {
            Log.e(TAG, "openFrontCamera unexpected", t);
        }
    }

    private void createSession() {
        if (cameraDevice == null || imageReader == null || !running.get()) return;
        try {
            Surface surface = imageReader.getSurface();
            final CaptureRequest.Builder builder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(surface);
            cameraDevice.createCaptureSession(Collections.singletonList(surface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            if (!running.get() || cameraDevice == null) {
                                try {
                                    session.close();
                                } catch (Throwable ignored) {
                                }
                                return;
                            }
                            captureSession = session;
                            try {
                                builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                                session.setRepeatingRequest(builder.build(), null, cameraHandler);
                            } catch (CameraAccessException | IllegalStateException e) {
                                Log.e(TAG, "setRepeatingRequest", e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e(TAG, "configure failed");
                        }
                    }, cameraHandler);
        } catch (CameraAccessException | IllegalStateException e) {
            Log.e(TAG, "createSession", e);
        }
    }

    private void closeCamera() {
        try {
            if (captureSession != null) {
                captureSession.close();
            }
        } catch (Throwable ignored) {
        }
        captureSession = null;
        try {
            if (cameraDevice != null) {
                cameraDevice.close();
            }
        } catch (Throwable ignored) {
        }
        cameraDevice = null;
        try {
            if (imageReader != null) {
                imageReader.close();
            }
        } catch (Throwable ignored) {
        }
        imageReader = null;
        cameraId = null;
    }

    private static String findFrontCameraId(CameraManager manager) throws CameraAccessException {
        for (String id : manager.getCameraIdList()) {
            CameraCharacteristics c = manager.getCameraCharacteristics(id);
            Integer facing = c.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                return id;
            }
        }
        String[] ids = manager.getCameraIdList();
        return ids.length > 0 ? ids[0] : null;
    }

    private Size choosePreviewSize(CameraCharacteristics chars) {
        android.hardware.camera2.params.StreamConfigurationMap map =
                chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size fallback = new Size(640, 480);
        if (map == null) return fallback;
        Size[] sizes = map.getOutputSizes(ImageFormat.YUV_420_888);
        if (sizes == null || sizes.length == 0) return fallback;
        Size best = fallback;
        int bestScore = Integer.MAX_VALUE;
        int prefer = targetMaxEdge;
        for (Size s : sizes) {
            int edge = Math.max(s.getWidth(), s.getHeight());
            if (edge > 800) continue; // tránh OOM / lag
            int score = Math.abs(edge - prefer);
            if (edge >= 320 && score < bestScore) {
                bestScore = score;
                best = s;
            }
        }
        return best;
    }

    /** Scale ngay trong lúc convert để tránh alloc bitmap full-res. */
    private static Bitmap yuv420ToScaledBitmap(Image image, int maxEdge) {
        if (image == null) return null;
        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= 0 || height <= 0) return null;
        int edge = Math.max(width, height);
        int step = edge > maxEdge ? Math.max(1, (int) Math.ceil(edge / (double) maxEdge)) : 1;
        int outW = Math.max(1, width / step);
        int outH = Math.max(1, height / step);

        Image.Plane[] planes = image.getPlanes();
        if (planes == null || planes.length < 3) return null;
        ByteBuffer yBuf = planes[0].getBuffer();
        ByteBuffer uBuf = planes[1].getBuffer();
        ByteBuffer vBuf = planes[2].getBuffer();
        int yRowStride = planes[0].getRowStride();
        int uvRowStride = planes[1].getRowStride();
        int uvPixelStride = planes[1].getPixelStride();

        int[] argb = new int[outW * outH];
        for (int row = 0; row < outH; row++) {
            int srcRow = row * step;
            int yRow = srcRow * yRowStride;
            int uvRow = (srcRow >> 1) * uvRowStride;
            for (int col = 0; col < outW; col++) {
                int srcCol = col * step;
                int y = yBuf.get(Math.min(yRow + srcCol, yBuf.limit() - 1)) & 0xff;
                int uvIndex = uvRow + (srcCol >> 1) * uvPixelStride;
                int u = uBuf.get(Math.min(uvIndex, uBuf.limit() - 1)) & 0xff;
                int v = vBuf.get(Math.min(uvIndex, vBuf.limit() - 1)) & 0xff;
                int c = y - 16;
                if (c < 0) c = 0;
                int d = u - 128;
                int e = v - 128;
                int r = (298 * c + 409 * e + 128) >> 8;
                int g = (298 * c - 100 * d - 208 * e + 128) >> 8;
                int b = (298 * c + 516 * d + 128) >> 8;
                if (r < 0) r = 0; else if (r > 255) r = 255;
                if (g < 0) g = 0; else if (g > 255) g = 255;
                if (b < 0) b = 0; else if (b > 255) b = 255;
                argb[row * outW + col] = 0xff000000 | (r << 16) | (g << 8) | b;
            }
        }
        return Bitmap.createBitmap(argb, outW, outH, Bitmap.Config.ARGB_8888);
    }

    private Bitmap prepareBitmapForControlPage(Bitmap src) {
        if (src == null || src.isRecycled()) return null;
        Bitmap scaled = scaleBitmap(src, targetMaxEdge);
        if (scaled == null) return null;
        Bitmap transformed = transformBitmap(scaled, extraRotationDeg, mirrorX);
        if (scaled != src && scaled != transformed) {
            safeRecycle(scaled, null);
        }
        if (transformed == null) return null;
        if (!coverToLandscape32) return transformed;
        Bitmap covered = coverCropToAspect(transformed, 3f / 2f);
        if (covered != null && covered != transformed && transformed != src) {
            safeRecycle(transformed, null);
        }
        return covered != null ? covered : transformed;
    }

    /** Center-crop như object-fit: cover vào tỉ lệ đích (3:2 ngang). */
    private static Bitmap coverCropToAspect(Bitmap src, float targetAspect) {
        if (src == null || src.isRecycled()) return null;
        int sw = src.getWidth();
        int sh = src.getHeight();
        if (sw <= 0 || sh <= 0) return src;
        float srcAspect = sw / (float) sh;
        int cropW;
        int cropH;
        int x;
        int y;
        if (srcAspect > targetAspect) {
            cropH = sh;
            cropW = Math.max(1, Math.round(sh * targetAspect));
            x = Math.max(0, (sw - cropW) / 2);
            y = 0;
        } else {
            cropW = sw;
            cropH = Math.max(1, Math.round(sw / targetAspect));
            x = 0;
            y = Math.max(0, (sh - cropH) / 2);
        }
        if (cropW > sw - x) cropW = sw - x;
        if (cropH > sh - y) cropH = sh - y;
        if (cropW <= 0 || cropH <= 0) return src;
        if (cropW == sw && cropH == sh) return src;
        return Bitmap.createBitmap(src, x, y, cropW, cropH);
    }

    private static Bitmap scaleBitmap(Bitmap src, int maxEdge) {
        if (src == null || src.isRecycled()) return null;
        int w = src.getWidth();
        int h = src.getHeight();
        int edge = Math.max(w, h);
        if (edge <= maxEdge) return src;
        float scale = maxEdge / (float) edge;
        int nw = Math.max(1, Math.round(w * scale));
        int nh = Math.max(1, Math.round(h * scale));
        return Bitmap.createScaledBitmap(src, nw, nh, true);
    }

    private static Bitmap transformBitmap(Bitmap src, int degrees, boolean flipX) {
        if (src == null || src.isRecycled()) return null;
        int d = ((degrees % 360) + 360) % 360;
        if (d == 0 && !flipX) return src;
        // Xoay quanh (0,0) — pivot giữa làm 90°/270° ra bitmap vuông/ép ngang.
        Matrix m = new Matrix();
        if (flipX) {
            m.postScale(-1f, 1f);
        }
        if (d != 0) {
            m.postRotate(d);
        }
        return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
    }

    private static Bitmap rotateBitmap(Bitmap src, int degrees) {
        return transformBitmap(src, degrees, false);
    }

    private byte[] bitmapToJpeg(Bitmap bmp) {
        if (bmp == null || bmp.isRecycled()) return null;
        synchronized (jpegBos) {
            jpegBos.reset();
            if (!bmp.compress(Bitmap.CompressFormat.JPEG, jpegQuality, jpegBos)) return null;
            return jpegBos.toByteArray();
        }
    }

    private static void safeRecycle(Bitmap b, Bitmap... keep) {
        if (b == null || b.isRecycled()) return;
        if (keep != null) {
            for (Bitmap k : keep) {
                if (k != null && b == k) return;
            }
        }
        try {
            b.recycle();
        } catch (Throwable ignored) {
        }
    }
}
