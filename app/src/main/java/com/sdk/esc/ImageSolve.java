package com.sdk.esc;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.util.Log;
import java.io.File;
import java.util.Objects;
import jp.co.cyberagent.android.gpuimage.GPUImage;
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter;
import jp.co.cyberagent.android.gpuimage.filter.GPUImageSharpenFilter;


public class ImageSolve {
    private final Context context;
    public Bitmap resizeBitmapWithGPUImage(Context context, Bitmap originalBitmap, int newWidth) {

        // 원본 이미지의 DPI 가져오기
        int dpi = originalBitmap.getDensity(); // DPI 값을 가져옵니다.
        if (dpi == 0) dpi = 203; // 기본 DPI 설정 (203은 일반 DPI 기본값)
        // Resize ảnh trước

        int originalWidth = originalBitmap.getWidth();
        int originalHeight = originalBitmap.getHeight();
        int newHeight = (int) Math.round((double) newWidth / originalWidth * originalHeight);
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true);

        // DPI 설정을 유지
        resizedBitmap.setDensity(dpi);

        // Khởi tạo GPUImage
        GPUImage gpuImage = new GPUImage(context);
        gpuImage.setImage(resizedBitmap);

        // Áp dụng filter (hoặc giữ nguyên nếu không cần filter)
        gpuImage.setFilter(new GPUImageFilter());

        // Lấy ảnh đã xử lý
        Bitmap filteredBitmap = gpuImage.getBitmapWithFilterApplied();

        // DPI 설정 유지
        filteredBitmap.setDensity(dpi);

        return filteredBitmap;
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
}
