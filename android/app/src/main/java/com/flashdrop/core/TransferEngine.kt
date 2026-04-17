package com.flashdrop.core

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Environment
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.*
import java.net.ServerSocket
import java.net.Socket

data class TransferProgress(
    val filename:  String,
    val pct:       Float,
    val speedMbps: Float,
    val etaSec:    Long,
)

class TransferEngine(
    private val context:            Context,
    private val settings:           AppSettings,
    private val onIncomingRequest:  suspend (info: JSONObject) -> Boolean,
    private val onReceiveProgress:  (TransferProgress) -> Unit,
    private val onReceiveDone:      (filename: String, path: String, peer: String) -> Unit,
    private val onReceiveFailed:    (filename: String, reason: String) -> Unit,
    private val onSendProgress:     (TransferProgress) -> Unit,
    private val onSendDone:         (filename: String, peer: String) -> Unit,
    private val onSendFailed:       (filename: String, reason: String) -> Unit,
) {
    private val scope      = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var cancelSend = false
    private var pauseSend  = false

    fun start() { scope.launch { serverLoop() } }
    fun stop()  { scope.cancel() }
    fun cancelSend()  { cancelSend = true }
    fun pauseSend()   { pauseSend  = true }
    fun resumeSend()  { pauseSend  = false }

    private suspend fun serverLoop() {
        val server = ServerSocket(settings.tcpPort)
        server.reuseAddress = true
        while (scope.isActive) {
            try {
                val conn = withContext(Dispatchers.IO) { server.accept() }
                scope.launch { handleIncoming(conn) }
            } catch (e: Exception) { }
        }
        server.close()
    }

    private suspend fun handleIncoming(conn: Socket) {
        val peer = conn.inetAddress.hostAddress ?: "unknown"
        var filename = "unknown"
        try {
            val inp = conn.getInputStream()
            val out = conn.getOutputStream()

            val metaLine = readRawLine(inp)
            val meta     = JSONObject(metaLine)
            if (meta.optString("type") != "FILE_REQUEST") { conn.close(); return }

            filename     = meta.getString("filename")
            val filesize = meta.getLong("filesize")
            val total    = meta.getInt("total_chunks")
            val peerName = meta.optString("peer_name", peer)

            val accepted = onIncomingRequest(meta)
            if (!accepted) {
                out.write("REJECTED\n".toByteArray())
                out.flush()
                conn.close()
                return
            }
            out.write("ACCEPTED\n".toByteArray())
            out.flush()

            // Save to public Downloads folder using MediaStore (visible in all file managers)
            val saveFile = saveToPublicDownloads(filename)
            if (saveFile == null) {
                onReceiveFailed(filename, "Could not create file in Downloads")
                conn.close()
                return
            }

            var received  = 0L
            val startTime = System.currentTimeMillis()

            saveFile.second.use { fout ->
                repeat(total) {
                    val sizeLine  = readRawLine(inp)
                    val chunkSize = sizeLine.trim().toInt()
                    val chunk     = readExact(inp, chunkSize)
                    fout.write(chunk)
                    received += chunkSize

                    val elapsed = (System.currentTimeMillis() - startTime) / 1000f
                    val speed   = if (elapsed > 0) received / elapsed / 1024f / 1024f else 0f
                    val pct     = if (filesize > 0) received * 100f / filesize else 0f
                    val eta     = if (speed > 0) ((filesize - received) / 1024f / 1024f / speed).toLong() else 0L
                    onReceiveProgress(TransferProgress(filename, pct, speed, eta))
                }
            }

            out.write("TRANSFER_COMPLETE\n".toByteArray())
            out.flush()
            onReceiveDone(filename, saveFile.first, peerName)

        } catch (e: Exception) {
            onReceiveFailed(filename, e.message ?: "Receive error")
        } finally {
            conn.close()
        }
    }

    fun sendFile(uri: android.net.Uri, destIp: String, destPort: Int, peerName: String) {
        scope.launch { doSend(uri, destIp, destPort, peerName) }
    }

    private suspend fun doSend(uri: android.net.Uri, destIp: String, destPort: Int, peerName: String) {
        cancelSend = false
        pauseSend  = false

        val cr       = context.contentResolver
        val filename = getFilename(uri)
        val filesize = getFilesize(uri)
        val chunkSz  = 5 * 1024 * 1024
        val total    = maxOf(1, Math.ceil(filesize.toDouble() / chunkSz).toInt())

        try {
            val conn = withContext(Dispatchers.IO) {
                Socket(destIp, destPort).also { it.soTimeout = 15000 }
            }
            val inp = conn.getInputStream()
            val out = conn.getOutputStream()

            val meta = JSONObject().apply {
                put("type",         "FILE_REQUEST")
                put("filename",     filename)
                put("filesize",     filesize)
                put("total_chunks", total)
                put("peer_name",    settings.deviceName)
            }
            out.write((meta.toString() + "\n").toByteArray())
            out.flush()

            val response = readRawLine(inp).trim()
            if (response != "ACCEPTED") {
                onSendFailed(filename, "Rejected by receiver")
                conn.close()
                return
            }

            var sent      = 0L
            val startTime = System.currentTimeMillis()
            val buf       = ByteArray(chunkSz)

            cr.openInputStream(uri)?.use { finp ->
                repeat(total) {
                    while (pauseSend) delay(100)
                    if (cancelSend) {
                        onSendFailed(filename, "Cancelled")
                        conn.close()
                        return@use
                    }
                    val read = finp.read(buf)
                    if (read <= 0) return@repeat
                    out.write("$read\n".toByteArray())
                    out.write(buf, 0, read)
                    out.flush()
                    sent += read

                    val elapsed = (System.currentTimeMillis() - startTime) / 1000f
                    val speed   = if (elapsed > 0) sent / elapsed / 1024f / 1024f else 0f
                    val pct     = if (filesize > 0) sent * 100f / filesize else 0f
                    val eta     = if (speed > 0) ((filesize - sent) / 1024f / 1024f / speed).toLong() else 0L
                    onSendProgress(TransferProgress(filename, pct, speed, eta))
                }
            }

            conn.soTimeout = 10000
            val ack = readRawLine(inp).trim()
            if (ack == "TRANSFER_COMPLETE") onSendDone(filename, peerName)
            else onSendFailed(filename, "No completion ACK")
            conn.close()

        } catch (e: Exception) {
            onSendFailed(filename, e.message ?: "Send error")
        }
    }

    /** Save to public Downloads/FlashDrop — visible in all file managers */
    private fun saveToPublicDownloads(filename: String): Pair<String, OutputStream>? {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(android.provider.MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/FlashDrop")
                    put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(
                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                val stream = context.contentResolver.openOutputStream(uri) ?: return null
                // Mark not pending after write
                val path = "Downloads/FlashDrop/$filename"
                Pair(path, object : OutputStream() {
                    override fun write(b: Int) = stream.write(b)
                    override fun write(b: ByteArray) = stream.write(b)
                    override fun write(b: ByteArray, off: Int, len: Int) = stream.write(b, off, len)
                    override fun flush() = stream.flush()
                    override fun close() {
                        stream.close()
                        val update = android.content.ContentValues().apply {
                            put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                        }
                        context.contentResolver.update(uri, update, null, null)
                    }
                })
            } else {
                // Android 9 and below — direct file write
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "FlashDrop"
                ).also { it.mkdirs() }
                val file = uniqueFile(dir, filename)
                Pair(file.absolutePath, FileOutputStream(file) as OutputStream)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun readRawLine(inp: InputStream): String {
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = inp.read()
            if (b == -1) throw IOException("Stream closed")
            if (b == '\n'.code) break
            if (b != '\r'.code) buf.write(b)
        }
        return buf.toString("UTF-8")
    }

    private fun readExact(inp: InputStream, size: Int): ByteArray {
        val buf = ByteArray(size)
        var off = 0
        while (off < size) {
            val n = inp.read(buf, off, size - off)
            if (n < 0) throw IOException("Stream ended early at $off/$size")
            off += n
        }
        return buf
    }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val dot  = name.lastIndexOf('.')
        val base = if (dot >= 0) name.substring(0, dot) else name
        val ext  = if (dot >= 0) name.substring(dot) else ""
        var i = 1
        while (f.exists()) { f = File(dir, "${base}_${i}${ext}"); i++ }
        return f
    }

    private fun getFilename(uri: android.net.Uri): String {
        var name = "file"
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && idx >= 0) name = c.getString(idx)
        }
        return name
    }

    private fun getFilesize(uri: android.net.Uri): Long {
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (c.moveToFirst() && idx >= 0) size = c.getLong(idx)
        }
        return size
    }
}
