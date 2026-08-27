package com.sdk.esc;

import com.mphoto.mono.R;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import print.Print;
import android.media.MediaPlayer;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.opencv.android.OpenCVLoader;

public class Activity_Camera2 extends AppCompatActivity implements ControlPageCommandHost {
    File file;
    ImageSolve imgSolve;
    private MediaPlayer countdownSound;
    private MediaPlayer shutterSound;
    private int counterTime=1;
    Bitmap image = null;
    LocalDateTime current;
    private CameraDevice cameraDevice;
    private CaptureRequest.Builder captureRequestBuilder;
    private static final String TAG = "AndroidCameraApi";
    final private ExecutorService executorService = Executors.newSingleThreadExecutor();
    public Handler handler;
    // Button cho capture ảnh
    private ImageView clickButton;
    private ObjectAnimator clickButtonBlinkAnimator;
    private final Runnable deferredOpenCameraRunnable = this::openCameraIfReady;
    // preview camera
    private TextureView textureView;
    private ImageView imgFrame;
    private  int ISOvalue=400;
    private  long ExpoValue= 30000000;
    private final int PRINT_FAILURE = 0;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private static final int REQUEST_STORAGE_PERMISSION = 201;
    /** Luồng riêng cho upload server — không dùng chung queue in máy in. */
    private final ExecutorService uploadExecutorService = Executors.newSingleThreadExecutor();
    private UsbDevice device = null;
    private PendingIntent mPermissionIntent = null;
    private static final String ACTION_USB_PERMISSION = "com.PRINTSDKSample";
    // kiểm tra trạng thái  ORIENTATION của ảnh đầu ra
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    protected CameraCaptureSession cameraCaptureSessions;
    private android.util.Size imageDimension;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    TextView countdown;
    boolean havingUsb=false;
    int currentIndex;
    List<String> bitmapList;
    private int clickCount = 0;
    private int hideClickCount = 0;

    private GestureDetector gestureDetector;
    List<String> bitmapListImageView2;
    int currentIndexImageView2;
    private void changeLanguageFirst(String langCode) {
        Locale locale = new Locale(langCode);
        Locale.setDefault(locale);

        Configuration config = new Configuration();
        config.setLocale(locale);
        getBaseContext().getResources().updateConfiguration(config, getBaseContext().getResources().getDisplayMetrics());

        // Optional: Lưu lại
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("language", langCode);
        editor.apply();

    }
    @SuppressLint("ClickableViewAccessibility")
    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        String lang = prefs.getString("language", "vi");
        changeLanguageFirst(lang); // ✅ Gọi trước super.onCreate()
        super.onCreate(savedInstanceState);

