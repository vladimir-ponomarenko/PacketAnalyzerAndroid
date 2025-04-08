package com.packet.analyzer.data.datasource

import android.content.Context
import android.util.Log
import com.packet.analyzer.data.datasource.native.JniBridge
import com.packet.analyzer.data.datasource.root.RootDataSource
import com.packet.analyzer.data.model.PcapHeaderInfo
import com.packet.analyzer.data.util.RootStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.SharedFlow
import java.io.File

class PcapdDataSource(
    private val context: Context,
    private val rootDataSource: RootDataSource
) {
    private companion object {
        const val LOG_TAG = "PcapdDataSource"
        const val SOCKET_FILE = "pcapsock"
        const val PID_FILE = "pcapd.pid"
        const val LOG_FILE = "pcapd.log"
    }


    private val socketPath = File(context.cacheDir, SOCKET_FILE).absolutePath
    private val pidPath = File(context.cacheDir, PID_FILE).absolutePath
    private val logPath = File(context.cacheDir, LOG_FILE).absolutePath

    private val dataSourceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isCaptureRunning = false
    private var pcapdPid: Int = -1

    val packetHeaderFlow: SharedFlow<PcapHeaderInfo> = JniBridge.packetHeaderFlow
    private var loggingJob: Job? = null
    private fun startLoggingData() {
        if (loggingJob?.isActive == true) return
        loggingJob = dataSourceScope.launch {
            Log.i(LOG_TAG, "Starting packet header logger...")
            packetHeaderFlow.collect { header ->

                Log.i(LOG_TAG, "RECV -> UID:${header.uid} TL:${header.totalLength} IPL:${header.ipLayerLength} P:${header.protocol} UL:${header.isUplink} TS:${header.timestampSec}.${header.timestampUsec} D:${header.packetDrops} IF:${header.interfaceId}")
            }
        }
    }
    private fun stopLoggingData() {
        loggingJob?.cancel()
        loggingJob = null
        Log.i(LOG_TAG, "Packet header logger stopped.")
    }

    suspend fun startCapture(): Boolean {
        if (isCaptureRunning) {
            Log.w(LOG_TAG, "Capture already running.")
            return true
        }
        Log.i(LOG_TAG, "Attempting to start capture...")
        if (rootDataSource.getCurrentRootStatus() != RootStatus.GRANTED) {
            Log.e(LOG_TAG, "Root access not granted."); return false
        }
        if (!cleanupPreCapture()) return false

        Log.d(LOG_TAG, "Starting native socket listener at $socketPath...")
        val listenerStarted = try { JniBridge.nativeStartSocketListener(socketPath) } catch (t: Throwable) { Log.e(LOG_TAG, "nativeStartSocketListener failed", t); false }
        if (!listenerStarted) { Log.e(LOG_TAG, "Failed to start native socket listener."); return false }
        Log.i(LOG_TAG, "Native socket listener started successfully.")

        startLoggingData()

        val pcapdLaunched = launchPcapdProcess()
        if (!pcapdLaunched) {
            stopLoggingData()
            JniBridge.nativeStopSocketListener(); return false
        }
        delay(1500)
        readPcapdPid()
        if (pcapdPid <= 0) Log.w(LOG_TAG, "pcapd PID not found/invalid after start.")
        else Log.i(LOG_TAG, "pcapd process confirmed running with PID: $pcapdPid")

        Log.i(LOG_TAG, "Capture successfully initiated.")
        isCaptureRunning = true
        return true
    }

    suspend fun stopCapture() {
        if (!isCaptureRunning) { Log.w(LOG_TAG, "Capture is not active."); return }
        Log.i(LOG_TAG, "Stopping capture...")
        stopLoggingData()
        stopCaptureInternal()
        isCaptureRunning = false
        Log.i(LOG_TAG, "Capture stopped.")
    }

    private suspend fun stopCaptureInternal() = withContext(Dispatchers.IO) {
        try { JniBridge.nativeStopSocketListener() } catch (t: Throwable) { Log.e(LOG_TAG, "nativeStopSocketListener error", t) }
        Log.d(LOG_TAG, "Native socket listener stop requested.")
        killPcapdProcess()
        File(socketPath).delete()
        File(pidPath).delete()
    }

    fun cleanup() {
        Log.i(LOG_TAG, "Cleaning up PcapdDataSource...")
        stopLoggingData()
        dataSourceScope.launch { stopCaptureInternal() }
        dataSourceScope.cancel("PcapdDataSource cleanup requested")

        try { JniBridge.nativeCleanup() } catch (t: Throwable) { Log.e(LOG_TAG, "nativeCleanup error", t)}
        Log.i(LOG_TAG, "PcapdDataSource cleanup complete.")
    }

    private suspend fun cleanupPreCapture(): Boolean = withContext(Dispatchers.IO){
        Log.d(LOG_TAG, "Cleaning up before capture start...")
        killPcapdProcess()

        rootDataSource.executeRootCommand("pkill -f libpcapd.so")
        delay(200)

        File(socketPath).delete()
        File(pidPath).delete()
        return@withContext true
    }

    private suspend fun launchPcapdProcess(): Boolean = withContext(Dispatchers.IO) {
        val pcapdPath = getPcapdExecutablePath()
        if (pcapdPath == null) {
            Log.e(LOG_TAG, "libpcapd.so not found or not executable.")
            return@withContext false
        }
        val cacheDirPath = context.cacheDir.absolutePath
        val fullLogPath = File(cacheDirPath, LOG_FILE).absolutePath
        val pcapdCommand = "cd '$cacheDirPath' && '$pcapdPath' -d -t -u -1 -l '$fullLogPath' -i @inet"

        Log.d(LOG_TAG, "Executing root command: su -c \"$pcapdCommand\"")
        val success = rootDataSource.executeRootCommand(pcapdCommand)
        if (!success) {
            Log.e(LOG_TAG, "Failed to execute pcapd start command via root.")
        }
        return@withContext success
    }
    private suspend fun killPcapdProcess() = withContext(Dispatchers.IO) {
        readPcapdPid()
        if (pcapdPid > 0) {
            Log.i(LOG_TAG, "Attempting to kill pcapd process (PID: $pcapdPid)...")
            var killed = rootDataSource.executeRootCommand("kill -TERM $pcapdPid")
            delay(300)
            val stillRunning = rootDataSource.executeRootCommand("kill -0 $pcapdPid")
            if (stillRunning) {
                Log.w(LOG_TAG, "pcapd (PID: $pcapdPid) didn't stop with TERM, sending KILL...")
                killed = rootDataSource.executeRootCommand("kill -KILL $pcapdPid")
                delay(200)
            }
            if (killed) Log.i(LOG_TAG, "pcapd process (PID: $pcapdPid) kill command sent.")
            else Log.e(LOG_TAG, "Failed to send kill signal to pcapd (PID: $pcapdPid).")
            pcapdPid = -1
            File(pidPath).delete()
        } else {
            Log.d(LOG_TAG, "No pcapd PID found, attempting pkill...")
            rootDataSource.executeRootCommand("pkill -f libpcapd.so")
        }
    }

    private fun readPcapdPid() {
        try {
            val pidFile = File(pidPath)
            pcapdPid = if (pidFile.exists() && pidFile.canRead()) pidFile.readText().trim().toIntOrNull() ?: -1 else -1
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error reading pcapd PID file at $pidPath", e); pcapdPid = -1
        }
    }

    private fun getPcapdExecutablePath(): String? {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val pcapdFile = File(nativeLibDir, "libpcapd.so")
        return if (pcapdFile.exists()) pcapdFile.absolutePath else null
    }
}