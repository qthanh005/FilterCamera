package com.example.cameraapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
