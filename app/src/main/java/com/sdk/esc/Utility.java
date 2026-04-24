package com.sdk.esc;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;

/**
 * Created by NO on 2017/9/14.
 */

public class Utility {

    public static Bitmap Tobitmap(Bitmap bitmap, int width, int height) {
        // 입력 Bitmap 의 DPI 및 포맷 확인
        int dpi = bitmap.getDensity();
        bitmap.getConfig();
        Bitmap.Config config = bitmap.getConfig();

        // 새 Bitmap 생성
        Bitmap target = Bitmap.createBitmap(width, height, config);

        // DPI 설정 유지
        target.setDensity(dpi);

        // Canvas 초기화 및 그리기
        Canvas canvas = new Canvas(target);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setDither(true); // Dithering 활성화
        canvas.drawBitmap(bitmap, null, new Rect(0, 0, target.getWidth(), target.getHeight()), paint);

        // Bitmap 반환
        return target;
    }

    //width：目标宽度，pageWidthPoint：初始宽度，pageHeightPoint：初始高度
    public static int getHeight(int width, int pageWidthPoint, int pageHeightPoint) {
        double bili = width / (double) pageWidthPoint;
        return (int) (pageHeightPoint * bili);
    }

    /**
     * Ghép theo thứ tự in: ảnh chính (đã + khung) ở trên, ảnh phụ ở dưới, cùng chiều rộng
     * với cách scale khi in ({@code printWidth} pt, thường 576). Bitmap trả về cần {@link Bitmap#recycle()}.
     */
    public static Bitmap buildVerticalStackForPrintWidth(Bitmap main, Bitmap second, int printWidth) {
        if (main == null || main.isRecycled()) {
            return null;
        }
        int h1 = getHeight(printWidth, main.getWidth(), main.getHeight());
        Bitmap s1 = Tobitmap(main, printWidth, h1);
        Bitmap s2 = null;
        if (second != null && !second.isRecycled()) {
            int h2 = getHeight(printWidth, second.getWidth(), second.getHeight());
            s2 = Tobitmap(second, printWidth, h2);
        }
        int totalH = s1.getHeight() + (s2 != null ? s2.getHeight() : 0);
        Bitmap out = Bitmap.createBitmap(printWidth, totalH, Bitmap.Config.ARGB_8888);
        out.setDensity(main.getDensity());
        Canvas c = new Canvas(out);
        c.drawColor(Color.WHITE);
        c.drawBitmap(s1, 0, 0, null);
        if (s2 != null) {
            c.drawBitmap(s2, 0, s1.getHeight(), null);
        }
        s1.recycle();
        if (s2 != null) {
            s2.recycle();
        }
        return out;
    }

    public static Bitmap Tobitmap90(Bitmap bitmap) {
        Matrix matrix = new Matrix();
        // 设置旋转角度
        matrix.setRotate(90);
        // 重新绘制Bitmap
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        return bitmap;
    }

}



