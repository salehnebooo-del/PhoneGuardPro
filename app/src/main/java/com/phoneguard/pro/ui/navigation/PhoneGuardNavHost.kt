package com.phoneguard.pro.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.phoneguard.pro.ui.screens.BatteryScreen
import com.phoneguard.pro.ui.screens.BenchmarkScreen
import com.phoneguard.pro.ui.screens.DashboardScreen
import com.phoneguard.pro.ui.screens.DiagnosticsScreen
import com.phoneguard.pro.ui.screens.OptimizationScreen
import com.phoneguard.pro.ui.screens.ReportScreen
import com.phoneguard.pro.ui.screens.TouchTestScreen
import com.phoneguard.pro.ui.theme.PgBg
import com.phoneguard.pro.ui.theme.PgBlue
import com.phoneguard.pro.ui.theme.PgCyan
import com.phoneguard.pro.ui.theme.PgSurface
import com.phoneguard.pro.ui.theme.PgText
import com.phoneguard.pro.ui.theme.PgTextDim

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("dashboard", "Dashboard", Icons.Rounded.Dashboard),
    Tab("diagnostics", "Diagnose", Icons.Rounded.Memory),
    Tab("battery", "Battery", Icons.Rounded.BatteryChargingFull),
    Tab("benchmark", "Bench", Icons.Rounded.Speed),
    Tab("optimize", "Optimize", Icons.Rounded.AutoFixHigh)
)

const val ROUTE_REPORT = "report"
const val ROUTE_TOUCH = "touch_test"

private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneGuardNavHost() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val fullScreen = route == ROUTE_TOUCH
    val isReport = route == ROUTE_REPORT

    Scaffold(
        containerColor = PgBg,
        topBar = {
            if (!fullScreen) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = PgText)) { append("PHONE GUARD ") }
                                withStyle(SpanStyle(color = PgCyan)) { append("PRO") }
                            },
                            fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 2.sp
                        )
                    },
                    navigationIcon = {
                        if (isReport) {
                            IconButton(onClick = { nav.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = PgText)
                            }
                        }
                    },
                    actions = {
                        if (!isReport) {
                            IconButton(onClick = { nav.navigate(ROUTE_REPORT) }) {
                                Icon(Icons.Rounded.Assessment, "Health report", tint = PgCyan)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = PgBg)
                )
            }
        },
        bottomBar = {
            if (!fullScreen && !isReport) {
                NavigationBar(containerColor = PgSurface) {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = { nav.navigateTab(t.route) },
                            icon = { Icon(t.icon, t.label) },
                            label = { Text(t.label, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PgCyan,
                                selectedTextColor = PgCyan,
                                indicatorColor = PgBlue.copy(alpha = 0.18f),
                                unselectedIconColor = PgTextDim,
                                unselectedTextColor = PgTextDim
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "dashboard",
            modifier = if (fullScreen) Modifier else Modifier.padding(padding)
        ) {
            composable("dashboard") {
                DashboardScreen(
                    onOpenReport = { nav.navigate(ROUTE_REPORT) },
                    onOpenBenchmark = { nav.navigateTab("benchmark") }
                )
            }
            composable("diagnostics") { DiagnosticsScreen(onOpenTouchTest = { nav.navigate(ROUTE_TOUCH) }) }
            composable("battery") { BatteryScreen() }
            composable("benchmark") { BenchmarkScreen() }
            composable("optimize") { OptimizationScreen() }
            composable(ROUTE_REPORT) { ReportScreen() }
            composable(ROUTE_TOUCH) { TouchTestScreen(onExit = { nav.popBackStack() }) }
        }
    }
}
