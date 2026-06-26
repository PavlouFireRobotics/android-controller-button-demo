package com.robot.g20demo

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.robot.g20demo.ui.G20State
import com.skydroid.rcsdk.KeyManager
import com.skydroid.rcsdk.RCSDKManager
import com.skydroid.rcsdk.SDKManagerCallBack
import com.skydroid.rcsdk.common.callback.CompletionCallbackWith
import com.skydroid.rcsdk.common.error.SkyException
import com.skydroid.rcsdk.key.RemoteControllerKey
import androidx.lifecycle.viewModelScope
import com.robot.g20demo.network.RobotBatteryState
import com.robot.g20demo.network.RobotClient
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.*
import kotlin.concurrent.fixedRateTimer

/**
 * ViewModel for the Main Activity.
 * Handles SDK initialization, RC polling, and UI state management.
 */
class MainViewModel : ViewModel() {

    private val robotClient = RobotClient().apply {
        onBatteryUpdate = { state ->
            batteryState = state
        }
    }

    // The current state of the G20 controller (16 channels)
    var g20State by mutableStateOf(G20State())
        private set

    // The current battery status of the robot
    var batteryState by mutableStateOf(RobotBatteryState())
        private set

    var videoUrl1 by mutableStateOf("rtsp://10.21.33.103:8554/video1")
        private set

    var videoUrl2 by mutableStateOf("rtsp://10.21.33.103:8554/video2")
        private set

    var networkStatus by mutableStateOf("Checking...")
        private set

    private var pollTimer: Timer? = null
    private var heartbeatTimer: Timer? = null

    private var isInitialized = false
    private var isSdkLoaded = false

    /**
     * Initializes the Skydroid SDK and connects to the RC.
     * @param context Application context for SDK initialization.
     */
    fun initialize(context: Context) {
        loadConfig(context)
        checkNetwork(context)
        if (isInitialized) return
        isInitialized = true

        // Start robot communication immediately
        robotClient.start()
        startHeartbeat()

        // Attempt to initialize RC SDK (will fail gracefully on emulators)
        try {
            RCSDKManager.initSDK(context.applicationContext, object : SDKManagerCallBack {
                override fun onRcConnectFail(e: SkyException?) {
                    Log.e("MainViewModel", "RC Connect Fail: ${e?.toString()}")
                }

                override fun onRcConnected() {
                    Log.d("MainViewModel", "RC Connected successfully!")
                    startPolling()
                }

                override fun onRcDisconnect() {
                    Log.w("MainViewModel", "RC Disconnected")
                    stopPolling()
                }
            })
            RCSDKManager.connectToRC()
            isSdkLoaded = true
        } catch (e: Throwable) {
            Log.w("MainViewModel", "RC SDK initialization skipped or failed: ${e.message}")
        }
    }

    private fun loadConfig(context: Context) {
        try {
            // 1. Try to load from internal storage first (allows easy overrides via ADB)
            val configFile = File(context.filesDir, "config.json")
            val jsonString = if (configFile.exists()) {
                Log.d("MainViewModel", "Loading config from internal storage")
                configFile.readText()
            } else {
                // 2. Fallback to assets
                Log.d("MainViewModel", "Loading config from assets")
                context.assets.open("config.json").bufferedReader().use { it.readText() }
            }

            val json = JSONObject(jsonString)
            val ip = json.optString("robot_ip", "10.21.33.103")
            // Try tcp_port first, then fallback to old robot_port
            val port = if (json.has("tcp_port")) json.getInt("tcp_port") else json.optInt("robot_port", 30001)

            val videoPort = json.optInt("video_port", 8554)
            val v1Path = json.optString("video1_path", "/video1")
            val v2Path = json.optString("video2_path", "/video2")

            videoUrl1 = "rtsp://$ip:$videoPort$v1Path"
            videoUrl2 = "rtsp://$ip:$videoPort$v2Path"

            Log.i("MainViewModel", "Configuring RobotClient with IP: $ip, Port: $port")
            robotClient.updateConfig(ip, port)
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error loading config, using defaults", e)
        }
    }

    private fun startHeartbeat() {
        heartbeatTimer?.cancel()
        heartbeatTimer = fixedRateTimer("RobotHeartbeat", daemon = true, initialDelay = 1000, period = 2000) {
            viewModelScope.launch {
                robotClient.sendHeartbeat()
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatTimer?.cancel()
        heartbeatTimer = null
    }

    private fun checkNetwork(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(activeNetwork)
        
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isEthernet = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        
        // TRANSPORT_USB was added in API 31, but we can detect it via other means or just rely on the IP
        val ip = getLocalIpAddress()
        
        networkStatus = when {
            isWifi -> "Wi-Fi ($ip)"
            isEthernet -> "Ethernet ($ip)"
            ip != "Unknown" -> "Connected ($ip)"
            else -> "Disconnected"
        }
        Log.d("MainViewModel", "Network Status: $networkStatus")
    }

    private fun getLocalIpAddress(): String {
        try {
            val en = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intf = en.nextElement()
                val enumIpAddr = intf.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress = enumIpAddr.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is InetAddress) {
                        val ip = inetAddress.hostAddress
                        if (ip != null && !ip.contains(":")) return ip // Prefer IPv4
                    }
                }
            }
        } catch (ex: Exception) {
            Log.e("MainViewModel", "Error getting IP", ex)
        }
        return "Unknown"
    }

    /**
     * Starts a timer to poll channel values every 100ms.
     */
    private fun startPolling() {
        pollTimer?.cancel()
        pollTimer = fixedRateTimer("G20Polling", daemon = true, initialDelay = 1000, period = 100) {
            KeyManager.get(RemoteControllerKey.KeyChannels, object : CompletionCallbackWith<IntArray> {
                override fun onSuccess(channels: IntArray?) {
                    channels?.let {
                        // Update the state with new channel data
                        g20State = G20State(it.clone())
                    }
                }

                override fun onFailure(e: SkyException) {
                    // Fail silently for polling errors
                }
            })
        }
    }

    /**
     * Stops the polling timer.
     */
    private fun stopPolling() {
        pollTimer?.cancel()
        pollTimer = null
    }

    /**
     * Toggles robot sleep mode.
     */
    fun toggleSleepMode() {
        viewModelScope.launch {
            robotClient.setSleepMode(!batteryState.isSleeping)
        }
    }

    /**
     * Clean up resources when ViewModel is cleared.
     */
    override fun onCleared() {
        super.onCleared()
        stopPolling()
        stopHeartbeat()
        robotClient.stop()
        if (isSdkLoaded) {
            try {
                RCSDKManager.disconnectRC()
            } catch (_: Throwable) {}
        }
    }
}
