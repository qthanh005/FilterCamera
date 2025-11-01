package com.example.cameraapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.List;

public class FilterAdapter extends RecyclerView.Adapter<FilterAdapter.FilterViewHolder> {

    private Context context;
    private List<FilterItem> filters;
    private OnFilterClickListener listener;

    public interface OnFilterClickListener {
        void onFilterClick(FilterItem filter);
    }

    public FilterAdapter(Context context, List<FilterItem> filters, OnFilterClickListener listener) {
        this.context = context;
        this.filters = filters;
        this.listener = listener;
    }

    @NonNull
    @Override
    public FilterViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_filter, parent, false);
        return new FilterViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FilterViewHolder holder, int position) {
        FilterItem item = filters.get(position);
        Glide.with(context).load(item.previewBitmap).circleCrop().into(holder.imgFilter);
        holder.tvFilterName.setText(item.name);
        holder.imgFilter.setOnClickListener(v -> listener.onFilterClick(item));
        
        // Animation cho từng filter item - hiển thị tuần tự
        setAnimation(holder.itemView, position);
    }
    
    private void setAnimation(View view, int position) {
        // Reset animation trước
        view.clearAnimation();
        
        // Tạo animation với delay dựa trên position
        Animation animation = AnimationUtils.loadAnimation(context, R.anim.item_animation_fade_in);
        
        // Delay cho từng item: 50ms * position để tạo hiệu ứng tuần tự
        animation.setStartOffset(position * 50);
        
        view.startAnimation(animation);
    }
    
    @Override
    public void onViewDetachedFromWindow(@NonNull FilterViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
        // Clear animation khi view bị detach để tránh memory leak
        holder.itemView.clearAnimation();
    }

    @Override
    public int getItemCount() {
        return filters.size();
    }

    static class FilterViewHolder extends RecyclerView.ViewHolder {
        ImageView imgFilter;
        TextView tvFilterName;

        public FilterViewHolder(@NonNull View itemView) {
            super(itemView);
            imgFilter = itemView.findViewById(R.id.imgFilterItem);
            tvFilterName = itemView.findViewById(R.id.tvFilterName);
        }
    }
}
