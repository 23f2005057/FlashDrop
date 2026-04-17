package com.flashdrop

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flashdrop.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

// ── UI State ──────────────────────────────────────────────────────
sealed class Screen {
    object Home      : Screen()
    object FilePicker: Screen()
    object Transfer  : Screen()
    object History   : Screen()
    object Settings  : Screen()
}

data class TransferUiState(
    val mode:      String  = "Sending",   // "Sending" / "Receiving"
    val filename:  String  = "",
    val filesize:  Long    = 0L,
    val peerName:  String  = "",
    val pct:       Float   = 0f,
    val speedMbps: Float   = 0f,
    val etaSec:    Long    = 0L,
    val done:      Boolean = false,
    val paused:    Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx         = app.applicationContext
    val settingsRepo        = SettingsRepo(ctx)
    val historyRepo         = HistoryRepo(ctx)

    // ── State flows ───────────────────────────────────────────────
    private val _screen    = MutableStateFlow<Screen>(Screen.Home)
    val screen             = _screen.asStateFlow()

    private val _devices   = MutableStateFlow<List<Device>>(emptyList())
    val devices            = _devices.asStateFlow()

    private val _wifiOk    = MutableStateFlow(false)
    val wifiOk             = _wifiOk.asStateFlow()

    private val _transfer  = MutableStateFlow(TransferUiState())
    val transfer           = _transfer.asStateFlow()

    private val _incoming  = MutableStateFlow<IncomingRequest?>(null)
    val incoming           = _incoming.asStateFlow()

    private val _history   = MutableStateFlow<List<TransferRecord>>(emptyList())
    val history            = _history.asStateFlow()

    private val _settings  = MutableStateFlow(AppSettings())
    val settings           = _settings.asStateFlow()

    // For file picker
    private val _selectedDevice = MutableStateFlow<Device?>(null)
    val selectedDevice          = _selectedDevice.asStateFlow()

    private var discovery: Discovery?      = null
    private var engine:    TransferEngine? = null

    // For blocking the incoming request on the server thread
    private var incomingDeferred: CompletableDeferred<Boolean>? = null

    init {
        viewModelScope.launch {
            settingsRepo.settings.collect { s ->
                _settings.value = s
                restartServices(s)
            }
        }
        loadHistory()
        checkWifi()
    }

    // ── Services ──────────────────────────────────────────────────
    private fun restartServices(s: AppSettings) {
        discovery?.stop()
        engine?.stop()

        discovery = Discovery(ctx, s,
            onDeviceFound = { dev ->
                _devices.update { list ->
                    if (list.none { it.id == dev.id }) list + dev else list
                }
                _wifiOk.value = true
            },
            onDeviceLost = { id ->
                _devices.update { list -> list.filter { it.id != id } }
            }
        ).also { it.start() }

        engine = TransferEngine(
            context           = ctx,
            settings          = s,
            onIncomingRequest = { meta -> handleIncomingRequest(meta) },
            onReceiveProgress = { p ->
                viewModelScope.launch(Dispatchers.Main) {
                    _transfer.update { it.copy(pct = p.pct, speedMbps = p.speedMbps, etaSec = p.etaSec) }
                }
            },
            onReceiveDone     = { filename, path, peer ->
                viewModelScope.launch(Dispatchers.Main) {
                    _transfer.update { it.copy(done = true) }
                    addHistory(filename, 0L, "RECEIVED", peer, "Success")
                    delay(2000); _screen.value = Screen.Home
                }
            },
            onReceiveFailed   = { filename, reason ->
                viewModelScope.launch(Dispatchers.Main) {
                    addHistory(filename, 0L, "RECEIVED", "unknown", "Failed")
                    _screen.value = Screen.Home
                }
            },
            onSendProgress    = { p ->
                viewModelScope.launch(Dispatchers.Main) {
                    _transfer.update { it.copy(pct = p.pct, speedMbps = p.speedMbps, etaSec = p.etaSec) }
                }
            },
            onSendDone        = { filename, peer ->
                viewModelScope.launch(Dispatchers.Main) {
                    _transfer.update { it.copy(done = true) }
                    addHistory(filename, 0L, "SENT", peer, "Success")
                    delay(2000); _screen.value = Screen.Home
                }
            },
            onSendFailed      = { filename, reason ->
                viewModelScope.launch(Dispatchers.Main) {
                    addHistory(filename, 0L, "SENT", "unknown",
                        if ("Cancel" in reason) "Cancelled" else "Failed")
                    _screen.value = Screen.Home
                }
            }
        ).also { it.start() }
    }

    // ── Incoming request (called from server IO thread) ───────────
    private suspend fun handleIncomingRequest(meta: JSONObject): Boolean {
        val req = IncomingRequest(
            filename = meta.getString("filename"),
            filesize = meta.getLong("filesize"),
            peerName = meta.optString("peer_name", "Unknown"),
        )

        if (_settings.value.autoAccept) return true

        val deferred = CompletableDeferred<Boolean>()
        incomingDeferred = deferred
        _incoming.value  = req
        _transfer.value  = TransferUiState(
            mode     = "Receiving",
            filename = req.filename,
            filesize = req.filesize,
            peerName = req.peerName,
        )

        return deferred.await()
    }

    fun acceptIncoming() {
        _incoming.value = null
        _screen.value   = Screen.Transfer
        incomingDeferred?.complete(true)
    }

    fun rejectIncoming() {
        _incoming.value = null
        incomingDeferred?.complete(false)
    }

    // ── Navigation ────────────────────────────────────────────────
    fun navigateTo(screen: Screen) { _screen.value = screen }

    fun selectDevice(device: Device) {
        _selectedDevice.value = device
        _screen.value = Screen.FilePicker
    }

    fun sendFiles(uris: List<Uri>) {
        val device = _selectedDevice.value ?: return
        if (uris.isEmpty()) return

        viewModelScope.launch {
            for (uri in uris) {
                val filename = getFilename(uri)
                _transfer.value = TransferUiState(
                    mode     = "Sending",
                    filename = filename,
                    filesize = 0L,
                    peerName = device.name,
                )
                _screen.value = Screen.Transfer

                val done = CompletableDeferred<Unit>()
                // Override callbacks for this file to signal completion
                engine?.sendFile(uri, device.ip, device.tcpPort, device.name)
                // Wait a moment for the transfer screen to show progress
                delay(500)
            }
        }
    }

    fun cancelTransfer() {
        engine?.cancelSend()
        _screen.value = Screen.Home
    }

    fun pauseTransfer()  { engine?.pauseSend();  _transfer.update { it.copy(paused = true)  } }
    fun resumeTransfer() { engine?.resumeSend(); _transfer.update { it.copy(paused = false) } }

    // ── History ───────────────────────────────────────────────────
    fun loadHistory() { _history.value = historyRepo.getAll() }

    fun clearHistory() {
        historyRepo.clear()
        _history.value = emptyList()
    }

    private fun addHistory(filename: String, size: Long,
                            direction: String, peer: String, status: String) {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            .format(Date())
        historyRepo.add(TransferRecord(filename, size, direction, peer, status, ts))
        loadHistory()
    }

    // ── Wifi check ────────────────────────────────────────────────
    private fun checkWifi() {
        val cm = ctx.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                as android.net.ConnectivityManager
        val net = cm.activeNetwork
        val cap = cm.getNetworkCapabilities(net)
        _wifiOk.value = cap?.hasTransport(
            android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    fun saveSettings(s: AppSettings) {
        viewModelScope.launch { settingsRepo.save(s) }
    }

    fun manualConnect(ip: String, port: Int) {
        val device = Device(
            id      = "$ip:$port",
            name    = ip,
            type    = "Manual",
            ip      = ip,
            tcpPort = port,
        )
        selectDevice(device)
    }

    private fun getFilename(uri: Uri): String {
        var name = "file"
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && idx >= 0) name = c.getString(idx)
        }
        return name
    }

    override fun onCleared() {
        super.onCleared()
        discovery?.stop()
        engine?.stop()
    }
}
