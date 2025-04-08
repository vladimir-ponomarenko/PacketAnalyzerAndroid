package com.packet.analyzer.data.repository

import android.content.Context
import android.util.Log
import com.packet.analyzer.data.datasource.PcapdDataSource
import com.packet.analyzer.data.datasource.app.AppInfoDataSource
import com.packet.analyzer.data.datasource.native.JniBridge
import com.packet.analyzer.data.datasource.root.RootDataSource
import com.packet.analyzer.data.model.AppInfo
import com.packet.analyzer.data.model.PcapHeaderInfo
import com.packet.analyzer.data.util.RootStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class TrafficRepositoryImpl @Inject constructor(
    private val context: Context,
    private val rootDataSource: RootDataSource,
    private val appInfoDataSource: AppInfoDataSource
) : TrafficRepository {

    private val pcapdDataSource: PcapdDataSource = PcapdDataSource(context, rootDataSource)

    private val _isCapturing = MutableStateFlow(false)
    override fun getCaptureStatusUpdates(): StateFlow<Boolean> = _isCapturing.asStateFlow()

    val packetHeaderFlow: SharedFlow<PcapHeaderInfo> get() = pcapdDataSource.packetHeaderFlow

    override fun getRootStatusUpdates(): Flow<RootStatus> {
        return rootDataSource.rootStatusFlow
    }

    override suspend fun checkOrRequestRootAccess(): Boolean {
        Log.d("TrafficRepository", "Delegating root check/request to RootDataSource")
        return rootDataSource.checkOrRequestRootAccess()
    }

    override suspend fun getAppList(includeSystemApps: Boolean): List<AppInfo> {
        Log.d("TrafficRepository", "Fetching app list (includeSystemApps: $includeSystemApps)")
        return appInfoDataSource.getInstalledApps(includeSystemApps)
    }

    override suspend fun startCapture() {
        if (_isCapturing.value) {
            Log.w("TrafficRepository", "Capture is already running.")
            return
        }
        Log.d("TrafficRepository", "Attempting to start capture via PcapdDataSource...")
        try {
            val started = pcapdDataSource.startCapture()
            if (started) {
                _isCapturing.value = true
                Log.i("TrafficRepository", "Capture start initiated successfully.")
            } else {
                _isCapturing.value = false
                Log.e("TrafficRepository", "Capture start initiation failed (check DataSource logs).")
                throw RuntimeException("Failed to start capture")
            }
        } catch (e: Exception) {
            Log.e("TrafficRepository", "Exception during startCapture", e)
            _isCapturing.value = false
            throw e
        }
    }

    override suspend fun stopCapture() {
        if (!_isCapturing.value) {
            Log.w("TrafficRepository", "Capture is not running")
            return
        }
        Log.d("TrafficRepository", "Stopping capture via PcapdDataSource...")
        try {
            pcapdDataSource.stopCapture()
            _isCapturing.value = false
            Log.i("TrafficRepository", "Capture stop initiated.")
        } catch (e: Exception) {
            Log.e("TrafficRepository", "Exception during stopCapture", e)
            _isCapturing.value = false
            throw e
        }
    }

    fun cleanupDataSource() {
        Log.i("TrafficRepositoryImpl", "Cleanup requested for PcapdDataSource")
        pcapdDataSource.cleanup()
    }
}