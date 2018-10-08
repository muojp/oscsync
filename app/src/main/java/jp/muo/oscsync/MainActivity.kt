package jp.muo.oscsync

import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.experimental.CoroutineStart
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.launch

class MainActivity : AppCompatActivity() {

    companion object {
        const val TAG = "OSCSync"
        const val CAMERA_IDENTIFIER = "THETA"
    }

    private lateinit var oscConnection: OSCConnection

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        this.oscConnection = OSCConnection(applicationContext)
        btn_takePicture.isClickable = false
    }

    private fun chooseOne(items: List<String>, cb: (String) -> Unit) = AlertDialog.Builder(this).let {
        it.setTitle(getString(R.string.chooseSsid))
        it.setItems(items.toTypedArray()) { _, which -> cb(items.elementAt(which)) }
        it.show()
    }

    private fun onWiFiDeviceConnected(isConnected: Boolean) = runOnUiThread {
        Toast.makeText(this, "OSC Device " + (if (isConnected) "connected" else "disconnected"), Toast.LENGTH_LONG).show()
        this.oscConnection.ensureAppTrafficOnOSCDevice()
        btn_takePicture.isClickable = isConnected
    }

    fun onConnect(view: View) = this.oscConnection.let {
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

    fun onTakePicture(view: View) {
        if (!this.oscConnection.isConnected()) {
            return
        }
        val client = OSCClient(this.oscConnection.getWiFiGatewayAddress())
        GlobalScope.launch(Dispatchers.Main, CoroutineStart.DEFAULT) { client.performTakePicture() }
    }
}
