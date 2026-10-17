package com.example.whatsoff.discovery

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryPayloadTest {

    @Test
    fun testDiscoveryBeaconWithSpecialCharactersAndColons() {
        val trickyDeviceName = "Pixel 8: Pro (Naman's : 5GHz : Hotspot) 🚀"
        val ip = "192.168.1.15"
        val port = 5000
        val isHosting = true

        val json = JSONObject().apply {
            put("type", "BEACON")
            put("name", trickyDeviceName)
            put("ip", ip)
            put("port", port)
            put("isHosting", isHosting)
            put("ts", 1710000000L)
        }

        val jsonString = json.toString()
        val parsed = JSONObject(jsonString)

        assertEquals("BEACON", parsed.getString("type"))
        assertEquals(trickyDeviceName, parsed.getString("name"))
        assertEquals(ip, parsed.getString("ip"))
        assertEquals(port, parsed.getInt("port"))
        assertTrue(parsed.getBoolean("isHosting"))
    }

    @Test
    fun testProbePayload() {
        val json = JSONObject().apply {
            put("type", "PROBE")
            put("name", "Device:1")
            put("ip", "10.0.0.1")
        }

        val parsed = JSONObject(json.toString())
        assertEquals("PROBE", parsed.getString("type"))
        assertEquals("Device:1", parsed.getString("name"))
        assertEquals("10.0.0.1", parsed.getString("ip"))
    }
}
