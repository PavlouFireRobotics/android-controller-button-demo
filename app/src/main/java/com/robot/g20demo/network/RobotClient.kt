package com.robot.g20demo.network

import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.net.DatagramPacket
import java.net.DatagramSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

class RobotClient(private var ip: String = "10.21.33.103", private var port: Int = 30001) {

    fun updateConfig(ip: String, port: Int) {
        this.ip = ip
        this.port = port
    }

    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var listenJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var lastState = RobotBatteryState()
    var onBatteryUpdate: ((RobotBatteryState) -> Unit)? = null

    fun start() {
        if (listenJob != null) return
        
        listenJob = scope.launch {
            while (isActive) {
                try {
                    Log.d("RobotClient", "Connecting to $ip:$port...")
                    val s = Socket(ip, port)
                    s.soTimeout = 10000
                    socket = s
                    outputStream = s.getOutputStream()
                    Log.d("RobotClient", "Connected to robot!")

                    val inputStream = s.getInputStream()
                    handleInputStream(inputStream)
                } catch (e: Exception) {
                    Log.e("RobotClient", "Connection error: ${e.message}")
                    socket?.close()
                    socket = null
                    outputStream = null
                    delay(5000) // Retry delay
                }
            }
        }
    }

    private fun handleInputStream(inputStream: InputStream) {
        val headerBuffer = ByteArray(16)
        while (socket?.isConnected == true) {
            // 1. Read Header
            if (!readFully(inputStream, headerBuffer)) break
            
            // Validate Magic Numbers
            if (headerBuffer[0] != 0xeb.toByte() || headerBuffer[1] != 0x91.toByte()) {
                Log.w("RobotClient", "Invalid magic header, syncing...")
                continue
            }

            // 2. Read Payload Length
            val asduLength = (headerBuffer[4].toInt() and 0xFF) or ((headerBuffer[5].toInt() and 0xFF) shl 8)
            
            // 3. Read Payload
            val payloadBuffer = ByteArray(asduLength)
            if (!readFully(inputStream, payloadBuffer)) break
            
            val payload = String(payloadBuffer, StandardCharsets.UTF_8)
            handlePayload(payload)
        }
    }

    private fun readFully(inputStream: InputStream, buffer: ByteArray): Boolean {
        var totalRead = 0
        while (totalRead < buffer.size) {
            val read = inputStream.read(buffer, totalRead, buffer.size - totalRead)
            if (read == -1) return false
            totalRead += read
        }
        return true
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
        //sendUdpCommand(json, messageId = 1) send over UDP
        sendRawCommand(json, messageId = 1) // send over TCP
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
            val out = outputStream ?: return@withContext
            
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
            
            out.write(packetData)
            out.flush()
        } catch (e: Exception) {
            Log.e("RobotClient", "Send error: ${e.message}")
        }
    }

    // draft for also sending commands over UDP (Needs Refinement e.g. port as a parameter, not create a new udp socket everytime etc.)
    private suspend fun sendUdpCommand(json: String, messageId: Int = 1) = withContext(Dispatchers.IO) {
        try {
            val dataBytes = json.toByteArray(StandardCharsets.UTF_8)
            val dataLength = dataBytes.size

            // Build the identical 16-byte header
            val header = ByteArray(16)
            header[0] = 0xeb.toByte()
            header[1] = 0x91.toByte()
            header[2] = 0xeb.toByte()
            header[3] = 0x90.toByte()
            header[4] = (dataLength and 0xFF).toByte()
            header[5] = ((dataLength shr 8) and 0xFF).toByte()
            header[6] = (messageId and 0xFF).toByte()
            header[7] = ((messageId shr 8) and 0xFF).toByte()
            header[8] = 0x01.toByte() // JSON format

            val packetData = ByteArray(16 + dataLength)
            System.arraycopy(header, 0, packetData, 0, 16)
            System.arraycopy(dataBytes, 0, packetData, 16, dataLength)

            // --- UDP SENDING LOGIC ---
            // Replace "10.0.2.2" if you have a dynamic IP variable stored in RobotClient
            val address = InetAddress.getByName("10.0.2.2")
            val port = 30000 // UDP port

            val udpSocket = DatagramSocket()
            val datagramPacket = DatagramPacket(packetData, packetData.size, address, port)

            udpSocket.send(datagramPacket) //send over udp
            udpSocket.close()
            // -------------------------

        } catch (e: Exception) {
            Log.e("RobotClient", "UDP Send error: ${e.message}")
        }
    }
    private fun handlePayload(payload: String) {
        try {
            val json = JSONObject(payload)
            val patrolDevice = json.optJSONObject("PatrolDevice") ?: return
            val type = patrolDevice.optInt("Type")
            val command = patrolDevice.optInt("Command")
            val items = patrolDevice.optJSONObject("Items") ?: return
            // Log the received feedback
            Log.d("MY_WIRETAP", "Parsed Type: $type, Command: $command")
            Log.d("MY_WIRETAP", "RAW JSON: $payload")
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
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        outputStream = null
    }
}
