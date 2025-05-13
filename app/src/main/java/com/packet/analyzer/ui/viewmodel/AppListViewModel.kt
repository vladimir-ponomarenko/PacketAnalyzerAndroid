package com.packet.analyzer.ui.viewmodel

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packet.analyzer.data.model.AppInfo
import com.packet.analyzer.data.model.AppSessionTrafficData
import com.packet.analyzer.data.repository.TrafficRepository
import com.packet.analyzer.util.FormatUtils
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


data class AppListScreenUiState(
    val isLoadingApps: Boolean = true,
    val appItems: List<AppListItemData> = emptyList(),
    val error: String? = null,
    val includeSystemApps: Boolean = true // TODO: Добавить управление этим флагом
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


    private val appInfoByUidCache = MutableStateFlow<Map<Int, AppInfo>>(emptyMap())

    private val uiUpdateThrottlePeriodMs = 500L

    val uiState: StateFlow<AppListScreenUiState> = combine(
        _isLoadingApps,
        _rawAppList,
        repository.getAggregatedTrafficData().sample(uiUpdateThrottlePeriodMs),


        _error
    ) { isLoading, rawApps, throttledTrafficMap, errorMsg ->


        if (isLoading) {
            AppListScreenUiState(isLoadingApps = true)
        } else if (errorMsg != null) {
            AppListScreenUiState(isLoadingApps = false, error = errorMsg)
        } else {

            val appListItems = rawApps.map { appInfo ->
                val uid = getUidForPackageName(appInfo.packageName)
                AppListItemData(
                    appInfo = appInfo,
                    sessionTraffic = if (uid != -1) throttledTrafficMap[uid] else null
                )
            }.sortedByDescending { it.sessionTraffic?.totalBytes ?: -1L }

            Log.d("AppListViewModel", "Processed ${appListItems.size} items for UI state (throttled)")
            AppListScreenUiState(
                isLoadingApps = false,
                appItems = appListItems,
                error = if (rawApps.isEmpty() && appListItems.isEmpty()) "No applications found" else null
            )
        }
    }
        .flowOn(Dispatchers.Default)
        .catch { e ->
            Log.e("AppListViewModel", "Error in UI state flow", e)
            emit(AppListScreenUiState(isLoadingApps = false, error = "Error processing app list: ${e.message}"))
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







                Log.d("AppListViewModel", "Loaded ${apps.size} apps.")
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
        // Log.v("AppListViewModel", "Getting UID for $packageName")
        return try {
            applicationContext.packageManager.getPackageUid(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            // Log.w("AppListViewModel", "UID not found for $packageName")
            -1
        } catch (e: Exception) {
            // Log.e("AppListViewModel", "Error getting UID for $packageName", e)
            -1
        }
    }

    fun toggleIncludeSystemApps() {
        _includeSystemApps.value = !_includeSystemApps.value
        loadInstalledApps()
    }
}