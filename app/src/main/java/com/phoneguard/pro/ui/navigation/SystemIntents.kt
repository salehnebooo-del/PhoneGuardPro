package com.phoneguard.pro.ui.navigation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.storage.StorageManager
import android.provider.Settings
import android.widget.Toast
import com.phoneguard.pro.data.model.RecommendationAction

/** All system screens the app can send the user to. Every launch has a fallback. */
object SystemIntents {

    fun open(context: Context, action: RecommendationAction) {
        when (action) {
            RecommendationAction.NONE -> Unit
            RecommendationAction.OPEN_STORAGE_MANAGER ->
                launch(context, Intent(StorageManager.ACTION_MANAGE_STORAGE), Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
            RecommendationAction.OPEN_USAGE_ACCESS -> launch(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            RecommendationAction.OPEN_BATTERY_SAVER ->
                launch(context, Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS), Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
            RecommendationAction.OPEN_DEVELOPER_OPTIONS -> {
                val devEnabled = Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
                if (devEnabled) {
                    launch(context, Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                } else {
                    Toast.makeText(context, "Enable Developer options first: tap 'Build number' 7 times in About phone.", Toast.LENGTH_LONG).show()
                    launch(context, Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
                }
            }
            RecommendationAction.OPEN_BATTERY_USAGE ->
                launch(context, Intent(Intent.ACTION_POWER_USAGE_SUMMARY), Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
            RecommendationAction.OPEN_ABOUT_PHONE ->
                launch(context, Intent(Settings.ACTION_DEVICE_INFO_SETTINGS), Intent(Settings.ACTION_SETTINGS))
        }
    }

    /** App info page, where the user can clear cache, force stop, restrict battery or uninstall. */
    fun openAppDetails(context: Context, packageName: String) {
        launch(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    fun openStorageManager(context: Context) = open(context, RecommendationAction.OPEN_STORAGE_MANAGER)
    fun openUsageAccess(context: Context) = open(context, RecommendationAction.OPEN_USAGE_ACCESS)
    fun openBatteryUsage(context: Context) = open(context, RecommendationAction.OPEN_BATTERY_USAGE)

    private fun launch(context: Context, primary: Intent, fallback: Intent? = null) {
        try {
            context.startActivity(primary)
        } catch (e: ActivityNotFoundException) {
            try {
                context.startActivity(fallback ?: Intent(Settings.ACTION_SETTINGS))
            } catch (e2: Exception) {
                Toast.makeText(context, "This settings screen is not available on your device.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            Toast.makeText(context, "This settings screen is not available on your device.", Toast.LENGTH_SHORT).show()
        }
    }
}
