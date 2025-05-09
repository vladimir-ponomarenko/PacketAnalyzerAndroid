package com.packet.analyzer.data.repository

import com.packet.analyzer.data.model.AppInfo
import com.packet.analyzer.data.model.AppSessionTrafficData
import com.packet.analyzer.data.util.RootStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface TrafficRepository {

    fun getCaptureStatusUpdates(): StateFlow<Boolean>

    fun getRootStatusUpdates(): Flow<RootStatus>

    suspend fun checkOrRequestRootAccess(): Boolean

    suspend fun getAppList(includeSystemApps: Boolean): List<AppInfo>

    suspend fun startCapture()

    suspend fun stopCapture()

    // fun getTrafficUpdates(): Flow<List<AppTrafficInfo>>
    // suspend fun getAppList(): List<AppData>
    fun getAggregatedTrafficData(): StateFlow<Map<Int, AppSessionTrafficData>>
    fun getTrafficDataForUid(uid: Int): Flow<AppSessionTrafficData?>

    // fun getRawPacketHeaderFlow(): SharedFlow<JniBridge.PcapHeaderInfo>

    fun cleanupCaptureResources()
}