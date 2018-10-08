package jp.muo.oscsync

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Toast
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
    fun startNetworkStatusObserver() = lock.withLock {
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

class MainActivity : AppCompatActivity() {

    private lateinit var oscConnection: OSCConnection

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        this.oscConnection = OSCConnection(applicationContext)
    }

    private fun chooseOne(items: List<String>, cb: (String) -> Unit) = AlertDialog.Builder(this).let {
        it.setTitle(getString(R.string.chooseSsid))
        it.setItems(items.toTypedArray()) { _, which -> cb(items.elementAt(which)) }
        it.show()
    }

    private fun onWiFiDeviceConnected(isConnected: Boolean) =
            runOnUiThread { Toast.makeText(this, "OSC Device " + (if (isConnected) "connected" else "disconnected"), Toast.LENGTH_LONG).show() }

    public fun onConnect(view: View) = this.oscConnection.let {
        it.connectionStatusCallback = this::onWiFiDeviceConnected
        if (!it.isConnected()) {
            // perform connection
            val deviceNames = it.savedOSCDevices.asSequence().map { dev -> dev.SSID }.sorted().toList()
            when (deviceNames.count()) {
                0 -> Toast.makeText(this@MainActivity, "No device preconfigured. Please configure one.", Toast.LENGTH_LONG).show()
                1 -> it.attemptToConnect(deviceNames[0])
                else -> chooseOne(deviceNames) { name -> it.attemptToConnect(name) }
            }
        }
    }
}
