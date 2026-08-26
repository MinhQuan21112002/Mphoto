package com.sdk.esc;

import com.mphoto.mono.R;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
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
import androidx.core.content.ContextCompat;
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
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy;
import com.bumptech.glide.request.RequestOptions;
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
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import print.Print;

public class Activity_Camera2_Manual extends AppCompatActivity implements ControlPageCommandHost {
    private ProgressDialog progressDialog;

    ImageSolve imgSolve; // Class for image processing
    private int counterTime=1; // Using for counting number that we have captured
    LocalDateTime current; // Using for calculating Date time now
    private CameraDevice cameraDevice;
    private CaptureRequest.Builder captureRequestBuilder;
    private static final String TAG = "AndroidCameraApi";
    final private ExecutorService executorService = Executors.newSingleThreadExecutor();
    /** Luồng riêng upload server — không chặn hàng đợi in máy in. */
    private final ExecutorService uploadExecutorService = Executors.newSingleThreadExecutor();
    TextView countdown; // Textview for counting down before capture image ( 3 2 1 )
    private TextureView textureView;
    private  int ISOvalue=400;
    private  long ExpoValue= 30000000;
    private static final String[] ISO_LEVELS = { "200", "300", "400", "500", "600", "700", "800", "100" };
    private static final String[] EXPOSURE_LEVELS_LABEL = { "0.2s", "0.3s", "0.4s", "0.5s", "0.6s", "0.7s", "0.8s", "0.9s", "1s", "1.2s", "1.4s", "2.0s", "0.1s" };
    private static final String[] EXPO_US = { "20000000", "30000000", "40000000", "50000000", "60000000", "70000000", "80000000", "90000000", "100000000", "120000000", "140000000", "250000000", "10000000" };
    private final int PRINT_FAILURE = 0;
    private final int PRINT_THREE_INCH = 576;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private static final int REQUEST_STORAGE_PERMISSION = 201;
    private UsbDevice device = null;
    private PendingIntent mPermissionIntent = null;
    private static final String ACTION_USB_PERMISSION = "com.PRINTSDKSample";
    @Nullable
    private AlertDialog activeRightPanelDialog;
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
    /** Dòng In / Hủy sát dưới vùng live + ảnh phụ; chỉ {@link View#VISIBLE} sau khi chụp. */
    View layoutPrintCancelRow;
    Button btnPrint;
    Button btnCancel;
    private volatile boolean monoPostCapturePending;
    ImageView imageViewPreview ;
    Bitmap image = null;
    ImageView imageViewSecond;
    /** Ảnh mới nhất trong thư mục M-Photo Mono (cạnh nút next khung). */
    ImageView imageMonoLatestThumb;
    private final Runnable monoFolderThumbRefreshRetry = () -> refreshMonoFolderThumbnail();
    private final Runnable deferredOpenCameraRunnable = this::openCameraIfReady;
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
    private View monoPreviewFlexHost;
    /** Cột giữa (trái/phải nút) — cao thật dùng tính w; host chỉ wrap nội dung. */
    private View monoPreviewCenterColumn;
    private ViewGroup monoPreviewFlexContent;
    private ViewGroup frameMonoSecond;
    private int lastMonoFlexSignature = Integer.MIN_VALUE;
    /** Dialog cài đặt đang mở — sync realtime từ Control Page. */
    @Nullable private AlertDialog openSettingsDialog;
    @Nullable private androidx.appcompat.widget.SwitchCompat openSettingsSwPrinterTest;
    @Nullable private androidx.appcompat.widget.SwitchCompat openSettingsSwDownload;
    @Nullable private Spinner openSettingsSpinnerPrintMode;
    private boolean settingsDialogUpdatingFromRemote;
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

        // Bắt buộc All files access — chưa bật thì chặn app
        MachineManager.getInstance(this).enforceDurableStorageAccessRequired(this);

        MonoGalleryCleanup.runInBackground(this);

        //initialize the interface

        setContentView(R.layout.activity_camera2_manual);
        GlassDialogHelper.applyPinkSystemBars(this);
        buttonUp=findViewById(R.id.button_up);
        buttonDown=findViewById(R.id.button_down);
        buttonList=findViewById(R.id.button_list);
        imageMonoLatestThumb = findViewById(R.id.imageMonoLatestThumb);
        if (imageMonoLatestThumb != null) {
            imageMonoLatestThumb.setOnClickListener(v -> showMonoGalleryPickerDialog());
        }
        increase=findViewById(R.id.btnIncrease);
        decrease=findViewById(R.id.btnDecrease);
        numberCount=findViewById(R.id.editTextNumber);
        btnPrint=findViewById(R.id.btnPrint);
        btnCancel=findViewById(R.id.btnCancel);
        textureView = findViewById(R.id.texture);
        imageViewSecond=findViewById(R.id.imageViewSecond);
        if (imageViewSecond != null) {
            imageViewSecond.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageViewSecond.setAdjustViewBounds(true);
        }
        imageViewPreview = findViewById(R.id.imageViewPreview);
        layoutPrintCancelRow = findViewById(R.id.layout_print_cancel_row);
        countdown= findViewById(R.id.countdownTextManual);
        frame=findViewById(R.id.imageView);
        monoPreviewFlexHost = findViewById(R.id.mono_preview_flex_host);
        monoPreviewCenterColumn = findViewById(R.id.mono_preview_center_column);
        monoPreviewFlexContent = findViewById(R.id.mono_preview_flex_content);
        frameMonoSecond = findViewById(R.id.frame_mono_second);
        ImageButton btnChangeLanguage = findViewById(R.id.btnChangeLanguage);
        ImageButton settingButton = findViewById(R.id.button_settings);
        ImageButton backButton = findViewById(R.id.button_back);
        //-----------------------------------------------------------------------------

        // Get value for iso and exposure time that saved in SharedPreferences
        SharedPreferences sharedPreferences = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        ISOvalue = Integer.parseInt(sharedPreferences.getString("isovalue", "400"));
        ExpoValue=Integer.parseInt(sharedPreferences.getString("epxvalue", "30000000"));

        counterTime=sharedPreferences.getInt("counterTime", 1);

        btnChangeLanguage.setOnClickListener(v -> showLanguagePickerDialog());

        //xu ly anh phu -------------------------------------------------------------
        SharedPreferences sharedPreferences2 = getSharedPreferences("MyAppPrefs2", MODE_PRIVATE);
        currentIndexImageView2=sharedPreferences2.getInt("indexImageView2", 0);
        String jsonStringImageview2 = sharedPreferences2.getString("ImageViewList", "[]");
        Gson gson1 = new Gson();
        bitmapListImageView2 = gson1.fromJson(jsonStringImageview2, new TypeToken<List<String>>() {}.getType());

        updateImageView2(currentIndexImageView2);
        //Load anh phu
        if (!bitmapListImageView2.isEmpty()) {
            image = UserAssetFileStore.decodeListEntryToBitmap(this,
                bitmapListImageView2.get(currentIndexImageView2));
        }
        if (image == null) {
            image = BitmapFactory.decodeResource(getResources(), R.drawable.bottom);
        }
        runOnUiThread(() -> {
            Glide.with(Activity_Camera2_Manual.this)
                    .load(image)
                    .fitCenter()
                    .into(imageViewSecond);
            View monoSizeRef = getMonoPreviewFlexSizeRef();
            if (monoSizeRef != null) {
                monoSizeRef.post(this::notifyMonoPreviewFlexRecalc);
            }
        });

        imageViewSecond.setOnClickListener(v -> showImagePickerDialog());

        //-----------------------------------------------------------

        if (tokenManager.canUseCloudFeatures()) {
            GoogleDriveService googleDriveService = new GoogleDriveService(this);
            googleDriveService.createOrGetSubFolder(this).thenAccept(folderId -> {
                if (folderId != null) {
                    Log.d("MainActivity", "Folder NameCard đã sẵn sàng với ID: " + folderId);
                } else {
                    Log.e("MainActivity", "Lỗi khi tạo hoặc lấy thư mục NameCard.");
                }
            });
            SoftwareUpdateHelper.checkAndDownloadInBackground(this);
            final String tokenSync = tokenManager.getToken();
            new Thread(() ->
                    GalleryUploadMethodService.getInstance(Activity_Camera2_Manual.this).syncFromServer(tokenSync)
            ).start();
        }

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
        View.OnClickListener livePreviewClickListener = v -> {
            if (!Print.IsOpened() && !PrinterTestMode.isEnabled(this)) {
                Toast.makeText(Activity_Camera2_Manual.this, getString(R.string.please_connect_printer), Toast.LENGTH_SHORT).show();
                try {
                    if (havingUsb) {
                        connectUSB();
                    }
                } catch (NumberFormatException e) {
                    Toast.makeText(getApplicationContext(), "Can't find Printer", Toast.LENGTH_SHORT).show();
                }
            } else {
                takePicture();
                setLiveViewCaptureInputEnabled(false);
            }
        };
        textureView.setOnClickListener(livePreviewClickListener);
        frame.setOnClickListener(livePreviewClickListener);
        imageViewPreview.setOnClickListener(livePreviewClickListener);
        countdown.setOnClickListener(livePreviewClickListener);
        //-----------------------------------------------------------------------------


        // function for behavior of clicking setting button
        settingButton.setOnClickListener(v -> showSettingDialog());
        bindIsoExposurePanel();
        // function for behavior of clicking back Button
        backButton.setOnClickListener(v -> returnToMainActivity());

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
        View monoSizeRef = getMonoPreviewFlexSizeRef();
        if (monoSizeRef != null) {
            monoSizeRef.getViewTreeObserver().addOnGlobalLayoutListener(this::onMonoPreviewFlexLayout);
            monoSizeRef.post(this::applyMonoPreviewFlexLayout);
        }
    }

    private void showLanguagePickerDialog() {
        View root = getLayoutInflater().inflate(R.layout.dialog_language, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(root)
                .create();
        View.OnClickListener pick = v -> {
            String code;
            int id = v.getId();
            if (id == R.id.btnLanguageEn) {
                code = "en";
            } else if (id == R.id.btnLanguageKo) {
                code = "ko";
            } else {
                code = "vi";
            }
            dialog.dismiss();
            changeLanguage(code);
        };
        root.findViewById(R.id.btnLanguageVi).setOnClickListener(pick);
        root.findViewById(R.id.btnLanguageEn).setOnClickListener(pick);
        root.findViewById(R.id.btnLanguageKo).setOnClickListener(pick);
        root.findViewById(R.id.btnCloseLanguage).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        GlassDialogHelper.applyGlassWindow(dialog);
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
            Bitmap bitmap = UserAssetFileStore.decodeListEntryToBitmap(this, bitmapList.get(index));
            if (bitmap == null) {
                return;
            }

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
            Bitmap bitmap = UserAssetFileStore.decodeListEntryToBitmap(this, bitmapListImageView2.get(index));
            if (bitmap != null) {
                imageViewSecond.setImageBitmap(bitmap);
                image = bitmap;
                notifyMonoPreviewFlexRecalc();
            }
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
                SocketService.getInstance().notifyControlPageSettingsChanged();
            } catch (Exception e) {
                Log.e("SDKSample", "Activity_Main --> mUsbReceiver: " + e.getMessage());
            }
        }
    };
    private void dismissActiveRightPanelDialog() {
        if (activeRightPanelDialog != null && activeRightPanelDialog.isShowing()) {
            activeRightPanelDialog.dismiss();
        }
        activeRightPanelDialog = null;
    }

    private void registerRightPanelDialog(@NonNull AlertDialog dialog) {
        dismissActiveRightPanelDialog();
        activeRightPanelDialog = dialog;
        dialog.setOnDismissListener(d -> {
            if (activeRightPanelDialog == d) {
                activeRightPanelDialog = null;
            }
        });
    }

    /** Dialog ảnh phụ / khung: nửa phải màn hình. */
    private void applyRightHalfDialogLayout(@NonNull AlertDialog dialog) {
        GlassDialogHelper.applyGlassWindow(dialog);
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        layoutParams.copyFrom(window.getAttributes());
        layoutParams.gravity = Gravity.TOP | Gravity.END;
        layoutParams.width = (int) (metrics.widthPixels * 0.5f);
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        layoutParams.x = 0;
        layoutParams.y = (int) (48f * metrics.density);
        window.setAttributes(layoutParams);
    }

    @SuppressLint("NotifyDataSetChanged")
    private void showImagePickerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_image_picker, null);
        Button btnPickImage = dialogView.findViewById(R.id.btnPickImage);
        Button btnResyncSub = dialogView.findViewById(R.id.btnResyncMonoSubPhotos);
        Button btnLoadMoreSub = dialogView.findViewById(R.id.btnLoadMoreSub);
