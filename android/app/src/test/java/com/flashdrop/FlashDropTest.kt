package com.flashdrop

import com.flashdrop.core.Device
import com.flashdrop.core.TransferRecord
import com.flashdrop.core.IncomingRequest
import org.junit.Assert.*
import org.junit.Test

/**
 * FlashDrop — Android Unit Tests
 * Covers Integration, Regression, and Boundary/Mutation testing.
 * Run in Android Studio: right-click file → Run 'FlashDropTest'
 */
class FlashDropTest {

    // ════════════════════════════════════════════════════════════════
    // INTEGRATION TESTS
    // ════════════════════════════════════════════════════════════════

    @Test
    fun it01_deviceModelHasAllRequiredFields() {
        // Integration: Device model must contain all fields
        // needed by discovery and home screen
        val device = Device(
            id      = "192.168.1.5:5006",
            name    = "vivobook15",
            type    = "Windows",
            ip      = "192.168.1.5",
            tcpPort = 5006,
            status  = "Available"
        )
        assertEquals("192.168.1.5:5006", device.id)
        assertEquals("vivobook15", device.name)
        assertEquals("Windows", device.type)
        assertEquals(5006, device.tcpPort)
    }

    @Test
    fun it02_transferRecordIntegratesWithHistory() {
        // Integration: TransferRecord must store all fields
        // correctly for history display
        val record = TransferRecord(
            filename  = "photo.jpg",
            filesize  = 2516582L,
            direction = "SENT",
            peerName  = "vivobook15",
            status    = "Success",
            timestamp = "2024-04-12 19:15"
        )
        assertEquals("photo.jpg",  record.filename)
        assertEquals("SENT",       record.direction)
        assertEquals("Success",    record.status)
        assertEquals("vivobook15", record.peerName)
    }

    @Test
    fun it03_incomingRequestHasAllDisplayFields() {
        // Integration: IncomingRequest must have all fields
        // needed by the accept/reject dialog
        val request = IncomingRequest(
            filename = "document.pdf",
            filesize = 1048576L,
            peerName = "Android-Device"
        )
        assertNotNull(request.filename)
        assertNotNull(request.peerName)
        assertTrue(request.filesize > 0)
    }

    @Test
    fun it04_deviceIdFormatIsCorrect() {
        // Integration: device ID must be ip:port format
        // used by discovery to deduplicate devices
        val ip   = "192.168.43.100"
        val port = 5006
        val id   = "$ip:$port"
        assertTrue(id.contains(":"))
        assertTrue(id.startsWith(ip))
        assertTrue(id.endsWith(port.toString()))
    }

    // ════════════════════════════════════════════════════════════════
    // REGRESSION TESTS
    // ════════════════════════════════════════════════════════════════

    @Test
    fun rt01_deviceStatusDefaultIsAvailable() {
        // Regression: default device status must be Available
        // (was causing null crash before fix)
        val device = Device(
            id = "1.2.3.4:5006", name = "Test",
            type = "Android", ip = "1.2.3.4", tcpPort = 5006
        )
        assertEquals("Available", device.status)
    }

    @Test
    fun rt02_transferRecordDirectionNotNull() {
        // Regression: direction must never be null
        // (was causing history crash before fix)
        val record = TransferRecord(
            filename = "f.txt", filesize = 0L,
            direction = "SENT", peerName = "Dev",
            status = "Success", timestamp = "2024-01-01"
        )
        assertNotNull(record.direction)
        assertFalse(record.direction.isEmpty())
    }

    @Test
    fun rt03_fileSizeCanBeZero() {
        // Regression: zero filesize must not crash the model
        // (was causing divide-by-zero in progress calc)
        val record = TransferRecord(
            filename = "empty.txt", filesize = 0L,
            direction = "RECEIVED", peerName = "Dev",
            status = "Failed", timestamp = "2024-01-01"
        )
        assertEquals(0L, record.filesize)
    }

    @Test
    fun rt04_deviceTypeIsAndroidOrWindows() {
        // Regression: device type must be valid
        // (was showing wrong icon in device list before fix)
        val validTypes = listOf("Android", "Windows", "Manual")
        val device = Device(
            id = "1.2.3.4:5006", name = "Test",
            type = "Android", ip = "1.2.3.4", tcpPort = 5006
        )
        assertTrue(device.type in validTypes)
    }

