package com.packet.analyzer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packet.analyzer.R
import com.packet.analyzer.data.repository.TrafficRepository
import com.packet.analyzer.data.util.RootStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject


data class CaptureUiState(
    val rootStatus: RootStatus = RootStatus.UNKNOWN,
    val isCapturing: Boolean = false,
    val isOperationInProgress: Boolean = false,
    val buttonTextResId: Int = R.string.capture_start,
    val statusTextResId: Int = R.string.status_checking_root,
    val error: String? = null
) {
    val isButtonEnabled: Boolean
        get() = rootStatus == RootStatus.GRANTED && !isOperationInProgress
}

@HiltViewModel
class CaptureControlViewModel @Inject constructor(
    private val repository: TrafficRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    private val _isCaptureOperationRunning = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            val initialRootStatus = repository.getRootStatusUpdates().first()
            _uiState.update { it.copy(rootStatus = initialRootStatus, statusTextResId = getInitialStatusText(initialRootStatus)) }

            if (initialRootStatus == RootStatus.UNKNOWN /* || initialRootStatus == RootStatus.DENIED */ ) {
                _uiState.update { it.copy(isOperationInProgress = true, statusTextResId = R.string.status_checking_root) }
                try {
                    repository.checkOrRequestRootAccess()
                } catch(e: Exception) {
                    _uiState.update { it.copy(error = "Initial root check failed: ${e.message}") }
                } finally {
                    _uiState.update { it.copy(isOperationInProgress = false) }
                }
            }

            combine(
                repository.getRootStatusUpdates(),
                repository.getCaptureStatusUpdates(),
                _isCaptureOperationRunning
            ) { root, capturing, isOpRunning ->
                determineUiState(root, capturing, isOpRunning, _uiState.value.error)
            }.collect { newState ->
                val finalState = if (newState.error == null && _uiState.value.error != null) {
                    newState.copy(error = _uiState.value.error)
                } else {
                    newState
                }
                _uiState.value = finalState
            }
        }
    }

    private fun getInitialStatusText(status: RootStatus): Int {
        return when (status) {
            RootStatus.GRANTED -> R.string.status_idle
            RootStatus.DENIED -> R.string.status_no_root
            RootStatus.UNKNOWN -> R.string.status_root_unknown
        }
    }

    private fun determineUiState(
        rootStatus: RootStatus,
        isCapturing: Boolean,
        isOperationRunning: Boolean,
        currentError: String?
    ): CaptureUiState {
        val determinedStatusTextResId = determineStatusTextResId(rootStatus, isCapturing, isOperationRunning)
        val determinedButtonTextResId = if (isCapturing) R.string.capture_stop else R.string.capture_start

        return CaptureUiState(
            rootStatus = rootStatus,
            isCapturing = isCapturing,
            isOperationInProgress = isOperationRunning,
            buttonTextResId = determinedButtonTextResId,
            statusTextResId = determinedStatusTextResId,
            error = currentError,
        )
    }

    private fun determineStatusTextResId(
        rootStatus: RootStatus,
        isCapturing: Boolean,
        isOperationRunning: Boolean
    ): Int {
        return when {
            isOperationRunning -> if (isCapturing) R.string.status_stopping else R.string.status_starting
            rootStatus == RootStatus.DENIED -> R.string.status_no_root
            rootStatus == RootStatus.UNKNOWN -> R.string.status_root_unknown
            isCapturing -> R.string.status_capturing
            else -> R.string.status_idle
        }
    }


    fun toggleCaptureState() {
        viewModelScope.launch {
            _isCaptureOperationRunning.value = true
            _uiState.update { it.copy(error = null, statusTextResId = R.string.status_checking_root) }
            var hasRoot = false
            try {
                hasRoot = repository.checkOrRequestRootAccess()
                if (!hasRoot) {
                    _uiState.update { it.copy(rootStatus = RootStatus.DENIED, statusTextResId = R.string.status_no_root) }
                    _isCaptureOperationRunning.value = false
                    return@launch
                }

                val targetState = !_uiState.value.isCapturing

                _uiState.update { it.copy(statusTextResId = if (targetState) R.string.status_starting else R.string.status_stopping) }

                if (targetState) {
                    repository.startCapture()
                } else {
                    repository.stopCapture()
                }

            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Operation failed: ${e.message}", statusTextResId = R.string.status_error) }
            } finally {
                if (hasRoot) {
                    _isCaptureOperationRunning.value = false
                }
            }
        }
    }

    fun clearError() {
        val currentStatusText = determineStatusTextResId(
            _uiState.value.rootStatus,
            _uiState.value.isCapturing,
            _uiState.value.isOperationInProgress
        )
        _uiState.update { it.copy(error = null, statusTextResId = currentStatusText) }
    }
}