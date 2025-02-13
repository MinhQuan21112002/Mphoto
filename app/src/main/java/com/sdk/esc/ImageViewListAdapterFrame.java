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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;

import java.util.List;

public class ImageViewListAdapterFrame extends RecyclerView.Adapter<ImageViewListAdapterFrame.ImageViewHolder> {
    private List<String> imageList;
    private final Context context;
    private final OnImageDeleteListener onImageDeleteListener;
    private final OnImageClickListener onImageClickListener;

    public ImageViewListAdapterFrame(Context context, List<String> imageList,
                                     OnImageDeleteListener onImageDeleteListener,
                                     OnImageClickListener onImageClickListener) {
        this.context = context;
        this.imageList = imageList;
        this.onImageDeleteListener = onImageDeleteListener;
        this.onImageClickListener = onImageClickListener;
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
        String encodedBitmap = imageList.get(position);
        byte[] decodedBytes = Base64.decode(encodedBitmap, Base64.DEFAULT);
        Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
        holder.imageView.setImageBitmap(bitmap);

        // Xử lý sự kiện click
        holder.imageView.setOnClickListener(v -> {
            if (onImageClickListener != null) {
                onImageClickListener.onImageClicked(position);
            }
        });

        // Xóa ảnh
        holder.buttonDelete.setOnClickListener(v -> {
            Toast.makeText(context, "vị trí chọn xóa : " +position, Toast.LENGTH_SHORT).show();

            int newPosition=position;
            if(imageList.size()==1)
            {
                newPosition=0;
            }
            if(position==imageList.size())
            {
                newPosition=position-1;
            }
            if (newPosition < imageList.size() && newPosition >= 0) {
                // Xóa hình ảnh khỏi danh sách
                imageList.remove(newPosition);

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
