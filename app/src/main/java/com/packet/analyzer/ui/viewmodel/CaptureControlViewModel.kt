package com.packet.analyzer.ui.viewmodel

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packet.analyzer.R
import com.packet.analyzer.data.model.AppInfo
import com.packet.analyzer.data.model.AppSessionTrafficData
import com.packet.analyzer.data.repository.TrafficRepository
import com.packet.analyzer.data.util.RootStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject


data class CaptureControlScreenUiState(
    val rootStatus: RootStatus = RootStatus.UNKNOWN,
    val isCapturing: Boolean = false,
    val isOperationInProgress: Boolean = false,
    val buttonTextResId: Int = R.string.capture_start,
    val statusTextResId: Int = R.string.status_checking_root,
    val error: String? = null,
    val totalSessionTraffic: Long = 0L,
    val totalSessionPackets: Long = 0L
) {
    val isButtonEnabled: Boolean
        get() = (rootStatus == RootStatus.GRANTED || isCapturing) && !isOperationInProgress
}

@HiltViewModel
class CaptureControlViewModel @Inject constructor(
    private val repository: TrafficRepository,
    @ApplicationContext private val applicationContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(CaptureControlScreenUiState())
    val uiState: StateFlow<CaptureControlScreenUiState> = _uiState.asStateFlow()


    private val _isCaptureOperationRunning = MutableStateFlow(false)

    init {
        viewModelScope.launch {

            val initialRootStatus = repository.getRootStatusUpdates().first()
            _uiState.update {
                it.copy(
                    rootStatus = initialRootStatus,
                    statusTextResId = getStatusTextRes(initialRootStatus, it.isCapturing, it.isOperationInProgress)
                )
            }

            if (initialRootStatus == RootStatus.UNKNOWN) {
                checkRootAccess(showLoading = true)
            }


            combine(
                repository.getRootStatusUpdates(),
                repository.getCaptureStatusUpdates(),
                _isCaptureOperationRunning,
                repository.getAggregatedTrafficData()
            ) { root, capturing, opRunning, trafficMap ->

                val totalTraffic = trafficMap.values.sumOf { it.totalBytes }
                val totalPackets = trafficMap.values.sumOf { it.totalPackets }

                CaptureControlScreenUiState(
                    rootStatus = root,
                    isCapturing = capturing,
                    isOperationInProgress = opRunning,
                    buttonTextResId = if (capturing) R.string.capture_stop else R.string.capture_start,
                    statusTextResId = getStatusTextRes(root, capturing, opRunning),
                    error = _uiState.value.error,
                    totalSessionTraffic = totalTraffic,
                    totalSessionPackets = totalPackets
                )
            }.catch { e ->
                Log.e("CaptureControlVM", "Error in UI state combine", e)
                _uiState.update { it.copy(error = "UI update error: ${e.message}") }
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    private fun getStatusTextRes(root: RootStatus, capturing: Boolean, opRunning: Boolean): Int {
        return when {
            opRunning -> if (capturing) R.string.status_stopping else R.string.status_starting
            root == RootStatus.DENIED -> R.string.status_no_root
            root == RootStatus.UNKNOWN -> R.string.status_root_unknown
            capturing -> R.string.status_capturing
            else -> R.string.status_idle
        }
    }

    fun toggleCaptureState() {
        viewModelScope.launch {
            _isCaptureOperationRunning.value = true
            _uiState.update { it.copy(error = null) }

            try {
                val currentRootStatus = repository.getRootStatusUpdates().first()
                if (currentRootStatus != RootStatus.GRANTED && !_uiState.value.isCapturing) {
                    _uiState.update { it.copy(
                        statusTextResId = R.string.status_no_root,
                        error = applicationContext.getString(R.string.status_error_root_required))
                    }
                    _isCaptureOperationRunning.value = false
                    return@launch
                }

                if (_uiState.value.isCapturing) {
                    repository.stopCapture()
                } else {
                    repository.startCapture()
                }
            } catch (e: Exception) {
                Log.e("CaptureControlVM", "Error toggling capture state", e)
                _uiState.update { it.copy(error = e.localizedMessage ?: "Operation failed") }
            } finally {
                delay(100)
                _isCaptureOperationRunning.value = false
            }
        }
    }

    fun checkRootAccess(showLoading: Boolean = false) {
        if (_uiState.value.isOperationInProgress) return
        viewModelScope.launch {
            if(showLoading) _uiState.update { it.copy(isOperationInProgress = true, statusTextResId = R.string.status_checking_root) }
            else _uiState.update { it.copy(isOperationInProgress = true) }
            try {
                repository.checkOrRequestRootAccess()

            } catch (e: Exception) {
                Log.e("CaptureControlVM", "Error checking root access", e)
                _uiState.update { it.copy(error = e.localizedMessage ?: "Root check failed") }
            } finally {
                _uiState.update { it.copy(isOperationInProgress = false) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()

        repository.cleanupCaptureResources()
        Log.d("CaptureControlVM", "ViewModel cleared and resources cleaned up.")
    }
}