package com.packet.analyzer.ui.viewmodel


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packet.analyzer.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Состояние UI для экрана захвата
data class CaptureUiState(
    val isCapturing: Boolean = false, // Идет ли захват?
    val buttonTextResId: Int = R.string.capture_start, // Текст на кнопке
    val statusTextResId: Int = R.string.status_idle, // Текст статуса под кнопкой
    val isButtonEnabled: Boolean = true // Доступна ли кнопка (при проверке root)
    // ...
)

// @HiltViewModel
class CaptureControlViewModel /*@Inject constructor(
    // private val repository: TrafficRepository
)*/ : ViewModel() {

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    init {

        checkRootAccess()
    }

    private fun checkRootAccess() {
        viewModelScope.launch {
            // TODO: заменить реальной логикой
            kotlinx.coroutines.delay(1000) // Имитация задержки
            val hasRoot = true // TODO: Заменить реальной проверкой через репозиторий/RootExecutor

            _uiState.update {
                it.copy(
                    isButtonEnabled = hasRoot,
                    statusTextResId = if (hasRoot) R.string.status_idle else R.string.status_no_root
                )
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
                    // Здесь вызов repository.startCapture()
                    kotlinx.coroutines.delay(1500) // Имитация запуска
                    // Имитация успешного запуска
                    _uiState.update {
                        it.copy(
                            isCapturing = true,
                            buttonTextResId = R.string.capture_stop,
                            statusTextResId = R.string.status_capturing,
                            isButtonEnabled = true
                        )
                    }
                } else {
                    // Здесь вызов repository.stopCapture()
                    kotlinx.coroutines.delay(1000)
                    // Имитация успешной остановки
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
                        isButtonEnabled = true
                    )
                }
            }
        }
    }
}