package com.phoneguard.pro.data.optimization

import android.Manifest
import android.app.AppOpsManager
import android.app.RecoverableSecurityException
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.provider.Settings
import com.phoneguard.pro.data.model.AppUsageItem
import com.phoneguard.pro.data.model.MediaFileItem
import com.phoneguard.pro.data.model.SystemTuning
import com.phoneguard.pro.util.dirSize

/**
 * Safe optimisation analysis. This class NEVER kills processes, never clears other apps' data,
 * and never claims to speed up the CPU. Everything it does is either read-only analysis,
 * cleaning this app's own cache, or deleting media the user explicitly selects (with the
 * system confirmation dialog on Android 11+).
 */
class OptimizationAnalyzer(private val context: Context) {
    private val pm: PackageManager = context.packageManager

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        } else mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasMediaPermission(): Boolean {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
        } else listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        return perms.any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }

    /** Launchable apps only (declared in <queries>), excluding this app. */
    @Suppress("DEPRECATION")
    private fun launchableApps(): Map<String, String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .associate { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filterKeys { it != context.packageName }
    }

    /**
     * Per-app usage (last ~24 h, aggregated from daily buckets) plus storage sizes.
     * Requires Usage Access. Foreground-service time is the best available proxy for background
     * battery drain: real per-app energy data requires the system-only BATTERY_STATS permission.
     */
    @Suppress("DEPRECATION")
    fun appUsage(includeStorage: Boolean = true): List<AppUsageItem> {
        if (!hasUsageAccess()) return emptyList()
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val day = usm.queryAndAggregateUsageStats(now - DAY_MS, now)
        val month = usm.queryAndAggregateUsageStats(now - 30 * DAY_MS, now)
        val ssm = context.getSystemService(StorageStatsManager::class.java)
        val user = Process.myUserHandle()

        return launchableApps().map { (pkg, label) ->
            val d = day[pkg]
            val m = month[pkg]
            val info = try { pm.getPackageInfo(pkg, 0) } catch (e: Exception) { null }
            val flags = info?.applicationInfo?.flags ?: 0
            val stats = if (includeStorage) {
                try { ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, pkg, user) } catch (e: Exception) { null }
            } else null
            AppUsageItem(
                packageName = pkg,
                label = label,
                isSystem = flags and ApplicationInfo.FLAG_SYSTEM != 0,
                firstInstallTime = info?.firstInstallTime ?: 0L,
                foregroundMs = d?.totalTimeInForeground ?: 0L,
                foregroundServiceMs = d?.totalTimeForegroundServiceUsed ?: 0L,
                lastUsed = m?.lastTimeUsed ?: 0L,
                appBytes = stats?.appBytes,
                dataBytes = stats?.dataBytes,
                cacheBytes = stats?.cacheBytes
            )
        }
    }

    /** Large media files via MediaStore. Requires media read permission. */
    fun largeMediaFiles(minBytes: Long = 50L * 1024 * 1024, limit: Int = 40): List<MediaFileItem> {
        if (!hasMediaPermission()) return emptyList()
        val filesUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MEDIA_TYPE
        )
        val selection = "${MediaStore.Files.FileColumns.SIZE} >= ? AND ${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (1,2,3)"
        val out = mutableListOf<MediaFileItem>()
        try {
            context.contentResolver.query(
                filesUri, projection, selection, arrayOf(minBytes.toString()),
                "${MediaStore.Files.FileColumns.SIZE} DESC"
            )?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val id = c.getLong(0)
                    val mediaType = c.getInt(5)
                    val base = when (mediaType) {
                        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                        else -> filesUri
                    }
                    out += MediaFileItem(
                        id = id,
                        uri = ContentUris.withAppendedId(base, id),
                        name = c.getString(1) ?: "Unnamed",
                        sizeBytes = c.getLong(2),
                        mimeType = c.getString(3),
                        dateModifiedSec = c.getLong(4)
                    )
                }
            }
        } catch (e: Exception) {
            // Some OEM MediaStore implementations reject the query; return what we have.
        }
        return out
    }

    /**
     * Android 11+: returns the system confirmation dialog (user must approve deletion).
     * Android 10: deletes directly (legacy storage) or returns the recoverable-permission dialog.
     */
    fun deleteMedia(uris: List<Uri>): IntentSender? {
        if (uris.isEmpty()) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
        }
        for (u in uris) {
            try {
                context.contentResolver.delete(u, null, null)
            } catch (e: RecoverableSecurityException) {
                return e.userAction.actionIntent.intentSender
            } catch (e: Exception) {
                // skip files that cannot be deleted
            }
        }
        return null
    }

    fun ownCacheBytes(): Long = dirSize(context.cacheDir) + dirSize(context.externalCacheDir)

    /** Clears ONLY this app's cache. Since Android 6, apps cannot clear other apps' caches. */
    fun clearOwnCache(): Long {
        val before = ownCacheBytes()
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        context.externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() }
        return (before - ownCacheBytes()).coerceAtLeast(0)
    }

    fun systemTuning(): SystemTuning {
        val cr = context.contentResolver
        val power = context.getSystemService(PowerManager::class.java)
        return SystemTuning(
            animatorScale = Settings.Global.getFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, 1f),
            transitionScale = Settings.Global.getFloat(cr, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f),
            windowScale = Settings.Global.getFloat(cr, Settings.Global.WINDOW_ANIMATION_SCALE, 1f),
            powerSaveMode = power.isPowerSaveMode
        )
    }

    companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
