package com.sdk.esc;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.renderscript.ScriptIntrinsicConvolve3x3;
import android.view.View;


import java.io.File;
import java.util.Arrays;
import java.util.Objects;

import jp.co.cyberagent.android.gpuimage.GPUImage;
import jp.co.cyberagent.android.gpuimage.filter.GPUImageBilateralBlurFilter;
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter;
import jp.co.cyberagent.android.gpuimage.filter.GPUImageGaussianBlurFilter;
import jp.co.cyberagent.android.gpuimage.filter.GPUImageGrayscaleFilter;
import jp.co.cyberagent.android.gpuimage.filter.GPUImageSharpenFilter;


public class ImageSolve {
    private final Context context;
    public Bitmap smoothImageWithGPUImage(Bitmap originalBitmap, float distanceNormalizationFactor) {
        GPUImage gpuImage = new GPUImage(context);
        gpuImage.setImage(originalBitmap);

        GPUImageBilateralBlurFilter bilateralFilter = new GPUImageBilateralBlurFilter();
        bilateralFilter.setDistanceNormalizationFactor(distanceNormalizationFactor);

        gpuImage.setFilter(bilateralFilter);
        return gpuImage.getBitmapWithFilterApplied();
    }
    public Bitmap applyGaussianBlurToBitmap(Bitmap srcBitmap, Context context, float radius) {
        // Khởi tạo GPUImage với context
        GPUImage gpuImage = new GPUImage(context);

        // Tạo bộ lọc Gaussian Blur với bán kính tùy chỉnh
        GPUImageGaussianBlurFilter blurFilter = new GPUImageGaussianBlurFilter();
        blurFilter.setBlurSize(radius);  // Điều chỉnh bán kính (mức độ làm mờ)

        // Áp dụng bộ lọc Gaussian Blur cho ảnh
        gpuImage.setFilter(blurFilter);

        // Đặt ảnh nguồn (Bitmap)
        gpuImage.setImage(srcBitmap);

        // Lấy Bitmap đã được xử lý
        return gpuImage.getBitmapWithFilterApplied();
    }
    public Bitmap resizeBitmapWithGPUImage(Context context, Bitmap originalBitmap, int newWidth) {
        // Resize ảnh trước

        int originalWidth = originalBitmap.getWidth();
        int originalHeight = originalBitmap.getHeight();
        int newHeight = (int) ((double) newWidth / originalWidth * originalHeight);
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true);

        // Khởi tạo GPUImage
        GPUImage gpuImage = new GPUImage(context);
        gpuImage.setImage(resizedBitmap);

        // Áp dụng filter (hoặc giữ nguyên nếu không cần filter)
        gpuImage.setFilter(new GPUImageFilter());

