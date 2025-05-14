package com.packet.analyzer.ui.viewmodel

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mikephil.charting.data.Entry
import com.packet.analyzer.R
import com.packet.analyzer.data.model.AppInfo
import com.packet.analyzer.data.model.AppSessionTrafficData
import com.packet.analyzer.data.model.MAX_GRAPH_PACKET_SAMPLES
import com.packet.analyzer.data.repository.TrafficRepository
import com.packet.analyzer.data.repository.TrafficRepositoryImpl
import com.packet.analyzer.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.LinkedList
import javax.inject.Inject


data class AppDetailsScreenState(
    val appInfo: AppInfo? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val trafficChartEntries: List<Entry> = emptyList(),
    val totalPackets: Long = 0,
    val totalTraffic: Long = 0,
    val uplinkTraffic: Long = 0,
    val downlinkTraffic: Long = 0,
    val isOverallStats: Boolean = false
)

@OptIn(FlowPreview::class)
@HiltViewModel
class AppDetailsViewModel @Inject constructor(
    private val repository: TrafficRepository,
    @ApplicationContext private val applicationContext: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val appUid: Int = savedStateHandle.get<Int>("appUid")
        ?: throw IllegalStateException("appUid not found in SavedStateHandle")

    private val _uiState = MutableStateFlow(AppDetailsScreenState(isLoading = true, isOverallStats = appUid == Screen.AppDetails.OVERALL_STATS_UID))
    val uiState: StateFlow<AppDetailsScreenState> = _uiState.asStateFlow()

    init {
        Log.d("AppDetailsViewModel", "Initializing for UID: $appUid. IsOverall: ${uiState.value.isOverallStats}")

        if (uiState.value.isOverallStats) {
            observeOverallTrafficData()
        } else {

            if (_uiState.value.appInfo == null) {
                loadInitialAppInfoOnce(appUid)
            }
            observeAppTrafficData()
        }
    }

    private fun observeAppTrafficData() {
        viewModelScope.launch {
            repository.getTrafficDataForUid(appUid)
                .onStart {


                    if (_uiState.value.appInfo == null && !initialAppInfoLoaded) {
                        _uiState.update { it.copy(isLoading = true) }
                    }
                }
                .catch { e ->
                    Log.e("AppDetailsViewModel", "Error observing app traffic data for UID $appUid", e)
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Error: ${e.localizedMessage}") }
                }
                .collect { appData ->
                    if (appData == null) {


                        if (_uiState.value.appInfo != null) {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = null,
                                    totalPackets = 0,
                                    totalTraffic = 0,
                                    uplinkTraffic = 0,
                                    downlinkTraffic = 0,
                                    trafficChartEntries = emptyList()
                                )
                            }
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


    private data class TempOverallSummary(
        val totalBytes: Long,
        val uplinkBytes: Long,
        val downlinkBytes: Long,
        val totalPackets: Long,
        val chartEntries: List<Entry>
    )

    private fun observeOverallTrafficData() {
        viewModelScope.launch {


            (repository as? TrafficRepositoryImpl)?.getAggregatedTrafficData()
                ?.map { trafficMap ->
                    var totalBytes = 0L
                    var uplinkBytes = 0L
                    var downlinkBytes = 0L
                    var totalPackets = 0L
                    val allPacketWeights = LinkedList<Long>()

                    trafficMap.values.forEach { sessionData ->
                        totalBytes += sessionData.totalBytes
                        uplinkBytes += sessionData.uplinkBytes
                        downlinkBytes += sessionData.downlinkBytes
                        totalPackets += sessionData.totalPackets
                        allPacketWeights.addAll(sessionData.packetWeights)
                    }
                    while (allPacketWeights.size > MAX_GRAPH_PACKET_SAMPLES) {
                        allPacketWeights.removeFirst()
                    }
                    val overallWeightDistribution = allPacketWeights
                        .groupBy { it }
                        .mapValues { it.value.size }
                        .mapKeys { it.key.toFloat() }

                    val chartEntries = overallWeightDistribution
                        .map { (weight, count) -> Entry(weight, count.toFloat()) }
                        .sortedBy { it.x }
                    TempOverallSummary(
                        totalBytes, uplinkBytes, downlinkBytes, totalPackets, chartEntries
                    )
                }
                ?.flowOn(Dispatchers.Default)
                ?.catch { e ->
                    Log.e("AppDetailsViewModel", "Error observing overall traffic data", e)
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Error: ${e.localizedMessage}") }
                }
                ?.collect { summary ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            appInfo = AppInfo(
                                appName = applicationContext.getString(R.string.overall_stats_title),
                                packageName = "overall.statistics",
                                icon = null
                            ),
                            trafficChartEntries = summary.chartEntries,
                            totalPackets = summary.totalPackets,
                            totalTraffic = summary.totalBytes,
                            uplinkTraffic = summary.uplinkBytes,
                            downlinkTraffic = summary.downlinkBytes,
                            errorMessage = null,
                            isOverallStats = true
                        )
                    }
                } ?: run {

                Log.e("AppDetailsViewModel", "Failed to cast repository to TrafficRepositoryImpl for overall stats or flow was null.")
                _uiState.update { it.copy(isLoading = false, errorMessage = "Could not load overall statistics.") }
            }
        }
    }

    private var initialAppInfoLoaded = false
    private fun loadInitialAppInfoOnce(uid: Int) {
        if (initialAppInfoLoaded || uiState.value.isOverallStats) return
        initialAppInfoLoaded = true
        Log.d("AppDetailsViewModel", "Loading initial AppInfo for UID: $uid")

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            var foundApp: AppInfo? = null
            var loadError: String? = null
            try {
                val packages = applicationContext.packageManager.getPackagesForUid(uid)
                if (!packages.isNullOrEmpty()) {
                    val packageName = packages[0]
                    val applicationInfo = applicationContext.packageManager.getApplicationInfo(packageName, 0)
                    val appName = applicationContext.packageManager.getApplicationLabel(applicationInfo).toString()
                    val icon = try { applicationContext.packageManager.getApplicationIcon(applicationInfo) } catch (_: Exception) { null }
                    foundApp = AppInfo(appName, packageName, icon)
                } else {

                    Log.w("AppDetailsViewModel", "No packages found for UID: $uid")
                    foundApp = AppInfo(applicationContext.getString(R.string.app_details_loading_uid, uid), "unknown.uid.$uid", null)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w("AppDetailsViewModel", "App package not found for UID: $uid")
                foundApp = AppInfo(applicationContext.getString(R.string.app_details_app_not_found_uid, uid), "not.found.$uid", null)
            } catch (e: Exception) {
                Log.e("AppDetailsViewModel", "Error loading app info for UID $uid via packageManager", e)
                loadError = "Error loading app details: ${e.localizedMessage}"
            }

            _uiState.update { currentState ->
                if (!currentState.isOverallStats) {
                    currentState.copy(
                        appInfo = foundApp ?: AppInfo(applicationContext.getString(R.string.app_details_loading_uid, uid), "unknown.uid.$uid", null),
                        isLoading = false,
                        errorMessage = currentState.errorMessage ?: loadError
                    )
                } else {
                    currentState.copy(isLoading = false)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("AppDetailsViewModel", "ViewModel for UID $appUid cleared.")
    }
}