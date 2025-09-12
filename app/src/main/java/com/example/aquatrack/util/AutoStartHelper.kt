    package com.example.aquatrack.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log

object AutoStartHelper {
    private const val TAG = "AutoStartHelper"

    fun promptAutoStartSettings(context: Context) {
        // Common OEM settings packages / components
        val intents = listOf(
            // MIUI
            Intent().setPackage("com.miui.securitycenter").setAction("miui.intent.action.OP_AUTO_START"),
            Intent("miui.intent.action.OP_AUTO_START"),
            // Huawei
            Intent().setComponent(android.content.ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
            Intent().setPackage("com.huawei.systemmanager"),
            // Xiaomi
            Intent().setComponent(android.content.ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            // Oppo
            Intent().setComponent(android.content.ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
            Intent().setComponent(android.content.ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
            // Vivo
            Intent().setComponent(android.content.ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
            Intent().setComponent(android.content.ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
            // Samsung
            Intent().setComponent(android.content.ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")),
            // Generic app settings as fallback
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        )

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.d(TAG, "Launched intent for auto-start settings: ${intent.action} ${intent.component}")
                return
            } catch (e: ActivityNotFoundException) {
                // try next
                Log.d(TAG, "Intent not available: ${intent.component} / ${intent.action}")
            } catch (t: Throwable) {
                Log.w(TAG, "Error launching auto-start intent: ${t.localizedMessage}")
            }
        }

        Log.d(TAG, "No OEM auto-start intent worked; fallback to app details settings.")
    }
}

