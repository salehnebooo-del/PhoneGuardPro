package com.phoneguard.pro.data.diagnostics

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.Build
import com.phoneguard.pro.data.model.LatencyResult
import com.phoneguard.pro.data.model.NetworkStatus
import com.phoneguard.pro.data.model.WifiDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Network status via ConnectivityManager / WifiManager.
 * SSID requires location permission on Android 8.1+; this app intentionally does NOT request
 * location, so the SSID is shown as hidden. Signal, link speed and band remain available.
 */
class NetworkInfoProvider(context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val wm: WifiManager? = context.applicationContext.getSystemService(WifiManager::class.java)

    fun read(): NetworkStatus {
        val net = cm.activeNetwork
        val caps = net?.let { cm.getNetworkCapabilities(it) }
        val lp = net?.let { cm.getLinkProperties(it) }
        val transport = when {
            caps == null -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
            else -> "Other"
        }
        val wifi = if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) readWifi() else null
        return NetworkStatus(
            connected = caps != null,
            validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            transport = transport,
            metered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false,
            downstreamKbps = caps?.linkDownstreamBandwidthKbps?.takeIf { it > 0 },
            upstreamKbps = caps?.linkUpstreamBandwidthKbps?.takeIf { it > 0 },
            ipAddresses = lp?.linkAddresses?.mapNotNull { it.address?.hostAddress } ?: emptyList(),
            dnsServers = lp?.dnsServers?.mapNotNull { it.hostAddress } ?: emptyList(),
            wifi = wifi,
            totalRxBytes = TrafficStats.getTotalRxBytes(),
            totalTxBytes = TrafficStats.getTotalTxBytes()
        )
    }

    @Suppress("DEPRECATION")
    private fun readWifi(): WifiDetails? {
        val info = wm?.connectionInfo ?: return null
        val rssi = info.rssi.takeIf { it in -126..-1 }
        val ssid = info.ssid?.removeSurrounding("\"")
            ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
        return WifiDetails(
            rssiDbm = rssi,
            signalPercent = rssi?.let { (2 * (it + 100)).coerceIn(0, 100) },
            linkSpeedMbps = info.linkSpeed.takeIf { it > 0 },
            txLinkMbps = info.txLinkSpeedMbps.takeIf { it > 0 },
            rxLinkMbps = info.rxLinkSpeedMbps.takeIf { it > 0 },
            frequencyMhz = info.frequency.takeIf { it > 0 },
            standard = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) wifiStandard(info.wifiStandard) else null,
            ssid = ssid
        )
    }

    private fun wifiStandard(s: Int) = when (s) {
        1 -> "Legacy (802.11a/b/g)"
        4 -> "Wi-Fi 4 (802.11n)"
        5 -> "Wi-Fi 5 (802.11ac)"
        6 -> "Wi-Fi 6 (802.11ax)"
        7 -> "802.11ad"
        8 -> "Wi-Fi 7 (802.11be)"
        else -> null
    }

    /** Real TCP handshake latency (median of [attempts]). Runs only when the user taps the test. */
    suspend fun tcpLatency(label: String, host: String, port: Int, attempts: Int = 3): LatencyResult =
        withContext(Dispatchers.IO) {
            val times = mutableListOf<Double>()
            var error: String? = null
            repeat(attempts) {
                try {
                    Socket().use { s ->
                        val t0 = System.nanoTime()
                        s.connect(InetSocketAddress(host, port), 3000)
                        times += (System.nanoTime() - t0) / 1e6
                    }
                } catch (e: Exception) {
                    error = e.javaClass.simpleName
                }
            }
            val sorted = times.sorted()
            LatencyResult(label, sorted.getOrNull(sorted.size / 2), if (sorted.isEmpty()) (error ?: "Failed") else null)
        }

    suspend fun dnsLookup(host: String): LatencyResult = withContext(Dispatchers.IO) {
        try {
            val t0 = System.nanoTime()
            InetAddress.getAllByName(host)
            LatencyResult("DNS lookup ($host)", (System.nanoTime() - t0) / 1e6, null)
        } catch (e: Exception) {
            LatencyResult("DNS lookup ($host)", null, e.javaClass.simpleName)
        }
    }
}
