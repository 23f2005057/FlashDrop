package com.flashdrop.core

import android.content.Context
import android.net.wifi.WifiManager
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "flashdrop_settings")

data class AppSettings(
    val deviceName:  String  = "Android-Device",
    val tcpPort:     Int     = 5006,
    val udpPort:     Int     = 5005,
    val autoAccept:  Boolean = false,
    val saveFolder:  String  = "Downloads/FlashDrop",
)

object SettingsKeys {
    val DEVICE_NAME  = stringPreferencesKey("device_name")
    val TCP_PORT     = intPreferencesKey("tcp_port")
    val UDP_PORT     = intPreferencesKey("udp_port")
    val AUTO_ACCEPT  = booleanPreferencesKey("auto_accept")
    val SAVE_FOLDER  = stringPreferencesKey("save_folder")
}

class SettingsRepo(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            deviceName = prefs[SettingsKeys.DEVICE_NAME]  ?: android.os.Build.MODEL,
            tcpPort    = prefs[SettingsKeys.TCP_PORT]     ?: 5006,
            udpPort    = prefs[SettingsKeys.UDP_PORT]     ?: 5005,
            autoAccept = prefs[SettingsKeys.AUTO_ACCEPT]  ?: false,
            saveFolder = prefs[SettingsKeys.SAVE_FOLDER]  ?: "Downloads/FlashDrop",
        )
    }

    suspend fun save(s: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[SettingsKeys.DEVICE_NAME] = s.deviceName
            prefs[SettingsKeys.TCP_PORT]    = s.tcpPort
            prefs[SettingsKeys.UDP_PORT]    = s.udpPort
            prefs[SettingsKeys.AUTO_ACCEPT] = s.autoAccept
            prefs[SettingsKeys.SAVE_FOLDER] = s.saveFolder
        }
    }
}
