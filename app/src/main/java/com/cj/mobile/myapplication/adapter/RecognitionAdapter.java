package com.cj.mobile.myapplication.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.cj.mobile.myapplication.R;
import com.cj.mobile.myapplication.model.Recognition;

public class RecognitionAdapter extends ListAdapter<Recognition, RecognitionAdapter.RecognitionViewHolder> {

    private final Context ctx;

    public RecognitionAdapter(Context ctx) {
        super(new DiffUtil.ItemCallback<Recognition>() {
            @Override
            public boolean areItemsTheSame(@NonNull Recognition oldItem, @NonNull Recognition newItem) {
                // 比较条目是否是同一个，用于优化
                return oldItem.getTitle().equals(newItem.getTitle());
            }

            @Override
            public boolean areContentsTheSame(@NonNull Recognition oldItem, @NonNull Recognition newItem) {
                // 比较内容是否相同
                return oldItem.getConfidence() == newItem.getConfidence();
            }
        });
        this.ctx = ctx;
    }

    @NonNull
    @Override
    public RecognitionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.recognition_list_item, parent, false);
        return new RecognitionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecognitionViewHolder holder, int position) {
        holder.txviLabel.setText(getItem(position).getTitle());

        String confidence = String.format("%.1f%%", getItem(position).getConfidence() * 100.0f);
        holder.txviConfidence.setText(confidence);
    }

    static class RecognitionViewHolder extends RecyclerView.ViewHolder {
        TextView txviLabel;
        TextView txviConfidence;

        public RecognitionViewHolder(@NonNull View view) {
            super(view);
            txviLabel = view.findViewById(R.id.txvi_recognition_lable);
            txviConfidence = view.findViewById(R.id.txvi_recognition_percentage);
        }
    }
}

