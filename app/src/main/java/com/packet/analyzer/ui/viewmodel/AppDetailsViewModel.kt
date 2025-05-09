package com.packet.analyzer.ui.viewmodel

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mikephil.charting.data.Entry
import com.packet.analyzer.data.model.AppInfo
import com.packet.analyzer.data.model.AppSessionTrafficData
import com.packet.analyzer.data.repository.TrafficRepository
import com.packet.analyzer.data.repository.TrafficRepositoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject


data class AppDetailsScreenState(
    val appInfo: AppInfo? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val trafficChartEntries: List<Entry> = emptyList(),
    val totalPackets: Long = 0,
    val totalTraffic: Long = 0,
    val uplinkTraffic: Long = 0,
    val downlinkTraffic: Long = 0
)

@HiltViewModel
class AppDetailsViewModel @Inject constructor(
    private val repository: TrafficRepository,
    @ApplicationContext private val applicationContext: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val appUid: Int = savedStateHandle.get<Int>("appUid")!!

    private val _uiState = MutableStateFlow(AppDetailsScreenState(isLoading = true))
    val uiState: StateFlow<AppDetailsScreenState> = _uiState.asStateFlow()

    init {
        Log.d("AppDetailsViewModel", "Initializing for UID: $appUid")

        if (_uiState.value.appInfo == null) {
            loadInitialAppInfoOnce(appUid)
        }
        observeAppTrafficData()
    }

    private fun observeAppTrafficData() {
        viewModelScope.launch {
            repository.getTrafficDataForUid(appUid)
                .onStart {

                    if (_uiState.value.appInfo == null) {
                        _uiState.update { it.copy(isLoading = true) }
                    }
                }
                .catch { e ->
                    Log.e("AppDetailsViewModel", "Error observing traffic data for UID $appUid", e)
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Error: ${e.localizedMessage}") }
                }
                .collect { appData ->
                    if (appData == null) {


                        if (_uiState.value.appInfo != null) {
                            _uiState.update { it.copy(isLoading = false, errorMessage = null) }
                        } else if (!initialAppInfoLoaded) {
                            loadInitialAppInfoOnce(appUid)
                        }

                    } else {
                        val chartEntries = appData.weightDistributionForChart
                            .map { (weight, count) -> Entry(weight, count.toFloat()) }
                            .sortedBy { it.x }

                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                appInfo = appData.appInfo ?: it.appInfo,
                                trafficChartEntries = chartEntries,
                                totalPackets = appData.totalPackets,
                                totalTraffic = appData.totalBytes,
                                uplinkTraffic = appData.uplinkBytes,
                                downlinkTraffic = appData.downlinkBytes,
                                errorMessage = null
                            )
                        }
                    }
                }
        }
    }

    private var initialAppInfoLoaded = false
    private fun loadInitialAppInfoOnce(uid: Int) {
        if (initialAppInfoLoaded) return
        initialAppInfoLoaded = true
        Log.d("AppDetailsViewModel", "Loading initial AppInfo for UID: $uid")

        viewModelScope.launch(Dispatchers.IO) {
            var foundApp: AppInfo? = null
            try {
                val packages = applicationContext.packageManager.getPackagesForUid(uid)
                if (packages != null && packages.isNotEmpty()) {
                    val packageName = packages[0]
                    val applicationInfo = applicationContext.packageManager.getApplicationInfo(packageName, 0)
                    val appName = applicationContext.packageManager.getApplicationLabel(applicationInfo).toString()
                    val icon = try { applicationContext.packageManager.getApplicationIcon(applicationInfo) } catch (_: Exception) { null }
                    foundApp = AppInfo(appName, packageName, icon)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w("AppDetailsViewModel", "App package not found for UID: $uid")
            } catch (e: Exception) {
                Log.e("AppDetailsViewModel", "Error loading app info for UID $uid via packageManager", e)
            }


            _uiState.update {
                it.copy(


                    appInfo = it.appInfo ?: foundApp ?: AppInfo("UID: $uid", "unknown.uid.$uid", null),
                    isLoading = false
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("AppDetailsViewModel", "ViewModel for UID $appUid cleared.")
    }
}