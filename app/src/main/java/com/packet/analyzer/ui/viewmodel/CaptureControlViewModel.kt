package com.packet.analyzer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packet.analyzer.R
import com.packet.analyzer.data.repository.TrafficRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Состояние UI для экрана захвата
data class CaptureUiState(
    val isCapturing: Boolean = false,
    val buttonTextResId: Int = R.string.capture_start,
    val statusTextResId: Int = R.string.status_idle,
    val isButtonEnabled: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class CaptureControlViewModel @Inject constructor(
    // private val repository: TrafficRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    init {
        checkRootAccess()
    }

    private fun checkRootAccess() {
        _uiState.update { it.copy(isButtonEnabled = false, statusTextResId = R.string.status_no_root) }
        viewModelScope.launch {
            try {
                // val hasRoot = repository.checkRootAccess()
                kotlinx.coroutines.delay(1000)
                val hasRoot = true

                _uiState.update {
                    it.copy(
                        isButtonEnabled = hasRoot,
                        statusTextResId = if (hasRoot) R.string.status_idle else R.string.status_no_root
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isButtonEnabled = false,
                        statusTextResId = R.string.status_error,
                        error = "Root check failed: ${e.message}"
                    )
                }
            }
        }
    }


    fun toggleCaptureState() {
        if (!_uiState.value.isButtonEnabled) return

        val currentlyCapturing = _uiState.value.isCapturing
        val nextState = !currentlyCapturing

        _uiState.update {
            it.copy(
                isButtonEnabled = false,
                statusTextResId = if (nextState) R.string.status_starting else R.string.status_stopping
            )
        }

        viewModelScope.launch {
            try {
                if (nextState) {
                    kotlinx.coroutines.delay(1500)

                    _uiState.update {
                        it.copy(
                            isCapturing = true,
                            buttonTextResId = R.string.capture_stop,
                            statusTextResId = R.string.status_capturing,
                            isButtonEnabled = true
                        )
                    }
                } else {
                    // repository.stopCapture()
                    kotlinx.coroutines.delay(1000)

                    _uiState.update {
                        it.copy(
                            isCapturing = false,
                            buttonTextResId = R.string.capture_start,
                            statusTextResId = R.string.status_idle,
                            isButtonEnabled = true
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isCapturing = false,
                        buttonTextResId = R.string.capture_start,
                        statusTextResId = R.string.status_error,
                        isButtonEnabled = true,
                        error = "Operation failed: ${e.message}"
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}