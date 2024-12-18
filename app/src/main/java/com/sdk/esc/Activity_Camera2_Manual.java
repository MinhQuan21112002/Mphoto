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
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.bumptech.glide.Glide;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import print.Print;

public class Activity_Camera2_Manual extends AppCompatActivity {


    ImageSolve imgSolve;
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
    private final int PRINT_THREE_INCH = 576;
    private final int BITMAP_SHAKE = 1;
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

    protected CameraCaptureSession cameraCaptureSessions;
    private Size imageDimension;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    FrameLayout frameLayoutPopup;
    Button btnPrint;
    Button btnCancel;
    ImageView imageViewPreview ;

    ImageView imageViewSecond;
    boolean havingUsb=false;
    Button decrease;
    Button increase;
    EditText numberCount;
    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera2_manual);
        SharedPreferences sharedPreferences = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);

        btnPrint=findViewById(R.id.btnPrint);
        btnCancel=findViewById(R.id.btnCancel);
        ISOvalue = Integer.parseInt(sharedPreferences.getString("isovalue", "400"));
        ExpoValue=Integer.parseInt(sharedPreferences.getString("epxvalue", "30000000"));
     //   light=Integer.parseInt(sharedPreferences.getString("light", "10"));
      //  contrast=sharedPreferences.getFloat("contrast", 1.3f);
        imgSolve = new ImageSolve(this);

        btnPrint.setEnabled(false);
        btnCancel.setEnabled(false);

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
        ImageButton backButton = findViewById(R.id.button_back);

        assert textureView != null;
        textureView.setSurfaceTextureListener(textureListener);

        textureView.setOnClickListener(v -> {
            btnPrint.setEnabled(true);
            btnCancel.setEnabled(true);

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
                takePicture(); // Gọi hàm chụp ảnh sau khi đếm ngược xong
                textureView.setEnabled(false);
            }
        });

        settingButton.setOnClickListener(v -> showSettingDialog());
        backButton.setOnClickListener(v -> {
            Intent intent2 = new Intent(Activity_Camera2_Manual.this, Activity_Camera2.class); // Chuyển đến SettingsActivity
            startActivity(intent2); // Bắt đầu Activity mới
        });
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

    private void showSettingDialog() {
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_2_columns_layout, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        // Lấy các thành phần từ layout dialog
        TextView isoText1 = dialogView.findViewById(R.id.isoText1);
        TextView isoText2 = dialogView.findViewById(R.id.isoText2);
        TextView isoText3 = dialogView.findViewById(R.id.isoText3);
        TextView isoText4 = dialogView.findViewById(R.id.isoText4);
        TextView isoText5 = dialogView.findViewById(R.id.isoText5);
        TextView isoText6 = dialogView.findViewById(R.id.isoText6);
        TextView isoText7 = dialogView.findViewById(R.id.isoText7);

        TextView exposureText1 = dialogView.findViewById(R.id.exposureText1);
        TextView exposureText2 = dialogView.findViewById(R.id.exposureText2);
        TextView exposureText3 = dialogView.findViewById(R.id.exposureText3);
        TextView exposureText4 = dialogView.findViewById(R.id.exposureText4);
        TextView exposureText5 = dialogView.findViewById(R.id.exposureText5);
        TextView exposureText6 = dialogView.findViewById(R.id.exposureText6);
        TextView exposureText7 = dialogView.findViewById(R.id.exposureText7);

        // Các mức ISO và Exposure
        final String[] isoLevels = { "200", "300", "400", "500", "600", "700", "800" };
        final String[] exposureLevelsDisplay = { "0.2s", "0.3s", "0.4s", "0.5s", "0.6s", "0.7s", "0.8s" };
        final String[] exposureLevels = { "20000000", "30000000", "40000000", "50000000", "60000000", "70000000", "80000000" };

        // Lấy isovalue và exposurevalue từ đâu đó
        String isovalue = String.valueOf(ISOvalue);     // Thay vào giá trị của isovalue
        String exposurevalue = String.valueOf(ExpoValue); // Thay vào giá trị của exposurevalue

        // Hiển thị danh sách cột bên trái (ISO) và cột bên phải (Exposure)
        isoText1.setText(isoLevels[0]);
        exposureText1.setText(exposureLevelsDisplay[0]);
        if (isoLevels[0].equals(isovalue)) {
            isoText1.setBackgroundColor(Color.GRAY);
        }
        if (exposureLevels[0].equals(exposurevalue)) {
            exposureText1.setBackgroundColor(Color.GRAY);
        }

        isoText2.setText(isoLevels[1]);
        exposureText2.setText(exposureLevelsDisplay[1]);
        if (isoLevels[1].equals(isovalue)) {
            isoText2.setBackgroundColor(Color.GRAY);
        }
        if (exposureLevels[1].equals(exposurevalue)) {
            exposureText2.setBackgroundColor(Color.GRAY);
        }

        isoText3.setText(isoLevels[2]);
        exposureText3.setText(exposureLevelsDisplay[2]);
        if (isoLevels[2].equals(isovalue)) {
            isoText3.setBackgroundColor(Color.GRAY);
        }
        if (exposureLevels[2].equals(exposurevalue)) {
            exposureText3.setBackgroundColor(Color.GRAY);
        }

        isoText4.setText(isoLevels[3]);
        exposureText4.setText(exposureLevelsDisplay[3]);
        if (isoLevels[3].equals(isovalue)) {
            isoText4.setBackgroundColor(Color.GRAY);
        }
        if (exposureLevels[3].equals(exposurevalue)) {
            exposureText4.setBackgroundColor(Color.GRAY);
        }

        isoText5.setText(isoLevels[4]);
        exposureText5.setText(exposureLevelsDisplay[4]);
        if (isoLevels[4].equals(isovalue)) {
            isoText5.setBackgroundColor(Color.GRAY);
        }
        if (exposureLevels[4].equals(exposurevalue)) {
            exposureText5.setBackgroundColor(Color.GRAY);
        }

        isoText6.setText(isoLevels[5]);
        exposureText6.setText(exposureLevelsDisplay[5]);
        if (isoLevels[5].equals(isovalue)) {
            isoText6.setBackgroundColor(Color.GRAY);
        }
        if (exposureLevels[5].equals(exposurevalue)) {
            exposureText6.setBackgroundColor(Color.GRAY);
        }

        isoText7.setText(isoLevels[6]);
        exposureText7.setText(exposureLevelsDisplay[6]);
        if (isoLevels[6].equals(isovalue)) {
            isoText7.setBackgroundColor(Color.GRAY);
        }
        if (exposureLevels[6].equals(exposurevalue)) {
            exposureText7.setBackgroundColor(Color.GRAY);
        }

        // Set click listeners
        isoText1.setOnClickListener(v -> {
            applyISO(isoLevels[0]);
            updateISO(isoLevels[0]);
            dialog.dismiss();
            reloadDialog(); // Reload lại dialog
        });

        isoText2.setOnClickListener(v -> {
            applyISO(isoLevels[1]);
            updateISO(isoLevels[1]);
            dialog.dismiss();
            reloadDialog(); // Reload lại dialog
        });

        isoText3.setOnClickListener(v -> {
            applyISO(isoLevels[2]);
            updateISO(isoLevels[2]);
            dialog.dismiss();
            reloadDialog(); // Reload lại dialog
        });

        isoText4.setOnClickListener(v -> {
            applyISO(isoLevels[3]);
            updateISO(isoLevels[3]);
            dialog.dismiss();
            reloadDialog(); // Reload lại dialog
        });

        isoText5.setOnClickListener(v -> {
            applyISO(isoLevels[4]);
            updateISO(isoLevels[4]);
            dialog.dismiss();
            reloadDialog(); // Reload lại dialog
        });

        isoText6.setOnClickListener(v -> {
            applyISO(isoLevels[5]);
            updateISO(isoLevels[5]);
            dialog.dismiss();
            reloadDialog(); // Reload lại dialog
        });

        isoText7.setOnClickListener(v -> {
            applyISO(isoLevels[6]);
            updateISO(isoLevels[6]);
            dialog.dismiss();
            reloadDialog(); // Reload lại dialog
        });

        exposureText1.setOnClickListener(v -> {
            applyExpose(exposureLevels[0]);
            updateExposure(exposureLevels[0]);
            dialog.dismiss();
            reloadDialog(); // Reload lại dialog
        });

        exposureText2.setOnClickListener(v -> {
            applyExpose(exposureLevels[1]);
            updateExposure(exposureLevels[1]);
            dialog.dismiss();
            reloadDialog(); // Reload lại dialog

        });

        exposureText3.setOnClickListener(v -> {
            applyExpose(exposureLevels[2]);
            updateExposure(exposureLevels[2]);
            dialog.dismiss();
            reloadDialog(); // Reload lại dialog

        });

        exposureText4.setOnClickListener(v -> {
            applyExpose(exposureLevels[3]);
            updateExposure(exposureLevels[3]);
            dialog.dismiss();
            reloadDialog(); // Reload lại dialog

        });

        exposureText5.setOnClickListener(v -> {
            applyExpose(exposureLevels[4]);
            updateExposure(exposureLevels[4]);
            dialog.dismiss();
            reloadDialog(); // Reload lại dialog

        });

        exposureText6.setOnClickListener(v -> {
            applyExpose(exposureLevels[5]);
            updateExposure(exposureLevels[5]);
            dialog.dismiss();
            reloadDialog(); // Reload lại dialog

        });

        exposureText7.setOnClickListener(v -> {
            applyExpose(exposureLevels[6]);
            updateExposure(exposureLevels[6]);
            dialog.dismiss();
            reloadDialog(); // Reload lại dialog

        });
        builder.setPositiveButton("OK", (dialog1, which) -> dialog1.dismiss());
        dialog.show();
    }


    private void reloadDialog() {
        showSettingDialog(); // Gọi lại showSettingDialog() để load lại dialog
    }

    // Hiển thị dialog chọn ISO


    private void applyISO(String iso) {
        try {
            SharedPreferences sharedPreferences = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("isovalue", iso).apply();
            ISOvalue = Integer.parseInt(iso); // Cập nhật giá trị mới
            Log.d("ISO_SETTING", "ISO mới được áp dụng: " + ISOvalue);
            Toast.makeText(getApplicationContext(), "ISO hiện tại: " + ISOvalue, Toast.LENGTH_SHORT).show();
        } catch (NumberFormatException e) {
            Log.e("ISO_SETTING", "Không thể chuyển đổi giá trị ISO: " + iso);
        }
    }
    private void updateISO(String newISO) {
        try {
            // Cập nhật giá trị ISO
            captureRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, Integer.parseInt(newISO));

            // Áp dụng request mới
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, null);
            Log.d("CameraISO", "ISO đã cập nhật: " + newISO);
        } catch (CameraAccessException e) {
            Log.d("CameraISO", "Lo" + e.getMessage());
        }
    }
    private void applyExpose(String exp) {
        try {
            SharedPreferences sharedPreferences = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("epxvalue", exp).apply();
            ExpoValue = Long.parseLong(exp); // Cập nhật giá trị mới
            Log.d("EXP_SETTING", "EXP mới được áp dụng: " + ExpoValue);
            Toast.makeText(getApplicationContext(), "expo hiện tại: " + ExpoValue, Toast.LENGTH_SHORT).show();
        } catch (NumberFormatException e) {
            Log.e("EXP_SETTING", "Không thể chuyển đổi giá trị expo: " + exp);
        }
    }
    private void updateExposure(String exp) {
        try {
            // Cập nhật giá trị ISO
            captureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, Long.parseLong(exp));

            // Áp dụng request mới
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, null);
            Log.d("CameraEXP", "EXP đã cập nhật: " + exp);
        } catch (CameraAccessException e) {
            Log.d("CameraEXP", "Error " + e);
        }
    }
    private void setPrintDialog2(String path) {

        Bitmap origin = BitmapFactory.decodeFile(path);

        int dpi = origin.getDensity();
        if (dpi == 0) dpi = 203; // 기본 DPI 설정

        // Bitmap을 Mat 객체로 변환
        origin = origin.copy(Bitmap.Config.ARGB_8888, true); // Bitmap 포맷을 ARGB_8888로 변환
        Mat matOriginal = new Mat();
        Utils.bitmapToMat(origin, matOriginal);

        // 흑백 이미지로 변환
        Mat matGray = new Mat();
        Imgproc.cvtColor(matOriginal, matGray, Imgproc.COLOR_BGR2GRAY);

        // CLAHE 적용
        Mat matCLAHE = new Mat();
        CLAHE clahe = Imgproc.createCLAHE(0.4, new org.opencv.core.Size(3, 3));
        clahe.apply(matGray, matCLAHE);

// *가우시안 블러 적용*
        Mat matBlurred = new Mat();
        Imgproc.GaussianBlur(matCLAHE, matBlurred, new org.opencv.core.Size(3, 3), 0);

// Mat을 Bitmap으로 변환
        Bitmap processedBitmap = Bitmap.createBitmap(matBlurred.cols(), matBlurred.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(matBlurred, processedBitmap);
        float widthMm = 80; // 출력 폭 (mm)
        int widthPixels = (int) (widthMm * dpi / 25.4);

        Bitmap resizedBitmap = imgSolve.resizeBitmapWithGPUImage(Activity_Camera2_Manual.this, processedBitmap, widthPixels);

        AtomicReference<Bitmap> bmp = new AtomicReference<>(imgSolve.applySharpening(resizedBitmap, 1.5f));
        bmp.get().setDensity(dpi);
        frameLayoutPopup=findViewById(R.id.frame_layout);

        btnPrint=findViewById(R.id.btnPrint);
        btnCancel=findViewById(R.id.btnCancel);

        imageViewPreview = findViewById(R.id.imageViewPreview);

        imageViewSecond = findViewById(R.id.imageViewSecond);
        increase=findViewById(R.id.btnIncrease);
        decrease=findViewById(R.id.btnDecrease);
        numberCount=findViewById(R.id.editTextNumber);
        // Lấy giá trị nguyên sau khi nhân với 10

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
//        imageViewSecond.setOnClickListener(v -> {
//            // Ẩn popup khi click vào chính popup
//
//                try {
//                    PictureSelector.create(Activity_Camera2_Manual.this)
//                            .openGallery(SelectMimeType.ofImage())  // Open gallery to pick image
//                            .setImageEngine(GlideEngine.createGlideEngine())  // Use Glide for loading image
//                            .forResult(new OnResultCallbackListener<LocalMedia>() {
//                                @Override
//                                public void onResult(ArrayList<LocalMedia> result) {
//                                    if (result != null && !result.isEmpty()) {
//                                        // Lấy đường dẫn ảnh đầu tiên trong danh sách kết quả
//                                        String imagePath = result.get(0).getPath();
//
//                                        // Dùng Glide để tải ảnh vào ImageView
//                                        Uri uri = Uri.parse(imagePath);
//                                        try {
//                                            InputStream inputStream = getContentResolver().openInputStream(uri); // Mở luồng từ URI
//                                            pictureUnder[0]= BitmapFactory.decodeStream(inputStream); // Decode thành Bitmap
//                                            assert inputStream != null;
//                                            inputStream.close(); // Đóng luồng sau khi sử dụng
//                                        } catch (Exception e) {
//                                            Log.d("CameraEXP", "Error " + e);
//
//                                        }
//                                        pictureUnder[0]=imgSolve.convertToGrayscale(pictureUnder[0]);
//                                        Glide.with(Activity_Camera2_Manual.this)
//                                                .load(pictureUnder[0])  // Đường dẫn ảnh
//                                                .into(imageViewSecond);  // Gắn ảnh vào ImageView
//
//                                    }
//                                }
//
//                                @Override
//                                public void onCancel() {
//                                    // Handle cancel action if needed
//                                }
//                            });
//                } catch (Exception e) {
//                    Log.e("FrameLayoutError", "Error setting visibility for FrameLayout", e);
//                }
//
//        });

        runOnUiThread(() -> {
            try {
                frameLayoutPopup.setVisibility(View.VISIBLE);
                textureView.setEnabled(false);

            } catch (Exception e) {
                Log.e("FrameLayoutError", "Error setting visibility for FrameLayout", e);
            }
        });

        float contrast = 1.4f;
        int light = 15;
        int[] lightValue1 = {light}; // Adjust brightness based on SeekBar progress
        float[] contrastValue = {contrast};
        Bitmap[] adjustedBitmap2 = {null};


        adjustedBitmap2[0] = imgSolve.adjustBrightness(bmp.get(), lightValue1[0]);
        adjustedBitmap2[0] = imgSolve.adjustContrast(adjustedBitmap2[0], contrastValue[0]);
        adjustedBitmap2[0].setDensity(origin.getDensity());
        @SuppressLint("UseCompatLoadingForDrawables") Drawable noel = getResources().getDrawable(R.drawable.noel6, null);
        Bitmap bitmapFrame = imgSolve.drawableToBitmap(noel);
        bitmapFrame = imgSolve.resizeBitmapMaintainAspect(bitmapFrame,800); // Nếu cần chuyển thành grayscale
        bitmapFrame=imgSolve.convertToGrayscale(bitmapFrame);
// Bitmap đã xử lý (adjustedBitmap2[0])
        Bitmap processedBitmap2 = adjustedBitmap2[0];
        processedBitmap2=imgSolve.cropLeftRightToSquare(processedBitmap2);
// Tạo Bitmap mới để kết hợp
        // Tính toán kích thước mới cho processedBitmap2
        int newWidth = (int) (processedBitmap2.getWidth()*1.3);
        int newHeight = (int) (processedBitmap2.getHeight()*1.3);

// Phóng to processedBitmap2
        Bitmap enlargedBitmap = Bitmap.createScaledBitmap(processedBitmap2, newWidth, newHeight, true);

        int compensation=0;
        // compensation=65 android 14
// Tạo Bitmap mới để kết hợp
        Bitmap combinedBitmap = Bitmap.createBitmap(
                bitmapFrame.getWidth()+compensation,
                bitmapFrame.getHeight()+compensation,
                Bitmap.Config.ARGB_8888
        );

// Vẽ bitmapFrame lên Canvas
        Canvas canvas = new Canvas(combinedBitmap);
        canvas.drawBitmap(bitmapFrame, 0, 0, null);

// Tính toán xOffset và yOffset để căn giữa enlargedBitmap bên trong bitmapFrame
        int xOffset = (bitmapFrame.getWidth() - enlargedBitmap.getWidth()) / 2+compensation;
        int yOffset = (bitmapFrame.getHeight() - enlargedBitmap.getHeight()) / 2+compensation;

// Vẽ enlargedBitmap đã căn giữa vào bên trong bitmapFrame
        canvas.drawBitmap(enlargedBitmap, xOffset, yOffset, null);

// Đặt mật độ cho combinedBitmap giống bitmapFrame
        combinedBitmap.setDensity(bitmapFrame.getDensity());

        runOnUiThread(() -> {
            try {
                imageViewPreview.setImageBitmap(combinedBitmap);
            } catch (Exception e) {
                Log.e("FrameLayoutError", "Error setting visibility for FrameLayout", e);
            }
        });


        btnPrint.setOnClickListener(v -> {
            // Ẩn popup khi click vào chính popup
            try {
                new Handler().postDelayed(() -> {
                    textureView.setEnabled(true); // Bật lại textureView sau 1.5 giây
                }, 1500);
                //adjustedBitmap2[0]=imgSolve.applyMedianFilter(adjustedBitmap2[0],3);
                onClickPrint();
                printImage(
                        combinedBitmap,
                        10,
                        PRINT_THREE_INCH , false,
                        BITMAP_SHAKE
                );


                printDrawableImage(pictureUnder[0]);

                imgSolve.clearCache();
                counterTime++;
                runOnUiThread(() -> {
                    try {
                        imageViewPreview.setImageResource(R.drawable.imagepreview);
                    } catch (Exception e) {
                        Log.e("FrameLayoutError", "Error setting visibility for FrameLayout", e);
                    }
                });


            } catch (Exception e) {
                Log.e("PrintError", "Exception during printImage call", e);
            }
            btnPrint.setEnabled(false);
            btnCancel.setEnabled(false);

            adjustedBitmap2[0]=null;
            bmp.set(null);

        });

        btnCancel.setOnClickListener(v -> {
            // Ẩn popup khi click vào chính popup
            try {


                new Handler().postDelayed(() -> {
                    textureView.setEnabled(true); // Bật lại textureView sau 1.5 giây
                }, 1500);
                imgSolve.clearCache();
                runOnUiThread(() -> {
                    try {
                        imageViewPreview.setImageResource(R.drawable.imagepreview);
                    } catch (Exception e) {
                        Log.e("FrameLayoutError", "Error setting visibility for FrameLayout", e);
                    }
                });

            } catch (Exception e) {
                Log.e("PrintError", "Exception during printImage call", e);
            }
            btnPrint.setEnabled(false);
            btnCancel.setEnabled(false);

            adjustedBitmap2[0]=null;
            bmp.set(null);

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
            String cameraId = manager.getCameraIdList()[1];
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
    public void onBackPressed() {
        // Intent để chuyển từ ActivityCamera2Manual về ActivityCamera2
        Intent intent = new Intent(Activity_Camera2_Manual.this, Activity_Camera2.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent); // Bắt đầu ActivityCamera2
        finish(); // Đóng ActivityCamera2Manual
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
