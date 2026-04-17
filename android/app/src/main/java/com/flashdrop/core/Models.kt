package com.flashdrop.core

data class Device(
    val id:         String,   // "ip:port"
    val name:       String,
    val type:       String,   // "Android" / "Windows"
    val ip:         String,
    val tcpPort:    Int,
    val status:     String = "Available",   // Available / Busy / Connecting
)

data class TransferRecord(
    val filename:   String,
    val filesize:   Long,
    val direction:  String,   // "SENT" / "RECEIVED"
    val peerName:   String,
    val status:     String,   // "Success" / "Failed" / "Cancelled"
    val timestamp:  String,
)

data class IncomingRequest(
    val filename: String,
    val filesize: Long,
    val peerName: String,
)
