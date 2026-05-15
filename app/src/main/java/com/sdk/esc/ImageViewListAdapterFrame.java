package com.sdk.esc;

import com.mphoto.mono.R;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;

import java.util.List;

public class ImageViewListAdapterFrame extends RecyclerView.Adapter<ImageViewListAdapterFrame.ImageViewHolder> {
    private List<String> imageList;
    private int visibleCount;
    private final Context context;
    private final OnImageDeleteListener onImageDeleteListener;
    private final OnImageClickListener onImageClickListener;

    public ImageViewListAdapterFrame(Context context, List<String> imageList,
                                     OnImageDeleteListener onImageDeleteListener,
                                     OnImageClickListener onImageClickListener) {
        this.context = context;
        this.imageList = imageList;
        this.visibleCount = imageList != null ? imageList.size() : 0;
        this.onImageDeleteListener = onImageDeleteListener;
        this.onImageClickListener = onImageClickListener;
    }

    public void setVisibleCount(int count) {
        if (imageList == null) {
            visibleCount = 0;
        } else {
            visibleCount = Math.max(0, Math.min(count, imageList.size()));
        }
        notifyDataSetChanged();
    }

    public boolean increaseVisibleCount(int delta) {
        int old = visibleCount;
        setVisibleCount(old + Math.max(0, delta));
        return visibleCount > old;
    }

    public boolean canLoadMore() {
        return imageList != null && visibleCount < imageList.size();
    }

    public interface OnImageDeleteListener {
        void onImageDeleted(int position);
    }

    public interface OnImageClickListener {
        void onImageClicked(int position);
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_image2, parent, false);
        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        String entry = imageList.get(position);
        Bitmap bitmap = UserAssetFileStore.decodeListEntryToBitmap(context, entry);
        if (bitmap != null) {
            holder.imageView.setImageBitmap(bitmap);
        }

        // Xử lý sự kiện click
        holder.imageView.setOnClickListener(v -> {
            if (onImageClickListener != null) {
                onImageClickListener.onImageClicked(holder.getAdapterPosition());
            }
        });

        // Xóa ảnh
        holder.buttonDelete.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) {
                return;
            }
            Toast.makeText(context, "vị trí chọn xóa : " + pos, Toast.LENGTH_SHORT).show();

            if (pos < imageList.size() && pos >= 0) {
                UserAssetFileStore.deleteFileForListEntry(context, imageList.get(pos));
                imageList.remove(pos);
                if (visibleCount > imageList.size()) {
                    visibleCount = imageList.size();
                }

                // Lưu danh sách cập nhật vào SharedPreferences
                saveImageListToPreferences();

                // Cập nhật RecyclerView
                notifyItemRemoved(pos);

                // Gọi callback nếu cần
                if (onImageDeleteListener != null) {
                    onImageDeleteListener.onImageDeleted(pos);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return imageList == null ? 0 : Math.min(visibleCount, imageList.size());
    }

    private void saveImageListToPreferences() {
        SharedPreferences preferences = context.getSharedPreferences("FrameImage", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        Gson gson = new Gson();
        String json = gson.toJson(imageList);
        editor.putString("bitmap_list", json);
        editor.apply();
        MPhotoUserDataBackup.scheduleSave(context.getApplicationContext());
    }

    public static class ImageViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        Button buttonDelete;

        public ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imageViewItem);
            buttonDelete = itemView.findViewById(R.id.buttonDelete);
        }
    }
}
