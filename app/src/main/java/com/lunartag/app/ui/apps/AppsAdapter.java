package com.lunartag.app.ui.apps;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.lunartag.app.R;

import java.util.List;

public class AppsAdapter extends RecyclerView.Adapter<AppsAdapter.AppViewHolder> {

    private final Context context;
    private final PackageManager packageManager;
    private List<ResolveInfo> appList;
    private String currentSelectedLabel; // The label currently saved in preferences
    private final OnAppSelectedListener listener;

    public interface OnAppSelectedListener {
        void onAppSelected(String appLabel, String packageName);
    }

    public AppsAdapter(Context context, List<ResolveInfo> appList, String currentSelectedLabel, OnAppSelectedListener listener) {
        this.context = context;
        this.packageManager = context.getPackageManager();
        this.appList = appList;
        this.currentSelectedLabel = currentSelectedLabel;
        this.listener = listener;
    }

    public void updateData(List<ResolveInfo> newList, String selectedLabel) {
        this.appList = newList;
        this.currentSelectedLabel = selectedLabel;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_app_list, parent, false);
        return new AppViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        ResolveInfo info = appList.get(position);

        // 1. Load App Label (Name)
        // This is critical. Clones usually have names like "WhatsApp (Dual)".
        // The Accessibility Service uses THIS text to find the app.
        CharSequence labelSeq = info.loadLabel(packageManager);
        String label = labelSeq != null ? labelSeq.toString() : info.activityInfo.packageName;

        holder.textAppName.setText(label);

        // 2. Load App Icon
        Drawable icon = info.loadIcon(packageManager);
        holder.imageAppIcon.setImageDrawable(icon);

        // 3. Handle Selection State
        // We compare the names. If this row matches the saved name, show the checkmark.
        if (label.equals(currentSelectedLabel)) {
            holder.imageSelectedCheck.setVisibility(View.VISIBLE);
            holder.cardView.setStrokeWidth(4);
            holder.cardView.setStrokeColor(context.getResources().getColor(com.google.android.material.R.color.design_default_color_primary));
        } else {
            holder.imageSelectedCheck.setVisibility(View.GONE);
            holder.cardView.setStrokeWidth(0);
        }

        // 4. Handle Click
        holder.itemView.setOnClickListener(v -> {
            // Update local state for instant UI feedback
            currentSelectedLabel = label;
            notifyDataSetChanged();

            // Notify Fragment to save to Preferences
            if (listener != null) {
                listener.onAppSelected(label, info.activityInfo.packageName);
            }
        });
    }

    @Override
    public int getItemCount() {
        return appList.size();
    }

    static class AppViewHolder extends RecyclerView.ViewHolder {
        final ImageView imageAppIcon;
        final TextView textAppName;
        final ImageView imageSelectedCheck;
        final com.google.android.material.card.MaterialCardView cardView;

        AppViewHolder(@NonNull View itemView) {
            super(itemView);
            imageAppIcon = itemView.findViewById(R.id.image_app_icon);
            textAppName = itemView.findViewById(R.id.text_app_name);
            imageSelectedCheck = itemView.findViewById(R.id.image_selected_check);
            cardView = (com.google.android.material.card.MaterialCardView) itemView;
        }
    }
    }
