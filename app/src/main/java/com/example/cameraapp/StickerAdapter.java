package com.example.cameraapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class StickerAdapter extends RecyclerView.Adapter<StickerAdapter.StickerViewHolder> {

    private Context context;
    private List<Integer> stickerResIds;
    private OnStickerClickListener listener;

    public interface OnStickerClickListener {
        void onStickerClick(int stickerResId);
    }

    public StickerAdapter(Context context, List<Integer> stickerResIds, OnStickerClickListener listener) {
        this.context = context;
        this.stickerResIds = stickerResIds;
        this.listener = listener;
    }

    @NonNull
    @Override
    public StickerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_sticker, parent, false);
        return new StickerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StickerViewHolder holder, int position) {
        int stickerResId = stickerResIds.get(position);
        holder.imgSticker.setImageResource(stickerResId);
        
        holder.itemView.setOnClickListener(v -> {
            Animation selectAnim = AnimationUtils.loadAnimation(context, R.anim.filter_select_light);
            v.startAnimation(selectAnim);
            listener.onStickerClick(stickerResId);
        });
    }

    @Override
    public int getItemCount() {
        return stickerResIds.size();
    }

    static class StickerViewHolder extends RecyclerView.ViewHolder {
        ImageView imgSticker;

        public StickerViewHolder(@NonNull View itemView) {
            super(itemView);
            imgSticker = itemView.findViewById(R.id.imgStickerItem);
        }
    }
}