        // Lấy ảnh đã xử lý
        return gpuImage.getBitmapWithFilterApplied();
    }


    public Bitmap applySharpening(Bitmap inputBitmap, float sharpness) {
        GPUImage gpuImage = new GPUImage(context);

        // Thiết lập ảnh đầu vào
        gpuImage.setImage(inputBitmap);

        // Tạo và áp dụng bộ lọc làm sắc
        GPUImageSharpenFilter sharpenFilter = new GPUImageSharpenFilter();
        sharpenFilter.setSharpness(sharpness); // Giá trị sắc nét (tùy chỉnh, thường từ -4.0 đến 4.0)
        gpuImage.setFilter(sharpenFilter);

        // Trả về ảnh đã áp dụng bộ lọc
        return gpuImage.getBitmapWithFilterApplied();
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
        Bitmap grayscaleBitmap = Bitmap.createBitmap(
                original.getWidth(),
                original.getHeight(),
                Bitmap.Config.ARGB_8888
        );
        Canvas canvas = new Canvas(grayscaleBitmap);
        Paint paint = new Paint();
        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.setSaturation(0); // Set saturation to 0
        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
        paint.setColorFilter(filter);
        canvas.drawBitmap(original, 0, 0, paint);
        return grayscaleBitmap;
    }
    public Bitmap convertToGrayscale2(Context context, Bitmap inputBitmap) {
        // Khởi tạo GPUImage
        GPUImage gpuImage = new GPUImage(context);

        // Đặt hình ảnh đầu vào
        gpuImage.setImage(inputBitmap);

        // Áp dụng bộ lọc Grayscale
        gpuImage.setFilter(new GPUImageGrayscaleFilter());

        // Trả về hình ảnh đã chuyển đổi
        return gpuImage.getBitmapWithFilterApplied();
    }
    public Bitmap blurImage(Bitmap inputBitmap, Context context, float radius) {
        // Tạo RenderScript
        RenderScript rs = RenderScript.create(context);

        // Input Allocation
        Allocation input = Allocation.createFromBitmap(rs, inputBitmap);

        // Output Allocation
        Allocation output = Allocation.createTyped(rs, input.getType());

        // Tạo ScriptIntrinsicBlur
        ScriptIntrinsicBlur blurScript = ScriptIntrinsicBlur.create(rs, input.getElement());
        blurScript.setRadius(radius); // Đặt bán kính làm mờ (từ 0.1 đến 25)
        blurScript.setInput(input);
        blurScript.forEach(output);

        // Sao chép kết quả sang Bitmap
        output.copyTo(inputBitmap);

        // Giải phóng tài nguyên
        input.destroy();
        output.destroy();
        blurScript.destroy();
        rs.destroy();

        return inputBitmap;
    }
    public Bitmap applyGaussianBlur(Bitmap src) {
        RenderScript rs = RenderScript.create(context);
        Allocation input = Allocation.createFromBitmap(rs, src);
        Allocation output = Allocation.createTyped(rs, input.getType());
        ScriptIntrinsicBlur blur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
        blur.setRadius(10); // Adjust blur radius
        blur.setInput(input);
        blur.forEach(output);
        output.copyTo(src);
        rs.destroy();
        return src;
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
    public Bitmap flipBitmap(Bitmap bitmap) {
        // Tạo một Matrix để lật ảnh
        Matrix matrix = new Matrix();
        matrix.preScale(-1.0f, 1.0f); // Lật ảnh theo chiều ngang

        // Tạo Bitmap mới sau khi lật
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    public Bitmap applySharpening2(Bitmap src,float degree) {

float degreemid=degree*4+1;
        float[] sharpenKernel = {
                0,  -degree,  0,
                -degree,  degreemid, -degree,
                0,  -degree,  0
        };
        try {
            // Tạo RenderScript và Allocation
            RenderScript rs = RenderScript.create(context);
            Allocation input = Allocation.createFromBitmap(rs, src);
            Allocation output = Allocation.createTyped(rs, input.getType());

            // Tạo ScriptIntrinsic convolve3x3 với kernel sharpening
            ScriptIntrinsicConvolve3x3 convolveScript = ScriptIntrinsicConvolve3x3.create(rs, Element.U8_4(rs));
            convolveScript.setCoefficients(sharpenKernel); // Áp dụng kernel vào ScriptIntrinsicConvolve3x3

            // Áp dụng sharpening
            convolveScript.setInput(input);
            convolveScript.forEach(output);
            output.copyTo(src); // Lưu ảnh đã xử lý lại vào ảnh nguồn

            // Dọn dẹp RenderScript
            rs.destroy();

        } catch (Exception e) {
            // Bắt lỗi nếu có
            e.printStackTrace();
        }

        return src;
    }

    public void clearCache() {
            File cacheDir = context.getCacheDir(); // Lấy thư mục cache
        if (cacheDir != null && cacheDir.isDirectory()) {
            // Lấy danh sách các tệp trong thư mục cache và xóa từng tệp
            for (File file : Objects.requireNonNull(cacheDir.listFiles())) {
                if (file.isFile()) {
                    file.delete();
                }
            }
        }
    }


    public Bitmap transform(Bitmap toTransform) {
        // Tạo một màu sắc để chuyển thành ảnh đen trắng
        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.setSaturation(0); // Chuyển sang trắng đen (saturation = 0)

        // Áp dụng bộ lọc màu sắc vào ảnh
        Paint paint = new Paint();
        paint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));

        // Tạo ảnh mới với bộ lọc đã áp dụng
        Bitmap result = Bitmap.createBitmap(toTransform.getWidth(), toTransform.getHeight(), toTransform.getConfig());
        Canvas canvas = new Canvas(result);
        canvas.drawBitmap(toTransform, 0, 0, paint);

        return result;
    }
    public Bitmap viewToBitmap(View view) {
        // Đặt kích thước cho View (nếu cần)
        view.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());

        // Tạo Bitmap từ View
        Bitmap bitmap = Bitmap.createBitmap(view.getMeasuredWidth(), view.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);
        return bitmap;
    }
    public Bitmap drawableToBitmap(Drawable drawable) {
        // Kiểm tra nếu drawable là BitmapDrawable
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }

        // Nếu không phải BitmapDrawable, bạn có thể chuyển đổi thông qua Canvas
        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

}
