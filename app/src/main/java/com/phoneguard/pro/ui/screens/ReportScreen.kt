package com.phoneguard.pro.ui.screens

import android.content.Intent
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
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phoneguard.pro.data.model.Recommendation
import com.phoneguard.pro.data.model.Severity
import com.phoneguard.pro.ui.components.GlassCard
import com.phoneguard.pro.ui.components.NoteText
import com.phoneguard.pro.ui.components.PrimaryButton
import com.phoneguard.pro.ui.components.ScoreGauge
import com.phoneguard.pro.ui.components.SecondaryButton
import com.phoneguard.pro.ui.components.SectionTitle
import com.phoneguard.pro.ui.components.StatusChip
import com.phoneguard.pro.ui.components.UsageBar
import com.phoneguard.pro.ui.components.severityColor
import com.phoneguard.pro.ui.theme.PgCyan
import com.phoneguard.pro.ui.theme.PgSuccess
import com.phoneguard.pro.ui.theme.PgText
import com.phoneguard.pro.ui.theme.PgTextDim
import com.phoneguard.pro.ui.theme.scoreColor
import com.phoneguard.pro.ui.viewmodel.ReportViewModel
import com.phoneguard.pro.util.Fmt

@Composable
fun ReportScreen(vm: ReportViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(s.shareFile) {
        s.shareFile?.let { f ->
            context.startActivity(Intent.createChooser(vm.shareIntent(f), "Share health report"))
            vm.onShareHandled()
        }
    }
    LaunchedEffect(s.message) {
        s.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }

    val r = s.report
    Box(Modifier.fillMaxSize()) {
        if (r == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = PgCyan)
                    Spacer(Modifier.height(12.dp))
                    Text("Analysing device…", color = PgTextDim)
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    GlassCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Device health report", style = MaterialTheme.typography.headlineSmall, color = PgText, modifier = Modifier.weight(1f))
                            IconButton(onClick = vm::generate, enabled = !s.generating) {
                                Icon(Icons.Rounded.Refresh, "Re-analyse", tint = PgCyan)
                            }
                        }
                        Text("${r.device.manufacturer} ${r.device.model} · ${Fmt.dateTime(r.timestamp)}", color = PgTextDim, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(16.dp))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            ScoreGauge(r.overallScore, diameter = 190.dp, label = "OVERALL HEALTH")
                        }
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            StatusChip(r.overallCondition, scoreColor(r.overallScore))
                        }
                    }
                }

                item {
                    GlassCard {
                        SectionTitle("Components", Icons.Rounded.Assessment)
                        r.components.forEach { c ->
                            Column(Modifier.padding(vertical = 6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(c.name, color = PgText, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    Text(
                                        c.score?.let { "$it · ${c.condition}" } ?: c.condition,
                                        color = scoreColor(c.score), fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                UsageBar((c.score ?: 0) / 100f, color = scoreColor(c.score))
                                c.details.forEach { d -> NoteText("• $d") }
                            }
                        }
                        NoteText("Components without data are excluded from the overall score rather than guessed.")
                    }
                }

                item {
                    GlassCard {
                        SectionTitle("Detected issues (${r.issues.size})", Icons.Rounded.Warning)
                        if (r.issues.isEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.CheckCircle, null, tint = PgSuccess)
                                Spacer(Modifier.width(8.dp))
                                Text("No significant issues detected.", color = PgText)
                            }
                        }
                        r.issues.forEach { IssueRow(it) }
                    }
                }

                item {
                    GlassCard {
                        SectionTitle("Recommended actions", Icons.Rounded.TipsAndUpdates)
                        r.recommendations.filter { it.severity == Severity.INFO }.forEach { IssueRow(it) }
                        if (r.recommendations.none { it.severity == Severity.INFO }) NoteText("No further recommendations.")
                    }
                }

                item {
                    GlassCard {
                        SectionTitle("Export", Icons.Rounded.PictureAsPdf)
                        PrimaryButton(
                            if (s.exporting) "Generating PDF…" else "Export PDF & share",
                            vm::exportAndShare, Modifier.fillMaxWidth(), Icons.Rounded.PictureAsPdf, enabled = !s.exporting
                        )
                        Spacer(Modifier.height(8.dp))
                        SecondaryButton("Save PDF to Downloads", vm::saveToDownloads, Modifier.fillMaxWidth(), Icons.Rounded.Download, enabled = !s.exporting)
                        NoteText("The report is generated on-device. Nothing is uploaded unless you choose to share it.")
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

@Composable
private fun IssueRow(r: Recommendation) {
    val icon = when (r.severity) {
        Severity.CRITICAL -> Icons.Rounded.Error
        Severity.WARNING -> Icons.Rounded.Warning
        Severity.INFO -> Icons.Rounded.Info
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = severityColor(r.severity), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(r.title, color = PgText, style = MaterialTheme.typography.bodyLarge)
            Text(r.detail, color = PgTextDim, style = MaterialTheme.typography.bodySmall)
        }
    }
}
