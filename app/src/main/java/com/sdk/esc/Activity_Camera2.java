package com.sdk.esc;

import android.Manifest;
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

public class Activity_Camera2 extends AppCompatActivity {
    private static final String MONO_FOLDER_ID_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";

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
    // preview camera
    private TextureView textureView;
    private ImageView imgFrame;
    private  int ISOvalue=400;
    private  long ExpoValue= 30000000;
    private final int PRINT_FAILURE = 0;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private static final int REQUEST_STORAGE_PERMISSION = 201;
    /** Tải ảnh in lên Drive (bật Download) bất đồng bộ — chặn chụp tiếp cho tới khi xong. */
    private volatile boolean blockCaptureForDriveUpload;
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
        if (!tokenManager.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

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
        SharedPreferences sharedPreferences = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        ISOvalue = Integer.parseInt(sharedPreferences.getString("isovalue", "400"));
        ExpoValue=Integer.parseInt(sharedPreferences.getString("epxvalue", "30000000"));

        //xu ly anh phu -------------------------------------------------------------
        SharedPreferences sharedPreferences2 = getSharedPreferences("MyAppPrefs2", MODE_PRIVATE);
        currentIndexImageView2=sharedPreferences2.getInt("indexImageView2", 0);
        String jsonStringImageview2 = sharedPreferences2.getString("ImageViewList", "[]");
        Gson gson1 = new Gson();
        bitmapListImageView2 = gson1.fromJson(jsonStringImageview2, new TypeToken<List<String>>() {}.getType());

        //xu ly anh phu trong googleDrive -------------------------------------------------------------------------------
        GoogleDriveService googleDriveService = new GoogleDriveService(this);
        googleDriveService.createOrGetSubFolder(this).thenAccept(folderId -> {
            if (folderId != null) {
                Log.d("MainActivity", "Folder NameCard đã sẵn sàng với ID: " + folderId);
            } else {
                Log.e("MainActivity", "Lỗi khi tạo hoặc lấy thư mục NameCard.");
            }
        });
        //--------------------------------------------------------------------------------------------------------------
        counterTime=sharedPreferences.getInt("counterTime", 1);
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
                if (blockCaptureForDriveUpload) {
                    Toast.makeText(Activity_Camera2.this, R.string.drive_upload_wait_capture, Toast.LENGTH_SHORT).show();
                    return;
                }
                startCountdown();
                textureView.setEnabled(false);
                clickButton.setEnabled(false);
                clickButton.setVisibility(View.INVISIBLE);
            }
        });

        settingButton.setOnClickListener(v -> {
            clickCount++;

            if (clickCount == 3) {
                Intent intent2 = new Intent(Activity_Camera2.this, Activity_Camera2_Manual.class); // Chuyển đến SettingsActivity
                startActivity(intent2); // Bắt đầu Activity mới
            } else {
                // Reset click count after a short delay or you can reset immediately
                v.postDelayed(() -> clickCount = 0, 500); // Hoặc bạn có thể điều chỉnh thời gian delay
            }
        });
        hideLogoButton.setOnClickListener(v -> {
            clickCount++;

            if (clickCount == 3) {
                // Toggle alpha (trong suốt) của clickButton
                if (clickButton.getAlpha() == 1f) {
                    clickButton.setAlpha(0f); // Ẩn đi nhưng vẫn chạm được
                } else {
                    clickButton.setAlpha(1f); // Hiện lại
                }

                clickCount = 0;
            } else {
                // Reset click count nếu không đủ 3 lần trong khoảng thời gian nhất định
                v.postDelayed(() -> clickCount = 0, 500);
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

            // Tính toán kích thước mới theo tỷ lệ 16:10
            int originalWidth = bitmap.getWidth();
            int targetHeight = (int) (originalWidth * (9.5 / 16.0));

            // Resize bitmap
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, originalWidth, targetHeight, true);

            // Set resized bitmap vào ImageView
            imgFrame.setImageBitmap(resizedBitmap);
        }
    }

    private void startCountdown() {
        if (blockCaptureForDriveUpload) {
            Toast.makeText(this, R.string.drive_upload_wait_capture, Toast.LENGTH_SHORT).show();
            return;
        }
        // Hiển thị TextView countdown
        countdown.setVisibility(View.VISIBLE);
        countdownSound = MediaPlayer.create(this, R.raw.countdown); // Sử dụng tệp âm thanh cho 3 giây
        shutterSound = MediaPlayer.create(this, R.raw.shutter); // Sử dụng tệp âm thanh cho tiếng chụp

        // Khởi tạo CountDownTimer, đếm ngược từ 3 giây
        new CountDownTimer(3000, 1000) {

            @Override
            public void onTick(long millisUntilFinished) {
                // Cập nhật TextView với số giây còn lại
                int secondsRemaining = (int) millisUntilFinished / 1000;

                // Phát âm thanh cho mỗi giây đếm ngược
                switch (secondsRemaining+1) {
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
                // Sau khi đếm ngược xong, thực hiện chụp ảnh
                takePicture(); // Gọi hàm chụp ảnh sau khi đếm ngược xong

                // Phát âm thanh tiếng chụp
                shutterSound.start();

                // Ẩn TextView countdown sau khi chụp ảnh
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
        AtomicReference<Bitmap> bmp = new AtomicReference<>(imgSolve.applySharpening(resizedBitmap, 2.0f));
        bmp.get().setDensity(dpi);

        int light = 15;
        int[] lightValue1 = {light}; // Adjust brightness based on SeekBar progress
        float contrast = 1.4f;
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
        // Khung giữ màu (lưu file); bản trắng-đen chỉ dùng khi in
        bitmapFrame = imgSolve.resizeBitmapMaintainAspect(bitmapFrame, 800);
// Bitmap đã xử lý (adjustedBitmap2[0])
        Bitmap processedBitmap2 = adjustedBitmap2[0];
        processedBitmap2 = imgSolve.resizeBitmapMaintainAspect(processedBitmap2,800); // Nếu cần chuyển thành grayscale

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

        // Ảnh chính màu cho lưu: không qua processingImage (OpenCV xám) — cùng sharpen/sáng/tương phản
        Bitmap colorForSave = imgSolve.processingImageColorForSave(
                origin, dpi, 2.0f, lightValue1[0], contrast, 800);
        Bitmap flippedColor;
        if (colorForSave != null) {
            Bitmap colorAligned = Bitmap.createScaledBitmap(colorForSave, newWidth, newHeight, true);
            if (colorAligned != colorForSave) {
                colorForSave.recycle();
            }
            Bitmap enlargedColor = Bitmap.createScaledBitmap(colorAligned, newWidth + compensation, newHeight + compensation, true);
            if (enlargedColor != colorAligned) {
                colorAligned.recycle();
            }
            flippedColor = Bitmap.createBitmap(enlargedColor, 0, 0,
                    enlargedColor.getWidth(), enlargedColor.getHeight(), matrix, true);
            enlargedColor.recycle();
        } else {
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

        runOnUiThread(() -> {
            if (needWaitForDriveUploadPipeline()) {
                blockCaptureForDriveUpload = true;
            } else {
                if (textureView != null) {
                    textureView.setEnabled(true);
                }
                if (clickButton != null) {
                    clickButton.setEnabled(true);
                    clickButton.setVisibility(View.VISIBLE);
                }
            }
        });
        if (PrinterTestMode.isEnabled(this)) {
            try {
                String fn = PrinterTestMode.newTestFileNameJpeg();
                Bitmap fullPage = Utility.buildVerticalStackForPrintWidth(combinedBitmapColor, image, 576);
                if (fullPage == null) {
                    fullPage = combinedBitmapColor != null ? combinedBitmapColor : combinedBitmap;
                }
                MonoGallerySaver.saveBitmapToMonoFolder(this, fullPage, fn);
                File tmp = PrinterTestMode.writeJpegToCacheDir(this, fullPage, fn);
                if (fullPage != null && fullPage != combinedBitmap && fullPage != combinedBitmapColor) {
                    fullPage.recycle();
                }
                if (combinedBitmapColor != null) {
                    combinedBitmapColor.recycle();
                }
                SharedPreferences prefsTest = getSharedPreferences("settings", MODE_PRIVATE);
                if (prefsTest.getBoolean("Download", false) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    final String monoFolderName = generateMonoServerFolderId();
                    printMonoDriveQrForUploadedFileLink(buildMonoServerGalleryUrl(monoFolderName));
                    uploadMonoPhotoToServerInBackground(tmp, monoFolderName);
                    runOnUiThread(() -> {
                        imgSolve.clearCache();
                        releaseCaptureAfterDriveUpload();
                    });
                } else {
                    imgSolve.clearCache();
                }
            } catch (Exception e) {
                Log.e(TAG, "Test mode: save/Drive", e);
                runOnUiThread(() -> {
                    imgSolve.clearCache();
                    releaseCaptureAfterDriveUpload();
                });
            }
            counterTime++;
            SharedPreferences preferences2t = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE);
            preferences2t.edit().putInt("counterTime", counterTime).apply();
            return;
        }
        //adjustedBitmap2[0]=imgSolve.applyMedianFilter(adjustedBitmap2[0],3);
        int PRINT_THREE_INCH = 576;
        int BITMAP_SHAKE = 1;
        final String monoFolderName = generateMonoServerFolderId();
        final String monoLocalPhotoName = monoFolderName + "_1.jpg";
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
        printImage(
                combinedBitmap,
                0,
                PRINT_THREE_INCH,
                false,
                BITMAP_SHAKE
        );

        printImage2(image, 0, 576, false, 1);
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        boolean Download= prefs.getBoolean("Download", false);
        if(Download) {
            final File fCombinedDrive = combinedFileForDrive;
            if (fCombinedDrive != null && fCombinedDrive.exists()) {
                printMonoDriveQrForUploadedFileLink(buildMonoServerGalleryUrl(monoFolderName));
                uploadMonoPhotoToServerInBackground(fCombinedDrive, monoFolderName);
            } else {
                printMonoDriveQrForUploadedFileLink(null);
            }
            imgSolve.clearCache();
            releaseCaptureAfterDriveUpload();
        } else {

            Bitmap bitmapPrint = BitmapFactory.decodeResource(Activity_Camera2.this.getResources(), R.drawable.end);
            printEmptyAndCut(0, 150, false, 1,bitmapPrint);
            imgSolve.clearCache();
            releaseCaptureAfterDriveUpload();
        }
//        imgSolve.clearCache();
        counterTime++;
        SharedPreferences preferences2 = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences2.edit();
        editor.putInt("counterTime", counterTime);
        editor.apply();


    }

    private boolean needWaitForDriveUploadPipeline() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false;
        }
        return getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("Download", false);
    }

    private void releaseCaptureAfterDriveUpload() {
        blockCaptureForDriveUpload = false;
        runOnUiThread(() -> {
            if (textureView != null) {
                textureView.setEnabled(true);
            }
            if (clickButton != null) {
                clickButton.setEnabled(true);
                clickButton.setVisibility(View.VISIBLE);
            }
        });
    }

    private void printMonoDriveQrForUploadedFileLink(@Nullable String driveFileLink) {
        if (driveFileLink == null || driveFileLink.isEmpty()) {
            Log.e(TAG, "Không có link file Drive — in kết thúc, bỏ QR.");
            Bitmap bitmapPrint = BitmapFactory.decodeResource(getResources(), R.drawable.end);
            printEmptyAndCut(0, 150, false, 1, bitmapPrint);
            return;
        }
        GoogleDriveService driveService = new GoogleDriveService(this);
        Bitmap qr = driveService.generateQRCodeForUrl(driveFileLink);
        if (qr != null) {
            Log.d(TAG, "QR từ link file: " + driveFileLink);
            printQR(qr, 0, 140, true, 1);
            imgSolve.generateQRCode("https://maps.app.goo.gl/BrvtyEMcy8gPFq939", 500);
            Bitmap end = BitmapFactory.decodeResource(getResources(), R.drawable.end);
            printEmptyAndCut(0, 150, false, 1, end);
        } else {
            Log.e(TAG, "Không tạo được QR từ link file");
            Bitmap end = BitmapFactory.decodeResource(getResources(), R.drawable.end);
            printEmptyAndCut(0, 150, false, 1, end);
        }
    }

    private String buildMonoServerGalleryUrl(String folderName) {
        return ApiService.BASE_URL + "/mono-results/g/" + folderName;
    }

    private String generateMonoServerFolderId() {
        String datePart = new java.text.SimpleDateFormat("ddMMyyyyHHmmss", Locale.US).format(new java.util.Date());
        Random r = new Random();
        StringBuilder randomPart = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            randomPart.append(MONO_FOLDER_ID_CHARS.charAt(r.nextInt(MONO_FOLDER_ID_CHARS.length())));
        }
        return datePart + randomPart;
    }

    private void uploadMonoPhotoToServerInBackground(@Nullable File uploadFile, String folderName) {
        if (uploadFile == null || !uploadFile.exists()) {
            Log.e(TAG, "uploadMonoPhotoToServerInBackground: file null/không tồn tại");
            return;
        }
        executorService.execute(() -> {
            try {
                String token = TokenManager.getInstance(Activity_Camera2.this).getToken();
                if (token == null || token.isEmpty()) {
                    Log.e(TAG, "Upload Mono server: thiếu token");
                    return;
                }
                org.json.JSONObject uploadRes = ApiService.uploadMonoPhotoWithName(token, folderName, uploadFile, "1.jpg");
                Log.d(TAG, "Upload Mono server OK: " + uploadRes.optString("folderId", folderName));
            } catch (Exception e) {
                Log.e(TAG, "Upload Mono server lỗi", e);
            }
        });
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
                    Boolean Download= prefs.getBoolean("Download", false);
                    String lang = prefs.getString("language", "vi");
                    if(Download)
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
                //Print.SetPrintDensity((byte)4);
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
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            Log.d("StopBackgroundThread", "Error " + e);
        }
    }
    private void takePicture() {
        if (null == cameraDevice) {
            Log.e(TAG, "cameraDevice is null");
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
            ImageReader reader = ImageReader.newInstance(480,640, ImageFormat.JPEG, 1);
            // Kiểm tra độ phân giải tối đa mà camera hỗ trợ

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
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "CameraAccessException: " + e.getMessage());
        } catch (IllegalStateException e) {
            Log.e(TAG, "IllegalStateException: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Unexpected exception: " + e.getMessage());
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
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.e(TAG, "is camera open");
        try {
            String cameraId = manager.getCameraIdList()[1];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(Activity_Camera2.this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
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

    @Override
    protected void onResume() {
        super.onResume();
        Log.e(TAG, "onResume");
        MonoDriveServerSync.requestSyncIfLoggedIn(this);
        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }
    @Override
    protected void onPause() {
        Log.e(TAG, "onPause");
        //closeCamera();
        stopBackgroundThread();
        super.onPause();
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
        super.onDestroy();
        try {
            Print.PortClose();
        } catch (Exception ignored) {
        }
    }



}
