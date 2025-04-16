package com.packet.analyzer.data.datasource.root

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.packet.analyzer.data.util.PreferencesKeys
import com.packet.analyzer.data.util.RootStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton


private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class RootDataSource @Inject constructor(
    private val context: Context
) {
    private val dataStore = context.dataStore
    private val commandTimeoutMillis = 5000L

    val rootStatusFlow: Flow<RootStatus> = dataStore.data
        .catch { exception ->
            Log.e("RootDataSource", "Error reading DataStore", exception)
            if (exception is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val statusString = preferences[PreferencesKeys.ROOT_STATUS] ?: RootStatus.UNKNOWN.name
            try {
                RootStatus.valueOf(statusString)
            } catch (e: IllegalArgumentException) {
                Log.e("RootDataSource", "Invalid root status in DataStore: $statusString")
                RootStatus.UNKNOWN
            }
        }

    suspend fun checkOrRequestRootAccess(): Boolean = withContext(Dispatchers.IO) {
        Log.d("RootDataSource", "Checking/Requesting Root access...")
        var process: Process? = null
        var granted = false
        val startTime = System.currentTimeMillis()

        try {
            process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))

            val exitValue = withTimeoutOrNull(commandTimeoutMillis) {
                process.waitFor()
            }

            if (exitValue == null) {

                Log.w("RootDataSource", "Root command 'su -c id' timed out after $commandTimeoutMillis ms.")
                granted = false
                process?.destroyForcibly()
            } else {
                granted = (exitValue == 0)
                Log.d("RootDataSource", "Root command exit value: $exitValue. Granted: $granted")
            }

        } catch (e: IOException) {
            Log.e("RootDataSource", "IOException during root check", e)
            granted = false
        } catch (e: SecurityException) {
            Log.e("RootDataSource", "SecurityException during root check (SELinux?)", e)
            granted = false
        } catch (e: InterruptedException) {
            Log.w("RootDataSource", "Root check interrupted")
            Thread.currentThread().interrupt()
            granted = false
        } catch (e: TimeoutCancellationException) {
            Log.w("RootDataSource", "Root command timed out (caught TimeoutCancellationException).")
            granted = false
            process?.destroyForcibly()
        } catch (e: Exception) {
            Log.e("RootDataSource", "Unexpected error during root check", e)
            granted = false
        } finally {
            process?.destroy()
            val duration = System.currentTimeMillis() - startTime
            Log.d("RootDataSource", "Root check finished in ${duration}ms. Result: $granted")
        }

        val currentStatus = getCurrentRootStatus()
        val newStatus = if (granted) RootStatus.GRANTED else RootStatus.DENIED
        if (currentStatus != newStatus) {
            updateRootStatus(newStatus)
            Log.d("RootDataSource", "Root status updated to: $newStatus")
        } else {
            Log.d("RootDataSource", "Root status ($currentStatus) hasn't changed.")
        }

        return@withContext granted
    }

    private suspend fun updateRootStatus(newStatus: RootStatus) {
        try {
            dataStore.edit { settings ->
                settings[PreferencesKeys.ROOT_STATUS] = newStatus.name
            }
        } catch (e: Exception) {
            Log.e("RootDataSource", "Failed to update root status in DataStore", e)
        }
    }

    suspend fun getCurrentRootStatus(): RootStatus = withContext(Dispatchers.IO) {
        try {
            rootStatusFlow.first()
        } catch (e: Exception) {
            Log.e("RootDataSource", "Failed to get current root status from DataStore", e)
            RootStatus.UNKNOWN
        }
    }
}