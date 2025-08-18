package com.example.aquatrack.util

import android.app.Activity
import android.os.Build
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

object WindowManager {
    fun setUpEdgeToEdge(activity: Activity) {
        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)

        // Get the controller for window insets
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)

        // Handle system bars differently based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+) - Use new APIs
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            // Android 10 and below - Use legacy APIs but maintain compatibility
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_TOUCH
        }

        // Set up initial system bar visibility
        controller.show(WindowInsetsCompat.Type.systemBars())
    }

    fun setStatusBarColor(activity: Activity, color: Int, isLight: Boolean) {
        activity.window.statusBarColor = color
        WindowInsetsControllerCompat(activity.window, activity.window.decorView)
            .isAppearanceLightStatusBars = isLight
    }

    fun setNavigationBarColor(activity: Activity, color: Int, isLight: Boolean) {
        activity.window.navigationBarColor = color
        WindowInsetsControllerCompat(activity.window, activity.window.decorView)
            .isAppearanceLightNavigationBars = isLight
    }
}
