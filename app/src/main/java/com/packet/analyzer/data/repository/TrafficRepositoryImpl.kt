package com.packet.analyzer.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class TrafficRepositoryImpl @Inject constructor() : TrafficRepository {

    private val _isCapturing = MutableStateFlow(false)
    override fun getCaptureStatusUpdates(): StateFlow<Boolean> = _isCapturing.asStateFlow()

    override suspend fun checkRootAccess(): Boolean = withContext(Dispatchers.IO) {
        Log.d("TrafficRepository", "Checking root access...")
        delay(500)
        Log.d("TrafficRepository", "Root access check result: true (simulation)")
        true
    }

    override suspend fun startCapture() = withContext(Dispatchers.IO) {
        if (_isCapturing.value) {
            Log.w("TrafficRepository", "Capture already running")
            return@withContext
        }
        Log.d("TrafficRepository", "Starting capture...")
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
        delay(500)
        _isCapturing.value = false
        Log.d("TrafficRepository", "Capture stopped successfully (simulation)")
    }
}