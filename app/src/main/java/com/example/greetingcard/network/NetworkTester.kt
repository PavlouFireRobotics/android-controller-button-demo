package com.example.greetingcard.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TestResult(
    val type: String,
    val destination: String,
    val startTime: String,
    val isSuccess: Boolean,
    val response: String? = null,
    val error: String? = null
)

class NetworkTester {

    private fun getCurrentTimestamp(): String {
        return SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
    }

    suspend fun testPing(ip: String): TestResult = withContext(Dispatchers.IO) {
        val startTime = getCurrentTimestamp()
        try {
            val process = Runtime.getRuntime().exec("ping -c 1 -W 2 $ip")
            val exitCode = process.waitFor()
            val isSuccess = exitCode == 0
            TestResult(
                type = "PING",
                destination = ip,
                startTime = startTime,
                isSuccess = isSuccess,
                response = if (isSuccess) "Host is reachable" else "Host unreachable (Exit code: $exitCode)"
            )
        } catch (e: Exception) {
            TestResult(
                type = "PING",
                destination = ip,
                startTime = startTime,
                isSuccess = false,
                error = e.localizedMessage
            )
        }
    }

    suspend fun testTcp(ip: String, port: Int): TestResult = withContext(Dispatchers.IO) {
        val startTime = getCurrentTimestamp()
        val destination = "$ip:$port"
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(ip, port), 2000)
            socket.close()
            TestResult(
                type = "TCP",
                destination = destination,
                startTime = startTime,
                isSuccess = true,
                response = "Connected successfully"
            )
        } catch (e: Exception) {
            TestResult(
                type = "TCP",
                destination = destination,
                startTime = startTime,
                isSuccess = false,
                error = e.localizedMessage
            )
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    suspend fun testUdp(ip: String, port: Int): TestResult = withContext(Dispatchers.IO) {
        val startTime = getCurrentTimestamp()
        val destination = "$ip:$port"
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            socket.soTimeout = 2000
            val address = InetAddress.getByName(ip)
            val data = "TEST".toByteArray()
            val packet = DatagramPacket(data, data.size, address, port)
            
            socket.send(packet)
            
            val receiveData = ByteArray(1024)
            val receivePacket = DatagramPacket(receiveData, receiveData.size)
            
            try {
                socket.receive(receivePacket)
                val responseText = String(receivePacket.data, 0, receivePacket.length)
                TestResult(
                    type = "UDP",
                    destination = destination,
                    startTime = startTime,
                    isSuccess = true,
                    response = "Received response: $responseText"
                )
            } catch (e: java.net.SocketTimeoutException) {
                TestResult(
                    type = "UDP",
                    destination = destination,
                    startTime = startTime,
                    isSuccess = true, // Send was successful
                    response = "Sent successfully. No reply received within 2s. (Note: UDP success does not guarantee delivery without a reply)"
                )
            }
        } catch (e: Exception) {
            TestResult(
                type = "UDP",
                destination = destination,
                startTime = startTime,
                isSuccess = false,
                error = e.localizedMessage
            )
        } finally {
            socket?.close()
        }
    }

    suspend fun testHeartbeat(ip: String, port: Int): TestResult = withContext(Dispatchers.IO) {
        val startTime = getCurrentTimestamp()
        val destination = "$ip:$port"
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            socket.soTimeout = 3000
            val address = InetAddress.getByName(ip)
            
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val currentTime = sdf.format(Date())
            val json = """{"PatrolDevice":{"Type":100,"Command":100,"Time":"$currentTime","Items":{}}}"""
            val dataBytes = json.toByteArray(StandardCharsets.UTF_8)
            val dataLength = dataBytes.size
            
            val header = ByteArray(16)
            header[0] = 0xeb.toByte()
            header[1] = 0x91.toByte()
            header[2] = 0xeb.toByte()
            header[3] = 0x90.toByte()
            header[4] = (dataLength and 0xFF).toByte()
            header[5] = ((dataLength shr 8) and 0xFF).toByte()
            header[6] = 0x01.toByte()
            header[7] = 0x00.toByte()
            header[8] = 0x01.toByte()
            
            val packetData = ByteArray(16 + dataLength)
            System.arraycopy(header, 0, packetData, 0, 16)
            System.arraycopy(dataBytes, 0, packetData, 16, dataLength)
            
            val packet = DatagramPacket(packetData, packetData.size, address, port)
            socket.send(packet)
            
            val receiveData = ByteArray(2048)
            val receivePacket = DatagramPacket(receiveData, receiveData.size)
            
            socket.receive(receivePacket)
            if (receivePacket.length < 16) {
                return@withContext TestResult(
                    type = "HEARTBEAT",
                    destination = destination,
                    startTime = startTime,
                    isSuccess = false,
                    error = "Invalid response length"
                )
            }
            
            val asduLen = (receiveData[4].toInt() and 0xFF) or ((receiveData[5].toInt() and 0xFF) shl 8)
            val payload = String(receiveData, 16, asduLen, StandardCharsets.UTF_8)
            
            TestResult(
                type = "HEARTBEAT",
                destination = destination,
                startTime = startTime,
                isSuccess = true,
                response = "Payload: $payload"
            )
        } catch (e: Exception) {
            TestResult(
                type = "HEARTBEAT",
                destination = destination,
                startTime = startTime,
                isSuccess = false,
                error = e.localizedMessage
            )
        } finally {
            socket?.close()
        }
    }
}
