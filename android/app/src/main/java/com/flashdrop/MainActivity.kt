package com.flashdrop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.lifecycleScope
import com.flashdrop.core.AppSettings
import com.flashdrop.core.IncomingRequest
import com.flashdrop.ui.history.HistoryScreen
import com.flashdrop.ui.home.FilePickerScreen
import com.flashdrop.ui.home.HomeScreen
import com.flashdrop.ui.settings.SettingsScreen
import com.flashdrop.ui.transfer.IncomingRequestDialog
import com.flashdrop.ui.transfer.TransferScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FlashDropRoot() }
    }
}

@Composable
fun FlashDropRoot(vm: MainViewModel = viewModel()) {
    val screen   by vm.screen.collectAsState()
    val devices  by vm.devices.collectAsState()
    val wifiOk   by vm.wifiOk.collectAsState()
    val transfer by vm.transfer.collectAsState()
    val incoming by vm.incoming.collectAsState()
    val history  by vm.history.collectAsState()
    val settings by vm.settings.collectAsState()
    val selDev   by vm.selectedDevice.collectAsState()

    // Show incoming request dialog on top of whatever screen is showing
    incoming?.let { req ->
        IncomingRequestDialog(
            request  = req,
            onAccept = { vm.acceptIncoming() },
            onReject = { vm.rejectIncoming() }
        )
    }

    when (screen) {
        Screen.Home -> HomeScreen(
            wifiOk        = wifiOk,
            devices       = devices,
            onDeviceTap   = { vm.selectDevice(it) },
            onRefresh     = { /* discovery is automatic */ },
            onManualIp    = { ip, port -> vm.manualConnect(ip, port) },
            onHistoryTap  = { vm.navigateTo(Screen.History) },
            onSettingsTap = { vm.navigateTo(Screen.Settings) },
        )

        Screen.FilePicker -> selDev?.let { device ->
            FilePickerScreen(
                device  = device,
                onSend  = { uris -> vm.sendFiles(uris) },
                onBack  = { vm.navigateTo(Screen.Home) },
            )
        }

        Screen.Transfer -> TransferScreen(
            state         = transfer,
            onCancel      = { vm.cancelTransfer() },
            onPauseResume = {
                if (transfer.paused) vm.resumeTransfer()
                else vm.pauseTransfer()
            },
        )

        Screen.History -> HistoryScreen(
            records  = history,
            onBack   = { vm.navigateTo(Screen.Home) },
            onClear  = { vm.clearHistory() },
        )

        Screen.Settings -> SettingsScreen(
            settings = settings,
            onSave   = { s ->
                vm.saveSettings(s)
            },
            onBack   = { vm.navigateTo(Screen.Home) },
        )
    }
}
