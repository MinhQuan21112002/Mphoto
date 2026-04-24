package com.sdk.esc;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Khoảng cách đều giữa các ô trong lưới cột cố định.
 */
public class MonoGalleryGridGapDecoration extends RecyclerView.ItemDecoration {
    private final int spanCount;
    private final int gapPx;

    public MonoGalleryGridGapDecoration(int spanCount, int gapPx) {
        this.spanCount = spanCount;
        this.gapPx = gapPx;
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
            @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (pos < 0) {
            return;
        }
        int column = pos % spanCount;
        int row = pos / spanCount;
        outRect.left = column * gapPx / spanCount;
        outRect.right = gapPx - (column + 1) * gapPx / spanCount;
        if (row > 0) {
            outRect.top = gapPx;
        }
        outRect.bottom = gapPx;
    }
}
