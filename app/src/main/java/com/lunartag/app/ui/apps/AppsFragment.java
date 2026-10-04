package com.lunartag.app.ui.apps;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.lunartag.app.R;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Apps Fragment.
 * Scans the device for apps that handle Intent.ACTION_SEND (Images).
 * Allows the user to select a specific Target (including Clones) for the Robot.
 */
public class AppsFragment extends Fragment {

    // Must match LunarTagAccessibilityService constants
    private static final String PREFS_ACCESSIBILITY = "LunarTagAccessPrefs";
    private static final String KEY_TARGET_APP_LABEL = "target_app_label";
    private static final String KEY_TARGET_APP_PKG = "target_app_package";

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView textCurrentTarget;
    private AppsAdapter adapter;
    private ExecutorService executorService;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_apps, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recycler_view_apps);
        progressBar = view.findViewById(R.id.progress_bar_apps);
        textCurrentTarget = view.findViewById(R.id.text_current_target_app);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        executorService = Executors.newSingleThreadExecutor();

        // 1. Load Saved Preference
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_ACCESSIBILITY, Context.MODE_PRIVATE);
        String savedLabel = prefs.getString(KEY_TARGET_APP_LABEL, "None Selected");
        textCurrentTarget.setText(savedLabel);

        // 2. Initialize Adapter
        adapter = new AppsAdapter(getContext(), Collections.emptyList(), savedLabel, new AppsAdapter.OnAppSelectedListener() {
            @Override
            public void onAppSelected(String appLabel, String packageName) {
                saveSelection(appLabel, packageName);
            }
        });
        recyclerView.setAdapter(adapter);

        // 3. Start Scanning Apps
        loadInstalledApps(savedLabel);
    }

    private void loadInstalledApps(String currentSelection) {
        progressBar.setVisibility(View.VISIBLE);

        executorService.execute(() -> {
            PackageManager pm = requireContext().getPackageManager();

            // Create an Intent that matches what we do when sharing a photo
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/*");

            // Query ALL apps that can handle this intent
            // This automatically includes Clones/Dual Apps because they register for the same intent
            List<ResolveInfo> resolveInfos = pm.queryIntentActivities(shareIntent, 0);

            // Sort alphabetically by App Name (Label)
            Collections.sort(resolveInfos, new Comparator<ResolveInfo>() {
                @Override
                public int compare(ResolveInfo a, ResolveInfo b) {
                    String labelA = a.loadLabel(pm).toString();
                    String labelB = b.loadLabel(pm).toString();
                    return labelA.compareToIgnoreCase(labelB);
                }
            });

            // Update UI
            new Handler(Looper.getMainLooper()).post(() -> {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                if (adapter != null) {
                    adapter.updateData(resolveInfos, currentSelection);
                }
            });
        });
    }

    private void saveSelection(String label, String packageName) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_ACCESSIBILITY, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_TARGET_APP_LABEL, label)
                .putString(KEY_TARGET_APP_PKG, packageName)
                .apply();

        textCurrentTarget.setText(label);
        Toast.makeText(getContext(), "Target Set: " + label, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}