package com.example.robotarmcontroller.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "connection_preferences")

@Singleton
class ConnectionPreferences @Inject constructor(
    private val context: Context
) {
    companion object {
        private val LAST_CONNECTED_DEVICE_ADDRESS = stringPreferencesKey("last_connected_device_address")
        private val LAST_CONNECTED_DEVICE_NAME = stringPreferencesKey("last_connected_device_name")
        private val AUTO_RECONNECT_ENABLED = booleanPreferencesKey("auto_reconnect_enabled")
        private val CONNECTION_TIMESTAMP = stringPreferencesKey("connection_timestamp")
    }

    suspend fun saveConnectionInfo(
        deviceAddress: String,
        deviceName: String? = null
    ) {
        context.dataStore.edit { preferences ->
            preferences[LAST_CONNECTED_DEVICE_ADDRESS] = deviceAddress
            if (deviceName != null) {
                preferences[LAST_CONNECTED_DEVICE_NAME] = deviceName
            }
            preferences[CONNECTION_TIMESTAMP] = System.currentTimeMillis().toString()
        }
    }


    suspend fun clearConnectionInfo() {
        context.dataStore.edit { preferences ->
            preferences.remove(LAST_CONNECTED_DEVICE_ADDRESS)
            preferences.remove(LAST_CONNECTED_DEVICE_NAME)
            preferences.remove(CONNECTION_TIMESTAMP)
        }
    }

    val lastConnectedDeviceAddress: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[LAST_CONNECTED_DEVICE_ADDRESS] }

    val lastConnectedDeviceName: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[LAST_CONNECTED_DEVICE_NAME] }

    val autoReconnectEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[AUTO_RECONNECT_ENABLED] ?: true }

    suspend fun setAutoReconnectEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_RECONNECT_ENABLED] = enabled
        }
    }

    fun getLastConnectionInfo(): Flow<Pair<String?, String?>> = context.dataStore.data
        .map { preferences ->
            Pair(
                preferences[LAST_CONNECTED_DEVICE_ADDRESS],
                preferences[LAST_CONNECTED_DEVICE_NAME]
            )
        }
}