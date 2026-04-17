package com.flashdrop.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flashdrop.core.AppSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSave:   (AppSettings) -> Unit,
    onBack:   () -> Unit,
) {
    var deviceName  by remember { mutableStateOf(settings.deviceName) }
    var autoAccept  by remember { mutableStateOf(settings.autoAccept) }
    var manualConf  by remember { mutableStateOf(!settings.autoAccept) }
    var portStr     by remember { mutableStateOf(settings.tcpPort.toString()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 4.dp) {
                Button(
                    onClick = {
                        onSave(settings.copy(
                            deviceName = deviceName.ifBlank { "Android-Device" },
                            autoAccept = autoAccept,
                            tcpPort    = portStr.toIntOrNull() ?: 5006,
                        ))
                        onBack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(52.dp)
                ) {
                    Text("Save Settings")
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Device settings
            SectionLabel("DEVICE SETTINGS")

            OutlinedTextField(
                value         = deviceName,
                onValueChange = { deviceName = it },
                label         = { Text("Device Name") },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true
            )

            ToggleRow("Auto Accept Incoming Files", autoAccept) {
                autoAccept = it; if (it) manualConf = false
            }
            ToggleRow("Manual Confirmation Required", manualConf) {
                manualConf = it; if (it) autoAccept = false
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // Storage
            SectionLabel("STORAGE")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Default Save Location",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("Downloads/FlashDrop",
                        style = MaterialTheme.typography.bodyMedium)
                    Text("(fixed for prototype — configurable in v2)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // Network
            SectionLabel("NETWORK")
            OutlinedTextField(
                value         = portStr,
                onValueChange = { portStr = it },
                label         = { Text("Port Number") },
                supportingText = { Text("Default: 5006") },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // About
            SectionLabel("ABOUT")
            Text("App Version: 1.0.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Developer: FlashDrop",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}
