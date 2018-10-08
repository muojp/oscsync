package jp.muo.oscsync

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import java.util.*
import kotlin.concurrent.timer
import kotlin.concurrent.withLock

class OSCConnection(ctx: Context) {
    private val wifiManager = ctx.getSystemService(WifiManager::class.java)!!
    private val connManager = ctx.getSystemService(ConnectivityManager::class.java)!!

    companion object {
        const val OSC_SUFFIX = ".OSC\""
        const val NETWORK_STATUS_POLL_INTERVAL: Long = 1000
        val lock = java.util.concurrent.locks.ReentrantLock()
    }

    init {
        startNetworkStatusObserver()
    }

    fun isConnected(): Boolean {
        if (!this.wifiManager.isWifiEnabled) {
            return false
        }
        return connectedOSCNetwork != null
    }

    private val connectedOSCNetwork: WifiConfiguration?
        get() {
            with(this.wifiManager.connectionInfo) {
                if (this.networkId == -1) {
                    return null
                }
                return savedOSCDevices.find { it.networkId == this.networkId && it.status == WifiConfiguration.Status.CURRENT }
            }
        }

    fun attemptToConnect(ssid: String): Boolean =
            this.wifiManager.configuredNetworks.find { it.SSID == ssid }
                    ?.let { this.wifiManager.enableNetwork(it.networkId, true) } ?: false

    fun ensureAppTrafficOnOSCDevice(): Boolean {
        return with(connManager) {
            allNetworks.find { getNetworkInfo(it).type == ConnectivityManager.TYPE_WIFI }
                    ?.let { bindProcessToNetwork(it) } ?: false
        }
    }

    val savedOSCDevices: List<WifiConfiguration>
        get() = this.wifiManager.configuredNetworks.filter { it.SSID.endsWith(OSC_SUFFIX) }

    var connectionStatusCallback: (Boolean) -> Unit = { _ -> Unit }

    private fun onOSCDeviceConnected() = this.connectionStatusCallback(true)
    private fun onOSCDeviceDisconnected() = this.connectionStatusCallback(false)

    private var connectionState = false
    private var networkChecker: Timer? = null
    private fun startNetworkStatusObserver() = lock.withLock {
        if (this.networkChecker != null) {
            return
        }
        this.connectionState = isConnected()
        this.networkChecker = timer(period = NETWORK_STATUS_POLL_INTERVAL) {
            with(this@OSCConnection) {
                val newState = this.isConnected()
                if (!this.connectionState && newState) {
                    // connected
                    this.onOSCDeviceConnected()
                } else if (this.connectionState && !newState) {
                    // disconnected
                    this.onOSCDeviceDisconnected()
                }
                this.connectionState = newState
            }
        }
    }
}