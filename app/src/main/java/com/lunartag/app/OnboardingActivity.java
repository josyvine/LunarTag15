package com.lunartag.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.lunartag.app.databinding.ActivityOnboardingBinding;

/**
 * This is the first screen the user sees.
 * It explains the app's purpose and necessary permissions. Once the user continues,
 * it saves a flag to prevent this screen from showing again.
 */
public class OnboardingActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "LunarTagPrefs";
    private static final String KEY_ONBOARDING_COMPLETE = "onboarding_complete";

    private ActivityOnboardingBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check if onboarding has already been completed on a previous launch.
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean hasCompleted = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false);

        if (hasCompleted) {
            // If completed, skip this screen and go directly to the main app.
            navigateToMainActivity();
            return; // Important to return here to stop further execution.
        }

        // If not completed, set up the view for the user.
        binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Set up the click listener for the "Continue" button.
        binding.buttonContinue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // When the user continues, save this choice to SharedPreferences.
                SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
                editor.putBoolean(KEY_ONBOARDING_COMPLETE, true);
                editor.apply();

                // Then, proceed to the main app.
                navigateToMainActivity();
            }
        });
    }

    /**
     * Helper method to create an Intent for MainActivity, start it,
     * and finish the current OnboardingActivity so the user cannot navigate back to it.
     */
    private void navigateToMainActivity() {
        Intent intent = new Intent(OnboardingActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}