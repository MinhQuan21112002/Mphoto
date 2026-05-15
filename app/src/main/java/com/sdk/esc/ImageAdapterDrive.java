package com.sdk.esc;

import com.mphoto.mono.R;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ImageAdapterDrive extends RecyclerView.Adapter<ImageAdapterDrive.ImageViewHolder> {
    private final List<String> imageUrls;
    private final Context context;
    private final Set<String> selectedImages = new HashSet<>(); // Lưu ảnh đã chọn

    public ImageAdapterDrive(Context context, List<String> imageUrls) {
        this.context = context;
        this.imageUrls = imageUrls;
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_image_drive, parent, false);
        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        String imageUrl = imageUrls.get(position);

        // Load ảnh bằng Glide
        Glide.with(context)
                .load(imageUrl)
                .placeholder(R.drawable.holder) // Hình mặc định khi tải
                .into(holder.imageView);

        // Kiểm tra xem ảnh đã được chọn chưa
        holder.checkBox.setChecked(selectedImages.contains(imageUrl));

        // Xử lý sự kiện khi nhấn vào ảnh
        holder.itemView.setOnClickListener(v -> {
            if (selectedImages.contains(imageUrl)) {
                selectedImages.remove(imageUrl);
                holder.checkBox.setChecked(false);
            } else {
                selectedImages.add(imageUrl);
                holder.checkBox.setChecked(true);
            }
        });

        // Xử lý khi nhấn vào checkbox
        holder.checkBox.setOnClickListener(v -> {
            if (holder.checkBox.isChecked()) {
                selectedImages.add(imageUrl);
            } else {
                selectedImages.remove(imageUrl);
            }
        });
    }

    @Override
    public int getItemCount() {
        return imageUrls.size();
    }

    // Trả về danh sách ảnh đã chọn
    public List<String> getSelectedImages() {
        return List.copyOf(selectedImages);
    }

    public static class ImageViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        CheckBox checkBox;

        public ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imageView);
            checkBox = itemView.findViewById(R.id.checkBox);
        }
    }
}
