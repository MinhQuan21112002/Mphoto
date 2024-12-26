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

import java.util.List;
public class ImageListAdapter extends RecyclerView.Adapter<ImageListAdapter.ImageViewHolder> {
    private final List<String> imageList;
    private final Context context;

    public ImageListAdapter(Context context, List<String> imageList) {
        this.context = context;
        this.imageList = imageList;
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_image, parent, false);
        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        String encodedBitmap = imageList.get(position);

        // Decode Base64 string to Bitmap
        byte[] decodedBytes = Base64.decode(encodedBitmap, Base64.DEFAULT);
        Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);

        // Set the image in ImageView
        holder.imageView.setImageBitmap(bitmap);

        // Handle "Delete" button click
        holder.buttonDelete.setOnClickListener(v -> {
            if (position < imageList.size() && position >= 0) {
                // Remove the image from the list
                imageList.remove(position);
                notifyItemRemoved(position); // Notify RecyclerView item removed

                // Save updated list back to SharedPreferences
                saveImageListToPreferences();
            } else {
                // If position is not valid, attempt to remove item at position - 1
                if (position - 1 >= 0 && position - 1 < imageList.size()) {
                    imageList.remove(position - 1);
                    notifyItemRemoved(position - 1); // Notify RecyclerView item removed
                    saveImageListToPreferences(); // Save updated list back
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
