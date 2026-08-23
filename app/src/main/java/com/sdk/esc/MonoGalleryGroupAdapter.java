package com.sdk.esc;

import com.mphoto.mono.R;

import android.content.ContentResolver;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class MonoGalleryGroupAdapter extends RecyclerView.Adapter<MonoGalleryGroupAdapter.Holder> {

    public static class Item {
        /** folderId trên server / hiển thị (30 ký tự, có mã máy). */
        public String folderId;
        /** folderId parse từ tên file local (có thể 24 ký tự, thiếu mã máy). */
        public String localFolderId;
        public Uri previewUri;
        public String viewUrl;
        public boolean synced;
        public boolean localSource;
    }

    public interface Listener {
        void onViewClick(@NonNull Item item);

        void onPrintClick(@NonNull Item item);

        void onSyncClick(@NonNull Item item);

        void onDeleteClick(@NonNull Item item);
    }

    private final List<Item> items = new ArrayList<>();
    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<Item> data) {
        items.clear();
        if (data != null) {
            items.addAll(data);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_mono_gallery_group, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Item item = items.get(position);
        h.textFolderId.setText(item.folderId);
        h.textSyncState.setText(item.synced ? "Đã đồng bộ" : "Chưa đồng bộ");
        h.textSyncState.setTextColor(item.synced ? 0xFF2E7D32 : 0xFFD84315);

        if (item.previewUri != null) {
            Glide.with(h.imagePreview).load(item.previewUri).into(h.imagePreview);
        } else {
            h.imagePreview.setImageResource(android.R.color.transparent);
        }

        h.btnSync.setVisibility(item.localSource && !item.synced ? View.VISIBLE : View.GONE);
        h.btnSync.setEnabled(!item.synced);
        h.btnSync.setText("Đồng bộ");

        h.btnDelete.setVisibility(item.localSource ? View.VISIBLE : View.GONE);

        h.btnView.setOnClickListener(v -> {
            if (listener != null) listener.onViewClick(item);
        });
        h.btnPrint.setOnClickListener(v -> {
            if (listener != null) listener.onPrintClick(item);
        });
        h.btnSync.setOnClickListener(v -> {
            if (listener != null) listener.onSyncClick(item);
        });
        h.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDeleteClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView imagePreview;
        final TextView textFolderId;
        final TextView textSyncState;
        final Button btnView;
        final Button btnPrint;
        final Button btnSync;
        final Button btnDelete;

        Holder(@NonNull View itemView) {
            super(itemView);
            imagePreview = itemView.findViewById(R.id.imageMonoGroupPreview);
            textFolderId = itemView.findViewById(R.id.textMonoGroupFolderId);
            textSyncState = itemView.findViewById(R.id.textMonoGroupSyncState);
            btnView = itemView.findViewById(R.id.btnMonoGroupView);
            btnPrint = itemView.findViewById(R.id.btnMonoGroupPrint);
            btnSync = itemView.findViewById(R.id.btnMonoGroupSync);
            btnDelete = itemView.findViewById(R.id.btnMonoGroupDelete);
        }
    }
}
