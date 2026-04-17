package com.flashdrop.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class HistoryRepo(context: Context) {

    private val prefs = context.getSharedPreferences("flashdrop_history", Context.MODE_PRIVATE)

    fun add(record: TransferRecord) {
        val arr  = load()
        val json = JSONObject().apply {
            put("filename",  record.filename)
            put("filesize",  record.filesize)
            put("direction", record.direction)
            put("peerName",  record.peerName)
            put("status",    record.status)
            put("timestamp", record.timestamp)
        }
        arr.put(0, json)
        prefs.edit().putString("history", arr.toString()).apply()
    }

    fun getAll(): List<TransferRecord> {
        val arr  = load()
        val list = mutableListOf<TransferRecord>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(TransferRecord(
                filename  = obj.getString("filename"),
                filesize  = obj.getLong("filesize"),
                direction = obj.getString("direction"),
                peerName  = obj.getString("peerName"),
                status    = obj.getString("status"),
                timestamp = obj.getString("timestamp"),
            ))
        }
        return list
    }

    fun clear() {
        prefs.edit().remove("history").apply()
    }

    private fun load(): JSONArray {
        val raw = prefs.getString("history", null) ?: return JSONArray()
        return try { JSONArray(raw) } catch (e: Exception) { JSONArray() }
    }
}
