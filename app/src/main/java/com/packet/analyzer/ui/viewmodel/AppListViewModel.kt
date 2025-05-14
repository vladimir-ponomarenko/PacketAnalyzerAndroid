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
import com.packet.analyzer.data.repository.TrafficRepositoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject


data class AppListItemData(
    val appInfo: AppInfo,
    val sessionTraffic: AppSessionTrafficData?
)

data class OverallAppSettings(
    val placeholder: Boolean = true
)

data class AppListScreenUiState(
    val isLoadingApps: Boolean = true,
    val appItems: List<AppListItemData> = emptyList(),
    val error: String? = null,
    val includeSystemApps: Boolean = true,
    val isOverallSectionExpanded: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class AppListViewModel @Inject constructor(
    private val repository: TrafficRepository,
    @ApplicationContext private val applicationContext: Context
) : ViewModel() {

    private val _isLoadingApps = MutableStateFlow(true)
    private val _rawAppList = MutableStateFlow<List<AppInfo>>(emptyList())
    private val _error = MutableStateFlow<String?>(null)
    private val _includeSystemApps = MutableStateFlow(true)
    private val _isOverallSectionExpanded = MutableStateFlow(true)
    private val aggregatedTrafficDataFlow: StateFlow<Map<Int, AppSessionTrafficData>> =
        (repository as? TrafficRepositoryImpl)?.getAggregatedTrafficData()
            ?: MutableStateFlow<Map<Int, AppSessionTrafficData>>(emptyMap()).asStateFlow()


    val uiState: StateFlow<AppListScreenUiState> = combine(
        _isLoadingApps,                 // Flow<Boolean>
        _rawAppList,                    // Flow<List<AppInfo>>
        aggregatedTrafficDataFlow.sample(500L), // Flow<Map<Int, AppSessionTrafficData>> - для элементов списка
        _error,                         // Flow<String?>
        _includeSystemApps,             // Flow<Boolean> - для переключателя
        _isOverallSectionExpanded       // Flow<Boolean> - для сворачивания
    ) { flows ->
        val isLoading = flows[0] as Boolean
        val rawApps = flows[1] as List<AppInfo>
        val trafficMap = flows[2] as Map<Int, AppSessionTrafficData>
        val errorMsg = flows[3] as String?
        val includeSystem = flows[4] as Boolean
        val isOverallExpanded = flows[5] as Boolean
        if (isLoading) {
            AppListScreenUiState(isLoadingApps = true, includeSystemApps = includeSystem, isOverallSectionExpanded = isOverallExpanded)
        } else if (errorMsg != null) {
            AppListScreenUiState(isLoadingApps = false, error = errorMsg, includeSystemApps = includeSystem, isOverallSectionExpanded = isOverallExpanded)
        } else {
            val appListItems = rawApps.mapNotNull { appInfo ->
                val uid = getUidForPackageName(appInfo.packageName)
                if (uid != -1) {
                    AppListItemData(
                        appInfo = appInfo,
                        sessionTraffic = trafficMap[uid]
                    )
                } else {
                    null
                }
            }.sortedByDescending { it.sessionTraffic?.totalBytes ?: -1L }
            AppListScreenUiState(
                isLoadingApps = false,
                appItems = appListItems,
                error = if (rawApps.isEmpty() && appListItems.isEmpty()) applicationContext.getString(R.string.app_list_no_apps_found) else null,
                includeSystemApps = includeSystem,
                isOverallSectionExpanded = isOverallExpanded
            )
        }
    }
        .flowOn(Dispatchers.Default)
        .catch { e ->
            Log.e("AppListViewModel", "Error in UI state flow", e)
            _error.value = "Error processing UI state: ${e.message}"
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppListScreenUiState(isLoadingApps = true)
        )

    init {
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingApps.value = true
            _error.value = null
            try {
                val apps = repository.getAppList(includeSystemApps = _includeSystemApps.value)
                _rawAppList.value = apps
                Log.d("AppListViewModel", "Loaded ${apps.size} apps. Include system: ${_includeSystemApps.value}")
            } catch (e: Exception) {
                Log.e("AppListViewModel", "Error loading installed apps", e)
                _error.value = "Failed to load apps: ${e.localizedMessage}"
                _rawAppList.value = emptyList()
            } finally {
                _isLoadingApps.value = false
            }
        }
    }

    private fun getUidForPackageName(packageName: String): Int {
        return try {
            applicationContext.packageManager.getPackageUid(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            -1
        } catch (e: Exception) {
            -1
        }
    }

    fun toggleIncludeSystemApps() {
        _includeSystemApps.value = !_includeSystemApps.value
        loadInstalledApps()
    }

    fun toggleOverallSectionExpanded() {
        _isOverallSectionExpanded.value = !_isOverallSectionExpanded.value
    }
}