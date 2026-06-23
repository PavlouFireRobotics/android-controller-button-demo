package com.example.greetingcard.network

import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BatteryInfo(
    val level: Int = 0,
    val voltage: Float = 0f,
    val temperature: Float = 0f,
    val isCharging: Boolean = false
)

data class RobotBatteryState(
    val front: BatteryInfo = BatteryInfo(),
    val rear: BatteryInfo = BatteryInfo(),
    val sleepStatus: Int = 0 // 0: Awake, 1: Sleep, 2: Entering Sleep
) {
    val isSleeping: Boolean get() = sleepStatus != 0
}

class RobotClient(private val ip: String = "10.21.33.103", private val port: Int = 30000) {

    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private var socket: DatagramSocket? = null
    private var listenJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var lastState = RobotBatteryState()
    var onBatteryUpdate: ((RobotBatteryState) -> Unit)? = null

    fun start() {
        if (listenJob != null) return
        
        socket = DatagramSocket().apply {
            soTimeout = 5000 // High timeout for the listener
        }
        
        listenJob = scope.launch {
            val buffer = ByteArray(8192)
            val packet = DatagramPacket(buffer, buffer.size)
            while (isActive) {
                try {
                    socket?.receive(packet)
                    val data = packet.data.copyOfRange(0, packet.length)
                    handleResponse(data)
                } catch (_: Exception) {
                    // Timeout or socket closed
                    if (socket?.isClosed == true) break
                }
            }
        }
    }

    suspend fun sendHeartbeat() = withContext(Dispatchers.IO) {
        val currentTime = sdf.format(Date())
        val json = """
        {
            "PatrolDevice": {
                "Type": 100,
                "Command": 100,
                "Time": "$currentTime",
                "Items": {}
            }
        }
        """.trimIndent()
        sendRawCommand(json, messageId = 1)
    }

    suspend fun setSleepMode(sleep: Boolean) = withContext(Dispatchers.IO) {
        val currentTime = sdf.format(Date())
        val json = """
        {
            "PatrolDevice": {
                "Type": 1101,
                "Command": 6,
                "Time": "$currentTime",
                "Items": {
                    "Sleep": $sleep,
                    "Auto": true,
                    "Time": 5
                }
            }
        }
        """.trimIndent()
        sendRawCommand(json, messageId = 1)
    }

    private suspend fun sendRawCommand(json: String, messageId: Int = 1) = withContext(Dispatchers.IO) {
        try {
            val ds = socket ?: return@withContext
            val address = InetAddress.getByName(ip)
            
            val dataBytes = json.toByteArray(StandardCharsets.UTF_8)
            val dataLength = dataBytes.size
            
            val header = ByteArray(16)
            header[0] = 0xeb.toByte()
            header[1] = 0x91.toByte()
            header[2] = 0xeb.toByte()
            header[3] = 0x90.toByte()
            header[4] = (dataLength and 0xFF).toByte()
            header[5] = ((dataLength shr 8) and 0xFF).toByte()
            header[6] = (messageId and 0xFF).toByte()
            header[7] = ((messageId shr 8) and 0xFF).toByte()
            header[8] = 0x01.toByte() // JSON
            
            val packetData = ByteArray(16 + dataLength)
            System.arraycopy(header, 0, packetData, 0, 16)
            System.arraycopy(dataBytes, 0, packetData, 16, dataLength)
            
            val packet = DatagramPacket(packetData, packetData.size, address, port)
            ds.send(packet)
        } catch (e: Exception) {
            Log.e("RobotClient", "Send error: ${e.message}")
        }
    }

    private fun handleResponse(data: ByteArray) {
        if (data.size < 16) return
        try {
            val asduLength = (data[4].toInt() and 0xFF) or ((data[5].toInt() and 0xFF) shl 8)
            if (data.size < 16 + asduLength) return
            
            val payload = String(data, 16, asduLength, StandardCharsets.UTF_8)
            
            val json = JSONObject(payload)
            val patrolDevice = json.optJSONObject("PatrolDevice") ?: return
            val type = patrolDevice.optInt("Type")
            val command = patrolDevice.optInt("Command")
            val items = patrolDevice.optJSONObject("Items") ?: return

            if (type == 1002) {
                when (command) {
                    5 -> parseBatteryData(items)
                    6 -> parseBasicStatus(items)
                }
            }
        } catch (e: Exception) {
            Log.e("RobotClient", "Parse error: ${e.message}")
        }
    }

    private fun parseBasicStatus(items: JSONObject) {
        val basicStatus = items.optJSONObject("BasicStatus") ?: return
        val sleepStatus = when (val sleepValue = basicStatus.opt("Sleep")) {
            is Boolean -> if (sleepValue) 1 else 0
            is Number -> sleepValue.toInt()
            else -> 0
        }
        
        lastState = lastState.copy(sleepStatus = sleepStatus)
        onBatteryUpdate?.invoke(lastState)
    }

    private fun parseBatteryData(items: JSONObject) {
        var front = BatteryInfo()
        var rear = BatteryInfo()
        val sleepValue = items.opt("Sleep")
        val sleepStatus = when (sleepValue) {
            is Boolean -> if (sleepValue) 1 else 0
            is Number -> sleepValue.toInt()
            else -> lastState.sleepStatus
        }

        if (items.has("BatteryList")) {
            val list = items.optJSONArray("BatteryList")
            if (list != null && list.length() >= 2) {
                val rearObj = list.getJSONObject(0)
                val frontObj = list.getJSONObject(1)
                
                rear = BatteryInfo(
                    level = rearObj.optInt("BatteryLevel"),
                    voltage = rearObj.optDouble("Voltage").toFloat(),
                    temperature = rearObj.optDouble("battery_temperature").toFloat(),
                    isCharging = rearObj.optBoolean("charge")
                )
                front = BatteryInfo(
                    level = frontObj.optInt("BatteryLevel"),
                    voltage = frontObj.optDouble("Voltage").toFloat(),
                    temperature = frontObj.optDouble("battery_temperature").toFloat(),
                    isCharging = frontObj.optBoolean("charge")
                )
            }
        } else if (items.has("BatteryStatus")) {
            val status = items.optJSONObject("BatteryStatus")
            if (status != null) {
                rear = BatteryInfo(
                    level = status.optInt("BatteryLevelLeft"),
                    voltage = status.optDouble("VoltageLeft").toFloat(),
                    temperature = status.optDouble("battery_temperatureLeft").toFloat(),
                    isCharging = status.optBoolean("chargeLeft")
                )
                front = BatteryInfo(
                    level = status.optInt("BatteryLevelRight"),
                    voltage = status.optDouble("VoltageRight").toFloat(),
                    temperature = status.optDouble("battery_temperatureRight").toFloat(),
                    isCharging = status.optBoolean("chargeRight")
                )
            }
        }
        
        lastState = RobotBatteryState(front, rear, sleepStatus)
        onBatteryUpdate?.invoke(lastState)
    }

    fun stop() {
        listenJob?.cancel()
        listenJob = null
        socket?.close()
        socket = null
    }
}
