package com.flashdrop.ui.transfer

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flashdrop.TransferUiState
import com.flashdrop.core.IncomingRequest

// ── Transfer progress ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(
    state:          TransferUiState,
    onCancel:       () -> Unit,
    onPauseResume:  () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.mode == "Sending") "Transferring File"
                               else "Receiving File") }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // File info card
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(state.filename, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        "${state.mode} ${if (state.mode == "Sending") "to" else "from"} ${state.peerName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Progress bar + %
            LinearProgressIndicator(
                progress     = { state.pct / 100f },
                modifier     = Modifier.fillMaxWidth().height(10.dp),
                strokeCap    = androidx.compose.ui.graphics.StrokeCap.Round,
            )

            Text(
                "${state.pct.toInt()}%",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            // Speed + ETA
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors   = CardDefaults.cardColors(
                    MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Transfer Speed: ${"%.1f".format(state.speedMbps)} MB/s",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Time Remaining: ${humanTime(state.etaSec)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Status
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors   = CardDefaults.cardColors(
                    MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = when {
                        state.done   -> "Status: Complete"
                        state.paused -> "Status: Paused"
                        else         -> "Status: ${state.mode}"
                    },
                    modifier = Modifier.padding(14.dp),
                    style    = MaterialTheme.typography.bodyMedium
                )
            }

            if (state.done) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors   = CardDefaults.cardColors(
                        MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("Transfer complete!",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Buttons
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.mode == "Sending" && !state.done) {
                    OutlinedButton(
                        onClick  = onPauseResume,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text(if (state.paused) "Resume" else "Pause")
                    }
                }
                Button(
                    onClick  = onCancel,
                    enabled  = !state.done,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors   = ButtonDefaults.buttonColors(
                        MaterialTheme.colorScheme.error)
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

// ── Incoming request dialog ───────────────────────────────────────
@Composable
fun IncomingRequestDialog(
    request:  IncomingRequest,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onReject,
        icon  = { Icon(Icons.Default.FileDownload, contentDescription = null) },
        title = { Text("Incoming File Request") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text  = "A device wants to send you a file",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            text       = request.filename,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text  = "File Size: ${humanSize(request.filesize)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text  = "From: ${request.peerName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text  = "File will be saved to Downloads/FlashDrop",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Check, contentDescription = null,
                    modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Accept")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onReject, modifier = Modifier.fillMaxWidth()) {
                Text("Reject")
            }
        }
    )
}

private fun humanTime(sec: Long): String = when {
    sec <= 0   -> "—"
    sec < 60   -> "${sec}s"
    sec < 3600 -> "${sec / 60}m ${sec % 60}s"
    else       -> "${sec / 3600}h ${(sec % 3600) / 60}m"
}

private fun humanSize(bytes: Long): String {
    var v = bytes.toDouble()
    for (u in listOf("B", "KB", "MB", "GB", "TB")) {
        if (v < 1024) return "%.1f %s".format(v, u)
        v /= 1024
    }
    return "%.1f PB".format(v)
}
