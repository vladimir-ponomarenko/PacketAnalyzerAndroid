package com.packet.analyzer.data.datasource.root

import android.content.Context
import android.util.Log
import com.packet.analyzer.data.util.RootStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RootDataSource @Inject constructor(private val context: Context) {

    private val _rootStatusFlow = MutableStateFlow(RootStatus.UNKNOWN)
    val rootStatusFlow: Flow<RootStatus> = _rootStatusFlow.asStateFlow()

    init {

        checkRootAccessInternal()
    }

    fun getCurrentRootStatus(): RootStatus = _rootStatusFlow.value

    private fun checkRootAccessInternal() {


        try {
            val process = Runtime.getRuntime().exec("su -c id")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("exit\n")
            os.flush()
            os.close()
            val exitCode = process.waitFor()
            _rootStatusFlow.update {
                if (exitCode == 0) RootStatus.GRANTED else RootStatus.DENIED
            }
            Log.i("RootDataSource", "Root check finished with exit code: $exitCode")
        } catch (e: Exception) {
            Log.e("RootDataSource", "Root check failed", e)
            _rootStatusFlow.update { RootStatus.DENIED }
        }
    }

    suspend fun checkOrRequestRootAccess(): Boolean = withContext(Dispatchers.IO) {
        checkRootAccessInternal()
        delay(100)
        return@withContext _rootStatusFlow.value == RootStatus.GRANTED
    }

    suspend fun executeRootCommand(command: String): Boolean = withContext(Dispatchers.IO) {
        var process: Process? = null
        var os: DataOutputStream? = null
        var success = false
        Log.d("RootDataSource", "Executing command: su -c \"$command\"")
        try {
            process = Runtime.getRuntime().exec("su")
            os = DataOutputStream(process.outputStream)
            os.writeBytes("$command\n")
            os.flush()
            os.writeBytes("exit\n")
            os.flush()


            // val reader = BufferedReader(InputStreamReader(process.inputStream))
            // var line: String?
            // while (reader.readLine().also { line = it } != null) {
            //     Log.d("RootDataSource", "su stdout: $line")
            // }
            // val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            // while (errorReader.readLine().also { line = it } != null) {
            //      Log.e("RootDataSource", "su stderr: $line")
            // }


            val exitCode = process.waitFor()
            success = (exitCode == 0)
            Log.i("RootDataSource", "Command exited with code: $exitCode. Success: $success")

        } catch (e: Exception) {
            Log.e("RootDataSource", "Failed to execute root command: $command", e)
            success = false
        } finally {
            try {
                os?.close()
            } catch (e: Exception) { /* ignore */ }
            process?.destroy()
        }
        return@withContext success
    }
}