package com.sdk.esc;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.bumptech.glide.Glide;
import com.luck.picture.lib.basic.PictureSelector;
import com.luck.picture.lib.config.SelectMimeType;
import com.luck.picture.lib.entity.LocalMedia;
import com.luck.picture.lib.interfaces.OnResultCallbackListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import print.Print;

public class Activity_Camera2_Manual extends AppCompatActivity {



    private MediaPlayer countdownSound;
    private MediaPlayer shutterSound;
    private int counterTime=1;

    LocalDateTime current;
    private CameraDevice cameraDevice;
    private CaptureRequest.Builder captureRequestBuilder;
    private static final String TAG = "AndroidCameraApi";
    private ProgressDialog dialog;
    final private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Handler handler;
    // Button cho capture ảnh

    // preview camera
    private TextureView textureView;
    private  int ISOvalue=400;
    private  long ExpoValue= 30000000;
    private final int PRINT_FAILURE = 0;
    private final int PRINT_THREE_INCH = 650;
    private final int PRINT_TWO_INCH = 384;
    private final int PRINT_FOUR_INCH = 832;
    private final int BITMAP_SHAKE = 1;
    private final int BITMAP_GATHER = 2;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private static final int REQUEST_STORAGE_PERMISSION = 201;
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

    private String cameraId;
    protected CameraCaptureSession cameraCaptureSessions;
    private Size imageDimension;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    FrameLayout frameLayoutPopup;
    RadioButton rbShake ;
    Button btnPrint;
    Button btnCancel;
    RadioButton rbInch3 ;
    RadioButton rbInch2;
    RadioButton rbInch4;
    RadioButton rbZero ;
    ImageView imageViewPreview ;
    SeekBar seekBarLight ;
    SeekBar seekBarConst;
    ImageView imageViewSecond;
    TextView countdown;
    boolean havingUsb=false;
    Button decrease;
    Button increase;
    EditText numberCount;
    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera2);
        Intent intent = new Intent(ACTION_USB_PERMISSION);
        intent.setPackage(Activity_Camera2_Manual.this.getPackageName());
        IntentFilter filter = new IntentFilter();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mPermissionIntent = PendingIntent.getBroadcast(Activity_Camera2_Manual.this, 0, intent, PendingIntent.FLAG_MUTABLE);
        } else {
            mPermissionIntent = PendingIntent.getBroadcast(Activity_Camera2_Manual.this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Activity_Camera2_Manual.this.registerReceiver(mUsbReceiver, filter, RECEIVER_EXPORTED);
        }
        textureView = findViewById(R.id.texture);
        ImageButton settingButton = findViewById(R.id.button_settings);
        ImageButton settingButtonExpos = findViewById(R.id.button_settings_exposu);
        countdown= findViewById(R.id.countdownText);
        countdown.setVisibility(View.INVISIBLE);
        assert textureView != null;
        textureView.setSurfaceTextureListener(textureListener);

        textureView.setOnClickListener(v -> {

            if (!Print.IsOpened()) {
                Toast.makeText(Activity_Camera2_Manual.this, "Please connect to Printer", Toast.LENGTH_SHORT).show();
                try {
                    if(havingUsb)
                    {
                        connectUSB();
                    }
                } catch (NumberFormatException e) {
                    Toast.makeText(getApplicationContext(), "Can't find Printer", Toast.LENGTH_SHORT).show();
                }

            }
            else{
                startCountdown();
                textureView.setEnabled(false);
            }
        });

        settingButton.setOnClickListener(v -> showSetiingDialog());
        settingButtonExpos.setOnClickListener(v -> showSetiingDialogExposuTime());
    }

    private void startCountdown() {
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
                        countdownSound = MediaPlayer.create(Activity_Camera2_Manual.this, R.raw.countdown);
                        countdownSound.start();
                        countdown.setText("3");
                        break;
                    case 2:
                        countdownSound = MediaPlayer.create(Activity_Camera2_Manual.this, R.raw.countdown);
                        countdownSound.start();
                        countdown.setText("2");
                        break;
                    case 1:
                        countdownSound = MediaPlayer.create(Activity_Camera2_Manual.this, R.raw.countdown);
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
        UsbManager mUsbManager = (UsbManager) Activity_Camera2_Manual.this.getSystemService(Context.USB_SERVICE);
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
                            if (Print.PortOpen(Activity_Camera2_Manual.this, device) != 0) {
                                Toast.makeText(Activity_Camera2_Manual.this, "Lỗi khi mở cổng", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(Activity_Camera2_Manual.this, "Kết nối thành công", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(Activity_Camera2_Manual.this, "Quyền bị từ chối", Toast.LENGTH_SHORT).show();
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
    private void showSetiingDialog() {
        // Các mức ISO bạn muốn hiển thị
        final String[] isoLevels = {"100", "200", "300", "400","500", "600", "700", "800","900", "1000", "1100", "1200"};

        // Giả sử ISOvalue là giá trị bạn muốn kiểm tra

        // Tạo dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Chọn mức ISO");

        // Tìm chỉ số của ISOvalue trong mảng isoLevels
        int initialSelection = -1;
        for (int i = 0; i < isoLevels.length; i++) {
            int isoLevelInt = Integer.parseInt(isoLevels[i]);
            if (isoLevelInt == ISOvalue) {
                initialSelection = i;  // Lưu vị trí nếu tìm thấy
                break;
            }
        }

        // Thiết lập các item trong dialog
        builder.setSingleChoiceItems(isoLevels, initialSelection, (dialog, which) -> {
            // which trả về vị trí của ISO được chọn
            String selectedISO = isoLevels[which];
            Toast.makeText(getApplicationContext(), "ISO đã chọn: " + selectedISO, Toast.LENGTH_SHORT).show();

            // Gọi hàm hoặc xử lý với giá trị ISO được chọn
            applyISO(selectedISO);
            int selectedISO2 = Integer.parseInt(selectedISO);
            updateISO(selectedISO2);
        });

        // Hiển thị dialog
        builder.create().show();
    }

    private void applyISO(String iso) {
        try {
            ISOvalue = Integer.parseInt(iso); // Cập nhật giá trị mới
            Log.d("ISO_SETTING", "ISO mới được áp dụng: " + ISOvalue);
            Toast.makeText(getApplicationContext(), "ISO hiện tại: " + ISOvalue, Toast.LENGTH_SHORT).show();
        } catch (NumberFormatException e) {
            Log.e("ISO_SETTING", "Không thể chuyển đổi giá trị ISO: " + iso);
        }
    }
    private void updateISO(int newISO) {
        try {
            // Cập nhật giá trị ISO
            captureRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, newISO);

            // Áp dụng request mới
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, null);
            Log.d("CameraISO", "ISO đã cập nhật: " + newISO);
        } catch (CameraAccessException e) {
            Log.d("CameraISO", "Lo" + e.getMessage());
        }
    }


    private void showSetiingDialogExposuTime() {
        // Các mức ISO bạn muốn hiển thị
        final String[] ExposureLevels = {"10000000","20000000","30000000","40000000","50000000","60000000","70000000","80000000","90000000","100000000"};

        // Giả sử ISOvalue là giá trị bạn muốn kiểm tra

        // Tạo dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Chọn mức Expos");

        // Tìm chỉ số của ISOvalue trong mảng isoLevels
        int initialSelection = -1;
        for (int i = 0; i < ExposureLevels.length; i++) {
            long ExposureLevel = Long.parseLong(ExposureLevels[i]);
            if (ExposureLevel == ExpoValue) {
                initialSelection = i;  // Lưu vị trí nếu tìm thấy
                break;
            }
        }

        // Thiết lập các item trong dialog
        builder.setSingleChoiceItems(ExposureLevels, initialSelection, (dialog, which) -> {
            // which trả về vị trí của ISO được chọn
            String selectedExpo = ExposureLevels[which];
            Toast.makeText(getApplicationContext(), "Phơi sáng đã chọn: " + selectedExpo, Toast.LENGTH_SHORT).show();

            // Gọi hàm hoặc xử lý với giá trị ISO được chọn
            applyExpose(selectedExpo);
            long selectedExpose2 = Long.parseLong(selectedExpo);
            updateExpose(selectedExpose2);
        });

        // Hiển thị dialog
        builder.create().show();
    }

    private void applyExpose(String exp) {
        try {
            ExpoValue = Long.parseLong(exp); // Cập nhật giá trị mới
            Log.d("EXP_SETTING", "EXP mới được áp dụng: " + ExpoValue);
            Toast.makeText(getApplicationContext(), "expo hiện tại: " + ExpoValue, Toast.LENGTH_SHORT).show();
        } catch (NumberFormatException e) {
            Log.e("EXP_SETTING", "Không thể chuyển đổi giá trị expo: " + exp);
        }
    }
    private void updateExpose(long exp) {
        try {
            // Cập nhật giá trị ISO
            captureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exp);

            // Áp dụng request mới
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, null);
            Log.d("CameraEXP", "EXP đã cập nhật: " + exp);
        } catch (CameraAccessException e) {
            Log.d("CameraEXP", "Error " + e);
        }
    }
    private void setPrintDialog2(String path) throws Exception {
        ImageSolve imgSolve=new ImageSolve(Activity_Camera2_Manual.this);
        Bitmap origin = BitmapFactory.decodeFile(path);


        Print.setPrintResolution(203, 203);

        origin=imgSolve.applySharpening2(origin,1f);
        origin = imgSolve.convertToGrayscale(origin);

        Bitmap bmp = (origin);
        bmp.setDensity(origin.getDensity());
        frameLayoutPopup=findViewById(R.id.frame_layout);
        rbShake = findViewById(R.id.rb_shake);
        btnPrint=findViewById(R.id.btnPrint);
        btnCancel=findViewById(R.id.btnCancel);
        rbInch3 = findViewById(R.id.rb_inch3);
        rbInch2 = findViewById(R.id.rb_inch2);
        rbInch4 = findViewById(R.id.rb_inch4);
        rbZero = findViewById(R.id.rb_zero);
        imageViewPreview = findViewById(R.id.imageViewPreview);
        seekBarLight = findViewById(R.id.seekbar_light);
        seekBarConst = findViewById(R.id.seekbar_constrast);
        imageViewSecond = findViewById(R.id.imageViewSecond);
        increase=findViewById(R.id.btnIncrease);
        decrease=findViewById(R.id.btnDecrease);
        numberCount=findViewById(R.id.editTextNumber);
        seekBarConst.setProgress(17);
        seekBarLight.setProgress(20);
        numberCount.setText(String.valueOf(counterTime));
        decrease.setOnClickListener(v -> {
            counterTime--; // Giảm giá trị
            numberCount.setText(String.valueOf(counterTime)); // Cập nhật lại EditText
        });

        // Xử lý sự kiện khi nhấn nút tăng
        increase.setOnClickListener(v -> {
            counterTime++; // Tăng giá trị
            numberCount.setText(String.valueOf(counterTime)); // Cập nhật lại EditText
        });
        @SuppressLint("UseCompatLoadingForDrawables") Drawable drawable = getResources().getDrawable(R.drawable.facebook, null);

// Chuyển drawable thành bitmap

        runOnUiThread(() -> {
            Bitmap bitmapimgView = imgSolve.drawableToBitmap(drawable);
            bitmapimgView=imgSolve.convertToGrayscale(bitmapimgView);
            Glide.with(Activity_Camera2_Manual.this)
                    .load(bitmapimgView)  // Đường dẫn ảnh
                    .into(imageViewSecond);  // Gắn ảnh vào ImageView
        });

        Bitmap[] pictureUnder = {null};
        imageViewSecond.setOnClickListener(v -> {
            // Ẩn popup khi click vào chính popup
            runOnUiThread(() -> {
                try {
                    PictureSelector.create(Activity_Camera2_Manual.this)
                            .openGallery(SelectMimeType.ofImage())  // Open gallery to pick image
                            .setImageEngine(GlideEngine.createGlideEngine())  // Use Glide for loading image
                            .forResult(new OnResultCallbackListener<LocalMedia>() {
                                @Override
                                public void onResult(ArrayList<LocalMedia> result) {
                                    if (result != null && !result.isEmpty()) {
                                        // Lấy đường dẫn ảnh đầu tiên trong danh sách kết quả
                                        String imagePath = result.get(0).getPath();

                                        // Dùng Glide để tải ảnh vào ImageView
                                        Uri uri = Uri.parse(imagePath);
                                        try {
                                            InputStream inputStream = getContentResolver().openInputStream(uri); // Mở luồng từ URI
                                            pictureUnder[0]= BitmapFactory.decodeStream(inputStream); // Decode thành Bitmap
                                            assert inputStream != null;
                                            inputStream.close(); // Đóng luồng sau khi sử dụng
                                        } catch (Exception e) {
                                            Log.d("CameraEXP", "Error " + e);

                                        }
                                        pictureUnder[0]=imgSolve.convertToGrayscale(pictureUnder[0]);
                                        Glide.with(Activity_Camera2_Manual.this)
                                                .load(pictureUnder[0])  // Đường dẫn ảnh
                                                .into(imageViewSecond);  // Gắn ảnh vào ImageView

                                    }
                                }

                                @Override
                                public void onCancel() {
                                    // Handle cancel action if needed
                                }
                            });
                } catch (Exception e) {
                    Log.e("FrameLayoutError", "Error setting visibility for FrameLayout", e);
                }
            });

        });

        runOnUiThread(() -> {
            try {
                frameLayoutPopup.setVisibility(View.VISIBLE);
                textureView.setEnabled(false);

            } catch (Exception e) {
                Log.e("FrameLayoutError", "Error setting visibility for FrameLayout", e);
            }
        });


        int[] lightValue1 = {0}; // Adjust brightness based on SeekBar progress
        float[] contrastValue = {1.7f};
        Bitmap[] adjustedBitmap2 = {null};


        adjustedBitmap2[0] = imgSolve.adjustBrightness(bmp, lightValue1[0]);
        adjustedBitmap2[0] = imgSolve.adjustContrast(adjustedBitmap2[0], contrastValue[0]);
        adjustedBitmap2[0].setDensity(origin.getDensity());
        runOnUiThread(() -> {
            try {
                imageViewPreview.setImageBitmap(adjustedBitmap2[0]);
            } catch (Exception e) {
                Log.e("FrameLayoutError", "Error setting visibility for FrameLayout", e);
            }
        });
        seekBarLight.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                try {
                    lightValue1[0] = progress - 20; // Adjust brightness based on SeekBar progress

                    adjustedBitmap2[0] = imgSolve.adjustBrightness(bmp, lightValue1[0]);
                    adjustedBitmap2[0] = imgSolve.adjustContrast(adjustedBitmap2[0], contrastValue[0]);

                    imageViewPreview.setImageBitmap(adjustedBitmap2[0]);  // Show the adjusted image
                } catch (Exception e) {
                    Log.e("SeekBarError", "Error adjusting brightness", e);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekBarConst.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                try {
                    // Convert SeekBar value to contrast factor (1.0 is default, higher value increases contrast)
                    contrastValue[0] = (float) progress / 10.0f;
                    adjustedBitmap2[0] = imgSolve.adjustBrightness(bmp, lightValue1[0]);
                    adjustedBitmap2[0] = imgSolve.adjustContrast(adjustedBitmap2[0], contrastValue[0]);
                    imageViewPreview.setImageBitmap(adjustedBitmap2[0]);
                } catch (Exception e) {
                    Log.e("SeekBarError", "Error adjusting contrast", e);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });


        btnPrint.setOnClickListener(v -> {
            // Ẩn popup khi click vào chính popup
            try {
                textureView.setEnabled(true);
                //adjustedBitmap2[0]=imgSolve.applyMedianFilter(adjustedBitmap2[0],3);
                onClickPrint();
                printImage(
                        adjustedBitmap2[0],
                        10,
                        rbInch3.isChecked() ? PRINT_THREE_INCH : rbInch2.isChecked() ? PRINT_TWO_INCH : rbInch4.isChecked() ? PRINT_FOUR_INCH : 0,
                        !rbZero.isChecked(),
                        rbShake.isChecked() ? BITMAP_SHAKE : BITMAP_GATHER
                );


                printDrawableImage(pictureUnder[0]);
                runOnUiThread(() -> {
                    try {
                        frameLayoutPopup.setVisibility(View.GONE);
                        imageViewPreview.setImageBitmap(null);

                    } catch (Exception e) {
                        Log.e("FrameLayoutError", "Error setting visibility for FrameLayout", e);
                    }
                });
                imgSolve.clearCache();
                counterTime++;

            } catch (Exception e) {
                Log.e("PrintError", "Exception during printImage call", e);
            }
        });

        btnCancel.setOnClickListener(v -> {
            // Ẩn popup khi click vào chính popup
            try {
                frameLayoutPopup.setVisibility(View.GONE);
                imageViewPreview.setImageBitmap(null);
                textureView.setEnabled(true);
                imgSolve.clearCache();

            } catch (Exception e) {
                Log.e("PrintError", "Exception during printImage call", e);
            }
        });

    }
    public void printImage(final Bitmap bitmap, final int light, final int size,
                           final boolean isRotate, final int sype) {
        dialog = new ProgressDialog(Activity_Camera2_Manual.this);
        dialog.setMessage("Printing.....");
        dialog.setProgress(100);
        dialog.show();

        executorService.execute(() -> {
            Bitmap bitmapPrint = bitmap;
            bitmapPrint.setDensity(bitmap.getDensity());
            if (isRotate) {
                bitmapPrint = Utility.Tobitmap90(bitmapPrint);  // Xoay ảnh nếu cần
            }
            if (size != 0)
                bitmapPrint = Utility.Tobitmap(bitmapPrint, size, Utility.getHeight(size, bitmapPrint.getWidth(), bitmapPrint.getHeight()));


            try {
                //    Print.SetPrintDensity((byte)4);
                Print.setPrintResolution(203,203);
                Print.PrintBitmap(bitmapPrint, sype, light);  // In ảnh


            } catch (Exception e) {
                handler.sendEmptyMessage(PRINT_FAILURE);
            }
            bitmap.recycle();
            bitmapPrint.recycle();
            dialog.cancel();
        });
    }
    private void printDrawableImage(Bitmap pic) {
        // Load the drawable image as a Bitmap

        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.facebook);
        if(pic!=null)
        {
            bitmap=pic;
        }
        // Check if the Bitmap was loaded successfully

        // Call the existing printImage method to print the loaded image
        printImage2(bitmap, -25, 576, false, 2);
    }
    public void onClickPrint() {
        if (!checkClick.isClickEvent()) return;
        int iLeftMargin = 0;
        String formatted="";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            current = LocalDateTime.now(); // Lấy ngày và giờ hiện tại
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd"); // Định dạng
            formatted = current.format(formatter); // Chuỗi ngày giờ định dạng
        }
        try {
            String sText = counterTime +"   " +
                    "                                 "+formatted+"     ";





            int iAlignment = 0;
            int iAttribute;
            int iTextSize = 0;

           iAttribute =(16 | 32 | 2);

            PublicAction PAct = new PublicAction(Activity_Camera2_Manual.this);


            Print.SetLeftMargin(iLeftMargin);
            PAct.BeforePrintActionText();
            Print.PrintText(sText, iAlignment, iAttribute, iTextSize);

            PAct.AfterPrintActionText();
        } catch (Exception e) {
            Log.d("SDKSample", "Activity_TextFormat --> onClickPrint " + e.getMessage());
        }
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
                Print.PrintBitmap(bitmapPrint, sype, light);  // In ảnh
                Print.CutPaper(0,0); // Cắt giấy đầy đủ

            } catch (Exception e) {
                handler.sendEmptyMessage(PRINT_FAILURE);
            }

            bitmap.recycle();
            bitmapPrint.recycle();
            dialog.cancel();
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
            Size[] jpegSizes = map.getOutputSizes(ImageFormat.JPEG);
            Size largestSize = jpegSizes[0];
            for (Size size : jpegSizes) {
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
            captureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, ExpoValue);  // 100ms
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
                    Toast.makeText(Activity_Camera2_Manual.this, "Configuration change", Toast.LENGTH_SHORT).show();
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
            cameraId = manager.getCameraIdList()[1];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(Activity_Camera2_Manual.this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                return;
            }

            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            Log.d("OpenCamera", "Error " + e);
        }
    }
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
                Toast.makeText(Activity_Camera2_Manual.this, "Sorry!!!, you can't use this app without granting camera permission", Toast.LENGTH_LONG).show();
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
        File file = new File(getCacheDir(), "photo_" + System.currentTimeMillis() + ".jpg");

        // Lưu dữ liệu byte vào tệp
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(bytes);
            fos.flush();
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), options);

        // Truyền đường dẫn của tệp cache cho hàm setPrintDialog2
        setPrintDialog2(file.getPath());
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
