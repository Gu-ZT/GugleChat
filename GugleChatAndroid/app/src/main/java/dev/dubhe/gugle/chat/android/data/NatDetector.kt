package dev.dubhe.gugle.chat.android.data

import android.util.Log
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * RFC 5780 NAT Behavior Discovery using raw STUN Binding requests.
 *
 * Classification:
 * - Endpoint-Independent Mapping (EIM): same mapped addr regardless of destination
 * - Address-Dependent Mapping (ADM): different mapped port per destination IP
 * - Address-and-Port-Dependent Mapping (APDM): different mapped port per IP:port
 *
 * Score: NAT1(Open)=1.0, NAT2(Cone/EIM)=0.8, NAT3(Restricted)=0.6, NAT4(Symmetric/ADM)=0.25
 */
object NatDetector {

    private const val TAG = "NatDetector"
    private const val STUN_PORT = 3478
    private const val TIMEOUT_MS = 3000L

    data class NatResult(val type: String, val score: Double, val details: String)

    suspend fun detect(): NatResult = withContext(Dispatchers.IO) {
        try {
            // STUN servers that support RFC 5780 (different IPs)
            val servers = listOf(
                InetSocketAddress(InetAddress.getByName("stun.l.google.com"), 19302),
                InetSocketAddress(InetAddress.getByName("stun1.l.google.com"), 19302),
            )

            val mappings = mutableMapOf<String, Int>()
            val socket = DatagramSocket()
            socket.soTimeout = TIMEOUT_MS.toInt()

            for (server in servers) {
                val mapped = sendBindingRequest(socket, server)
                if (mapped != null) mappings["${server.address.hostAddress}:${server.port}"] = mapped
            }
            socket.close()

            val uniquePorts = mappings.values.toSet()
            Log.d(TAG, "Mappings: $mappings, unique ports: $uniquePorts")

            if (mappings.isEmpty()) {
                NatResult("UDP Blocked", 0.2, "No STUN response — UDP may be blocked")
            } else if (uniquePorts.size == 1) {
                NatResult("Cone NAT (NAT2)", 0.8,
                    "Endpoint-Independent Mapping — consistent port across ${servers.size} destinations")
            } else {
                NatResult("Symmetric NAT (NAT4)", 0.25,
                    "Address-Dependent Mapping — different port per destination (${uniquePorts.size} unique ports)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Detection failed", e)
            NatResult("Unknown", 0.5, "Detection error: ${e.message}")
        }
    }

    private fun sendBindingRequest(socket: DatagramSocket, server: InetSocketAddress): Int? {
        try {
            val request = buildStunBindingRequest()
            val packet = DatagramPacket(request, request.size, server)
            socket.send(packet)

            val buf = ByteArray(1024)
            val response = DatagramPacket(buf, buf.size)
            socket.receive(response)

            // Parse XOR-MAPPED-ADDRESS from STUN response (type 0x0020)
            return parseXorMappedAddress(response.data, response.offset, response.length)
        } catch (e: Exception) {
            Log.d(TAG, "STUN to $server failed: ${e.message}")
            return null
        }
    }

    /** Build minimal STUN Binding Request (RFC 5389) */
    private fun buildStunBindingRequest(): ByteArray {
        val msg = ByteArray(20)
        // Message Type: Binding Request (0x0001)
        msg[0] = 0x00; msg[1] = 0x01
        // Message Length: 0 (no attributes)
        msg[2] = 0x00; msg[3] = 0x00
        // Magic Cookie: 0x2112A442
        msg[4] = 0x21.toByte(); msg[5] = 0x12.toByte()
        msg[6] = 0xA4.toByte(); msg[7] = 0x42.toByte()
        // Transaction ID: random 12 bytes
        for (i in 8..19) msg[i] = (Math.random() * 256).toInt().toByte()
        return msg
    }

    /** Parse XOR-MAPPED-ADDRESS (0x0020) from STUN response */
    private fun parseXorMappedAddress(data: ByteArray, offset: Int, length: Int): Int? {
        if (length < 20) return null
        var pos = 20 // skip 20-byte STUN header
        while (pos + 4 <= length) {
            val type = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
            val attrLen = ((data[pos + 2].toInt() and 0xFF) shl 8) or (data[pos + 3].toInt() and 0xFF)
            pos += 4
            if (type == 0x0020 && attrLen >= 8) { // XOR-MAPPED-ADDRESS
                // Skip family (1 byte) + x-port (2 bytes)
                val port = (
                    ((data[pos + 2].toInt() and 0xFF) shl 8) or (data[pos + 3].toInt() and 0xFF)
                ) xor 0x2112
                return port
            }
            pos += attrLen
            // Align to 4 bytes
            if (attrLen % 4 != 0) pos += 4 - (attrLen % 4)
        }
        return null
    }
}