//        Button btnPickImageDrive = dialogView.findViewById(R.id.btnPickImageDrive);
        RecyclerView recyclerView = dialogView.findViewById(R.id.recyclerViewImages);

        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));

        SharedPreferences preferences = getSharedPreferences("MyAppPrefs2", Context.MODE_PRIVATE);
        currentIndexImageView2 = preferences.getInt("indexImageView2", 0);
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
                        image = bitmapFrameNull;
                        notifyMonoPreviewFlexRecalc();
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
                        Bitmap bitmap = UserAssetFileStore.decodeListEntryToBitmap(Activity_Camera2_Manual.this, encodedBitmap);
                        if (bitmap != null) {
                            imageViewSecond.setImageBitmap(bitmap);
                            image = bitmap;
                            notifyMonoPreviewFlexRecalc();
                        }

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
                        Bitmap bitmap = UserAssetFileStore.decodeListEntryToBitmap(Activity_Camera2_Manual.this, encodedBitmap);
                        if (bitmap != null) {
                            imageViewSecond.setImageBitmap(bitmap);
                            image = bitmap;
                            notifyMonoPreviewFlexRecalc();
                        }
                    }
                },
                position2 -> {

                    String encodedBitmap =bitmapListImageView2.get(position2);
                    currentIndexImageView2=position2;
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putInt("indexImageView2", currentIndexImageView2);
                    editor.apply();
                    Bitmap resizedBitmap = UserAssetFileStore.decodeListEntryToBitmap(Activity_Camera2_Manual.this, encodedBitmap);
                    if (resizedBitmap != null) {
                        imageViewSecond.setImageBitmap(resizedBitmap);
                        image = resizedBitmap;
                        notifyMonoPreviewFlexRecalc();
                    }
                });

        recyclerView.setAdapter(adapter);
        final int pageSizeSub = 5;
        adapter.setVisibleCount(pageSizeSub);
        if (btnLoadMoreSub != null) {
            btnLoadMoreSub.setVisibility(adapter.canLoadMore() ? View.VISIBLE : View.GONE);
            btnLoadMoreSub.setOnClickListener(v -> {
                boolean more = adapter.increaseVisibleCount(pageSizeSub);
                if (!more || !adapter.canLoadMore()) {
                    btnLoadMoreSub.setVisibility(View.GONE);
                }
            });
        }

        builder.setView(dialogView);
        builder.setTitle(getString(R.string.sub_photo_list_title));
        builder.setPositiveButton(getString(R.string.close), (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = builder.create();
        registerRightPanelDialog(dialog);
        dialog.show();
        applyRightHalfDialogLayout(dialog);

        setCloudControlEnabled(btnResyncSub);
        if (btnResyncSub != null) {
            btnResyncSub.setOnClickListener(v -> runMonoSubPhotoSyncFromServer(dialog));
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


                                                String toStore = UserAssetFileStore.saveBitmapAsFileToken(
                                                    getApplicationContext(), image, false);
                                                if (toStore == null) {
                                                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                                    image.compress(Bitmap.CompressFormat.PNG, 100, baos);
                                                    toStore = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
                                                }

                                                bitmapListImageView2.add(toStore);

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
                                            MPhotoUserDataBackup.scheduleSave(getApplicationContext());

                                            // Hiển thị ảnh cuối cùng lên ImageView
                                            if (!bitmapListImageView2.isEmpty()) {
                                                Bitmap lastImage = UserAssetFileStore.decodeListEntryToBitmap(
                                                    Activity_Camera2_Manual.this,
                                                    bitmapListImageView2.get(currentIndexImageView2));
                                                if (lastImage != null) {
                                                    image = lastImage;
                                                    Glide.with(Activity_Camera2_Manual.this).load(lastImage).fitCenter().into(imageViewSecond);
                                                }
                                                View r = getMonoPreviewFlexSizeRef();
                                                if (r != null) {
                                                    r.post(Activity_Camera2_Manual.this::notifyMonoPreviewFlexRecalc);
                                                }
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
        Button btnResyncFrames = dialogView.findViewById(R.id.btnResyncMonoFrames);
        Button btnLoadMoreFrame = dialogView.findViewById(R.id.btnLoadMoreFrame);
        RecyclerView recyclerView = dialogView.findViewById(R.id.recyclerViewImages);

        // Setup RecyclerView with GridLayoutManager
        int numberOfColumns = 3; // Number of columns to display
        recyclerView.setLayoutManager(new GridLayoutManager(this, numberOfColumns));

        // Load image list from SharedPreferences
        SharedPreferences preferences = getSharedPreferences("FrameImage", Context.MODE_PRIVATE);
        currentIndex = preferences.getInt("current_index", 0);
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
                        Bitmap bitmap = UserAssetFileStore.decodeListEntryToBitmap(Activity_Camera2_Manual.this, encodedBitmap);
                        if (bitmap == null) {
                            return;
                        }

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
                        Bitmap bitmap = UserAssetFileStore.decodeListEntryToBitmap(Activity_Camera2_Manual.this, encodedBitmap);
                        if (bitmap == null) {
                            return;
                        }

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
                    Bitmap bitmap= UserAssetFileStore.decodeListEntryToBitmap(Activity_Camera2_Manual.this, encodedBitmap);
                    if (bitmap == null) {
                        return;
                    }
                    int originalWidth = bitmap.getWidth();
                    int newHeight = (int) (originalWidth * (3.0 / 4.0));
                    Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, originalWidth, newHeight, true);

                    frame.setImageBitmap(resizedBitmap);
                });
        recyclerView.setAdapter(adapter);
        final int pageSizeFrame = 5;
        adapter.setVisibleCount(pageSizeFrame);
        if (btnLoadMoreFrame != null) {
            btnLoadMoreFrame.setVisibility(adapter.canLoadMore() ? View.VISIBLE : View.GONE);
            btnLoadMoreFrame.setOnClickListener(v -> {
                boolean more = adapter.increaseVisibleCount(pageSizeFrame);
                if (!more || !adapter.canLoadMore()) {
                    btnLoadMoreFrame.setVisibility(View.GONE);
                }
            });
        }

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

                                                String toStore = UserAssetFileStore.saveBitmapAsFileToken(
                                                    getApplicationContext(), resizedBitmap, true);
                                                if (toStore == null) {
                                                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                                    resizedBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
                                                    toStore = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
                                                }

                                                bitmapList.add(toStore);

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
                                            MPhotoUserDataBackup.scheduleSave(getApplicationContext());

                                            // Hiển thị ảnh cuối cùng lên ImageView
                                            if (!bitmapList.isEmpty()) {
                                                Bitmap lastImage = UserAssetFileStore.decodeListEntryToBitmap(
                                                    Activity_Camera2_Manual.this, bitmapList.get(currentIndex));
                                                if (lastImage != null) {
                                                    Glide.with(Activity_Camera2_Manual.this).load(lastImage).into(frame);
                                                }
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

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setTitle(getString(R.string.frame_list_title));
        builder.setPositiveButton(getString(R.string.close), (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = builder.create();
        registerRightPanelDialog(dialog);
        dialog.show();
        applyRightHalfDialogLayout(dialog);

        buttonAdd.setTag(dialog);
        setCloudControlEnabled(btnResyncFrames);
        if (btnResyncFrames != null) {
            btnResyncFrames.setOnClickListener(v -> runMonoFrameSyncFromServer(dialog));
        }
    }

    private void runMonoFrameSyncFromServer(AlertDialog parentDialog) {
        if (!TokenManager.getInstance(this).canUseCloudFeatures()) {
            Toast.makeText(this, R.string.guest_feature_need_login, Toast.LENGTH_SHORT).show();
            return;
        }
        String token = TokenManager.getInstance(this).getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, R.string.mono_sync_need_login, Toast.LENGTH_SHORT).show();
            return;
        }
        if (parentDialog != null && parentDialog.isShowing()) {
            parentDialog.dismiss();
        }
        ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage(getString(R.string.mono_syncing));
        pd.setCancelable(false);
        pd.show();
        MonoCacheSync.syncFramesInBackground(this, token, new MonoCacheSync.Listener() {
            @Override
            public void onSuccess() {
                pd.dismiss();
                SharedPreferences p = getSharedPreferences("FrameImage", Context.MODE_PRIVATE);
                String json = p.getString("bitmap_list", "[]");
                bitmapList = new Gson().fromJson(json, new TypeToken<List<String>>() {}.getType());
                currentIndex = p.getInt("current_index", 0);
                if (bitmapList != null && !bitmapList.isEmpty()) {
                    updateImageView(currentIndex);
                } else {
                    @SuppressLint("UseCompatLoadingForDrawables")
                    Drawable noel = getResources().getDrawable(R.drawable.nothing, null);
                    Bitmap b = imgSolve.drawableToBitmap(noel);
                    frame.setImageBitmap(b);
                }
                Toast.makeText(Activity_Camera2_Manual.this, R.string.mono_sync_ok, Toast.LENGTH_SHORT).show();
                showImageFrameDialog();
            }

            @Override
            public void onError(String message) {
                pd.dismiss();
                Toast.makeText(Activity_Camera2_Manual.this, message, Toast.LENGTH_LONG).show();
                showImageFrameDialog();
            }
        });
    }

    private void runMonoSubPhotoSyncFromServer(AlertDialog parentDialog) {
        if (!TokenManager.getInstance(this).canUseCloudFeatures()) {
            Toast.makeText(this, R.string.guest_feature_need_login, Toast.LENGTH_SHORT).show();
            return;
        }
        String token = TokenManager.getInstance(this).getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, R.string.mono_sync_need_login, Toast.LENGTH_SHORT).show();
            return;
        }
        if (parentDialog != null && parentDialog.isShowing()) {
            parentDialog.dismiss();
        }
        ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage(getString(R.string.mono_syncing));
        pd.setCancelable(false);
        pd.show();
        MonoCacheSync.syncSubPhotosInBackground(this, token, new MonoCacheSync.Listener() {
            @Override
            public void onSuccess() {
                pd.dismiss();
                SharedPreferences p2 = getSharedPreferences("MyAppPrefs2", Context.MODE_PRIVATE);
                String json = p2.getString("ImageViewList", "[]");
                bitmapListImageView2 = new Gson().fromJson(json, new TypeToken<List<String>>() {}.getType());
                currentIndexImageView2 = p2.getInt("indexImageView2", 0);
                if (bitmapListImageView2 != null && !bitmapListImageView2.isEmpty()) {
                    updateImageView2(currentIndexImageView2);
                } else {
                    @SuppressLint("UseCompatLoadingForDrawables")
                    Drawable d = getResources().getDrawable(R.drawable.bottom, null);
                    Bitmap b = imgSolve.drawableToBitmap(d);
                    imageViewSecond.setImageBitmap(b);
                    image = b;
                    notifyMonoPreviewFlexRecalc();
                }
                Toast.makeText(Activity_Camera2_Manual.this, R.string.mono_sync_ok, Toast.LENGTH_SHORT).show();
                showImagePickerDialog();
            }

            @Override
            public void onError(String message) {
                pd.dismiss();
                Toast.makeText(Activity_Camera2_Manual.this, message, Toast.LENGTH_LONG).show();
                showImagePickerDialog();
            }
        });
    }

    private void clearIsoExposureRowBackgrounds() {
        int[] ids = { R.id.isoText1, R.id.isoText2, R.id.isoText3, R.id.isoText4, R.id.isoText5, R.id.isoText6, R.id.isoText7, R.id.isoText8, R.id.exposureText1, R.id.exposureText2, R.id.exposureText3, R.id.exposureText4, R.id.exposureText5, R.id.exposureText6, R.id.exposureText7, R.id.exposureText8, R.id.exposureText9, R.id.exposureText10, R.id.exposureText11, R.id.exposureText12, R.id.exposureText14 };
        for (int id : ids) {
            TextView t = findViewById(id);
            if (t != null) {
                t.setBackgroundColor(Color.TRANSPARENT);
            }
        }
    }

    /** Cập nhật nhãn + nền mức đang chọn (ISO / phơi sáng) trên cột bên phải. */
    private void refreshIsoExposurePanelDisplay() {
        if (findViewById(R.id.isoText1) == null) {
            return;
        }
        clearIsoExposureRowBackgrounds();
        TextView isoText8 = findViewById(R.id.isoText8);
        TextView isoText1 = findViewById(R.id.isoText1);
        TextView isoText2 = findViewById(R.id.isoText2);
        TextView isoText3 = findViewById(R.id.isoText3);
        TextView isoText4 = findViewById(R.id.isoText4);
        TextView isoText5 = findViewById(R.id.isoText5);
        TextView isoText6 = findViewById(R.id.isoText6);
        TextView isoText7 = findViewById(R.id.isoText7);
        TextView exposureText11 = findViewById(R.id.exposureText11);
        TextView exposureText1 = findViewById(R.id.exposureText1);
        TextView exposureText2 = findViewById(R.id.exposureText2);
        TextView exposureText3 = findViewById(R.id.exposureText3);
        TextView exposureText4 = findViewById(R.id.exposureText4);
        TextView exposureText5 = findViewById(R.id.exposureText5);
        TextView exposureText6 = findViewById(R.id.exposureText6);
        TextView exposureText7 = findViewById(R.id.exposureText7);
        TextView exposureText8 = findViewById(R.id.exposureText8);
        TextView exposureText9 = findViewById(R.id.exposureText9);
        TextView exposureText12 = findViewById(R.id.exposureText12);
        TextView exposureText14 = findViewById(R.id.exposureText14);
        TextView exposureText10 = findViewById(R.id.exposureText10);

        String isovalue = String.valueOf(ISOvalue);
        String exposurevalue = String.valueOf(ExpoValue);

        if (isoText8 != null) {
            isoText8.setText(ISO_LEVELS[7]);
            if (ISO_LEVELS[7].equals(isovalue)) {
                isoText8.setBackgroundColor(ContextCompat.getColor(this, R.color.mp_brand_light));
            }
        }
        if (exposureText11 != null) {
            exposureText11.setText(EXPOSURE_LEVELS_LABEL[12]);
            if (EXPO_US[12].equals(exposurevalue)) {
                exposureText11.setBackgroundColor(ContextCompat.getColor(this, R.color.mp_brand_light));
            }
        }
        if (isoText1 != null) {
            isoText1.setText(ISO_LEVELS[0]);
            if (ISO_LEVELS[0].equals(isovalue)) {
                isoText1.setBackgroundColor(ContextCompat.getColor(this, R.color.mp_brand_light));
            }
        }
        if (exposureText1 != null) {
            exposureText1.setText(EXPOSURE_LEVELS_LABEL[0]);
            if (EXPO_US[0].equals(exposurevalue)) {
                exposureText1.setBackgroundColor(ContextCompat.getColor(this, R.color.mp_brand_light));
            }
        }
        if (isoText2 != null) {
            isoText2.setText(ISO_LEVELS[1]);
            if (ISO_LEVELS[1].equals(isovalue)) {
                isoText2.setBackgroundColor(ContextCompat.getColor(this, R.color.mp_brand_light));
            }
        }
        if (exposureText2 != null) {
            exposureText2.setText(EXPOSURE_LEVELS_LABEL[1]);
            if (EXPO_US[1].equals(exposurevalue)) {
                exposureText2.setBackgroundColor(ContextCompat.getColor(this, R.color.mp_brand_light));
            }
        }
        if (isoText3 != null) {
            isoText3.setText(ISO_LEVELS[2]);
            if (ISO_LEVELS[2].equals(isovalue)) {
                isoText3.setBackgroundColor(ContextCompat.getColor(this, R.color.mp_brand_light));
            }
        }
        if (exposureText3 != null) {
            exposureText3.setText(EXPOSURE_LEVELS_LABEL[2]);
            if (EXPO_US[2].equals(exposurevalue)) {
                exposureText3.setBackgroundColor(ContextCompat.getColor(this, R.color.mp_brand_light));
            }
        }
        if (isoText4 != null) {
            isoText4.setText(ISO_LEVELS[3]);
            if (ISO_LEVELS[3].equals(isovalue)) {
                isoText4.setBackgroundColor(ContextCompat.getColor(this, R.color.mp_brand_light));
            }
        }
        if (exposureText4 != null) {
            exposureText4.setText(EXPOSURE_LEVELS_LABEL[3]);
            if (EXPO_US[3].equals(exposurevalue)) {
                exposureText4.setBackgroundColor(ContextCompat.getColor(this, R.color.mp_brand_light));
            }
        }
        if (isoText5 != null) {
            isoText5.setText(ISO_LEVELS[4]);
            if (ISO_LEVELS[4].equals(isovalue)) {
                isoText5.setBackgroundColor(ContextCompat.getColor(this, R.color.mp_brand_light));
            }
        }
        if (exposureText5 != null) {
            exposureText5.setText(EXPOSURE_LEVELS_LABEL[4]);
            if (EXPO_US[4].equals(exposurevalue)) {
                exposureText5.setBackgroundColor(ContextCompat.getColor(this, R.color.mp_brand_light));
            }
        }
        if (isoText6 != null) {
            isoText6.setText(ISO_LEVELS[5]);
            if (ISO_LEVELS[5].equals(isovalue)) {
                isoText6.setBackgroundColor(ContextCompat.getColor(this, R.color.mp_brand_light));
            }
        }
        if (exposureText6 != null) {
            exposureText6.setText(EXPOSURE_LEVELS_LABEL[5]);
            if (EXPO_US[5].equals(exposurevalue)) {
                exposureText6.setBackgroundColor(ContextCompat.getColor(this, R.color.mp_brand_light));
            }
        }
        if (isoText7 != null) {
            isoText7.setText(ISO_LEVELS[6]);
            if (ISO_LEVELS[6].equals(isovalue)) {
                isoText7.setBackgroundColor(ContextCompat.getColor(this, R.color.mp_brand_light));
            }
        }
        if (exposureText7 != null) {
            exposureText7.setText(EXPOSURE_LEVELS_LABEL[6]);
            if (EXPO_US[6].equals(exposurevalue)) {
                exposureText7.setBackgroundColor(ContextCompat.getColor(this, R.color.mp_brand_light));
            }
        }
        if (exposureText8 != null) {
            exposureText8.setText(EXPOSURE_LEVELS_LABEL[7]);
            if (EXPO_US[7].equals(exposurevalue)) {
                exposureText8.setBackgroundColor(ContextCompat.getColor(this, R.color.mp_brand_light));
            }
        }
        if (exposureText9 != null) {
            exposureText9.setText(EXPOSURE_LEVELS_LABEL[8]);
            if (EXPO_US[8].equals(exposurevalue)) {
                exposureText9.setBackgroundColor(ContextCompat.getColor(this, R.color.mp_brand_light));
            }
        }
        if (exposureText10 != null) {
            exposureText10.setText(EXPOSURE_LEVELS_LABEL[11]);
            if (EXPO_US[11].equals(exposurevalue)) {
                exposureText10.setBackgroundColor(ContextCompat.getColor(this, R.color.mp_brand_light));
            }
        }
        if (exposureText12 != null) {
            exposureText12.setText(EXPOSURE_LEVELS_LABEL[9]);
            if (EXPO_US[9].equals(exposurevalue)) {
                exposureText12.setBackgroundColor(ContextCompat.getColor(this, R.color.mp_brand_light));
            }
        }
        if (exposureText14 != null) {
            exposureText14.setText(EXPOSURE_LEVELS_LABEL[10]);
            if (EXPO_US[10].equals(exposurevalue)) {
                exposureText14.setBackgroundColor(ContextCompat.getColor(this, R.color.mp_brand_light));
            }
        }
    }

    private void bindIsoExposurePanel() {
        if (findViewById(R.id.isoText1) == null) {
            return;
        }
        refreshIsoExposurePanelDisplay();
        findViewById(R.id.isoText8).setOnClickListener(v -> { applyISO(ISO_LEVELS[7]); updateISO(ISO_LEVELS[7]); refreshIsoExposurePanelDisplay(); });
        findViewById(R.id.isoText1).setOnClickListener(v -> { applyISO(ISO_LEVELS[0]); updateISO(ISO_LEVELS[0]); refreshIsoExposurePanelDisplay(); });
        findViewById(R.id.isoText2).setOnClickListener(v -> { applyISO(ISO_LEVELS[1]); updateISO(ISO_LEVELS[1]); refreshIsoExposurePanelDisplay(); });
        findViewById(R.id.isoText3).setOnClickListener(v -> { applyISO(ISO_LEVELS[2]); updateISO(ISO_LEVELS[2]); refreshIsoExposurePanelDisplay(); });
        findViewById(R.id.isoText4).setOnClickListener(v -> { applyISO(ISO_LEVELS[3]); updateISO(ISO_LEVELS[3]); refreshIsoExposurePanelDisplay(); });
        findViewById(R.id.isoText5).setOnClickListener(v -> { applyISO(ISO_LEVELS[4]); updateISO(ISO_LEVELS[4]); refreshIsoExposurePanelDisplay(); });
        findViewById(R.id.isoText6).setOnClickListener(v -> { applyISO(ISO_LEVELS[5]); updateISO(ISO_LEVELS[5]); refreshIsoExposurePanelDisplay(); });
        findViewById(R.id.isoText7).setOnClickListener(v -> { applyISO(ISO_LEVELS[6]); updateISO(ISO_LEVELS[6]); refreshIsoExposurePanelDisplay(); });
        findViewById(R.id.exposureText1).setOnClickListener(v -> { applyExpose(EXPO_US[0]); updateExposure(EXPO_US[0]); refreshIsoExposurePanelDisplay(); });
        findViewById(R.id.exposureText2).setOnClickListener(v -> { applyExpose(EXPO_US[1]); updateExposure(EXPO_US[1]); refreshIsoExposurePanelDisplay(); });
        findViewById(R.id.exposureText3).setOnClickListener(v -> { applyExpose(EXPO_US[2]); updateExposure(EXPO_US[2]); refreshIsoExposurePanelDisplay(); });
        findViewById(R.id.exposureText4).setOnClickListener(v -> { applyExpose(EXPO_US[3]); updateExposure(EXPO_US[3]); refreshIsoExposurePanelDisplay(); });
        findViewById(R.id.exposureText5).setOnClickListener(v -> { applyExpose(EXPO_US[4]); updateExposure(EXPO_US[4]); refreshIsoExposurePanelDisplay(); });
        findViewById(R.id.exposureText6).setOnClickListener(v -> { applyExpose(EXPO_US[5]); updateExposure(EXPO_US[5]); refreshIsoExposurePanelDisplay(); });
        findViewById(R.id.exposureText7).setOnClickListener(v -> { applyExpose(EXPO_US[6]); updateExposure(EXPO_US[6]); refreshIsoExposurePanelDisplay(); });
        findViewById(R.id.exposureText8).setOnClickListener(v -> { applyExpose(EXPO_US[7]); updateExposure(EXPO_US[7]); refreshIsoExposurePanelDisplay(); });
        findViewById(R.id.exposureText9).setOnClickListener(v -> { applyExpose(EXPO_US[8]); updateExposure(EXPO_US[8]); refreshIsoExposurePanelDisplay(); });
        findViewById(R.id.exposureText10).setOnClickListener(v -> { applyExpose(EXPO_US[11]); updateExposure(EXPO_US[11]); refreshIsoExposurePanelDisplay(); });
        findViewById(R.id.exposureText12).setOnClickListener(v -> { applyExpose(EXPO_US[9]); updateExposure(EXPO_US[9]); refreshIsoExposurePanelDisplay(); });
        findViewById(R.id.exposureText14).setOnClickListener(v -> { applyExpose(EXPO_US[10]); updateExposure(EXPO_US[10]); refreshIsoExposurePanelDisplay(); });
        findViewById(R.id.exposureText11).setOnClickListener(v -> { applyExpose(EXPO_US[12]); updateExposure(EXPO_US[12]); refreshIsoExposurePanelDisplay(); });
    }

    private void showSettingDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_manual_settings, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setNegativeButton(getString(R.string.close), (dialog, which) -> dialog.dismiss());
        final AlertDialog dialog1 = builder.create();
        openSettingsDialog = dialog1;
        dialog1.setOnDismissListener(d -> {
            if (openSettingsDialog == dialog1) {
                openSettingsDialog = null;
                openSettingsSwPrinterTest = null;
                openSettingsSwDownload = null;
                openSettingsSpinnerPrintMode = null;
            }
        });
        androidx.appcompat.widget.SwitchCompat swPrinterTest = dialogView.findViewById(R.id.switchPrinterTestMode);
        openSettingsSwPrinterTest = swPrinterTest;
        if (swPrinterTest != null) {
            swPrinterTest.setChecked(PrinterTestMode.isEnabled(this));
            swPrinterTest.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (settingsDialogUpdatingFromRemote) return;
                PrinterTestMode.setEnabled(Activity_Camera2_Manual.this, isChecked);
                SocketService.getInstance().notifyControlPageSettingsChanged();
            });
        }
        SharedPreferences prefSettings = getSharedPreferences("settings", MODE_PRIVATE);
        androidx.appcompat.widget.SwitchCompat swDownload = dialogView.findViewById(R.id.switchDownload);
        openSettingsSwDownload = swDownload;
        if (swDownload != null) {
            swDownload.setChecked(prefSettings.getBoolean("Download", false));
            swDownload.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (settingsDialogUpdatingFromRemote) return;
                prefSettings.edit().putBoolean("Download", isChecked).apply();
                SocketService.getInstance().notifyControlPageSettingsChanged();
            });
            setCloudControlEnabled(swDownload);
        }
        Spinner spinnerPrintMode = dialogView.findViewById(R.id.spinnerPrintBitmapMode);
        openSettingsSpinnerPrintMode = spinnerPrintMode;
        if (spinnerPrintMode != null) {
            ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(
                    this,
                    android.R.layout.simple_spinner_dropdown_item,
                    PrintBitmapMode.labels(this));
            spinnerPrintMode.setAdapter(modeAdapter);
            spinnerPrintMode.setSelection(PrintBitmapMode.indexOf(PrintBitmapMode.get(this)));
            spinnerPrintMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (settingsDialogUpdatingFromRemote) return;
                    PrintBitmapMode.set(Activity_Camera2_Manual.this, PrintBitmapMode.modeAt(position));
                    SocketService.getInstance().notifyControlPageSettingsChanged();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
        }
        Button btnLogout = dialogView.findViewById(R.id.button_logout);
        if (btnLogout != null) {
            boolean guestMode = TokenManager.getInstance(this).isGuestMode();
            btnLogout.setText(guestMode ? R.string.login_button : R.string.logout);
            btnLogout.setOnClickListener(v -> showLogoutConfirmDialog(dialog1, guestMode));
        }
        TextView textAppUpdateFile = dialogView.findViewById(R.id.text_app_update_file);
        bindAppUpdateFileLabel(textAppUpdateFile);
        Button btnCheckUpdate = dialogView.findViewById(R.id.button_check_app_update);
        Button btnInstallUpdate = dialogView.findViewById(R.id.button_install_app_update);
        setCloudControlEnabled(btnCheckUpdate);
        setCloudControlEnabled(btnInstallUpdate);
        ProgressBar progressCheckUpdate = dialogView.findViewById(R.id.progress_check_app_update);
        if (btnCheckUpdate != null) {
            final Button btnUpd = btnCheckUpdate;
            final ProgressBar pbUpd = progressCheckUpdate;
            final TextView tvFileLabel = textAppUpdateFile;
            btnCheckUpdate.setOnClickListener(v -> SoftwareUpdateHelper.checkAndDownloadWithFeedback(
                    Activity_Camera2_Manual.this, false,
                    () -> {
                        if (pbUpd != null) {
                            pbUpd.setVisibility(View.VISIBLE);
                        }
                        btnUpd.setEnabled(false);
                        btnUpd.setText(R.string.app_update_downloading);
                    },
                    () -> {
                        if (pbUpd != null) {
                            pbUpd.setVisibility(View.GONE);
                        }
                        btnUpd.setEnabled(true);
                        btnUpd.setText(R.string.app_update_check_download);
                        bindAppUpdateFileLabel(tvFileLabel);
                    }
            ));
        }
        if (btnInstallUpdate != null) {
            btnInstallUpdate.setOnClickListener(v -> SoftwareUpdateHelper.tryInstallPending(Activity_Camera2_Manual.this));
        }
        dialog1.show();
        GlassDialogHelper.applyGlassWindow(dialog1);
    }

    /** Cập nhật UI dialog cài đặt khi Control Page đổi print mode / test / QR. */
    private void refreshOpenSettingsDialogFromPrefs() {
        if (openSettingsDialog == null || !openSettingsDialog.isShowing()) return;
        runOnUiThread(() -> {
            settingsDialogUpdatingFromRemote = true;
            try {
                if (openSettingsSwPrinterTest != null) {
                    boolean v = PrinterTestMode.isEnabled(this);
                    if (openSettingsSwPrinterTest.isChecked() != v) {
                        openSettingsSwPrinterTest.setChecked(v);
                    }
                }
                if (openSettingsSwDownload != null) {
                    boolean v = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("Download", false);
                    if (openSettingsSwDownload.isChecked() != v) {
                        openSettingsSwDownload.setChecked(v);
                    }
                }
                if (openSettingsSpinnerPrintMode != null) {
                    int idx = PrintBitmapMode.indexOf(PrintBitmapMode.get(this));
                    if (openSettingsSpinnerPrintMode.getSelectedItemPosition() != idx) {
                        openSettingsSpinnerPrintMode.setSelection(idx);
                    }
                }
            } finally {
                settingsDialogUpdatingFromRemote = false;
            }
        });
    }

    /** Dialog xác nhận đăng xuất / chuyển login — cùng tone glass với settings. */
    private void showLogoutConfirmDialog(@Nullable AlertDialog settingsDialog, boolean guestMode) {
        View root = getLayoutInflater().inflate(R.layout.dialog_logout_confirm, null);
        TextView title = root.findViewById(R.id.text_logout_confirm_title);
        TextView message = root.findViewById(R.id.text_logout_confirm_message);
        Button btnCancel = root.findViewById(R.id.button_logout_cancel);
        Button btnConfirm = root.findViewById(R.id.button_logout_confirm);
        if (title != null) {
            title.setText(guestMode ? R.string.login_confirm_title : R.string.logout_confirm_title);
        }
        if (message != null) {
            message.setText(guestMode ? R.string.login_confirm_message : R.string.logout_confirm_message);
        }
        if (btnConfirm != null) {
            btnConfirm.setText(guestMode ? R.string.login_confirm_yes : R.string.logout_confirm_yes);
        }
        AlertDialog confirm = new AlertDialog.Builder(this)
                .setView(root)
                .create();
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> confirm.dismiss());
        }
        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> {
                confirm.dismiss();
                if (settingsDialog != null && settingsDialog.isShowing()) {
                    settingsDialog.dismiss();
                }
                TokenManager.getInstance(Activity_Camera2_Manual.this).clearToken();
                Intent i = new Intent(Activity_Camera2_Manual.this, LoginActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
                finish();
            });
        }
        confirm.show();
        GlassDialogHelper.applyGlassWindow(confirm);
    }

    private void bindAppUpdateFileLabel(@Nullable TextView tv) {
        if (tv == null) {
            return;
        }
        String name = SoftwareUpdateHelper.getPendingApkFileNameForDisplay(this);
        if (name != null) {
            tv.setText(getString(R.string.app_update_file_in_settings, name));
        } else {
            tv.setText(R.string.app_update_no_saved_file);
        }
    }

    private void refreshMonoFolderThumbnail() {
        if (imageMonoLatestThumb == null) {
            return;
        }
        executorService.execute(() -> {
            android.net.Uri u = MonoFolderImages.getLatestImageUri(Activity_Camera2_Manual.this);
            runOnUiThread(() -> {
                if (u == null) {
                    Glide.with(Activity_Camera2_Manual.this).clear(imageMonoLatestThumb);
                    imageMonoLatestThumb.setImageDrawable(null);
                    imageMonoLatestThumb.setBackgroundResource(R.drawable.mono_folder_thumb_bg);
                } else {
                    imageMonoLatestThumb.setBackgroundResource(R.drawable.mono_folder_thumb_bg);
                    int sidePx = getResources().getDimensionPixelSize(R.dimen.mono_thumb);
                    int decode = Math.max(400, (int) (sidePx * 2.5f));
                    RequestOptions ro = new RequestOptions()
                            .fitCenter()
                            .format(DecodeFormat.PREFER_ARGB_8888)
                            .downsample(DownsampleStrategy.CENTER_INSIDE)
                            .override(decode, decode);
                    Glide.with(Activity_Camera2_Manual.this)
                            .load(u)
                            .apply(ro)
                            .into(imageMonoLatestThumb);
                }
            });
        });
    }

    /**
     * Sau khi lưu ảnh vào thư mục Mono — MediaStore đôi khi cập nhật chậm nên gọi lại một lần.
     */
    private void scheduleMonoFolderThumbnailUpdate() {
        refreshMonoFolderThumbnail();
        if (imageMonoLatestThumb != null) {
            imageMonoLatestThumb.removeCallbacks(monoFolderThumbRefreshRetry);
            imageMonoLatestThumb.postDelayed(monoFolderThumbRefreshRetry, 500);
        }
    }

    /**
     * Mở thư mục Google Drive {@code M-Photo Mono_userId} trên trình duyệt.
     */
    /**
     * Mở bằng trình chọn ứng dụng: tránh mặc định mở app Drive (báo lỗi khi chưa có tài khoản Google trên máy). Nên chọn Chrome / trình duyệt.
     */
    private void openDriveFolderUrl(String link) {
        if (link == null || link.isEmpty()) {
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(Intent.createChooser(intent, getString(R.string.drive_choose_app_open) + "\n" + getString(R.string.drive_chooser_subtitle)));
        } catch (Exception e) {
            Log.e(TAG, "open drive", e);
            Toast.makeText(this, R.string.drive_link_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    private void openLocalImageInGallery(@NonNull MonoGalleryGroupAdapter.Item item) {
        if (item.previewUri == null) {
            Toast.makeText(this, "Không có ảnh local để mở", Toast.LENGTH_SHORT).show();
            return;
        }
        executorService.execute(() -> {
            Bitmap bm = decodeBitmapFromUriSafe(item.previewUri);
            if (bm == null) {
                try {
                    File f = MonoFolderImages.resolveFileFromUri(
                            Activity_Camera2_Manual.this,
                            item.previewUri,
                            MonoGalleryFolderIds.localFileNameForItem(item.localFolderId, item.folderId)
                    );
                    if (f != null && f.exists()) {
                        bm = BitmapFactory.decodeFile(f.getAbsolutePath());
                    }
                } catch (Exception ignored) {
                }
            }
            final Bitmap previewBitmap = bm;
            runOnUiThread(() -> {
                if (previewBitmap == null) {
                    Toast.makeText(Activity_Camera2_Manual.this, "Không mở được ảnh local", Toast.LENGTH_SHORT).show();
                    return;
                }
                ImageView iv = new ImageView(Activity_Camera2_Manual.this);
                iv.setImageBitmap(previewBitmap);
                iv.setAdjustViewBounds(true);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                new AlertDialog.Builder(Activity_Camera2_Manual.this)
                        .setTitle(item.folderId)
                        .setView(iv)
                        .setPositiveButton(R.string.close, (d, w) -> d.dismiss())
                        .show();
            });
        });
    }

    /**
     * Hiển thị QR; chỉ khi chạm vào mã QR mới mở link (không mở trình duyệt ngay khi bấm nút).
     */
    private void openMonoUserDriveFolderQrDialog() {
        GoogleDriveService g = new GoogleDriveService(this);
        String link = g.getMonoUserFolderLink(this);
        if (link != null) {
            showDriveFolderQrDialogInternal(link, g);
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Toast.makeText(this, R.string.drive_link_unavailable, Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, R.string.mono_syncing, Toast.LENGTH_SHORT).show();
        executorService.execute(() -> {
            String resolvedLink = MonoDriveServerSync.resolveFolderLinkForUi(Activity_Camera2_Manual.this);
            runOnUiThread(() -> {
                if (resolvedLink == null || resolvedLink.isEmpty()) {
                    Toast.makeText(Activity_Camera2_Manual.this, R.string.drive_link_unavailable, Toast.LENGTH_LONG).show();
                    return;
                }
                showDriveFolderQrDialogInternal(resolvedLink, new GoogleDriveService(Activity_Camera2_Manual.this));
            });
        });
    }

    private void showDriveFolderQrDialogInternal(String link, GoogleDriveService g) {
        View root = getLayoutInflater().inflate(R.layout.dialog_mono_drive_qr, null);
        ImageView iv = root.findViewById(R.id.imageMonoDriveQr);
        ProgressBar pb = root.findViewById(R.id.progressMonoDriveQr);
        if (iv == null) {
            return;
        }
        iv.setVisibility(View.GONE);
        if (pb != null) {
            pb.setVisibility(View.VISIBLE);
        }
        iv.setOnClickListener(v -> openDriveFolderUrl(link));

        AlertDialog dlg = new AlertDialog.Builder(this)
            .setTitle(R.string.mono_gallery_qr_dialog_title)
            .setView(root)
            .setPositiveButton(R.string.close, (d, w) -> d.dismiss())
            .create();
        dlg.show();
        GlassDialogHelper.applyGlassWindow(dlg);

        final Activity_Camera2_Manual activity = this;
        executorService.execute(() -> {
            Bitmap bm = g.generateQRCodeForUrl(link);
            runOnUiThread(() -> {
                if (activity.isFinishing()) {
                    return;
                }
                if (pb != null) {
                    pb.setVisibility(View.GONE);
                }
                if (bm != null) {
                    iv.setImageBitmap(bm);
                    iv.setVisibility(View.VISIBLE);
                } else {
                    Toast.makeText(activity, R.string.drive_link_unavailable, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void showMonoGalleryPickerDialog() {
        View root = getLayoutInflater().inflate(R.layout.dialog_mono_gallery, null);
        Button btnTabLocal = root.findViewById(R.id.btnMonoTabLocal);
        Button btnTabServer = root.findViewById(R.id.btnMonoTabServer);
        RecyclerView rv = root.findViewById(R.id.recyclerMonoGallery);
        TextView empty = root.findViewById(R.id.textMonoGalleryEmpty);
        ProgressBar progress = root.findViewById(R.id.progressMonoGallery);
        Button btnLoadMore = root.findViewById(R.id.btnLoadMoreMonoGallery);
        if (rv == null || progress == null) {
            return;
        }

        GridLayoutManager glm = new GridLayoutManager(this, 3);
        rv.setLayoutManager(glm);
        MonoGalleryGroupAdapter adapter = new MonoGalleryGroupAdapter();
        rv.setAdapter(adapter);
        int gapPx = (int) (10 * getResources().getDisplayMetrics().density);
        rv.addItemDecoration(new MonoGalleryGridGapDecoration(3, gapPx));
        final int pageSize = 5;
        final int[] currentTab = {0}; // 0 local, 1 server
        final List<MonoGalleryGroupAdapter.Item> localItems = new ArrayList<>();
        final List<MonoGalleryGroupAdapter.Item> serverItems = new ArrayList<>();
        final int[] visibleCount = {pageSize, pageSize}; // local, server

        Runnable renderCurrent = () -> {
            List<MonoGalleryGroupAdapter.Item> src = currentTab[0] == 0 ? localItems : serverItems;
            int tabIndex = currentTab[0] == 0 ? 0 : 1;
            int currentVisible = Math.min(Math.max(visibleCount[tabIndex], 0), src.size());
            List<MonoGalleryGroupAdapter.Item> shown = new ArrayList<>(src.subList(0, currentVisible));
            adapter.setItems(shown);
            progress.setVisibility(View.GONE);
            if (src.isEmpty()) {
                if (empty != null) empty.setVisibility(View.VISIBLE);
            } else {
                if (empty != null) empty.setVisibility(View.GONE);
            }
            if (btnTabLocal != null) {
                btnTabLocal.setEnabled(true);
                btnTabLocal.setBackgroundResource(
                        currentTab[0] == 0 ? R.drawable.mono_tab_selected : R.drawable.mono_tab_normal);
                btnTabLocal.setTextColor(ContextCompat.getColor(this,
                        currentTab[0] == 0 ? android.R.color.white : R.color.mp_brand_text));
            }
            if (btnTabServer != null) {
                btnTabServer.setEnabled(true);
                btnTabServer.setBackgroundResource(
                        currentTab[0] == 1 ? R.drawable.mono_tab_selected : R.drawable.mono_tab_normal);
                btnTabServer.setTextColor(ContextCompat.getColor(this,
                        currentTab[0] == 1 ? android.R.color.white : R.color.mp_brand_text));
            }
            if (btnLoadMore != null) {
                if (currentVisible < src.size()) {
                    btnLoadMore.setVisibility(View.VISIBLE);
                    btnLoadMore.setEnabled(true);
                } else {
                    btnLoadMore.setVisibility(View.GONE);
                }
            }
        };

        Runnable reloadData = () -> {
            progress.setVisibility(View.VISIBLE);
            if (empty != null) empty.setVisibility(View.GONE);
            executorService.execute(() -> {
                List<MonoFolderImages.LocalGalleryItem> local = MonoFolderImages.loadLocalGalleryItems(Activity_Camera2_Manual.this);
                java.util.Set<String> serverIds = new java.util.HashSet<>();
                List<MonoGalleryGroupAdapter.Item> servers = new ArrayList<>();
                try {
                    String token = TokenManager.getInstance(Activity_Camera2_Manual.this).getToken();
                    if (token != null && !token.isEmpty()) {
                        org.json.JSONArray idArr = ApiService.getMonoAllGalleryIds(token);
                        if (idArr != null) {
                            for (int i = 0; i < idArr.length(); i++) {
                                String id = idArr.optString(i, "");
                                if (id != null && !id.trim().isEmpty()) {
                                    serverIds.add(id.trim());
                                }
                            }
                        }
                        org.json.JSONObject obj = ApiService.getJsonObjectAuthed("/mono-results/groups?page=1&limit=10000", token);
                        org.json.JSONArray arr = obj.optJSONArray("data");
                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                org.json.JSONObject it = arr.optJSONObject(i);
                                if (it == null) continue;
                                String folderId = it.optString("folderId", "");
                                if (folderId.isEmpty()) continue;
                                MonoGalleryGroupAdapter.Item sItem = new MonoGalleryGroupAdapter.Item();
                                sItem.folderId = folderId;
                                sItem.viewUrl = it.optString("viewUrl", buildMonoServerGalleryUrl(folderId));
                                sItem.synced = true;
                                sItem.localSource = false;
                                org.json.JSONArray photos = it.optJSONArray("photos");
                                if (photos != null && photos.length() > 0) {
                                    org.json.JSONObject p0 = photos.optJSONObject(0);
                                    if (p0 != null) {
                                        String u = p0.optString("url", "");
                                        if (!u.isEmpty()) sItem.previewUri = Uri.parse(u);
                                    }
                                }
                                servers.add(sItem);
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "load server mono groups", e);
                }

                List<MonoGalleryGroupAdapter.Item> locals = new ArrayList<>();
                for (MonoFolderImages.LocalGalleryItem l : local) {
                    MonoGalleryGroupAdapter.Item item = new MonoGalleryGroupAdapter.Item();
                    String canonicalId = MonoGalleryFolderIds.resolveCanonicalServerId(
                            l.folderId, serverIds, Activity_Camera2_Manual.this);
                    item.localFolderId = l.folderId;
                    item.folderId = canonicalId;
                    item.previewUri = l.previewUri;
                    item.synced = MonoGalleryFolderIds.isSyncedOnServer(l.folderId, serverIds);
                    item.localSource = true;
                    item.viewUrl = buildMonoServerGalleryUrl(canonicalId);
                    locals.add(item);
                }
                runOnUiThread(() -> {
                    localItems.clear();
                    localItems.addAll(locals);
                    serverItems.clear();
                    serverItems.addAll(servers);
                    visibleCount[0] = Math.min(pageSize, localItems.size());
                    visibleCount[1] = Math.min(pageSize, serverItems.size());
                    renderCurrent.run();
                });
            });
        };

        adapter.setListener(new MonoGalleryGroupAdapter.Listener() {
            @Override
            public void onViewClick(@NonNull MonoGalleryGroupAdapter.Item item) {
                if (item.localSource) {
                    openLocalImageInGallery(item);
                } else {
                    openDriveFolderUrl(item.viewUrl);
                }
            }

            @Override
            public void onPrintClick(@NonNull MonoGalleryGroupAdapter.Item item) {
                executorService.execute(() -> {
                    try {
                        if (!ensurePrinterReadyForDialogPrint()) {
                            return;
                        }
                        Bitmap bm = resolveBitmapForReprint(item);
                        if (bm == null) {
                            runOnUiThread(() -> Toast.makeText(Activity_Camera2_Manual.this, "Không đọc được ảnh để in", Toast.LENGTH_SHORT).show());
                            return;
                        }
                        printImage(bm, 0, 576, false, PrintBitmapMode.get(Activity_Camera2_Manual.this));
                        if (isShowQrEnabled()) {
                            String qrUrl = buildMonoServerGalleryUrl(item.folderId);
                            printMonoDriveQrForUploadedFileLink(qrUrl);
                        } else {
                            Bitmap end = BitmapFactory.decodeResource(getResources(), R.drawable.end);
                            printEmptyAndCut(0, 150, false, PrintBitmapMode.get(Activity_Camera2_Manual.this), end);
                        }
                    } catch (Throwable e) {
                        Log.e(TAG, "print mono gallery item", e);
                        runOnUiThread(() -> Toast.makeText(Activity_Camera2_Manual.this, "In ảnh thất bại", Toast.LENGTH_SHORT).show());
                    }
                });
            }

            @Override
            public void onSyncClick(@NonNull MonoGalleryGroupAdapter.Item item) {
                if (!item.localSource || item.synced || item.previewUri == null) return;
                if (!TokenManager.getInstance(Activity_Camera2_Manual.this).canUseCloudFeatures()) {
                    Toast.makeText(Activity_Camera2_Manual.this, R.string.guest_feature_need_login, Toast.LENGTH_SHORT).show();
                    return;
                }
                executorService.execute(() -> {
                    try {
                        String token = TokenManager.getInstance(Activity_Camera2_Manual.this).getToken();
                        if (token == null || token.isEmpty()) throw new Exception("Thiếu token");
                        String localId = item.localFolderId != null && !item.localFolderId.isEmpty()
                                ? item.localFolderId : item.folderId;
                        File f = MonoFolderImages.resolveFileFromUri(
                                Activity_Camera2_Manual.this,
                                item.previewUri,
                                MonoGalleryFolderIds.localPhotoFileName(localId));
                        if (f == null || !f.exists()) throw new Exception("Không đọc được file local");
                        String uploadFolderId = MonoGalleryFolderIds.upgradeLocalFolderId(
                                localId, Activity_Camera2_Manual.this);
                        ApiService.uploadMonoGalleryPhoto(
                                Activity_Camera2_Manual.this, token, uploadFolderId, f, "1.jpg");
                        runOnUiThread(() -> {
                            Toast.makeText(Activity_Camera2_Manual.this, "Đồng bộ thành công", Toast.LENGTH_SHORT).show();
                            reloadData.run();
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "sync mono local", e);
                        runOnUiThread(() -> Toast.makeText(Activity_Camera2_Manual.this, "Đồng bộ thất bại", Toast.LENGTH_SHORT).show());
                    }
                });
            }

            @Override
            public void onDeleteClick(@NonNull MonoGalleryGroupAdapter.Item item) {
                if (!item.localSource || item.previewUri == null) return;
                new AlertDialog.Builder(Activity_Camera2_Manual.this)
                    .setMessage(R.string.mono_gallery_delete_confirm)
                    .setNegativeButton(R.string.Cancel, (d, w) -> d.dismiss())
                    .setPositiveButton(R.string.mono_gallery_yes_delete, (d, w) -> {
                        d.dismiss();
                        executorService.execute(() -> {
                            boolean ok = MonoFolderImages.deleteImage(Activity_Camera2_Manual.this, item.previewUri);
                            runOnUiThread(() -> {
                                if (ok) {
                                    Toast.makeText(Activity_Camera2_Manual.this, R.string.mono_gallery_deleted, Toast.LENGTH_SHORT).show();
                                    reloadData.run();
                                } else {
                                    Toast.makeText(Activity_Camera2_Manual.this, R.string.mono_gallery_delete_failed, Toast.LENGTH_SHORT).show();
                                }
                            });
                        });
                    })
                    .show();
            }
        });

        if (btnTabLocal != null) {
            btnTabLocal.setOnClickListener(v -> {
                currentTab[0] = 0;
                renderCurrent.run();
            });
        }
        if (btnTabServer != null) {
            btnTabServer.setOnClickListener(v -> {
                currentTab[0] = 1;
                renderCurrent.run();
            });
        }
        if (btnLoadMore != null) {
            btnLoadMore.setOnClickListener(v -> {
                List<MonoGalleryGroupAdapter.Item> src = currentTab[0] == 0 ? localItems : serverItems;
                int tabIndex = currentTab[0] == 0 ? 0 : 1;
                if (visibleCount[tabIndex] < src.size()) {
                    visibleCount[tabIndex] = Math.min(visibleCount[tabIndex] + pageSize, src.size());
                    renderCurrent.run();
                }
            });
        }

        reloadData.run();

        AlertDialog galleryDialog = new AlertDialog.Builder(this)
                .setView(root)
                .create();
        Button btnCloseGallery = root.findViewById(R.id.btnCloseMonoGallery);
        if (btnCloseGallery != null) {
            btnCloseGallery.setOnClickListener(v -> galleryDialog.dismiss());
        }
        galleryDialog.setOnDismissListener(d -> refreshMonoFolderThumbnail());
        galleryDialog.show();
        GlassDialogHelper.applyGlassWindow(galleryDialog);
        Window w = galleryDialog.getWindow();
        if (w != null) {
            DisplayMetrics m = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(m);
            w.setLayout(
                    (int) (m.widthPixels * 0.92f),
                    (int) (m.heightPixels * 0.8f)
            );
        }
        // Gọi sau show/setLayout — một số máy mất cancel-outside nếu set trước
        galleryDialog.setCancelable(true);
        galleryDialog.setCanceledOnTouchOutside(true);
    }

    private void openMonoServerGalleryListDialog() {
        if (!TokenManager.getInstance(this).canUseCloudFeatures()) {
            Toast.makeText(this, R.string.guest_feature_need_login, Toast.LENGTH_SHORT).show();
            return;
        }
        String token = TokenManager.getInstance(this).getToken();
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, R.string.mono_sync_need_login, Toast.LENGTH_SHORT).show();
            return;
        }
        executorService.execute(() -> {
            try {
                org.json.JSONObject data = ApiService.getJsonObjectAuthed("/mono-results/groups?page=1&limit=10000", token);
                org.json.JSONArray arr = data.optJSONArray("data");
                if (arr == null || arr.length() == 0) {
                    runOnUiThread(() -> Toast.makeText(
                        Activity_Camera2_Manual.this,
                        R.string.mono_gallery_empty,
                        Toast.LENGTH_SHORT
                    ).show());
                    return;
                }
                String[] labels = new String[arr.length()];
                String[] urls = new String[arr.length()];
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject item = arr.optJSONObject(i);
                    String folderId = item != null ? item.optString("folderId", "") : "";
                    String viewUrl = item != null ? item.optString("viewUrl", "") : "";
                    labels[i] = folderId;
                    urls[i] = viewUrl;
                }
                runOnUiThread(() -> new AlertDialog.Builder(Activity_Camera2_Manual.this)
                    .setTitle("Server Mono Galleries")
                    .setItems(labels, (d, which) -> {
                        String url = urls[which];
                        if (url == null || url.isEmpty()) {
                            url = buildMonoServerGalleryUrl(labels[which]);
                        }
                        openDriveFolderUrl(url);
                    })
                    .setNegativeButton(R.string.close, (d, w) -> d.dismiss())
                    .show());
            } catch (Exception e) {
                Log.e(TAG, "openMonoServerGalleryListDialog", e);
                runOnUiThread(() -> Toast.makeText(
                    Activity_Camera2_Manual.this,
                    R.string.drive_link_unavailable,
                    Toast.LENGTH_SHORT
                ).show());
            }
        });
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

    private void onMonoPreviewFlexLayout() {
        applyMonoPreviewFlexLayout();
    }

    private View getMonoPreviewFlexSizeRef() {
        return monoPreviewCenterColumn != null ? monoPreviewCenterColumn : monoPreviewFlexHost;
    }

    private void scheduleApplyMonoPreviewFlex() {
        View ref = getMonoPreviewFlexSizeRef();
        if (ref != null) {
            ref.post(this::applyMonoPreviewFlexLayout);
        }
    }

    private void notifyMonoPreviewFlexRecalc() {
        lastMonoFlexSignature = Integer.MIN_VALUE;
        scheduleApplyMonoPreviewFlex();
    }

    /** tỷ lệ cao/rộng (dọc/ngang) của ảnh phụ, để ước dọc = w*A; live luôn 4:3 theo cùng w. */
    private float resolveMonoSecondaryImageAspect() {
        if (image != null && image.getWidth() > 0) {
            return image.getHeight() / (float) image.getWidth();
        }
        if (imageViewSecond == null) {
            return 1f;
        }
        Drawable d = imageViewSecond.getDrawable();
        if (d == null) {
            return 1f;
        }
        int iw = d.getIntrinsicWidth();
        int ih = d.getIntrinsicHeight();
        if (iw > 0 && ih > 0) {
            return ih / (float) iw;
        }
        return 1f;
    }

    /**
     * Thu cùng bề ngang w cho live 4:3 + ảnh phụ (cao w*A) sao cho (3/4)w + gap + w*A vừa khung bố cục.
     */
    private void applyMonoPreviewFlexLayout() {
        View sizeRef = getMonoPreviewFlexSizeRef();
        if (sizeRef == null || monoPreviewFlexContent == null || frameMonoSecond == null) {
            return;
        }
        int hAvail = sizeRef.getHeight();
        int wParent = sizeRef.getWidth();
        if (monoPreviewFlexHost != null) {
            int hostW = monoPreviewFlexHost.getWidth();
            if (hostW > 0) {
                wParent = hostW;
            }
        }
        if (hAvail < 2 || wParent < 2) {
            return;
        }
        /* padding 2dp mỗi cạnh trên mono_preview_flex_content (XML) */
        int edgePad = Math.max(0, Math.round(4f * getResources().getDisplayMetrics().density));
        int hForFit = Math.max(2, hAvail - edgePad);
        float gapPx = 3f * getResources().getDisplayMetrics().density;
        int gap = Math.max(1, Math.round(gapPx));
        float aSec = resolveMonoSecondaryImageAspect();
        if (aSec < 0.1f) {
            aSec = 0.1f;
        }
        if (aSec > 8f) {
            aSec = 8f;
        }
        double denom = 0.75 + aSec;
        int wInner = (int) Math.max(1, Math.floor((hForFit - gap) / denom));
        int w = wInner + edgePad;
        if (w > wParent) {
            w = wParent;
            wInner = w - edgePad;
            if (wInner < 1) {
                wInner = 1;
            }
        }
        int hLivePx = (int) (wInner * 0.75f);
        int hSecApprox = (int) Math.round(wInner * aSec);
        if (hLivePx + gap + hSecApprox > hForFit) {
            wInner = (int) Math.max(1, Math.floor((hForFit - gap) / denom));
            w = wInner + edgePad;
            w = Math.min(w, wParent);
            wInner = w - edgePad;
            if (wInner < 1) {
                wInner = 1;
            }
        }
        int sig = Objects.hash(w, wParent, hForFit, image == null ? 0 : System.identityHashCode(image), Float.floatToIntBits(aSec));
        if (sig == lastMonoFlexSignature) {
            return;
        }
        lastMonoFlexSignature = sig;

        FrameLayout.LayoutParams outer = (FrameLayout.LayoutParams) monoPreviewFlexContent.getLayoutParams();
        if (outer == null) {
            outer = new FrameLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT);
        } else {
            outer.width = w;
            outer.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        outer.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        monoPreviewFlexContent.setLayoutParams(outer);

        LinearLayout.LayoutParams fp = (LinearLayout.LayoutParams) frameMonoSecond.getLayoutParams();
        if (fp == null) {
            fp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        } else {
            fp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            fp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        frameMonoSecond.setLayoutParams(fp);
    }

    /** Chạm chụp gắn trên TextureView; layer khung/preview/đếm nằm trên nên cũng phải bật/tắt cùng. */
    private void setLiveViewCaptureInputEnabled(boolean enabled) {
        if (textureView != null) {
            textureView.setEnabled(enabled);
        }
        if (frame != null) {
            frame.setEnabled(enabled);
        }
        if (imageViewPreview != null) {
            imageViewPreview.setEnabled(enabled);
        }
        if (countdown != null) {
            countdown.setEnabled(enabled);
        }
    }

    /** Switch "In mã QR" — chỉ khi đã đăng nhập (có upload server). */
    private boolean isShowQrEnabled() {
        if (!TokenManager.getInstance(this).canUseCloudFeatures()) {
            return false;
        }
        return getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("Download", false);
    }

    private void setCloudControlEnabled(@Nullable View view) {
        if (view == null) {
            return;
        }
        boolean on = TokenManager.getInstance(this).canUseCloudFeatures();
        view.setEnabled(on);
        view.setAlpha(on ? 1f : 0.45f);
    }

    private void resumeLiveViewAfterPrint() {
        new Handler(getMainLooper()).postDelayed(() -> setLiveViewCaptureInputEnabled(true), 1500);
    }

    private void showPostCapturePreview(Bitmap combinedColor) {
        runOnUiThread(() -> {
            try {
                if (imageViewPreview != null) {
                    imageViewPreview.setImageBitmap(combinedColor);
                    imageViewPreview.setVisibility(View.VISIBLE);
                }
                if (frame != null) {
                    frame.setVisibility(View.GONE);
                }
                if (layoutPrintCancelRow != null) {
                    layoutPrintCancelRow.setVisibility(View.VISIBLE);
                }
                if (btnPrint != null) {
                    btnPrint.setEnabled(true);
                }
                if (btnCancel != null) {
                    btnCancel.setEnabled(true);
                }
                setLiveViewCaptureInputEnabled(false);
                monoPostCapturePending = true;
                try {
                    SocketService.getInstance().notifyControlPageSettingsChanged();
                } catch (Exception ignored) {
                }
            } catch (Exception e) {
                Log.e("FrameLayoutError", "show post capture", e);
            }
        });
    }

    /** Gọi từ main thread, hoặc từ {@link #hidePostCaptureUi} qua runOnUiThread. */
    private void applyHidePostCaptureOnUi() {
        if (imageViewPreview != null) {
            imageViewPreview.setImageBitmap(null);
            imageViewPreview.setVisibility(View.GONE);
        }
        if (frame != null) {
            frame.setVisibility(View.VISIBLE);
        }
        if (layoutPrintCancelRow != null) {
            layoutPrintCancelRow.setVisibility(View.GONE);
        }
        if (btnPrint != null) {
            btnPrint.setEnabled(false);
        }
        if (btnCancel != null) {
            btnCancel.setEnabled(false);
        }
        monoPostCapturePending = false;
        try {
            SocketService.getInstance().notifyControlPageSettingsChanged();
        } catch (Exception ignored) {
        }
    }

    private void hidePostCaptureUi() {
        runOnUiThread(this::applyHidePostCaptureOnUi);
    }

    private void imageProcessing (String path) {

        Bitmap origin = BitmapFactory.decodeFile(path);
        int dpi = origin.getDensity();
        if (dpi == 0) dpi = 203; // 기본 DPI 설정
        Bitmap resizedBitmap = imgSolve.processingImage(origin);
        AtomicReference<Bitmap> bmp = new AtomicReference<>(imgSolve.applySharpening(resizedBitmap, 1.2f));

        bmp.get().setDensity(dpi);

        float contrast = 1.5f;
        int light = 5;
        int[] lightValue1 = {light}; // Adjust brightness based on SeekBar progress
        float[] contrastValue = {contrast};
        Bitmap[] adjustedBitmap2 = {null};

        adjustedBitmap2[0] = imgSolve.adjustBrightness(bmp.get(), lightValue1[0]);
        adjustedBitmap2[0] = imgSolve.adjustContrast(adjustedBitmap2[0], contrastValue[0]);
        adjustedBitmap2[0].setDensity(origin.getDensity());

        //Xu ly khung anh (màu cho lưu; trắng-đen chỉ khi in)
        @SuppressLint("UseCompatLoadingForDrawables")
        Drawable noel = getResources().getDrawable(R.drawable.nothing, null);
        Bitmap bitmapFrameNull = imgSolve.drawableToBitmap(noel);

        Bitmap bitmapFrame;
        if (bitmapList != null && !bitmapList.isEmpty()) {
            String entry = bitmapList.get(currentIndex);

            if (entry == null) {
                bitmapFrame = bitmapFrameNull;
            } else {
                bitmapFrame = UserAssetFileStore.decodeListEntryToBitmap(this, entry);
                if (bitmapFrame == null) {
                    bitmapFrame = bitmapFrameNull;
                }
            }
        } else {
            bitmapFrame = bitmapFrameNull;
        }
        bitmapFrame = imgSolve.resizeBitmapMaintainAspect(bitmapFrame, 1152);

        //Xu ly anh phu
        @SuppressLint("UseCompatLoadingForDrawables")
        Drawable emptyImageview2 = getResources().getDrawable(R.drawable.bottom, null);
        Bitmap emptyImageview2Null = imgSolve.drawableToBitmap(emptyImageview2);

        // Kiểm tra xem bitmapList có phần tử không trước khi lấy currentIndex
        if (bitmapListImageView2 != null && !bitmapListImageView2.isEmpty()) {
            String entry = bitmapListImageView2.get(currentIndexImageView2);

            if (entry == null) {
                image = emptyImageview2Null; // Set bitmapFrameNull nếu null
            } else {
                image = UserAssetFileStore.decodeListEntryToBitmap(this, entry);
                if (image == null) {
                    image = emptyImageview2Null;
                }
            }
        } else {
            image = emptyImageview2Null; // Set bitmapFrameNull nếu bitmapList trống
        }





// Bitmap đã xử lý (adjustedBitmap2[0])
        Bitmap processedBitmap2 = adjustedBitmap2[0];
        //processedBitmap2=imgSolve.cropLeftRightToSquare(processedBitmap2);
        processedBitmap2 = imgSolve.resizeBitmapMaintainAspect(processedBitmap2,1152); // 2× độ rộng in dither 576
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

        // Upload/gallery: ảnh thô gốc + khung (in vẫn dùng flippedBitmap đã xử lý)
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
        combinedBitmap.setDensity(bitmapFrame.getDensity());
        combinedBitmapColor.setDensity(bitmapFrame.getDensity());

        showPostCapturePreview(combinedBitmapColor);


        btnPrint.setOnClickListener(v -> {
            try {
                if (PrinterTestMode.isEnabled(Activity_Camera2_Manual.this)) {
                    try {
                        // Cùng folderId cho local + server (tránh lệch tên)
                        final String monoFolderNameTest = MonoGalleryFolderIds.generate(Activity_Camera2_Manual.this);
                        final String monoLocalPhotoNameTest = MonoGalleryFolderIds.localPhotoFileName(monoFolderNameTest);
                        Bitmap fullPageT = Utility.buildVerticalStackForPrintWidth(combinedBitmapColor, image, 576);
                        if (fullPageT == null) {
                            fullPageT = combinedBitmapColor != null ? combinedBitmapColor : combinedBitmap;
                        }
                        MonoGallerySaver.saveBitmapToMonoFolder(Activity_Camera2_Manual.this, fullPageT, monoLocalPhotoNameTest);
                        File tmp = PrinterTestMode.writeJpegToCacheDir(Activity_Camera2_Manual.this, fullPageT, monoLocalPhotoNameTest);
                        if (fullPageT != null && fullPageT != combinedBitmap && fullPageT != combinedBitmapColor) {
                            fullPageT.recycle();
                        }
                        if (combinedBitmapColor != null) {
                            combinedBitmapColor.recycle();
                        }
                        if (isShowQrEnabled()) {
                            printMonoDriveQrForUploadedFileLink(buildMonoServerGalleryUrl(monoFolderNameTest));
                        }
                        scheduleServerUpload(tmp, monoFolderNameTest);
                        imgSolve.clearCache();
                        resumeLiveViewAfterPrint();
                    } catch (Exception e) {
                        Log.e(TAG, "Test mode save/upload", e);
                        imgSolve.clearCache();
                        resumeLiveViewAfterPrint();
                    }
                    scheduleMonoFolderThumbnailUpdate();
                    counterTime++;
                    SharedPreferences preferences2t = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE);
                    preferences2t.edit().putInt("counterTime", counterTime).apply();
                    runOnUiThread(() -> {
                        try {
                            numberCount.setText(String.valueOf(counterTime));
                        } catch (Exception e) {
                            Log.e("FrameLayoutError", "Error setting visibility for FrameLayout", e);
                        }
                        applyHidePostCaptureOnUi();
                    });
                } else {
                File combinedFileForDrive = null;
                String combinedNameForDrive = null;
                final String monoFolderName = MonoGalleryFolderIds.generate(Activity_Camera2_Manual.this);
                final String monoLocalPhotoName = MonoGalleryFolderIds.localPhotoFileName(monoFolderName);
                Bitmap fullPageForFile = Utility.buildVerticalStackForPrintWidth(combinedBitmapColor, image, PRINT_THREE_INCH);
                try {
                    Bitmap sourceForDrive = fullPageForFile != null
                            ? fullPageForFile
                            : (combinedBitmapColor != null ? combinedBitmapColor : combinedBitmap);
                    if (sourceForDrive != null) {
                        combinedNameForDrive = monoLocalPhotoName;
                        combinedFileForDrive = PrinterTestMode.writeJpegToCacheDir(
                                Activity_Camera2_Manual.this, sourceForDrive, combinedNameForDrive);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Ghi ảnh đã ghép (khung+phụ) cho Drive", e);
                }
                try {
                    if (fullPageForFile != null) {
                        MonoGallerySaver.saveBitmapToMonoFolder(Activity_Camera2_Manual.this, fullPageForFile, monoLocalPhotoName);
                    } else {
                        Bitmap src = combinedBitmapColor != null ? combinedBitmapColor : combinedBitmap;
                        if (src != null) {
                            MonoGallerySaver.saveBitmapToMonoFolder(Activity_Camera2_Manual.this, src, monoLocalPhotoName);
                        }
                    }
                } catch (Exception e) {
                    Log.d(TAG, "save gallery: " + e.getMessage());
                }
                if (fullPageForFile != null) {
                    fullPageForFile.recycle();
                }
                if (combinedBitmapColor != null) {
                    combinedBitmapColor.recycle();
                }
                scheduleMonoFolderThumbnailUpdate();
                PrintNumber();
                final int printMode = PrintBitmapMode.get(Activity_Camera2_Manual.this);
                printImage(
                        combinedBitmap,
                        0,
                        PRINT_THREE_INCH , false,
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
                    Bitmap bitmapPrint = BitmapFactory.decodeResource(Activity_Camera2_Manual.this.getResources(), R.drawable.end);
                    printEmptyAndCut(0, 150, false, printMode, bitmapPrint);
                }
                scheduleServerUpload(fCombinedDrive, monoFolderName);
                imgSolve.clearCache();
                resumeLiveViewAfterPrint();
                //                imgSolve.clearCache();
                counterTime++;
                SharedPreferences preferences2 = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences2.edit();
                editor.putInt("counterTime", counterTime);
                editor.apply();
                runOnUiThread(() -> {
                    try {
                        numberCount.setText(String.valueOf(counterTime));
                    } catch (Exception e) {
                        Log.e("FrameLayoutError", "Error setting visibility for FrameLayout", e);
                    }
                    applyHidePostCaptureOnUi();
                });
                }
            } catch (Exception e) {
                Log.e("PrintError", "Exception during printImage call", e);
                resumeLiveViewAfterPrint();
            }

            adjustedBitmap2[0]=null;
            bmp.set(null);

        });

        btnCancel.setOnClickListener(v -> {
            try {
                resumeLiveViewAfterPrint();
                imgSolve.clearCache();
                runOnUiThread(() -> {
                    try {
                        applyHidePostCaptureOnUi();
                    } catch (Exception e) {
                        Log.e("FrameLayoutError", "cancel post capture", e);
                    }
                });
            } catch (Exception e) {
                Log.e("PrintError", "Exception during printImage call", e);
            }
            adjustedBitmap2[0]=null;
            bmp.set(null);
        });

    }

    private String buildMonoServerGalleryUrl(String folderName) {
        return ApiService.getApiBaseUrl() + "/mono-results/g/" + folderName;
    }

    @Nullable
    private Bitmap resolveBitmapForReprint(@NonNull MonoGalleryGroupAdapter.Item item) {
        Bitmap bm = null;
        if (item.previewUri != null) {
            bm = decodeBitmapFromUriSafe(item.previewUri);
        }
        if (bm != null) {
            return bm;
        }
        String picUrl = null;
        String token = TokenManager.getInstance(Activity_Camera2_Manual.this).getToken();
        if (token != null && !token.isEmpty() && item.folderId != null && !item.folderId.isEmpty()) {
            try {
                org.json.JSONObject pub = ApiService.getJsonObjectAuthed("/mono-results/public/" + item.folderId, token);
                org.json.JSONArray photos = pub.optJSONArray("photos");
                if (photos != null && photos.length() > 0) {
                    org.json.JSONObject p0 = photos.optJSONObject(0);
                    if (p0 != null) {
                        picUrl = p0.optString("url", null);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if ((picUrl == null || picUrl.isEmpty()) && item.previewUri != null) {
            String s = String.valueOf(item.previewUri);
            if (s.startsWith("http://") || s.startsWith("https://")) {
                picUrl = s;
            }
        }
        if (picUrl == null || picUrl.isEmpty()) {
            return null;
        }
        return decodeBitmapFromUrlSafe(picUrl);
    }

    @Nullable
    private Bitmap decodeBitmapFromUriSafe(@NonNull Uri uri) {
        try {
            String scheme = uri.getScheme();
            if (!ContentResolver.SCHEME_CONTENT.equals(scheme) && !ContentResolver.SCHEME_FILE.equals(scheme)) {
                return null;
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (java.io.InputStream in1 = getContentResolver().openInputStream(uri)) {
                if (in1 == null) return null;
                BitmapFactory.decodeStream(in1, null, bounds);
            }
            int sample = 1;
            int targetW = 1400;
            while ((bounds.outWidth / sample) > targetW) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = Math.max(1, sample);
            try (java.io.InputStream in2 = getContentResolver().openInputStream(uri)) {
                if (in2 == null) return null;
                return BitmapFactory.decodeStream(in2, null, opts);
            }
        } catch (Throwable t) {
            Log.w(TAG, "decodeBitmapFromUriSafe", t);
            return null;
        }
    }

    @Nullable
    private Bitmap decodeBitmapFromUrlSafe(@NonNull String urlStr) {
        java.net.HttpURLConnection conn = null;
        try {
            java.net.URL url = new java.net.URL(urlStr);
            conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "image/*,*/*");
            try (java.io.InputStream in = conn.getInputStream();
                 ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    bos.write(buf, 0, n);
                }
                byte[] data = bos.toByteArray();
                if (data.length == 0) return null;
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
                int sample = 1;
                int targetW = 1400;
                while ((bounds.outWidth / sample) > targetW) sample *= 2;
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = Math.max(1, sample);
                return BitmapFactory.decodeByteArray(data, 0, data.length, opts);
            }
        } catch (Throwable t) {
            Log.w(TAG, "decodeBitmapFromUrlSafe", t);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
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
                String token = TokenManager.getInstance(Activity_Camera2_Manual.this).getToken();
                if (token == null || token.isEmpty()) {
                    Log.e(TAG, "Upload Mono server: thiếu token");
                    return;
                }
                org.json.JSONObject uploadRes = ApiService.uploadMonoGalleryPhoto(
                        Activity_Camera2_Manual.this, token, folderId, fileToUpload, "1.jpg");
                Log.d(TAG, "Upload Mono server OK: " + uploadRes.optString("folderId", folderId));
            } catch (Exception e) {
                Log.e(TAG, "Upload Mono server lỗi", e);
            }
        });
    }


    public void printImage(final Bitmap bitmap, final int light, final int size,
                           final boolean isRotate, final int sype) {


        executorService.execute(() -> {
            if (bitmap == null || bitmap.isRecycled()) {
                notifyPrintFailure(new IllegalArgumentException("bitmap null/recycled"));
                return;
            }
            Bitmap bitmapPrint = bitmap;
            bitmapPrint.setDensity(bitmap.getDensity());
            if (isRotate) {
                bitmapPrint = Utility.Tobitmap90(bitmapPrint);  // Xoay ảnh nếu cần
            }
            if (size != 0)
                bitmapPrint = Utility.Tobitmap(bitmapPrint, size, Utility.getHeight(size, bitmapPrint.getWidth(), bitmapPrint.getHeight()));


            try {
                // Dither (sype=1): mật độ mực vừa phải — tránh quá nhạt trên nhiệt
                Print.SetPrintDensity((byte) 4);
                Print.setPrintResolution(203,203);
                Print.PrintBitmap(bitmapPrint, sype, light);  // In ảnh


            } catch (Exception e) {
                Log.w(TAG, "printImage first attempt failed, fallback print", e);
                try {
                    // Fallback for some printer firmwares that fail with setPrintResolution/light params.
                    Print.PrintBitmap(bitmapPrint, PrintBitmapMode.get(Activity_Camera2_Manual.this), 0);
                } catch (Exception ex) {
                    notifyPrintFailure(ex);
                }
            }
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
            if (bitmapPrint != null && bitmapPrint != bitmap && !bitmapPrint.isRecycled()) {
                bitmapPrint.recycle();
            }
        });
    }
    /**
     * Phiếu in QR: mã hóa <b>link file ảnh vừa tải</b> (xem/ tải đúng tấm in), không mở cả thư mục Mono
     * (tránh khách quét thấy ảnh user khác trong cùng folder).
     */
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
            // QR dùng Threshold để dễ quét; ảnh chính dùng chế độ user chọn
            printQR(qr, 0, 150, true, PrintBitmapMode.THRESHOLD);
            Bitmap end = BitmapFactory.decodeResource(getResources(), R.drawable.end);
            printEmptyAndCut(0, 140, false, printMode, end);
        } else {
            Log.e(TAG, "Không tạo được QR từ link file");
            Bitmap end = BitmapFactory.decodeResource(getResources(), R.drawable.end);
            printEmptyAndCut(0, 150, false, printMode, end);
        }
    }

    private CompletableFuture<String> uploadPrintedFileWithRetry(
            GoogleDriveService driveService,
            @Nullable String firstFolderId,
            @Nullable File file,
            @Nullable String displayName
    ) {
        if (file == null || !file.exists() || displayName == null || displayName.isEmpty()) {
            Log.e(TAG, "uploadPrintedFileWithRetry: file/displayName không hợp lệ");
            return CompletableFuture.completedFuture(null);
        }
        final String path = file.getAbsolutePath();
        if (firstFolderId == null || firstFolderId.isEmpty()) {
            Log.w(TAG, "uploadPrintedFileWithRetry: firstFolderId rỗng, thử tạo/lấy lại folder");
            return CompletableFuture.supplyAsync(() -> driveService.getOrCreateMonoUserFolderId(Activity_Camera2_Manual.this))
                    .thenCompose(fid -> {
                        if (fid == null || fid.isEmpty()) {
                            return CompletableFuture.completedFuture(null);
                        }
                        return driveService.uploadFileToDrive(path, fid, displayName);
                    });
        }
        return driveService.uploadFileToDrive(path, firstFolderId, displayName)
                .thenCompose(link -> {
                    if (link != null && !link.isEmpty()) {
                        return CompletableFuture.completedFuture(link);
                    }
                    Log.w(TAG, "Upload lần 1 thất bại, retry bằng folder mới/đồng bộ");
                    return CompletableFuture.supplyAsync(() -> driveService.getOrCreateMonoUserFolderId(Activity_Camera2_Manual.this))
                            .thenCompose(fid -> {
                                String retryFolderId = (fid == null || fid.isEmpty()) ? firstFolderId : fid;
                                return driveService.uploadFileToDrive(path, retryFolderId, displayName);
                            });
                });
    }

    public void printQR(final Bitmap bitmap, final int light, final int size,
                        final boolean haveWifi, final int sype) {
        executorService.execute(() -> {
            try {
                Bitmap bitmapPrint = bitmap;
                SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
                String lang = prefs.getString("language", "vi");
                // Lấy ảnh từ drawable
                Bitmap imageBitmap ;
                // Xoay QR code nếu cần

                    if (haveWifi) {

                        if (lang.equals("vi")) {
                            imageBitmap = BitmapFactory.decodeResource(Activity_Camera2_Manual.this.getResources(), R.drawable.getimage);
                        }
                        else if (lang.equals("en")) {
                            imageBitmap = BitmapFactory.decodeResource(Activity_Camera2_Manual.this.getResources(), R.drawable.getimageeng);
                        }
                        else if (lang.equals("ko")) {
                            imageBitmap = BitmapFactory.decodeResource(Activity_Camera2_Manual.this.getResources(), R.drawable.getimagekor);
                        }
                        else{
                            imageBitmap = null;
                        }
                    }
                    else{
                        imageBitmap = null;
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
                notifyPrintFailure(e);
            }
        });
    }

    public void printEmptyAndCut(final int light, final int size,
                                 final boolean isRotate, final int sype, final Bitmap bitmap) {
        executorService.execute(() -> {
            // Lấy ảnh từ drawable (end.jpg)
            Bitmap bitmapPrint=bitmap;
            if (bitmapPrint == null) {
                notifyPrintFailure(null);
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
                notifyPrintFailure(e);
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
                notifyPrintFailure(e);
            }
            bitmapPrint.recycle();
        });
    }

    private boolean ensurePrinterReadyForDialogPrint() {
        if (PrinterTestMode.isEnabled(this)) {
            return true;
        }
        if (Print.IsOpened()) {
            return true;
        }
        try {
            if (device != null) {
                try {
                    Print.PortClose();
                } catch (Exception ignored) {
                }
                if (Print.PortOpen(Activity_Camera2_Manual.this, device) == 0 && Print.IsOpened()) {
                    return true;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "reopen printer port failed", t);
        }
        if (havingUsb) {
            try {
                connectUSB(); // Request USB permission/open port asynchronously.
            } catch (Throwable t) {
                Log.w(TAG, "connectUSB retry failed", t);
            }
        }
        runOnUiThread(() -> Toast.makeText(
                Activity_Camera2_Manual.this,
                "Máy in chưa sẵn sàng. Hãy cắm lại USB và thử lại.",
                Toast.LENGTH_SHORT
        ).show());
        return false;
    }

    private void notifyPrintFailure(@Nullable Throwable error) {
        if (error != null) {
            Log.e(TAG, "Print failed", error);
        } else {
            Log.e(TAG, "Print failed: bitmap is null");
        }
        String reason = "In ảnh thất bại";
        if (error != null && error.getMessage() != null && !error.getMessage().isEmpty()) {
            reason = "In ảnh thất bại: " + error.getMessage();
            if (error.getMessage().contains("WriteData") || error.getMessage().contains("null object reference")) {
                reason = "In ảnh thất bại: máy in chưa kết nối ổn định, vui lòng rút/cắm lại USB";
            }
        }
        final String reasonText = reason;
        runOnUiThread(() -> Toast.makeText(Activity_Camera2_Manual.this, reasonText, Toast.LENGTH_SHORT).show());
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
            configureTransform(width, height);
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
        if (!MachineManager.getInstance(this).enforceDurableStorageAccessRequired(this)) {
            Log.w(TAG, "Blocked until All files access is granted");
            return;
        }
        final boolean softSwitch = MonoScreenSwitch.consumeSoftResume();
        MonoDriveServerSync.requestSyncIfLoggedIn(this);
        startBackgroundThread();
        if (textureView != null) {
            textureView.removeCallbacks(deferredOpenCameraRunnable);
        }
        try {
            SocketService.getInstance().attachControlPageBridge(this, textureView);
        } catch (Exception ignored) {
        }
        if (softSwitch && textureView != null) {
            textureView.postDelayed(deferredOpenCameraRunnable, 300);
            if (imageMonoLatestThumb != null) {
                imageMonoLatestThumb.postDelayed(monoFolderThumbRefreshRetry, 350);
            }
        } else {
            openCameraIfReady();
            refreshMonoFolderThumbnail();
        }
    }
    @Override
    protected void onPause() {
        Log.e(TAG, "onPause");
        if (textureView != null) {
            textureView.removeCallbacks(deferredOpenCameraRunnable);
        }
        if (imageMonoLatestThumb != null) {
            imageMonoLatestThumb.removeCallbacks(monoFolderThumbRefreshRetry);
        }
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
    /** Giữ Manual sống trên stack — lần sau vào lại không phải tạo mới (nhanh hơn). */
    private void returnToMainActivity() {
        MonoScreenSwitch.mark();
        Intent intent = new Intent(this, Activity_Camera2.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        android.app.ActivityOptions options = android.app.ActivityOptions.makeCustomAnimation(
                this, R.anim.mono_activity_pop_enter, R.anim.mono_activity_pop_exit);
        startActivity(intent, options.toBundle());
    }

    @Override
    public void onBackPressed() {
        returnToMainActivity();
    }
    @Override
    protected void onDestroy() {
        if (imageMonoLatestThumb != null) {
            imageMonoLatestThumb.removeCallbacks(monoFolderThumbRefreshRetry);
        }
        try {
            SocketService.getInstance().clearControlPageBridge();
        } catch (Exception ignored) {
        }
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
        return "manual";
    }

    @Override
    public boolean isMonoPostCapturePending() {
        return monoPostCapturePending;
    }

    @Override
    public void onControlPageSetIso(String isoValue) {
        runOnUiThread(() -> {
            applyISO(isoValue);
            updateISO(isoValue);
            refreshIsoExposurePanelDisplay();
            SocketService.getInstance().notifyControlPageSettingsChanged();
        });
    }

    @Override
    public void onControlPageSetExposure(String exposureNs) {
        runOnUiThread(() -> {
            applyExpose(exposureNs);
            updateExposure(exposureNs);
            refreshIsoExposurePanelDisplay();
            SocketService.getInstance().notifyControlPageSettingsChanged();
        });
    }

    @Override
    public void onControlPageSetPrintMode(int mode) {
        PrintBitmapMode.set(this, mode);
        refreshOpenSettingsDialogFromPrefs();
        SocketService.getInstance().notifyControlPageSettingsChanged();
    }

    @Override
    public void onControlPageSetPrinterTest(boolean enabled) {
        PrinterTestMode.setEnabled(this, enabled);
        refreshOpenSettingsDialogFromPrefs();
        SocketService.getInstance().notifyControlPageSettingsChanged();
    }

    @Override
    public void onControlPageSetQrPrint(boolean enabled) {
        getSharedPreferences("settings", MODE_PRIVATE).edit()
                .putBoolean("Download", enabled).apply();
        refreshOpenSettingsDialogFromPrefs();
        SocketService.getInstance().notifyControlPageSettingsChanged();
    }

    @Override
    public void onControlPageSetClickButtonHidden(boolean hidden) {
        // Pref dùng chung với Main — khi về trang chính sẽ áp dụng.
        getSharedPreferences("settings", MODE_PRIVATE).edit()
                .putBoolean("click_button_hidden", hidden).apply();
        SocketService.getInstance().notifyControlPageSettingsChanged();
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
                Toast.makeText(Activity_Camera2_Manual.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onControlPageSelectSubPhoto(String subPhotoId) {
        MonoAssetSelectHelper.selectSubById(this, subPhotoId, new MonoAssetSelectHelper.AfterSelect() {
            @Override
            public void onApplied(int index) {
                currentIndexImageView2 = index;
                SharedPreferences p2 = getSharedPreferences("MyAppPrefs2", MODE_PRIVATE);
                String json = p2.getString("ImageViewList", "[]");
                bitmapListImageView2 = new Gson().fromJson(json, new TypeToken<List<String>>() {}.getType());
                updateImageView2(currentIndexImageView2);
                SocketService.getInstance().notifyControlPageSettingsChanged();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(Activity_Camera2_Manual.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onControlPageCapture() {
        runOnUiThread(() -> {
            if (monoPostCapturePending) return;
            if (!Print.IsOpened() && !PrinterTestMode.isEnabled(this)) {
                Toast.makeText(this, getString(R.string.please_connect_printer), Toast.LENGTH_SHORT).show();
                return;
            }
            // Camera chưa sẵn (đang soft-switch Main→Manual): thử lại ngắn
            if (cameraDevice == null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    if (isFinishing() || monoPostCapturePending) return;
                    if (cameraDevice == null) {
                        Toast.makeText(this, "Camera chưa sẵn sàng, thử lại sau", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    takePicture();
                }, 500);
                return;
            }
            takePicture();
        });
    }

    @Override
    public void onControlPagePrint() {
        runOnUiThread(() -> {
            if (btnPrint != null && btnPrint.isEnabled() && monoPostCapturePending) {
                btnPrint.performClick();
            }
        });
    }

    @Override
    public void onControlPageCancelPostCapture() {
        runOnUiThread(() -> {
            if (btnCancel != null && btnCancel.isEnabled() && monoPostCapturePending) {
                btnCancel.performClick();
            } else {
                hidePostCaptureUi();
            }
        });
    }

    @Override
    public void onControlPageNavigateBackToMain() {
        runOnUiThread(this::returnToMainActivity);
    }

}


