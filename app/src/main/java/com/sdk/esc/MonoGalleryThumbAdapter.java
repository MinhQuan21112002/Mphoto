package com.sdk.esc;

import com.mphoto.mono.R;

import android.content.ContentResolver;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.signature.ObjectKey;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Lưới ảnh trong thư mục M-Photo Mono (URI từ MediaStore hoặc file).
 */
public class MonoGalleryThumbAdapter extends RecyclerView.Adapter<MonoGalleryThumbAdapter.Holder> {

    public interface OnDeleteClickListener {
        void onDeleteClick(int position, @NonNull Uri uri);
    }

    private final List<Uri> items = new ArrayList<>();
    @Nullable
    private OnDeleteClickListener onDeleteListener;

    public void setOnDeleteListener(@Nullable OnDeleteClickListener l) {
        onDeleteListener = l;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_mono_gallery_cell, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Uri u = items.get(position);
        Glide.with(h.image).clear(h.image);
        RequestOptions ro = new RequestOptions()
            .fitCenter()
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true);
        if (ContentResolver.SCHEME_FILE.equals(u.getScheme())) {
            String p = u.getPath();
            if (p != null) {
                File f = new File(p);
                if (f.isFile()) {
                    ro = ro.signature(new ObjectKey(f.getPath() + "@" + f.length() + "@" + f.lastModified()));
                }
            }
        } else {
            ro = ro.signature(new ObjectKey(String.valueOf(u)));
        }
        Glide.with(h.image).load(u).apply(ro).into(h.image);
        h.btnDelete.setOnClickListener(v -> {
            int p = h.getAdapterPosition();
            if (p != RecyclerView.NO_POSITION && onDeleteListener != null) {
                onDeleteListener.onDeleteClick(p, items.get(p));
            }
        });
    }

    @Override
    public void onViewRecycled(@NonNull Holder h) {
        super.onViewRecycled(h);
        Glide.with(h.image).clear(h.image);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void setInitial(List<Uri> page) {
        items.clear();
        if (page != null) {
            items.addAll(page);
        }
        notifyDataSetChanged();
    }

    public void appendPage(List<Uri> page) {
        if (page == null || page.isEmpty()) {
            return;
        }
        int before = items.size();
        items.addAll(page);
        notifyItemRangeInserted(before, page.size());
    }

    public int getDataSize() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView image;
        final ImageButton btnDelete;

        Holder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.imageCell);
            btnDelete = itemView.findViewById(R.id.buttonCellDelete);
        }
    }
}
