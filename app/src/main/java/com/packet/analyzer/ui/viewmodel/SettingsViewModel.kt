package com.packet.analyzer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packet.analyzer.data.repository.TrafficRepository
import com.packet.analyzer.data.util.RootStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine // Импортируем combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val rootStatus: RootStatus = RootStatus.UNKNOWN,
    val isCheckingRoot: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: TrafficRepository
) : ViewModel() {

    private val _isCheckingRoot = MutableStateFlow(false)
    val isCheckingRoot: StateFlow<Boolean> = _isCheckingRoot.asStateFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        repository.getRootStatusUpdates(),
        _isCheckingRoot
    ) { status, isChecking ->
        SettingsUiState(rootStatus = status, isCheckingRoot = isChecking)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = SettingsUiState()
    )

    fun checkOrRequestRoot() {
        if (_isCheckingRoot.value) return

        viewModelScope.launch {
            _isCheckingRoot.value = true
            try {
                repository.checkOrRequestRootAccess()
            } catch (e: Exception) {
                // TODO: Обработать ошибку, возможно показать Snackbar или лог
            } finally {
                _isCheckingRoot.value = false
            }
        }
    }
}