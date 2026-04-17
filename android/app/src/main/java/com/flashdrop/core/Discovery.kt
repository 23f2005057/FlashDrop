package com.flashdrop.core

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class Discovery(
    private val context: Context,
    private val settings: AppSettings,
    private val onDeviceFound: (Device) -> Unit,
    private val onDeviceLost:  (String) -> Unit,
) {
    private val scope      = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val seen       = mutableMapOf<String, Long>()  // id -> lastSeen ms
    private val seenLock   = Any()
    private var multiLock: WifiManager.MulticastLock? = null

    fun start() {
        scope.launch(Dispatchers.IO) { acquireMulticastLock() }
        scope.launch { broadcaster() }
        scope.launch { listener() }
        scope.launch { reaper() }
    }

    fun stop() {
        scope.cancel()
        multiLock?.release()
    }

    private fun myIp(): String {
        val wm = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip = wm.connectionInfo.ipAddress
        return String.format(
            "%d.%d.%d.%d",
            ip and 0xff, ip shr 8 and 0xff,
            ip shr 16 and 0xff, ip shr 24 and 0xff)
    }

    private fun broadcastAddress(): String {
        val wm = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip   = wm.connectionInfo.ipAddress
        val mask = wm.dhcpInfo.netmask
        val bcast = (ip and mask) or mask.inv()
        return String.format(
            "%d.%d.%d.%d",
            bcast and 0xff, bcast shr 8 and 0xff,
            bcast shr 16 and 0xff, bcast shr 24 and 0xff)
    }

    private suspend fun broadcaster() {
        val sock = DatagramSocket()
        sock.setBroadcast(true)
        while (scope.isActive) {
            try {
                val myIp = myIp()
                val payload = JSONObject().apply {
                    put("app",         "flashdrop")
                    put("device_name", settings.deviceName)
                    put("device_type", "Android")
                    put("ip",          myIp)
                    put("tcp_port",    settings.tcpPort)
                }.toString().toByteArray()

                // Broadcast to multiple addresses for maximum compatibility
                val targets = mutableListOf("255.255.255.255")
                // Add subnet broadcast
                try {
                    val bcast = broadcastAddress()
                    if (bcast != "255.255.255.255") targets.add(bcast)
                } catch (e: Exception) { }

                for (target in targets) {
                    try {
                        val pkt = DatagramPacket(
                            payload, payload.size,
                            InetAddress.getByName(target),
                            settings.udpPort)
                        sock.send(pkt)
                    } catch (e: Exception) { }
                }
            } catch (e: Exception) { }
            delay(2000)
        }
        sock.close()
    }

    private suspend fun listener() {
        val sock = DatagramSocket(settings.udpPort)
        sock.broadcast = true
        val buf = ByteArray(2048)
        val myIp = myIp()

        while (scope.isActive) {
            try {
                val pkt = DatagramPacket(buf, buf.size)
                withContext(Dispatchers.IO) { sock.receive(pkt) }
                val raw  = String(pkt.data, 0, pkt.length)
                val json = JSONObject(raw)
                if (json.optString("app") != "flashdrop") continue
                val ip   = json.getString("ip")
                if (ip == myIp) continue

                val port     = json.getInt("tcp_port")
                val deviceId = "$ip:$port"
                val device   = Device(
                    id      = deviceId,
                    name    = json.getString("device_name"),
                    type    = json.getString("device_type"),
                    ip      = ip,
                    tcpPort = port,
                )

                val isNew = synchronized(seenLock) {
                    val new = deviceId !in seen
                    seen[deviceId] = System.currentTimeMillis()
                    new
                }
                if (isNew) onDeviceFound(device)
                else synchronized(seenLock) { seen[deviceId] = System.currentTimeMillis() }

            } catch (e: Exception) { /* ignore */ }
        }
        sock.close()
    }

    private suspend fun reaper() {
        while (scope.isActive) {
            delay(3000)
            val now  = System.currentTimeMillis()
            val lost = synchronized(seenLock) {
                seen.entries
                    .filter { now - it.value > 6000 }
                    .map { it.key }
                    .also { ids -> ids.forEach { seen.remove(it) } }
            }
            lost.forEach { onDeviceLost(it) }
        }
    }

    private fun acquireMulticastLock() {
        val wm = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
        multiLock = wm.createMulticastLock("flashdrop").apply {
            setReferenceCounted(true)
            acquire()
        }
    }
}
