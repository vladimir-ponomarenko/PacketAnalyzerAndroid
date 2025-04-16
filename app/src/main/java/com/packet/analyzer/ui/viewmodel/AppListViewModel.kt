package com.packet.analyzer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packet.analyzer.data.model.AppInfo
import com.packet.analyzer.data.repository.TrafficRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppListUiState(
    val isLoading: Boolean = true,
    val apps: List<AppInfo> = emptyList(),
    val error: String? = null,
    val includeSystemApps: Boolean = true
)

@HiltViewModel
class AppListViewModel @Inject constructor(
    private val repository: TrafficRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppListUiState())
    val uiState: StateFlow<AppListUiState> = _uiState.asStateFlow()

    init {
        loadApps()
    }

    fun loadApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val appList = repository.getAppList(includeSystemApps = _uiState.value.includeSystemApps)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        apps = appList,
                        error = if (appList.isEmpty()) "No applications found" else null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        apps = emptyList(),
                        error = "Failed to load applications: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    // TODO: Добавить функцию для переключения includeSystemApps и вызова loadApps()
    // fun toggleSystemApps(include: Boolean) {
    //     _uiState.update { it.copy(includeSystemApps = include) }
    //     loadApps()
    // }
}