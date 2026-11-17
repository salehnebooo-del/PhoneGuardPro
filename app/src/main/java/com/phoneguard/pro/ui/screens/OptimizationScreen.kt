package com.phoneguard.pro.ui.screens

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PermMedia
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phoneguard.pro.data.model.AppUsageItem
import com.phoneguard.pro.data.model.Recommendation
import com.phoneguard.pro.data.model.RecommendationAction
import com.phoneguard.pro.data.model.Severity
import com.phoneguard.pro.ui.components.AppIcon
import com.phoneguard.pro.ui.components.GlassCard
import com.phoneguard.pro.ui.components.InfoRow
import com.phoneguard.pro.ui.components.NoteText
import com.phoneguard.pro.ui.components.PrimaryButton
import com.phoneguard.pro.ui.components.SecondaryButton
import com.phoneguard.pro.ui.components.SectionTitle
import com.phoneguard.pro.ui.components.UsageBar
import com.phoneguard.pro.ui.components.severityColor
import com.phoneguard.pro.ui.navigation.SystemIntents
import com.phoneguard.pro.ui.theme.PgCyan
import com.phoneguard.pro.ui.theme.PgSuccess
import com.phoneguard.pro.ui.theme.PgText
import com.phoneguard.pro.ui.theme.PgTextDim
import com.phoneguard.pro.ui.viewmodel.OptimizationViewModel
import com.phoneguard.pro.util.Fmt