        TokenManager tokenManager = TokenManager.getInstance(this);
        if (!tokenManager.canEnterApp()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // Bắt buộc All files access — chưa bật thì chặn app (dialog không tắt được)
        MachineManager.getInstance(this).enforceDurableStorageAccessRequired(this);

        MonoGalleryCleanup.runInBackground(this);

        if (OpenCVLoader.initDebug()) {
            Log.d("OpenCV", "OpenCV successfully loaded!");
        } else {
            Log.d("OpenCV", "OpenCV loading failed.");
        }
// OpenCV 네이티브 라이브러리 로드
        if (!OpenCVLoader.initDebug()) {
            throw new RuntimeException("OpenCV initialization failed!");
        }
        // imgSolve 초기화
        imgSolve = new ImageSolve(this);
        reloadIsoExposureFromPrefs();

        //xu ly anh phu -------------------------------------------------------------
        SharedPreferences sharedPreferences2 = getSharedPreferences("MyAppPrefs2", MODE_PRIVATE);
        currentIndexImageView2=sharedPreferences2.getInt("indexImageView2", 0);
        String jsonStringImageview2 = sharedPreferences2.getString("ImageViewList", "[]");
        Gson gson1 = new Gson();
        bitmapListImageView2 = gson1.fromJson(jsonStringImageview2, new TypeToken<List<String>>() {}.getType());

        if (tokenManager.canUseCloudFeatures()) {
            GoogleDriveService googleDriveService = new GoogleDriveService(this);
            googleDriveService.createOrGetSubFolder(this).thenAccept(folderId -> {
                if (folderId != null) {
                    Log.d("MainActivity", "Folder NameCard đã sẵn sàng với ID: " + folderId);
                } else {
                    Log.e("MainActivity", "Lỗi khi tạo hoặc lấy thư mục NameCard.");
                }
            });
            // Device Manager: đăng ký máy + join socket (android / mono)
            connectMachinePresenceForDeviceManager(tokenManager.getToken());
            final String tokenSync = tokenManager.getToken();
            new Thread(() ->
                    GalleryUploadMethodService.getInstance(Activity_Camera2.this).syncFromServer(tokenSync)
            ).start();
        }
        counterTime = getSharedPreferences("MyAppPrefs", MODE_PRIVATE).getInt("counterTime", 1);
        if (!bitmapListImageView2.isEmpty()) {
            image = UserAssetFileStore.decodeListEntryToBitmap(this,
                bitmapListImageView2.get(currentIndexImageView2));
        }
        if (image == null) {
            image = BitmapFactory.decodeResource(getResources(), R.drawable.bottom);
        }

        setContentView(R.layout.activity_camera2);
        Intent intent = new Intent(ACTION_USB_PERMISSION);
        intent.setPackage(Activity_Camera2.this.getPackageName());
        IntentFilter filter = new IntentFilter();
        mPermissionIntent = PendingIntent.getBroadcast(Activity_Camera2.this, 0, intent, PendingIntent.FLAG_MUTABLE);
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        // Kiểm tra thiết bị USB đã kết nối ngay khi ứng dụng khởi động
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        if (!deviceList.isEmpty()) {
            for (UsbDevice usbDevice : deviceList.values()) {
                // Kiểm tra nếu có thiết bị USB đã kết nối
                if (usbDevice.getInterfaceCount() > 0) {
                    havingUsb = true;
                }
            }
        }
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(ACTION_USB_PERMISSION);
        Activity_Camera2.this.registerReceiver(mUsbReceiver, filter, RECEIVER_EXPORTED);
        clickButton = findViewById(R.id.idClick);
        textureView = findViewById(R.id.texture);
        imgFrame=findViewById(R.id.idIVLogo);
        ImageButton settingButton = findViewById(R.id.button_setting_change);
        ImageButton hideLogoButton = findViewById(R.id.button_hide_logo);
        countdown= findViewById(R.id.countdownText);
        countdown.setVisibility(View.INVISIBLE);
        assert textureView != null;
        textureView.setSurfaceTextureListener(textureListener);
        applyClickButtonHiddenState();

        gestureDetector = new GestureDetector(this, new GestureListener());
        textureView.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));
        textureView.setOnClickListener(v -> {
        });

        clickButton.setOnClickListener(v -> {

            if (!Print.IsOpened() && !PrinterTestMode.isEnabled(this)) {
                Toast.makeText(Activity_Camera2.this, getString(R.string.please_connect_printer), Toast.LENGTH_SHORT).show();
                try {
                    if(havingUsb)
                    {
                        connectUSB();
                    }
                } catch (NumberFormatException e) {
                    Toast.makeText(getApplicationContext(), "Can't find Printer", Toast.LENGTH_SHORT).show();
                }

            }
            else {
                startCountdown();
                textureView.setEnabled(false);
                clickButton.setEnabled(false);
                stopClickButtonBlink();
                clickButton.setVisibility(View.INVISIBLE);
            }
        });

        settingButton.setOnClickListener(v -> {
            clickCount++;

            if (clickCount == 3) {
                openManualActivity();
                clickCount = 0;
            } else {
                // Reset click count after a short delay or you can reset immediately
                v.postDelayed(() -> clickCount = 0, 500); // Hoặc bạn có thể điều chỉnh thời gian delay
            }
        });
        hideLogoButton.setOnClickListener(v -> {
            hideClickCount++;
            if (hideClickCount >= 3) {
                // Dùng pref, không dùng getAlpha() — alpha đang bị animator nhấp nháy
                setClickButtonHidden(!isClickButtonHiddenPref());
                hideClickCount = 0;
            } else {
                v.postDelayed(() -> hideClickCount = 0, 500);
            }
        });



        SharedPreferences preferences = getSharedPreferences("FrameImage", Context.MODE_PRIVATE);
        String jsonString = preferences.getString("bitmap_list", "[]");
        Gson gson = new Gson();
        bitmapList = gson.fromJson(jsonString, new TypeToken<List<String>>() {}.getType());
        currentIndex= preferences.getInt("current_index", 0);


        updateImageView(currentIndex);

    }
    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            assert e1 != null;
            float diffX = e2.getX() - e1.getX();
            if (Math.abs(diffX) > Math.abs(e2.getY() - e1.getY())) {
                // Kiểm tra swipe sang trái hoặc phải
                if (diffX > 0) {
                    // Swipe sang phải
                    onSwipeRight();
                } else {
                    // Swipe sang trái
                    onSwipeLeft();
                }
                return true;
            }
            return false;
        }

    }

    // Xử lý swipe sang phải
    private void onSwipeRight() {
        SharedPreferences preferences = getSharedPreferences("FrameImage", Context.MODE_PRIVATE);
        String jsonString2 = preferences.getString("bitmap_list", "[]");
        Gson gson2 = new Gson();
        bitmapList = gson2.fromJson(jsonString2, new TypeToken<List<String>>() {}.getType());
        if (currentIndex > 0) { // Không nhỏ hơn 0
            currentIndex--;
            updateImageView(currentIndex);
            saveCurrentIndex(currentIndex);
        }
    }

    // Xử lý swipe sang trái
    private void onSwipeLeft() {
        SharedPreferences preferences = getSharedPreferences("FrameImage", Context.MODE_PRIVATE);
        String jsonString2 = preferences.getString("bitmap_list", "[]");
        Gson gson2 = new Gson();
        bitmapList = gson2.fromJson(jsonString2, new TypeToken<List<String>>() {}.getType());
        if (currentIndex < bitmapList.size() - 1) { // Không vượt quá danh sách
            currentIndex++;
            updateImageView(currentIndex);
            saveCurrentIndex(currentIndex);
        }
    }
    private void saveCurrentIndex(int index) {
        SharedPreferences preferences = getSharedPreferences("FrameImage", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("current_index", index); // Lưu index hiện tại
        editor.apply(); // Áp dụng thay đổi
    }
    private void updateImageView(int index) {
        if (bitmapList != null && index < bitmapList.size()) {
            Bitmap bitmap = UserAssetFileStore.decodeListEntryToBitmap(this, bitmapList.get(index));
            if (bitmap == null) {
                return;
            }

            // Giống Manual: khung 3:4 dọc, giữ alpha để thấy live view ở giữa
            int originalWidth = bitmap.getWidth();
            int targetHeight = (int) (originalWidth * (3.0 / 4.0));

            Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, originalWidth, targetHeight, true);
            imgFrame.setImageBitmap(resizedBitmap);
        }
    }

    private void startCountdown() {
        // Hiển thị TextView countdown
        countdown.setVisibility(View.VISIBLE);
        countdownSound = MediaPlayer.create(this, R.raw.countdown);
        shutterSound = MediaPlayer.create(this, R.raw.shutter);
        try {
            SocketService.getInstance().emitCaptureCountdown(3, 3);
        } catch (Exception ignored) {
        }

        new CountDownTimer(3000, 1000) {

            @Override
            public void onTick(long millisUntilFinished) {
                int secondsRemaining = (int) millisUntilFinished / 1000;
                int show = secondsRemaining + 1;
                try {
                    SocketService.getInstance().emitCaptureCountdown(show, 3);
                } catch (Exception ignored) {
                }

                switch (show) {
                    case 3:
                        countdownSound = MediaPlayer.create(Activity_Camera2.this, R.raw.countdown);
                        countdownSound.start();
                        countdown.setText("3");
                        break;
                    case 2:
                        countdownSound = MediaPlayer.create(Activity_Camera2.this, R.raw.countdown);
                        countdownSound.start();
                        countdown.setText("2");
                        break;
                    case 1:
                        countdownSound = MediaPlayer.create(Activity_Camera2.this, R.raw.countdown);
                        countdownSound.start();
                        countdown.setText("1");
                        break;
                }
            }

            @Override
            public void onFinish() {
                try {
                    SocketService.getInstance().emitCaptureCountdown(0, 3);
                } catch (Exception ignored) {
                }
                takePicture();
                shutterSound.start();
                countdown.setVisibility(View.GONE);
            }
        }.start();
    }


    private void connectUSB() {
        UsbManager mUsbManager = (UsbManager) Activity_Camera2.this.getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();

        boolean HavePrinter = false;
        while (deviceIterator.hasNext()) {
            device = deviceIterator.next();
            int count = device.getInterfaceCount();
            for (int i = 0; i < count; i++) {
                UsbInterface intf = device.getInterface(i);
                if (intf.getInterfaceClass() == 7) {
                    HavePrinter = true;
                    //Toast.makeText(thisCon, "vao roi", Toast.LENGTH_SHORT).show();
                    if (mPermissionIntent != null) {
                        Log.d("PRINT_TAG", "vendorID--" + device.getVendorId() + " ProductId--" + device.getProductId());
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            mUsbManager.requestPermission(device, mPermissionIntent);
                        }
                    }

                    // Lấy usbEndpointOut từ device
                    for (int j = 0; j < intf.getEndpointCount(); j++) {
                        UsbEndpoint endpoint = intf.getEndpoint(j);
                        if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                            break;
                        }
                    }
                }
            }
        }
        if (!HavePrinter) {
            Log.d("PRINT_TAG", "vendorID--" + device.getVendorId() + " ProductId--" + device.getProductId());

        }
    }
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            try {
                String action = intent.getAction();
                Log.d("TAG", "action: " + action);
                if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                    device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (device != null) {
                        havingUsb=true;
                        connectUSB(); // Tự động gọi connectUSB khi USB được kết nối
                    }

                }
                if (ACTION_USB_PERMISSION.equals(action)) {
                    synchronized (this) {
                        device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            if (Print.PortOpen(Activity_Camera2.this, device) != 0) {
                                Toast.makeText(Activity_Camera2.this, "Lỗi khi mở cổng", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(Activity_Camera2.this, "Kết nối thành công", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(Activity_Camera2.this, "Quyền bị từ chối", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
                if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                    device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (device != null) {
                        int count = device.getInterfaceCount();
                        for (int i = 0; i < count; i++) {
                            UsbInterface intf = device.getInterface(i);
                            if (intf.getInterfaceClass() == 7) {
                                Print.PortClose();
                            }
                        }

                        havingUsb=false;
                    }
                }
                SocketService.getInstance().notifyControlPageSettingsChanged();
            } catch (Exception e) {
                Log.e("SDKSample", "Activity_Main --> mUsbReceiver: " + e.getMessage());
            }
        }
    };
    private void imageProcessing(String path) {

        Bitmap origin = BitmapFactory.decodeFile(path);
        int dpi = origin.getDensity();
        if (dpi == 0) dpi = 203; // 기본 DPI 설정
        Bitmap resizedBitmap = imgSolve.processingImage(origin);
        AtomicReference<Bitmap> bmp = new AtomicReference<>(imgSolve.applySharpening(resizedBitmap, 1.2f));
        bmp.get().setDensity(dpi);

        int light = 10;
        int[] lightValue1 = {light}; // Adjust brightness based on SeekBar progress
        float contrast = 1.5f;
        float[] contrastValue = {contrast};
        Bitmap[] adjustedBitmap2 = {null};


        adjustedBitmap2[0] = imgSolve.adjustBrightness(bmp.get(), lightValue1[0]);
        adjustedBitmap2[0] = imgSolve.adjustContrast(adjustedBitmap2[0], contrast);
        adjustedBitmap2[0].setDensity(origin.getDensity());

        @SuppressLint("UseCompatLoadingForDrawables")
        Drawable noel = getResources().getDrawable(R.drawable.nothing, null);
        Bitmap bitmapFrameNull = imgSolve.drawableToBitmap(noel);

        Bitmap bitmapFrame;
// Kiểm tra xem bitmapList có phần tử không trước khi lấy currentIndex
        if (bitmapList != null && !bitmapList.isEmpty()) {
            String entry = bitmapList.get(currentIndex);
            if (entry == null) {
                bitmapFrame = bitmapFrameNull; // Set bitmapFrameNull nếu null
            } else {
                bitmapFrame = UserAssetFileStore.decodeListEntryToBitmap(this, entry);
                if (bitmapFrame == null) {
                    bitmapFrame = bitmapFrameNull;
                }
            }
        } else {
            bitmapFrame = bitmapFrameNull; // Set bitmapFrameNull nếu bitmapList trống
        }
        // Khung giữ màu (lưu file); bản trắng-đen chỉ dùng khi in — 1152 ≈ 2× độ rộng in dither 576
        bitmapFrame = imgSolve.resizeBitmapMaintainAspect(bitmapFrame, 1152);
// Bitmap đã xử lý (adjustedBitmap2[0])
        Bitmap processedBitmap2 = adjustedBitmap2[0];
        processedBitmap2 = imgSolve.resizeBitmapMaintainAspect(processedBitmap2,1152); // Nếu cần chuyển thành grayscale

// Tạo Bitmap mới để kết hợp
        // Tính toán kích thước mới cho processedBitmap2
        int newWidth = processedBitmap2.getWidth();
        int newHeight = processedBitmap2.getHeight();
        int compensation=0;
// Phóng to processedBitmap2
        Bitmap enlargedBitmap = Bitmap.createScaledBitmap(processedBitmap2, newWidth+compensation, newHeight+compensation, true);
        // Tạo một đối tượng Matrix
        Matrix matrix = new Matrix();

// Lật ngang (hoặc thay đổi scale để lật theo hướng mong muốn)
        matrix.preScale(-1, 1); // Lật ngang
// Nếu muốn lật dọc: matrix.preScale(1, -1);

// Áp dụng Matrix để tạo Bitmap mới
        Bitmap flippedBitmap = Bitmap.createBitmap(enlargedBitmap, 0, 0,
                enlargedBitmap.getWidth(), enlargedBitmap.getHeight(), matrix, true);

        // Upload/gallery: ảnh thô gốc (không sharpen/sáng/tương phản) + vẫn lật ngang cho khớp khung
        Bitmap flippedColor = Utility.buildRawFlippedForUpload(origin, newWidth, newHeight, compensation, matrix);
        if (flippedColor == null) {
            flippedColor = flippedBitmap;
        }

        Bitmap frameBw = imgSolve.convertToGrayscale(bitmapFrame);
        Bitmap newFrameBitmap = Bitmap.createScaledBitmap(frameBw, newWidth, newHeight, true);
        Bitmap newFrameBitmapColor = Bitmap.createScaledBitmap(bitmapFrame, newWidth, newHeight, true);
        frameBw.recycle();

        // compensation=70 android 14, compensation=0 android 11
// Tạo Bitmap mới để kết hợp
        Bitmap combinedBitmap = Bitmap.createBitmap(
                processedBitmap2.getWidth()+compensation,
                processedBitmap2.getHeight()+compensation,
                Bitmap.Config.ARGB_8888
        );

        Bitmap combinedBitmapColor = Bitmap.createBitmap(
                processedBitmap2.getWidth() + compensation,
                processedBitmap2.getHeight() + compensation,
                Bitmap.Config.ARGB_8888
        );

// Vẽ bitmapFrame lên Canvas
        Canvas canvas = new Canvas(combinedBitmap);
        canvas.drawBitmap(flippedBitmap, 0, 0, null);

        canvas.drawBitmap(newFrameBitmap, 0, 0, null);

        Canvas canvasColor = new Canvas(combinedBitmapColor);
        canvasColor.drawBitmap(flippedColor, 0, 0, null);
        canvasColor.drawBitmap(newFrameBitmapColor, 0, 0, null);
        if (flippedColor != null && flippedColor != flippedBitmap) {
            flippedColor.recycle();
        }
        newFrameBitmapColor.recycle();
        combinedBitmapColor.setDensity(bitmapFrame.getDensity());

        if (PrinterTestMode.isEnabled(this)) {
            try {
                // Cùng folderId cho local + server (tránh lệch tên)
                final String monoFolderName = MonoGalleryFolderIds.generate(this);
                final String monoLocalPhotoName = MonoGalleryFolderIds.localPhotoFileName(monoFolderName);
                Bitmap fullPage = Utility.buildVerticalStackForPrintWidth(combinedBitmapColor, image, 576);
                if (fullPage == null) {
                    fullPage = combinedBitmapColor != null ? combinedBitmapColor : combinedBitmap;
                }
                MonoGallerySaver.saveBitmapToMonoFolder(this, fullPage, monoLocalPhotoName);
                File tmp = PrinterTestMode.writeJpegToCacheDir(this, fullPage, monoLocalPhotoName);
                if (fullPage != null && fullPage != combinedBitmap && fullPage != combinedBitmapColor) {
                    fullPage.recycle();
                }
                if (combinedBitmapColor != null) {
                    combinedBitmapColor.recycle();
                }
                if (isShowQrEnabled()) {
                    printMonoDriveQrForUploadedFileLink(buildMonoServerGalleryUrl(monoFolderName));
                }
                scheduleServerUpload(tmp, monoFolderName);
                imgSolve.clearCache();
                enableCaptureControls();
            } catch (Exception e) {
                Log.e(TAG, "Test mode: save/upload", e);
                imgSolve.clearCache();
                enableCaptureControls();
            }
            counterTime++;
            SharedPreferences preferences2t = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE);
            preferences2t.edit().putInt("counterTime", counterTime).apply();
            return;
        }
        //adjustedBitmap2[0]=imgSolve.applyMedianFilter(adjustedBitmap2[0],3);
        int PRINT_THREE_INCH = 576;
        final String monoFolderName = MonoGalleryFolderIds.generate(this);
        final String monoLocalPhotoName = MonoGalleryFolderIds.localPhotoFileName(monoFolderName);
        Bitmap fullPageForFile = Utility.buildVerticalStackForPrintWidth(combinedBitmapColor, image, PRINT_THREE_INCH);
        try {
            if (fullPageForFile != null) {
                MonoGallerySaver.saveBitmapToMonoFolder(this, fullPageForFile, monoLocalPhotoName);
            } else {
                Bitmap src = combinedBitmapColor != null ? combinedBitmapColor : combinedBitmap;
                if (src != null) {
                    MonoGallerySaver.saveBitmapToMonoFolder(this, src, monoLocalPhotoName);
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "save gallery: " + e.getMessage());
        }
        File combinedFileForDrive = null;
        String combinedNameForDrive = null;
        try {
            if (fullPageForFile != null) {
                combinedNameForDrive = monoLocalPhotoName;
                combinedFileForDrive = PrinterTestMode.writeJpegToCacheDir(
                        Activity_Camera2.this, fullPageForFile, combinedNameForDrive);
            }
        } catch (Exception e) {
            Log.e(TAG, "Ghi ảnh đã ghép (khung+phụ) cho Drive", e);
        }
        if (fullPageForFile != null) {
            fullPageForFile.recycle();
        }
        if (combinedBitmapColor != null) {
            combinedBitmapColor.recycle();
        }
        PrintNumber();
        final int printMode = PrintBitmapMode.get(this);
        printImage(
                combinedBitmap,
                0,
                PRINT_THREE_INCH,
                false,
                printMode
        );

        printImage2(image, 0, 576, false, printMode);
        final File fCombinedDrive = combinedFileForDrive;
        final boolean showQr = isShowQrEnabled();
        if (showQr) {
            if (fCombinedDrive != null && fCombinedDrive.exists()) {
                printMonoDriveQrForUploadedFileLink(buildMonoServerGalleryUrl(monoFolderName));
            } else {
                printMonoDriveQrForUploadedFileLink(null);
            }
        } else {
            Bitmap bitmapPrint = BitmapFactory.decodeResource(Activity_Camera2.this.getResources(), R.drawable.end);
            printEmptyAndCut(0, 150, false, printMode, bitmapPrint);
        }
        scheduleServerUpload(fCombinedDrive, monoFolderName);
        imgSolve.clearCache();
        enableCaptureControls();
//        imgSolve.clearCache();
        counterTime++;
        SharedPreferences preferences2 = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences2.edit();
        editor.putInt("counterTime", counterTime);
        editor.apply();


    }

    /** Switch "In mã QR" — chỉ khi đã đăng nhập (có upload server). */
    private boolean isShowQrEnabled() {
        if (!TokenManager.getInstance(this).canUseCloudFeatures()) {
            return false;
        }
        return getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("Download", false);
    }

    private void enableCaptureControls() {
        runOnUiThread(() -> {
            if (textureView != null) {
                textureView.setEnabled(true);
            }
            if (clickButton != null) {
                clickButton.setEnabled(true);
                clickButton.setVisibility(View.VISIBLE);
                applyClickButtonHiddenState();
            }
        });
    }

    private void openManualActivity() {
        MonoScreenSwitch.mark();
        Intent intent = new Intent(this, Activity_Camera2_Manual.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        android.app.ActivityOptions options = android.app.ActivityOptions.makeCustomAnimation(
                this, R.anim.mono_activity_enter, R.anim.mono_activity_exit);
        startActivity(intent, options.toBundle());
    }

    private boolean isClickButtonHiddenPref() {
        return getSharedPreferences("settings", MODE_PRIVATE).getBoolean("click_button_hidden", false);
    }

    private void setClickButtonHidden(boolean hidden) {
        getSharedPreferences("settings", MODE_PRIVATE)
                .edit()
                .putBoolean("click_button_hidden", hidden)
                .apply();
        applyClickButtonHiddenState();
    }

    private void applyClickButtonHiddenState() {
        if (clickButton == null) {
            return;
        }
        if (isClickButtonHiddenPref()) {
            stopClickButtonBlink();
            clickButton.setAlpha(0f);
        } else {
            clickButton.setAlpha(1f);
            startClickButtonBlink();
        }
    }

    private void startClickButtonBlink() {
        if (clickButton == null || isClickButtonHiddenPref()) {
            return;
        }
        if (clickButton.getVisibility() != View.VISIBLE || !clickButton.isEnabled()) {
            return;
        }
        if (clickButtonBlinkAnimator != null && clickButtonBlinkAnimator.isRunning()) {
            return;
        }
        stopClickButtonBlink();
        clickButton.setAlpha(1f);
        clickButtonBlinkAnimator = ObjectAnimator.ofFloat(clickButton, View.ALPHA, 1f, 0.35f);
        clickButtonBlinkAnimator.setDuration(700);
        clickButtonBlinkAnimator.setRepeatMode(ValueAnimator.REVERSE);
        clickButtonBlinkAnimator.setRepeatCount(ValueAnimator.INFINITE);
        clickButtonBlinkAnimator.start();
    }

    private void stopClickButtonBlink() {
        if (clickButtonBlinkAnimator != null) {
            clickButtonBlinkAnimator.cancel();
            clickButtonBlinkAnimator = null;
        }
    }

    /** Upload server trên luồng riêng — không chặn hàng đợi in. */
    private void scheduleServerUpload(@Nullable File uploadFile, String folderName) {
        if (!TokenManager.getInstance(this).canUseCloudFeatures()) {
            return;
        }
        if (uploadFile == null || !uploadFile.exists()) {
            return;
        }
        final File fileToUpload = uploadFile;
        final String folderId = folderName;
        uploadExecutorService.execute(() -> {
            try {
                String token = TokenManager.getInstance(Activity_Camera2.this).getToken();
                if (token == null || token.isEmpty()) {
                    Log.e(TAG, "Upload Mono server: thiếu token");
                    return;
                }
                org.json.JSONObject uploadRes = ApiService.uploadMonoGalleryPhoto(
                        Activity_Camera2.this, token, folderId, fileToUpload, "1.jpg");
                Log.d(TAG, "Upload Mono server OK: " + uploadRes.optString("folderId", folderId));
            } catch (Exception e) {
                Log.e(TAG, "Upload Mono server lỗi", e);
            }
        });
    }

    private void printMonoDriveQrForUploadedFileLink(@Nullable String driveFileLink) {
        final int printMode = PrintBitmapMode.get(this);
        if (driveFileLink == null || driveFileLink.isEmpty()) {
            Log.e(TAG, "Không có link file Drive — in kết thúc, bỏ QR.");
            Bitmap bitmapPrint = BitmapFactory.decodeResource(getResources(), R.drawable.end);
            printEmptyAndCut(0, 150, false, printMode, bitmapPrint);
            return;
        }
        GoogleDriveService driveService = new GoogleDriveService(this);
        Bitmap qr = driveService.generateQRCodeForUrl(driveFileLink);
        if (qr != null) {
            Log.d(TAG, "QR từ link file: " + driveFileLink);
            printQR(qr, 0, 140, true, PrintBitmapMode.THRESHOLD);
            imgSolve.generateQRCode("https://maps.app.goo.gl/BrvtyEMcy8gPFq939", 500);
            Bitmap end = BitmapFactory.decodeResource(getResources(), R.drawable.end);
            printEmptyAndCut(0, 150, false, printMode, end);
        } else {
            Log.e(TAG, "Không tạo được QR từ link file");
            Bitmap end = BitmapFactory.decodeResource(getResources(), R.drawable.end);
            printEmptyAndCut(0, 150, false, printMode, end);
        }
    }

    private String buildMonoServerGalleryUrl(String folderName) {
        return ApiService.getApiBaseUrl() + "/mono-results/g/" + folderName;
    }

    public void printQR(final Bitmap bitmap, final int light, final int size,
                        final boolean haveWifi, final int sype) {
        executorService.execute(() -> {
            try {
                Bitmap bitmapPrint = bitmap;

                // Lấy ảnh từ drawable
                Bitmap imageBitmap =null;
                // Xoay QR code nếu cần
                if (haveWifi) {
                    SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
                    String lang = prefs.getString("language", "vi");
                    if (isShowQrEnabled())
                    {
                        if(lang.equals("vi"))
                        {
                            imageBitmap = BitmapFactory.decodeResource(Activity_Camera2.this.getResources(), R.drawable.getimage);
                        }
                        if(lang.equals("en"))
                        {
                            imageBitmap = BitmapFactory.decodeResource(Activity_Camera2.this.getResources(), R.drawable.getimageeng);
                        }
                        if(lang.equals("ko"))
                        {
                            imageBitmap = BitmapFactory.decodeResource(Activity_Camera2.this.getResources(), R.drawable.getimagekor);
                        }
                    }

                }
                else {
                    imageBitmap =null;
                }

                // Điều chỉnh kích thước QR code nếu cần
                if (size != 0) {
                    int newHeight = Utility.getHeight(size, bitmapPrint.getWidth(), bitmapPrint.getHeight());
                    bitmapPrint = Utility.Tobitmap(bitmapPrint, size, newHeight);
                }


                // Lấy kích thước QR code sau khi thay đổi size
                int qrWidth = bitmapPrint.getWidth();
                int qrHeight = bitmapPrint.getHeight();

                // Điều chỉnh ảnh sao cho có chiều cao bằng QR code
                int imageWidth = (int) ((float) imageBitmap.getWidth() / imageBitmap.getHeight() * qrHeight);
                Bitmap resizedImage = Bitmap.createScaledBitmap(imageBitmap, imageWidth, qrHeight, true);

                // Tạo bitmap mới để ghép QR code + ảnh
                Bitmap combinedBitmap = Bitmap.createBitmap(qrWidth + imageWidth, qrHeight, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(combinedBitmap);
                canvas.drawColor(Color.WHITE); // Đổ nền trắng

                // Vẽ QR code bên trái
                canvas.drawBitmap(bitmapPrint, 0, 0, null);

                // Vẽ ảnh bên phải
                canvas.drawBitmap(resizedImage, qrWidth, 0, null);

                // In ảnh
                Print.PrintBitmap(combinedBitmap, sype, light);
//                Print.CutPaper(0,0);
                // Giải phóng bộ nhớ
                combinedBitmap.recycle();
                resizedImage.recycle();
                imageBitmap.recycle();
                bitmapPrint.recycle();
            } catch (Exception e) {
                handler.sendEmptyMessage(PRINT_FAILURE);
            }
        });
    }

    public void printImage(final Bitmap bitmap, final int light, final int size,
                           final boolean isRotate, final int sype) {



        executorService.execute(() -> {
            Bitmap bitmapPrint = bitmap;
            bitmapPrint.setDensity(bitmap.getDensity());
            if (isRotate) {
                bitmapPrint = Utility.Tobitmap90(bitmapPrint);  // Xoay ảnh nếu cần
            }
            if (size != 0)
                bitmapPrint = Utility.Tobitmap(bitmapPrint, size, Utility.getHeight(size, bitmapPrint.getWidth(), bitmapPrint.getHeight()));


            try {
                // HalftoneMode từ settings (0/1/2)
                Print.SetPrintDensity((byte) 4);
                Print.setPrintResolution(203,203);
                Print.PrintBitmap(bitmapPrint, sype, light);  // In ảnh


            } catch (Exception e) {
                handler.sendEmptyMessage(PRINT_FAILURE);
            }
            bitmap.recycle();
            bitmapPrint.recycle();
        });
    }

    public void PrintNumber() {
        if (checkClick.isClickEvent()) return;

        String formatted="";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            current = LocalDateTime.now(); // Lấy ngày và giờ hiện tại
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd"); // Định dạng
            formatted = current.format(formatter); // Chuỗi ngày giờ định dạng
        }
        try {

            Print.PrintBitmap(imgSolve.createTextBitmap(String.valueOf(counterTime),formatted),1,0);

        } catch (Exception e) {
            Log.d("SDKSample", "Activity_TextFormat --> onClickPrint " + e.getMessage());
        }
    }
    public void printEmptyAndCut(final int light, final int size,
                                 final boolean isRotate, final int sype, final Bitmap bitmap) {
        executorService.execute(() -> {
            // Lấy ảnh từ drawable (end.jpg)
            Bitmap bitmapPrint=bitmap;
            if (bitmapPrint == null) {
                handler.sendEmptyMessage(PRINT_FAILURE);
                return;
            }

            if (isRotate) {
                bitmapPrint = Utility.Tobitmap90(bitmapPrint);  // Xoay ảnh nếu cần
            }

            if (size != 0) {
                // Tính toán lại kích thước ảnh
                int newHeight = Utility.getHeight(size, bitmapPrint.getWidth(), bitmapPrint.getHeight());
                bitmapPrint = Utility.Tobitmap(bitmapPrint, size, newHeight);
            }

            try {
                Print.PrintBitmap(bitmapPrint, sype, light);
                Print.CutPaper(0); // Cắt giấy đầy đủ
            } catch (Exception e) {
                handler.sendEmptyMessage(PRINT_FAILURE);
            }

            bitmapPrint.recycle();
        });
    }
    public void printImage2(final Bitmap bitmap, final int light, final int size,
                            final boolean isRotate, final int sype) {
        executorService.execute(() -> {
            Bitmap bitmapPrint = bitmap;
            if (isRotate) {
                bitmapPrint = Utility.Tobitmap90(bitmapPrint);  // Xoay ảnh nếu cần
            }

            if (size != 0) {
                // Tính toán lại kích thước ảnh
                int newHeight = Utility.getHeight(size, bitmapPrint.getWidth(), bitmapPrint.getHeight());
                bitmapPrint = Utility.Tobitmap(bitmapPrint, size, newHeight);

                // Áp dụng độ phân giải cao hơn (kỹ thuật làm sắc nét hoặc tăng chi tiết)

            }

            try {
                Print.PrintBitmap(bitmapPrint, sype, 0);  // In ảnh
//                Print.CutPaper(0,0); // Cắt giấy đầy đủ


            } catch (Exception e) {
                handler.sendEmptyMessage(PRINT_FAILURE);
            }

            bitmapPrint.recycle();
        });
    }
    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
            // Open camera khi ready

            openCamera();
            configureTransform(width,height);

        }
        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {

        }
        private void configureTransform(int viewWidth, int viewHeight) {
            if (null == textureView || null == imageDimension) {
                return;
            }
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            Matrix matrix = new Matrix();
            RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
            RectF bufferRect = new RectF(0, 0, imageDimension.getHeight(), imageDimension.getWidth());
            float centerX = viewRect.centerX();
            float centerY = viewRect.centerY();
            if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
                bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
                matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
                float scale = Math.max((float) viewHeight / imageDimension.getHeight(),
                        (float) viewWidth / imageDimension.getWidth());
                matrix.postScale(scale, scale, centerX, centerY);
                matrix.postRotate(90 * (rotation - 2), centerX, centerY);
            }
            textureView.setTransform(matrix);
        }
        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            return false;
        }
        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

        }

    };
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            // Camera opened
            Log.e(TAG, "onOpened");
            cameraDevice = camera;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                createCameraPreview();
            }
        }
        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraDevice.close();
        }
        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    // Thực hiển việc capture ảnh thông qua CAMERACAPTURESESSION


    protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }
    protected void stopBackgroundThread() {
        if (mBackgroundThread == null) {
            return;
        }
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            Log.d("StopBackgroundThread", "Error " + e);
        }
    }

    private void closeCamera() {
        try {
            if (cameraCaptureSessions != null) {
                cameraCaptureSessions.close();
                cameraCaptureSessions = null;
            }
        } catch (Exception e) {
            Log.w(TAG, "close session: " + e.getMessage());
        }
        try {
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
        } catch (Exception e) {
            Log.w(TAG, "close device: " + e.getMessage());
        }
    }
    private void takePicture() {
        if (null == cameraDevice) {
            Log.e(TAG, "cameraDevice is null");
            enableCaptureControls();
            return;
        }
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            assert map != null;
            android.util.Size[] jpegSizes = map.getOutputSizes(ImageFormat.JPEG);
            android.util.Size largestSize = jpegSizes[0];
            for (android.util.Size size : jpegSizes) {
                if (size.getWidth() * size.getHeight() > largestSize.getWidth() * largestSize.getHeight()) {
                    largestSize = size;
                }
            }
            // In dither 576px: chụp full JPEG (không cố định 480x640 — phóng to làm ảnh in rất xấu)
            ImageReader reader = ImageReader.newInstance(
                    largestSize.getWidth(), largestSize.getHeight(), ImageFormat.JPEG, 1);

            // Thiết lập các Surface để sử dụng với camera
            List<Surface> outputSurfaces = new ArrayList<>(2);
            outputSurfaces.add(reader.getSurface()); // Surface cho ImageReader
            outputSurfaces.add(new Surface(textureView.getSurfaceTexture())); // Surface cho preview

            // Tạo CaptureRequest để chụp ảnh
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());

            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
            captureBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY);
            captureBuilder.set(CaptureRequest.JPEG_QUALITY, (byte) 100);
            captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME,ExpoValue);  // 100ms
            captureBuilder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY);
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_EDOF);

            captureBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, ISOvalue);





            // Cấu hình orientation cho ảnh, tùy theo hướng của thiết bị
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            // Đặt file lưu ảnh (ví dụ: ảnh sẽ lưu vào thư mục Pictures của thiết bị)

            ImageReader.OnImageAvailableListener readerListener = reader1 -> {
                Image image = reader1.acquireLatestImage();

                if (image != null) {
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.capacity()];
                    buffer.get(bytes);
                    try {
                        save(bytes); // Lưu ảnh vào file
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    image.close(); // Đảm bảo đóng image sau khi sử dụng
                }

            };
            // Đặt listener cho ImageReader
            reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);

            // Cấu hình CameraCaptureSession để bắt đầu chụp
            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        session.capture(captureBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                            @Override
                            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                                super.onCaptureCompleted(session, request, result);
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                                    createCameraPreview();
                                }
                            }
                        }, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        Log.e(TAG, "CameraAccessException while capturing image: " + e.getMessage());
                    }
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.e(TAG, "Camera capture session configuration failed");
                    enableCaptureControls();
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "CameraAccessException: " + e.getMessage());
            enableCaptureControls();
        } catch (IllegalStateException e) {
            Log.e(TAG, "IllegalStateException: " + e.getMessage());
            enableCaptureControls();
        } catch (Exception e) {
            Log.e(TAG, "Unexpected exception: " + e.getMessage());
            enableCaptureControls();
        }
    }
    // Khởi tạo camera để preview trong textureview
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
    protected void createCameraPreview() {
        try {
            //checkCameraFeatures();
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(texture);

            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);



            // Đặt ISO (Sensitivity) - Ví dụ: ISO 800
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);


            captureRequestBuilder.set(CaptureRequest.JPEG_QUALITY, (byte) 100);
            if(ExpoValue<100000000) {
                captureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, ExpoValue);  // 100ms
            }
            else{
                long expoMax=80000000;
                captureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, expoMax);  // 100ms
            }
            captureRequestBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY);
            captureRequestBuilder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY);
            captureRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, ISOvalue);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_EDOF);
            int evValue = 2; // Giá trị EV (dương để tăng sáng, âm để giảm sáng)
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, evValue);


            cameraDevice.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback(){
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    // Camera đã bị đóng
                    if (null == cameraDevice) {
                        return;
                    }
                    // Khi session đã sẵn sàng, bắt đầu hiển thị preview
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                    try {
                        SocketService.getInstance()
                                .attachControlPageBridge(Activity_Camera2.this, textureView);
                    } catch (Exception ignored) {
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(Activity_Camera2.this, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            Log.d("CreateCameraPreview", "Error " + e);
        }
    }
    private void openCamera() {
        if (!MachineManager.hasDurableStorageAccess()) {
            return;
        }
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.e(TAG, "is camera open");
        try {
            String cameraId = manager.getCameraIdList()[1];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                    || ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(Activity_Camera2.this,
                        new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                        REQUEST_CAMERA_PERMISSION);
                return;
            }

            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            Log.d("OpenCamera", "Error " + e);
        }
    }