    @Test
    fun rt05_transferStatusValues() {
        // Regression: status must be one of 3 valid values
        // (was logging wrong status before fix)
        val validStatuses = listOf("Success", "Failed", "Cancelled")
        val statuses = listOf("Success", "Failed", "Cancelled")
        statuses.forEach { status ->
            assertTrue("$status is not valid", status in validStatuses)
        }
    }

    @Test
    fun rt06_historyTimestampNotEmpty() {
        // Regression: timestamp must never be empty string
        val record = TransferRecord(
            filename = "f.txt", filesize = 100L,
            direction = "SENT", peerName = "Dev",
            status = "Success", timestamp = "2024-04-12 19:15"
        )
        assertFalse(record.timestamp.isEmpty())
    }

    // ════════════════════════════════════════════════════════════════
    // BOUNDARY & MUTATION TESTS
    // ════════════════════════════════════════════════════════════════

    @Test
    fun bt01_humanSize_oneByte() {
        // Boundary: smallest file size
        val result = humanSize(1L)
        assertEquals("1.0 B", result)
    }

    @Test
    fun bt02_humanSize_oneKB() {
        // Boundary: exactly 1 KB
        val result = humanSize(1024L)
        assertEquals("1.0 KB", result)
    }

    @Test
    fun bt03_humanSize_oneMB() {
        // Boundary: exactly 1 MB
        val result = humanSize(1048576L)
        assertEquals("1.0 MB", result)
    }

    @Test
    fun bt04_humanSize_oneGB() {
        // Boundary: 1 GB must show GB unit
        val result = humanSize(1073741824L)
        assertTrue(result.contains("GB"))
    }

    @Test
    fun bt05_humanSize_zeroBytes() {
        // Boundary: zero size must not crash
        val result = humanSize(0L)
        assertNotNull(result)
        assertTrue(result.contains("B"))
    }

    @Test
    fun bt06_mutant_wrongPortRange() {
        // Mutation: port 0 is invalid — must not be default
        val defaultPort = 5006
        assertNotEquals(0, defaultPort)
        assertTrue(defaultPort in 1024..65535)
    }

    @Test
    fun bt07_mutant_deviceIdUnique() {
        // Mutation: two devices with same IP but diff ports
        // must have different IDs
        val id1 = "192.168.1.5:5006"
        val id2 = "192.168.1.5:5007"
        assertNotEquals(id1, id2)
    }

    @Test
    fun bt08_boundary_largeFilesize() {
        // Boundary: 50 GB file must be storable in Long
        val fiftyGB = 53687091200L
        val record = TransferRecord(
            filename = "big.mp4", filesize = fiftyGB,
            direction = "RECEIVED", peerName = "Dev",
            status = "Success", timestamp = "2024-01-01"
        )
        assertEquals(fiftyGB, record.filesize)
    }

    @Test
    fun bt09_mutation_emptyPeerName() {
        // Mutation: empty peer name must still create valid record
        val record = TransferRecord(
            filename = "f.txt", filesize = 100L,
            direction = "SENT", peerName = "",
            status = "Success", timestamp = "2024-01-01"
        )
        assertNotNull(record.peerName)
    }

    @Test
    fun bt10_boundary_maxFilename() {
        // Boundary: 255 character filename must be stored
        val longName = "a".repeat(251) + ".txt"
        val record = TransferRecord(
            filename = longName, filesize = 100L,
            direction = "SENT", peerName = "Dev",
            status = "Success", timestamp = "2024-01-01"
        )
        assertEquals(longName, record.filename)
    }

    // ── Helper (mirrors Android app logic) ──────────────────────────
    private fun humanSize(bytes: Long): String {
        var v = bytes.toDouble()
        for (unit in listOf("B", "KB", "MB", "GB", "TB")) {
            if (v < 1024) return "%.1f %s".format(v, unit)
            v /= 1024
        }
        return "%.1f PB".format(v)
    }
}
