package com.example.cameraapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class IntroPagerAdapter extends RecyclerView.Adapter<IntroPagerAdapter.IntroViewHolder> {
    
    private IntroActivity activity;
    private int[] images = {
        R.drawable.background_slider1,
        R.drawable.background_slider2
    };
    
    private String[] titles = {
        "Chụp ảnh đẹp",
        "Tạo sticker vui"
    };
    
    private String[] descriptions = {
        "Khám phá thế giới nhiếp ảnh với camera chất lượng cao",
        "Thêm sticker và filter để ảnh của bạn trở nên độc đáo"
    };
    
    public IntroPagerAdapter(IntroActivity activity) {
        this.activity = activity;
    }
    
    @NonNull
    @Override
    public IntroViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_intro, parent, false);
        return new IntroViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull IntroViewHolder holder, int position) {
        holder.imageView.setImageResource(images[position]);
        holder.titleText.setText(titles[position]);
        holder.descriptionText.setText(descriptions[position]);
    }
    
    @Override
    public int getItemCount() {
        return images.length;
    }
    
    static class IntroViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView titleText;
        TextView descriptionText;
        
        IntroViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imgIntro);
            titleText = itemView.findViewById(R.id.tvTitle);
            descriptionText = itemView.findViewById(R.id.tvDescription);
        }
    }
}