@Composable
fun OptimizationScreen(vm: OptimizationViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var selected by remember { mutableStateOf(setOf<Long>()) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    val mediaPerms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
    } else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refresh() }
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        selected = emptySet()
        vm.onDeleteFinished(res.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(s.message) {
        s.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (s.loading && s.storage == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PgCyan) }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text("Optimization center", style = MaterialTheme.typography.headlineSmall, color = PgText)
                    NoteText("Safe, transparent actions only. Phone Guard Pro never kills background processes and never claims to make the CPU faster.")
                }

                if (!s.usageAccess) {
                    item {
                        GlassCard {
                            SectionTitle("Usage Access required", Icons.Rounded.Lock)
                            Text(
                                "Grant Usage Access to analyse which apps use the most time, run in the background and take the most space. Data stays on your device.",
                                color = PgTextDim, style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(10.dp))
                            PrimaryButton("Open Usage Access settings", { SystemIntents.openUsageAccess(context) }, Modifier.fillMaxWidth())
                        }
                    }
                }

                item {
                    GlassCard {
                        SectionTitle("Recommendations", Icons.Rounded.Speed)
                        if (s.recommendations.isEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.CheckCircle, null, tint = PgSuccess)
                                Spacer(Modifier.width(8.dp))
                                Text("Everything looks good.", color = PgText)
                            }
                        }
                        s.recommendations.forEach { RecommendationRow(it) { a -> SystemIntents.open(context, a) } }
                    }
                }

                s.storage?.let { st ->
                    item {
                        GlassCard {
                            SectionTitle("Storage cleanup", Icons.Rounded.Storage)
                            InfoRow("Used", "${Fmt.bytes(st.usedBytes)} of ${Fmt.bytes(st.totalBytes)}")
                            UsageBar(st.usedFraction)
                            Spacer(Modifier.height(10.dp))
                            InfoRow("Phone Guard Pro cache", Fmt.bytes(s.ownCacheBytes))
                            Spacer(Modifier.height(8.dp))
                            SecondaryButton("Clear this app's cache", vm::clearOwnCache, Modifier.fillMaxWidth(), Icons.Rounded.CleaningServices)
                            Spacer(Modifier.height(8.dp))
                            PrimaryButton("Open system storage manager", { SystemIntents.openStorageManager(context) }, Modifier.fillMaxWidth(), Icons.Rounded.Storage)
                            NoteText("Since Android 6, apps cannot clear other apps' caches. Use the app lists below to open each app's settings page, where Android lets you clear its cache.")
                        }
                    }
                }

                if (s.usageAccess) {
                    item { AppListCard("Most used apps (24 h)", Icons.Rounded.Apps, s.heavyApps, "No usage recorded.") { "Screen time ${Fmt.duration(it.foregroundMs)}" } }
                    item { AppListCard("Background activity (24 h)", Icons.Rounded.BatteryAlert, s.drainApps, "No foreground-service activity recorded.") { "Ran in background ${Fmt.duration(it.foregroundServiceMs)}" } }
                    item { AppListCard("Largest apps", Icons.Rounded.Storage, s.largestApps, "App sizes unavailable.") { "App ${Fmt.bytes(it.appBytes)} · Data ${Fmt.bytes(it.dataBytes)} · Cache ${Fmt.bytes(it.cacheBytes)}" } }
                    item { AppListCard("Apps with large caches", Icons.Rounded.CleaningServices, s.cacheApps, "No app has more than 10 MB of cache.") { "Cache ${Fmt.bytes(it.cacheBytes)} · tap ⚙ → Storage → Clear cache" } }
                    item {
                        AppListCard("Unused for 30+ days", Icons.Rounded.Delete, s.unusedApps, "No long-unused apps found.") {
                            "Last used ${if (it.lastUsed > 0) Fmt.dateTime(it.lastUsed) else "over 30 days ago"} · ${Fmt.bytes(it.totalBytes)}"
                        }
                    }
                }

                item {
                    GlassCard {
                        SectionTitle("Large media files (≥ 50 MB)", Icons.Rounded.PermMedia)
                        if (!s.mediaPermission) {
                            Text("Allow access to photos, videos and audio to find large files.", color = PgTextDim, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(10.dp))
                            PrimaryButton("Allow media access", { permLauncher.launch(mediaPerms) }, Modifier.fillMaxWidth())
                        } else if (s.largeFiles.isEmpty()) {
                            NoteText("No media files larger than 50 MB found.")
                        } else {
                            s.largeFiles.forEach { f ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = f.id in selected,
                                        onCheckedChange = { c -> selected = if (c) selected + f.id else selected - f.id },
                                        colors = CheckboxDefaults.colors(checkedColor = PgCyan)
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(f.name, color = PgText, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                        Text(
                                            "${Fmt.bytes(f.sizeBytes)} · ${f.mimeType ?: "media"} · ${Fmt.dateTime(f.dateModifiedSec * 1000)}",
                                            color = PgTextDim, style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            val selSize = s.largeFiles.filter { it.id in selected }.sumOf { it.sizeBytes }
                            PrimaryButton(
                                if (selected.isEmpty()) "Select files to delete" else "Delete ${selected.size} files (${Fmt.bytes(selSize)})",
                                {
                                    val uris = s.largeFiles.filter { it.id in selected }.map { it.uri }
                                    val sender = vm.createDeleteRequest(uris)
                                    if (sender != null) {
                                        deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
                                    } else {
                                        selected = emptySet()
                                        vm.onDeleteFinished(true)
                                    }
                                },
                                Modifier.fillMaxWidth(), Icons.Rounded.Delete, enabled = selected.isNotEmpty()
                            )
                            NoteText("Android shows its own confirmation before anything is deleted.")
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

@Composable
private fun RecommendationRow(r: Recommendation, onAction: (RecommendationAction) -> Unit) {
    val color = severityColor(r.severity)
    val icon = when (r.severity) {
        Severity.CRITICAL -> Icons.Rounded.Error
        Severity.WARNING -> Icons.Rounded.Warning
        Severity.INFO -> Icons.Rounded.Info
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(r.title, color = PgText, style = MaterialTheme.typography.bodyLarge)
            Text(r.detail, color = PgTextDim, style = MaterialTheme.typography.bodySmall)
            r.action.buttonLabel?.let { label ->
                TextButton(onClick = { onAction(r.action) }, contentPadding = PaddingValues(0.dp)) {
                    Text(label, color = PgCyan)
                }
            }
        }
    }
}

@Composable
private fun AppListCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    apps: List<AppUsageItem>,
    empty: String,
    subtitle: (AppUsageItem) -> String
) {
    val context = LocalContext.current
    GlassCard {
        SectionTitle(title, icon)
        if (apps.isEmpty()) NoteText(empty)
        apps.forEach { a ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(a.packageName, 34.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.label, color = PgText, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Text(subtitle(a), color = PgTextDim, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
                IconButton(onClick = { SystemIntents.openAppDetails(context, a.packageName) }) {
                    Icon(Icons.Rounded.Settings, "Manage ${a.label}", tint = PgCyan)
                }
            }
        }
    }
}

