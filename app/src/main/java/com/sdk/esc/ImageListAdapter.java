package com.sdk.esc;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.List;
public class ImageListAdapter extends RecyclerView.Adapter<ImageListAdapter.ImageViewHolder> {
    private List<String> imageList;
    private final Context context;

    private final OnImageDeleteListener onImageDeleteListener;

    public ImageListAdapter(Context context, List<String> imageList, OnImageDeleteListener onImageDeleteListener) {
        this.context = context;
        this.imageList = imageList;
        this.onImageDeleteListener = onImageDeleteListener;
    }
    public interface OnImageDeleteListener {
        void onImageDeleted(int position);
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_image, parent, false);
        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        SharedPreferences preferences2 = context.getSharedPreferences("FrameImage", Context.MODE_PRIVATE);
        String jsonString2 = preferences2.getString("bitmap_list", "[]");
        Gson gson2 = new Gson();
        imageList = gson2.fromJson(jsonString2, new TypeToken<List<String>>() {}.getType());
        String encodedBitmap = imageList.get(position);

        // Decode Base64 string to Bitmap
        byte[] decodedBytes = Base64.decode(encodedBitmap, Base64.DEFAULT);
        Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);

        // Set the image in ImageView
        holder.imageView.setImageBitmap(bitmap);

        // Handle "Delete" button click
        holder.buttonDelete.setOnClickListener(v -> {
            if (position < imageList.size() && position >= 0) {
                // Xóa hình ảnh khỏi danh sách
                imageList.remove(position);

                // Lưu danh sách cập nhật vào SharedPreferences
                saveImageListToPreferences();

                // Cập nhật RecyclerView
                notifyItemRemoved(position);

                // Gọi callback nếu cần
                if (onImageDeleteListener != null) {
                    onImageDeleteListener.onImageDeleted(position);
                }
            }
        });


    }


    @Override
    public int getItemCount() {
        return imageList.size();
    }

    // Helper method to save the updated list back to SharedPreferences
    private void saveImageListToPreferences() {
        SharedPreferences preferences = context.getSharedPreferences("FrameImage", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        Gson gson = new Gson();
        String json = gson.toJson(imageList);
        editor.putString("bitmap_list", json);
        editor.apply();
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
