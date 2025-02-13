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
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
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
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.luck.picture.lib.basic.PictureSelector;
import com.luck.picture.lib.config.SelectMimeType;
import com.luck.picture.lib.entity.LocalMedia;
import com.luck.picture.lib.interfaces.OnResultCallbackListener;

import org.opencv.android.OpenCVLoader;

import java.io.ByteArrayOutputStream;
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
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import print.Print;

public class Activity_Camera2_Manual extends AppCompatActivity {

    private ProgressDialog progressDialog;

    ImageSolve imgSolve; // Class for image processing
    private int counterTime=1; // Using for counting number that we have captured
    LocalDateTime current; // Using for calculating Date time now
    private CameraDevice cameraDevice;
    private CaptureRequest.Builder captureRequestBuilder;
    private static final String TAG = "AndroidCameraApi";
    final private ExecutorService executorService = Executors.newSingleThreadExecutor();
    public Handler handler;
    TextView countdown; // Textview for counting down before capture image ( 3 2 1 )
    private TextureView textureView;
    private  int ISOvalue=400;
    private  long ExpoValue= 30000000;
    private final int PRINT_FAILURE = 0;
    private final int PRINT_THREE_INCH = 576;
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
    Bitmap image = null;
    ImageView imageViewSecond;
    boolean havingUsb=false;
    Button decrease;
    Button increase;
    EditText numberCount;
    ImageButton buttonUp;
    ImageButton buttonDown;
    ImageButton buttonList;
    List<String> bitmapList;
    List<String> bitmapListImageView2;
    int currentIndexImageView2;
    ImageView frame;
    int currentIndex;
    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    protected void onCreate(Bundle savedInstanceState) {


        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        String lang = prefs.getString("language", "vi");
        changeLanguageFirst(lang); // ✅ Gọi trước super.onCreate()
        super.onCreate(savedInstanceState);



        //initialize the interface
        setContentView(R.layout.activity_camera2_manual);
        buttonUp=findViewById(R.id.button_up);
        buttonDown=findViewById(R.id.button_down);
        buttonList=findViewById(R.id.button_list);
        increase=findViewById(R.id.btnIncrease);
        decrease=findViewById(R.id.btnDecrease);
        numberCount=findViewById(R.id.editTextNumber);
        btnPrint=findViewById(R.id.btnPrint);
        btnCancel=findViewById(R.id.btnCancel);
        textureView = findViewById(R.id.texture);
        imageViewSecond=findViewById(R.id.imageViewSecond);
        countdown= findViewById(R.id.countdownTextManual);
        frame=findViewById(R.id.imageView);
        ImageButton btnChangeLanguage = findViewById(R.id.btnChangeLanguage);
        ImageButton settingButton = findViewById(R.id.button_settings);
        ImageButton backButton = findViewById(R.id.button_back);
        //-----------------------------------------------------------------------------

        // Get value for iso and exposure time that saved in SharedPreferences
        SharedPreferences sharedPreferences = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        ISOvalue = Integer.parseInt(sharedPreferences.getString("isovalue", "400"));
        ExpoValue=Integer.parseInt(sharedPreferences.getString("epxvalue", "30000000"));

        counterTime=sharedPreferences.getInt("counterTime", 1);

        btnChangeLanguage.setOnClickListener(v -> {
            final String[] languages = {getString(R.string.korean), getString(R.string.english), getString(R.string.vietnamese),};
            final String[] langCodes = {"ko", "en", "vi"};

            AlertDialog.Builder builder = new AlertDialog.Builder(Activity_Camera2_Manual.this);
            builder.setTitle("Select Language");
            builder.setItems(languages, (dialog, which) -> {
                String selectedLangCode = langCodes[which];
                changeLanguage(selectedLangCode);
            });
            builder.show();
        });

        //xu ly anh phu -------------------------------------------------------------
        SharedPreferences sharedPreferences2 = getSharedPreferences("MyAppPrefs2", MODE_PRIVATE);
        currentIndexImageView2=sharedPreferences2.getInt("indexImageView2", 0);
        String jsonStringImageview2 = sharedPreferences2.getString("ImageViewList", "[]");
        Gson gson1 = new Gson();
        bitmapListImageView2 = gson1.fromJson(jsonStringImageview2, new TypeToken<List<String>>() {}.getType());

        updateImageView2(currentIndexImageView2);
        //Load anh phu
        if (!bitmapListImageView2.isEmpty()) {
            String encodedBitmap = bitmapListImageView2.get(currentIndexImageView2);
            byte[] bitmapBytes = Base64.decode(encodedBitmap, Base64.DEFAULT);
            image= BitmapFactory.decodeByteArray(bitmapBytes, 0, bitmapBytes.length);
        }
        else {
            image = BitmapFactory.decodeResource(getResources(), R.drawable.bottom);
        }
        runOnUiThread(() -> {
            Glide.with(Activity_Camera2_Manual.this)
                    .load(image)  // Đường dẫn ảnh
                    .into(imageViewSecond);  // Gắn ảnh vào ImageView
        });

        imageViewSecond.setOnClickListener(v -> showImagePickerDialog());

        //-----------------------------------------------------------

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

        imgSolve = new ImageSolve(this);
        btnPrint.setEnabled(false);
        btnCancel.setEnabled(false);
        //-----------------------------------------------------------------------------
        if (OpenCVLoader.initDebug()) {
            Log.d("OpenCV", "OpenCV được tải thành công!");
        } else {
            Log.d("OpenCV", "Lỗi: OpenCV không được tải.");
        }


        // Check the connected USB device as soon as the application starts -------------
        Intent intent = new Intent(ACTION_USB_PERMISSION);
        intent.setPackage(Activity_Camera2_Manual.this.getPackageName());
        IntentFilter filter = new IntentFilter();
        mPermissionIntent = PendingIntent.getBroadcast(Activity_Camera2_Manual.this, 0, intent, PendingIntent.FLAG_MUTABLE);
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

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
        Activity_Camera2_Manual.this.registerReceiver(mUsbReceiver, filter, RECEIVER_EXPORTED);
        //-----------------------------------------------------------------------------


        // Check for behavior of textureview if you have connected Printer or not
        assert textureView != null;
        textureView.setSurfaceTextureListener(textureListener);
        countdown.setVisibility(View.INVISIBLE);
        textureView.setOnClickListener(v -> {

            if (!Print.IsOpened()) {
                Toast.makeText(Activity_Camera2_Manual.this, getString(R.string.please_connect_printer), Toast.LENGTH_SHORT).show();
                try {
                    if(havingUsb)// Check if you have connected Printer or not
                    {
                        connectUSB(); // If you not, it will call function connectUSB();
                    }
                } catch (NumberFormatException e) {
                    Toast.makeText(getApplicationContext(), "Can't find Printer", Toast.LENGTH_SHORT).show();
                }

            }
            else{
                // If you have did that , it will start capturing picture
//                startCountdown();
                takePicture();
                textureView.setEnabled(false);
            }
        });
        //-----------------------------------------------------------------------------


        // function for behavior of clicking setting button
        settingButton.setOnClickListener(v -> showSettingDialog());
        // function for behavior of clicking back Button
        backButton.setOnClickListener(v -> {
            Intent intent2 = new Intent(Activity_Camera2_Manual.this, Activity_Camera2.class); // Chuyển đến SettingsActivity
            startActivity(intent2); // Bắt đầu Activity mới
        });




        //Add image Frame and edit
        buttonList.setOnClickListener(v -> showImageFrameDialog());
        SharedPreferences preferences = getSharedPreferences("FrameImage", Context.MODE_PRIVATE);
        String jsonString = preferences.getString("bitmap_list", "[]");
        Gson gson = new Gson();
        bitmapList = gson.fromJson(jsonString, new TypeToken<List<String>>() {}.getType());
        currentIndex= preferences.getInt("current_index", 0);
        updateImageView(currentIndex);

        // Processing with "Up" button
        buttonDown.setOnClickListener(v -> {
            String jsonString2 = preferences.getString("bitmap_list", "[]");
            Gson gson2 = new Gson();
            bitmapList = gson2.fromJson(jsonString2, new TypeToken<List<String>>() {}.getType());
            if (currentIndex < bitmapList.size() - 1) { // Không vượt quá danh sách
                currentIndex++;
                updateImageView(currentIndex);
                saveCurrentIndex(currentIndex);
            }
        });

        // Processing with "Down" button
        buttonUp.setOnClickListener(v -> {
            String jsonString2 = preferences.getString("bitmap_list", "[]");
            Gson gson2 = new Gson();
            bitmapList = gson2.fromJson(jsonString2, new TypeToken<List<String>>() {}.getType());
            if (currentIndex > 0) { // Không nhỏ hơn 0
                currentIndex--;
                updateImageView(currentIndex);
                saveCurrentIndex(currentIndex);
            }
        });





        // Lấy giá trị nguyên sau khi nhân với 10

        numberCount.setText(String.valueOf(counterTime));
        decrease.setOnClickListener(v -> {
            if(counterTime>0) {
                counterTime--; // Giảm giá trị
                numberCount.setText(String.valueOf(counterTime)); // Cập nhật lại EditText
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putInt("counterTime", counterTime);
                editor.apply();
            }
        });
        numberCount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Không cần làm gì ở đây
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {
                    int newCounterTime = Integer.parseInt(s.toString());
                    if (newCounterTime >= 0) {
                        counterTime = newCounterTime;
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putInt("counterTime", counterTime);
                        editor.apply();
                    }
                } catch (NumberFormatException e) {
                    // Handle the exception if input is not a number
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Không cần làm gì ở đây
            }
        });

        // Xử lý sự kiện khi nhấn nút tăng
        increase.setOnClickListener(v -> {
            counterTime++; // Tăng giá trị
            numberCount.setText(String.valueOf(counterTime)); // Cập nhật lại EditText
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putInt("counterTime", counterTime);
            editor.apply();
        });
    }

    public void changeLanguage(String langCode) {
        // Lưu lại trong SharedPreferences nếu cần
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        prefs.edit().putString("language", langCode).apply();

        // Thay đổi Locale
        Locale locale = new Locale(langCode);
        Locale.setDefault(locale);
        Configuration config = new Configuration();
        config.setLocale(locale);
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());

        // Khởi động lại activity để áp dụng
        Intent intent = getIntent();
        finish();
        startActivity(intent);
    }
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

    private void updateImageView(int index) {
        if (bitmapList != null && index < bitmapList.size()) {
            String encodedBitmap = bitmapList.get(index);
            byte[] decodedBytes = Base64.decode(encodedBitmap, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);

            // Tính toán kích thước mới theo tỷ lệ 16:10
            int originalWidth = bitmap.getWidth();
            int targetHeight = (int) (originalWidth * (3.0 / 4.0));

            // Resize bitmap
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, originalWidth, targetHeight, true);

            // Set resized bitmap vào ImageView
            frame.setImageBitmap(resizedBitmap);
        }
    }
    private void updateImageView2(int index) {
        if (bitmapListImageView2 != null && index < bitmapListImageView2.size()) {
            String encodedBitmap = bitmapListImageView2.get(index);
            byte[] decodedBytes = Base64.decode(encodedBitmap, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
            // Set resized bitmap vào ImageView
            imageViewSecond.setImageBitmap(bitmap);
        }
    }
    private void saveCurrentIndex(int index) {
        SharedPreferences preferences = getSharedPreferences("FrameImage", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("current_index", index); // Lưu index hiện tại
        editor.apply(); // Áp dụng thay đổi
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
    @SuppressLint("NotifyDataSetChanged")
    private void showImagePickerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_image_picker, null);
        Button btnPickImage = dialogView.findViewById(R.id.btnPickImage);
//        Button btnPickImageDrive = dialogView.findViewById(R.id.btnPickImageDrive);
        RecyclerView recyclerView = dialogView.findViewById(R.id.recyclerViewImages);

        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));

        SharedPreferences preferences = getSharedPreferences("MyAppPrefs2", Context.MODE_PRIVATE);
        String jsonString = preferences.getString("ImageViewList", "[]");
        Gson gson = new Gson();
        bitmapListImageView2 = gson.fromJson(jsonString, new TypeToken<List<String>>() {}.getType());

        ImageViewListAdapter adapter = new ImageViewListAdapter(this, bitmapListImageView2,
                position -> {
                    // Kiểm tra nếu vị trí bị xóa trùng với hình ảnh đang hiển thị
                    SharedPreferences preferences2 = getSharedPreferences("MyAppPrefs2", Context.MODE_PRIVATE);
                    String jsonString2 = preferences2.getString("ImageViewList", "[]");
                    Gson gson2 = new Gson();
                    bitmapListImageView2 = gson2.fromJson(jsonString2, new TypeToken<List<String>>() {}.getType());
                    if(bitmapListImageView2.isEmpty()) {
                        @SuppressLint("UseCompatLoadingForDrawables")
                        Drawable noel = getResources().getDrawable(R.drawable.bottom, null);
                        Bitmap bitmapFrameNull = imgSolve.drawableToBitmap(noel);
                        imageViewSecond.setImageBitmap(bitmapFrameNull);
                    }
                    else if (position == currentIndexImageView2) { // currentDisplayedImagePosition là vị trí hình ảnh hiện tại trong ImageView


                        String encodedBitmap;
                        if(position==0) {
                            encodedBitmap = bitmapListImageView2.get(position);
                            SharedPreferences.Editor editor = preferences.edit();
                            editor.putInt("indexImageView2", currentIndexImageView2);
                            editor.apply();
                        }
                        else{
                            currentIndexImageView2=position-1;
                            encodedBitmap = bitmapListImageView2.get(position-1);
                            SharedPreferences.Editor editor = preferences.edit();
                            editor.putInt("indexImageView2", currentIndexImageView2);
                            editor.apply();
                        }
                        byte[] decodedBytes = Base64.decode(encodedBitmap, Base64.DEFAULT);
                        Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);


                        // Set resized bitmap vào ImageView
                        imageViewSecond.setImageBitmap(bitmap);

                    }

                    else {

                        String encodedBitmap ;
                        if(position==0) {
                            currentIndexImageView2=currentIndexImageView2-1;
                            encodedBitmap = bitmapListImageView2.get(currentIndexImageView2);
                            SharedPreferences.Editor editor = preferences.edit();
                            editor.putInt("indexImageView2", currentIndexImageView2);
                            editor.apply();
                        } else {
                            if(currentIndexImageView2==0) {
                                encodedBitmap = bitmapListImageView2.get(currentIndexImageView2);
                                SharedPreferences.Editor editor = preferences.edit();
                                editor.putInt("indexImageView2", currentIndexImageView2);
                                editor.apply();
                            }
                            else{
                                currentIndexImageView2=currentIndexImageView2-1;
                                encodedBitmap = bitmapListImageView2.get(currentIndexImageView2);
                                SharedPreferences.Editor editor = preferences.edit();
                                editor.putInt("indexImageView2", currentIndexImageView2);
                                editor.apply();
                            }
                        }
                        byte[] decodedBytes = Base64.decode(encodedBitmap, Base64.DEFAULT);
                        Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);



                        // Set resized bitmap vào ImageView
                        imageViewSecond.setImageBitmap(bitmap);
                    }
                },
                position2 -> {

                    String encodedBitmap =bitmapListImageView2.get(position2);
                    currentIndexImageView2=position2;
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putInt("indexImageView2", currentIndexImageView2);
                    editor.apply();
                    byte[] bitmapBytes = Base64.decode(encodedBitmap, Base64.DEFAULT);
                    Bitmap resizedBitmap= BitmapFactory.decodeByteArray(bitmapBytes, 0, bitmapBytes.length);
                    imageViewSecond.setImageBitmap(resizedBitmap);
                });

        recyclerView.setAdapter(adapter);

        builder.setView(dialogView);
        builder.setTitle(getString(R.string.image_list_title));
        builder.setPositiveButton(getString(R.string.close), (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = builder.create();
        dialog.show();

        // Di chuyển dialog đến góc trái
        Window window = dialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
            layoutParams.copyFrom(window.getAttributes());

            layoutParams.gravity = Gravity.TOP | Gravity.START; // Căn góc trái trên
            layoutParams.x = 0;  // Điều chỉnh khoảng cách từ lề trái (0 = sát lề)
            layoutParams.y = 100; // Điều chỉnh khoảng cách từ lề trên

            window.setAttributes(layoutParams);
        }

        btnPickImage.setOnClickListener(v -> {

            try {
                PictureSelector.create(Activity_Camera2_Manual.this)
                        .openGallery(SelectMimeType.ofImage())  // Mở thư viện ảnh
                        .setImageEngine(GlideEngine.createGlideEngine())  // Sử dụng Glide để tải ảnh
                        .forResult(new OnResultCallbackListener<LocalMedia>() {
                            @Override
                            public void onResult(ArrayList<LocalMedia> result) {
                                if (result != null && !result.isEmpty()) {
                                    showLoading(true); // Hiển thị vòng xoay

                                    ExecutorService executor = Executors.newSingleThreadExecutor();
                                    executor.execute(() -> {
                                        SharedPreferences preferences = getSharedPreferences("MyAppPrefs2", Context.MODE_PRIVATE);
                                        SharedPreferences.Editor editor = preferences.edit();
                                        Gson gson = new Gson();




                                        for (LocalMedia media : result) {
                                            String imagePath = media.getPath();
                                            Uri uri = Uri.parse(imagePath);

                                            try {
                                                // Kiểm tra ảnh có phải PNG không
//                                                String mimeType = getContentResolver().getType(uri);
//                                                if (mimeType == null || !mimeType.equals("image/png")) {
//                                                    runOnUiThread(() -> Toast.makeText(Activity_Camera2_Manual.this, "File không phải PNG!", Toast.LENGTH_SHORT).show());
//                                                    continue;
//                                                }

                                                ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
                                                if (pfd != null) {
                                                    long fileSizeInBytes = pfd.getStatSize();
                                                    pfd.close();

                                                    double fileSizeInMB = fileSizeInBytes / (1024.0 * 1024.0);
                                                    if (fileSizeInMB > 5) {
                                                        runOnUiThread(() -> Toast.makeText(Activity_Camera2_Manual.this, "File " + imagePath + " quá 5MB, bỏ qua!", Toast.LENGTH_SHORT).show());
                                                        continue;
                                                    }
                                                }

                                                InputStream inputStream = getContentResolver().openInputStream(uri);
                                                Bitmap image = BitmapFactory.decodeStream(inputStream);
                                                if (inputStream != null) inputStream.close();

                                                // Resize ảnh
                                                int targetWidth = 800;
                                                int targetHeight = image.getHeight()*800/ image.getWidth();
                                                image = Bitmap.createScaledBitmap(image, targetWidth, targetHeight, true);

                                                // Chuyển sang ảnh xám
                                                image = imgSolve.convertALPHA8(image);

                                                // Thay đổi tỷ lệ ảnh


                                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                                image.compress(Bitmap.CompressFormat.PNG, 100, baos);
                                                byte[] bitmapBytes = baos.toByteArray();
                                                String encodedBitmap = Base64.encodeToString(bitmapBytes, Base64.DEFAULT);

                                                bitmapListImageView2.add(encodedBitmap);

                                            } catch (Exception e) {
                                                Log.e("BitmapProcessing", "Lỗi xử lý ảnh: " + e.getMessage());
                                            }
                                        }

                                        // Lưu danh sách vào SharedPreferences
                                        editor.putString("ImageViewList", gson.toJson(bitmapListImageView2));
                                        editor.apply();

                                        runOnUiThread(() -> {
                                            // Cập nhật chỉ số ảnh hiện tại
                                            currentIndexImageView2 = bitmapListImageView2.size() - 1;
                                            editor.putInt("indexImageView2", currentIndexImageView2);
                                            editor.apply();

                                            // Hiển thị ảnh cuối cùng lên ImageView
                                            if (!bitmapListImageView2.isEmpty()) {
                                                byte[] bitmapBytes = Base64.decode(bitmapListImageView2.get(currentIndexImageView2), Base64.DEFAULT);
                                                Bitmap lastImage = BitmapFactory.decodeByteArray(bitmapBytes, 0, bitmapBytes.length);
                                                Glide.with(Activity_Camera2_Manual.this).load(lastImage).into(imageViewSecond);
                                            }

                                            showLoading(false); // Ẩn vòng xoay sau khi load xong

                                            // Đóng dialog hiện tại nếu có
                                            AlertDialog dialog = (AlertDialog) v.getTag();
                                            if (dialog != null) {
                                                dialog.dismiss();
                                            }

                                            showImagePickerDialog();  // Reload dialog
                                        });
                                    });
                                }
                            }

                            @Override
                            public void onCancel() {
                                // Xử lý nếu người dùng hủy chọn ảnh
                            }
                        });
            } catch (Exception e) {
                Log.e("FrameLayoutError", "Lỗi khi mở thư viện ảnh", e);
            }
        });
//        final ImageAdapterDrive[] adapterDrive = new ImageAdapterDrive[1];
//        btnPickImageDrive.setOnClickListener(v -> {
//            Dialog fullScreenDialog = new Dialog(Activity_Camera2_Manual.this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
//            fullScreenDialog.setContentView(R.layout.dialog_fullscreen_picker);
//
//            RecyclerView recyclerViewDrive = fullScreenDialog.findViewById(R.id.recyclerViewImagesDrive);
//            GridLayoutManager layoutManager = new GridLayoutManager(Activity_Camera2_Manual.this, 4);
//            recyclerViewDrive.setLayoutManager(layoutManager);
//
//            Button btnCancel = fullScreenDialog.findViewById(R.id.btnCancel);
//            Button btnAddImage = fullScreenDialog.findViewById(R.id.btnAddImage); // Nút Thêm ảnh
//
//            SharedPreferences sharedPreferences = getSharedPreferences("GoogleDrive", Context.MODE_PRIVATE);
//            String savedFolderId = sharedPreferences.getString("NameCardFolderId", null);
//            GoogleDriveService driveService = new GoogleDriveService(this);
//            if (savedFolderId != null) {
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//                    driveService.getImagesFromFolder(savedFolderId).thenAccept(imageUrls -> {
//                        if (imageUrls != null && !imageUrls.isEmpty()) {
//                            runOnUiThread(() -> {
//                                adapterDrive[0] = new ImageAdapterDrive(Activity_Camera2_Manual.this, imageUrls);
//                                recyclerViewDrive.setAdapter(adapterDrive[0]);
//                            });
//                        } else {
//                            Log.d("DriveImages", "Không có ảnh nào được tìm thấy.");
//                        }
//                    });
//                }
//            } else {
//                Log.e("DriveImages", "Không tìm thấy Folder ID trong SharedPreferences.");
//            }
//
//            // Xử lý sự kiện khi bấm nút Thêm ảnh
//            btnAddImage.setOnClickListener(v1 -> {
//                SharedPreferences preferencesDrive = getSharedPreferences("MyAppPrefs2", Context.MODE_PRIVATE);
//                SharedPreferences.Editor editor = preferencesDrive.edit();
//                Gson gsonDrive = new Gson();
//
//                // Lấy danh sách ảnh đã lưu trước đó
//                String jsonStringDrive = preferencesDrive.getString("DriveImageList", "[]");
//                List<String> savedImageList = gsonDrive.fromJson(jsonStringDrive, new TypeToken<List<String>>() {}.getType());
//
//                // Lấy danh sách ảnh đã chọn
//                List<String> selectedImages = adapterDrive[0].getSelectedImages();
//
//                if (selectedImages.isEmpty()) {
//                    Toast.makeText(Activity_Camera2_Manual.this, "Vui lòng chọn ít nhất một ảnh!", Toast.LENGTH_SHORT).show();
//                    return;
//                }
//
//                // Thêm ảnh mới nếu chưa có
//                for (String imageUrl : selectedImages) {
//                    if (!savedImageList.contains(imageUrl)) {
//                        savedImageList.add(imageUrl);
//                    }
//                }
//
//                // Lưu danh sách vào SharedPreferences
//                editor.putString("DriveImageList", gsonDrive.toJson(savedImageList));
//                editor.apply();
//
//                // Cập nhật RecyclerView
//                adapterDrive[0].notifyDataSetChanged();
//
//                Toast.makeText(Activity_Camera2_Manual.this, "Đã thêm ảnh vào danh sách!", Toast.LENGTH_SHORT).show();
//            });
//
//
//            btnCancel.setOnClickListener(v1 -> fullScreenDialog.dismiss());
//
//            fullScreenDialog.show();
//        });
//

        btnPickImage.setTag(dialog);  // Store dialog reference for later dismissal
    }
    /**
     * Hiển thị hoặc ẩn vòng xoay loading
     */
    private void showLoading(boolean show) {
        runOnUiThread(() -> {
            if (show) {
                progressDialog = new ProgressDialog(Activity_Camera2_Manual.this);
                progressDialog.setMessage("Đang xử lý ảnh...");
                progressDialog.setCancelable(false);
                progressDialog.show();
            } else {
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
            }
        });
    }

    public void showImageFrameDialog() {
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.list_image_frame, null);
        Button buttonAdd = dialogView.findViewById(R.id.btnAddImage);
        RecyclerView recyclerView = dialogView.findViewById(R.id.recyclerViewImages);

        // Setup RecyclerView with GridLayoutManager
        int numberOfColumns = 3; // Number of columns to display
        recyclerView.setLayoutManager(new GridLayoutManager(this, numberOfColumns));

        // Load image list from SharedPreferences
        SharedPreferences preferences = getSharedPreferences("FrameImage", Context.MODE_PRIVATE);
        String jsonString = preferences.getString("bitmap_list", "[]");
        Gson gson = new Gson();
        bitmapList = gson.fromJson(jsonString, new TypeToken<List<String>>() {}.getType());

        // Display the image list in RecyclerView
        ImageViewListAdapterFrame adapter = new ImageViewListAdapterFrame(this, bitmapList,
                position -> {
                    // Kiểm tra nếu vị trí bị xóa trùng với hình ảnh đang hiển thị
                    SharedPreferences preferences2 = getSharedPreferences("FrameImage", Context.MODE_PRIVATE);
                    String jsonString2 = preferences2.getString("bitmap_list", "[]");
                    Gson gson2 = new Gson();
                    bitmapList = gson2.fromJson(jsonString2, new TypeToken<List<String>>() {}.getType());
                    if(bitmapList.isEmpty()) {
                        @SuppressLint("UseCompatLoadingForDrawables")
                        Drawable noel = getResources().getDrawable(R.drawable.nothing, null);
                        Bitmap bitmapFrameNull = imgSolve.drawableToBitmap(noel);
                        frame.setImageBitmap(bitmapFrameNull);
                    }
                    else if (position == currentIndex) { // currentDisplayedImagePosition là vị trí hình ảnh hiện tại trong ImageView


                        String encodedBitmap;
                        if(position==0) {
                            encodedBitmap = bitmapList.get(position);
                            SharedPreferences.Editor editor = preferences.edit();
                            editor.putInt("current_index", currentIndex);
                            editor.apply();
                        }
                        else{
                            currentIndex=position-1;
                            encodedBitmap = bitmapList.get(position-1);
                            SharedPreferences.Editor editor = preferences.edit();
                            editor.putInt("current_index", currentIndex);
                            editor.apply();
                        }
                        byte[] decodedBytes = Base64.decode(encodedBitmap, Base64.DEFAULT);
                        Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);

                        // Tính toán kích thước mới theo tỷ lệ 16:10
                        int originalWidth = bitmap.getWidth();
                        int targetHeight = (int) (originalWidth * (3.0 / 4.0));

                        // Resize bitmap
                        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, originalWidth, targetHeight, true);

                        // Set resized bitmap vào ImageView
                        frame.setImageBitmap(resizedBitmap);

                    }

                    else {

                        String encodedBitmap ;
                        if(position==0) {
                            currentIndex=currentIndex-1;
                            encodedBitmap = bitmapList.get(currentIndex);
                            SharedPreferences.Editor editor = preferences.edit();
                            editor.putInt("current_index", currentIndex);
                            editor.apply();
                        } else {
                            if(currentIndex==0) {
                                encodedBitmap = bitmapList.get(currentIndex);
                                SharedPreferences.Editor editor = preferences.edit();
                                editor.putInt("current_index", currentIndex);
                                editor.apply();
                            }
                            else{
                                currentIndex=currentIndex-1;
                                encodedBitmap = bitmapList.get(currentIndex);
                                SharedPreferences.Editor editor = preferences.edit();
                                editor.putInt("current_index", currentIndex);
                                editor.apply();
                            }
                        }
                        byte[] decodedBytes = Base64.decode(encodedBitmap, Base64.DEFAULT);
                        Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);

                        // Tính toán kích thước mới theo tỷ lệ 16:10
                        int originalWidth = bitmap.getWidth();
                        int targetHeight = (int) (originalWidth * (3.0 / 4.0));

                        // Resize bitmap
                        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, originalWidth, targetHeight, true);

                        // Set resized bitmap vào ImageView
                        frame.setImageBitmap(resizedBitmap);
                    }

                },
                position2->{
                    String encodedBitmap =bitmapList.get(position2);
                    currentIndex=position2;
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putInt("current_index", currentIndex);
                    editor.apply();
                    byte[] bitmapBytes = Base64.decode(encodedBitmap, Base64.DEFAULT);
                    Bitmap bitmap= BitmapFactory.decodeByteArray(bitmapBytes, 0, bitmapBytes.length);
                    int originalWidth = bitmap.getWidth();
                    int newHeight = (int) (originalWidth * (3.0 / 4.0));
                    Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, originalWidth, newHeight, true);

                    frame.setImageBitmap(resizedBitmap);
                });
        recyclerView.setAdapter(adapter);

        buttonAdd.setOnClickListener(v -> {

            try {
                PictureSelector.create(Activity_Camera2_Manual.this)
                        .openGallery(SelectMimeType.ofImage())  // Mở thư viện ảnh
                        .setImageEngine(GlideEngine.createGlideEngine())  // Sử dụng Glide để tải ảnh
                        .forResult(new OnResultCallbackListener<LocalMedia>() {
                            @Override
                            public void onResult(ArrayList<LocalMedia> result) {
                                if (result != null && !result.isEmpty()) {
                                    showLoading(true); // Hiển thị vòng xoay

                                    ExecutorService executor = Executors.newSingleThreadExecutor();
                                    executor.execute(() -> {
                                        SharedPreferences preferences = getSharedPreferences("FrameImage", Context.MODE_PRIVATE);
                                        SharedPreferences.Editor editor = preferences.edit();
                                        Gson gson = new Gson();

                                        // Lấy danh sách ảnh đã lưu
                                        String jsonString = preferences.getString("bitmap_list", "[]");
                                        List<String> bitmapList = gson.fromJson(jsonString, new TypeToken<List<String>>() {}.getType());

                                        for (LocalMedia media : result) {
                                            String imagePath = media.getPath();
                                            Uri uri = Uri.parse(imagePath);

                                            try {
                                                // Kiểm tra ảnh có phải PNG không
                                                String mimeType = getContentResolver().getType(uri);
                                                if (mimeType == null || !mimeType.equals("image/png")) {
                                                    runOnUiThread(() -> Toast.makeText(Activity_Camera2_Manual.this, "File không phải PNG!", Toast.LENGTH_SHORT).show());
                                                    continue;
                                                }

                                                ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
                                                if (pfd != null) {
                                                    long fileSizeInBytes = pfd.getStatSize();
                                                    pfd.close();

                                                    double fileSizeInMB = fileSizeInBytes / (1024.0 * 1024.0);
                                                    if (fileSizeInMB > 5) {
                                                        runOnUiThread(() -> Toast.makeText(Activity_Camera2_Manual.this, "File " + imagePath + " quá 5MB, bỏ qua!", Toast.LENGTH_SHORT).show());
                                                        continue;
                                                    }
                                                }

                                                InputStream inputStream = getContentResolver().openInputStream(uri);
                                                Bitmap image = BitmapFactory.decodeStream(inputStream);
                                                if (inputStream != null) inputStream.close();

                                                // Resize ảnh
                                                int targetWidth = 800;
                                                int targetHeight = 800;
                                                image = Bitmap.createScaledBitmap(image, targetWidth, targetHeight, true);

                                                // Chuyển sang ảnh xám
                                                image = imgSolve.convertALPHA8(image);

                                                // Thay đổi tỷ lệ ảnh
                                                int originalWidth = image.getWidth();
                                                int newHeight = (int) (originalWidth * (3.0 / 4.0));
                                                Bitmap resizedBitmap = Bitmap.createScaledBitmap(image, originalWidth, newHeight, true);

                                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                                resizedBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
                                                byte[] bitmapBytes = baos.toByteArray();
                                                String encodedBitmap = Base64.encodeToString(bitmapBytes, Base64.DEFAULT);

                                                bitmapList.add(encodedBitmap);

                                            } catch (Exception e) {
                                                Log.e("BitmapProcessing", "Lỗi xử lý ảnh: " + e.getMessage());
                                            }
                                        }

                                        // Lưu danh sách vào SharedPreferences
                                        editor.putString("bitmap_list", gson.toJson(bitmapList));
                                        editor.apply();

                                        runOnUiThread(() -> {
                                            // Cập nhật chỉ số ảnh hiện tại
                                            currentIndex = bitmapList.size() - 1;
                                            editor.putInt("current_index", currentIndex);
                                            editor.apply();

                                            // Hiển thị ảnh cuối cùng lên ImageView
                                            if (!bitmapList.isEmpty()) {
                                                byte[] bitmapBytes = Base64.decode(bitmapList.get(currentIndex), Base64.DEFAULT);
                                                Bitmap lastImage = BitmapFactory.decodeByteArray(bitmapBytes, 0, bitmapBytes.length);
                                                Glide.with(Activity_Camera2_Manual.this).load(lastImage).into(frame);
                                            }

                                            showLoading(false); // Ẩn vòng xoay sau khi load xong

                                            // Đóng dialog hiện tại nếu có
                                            AlertDialog dialog = (AlertDialog) v.getTag();
                                            if (dialog != null) {
                                                dialog.dismiss();
                                            }

                                            showImageFrameDialog();  // Reload dialog
                                        });
                                    });
                                }
                            }

                            @Override
                            public void onCancel() {
                                // Xử lý nếu người dùng hủy chọn ảnh
                            }
                        });
            } catch (Exception e) {
                Log.e("FrameLayoutError", "Lỗi khi mở thư viện ảnh", e);
            }
        });

        // Setup dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setTitle(getString(R.string.image_list_title));
        builder.setPositiveButton(getString(R.string.close), (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = builder.create();
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
            layoutParams.copyFrom(window.getAttributes());

            layoutParams.gravity = Gravity.TOP | Gravity.END; // Căn góc phải trên
            layoutParams.x = 0;  // Khoảng cách từ lề phải (0 = sát lề)
            layoutParams.y = 100; // Khoảng cách từ lề trên

            window.setAttributes(layoutParams);
        }

        // Store dialog reference to dismiss later
        buttonAdd.setTag(dialog);  // Store dialog reference for later dismissal
    }


    private void showSettingDialog() {
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_2_columns_layout, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setNegativeButton(getString(R.string.close), (dialog, which) -> dialog.dismiss());
        AlertDialog dialog1 = builder.create();

        // Lấy các thành phần từ layout dialog
        TextView isoText8 = dialogView.findViewById(R.id.isoText8);
        TextView isoText1 = dialogView.findViewById(R.id.isoText1);
        TextView isoText2 = dialogView.findViewById(R.id.isoText2);
        TextView isoText3 = dialogView.findViewById(R.id.isoText3);
        TextView isoText4 = dialogView.findViewById(R.id.isoText4);
        TextView isoText5 = dialogView.findViewById(R.id.isoText5);
        TextView isoText6 = dialogView.findViewById(R.id.isoText6);
        TextView isoText7 = dialogView.findViewById(R.id.isoText7);

        TextView exposureText11 = dialogView.findViewById(R.id.exposureText11);
        TextView exposureText1 = dialogView.findViewById(R.id.exposureText1);
        TextView exposureText2 = dialogView.findViewById(R.id.exposureText2);
        TextView exposureText3 = dialogView.findViewById(R.id.exposureText3);
        TextView exposureText4 = dialogView.findViewById(R.id.exposureText4);
        TextView exposureText5 = dialogView.findViewById(R.id.exposureText5);
        TextView exposureText6 = dialogView.findViewById(R.id.exposureText6);
        TextView exposureText7 = dialogView.findViewById(R.id.exposureText7);
        TextView exposureText8 = dialogView.findViewById(R.id.exposureText8);
        TextView exposureText9 = dialogView.findViewById(R.id.exposureText9);
        TextView exposureText12 = dialogView.findViewById(R.id.exposureText12);
        TextView exposureText14 = dialogView.findViewById(R.id.exposureText14);
        TextView exposureText10 = dialogView.findViewById(R.id.exposureText10);


        // Các mức ISO và Exposure
        final String[] isoLevels = { "200", "300", "400", "500", "600", "700", "800" ,"100"};
        final String[] exposureLevelsDisplay = { "0.2s", "0.3s", "0.4s", "0.5s", "0.6s","0.7s","0.8s","0.9s", "1s","1.2s","1.4s", "2.0s","0.1s" };
        final String[] exposureLevels = { "20000000", "30000000", "40000000", "50000000", "60000000","70000000","80000000","90000000", "100000000","120000000","140000000", "250000000","10000000" };

        // Lấy isovalue và exposurevalue từ đâu đó
        String isovalue = String.valueOf(ISOvalue);     // Thay vào giá trị của isovalue
        String exposurevalue = String.valueOf(ExpoValue); // Thay vào giá trị của exposurevalue

        // Hiển thị danh sách cột bên trái (ISO) và cột bên phải (Exposure)
        isoText8.setText(isoLevels[7]);
        if (isoLevels[7].equals(isovalue)) {
            isoText8.setBackgroundColor(Color.GRAY);
        }
        exposureText11.setText(exposureLevelsDisplay[12]);
        if (exposureLevels[12].equals(exposurevalue)) {
            exposureText11.setBackgroundColor(Color.GRAY);
        }
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

        exposureText8.setText(exposureLevelsDisplay[7]);
        if (exposureLevels[7].equals(exposurevalue)) {
            exposureText8.setBackgroundColor(Color.GRAY);
        }

        exposureText9.setText(exposureLevelsDisplay[8]);
        if (exposureLevels[8].equals(exposurevalue)) {
            exposureText9.setBackgroundColor(Color.GRAY);
        }

        exposureText10.setText(exposureLevelsDisplay[11]);
        if (exposureLevels[11].equals(exposurevalue)) {
            exposureText10.setBackgroundColor(Color.GRAY);
        }
        exposureText12.setText(exposureLevelsDisplay[9]);
        if (exposureLevels[9].equals(exposurevalue)) {
            exposureText12.setBackgroundColor(Color.GRAY);
        }
        exposureText14.setText(exposureLevelsDisplay[10]);
        if (exposureLevels[10].equals(exposurevalue)) {
            exposureText14.setBackgroundColor(Color.GRAY);
        }


        // Set click listeners
        isoText8.setOnClickListener(v -> {
            applyISO(isoLevels[7]);
            updateISO(isoLevels[7]);
            dialog1.dismiss();
            reloadDialog(); // Reload lại dialog
        });

        // Set click listeners
        isoText1.setOnClickListener(v -> {
            applyISO(isoLevels[0]);
            updateISO(isoLevels[0]);
            dialog1.dismiss();
            reloadDialog(); // Reload lại dialog
        });

        isoText2.setOnClickListener(v -> {
            applyISO(isoLevels[1]);
            updateISO(isoLevels[1]);
            dialog1.dismiss();
            reloadDialog(); // Reload lại dialog
        });

        isoText3.setOnClickListener(v -> {
            applyISO(isoLevels[2]);
            updateISO(isoLevels[2]);
            dialog1.dismiss();
            reloadDialog(); // Reload lại dialog
        });

        isoText4.setOnClickListener(v -> {
            applyISO(isoLevels[3]);
            updateISO(isoLevels[3]);
            dialog1.dismiss();
            reloadDialog(); // Reload lại dialog
        });

        isoText5.setOnClickListener(v -> {
            applyISO(isoLevels[4]);
            updateISO(isoLevels[4]);
            dialog1.dismiss();
            reloadDialog(); // Reload lại dialog
        });

        isoText6.setOnClickListener(v -> {
            applyISO(isoLevels[5]);
            updateISO(isoLevels[5]);
            dialog1.dismiss();
            reloadDialog(); // Reload lại dialog
        });

        isoText7.setOnClickListener(v -> {
            applyISO(isoLevels[6]);
            updateISO(isoLevels[6]);
            dialog1.dismiss();
            reloadDialog(); // Reload lại dialog
        });

        exposureText1.setOnClickListener(v -> {
            applyExpose(exposureLevels[0]);
            updateExposure(exposureLevels[0]);
            dialog1.dismiss();
            reloadDialog(); // Reload lại dialog
        });

        exposureText2.setOnClickListener(v -> {
            applyExpose(exposureLevels[1]);
            updateExposure(exposureLevels[1]);
            dialog1.dismiss();
            reloadDialog(); // Reload lại dialog
        });

        exposureText3.setOnClickListener(v -> {
            applyExpose(exposureLevels[2]);
            updateExposure(exposureLevels[2]);
            dialog1.dismiss();
            reloadDialog(); // Reload lại dialog
        });

        exposureText4.setOnClickListener(v -> {
            applyExpose(exposureLevels[3]);
            updateExposure(exposureLevels[3]);
            dialog1.dismiss();
            reloadDialog(); // Reload lại dialog
        });

        exposureText5.setOnClickListener(v -> {
            applyExpose(exposureLevels[4]);
            updateExposure(exposureLevels[4]);
            dialog1.dismiss();
            reloadDialog(); // Reload lại dialog
        });

        exposureText6.setOnClickListener(v -> {
            applyExpose(exposureLevels[5]);
            updateExposure(exposureLevels[5]);
            dialog1.dismiss();
            reloadDialog(); // Reload lại dialog
        });

        exposureText7.setOnClickListener(v -> {
            applyExpose(exposureLevels[6]);
            updateExposure(exposureLevels[6]);
            dialog1.dismiss();
            reloadDialog(); // Reload lại dialog
        });

        exposureText8.setOnClickListener(v -> {
            applyExpose(exposureLevels[7]);
            updateExposure(exposureLevels[7]);
            dialog1.dismiss();
            reloadDialog(); // Reload lại dialog
        });
        exposureText9.setOnClickListener(v -> {
            applyExpose(exposureLevels[8]);
            updateExposure(exposureLevels[8]);
            dialog1.dismiss();
            reloadDialog(); // Reload lại dialog
        });
        exposureText10.setOnClickListener(v -> {
            applyExpose(exposureLevels[11]);
            updateExposure(exposureLevels[11]);
            dialog1.dismiss();
            reloadDialog(); // Reload lại dialog
        });
        exposureText12.setOnClickListener(v -> {
            applyExpose(exposureLevels[9]);
            updateExposure(exposureLevels[9]);
            dialog1.dismiss();
            reloadDialog(); // Reload lại dialog
        });
        exposureText14.setOnClickListener(v -> {
            applyExpose(exposureLevels[10]);
            updateExposure(exposureLevels[10]);
            dialog1.dismiss();
            reloadDialog(); // Reload lại dialog
        });

        exposureText11.setOnClickListener(v -> {
            applyExpose(exposureLevels[12]);
            updateExposure(exposureLevels[12]);
            dialog1.dismiss();
            reloadDialog(); // Reload lại dialog
        });

        // Thêm nút "Thoát" với setNegativeButton

        dialog1.show();
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
            if(Long.parseLong(exp)>=100000000)
            {
                captureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, Long.parseLong("80000000"));
            }
            else {
                captureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, Long.parseLong(exp));
            }
            // Áp dụng request mới
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, null);
            Log.d("CameraEXP", "EXP đã cập nhật: " + exp);
        } catch (CameraAccessException e) {
            Log.d("CameraEXP", "Error " + e);
        }
    }
    private void imageProcessing (String path) {

        Bitmap origin = BitmapFactory.decodeFile(path);
        int dpi = origin.getDensity();
        if (dpi == 0) dpi = 203; // 기본 DPI 설정
        Bitmap resizedBitmap = imgSolve.processingImage(origin);
        AtomicReference<Bitmap> bmp = new AtomicReference<>(imgSolve.applySharpening(resizedBitmap, 1.5f));

        bmp.get().setDensity(dpi);
        btnPrint=findViewById(R.id.btnPrint);
        btnCancel=findViewById(R.id.btnCancel);
        imageViewPreview = findViewById(R.id.imageViewPreview);

        runOnUiThread(() -> {
            try {
                btnPrint.setEnabled(true);
                btnCancel.setEnabled(true);
                frameLayoutPopup.setVisibility(View.VISIBLE);
                textureView.setEnabled(false);

            } catch (Exception e) {
                Log.e("FrameLayoutError", "Error setting visibility for FrameLayout", e);
            }
        });

        float contrast = 1.4f;
        int light = 0;
        int[] lightValue1 = {light}; // Adjust brightness based on SeekBar progress
        float[] contrastValue = {contrast};
        Bitmap[] adjustedBitmap2 = {null};

        adjustedBitmap2[0] = imgSolve.adjustBrightness(bmp.get(), lightValue1[0]);
        adjustedBitmap2[0] = imgSolve.adjustContrast(adjustedBitmap2[0], contrastValue[0]);
        adjustedBitmap2[0].setDensity(origin.getDensity());

        //Xu ly khung anh
        @SuppressLint("UseCompatLoadingForDrawables")
        Drawable noel = getResources().getDrawable(R.drawable.nothing, null);
        Bitmap bitmapFrameNull = imgSolve.drawableToBitmap(noel);

        Bitmap bitmapFrame;
        // Kiểm tra xem bitmapList có phần tử không trước khi lấy currentIndex
        if (bitmapList != null && !bitmapList.isEmpty()) {
            String encodedBitmap = bitmapList.get(currentIndex);

            if (encodedBitmap == null) {
                bitmapFrame = bitmapFrameNull; // Set bitmapFrameNull nếu null
            } else {
                // Decode Base64 để lấy Bitmap
                byte[] decodedBytes = Base64.decode(encodedBitmap, Base64.DEFAULT);
                bitmapFrame = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
            }
        } else {
            bitmapFrame = bitmapFrameNull; // Set bitmapFrameNull nếu bitmapList trống
        }

        bitmapFrame = imgSolve.resizeBitmapMaintainAspect(bitmapFrame,800); // Nếu cần chuyển thành grayscale
        bitmapFrame=imgSolve.convertToGrayscale(bitmapFrame);
        //-----------------------------------------------------------------------------------------

        // Kiểm tra xem bitmapList có phần tử không trước khi lấy currentIndex
        if (bitmapList != null && !bitmapList.isEmpty()) {
            String encodedBitmap = bitmapList.get(currentIndex);

            if (encodedBitmap == null) {
                bitmapFrame = bitmapFrameNull; // Set bitmapFrameNull nếu null
            } else {
                // Decode Base64 để lấy Bitmap
                byte[] decodedBytes = Base64.decode(encodedBitmap, Base64.DEFAULT);
                bitmapFrame = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
            }
        } else {
            bitmapFrame = bitmapFrameNull; // Set bitmapFrameNull nếu bitmapList trống
        }

        bitmapFrame = imgSolve.resizeBitmapMaintainAspect(bitmapFrame,800); // Nếu cần chuyển thành grayscale
        bitmapFrame=imgSolve.convertToGrayscale(bitmapFrame);
        //-----------------------------------------------------------------------------------------



        //Xu ly anh phu
        @SuppressLint("UseCompatLoadingForDrawables")
        Drawable emptyImageview2 = getResources().getDrawable(R.drawable.bottom, null);
        Bitmap emptyImageview2Null = imgSolve.drawableToBitmap(emptyImageview2);

        // Kiểm tra xem bitmapList có phần tử không trước khi lấy currentIndex
        if (bitmapListImageView2 != null && !bitmapListImageView2.isEmpty()) {
            String encodedBitmap = bitmapListImageView2.get(currentIndexImageView2);

            if (encodedBitmap == null) {
                image = emptyImageview2Null; // Set bitmapFrameNull nếu null
            } else {
                // Decode Base64 để lấy Bitmap
                byte[] decodedBytes = Base64.decode(encodedBitmap, Base64.DEFAULT);
                image = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
            }
        } else {
            image = emptyImageview2Null; // Set bitmapFrameNull nếu bitmapList trống
        }





// Bitmap đã xử lý (adjustedBitmap2[0])
        Bitmap processedBitmap2 = adjustedBitmap2[0];
        //processedBitmap2=imgSolve.cropLeftRightToSquare(processedBitmap2);
        processedBitmap2 = imgSolve.resizeBitmapMaintainAspect(processedBitmap2,800); // Nếu cần chuyển thành grayscale
        int compensation=0;
// Phóng to processedBitmap2


// Tạo Bitmap mới để kết hợp
        // Tính toán kích thước mới cho processedBitmap2
        int newWidth = processedBitmap2.getWidth();
        int newHeight = processedBitmap2.getHeight();

        Bitmap enlargedBitmap = Bitmap.createScaledBitmap(processedBitmap2, newWidth+compensation, newHeight+compensation, true);
// Phóng to processedBitmap2
        // Tạo một đối tượng Matrix
        Matrix matrix = new Matrix();

// Lật ngang (hoặc thay đổi scale để lật theo hướng mong muốn)
        matrix.preScale(-1, 1); // Lật ngang
// Nếu muốn lật dọc: matrix.preScale(1, -1);

// Áp dụng Matrix để tạo Bitmap mới
        Bitmap flippedBitmap = Bitmap.createBitmap(enlargedBitmap, 0, 0,
                enlargedBitmap.getWidth(), enlargedBitmap.getHeight(), matrix, true);

        Bitmap newFrameBitmap = Bitmap.createScaledBitmap(bitmapFrame, newWidth, newHeight, true);


        // compensation=70 android 14, compensation=0 android 11
// Tạo Bitmap mới để kết hợp
        Bitmap combinedBitmap = Bitmap.createBitmap(
                processedBitmap2.getWidth()+compensation,
                processedBitmap2.getHeight()+compensation,
                Bitmap.Config.ARGB_8888
        );


// Vẽ bitmapFrame lên Canvas
        Canvas canvas = new Canvas(combinedBitmap);
        canvas.drawBitmap(flippedBitmap, 0, 0, null);

        canvas.drawBitmap(newFrameBitmap, 0, 0, null);

// Tính toán xOffset và yOffset để căn giữa enlargedBitmap bên trong bitmapFrame

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
                PrintNumber();
                printImage(
                        combinedBitmap,
                        0,
                        PRINT_THREE_INCH , false,
                        1
                );



                printImage2(image, 0, 576, false, 1);


                GoogleDriveService driveService = new GoogleDriveService(this);

                // 1️⃣ Tạo thư mục con
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    driveService.createSubFolder().thenCompose(subFolderId -> {
                        if (subFolderId != null) {
                            Log.d(TAG, "Thư mục con ID: " + subFolderId);

                            // 2️⃣ Tạo QR code từ thư mục con
                            Bitmap qrCodeFile = driveService.generateQRCode(subFolderId);
                            if (qrCodeFile != null) {
                                Log.d(TAG, "QR code đã tạo");

                                printQR(qrCodeFile, 0, 150, true, 1);
//                                Bitmap qrCodeFile2= imgSolve.generateQRCode("https://maps.app.goo.gl/BrvtyEMcy8gPFq939",500);
//                                printQR(qrCodeFile2, 0, 140, false, 1);
                                Bitmap bitmapPrint = BitmapFactory.decodeResource(Activity_Camera2_Manual.this.getResources(), R.drawable.end);

                                printEmptyAndCut(0, 140, false, 1,bitmapPrint);
                                // 3️⃣ Upload ảnh vào thư mục con
                                return driveService.uploadFileToDrive(path, subFolderId).thenApply(driveLink -> {
                                    if (driveLink != null) {
                                        Log.d(TAG, "Tải lên thành công: " + driveLink);
                                    } else {
                                        Log.e(TAG, "Upload thất bại.");
                                    }
                                    return driveLink;
                                });
                            } else {
                                Log.e(TAG, "Không thể tạo QR code.");
                            }
                        }
                        else {
                            Log.e(TAG, "Không thể tạo thư mục con.");
//                            Bitmap qrCodeFile= imgSolve.generateQRCode("https://www.tiktok.com/@mphotohcm",500);
//                           printQR(qrCodeFile, 0, 140, false, 1);
                            Bitmap bitmapPrint = BitmapFactory.decodeResource(Activity_Camera2_Manual.this.getResources(), R.drawable.end);
                            printEmptyAndCut(0, 150, false, 1,bitmapPrint);
                        }

                        return CompletableFuture.completedFuture(null);
                    }).thenRun(() -> {

                        // 💡 Chỉ gọi clearCache khi tất cả quá trình trước đó hoàn thành
                        Log.d(TAG, "Đã hoàn thành tất cả tác vụ, bắt đầu clear cache...");
                        imgSolve.clearCache();

                    });
                }
                //                imgSolve.clearCache();
                counterTime++;
                SharedPreferences preferences2 = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences2.edit();
                editor.putInt("counterTime", counterTime);
                editor.apply();
                runOnUiThread(() -> {
                    try {
                        numberCount.setText(String.valueOf(counterTime));
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
        });
    }
    public void printQR(final Bitmap bitmap, final int light, final int size,
                        final boolean haveWifi, final int sype) {
        executorService.execute(() -> {
            try {
                Bitmap bitmapPrint = bitmap;

                // Lấy ảnh từ drawable
                Bitmap imageBitmap ;
                // Xoay QR code nếu cần
                if (haveWifi) {
                    imageBitmap = BitmapFactory.decodeResource(Activity_Camera2_Manual.this.getResources(), R.drawable.getimage);
                }
                else {
                    imageBitmap = BitmapFactory.decodeResource(Activity_Camera2_Manual.this.getResources(), R.drawable.follow);
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

    public void PrintNumber() throws Exception {
        if (checkClick.isClickEvent()) return;

        String formatted="";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            current = LocalDateTime.now(); // Lấy ngày và giờ hiện tại
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd"); // Định dạng
            formatted = current.format(formatter); // Chuỗi ngày giờ định dạng
        }
        try {

            Print.PrintBitmap(imgSolve.createTextBitmap(String.valueOf(counterTime),formatted),1,0);
//            Print.CutPaper(0);
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
            if (textureView == null || imageDimension == null) {
                return;
            }

            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            Matrix matrix = new Matrix();

            RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);       // Kích thước TextureView
            RectF bufferRect = new RectF(0, 0, imageDimension.getHeight(), imageDimension.getWidth()); // Kích thước ảnh từ camera

            float centerX = viewRect.centerX();
            float centerY = viewRect.centerY();

            if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
                bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
                matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL); // Fill toàn bộ view
                float scale = Math.max(
                        (float) viewHeight / imageDimension.getHeight(),
                        (float) viewWidth / imageDimension.getWidth()
                );
                matrix.postScale(scale, scale, centerX, centerY);  // Scale hình ảnh để vừa vặn trong TextureView
                matrix.postRotate(90 * (rotation - 2), centerX, centerY); // Rotate hình ảnh nếu cần
            } else {
                matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL); // Fill toàn bộ view
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
        imageProcessing(file.getPath());
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




