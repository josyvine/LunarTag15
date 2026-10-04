package com.lunartag.app.ui.admin;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.lunartag.app.databinding.FragmentAdminConsoleBinding;

public class AdminConsoleFragment extends Fragment {

    private static final String PREFS_NAME = "LunarTagFeatureToggles";
    private static final String KEY_CUSTOM_TIMESTAMP_ENABLED = "customTimestampEnabled";

    private FragmentAdminConsoleBinding binding;
    private SharedPreferences featureTogglePrefs;
    private boolean isFeatureEnabled = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        featureTogglePrefs = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // Check if the feature is enabled. The default is false.
        isFeatureEnabled = featureTogglePrefs.getBoolean(KEY_CUSTOM_TIMESTAMP_ENABLED, false);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAdminConsoleBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // This is the critical UI logic based on the remote toggle.
        if (isFeatureEnabled) {
            // If the feature is enabled, make the admin console UI visible.
            view.setVisibility(View.VISIBLE);
            loadAuditLogs();
        } else {
            // If the feature is disabled, hide this entire UI.
            view.setVisibility(View.GONE);
        }
    }

    private void loadAuditLogs() {
        // Logic to:
        // 1. In a background thread, query the local Room database to get a list of all AuditLog records.
        // 2. Create and set an adapter for the RecyclerView to display the logs.
        // 3. Handle the UI states (show the list or the "No logs" message).
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}