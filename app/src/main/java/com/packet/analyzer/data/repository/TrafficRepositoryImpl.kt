package com.packet.analyzer.data.repository

import android.content.Context
import android.util.Log
import com.packet.analyzer.data.datasource.root.RootDataSource // Импортируем RootDataSource
import com.packet.analyzer.data.util.RootStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class TrafficRepositoryImpl @Inject constructor(
    private val context: Context,
    private val rootDataSource: RootDataSource
    // private val nativeDataSource: NativeTrafficDataSource
) : TrafficRepository {

    private val _isCapturing = MutableStateFlow(false)
    override fun getCaptureStatusUpdates(): StateFlow<Boolean> = _isCapturing.asStateFlow()

    override fun getRootStatusUpdates(): Flow<RootStatus> {
        return rootDataSource.rootStatusFlow
    }

    override suspend fun checkOrRequestRootAccess(): Boolean {
        Log.d("TrafficRepository", "Delegating root check/request to RootDataSource")
        return rootDataSource.checkOrRequestRootAccess()
    }

    override suspend fun startCapture() = withContext(Dispatchers.IO) {
        val currentRootStatus = rootDataSource.getCurrentRootStatus()
        if (currentRootStatus != RootStatus.GRANTED) {
            Log.e("TrafficRepository", "Cannot start capture: Root access not granted.")
            throw IllegalStateException("Root access not granted")
        }

        if (_isCapturing.value) {
            Log.w("TrafficRepository", "Capture already running")
            return@withContext
        }
        Log.d("TrafficRepository", "Starting capture...")
        // nativeDataSource.startPcapdListener()
        delay(1000)
        _isCapturing.value = true
        Log.d("TrafficRepository", "Capture started successfully (simulation)")
    }

    override suspend fun stopCapture() = withContext(Dispatchers.IO) {
        if (!_isCapturing.value) {
            Log.w("TrafficRepository", "Capture is not running")
            return@withContext
        }
        Log.d("TrafficRepository", "Stopping capture...")
        // nativeDataSource.stopPcapdListener()
        delay(500)
        _isCapturing.value = false
        Log.d("TrafficRepository", "Capture stopped successfully (simulation)")
    }
}