package com.flashdrop.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flashdrop.core.TransferRecord

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    records:    List<TransferRecord>,
    onBack:     () -> Unit,
    onClear:    () -> Unit,
) {
    var tab by remember { mutableStateOf("Sent") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transfer History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear")
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 4.dp) {
                OutlinedButton(
                    onClick  = onClear,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(48.dp)
                ) {
                    Text("Clear History")
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Tab row
            TabRow(selectedTabIndex = if (tab == "Sent") 0 else 1) {
                Tab(selected = tab == "Sent",
                    onClick  = { tab = "Sent" },
                    text     = { Text("Sent") })
                Tab(selected = tab == "Received",
                    onClick  = { tab = "Received" },
                    text     = { Text("Received") })
            }

            val filtered = records.filter { it.direction == tab.uppercase() }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.History, contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(12.dp))
                        Text("No ${tab.lowercase()} files yet",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                    contentPadding      = PaddingValues(vertical = 12.dp)
                ) {
                    items(filtered, key = { it.timestamp + it.filename }) { record ->
                        HistoryRow(record)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryRow(record: TransferRecord) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(record.filename, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                "${record.timestamp}  ·  ${record.peerName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            record.status,
            color = when (record.status) {
                "Success"   -> Color(0xFF2E7D32)
                "Failed"    -> MaterialTheme.colorScheme.error
                else        -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}
