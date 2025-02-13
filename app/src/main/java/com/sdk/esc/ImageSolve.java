package com.sdk.esc;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.util.Objects;
import jp.co.cyberagent.android.gpuimage.GPUImage;
import jp.co.cyberagent.android.gpuimage.filter.GPUImageSharpenFilter;


public class ImageSolve {
    private final Context context;
    public Bitmap createTextBitmap(String counterTime, String formatted) {
        int width = 576;  // Chiều rộng cố định
        int height = 70; // Chiều cao có thể điều chỉnh

        // Tạo một bitmap trống
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE); // Đổ nền trắng

        // Tạo Paint để vẽ text
        Paint paint = new Paint();
        paint.setColor(Color.BLACK);  // Màu chữ đen
        paint.setTextSize(30);        // Cỡ chữ
        paint.setAntiAlias(true);     // Làm mịn chữ

        // Vẽ counterTime ở sát bên trái
        canvas.drawText(counterTime, 10, (float) height / 2 + 15, paint);

        // Tính toán vị trí của formatted để căn phải
        float textWidth = paint.measureText(formatted);
        canvas.drawText(formatted, width - textWidth - 10, (float) height / 2 + 15, paint);

        return bitmap;
    }

    public Bitmap generateQRCode(String text, int size) {
        try {
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            BitMatrix bitMatrix = barcodeEncoder.encode(text, BarcodeFormat.QR_CODE, size, size);
            return barcodeEncoder.createBitmap(bitMatrix);
        } catch (WriterException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Bitmap processingImage(Bitmap origin) {

        // Bitmap을 Mat 객체로 변환
        origin = origin.copy(Bitmap.Config.ARGB_8888, true); // Bitmap 포맷을 ARGB_8888로 변환
        Mat matOriginal = new Mat();
        Utils.bitmapToMat(origin, matOriginal);

        // 흑백 이미지로 변환
        Mat matGray = new Mat();
        Imgproc.cvtColor(matOriginal, matGray, Imgproc.COLOR_BGR2GRAY);

        // CLAHE 적용
        Mat matCLAHE = new Mat();
        CLAHE clahe = Imgproc.createCLAHE(1, new org.opencv.core.Size(3, 3));
        clahe.apply(matGray, matCLAHE);

        // *가우시안 블러 적용*
        Mat matBlurred = new Mat();
        Imgproc.GaussianBlur(matCLAHE, matBlurred, new org.opencv.core.Size(3, 3), 0);

        // Mat을 Bitmap으로 변환
        Bitmap processedBitmap = Bitmap.createBitmap(matBlurred.cols(), matBlurred.rows(), Bitmap.Config.ARGB_8888);
        Log.d("Bitmap Dimensions", "Width: " + processedBitmap.getWidth() + ", Height: " + processedBitmap.getHeight());

        Utils.matToBitmap(matBlurred, processedBitmap);

        // *GPUImage를 사용하여 크


        return processedBitmap;
    }

    public Bitmap drawableToBitmap(Drawable drawable) {
        // Kiểm tra nếu drawable là BitmapDrawable
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }

        // Nếu không phải BitmapDrawable, bạn có thể chuyển đổi thông qua Canvas
        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ALPHA_8);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    public Bitmap applySharpening(Bitmap inputBitmap, float sharpness) {
        GPUImage gpuImage = new GPUImage(context);

        // Thiết lập ảnh đầu vào
        gpuImage.setImage(inputBitmap);

        // Tạo và áp dụng bộ lọc làm sắc
        GPUImageSharpenFilter sharpenFilter = new GPUImageSharpenFilter();
        sharpenFilter.setSharpness(sharpness); // Giá trị sắc nét (tùy chỉnh, thường từ -4.0 đến 4.0)
        gpuImage.setFilter(sharpenFilter);
        // 필터가 적용된 Bitmap 생성
        Bitmap sharpenedBitmap = gpuImage.getBitmapWithFilterApplied();

        // DPI 유지
        sharpenedBitmap.setDensity(inputBitmap.getDensity());

        return sharpenedBitmap;
    }
    // Constructor nhận context
    public ImageSolve(Context context) {
        this.context = context;
    }
    public Bitmap resizeBitmapMaintainAspect(Bitmap originalBitmap, int newWidth) {
        // Tính toán chiều cao mới giữ tỷ lệ ảnh
        int originalWidth = originalBitmap.getWidth();
        int originalHeight = originalBitmap.getHeight();
        int newHeight = (int) ((double) newWidth / originalWidth * originalHeight);

        // Sử dụng createScaledBitmap để thay đổi kích thước ảnh
        return Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true);
    }
    public Bitmap convertToGrayscale(Bitmap original) {
        // 원본 Bitmap의 DPI 가져오기 및 포맷 확인
        int dpi = original.getDensity();
        original.getConfig();
        Bitmap.Config config = original.getConfig();

        // 새로운 Bitmap 생성
        Bitmap grayscaleBitmap = Bitmap.createBitmap(
                original.getWidth(),
                original.getHeight(),
                config
        );

        // DPI 설정 유지
        grayscaleBitmap.setDensity(dpi);

        // Canvas 및 Paint 초기화
        Canvas canvas = new Canvas(grayscaleBitmap);
        Paint paint = new Paint();

        // ColorMatrix를 사용하여 흑백 변환 필터 설정
        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.setSaturation(0); // 채도를 0으로 설정하여 그레이스케일로 변환
        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
        paint.setColorFilter(filter);

        // Canvas에 원본 이미지를 그리기
        canvas.drawBitmap(original, 0, 0, paint);

        // 변환된 Bitmap 반환
        return grayscaleBitmap;
    }
    public Bitmap convertALPHA8(Bitmap original) {
        // 원본 Bitmap의 DPI 가져오기 및 포맷 확인
        int dpi = original.getDensity();
        original.getConfig();
        Bitmap.Config config = original.getConfig();

        // 새로운 Bitmap 생성
        Bitmap grayscaleBitmap = Bitmap.createBitmap(
                original.getWidth(),
                original.getHeight(),
                config
        );

        // DPI 설정 유지
        grayscaleBitmap.setDensity(dpi);

        // Canvas 및 Paint 초기화
        Canvas canvas = new Canvas(grayscaleBitmap);
        Paint paint = new Paint();

        // ColorMatrix를 사용하여 흑백 변환 필터 설정


        // Canvas에 원본 이미지를 그리기
        canvas.drawBitmap(original, 0, 0, paint);

        // 변환된 Bitmap 반환
        return grayscaleBitmap;
    }
    public Bitmap adjustBrightness(Bitmap original, int brightness) {
        ColorMatrix matrix = new ColorMatrix();
        matrix.set(new float[]{
                1, 0, 0, 0, brightness,
                0, 1, 0, 0, brightness,
                0, 0, 1, 0, brightness,
                0, 0, 0, 1, 0
        });
        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(matrix);
        Bitmap adjustedBitmap = Bitmap.createBitmap(original.getWidth(), original.getHeight(), original.getConfig());
        Canvas canvas = new Canvas(adjustedBitmap);
        Paint paint = new Paint();
        paint.setColorFilter(filter);
        canvas.drawBitmap(original, 0, 0, paint);
        return adjustedBitmap;
    }
    public Bitmap adjustContrast(Bitmap original, float contrast) {
        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.set(new float[] {
                contrast, 0, 0, 0, 0,
                0, contrast, 0, 0, 0,
                0, 0, contrast, 0, 0,
                0, 0, 0, 1, 0
        });

        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
        Bitmap adjustedBitmap = Bitmap.createBitmap(original.getWidth(), original.getHeight(), original.getConfig());
        Canvas canvas = new Canvas(adjustedBitmap);
        Paint paint = new Paint();
        paint.setColorFilter(filter);
        canvas.drawBitmap(original, 0, 0, paint);
        return adjustedBitmap;
    }

    public Bitmap adjustContrastWithOpenCV(Bitmap original, float contrastFactor) {
        // Chuyển Bitmap thành Mat
        Mat mat = new Mat();
        Utils.bitmapToMat(original, mat);

        // Tạo Mat đầu ra
        Mat result = new Mat();

        // Điều chỉnh độ tương phản: contrastFactor > 1 làm tăng tương phản, <1 thì giảm
        mat.convertTo(result, -1, contrastFactor, 0);

        // Chuyển lại Bitmap
        Bitmap output = Bitmap.createBitmap(original.getWidth(), original.getHeight(), original.getConfig());
        Utils.matToBitmap(result, output);

        return output;
    }

    public void clearCache() {
        File cacheDir = context.getCacheDir(); // Lấy thư mục cache
        if (cacheDir != null && cacheDir.isDirectory()) {
            // Lấy danh sách các tệp trong thư mục cache và xóa từng tệp
            for (File file : Objects.requireNonNull(cacheDir.listFiles())) {
                if (file.isFile()) {
                    boolean isDeleted = file.delete(); // Kiểm tra kết quả xóa tệp
                    if (isDeleted) {
                        // Tệp xóa thành công
                        Log.d("ClearCache", "Deleted file: " + file.getName());
                    } else {
                        // Xóa tệp thất bại
                        Log.d("ClearCache", "Failed to delete file: " + file.getName());
                    }
                }
            }
        }
    }
    // Hàm kiểm tra file có phải ảnh không

}
