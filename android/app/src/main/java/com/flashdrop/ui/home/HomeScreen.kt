package com.flashdrop.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flashdrop.core.Device

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    wifiOk:       Boolean,
    devices:      List<Device>,
    onDeviceTap:  (Device) -> Unit,
    onRefresh:    () -> Unit,
    onManualIp:   (String, Int) -> Unit,
    onHistoryTap: () -> Unit,
    onSettingsTap:() -> Unit,
) {
    var showManualDialog by remember { mutableStateOf(false) }

    if (showManualDialog) {
        ManualIpDialog(
            onConfirm = { ip, port -> onManualIp(ip, port); showManualDialog = false },
            onDismiss = { showManualDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FlashDrop", fontWeight = FontWeight.Bold) },
                actions = {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (wifiOk) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = if (wifiOk) "WiFi: Connected" else "WiFi: Disconnected",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 12.sp,
                            color = if (wifiOk) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    IconButton(onClick = onHistoryTap) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                    IconButton(onClick = onSettingsTap) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                "AVAILABLE DEVICES",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (devices.isEmpty()) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.WifiFind,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.outlineVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Scanning for devices…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Make sure other device has FlashDrop open",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(devices, key = { it.id }) { device ->
                        DeviceCard(device = device, onClick = { onDeviceTap(device) })
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Refresh")
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = { showManualDialog = true },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Enter IP Manually")
            }
        }
    }
}

@Composable
fun DeviceCard(device: Device, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (device.type == "Android")
                    Icons.Default.PhoneAndroid
                else Icons.Default.Computer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(device.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(
                    "${device.type} | ${device.status}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "IP: ${device.ip}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
fun ManualIpDialog(onConfirm: (String, Int) -> Unit, onDismiss: () -> Unit) {
    var ip   by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("5006") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("Enter IP Manually") },
        text    = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = ip, onValueChange = { ip = it },
                    label = { Text("IP Address") },
                    placeholder = { Text("192.168.1.x") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = port, onValueChange = { port = it },
                    label = { Text("Port") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (ip.isNotBlank())
                    onConfirm(ip.trim(), port.toIntOrNull() ?: 5006)
            }) { Text("Connect") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
