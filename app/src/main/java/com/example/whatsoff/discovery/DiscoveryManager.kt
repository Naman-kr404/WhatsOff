package com.example.whatsoff.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.example.whatsoff.model.DiscoveredDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Dual-Engine Discovery Manager:
 * 1. Primary: Android NsdManager (mDNS / DNS-SD / Bonjour) using service type "_whatsoff._tcp."
 *    Bypasses router UDP broadcast restrictions & AP client isolation.
 * 2. Fallback: UDP JSON Broadcast on port 8888.
 *    Uses structured JSON payloads, eliminating the colon-splitting bug completely.
 */
class DiscoveryManager(private val context: Context) {

    companion object {
        private const val TAG = "WhatsOffDiscovery"
        private const val SERVICE_TYPE = "_whatsoff._tcp."
        private const val UDP_DISCOVERY_PORT = 8888
        private const val DEFAULT_CHAT_PORT = 5000
        private const val DEVICE_TIMEOUT_MS = 12_000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var discoveryJob: Job? = null
    private var udpSocket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    private val nsdManager: NsdManager? by lazy {
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    }

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val deviceMap = mutableMapOf<String, DiscoveredDevice>()

    var isHosting: Boolean = false
        set(value) {
            field = value
            if (value) {
                registerNsdService()
            } else {
                unregisterNsdService()
            }
            broadcastAnnouncement()
        }

    val deviceName: String by lazy {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model"
        }
    }

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            val ipList = mutableListOf<Pair<String, String>>()

            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue

                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val hostAddress = addr.hostAddress ?: continue
                        if (!hostAddress.startsWith("127.")) {
                            ipList.add(Pair(iface.name.lowercase(), hostAddress))
                        }
                    }
                }
            }

            val wifiIp = ipList.firstOrNull { it.first.contains("wlan") }
            if (wifiIp != null) return wifiIp.second

            val hotspotIp = ipList.firstOrNull {
                it.first.contains("ap") || it.first.contains("rndis") || it.first.contains("swlan")
            }
            if (hotspotIp != null) return hotspotIp.second

            return ipList.firstOrNull()?.second
        } catch (e: Exception) {
            Log.e(TAG, "Error obtaining local IP", e)
            return null
        }
    }

    fun startDiscovery() {
        if (discoveryJob?.isActive == true) return

        acquireMulticastLock()
        startNsdDiscovery()

        discoveryJob = scope.launch {
            try {
                val s = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(java.net.InetSocketAddress(UDP_DISCOVERY_PORT))
                }
                udpSocket = s

                launch { listenForUdpPackets(s) }
                launch { beaconAndCleanupLoop() }

                sendProbe()
            } catch (e: Exception) {
                Log.e(TAG, "UDP discovery socket failed to start", e)
            }
        }
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null

        stopNsdDiscovery()
        unregisterNsdService()

        try {
            udpSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing UDP socket", e)
        } finally {
            udpSocket = null
        }

        releaseMulticastLock()
    }

    fun refreshDiscovery() {
        sendProbe()
        restartNsdDiscovery()
    }

    fun sendProbe() {
        scope.launch {
            val myIp = getLocalIpAddress() ?: return@launch
            val json = JSONObject().apply {
                put("type", "PROBE")
                put("name", deviceName)
                put("ip", myIp)
            }
            broadcastUdpPacket(json.toString())
        }
    }

    fun broadcastAnnouncement() {
        scope.launch {
            sendBeaconNow()
        }
    }

    private suspend fun beaconAndCleanupLoop() {
        while (scope.isActive) {
            sendBeaconNow()
            cleanUpStaleDevices()
            delay(3000)
        }
    }

    private fun sendBeaconNow() {
        val myIp = getLocalIpAddress() ?: return
        val json = JSONObject().apply {
            put("type", "BEACON")
            put("name", deviceName)
            put("ip", myIp)
            put("port", DEFAULT_CHAT_PORT)
            put("isHosting", isHosting)
            put("ts", System.currentTimeMillis())
        }
        broadcastUdpPacket(json.toString())
    }

    private fun broadcastUdpPacket(message: String) {
        val s = udpSocket ?: return
        try {
            val data = message.toByteArray(Charsets.UTF_8)

            // 1. Send to global broadcast 255.255.255.255
            try {
                val packet = DatagramPacket(data, data.size, InetAddress.getByName("255.255.255.255"), UDP_DISCOVERY_PORT)
                s.send(packet)
            } catch (e: Exception) {
                Log.w(TAG, "Global broadcast failed", e)
            }

            // 2. Send to interface broadcast addresses
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue

                for (ifaceAddr in iface.interfaceAddresses) {
                    val broadcast = ifaceAddr.broadcast
                    if (broadcast != null) {
                        try {
                            val packet = DatagramPacket(data, data.size, broadcast, UDP_DISCOVERY_PORT)
                            s.send(packet)
                        } catch (ignored: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in broadcast", e)
        }
    }

    private fun listenForUdpPackets(s: DatagramSocket) {
        val buffer = ByteArray(2048)
        while (scope.isActive && !s.isClosed) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                s.receive(packet)

                val message = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                val senderIp = packet.address.hostAddress ?: continue
                val myIp = getLocalIpAddress()

                if (senderIp == myIp || senderIp == "127.0.0.1") continue

                handleReceivedUdpJson(message, senderIp)
            } catch (e: Exception) {
                if (scope.isActive && !s.isClosed) {
                    Log.e(TAG, "UDP receive error", e)
                }
                break
            }
        }
    }

    private fun handleReceivedUdpJson(message: String, senderIp: String) {
        try {
            val json = JSONObject(message)
            when (json.optString("type")) {
                "BEACON" -> {
                    val peerName = json.optString("name", "Unknown Peer")
                    val peerIp = json.optString("ip", senderIp).ifBlank { senderIp }
                    val peerPort = json.optInt("port", DEFAULT_CHAT_PORT)
                    val peerHosting = json.optBoolean("isHosting", false)

                    recordDiscoveredDevice(
                        DiscoveredDevice(
                            name = peerName,
                            ip = peerIp,
                            port = peerPort,
                            isHosting = peerHosting,
                            lastSeen = System.currentTimeMillis()
                        )
                    )
                }
                "PROBE" -> {
                    sendBeaconNow()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Ignoring non-JSON UDP message: $message")
        }
    }

    private fun recordDiscoveredDevice(device: DiscoveredDevice) {
        synchronized(deviceMap) {
            deviceMap[device.ip] = device
            updateDiscoveredDevicesState()
        }
    }

    private fun cleanUpStaleDevices() {
        val now = System.currentTimeMillis()
        synchronized(deviceMap) {
            val iterator = deviceMap.entries.iterator()
            var modified = false
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (now - entry.value.lastSeen > DEVICE_TIMEOUT_MS) {
                    iterator.remove()
                    modified = true
                }
            }
            if (modified) {
                updateDiscoveredDevicesState()
            }
        }
    }

    private fun updateDiscoveredDevicesState() {
        _discoveredDevices.value = deviceMap.values.sortedWith(
            compareByDescending<DiscoveredDevice> { it.isHosting }
                .thenBy { it.name }
        )
    }

    // ==========================================
    // Android NsdManager (DNS-SD / mDNS) Engine
    // ==========================================

    private fun registerNsdService() {
        if (registrationListener != null) return
        try {
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = "WhatsOff-$deviceName"
                serviceType = SERVICE_TYPE
                port = DEFAULT_CHAT_PORT
            }

            registrationListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(service: NsdServiceInfo) {
                    Log.d(TAG, "mDNS Service successfully registered: ${service.serviceName}")
                }
                override fun onRegistrationFailed(service: NsdServiceInfo, errorCode: Int) {
                    Log.e(TAG, "mDNS Registration failed: $errorCode")
                    registrationListener = null
                }
                override fun onServiceUnregistered(service: NsdServiceInfo) {
                    Log.d(TAG, "mDNS Service unregistered")
                    registrationListener = null
                }
                override fun onUnregistrationFailed(service: NsdServiceInfo, errorCode: Int) {
                    Log.e(TAG, "mDNS Unregistration failed: $errorCode")
                    registrationListener = null
                }
            }

            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed registering NSD service", e)
        }
    }

    private fun unregisterNsdService() {
        registrationListener?.let {
            try {
                nsdManager?.unregisterService(it)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering NSD service", e)
            } finally {
                registrationListener = null
            }
        }
    }

    private fun startNsdDiscovery() {
        if (discoveryListener != null) return
        try {
            discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {
                    Log.d(TAG, "mDNS Discovery started for $regType")
                }
                override fun onServiceFound(service: NsdServiceInfo) {
                    Log.d(TAG, "mDNS Service found: ${service.serviceName}")
                    if (service.serviceType.contains("whatsoff", ignoreCase = true)) {
                        resolveNsdService(service)
                    }
                }
                override fun onServiceLost(service: NsdServiceInfo) {
                    Log.d(TAG, "mDNS Service lost: ${service.serviceName}")
                }
                override fun onDiscoveryStopped(serviceType: String) {
                    Log.d(TAG, "mDNS Discovery stopped")
                    discoveryListener = null
                }
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.e(TAG, "mDNS Start discovery failed: $errorCode")
                    discoveryListener = null
                }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.e(TAG, "mDNS Stop discovery failed: $errorCode")
                    discoveryListener = null
                }
            }

            nsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed starting NSD discovery", e)
        }
    }

    private fun resolveNsdService(service: NsdServiceInfo) {
        try {
            nsdManager?.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "mDNS resolve failed: $errorCode")
                }
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val host = serviceInfo.host?.hostAddress ?: return
                    val myIp = getLocalIpAddress()
                    if (host == myIp || host == "127.0.0.1") return

                    val cleanName = serviceInfo.serviceName.removePrefix("WhatsOff-")
                    recordDiscoveredDevice(
                        DiscoveredDevice(
                            name = cleanName,
                            ip = host,
                            port = serviceInfo.port,
                            isHosting = true,
                            lastSeen = System.currentTimeMillis()
                        )
                    )
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Failed resolving NSD service", e)
        }
    }

    private fun stopNsdDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager?.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping NSD discovery", e)
            } finally {
                discoveryListener = null
            }
        }
    }

    private fun restartNsdDiscovery() {
        stopNsdDiscovery()
        startNsdDiscovery()
    }

    private fun acquireMulticastLock() {
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("WhatsOffMulticastLock")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire MulticastLock", e)
        }
    }

    private fun releaseMulticastLock() {
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not release MulticastLock", e)
        } finally {
            multicastLock = null
        }
    }
}