//    private void openCamera() {
//        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
//        try {
//            String[] cameraIdList = manager.getCameraIdList();
//            if (cameraIdList.length == 0) {
//                Toast.makeText(this, "Không tìm thấy camera", Toast.LENGTH_SHORT).show();
//                return;
//            }
//
//            // Tạo danh sách các camera
//            AlertDialog.Builder builder = new AlertDialog.Builder(this);
//            builder.setTitle("Chọn Camera");
//            builder.setItems(cameraIdList, (dialog, which) -> {
//                String selectedCameraId = cameraIdList[which];
//                openSelectedCamera(selectedCameraId);
//            });
//
//            builder.show();
//        } catch (CameraAccessException e) {
//            Log.d("OpenCamera", "Error " + e);
//        }
//    }
//
//    // Hàm mở camera sau khi chọn
//    private void openSelectedCamera(String cameraId) {
//        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
//        try {
//            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
//            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
//            assert map != null;
//            imageDimension = map.getOutputSizes(SurfaceTexture.class)[2];
//
//            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
//                ActivityCompat.requestPermissions(Activity_Camera2.this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
//                return;
//            }
//
//            manager.openCamera(cameraId, stateCallback, null);
//        } catch (CameraAccessException e) {
//            Log.d("OpenCamera", "Error " + e);
//        }
//    }

    protected void updatePreview() {
        if(null == cameraDevice) {
            Log.e(TAG, "updatePreview error, return");
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_OFF);
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            Log.d("UpdatePreview", "Error " + e);
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults); // Gọi phương thức của lớp cha

        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(Activity_Camera2.this, "Sorry!!!, you can't use this app without granting camera permission", Toast.LENGTH_LONG).show();
                finish();
            }
        }
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Storage permission ok", Toast.LENGTH_SHORT).show();
                // Permission granted
            } else {
                Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /** Đọc lại ISO / exposure từ prefs — cần khi quay từ Manual (activity không recreate). */
    private void reloadIsoExposureFromPrefs() {
        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        try {
            ISOvalue = Integer.parseInt(prefs.getString("isovalue", "400"));
        } catch (NumberFormatException e) {
            ISOvalue = 400;
        }
        try {
            ExpoValue = Long.parseLong(prefs.getString("epxvalue", "30000000"));
        } catch (NumberFormatException e) {
            ExpoValue = 30000000L;
        }
        Log.d(TAG, "reloadIsoExposureFromPrefs ISO=" + ISOvalue + " Expo=" + ExpoValue);
    }

    /**
     * Đăng ký máy trên server + join socket machine-app (Device Manager hiện Android / Mono).
     */
    private void connectMachinePresenceForDeviceManager(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        new Thread(() -> {
            try {
                MachineManager machineManager = MachineManager.getInstance(Activity_Camera2.this);
                boolean ok = machineManager.checkAndUpdateMachine(token);
                if (!ok) {
                    Log.w(TAG, "checkAndUpdateMachine failed — skip socket presence");
                    return;
                }
                String machineId = machineManager.getMachineCode();
                if (machineId == null || machineId.isEmpty()) {
                    return;
                }
                final String machineIdUpper = machineId.trim().toUpperCase();
                runOnUiThread(() -> {
                    SocketService socketService = SocketService.getInstance();
                    socketService.attachAppContext(Activity_Camera2.this);
                    socketService.attachControlPageBridge(Activity_Camera2.this, textureView);
                    socketService.connect();
                    final Handler mainHandler = new Handler(android.os.Looper.getMainLooper());
                    final Runnable tryJoin = new Runnable() {
                        int attempts = 0;

                        @Override
                        public void run() {
                            attempts++;
                            if (socketService.isConnected()) {
                                socketService.joinMachineRoom(machineIdUpper);
                                socketService.joinMachineAppRoom(machineIdUpper, "android", "mono");
                                String token = TokenManager.getInstance(Activity_Camera2.this).getToken();
                                if (token != null && !token.isEmpty()) {
                                    socketService.joinUserRoom(token);
                                }
                                socketService.emitCameraSettingsForControlPage(true);
                                Log.d(TAG, "Joined machine-app room (mono): " + machineIdUpper);
                                return;
                            }
                            if (attempts < 15) {
                                mainHandler.postDelayed(this, 300);
                            }
                        }
                    };
                    mainHandler.post(tryJoin);
                });
            } catch (Exception e) {
                Log.e(TAG, "connectMachinePresenceForDeviceManager error", e);
            }
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.e(TAG, "onResume");
        MachineManager mm = MachineManager.getInstance(this);
        // Chưa bật quyền ghi mã máy → chặn camera / socket
        if (!mm.enforceDurableStorageAccessRequired(this)) {
            Log.w(TAG, "Blocked until All files access is granted");
            return;
        }
        mm.reloadDurableStorageAfterPermission();
        // Sau khi user cấp All files access: đăng ký máy (lần đầu có thể bị defer)
        TokenManager tm = TokenManager.getInstance(this);
        if (tm.canUseCloudFeatures() && (mm.getMachineCode() == null || mm.getMachineCode().isEmpty())) {
            connectMachinePresenceForDeviceManager(tm.getToken());
        }
        reloadIsoExposureFromPrefs();
        final boolean softSwitch = MonoScreenSwitch.consumeSoftResume();
        MonoDriveServerSync.requestSyncIfLoggedIn(this);
        applyClickButtonHiddenState();
        enableCaptureControls();
        startBackgroundThread();
        if (textureView != null) {
            textureView.removeCallbacks(deferredOpenCameraRunnable);
        }
        try {
            SocketService.getInstance().attachControlPageBridge(this, textureView);
        } catch (Exception ignored) {
        }
        if (softSwitch && textureView != null) {
            // Cho hiệu ứng chuyển màn chạy trước, rồi mới mở camera
            textureView.postDelayed(deferredOpenCameraRunnable, 300);
        } else {
            openCameraIfReady();
        }
    }
    @Override
    protected void onPause() {
        Log.e(TAG, "onPause");
        stopClickButtonBlink();
        if (textureView != null) {
            textureView.removeCallbacks(deferredOpenCameraRunnable);
        }
        // Phải nhả camera khi pause — nếu không Manual/settings không mở được → live view đứng im
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    private void openCameraIfReady() {
        if (isFinishing() || textureView == null) {
            return;
        }
        if (!MachineManager.hasDurableStorageAccess()) {
            return;
        }
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }
    private void save(byte[] bytes) throws Exception {
        // Tạo tệp trong thư mục cache của ứng dụng
        file = new File(getCacheDir(), "photo_" + System.currentTimeMillis() + ".jpg");

        // Lưu dữ liệu byte vào tệp
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(bytes);
            fos.flush();
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), options);

        // Truyền đường dẫn của tệp cache cho hàm setPrintDialog2
        imageProcessing(file.getPath());
    }

    @Override
    protected void onDestroy() {
        try {
            SocketService.getInstance().clearControlPageBridge();
        } catch (Exception ignored) {
        }
        stopClickButtonBlink();
        executorService.shutdownNow();
        uploadExecutorService.shutdownNow();
        super.onDestroy();
        try {
            Print.PortClose();
        } catch (Exception ignored) {
        }
    }

    @Override
    public String getControlPageWindowState() {
        return "main";
    }

    @Override
    public boolean isMonoPostCapturePending() {
        return false;
    }

    @Override
    public void onControlPageSetIso(String isoValue) {
        runOnUiThread(() -> {
            try {
                getSharedPreferences("MyAppPrefs", MODE_PRIVATE).edit()
                        .putString("isovalue", isoValue).apply();
                reloadIsoExposureFromPrefs();
                if (cameraCaptureSessions != null && captureRequestBuilder != null) {
                    captureRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, ISOvalue);
                    cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, null);
                }
                SocketService.getInstance().notifyControlPageSettingsChanged();
            } catch (Exception e) {
                Log.e(TAG, "onControlPageSetIso", e);
            }
        });
    }

    @Override
    public void onControlPageSetExposure(String exposureNs) {
        runOnUiThread(() -> {
            try {
                getSharedPreferences("MyAppPrefs", MODE_PRIVATE).edit()
                        .putString("epxvalue", exposureNs).apply();
                reloadIsoExposureFromPrefs();
                if (cameraCaptureSessions != null && captureRequestBuilder != null) {
                    long expo = Math.min(ExpoValue, 80_000_000L);
                    captureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, expo);
                    cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, null);
                }
                SocketService.getInstance().notifyControlPageSettingsChanged();
            } catch (Exception e) {
                Log.e(TAG, "onControlPageSetExposure", e);
            }
        });
    }

    @Override
    public void onControlPageSetPrintMode(int mode) {
        PrintBitmapMode.set(this, mode);
        SocketService.getInstance().notifyControlPageSettingsChanged();
    }

    @Override
    public void onControlPageSetPrinterTest(boolean enabled) {
        PrinterTestMode.setEnabled(this, enabled);
        SocketService.getInstance().notifyControlPageSettingsChanged();
    }

    @Override
    public void onControlPageSetQrPrint(boolean enabled) {
        getSharedPreferences("settings", MODE_PRIVATE).edit()
                .putBoolean("Download", enabled).apply();
        SocketService.getInstance().notifyControlPageSettingsChanged();
    }

    @Override
    public void onControlPageSetClickButtonHidden(boolean hidden) {
        runOnUiThread(() -> {
            setClickButtonHidden(hidden);
            SocketService.getInstance().notifyControlPageSettingsChanged();
        });
    }

    @Override
    public void onControlPageSelectFrame(String frameId) {
        MonoAssetSelectHelper.selectFrameById(this, frameId, new MonoAssetSelectHelper.AfterSelect() {
            @Override
            public void onApplied(int index) {
                currentIndex = index;
                SharedPreferences preferences = getSharedPreferences("FrameImage", Context.MODE_PRIVATE);
                String jsonString = preferences.getString("bitmap_list", "[]");
                bitmapList = new Gson().fromJson(jsonString, new TypeToken<List<String>>() {}.getType());
                updateImageView(currentIndex);
                SocketService.getInstance().notifyControlPageSettingsChanged();
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "select frame: " + message);
                Toast.makeText(Activity_Camera2.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onControlPageSelectSubPhoto(String subPhotoId) {
        MonoAssetSelectHelper.selectSubById(this, subPhotoId, new MonoAssetSelectHelper.AfterSelect() {
            @Override
            public void onApplied(int index) {
                currentIndexImageView2 = index;
                SharedPreferences sharedPreferences2 = getSharedPreferences("MyAppPrefs2", MODE_PRIVATE);
                String json = sharedPreferences2.getString("ImageViewList", "[]");
                bitmapListImageView2 = new Gson().fromJson(json, new TypeToken<List<String>>() {}.getType());
                if (bitmapListImageView2 != null && !bitmapListImageView2.isEmpty()
                        && index < bitmapListImageView2.size()) {
                    image = UserAssetFileStore.decodeListEntryToBitmap(
                            Activity_Camera2.this, bitmapListImageView2.get(index));
                }
                if (image == null) {
                    image = BitmapFactory.decodeResource(getResources(), R.drawable.bottom);
                }
                SocketService.getInstance().notifyControlPageSettingsChanged();
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "select sub: " + message);
                Toast.makeText(Activity_Camera2.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onControlPageCapture() {
        runOnUiThread(() -> {
            if (!Print.IsOpened() && !PrinterTestMode.isEnabled(this)) {
                Toast.makeText(this, getString(R.string.please_connect_printer), Toast.LENGTH_SHORT).show();
                return;
            }
            // Remote từ Control Page: không phụ thuộc nút chụp local đang enabled
            startCountdown();
            if (textureView != null) textureView.setEnabled(false);
            if (clickButton != null) {
                clickButton.setEnabled(false);
                stopClickButtonBlink();
                clickButton.setVisibility(View.INVISIBLE);
            }
        });
    }

    @Override
    public void onControlPagePrint() {
        // Main tự in sau chụp — không có bước In/Hủy
    }

    @Override
    public void onControlPageCancelPostCapture() {
        // no-op trên trang chính
    }

    @Override
    public void onControlPageNavigateBackToMain() {
        // Đã ở Main
    }

    @Override
    public void onControlPageWakeDevice() {
        runOnUiThread(() -> {
            DeviceWakeHelper.applyScreenOnFlags(this);
            if (textureView != null) {
                textureView.removeCallbacks(deferredOpenCameraRunnable);
                textureView.postDelayed(deferredOpenCameraRunnable, 400);
            } else {
                openCameraIfReady();
            }
        });
    }

}
