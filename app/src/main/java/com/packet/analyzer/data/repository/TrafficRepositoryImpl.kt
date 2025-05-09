package com.packet.analyzer.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.packet.analyzer.data.datasource.PcapdDataSource
import com.packet.analyzer.data.datasource.app.AppInfoDataSource
import com.packet.analyzer.data.datasource.root.RootDataSource
import com.packet.analyzer.data.model.AppInfo
import com.packet.analyzer.data.model.AppSessionTrafficData
import com.packet.analyzer.data.model.PcapHeaderInfo
import com.packet.analyzer.data.util.RootStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrafficRepositoryImpl @Inject constructor(
    private val context: Context,
    private val rootDataSource: RootDataSource,
    private val appInfoDataSource: AppInfoDataSource
) : TrafficRepository {

    private val pcapdDataSource: PcapdDataSource = PcapdDataSource(context, rootDataSource)
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isCapturing = MutableStateFlow(false)
    override fun getCaptureStatusUpdates(): StateFlow<Boolean> = _isCapturing.asStateFlow()


    private val _aggregatedTrafficData =
        MutableStateFlow<Map<Int, AppSessionTrafficData>>(emptyMap())
    override fun getAggregatedTrafficData(): StateFlow<Map<Int, AppSessionTrafficData>> =
        _aggregatedTrafficData.asStateFlow()


    private val appInfoCache = MutableStateFlow<Map<Int, AppInfo>>(emptyMap())


    init {

        repositoryScope.launch {

            try {
                val apps = appInfoDataSource.getInstalledApps(includeSystemApps = true)
                val uidMap = mutableMapOf<Int, AppInfo>()
                for (app in apps) {
                    try {
                        val uid = context.packageManager.getPackageUid(app.packageName, 0)
                        uidMap[uid] = app
                    } catch (e: Exception) {

                    }
                }
                appInfoCache.value = uidMap
                Log.d("TrafficRepository", "App info cache preloaded with ${uidMap.size} apps.")
            } catch (e: Exception) {
                Log.e("TrafficRepository", "Failed to preload app info cache", e)
            }



            pcapdDataSource.packetHeaderFlow.collect { headerInfo ->
                _aggregatedTrafficData.update { currentMap ->
                    val uid = headerInfo.uid
                    val currentData = currentMap[uid]
                        ?: AppSessionTrafficData(uid = uid, appInfo = appInfoCache.value[uid])

                    val updatedData = currentData.updatedWith(headerInfo)



                    if (updatedData.appInfo == null && !appInfoCache.value.containsKey(uid)) {
                        launch {
                            fetchAndCacheAppInfo(uid)
                        }
                    }
                    currentMap + (uid to updatedData)
                }
            }
        }
    }

    private suspend fun fetchAndCacheAppInfo(uid: Int) {
        if (appInfoCache.value.containsKey(uid)) return

        try {
            val packages = context.packageManager.getPackagesForUid(uid)
            if (packages != null && packages.isNotEmpty()) {
                val packageName = packages[0]
                val applicationInfo = context.packageManager.getApplicationInfo(packageName, 0)
                val appName = context.packageManager.getApplicationLabel(applicationInfo).toString()
                val icon = try { context.packageManager.getApplicationIcon(applicationInfo) } catch (_: Exception) { null }
                val newAppInfo = AppInfo(appName, packageName, icon)

                appInfoCache.update { it + (uid to newAppInfo) }

                _aggregatedTrafficData.update { currentMap ->
                    currentMap[uid]?.let { existingData ->
                        if (existingData.appInfo == null) {
                            currentMap + (uid to existingData.copy(appInfo = newAppInfo))
                        } else {
                            currentMap
                        }
                    } ?: currentMap
                }
                Log.d("TrafficRepository", "Fetched and cached AppInfo for UID: $uid")
            } else {

                appInfoCache.update { it + (uid to AppInfo("UID: $uid", "unknown.uid.$uid", null)) }
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w("TrafficRepository", "Package not found for UID: $uid while fetching info.")
            appInfoCache.update { it + (uid to AppInfo("UID: $uid", "not.found.$uid", null)) }
        } catch (e: Exception) {
            Log.e("TrafficRepository", "Error fetching app info for UID $uid on demand", e)
        }
    }


    override fun getTrafficDataForUid(uid: Int): Flow<AppSessionTrafficData?> {
        return _aggregatedTrafficData.map { it[uid] }
            .distinctUntilChanged()
    }

    override fun getRootStatusUpdates(): Flow<RootStatus> {
        return rootDataSource.rootStatusFlow
    }

    override suspend fun checkOrRequestRootAccess(): Boolean {
        Log.d("TrafficRepository", "Delegating root check/request to RootDataSource")
        return rootDataSource.checkOrRequestRootAccess()
    }

    override suspend fun getAppList(includeSystemApps: Boolean): List<AppInfo> {
        Log.d("TrafficRepository", "Fetching app list (includeSystemApps: $includeSystemApps)")

        return appInfoDataSource.getInstalledApps(includeSystemApps)
    }

    override suspend fun startCapture() {
        if (_isCapturing.value) {
            Log.w("TrafficRepository", "Capture is already running.")
            return
        }
        Log.d("TrafficRepository", "Attempting to start capture via PcapdDataSource...")
        try {

            _aggregatedTrafficData.value = emptyMap()


            val started = pcapdDataSource.startCapture()
            if (started) {
                _isCapturing.value = true
                Log.i("TrafficRepository", "Capture start initiated successfully.")
            } else {
                _isCapturing.value = false
                Log.e("TrafficRepository", "Capture start initiation failed.")
                throw RuntimeException("Failed to start capture")
            }
        } catch (e: Exception) {
            Log.e("TrafficRepository", "Exception during startCapture", e)
            _isCapturing.value = false
            throw e
        }
    }

    override suspend fun stopCapture() {
        if (!_isCapturing.value) {
            Log.w("TrafficRepository", "Capture is not running")
            return
        }
        Log.d("TrafficRepository", "Stopping capture via PcapdDataSource...")
        try {
            pcapdDataSource.stopCapture()
            _isCapturing.value = false
            Log.i("TrafficRepository", "Capture stop initiated.")

        } catch (e: Exception) {
            Log.e("TrafficRepository", "Exception during stopCapture", e)
            _isCapturing.value = false
            throw e
        }
    }

    override fun cleanupCaptureResources() {
        Log.i("TrafficRepositoryImpl", "Cleanup requested for PcapdDataSource")
        pcapdDataSource.cleanup()
        repositoryScope.cancel("Repository cleanup")
    }
}