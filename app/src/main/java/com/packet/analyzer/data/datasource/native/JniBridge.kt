package com.packet.analyzer.data.datasource.native

import android.util.Log
import com.packet.analyzer.data.model.PcapHeaderInfo
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * JNI мост для взаимодействия с нативной библиотекой socketclient.
 */
object JniBridge {

    /**
     * Класс для хранения полной информации о заголовке пакета, полученной от pcapd.
     */


    private val _packetHeaderFlow = MutableSharedFlow<PcapHeaderInfo>(
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val packetHeaderFlow: SharedFlow<PcapHeaderInfo> = _packetHeaderFlow.asSharedFlow()

    /**
     * Колбэк, вызываемый из C/C++.
     */
    @JvmStatic
    fun onPacketHeaderReceivedCallback(
        uid: Int,
        totalLen: Long,
        ipLen: Long,
        proto: Int,
        isUl: Boolean,
        tsSec: Long,
        tsUsec: Long,
        drops: Int,
        ifId: Int
    ) {

        val headerInfo = PcapHeaderInfo(
            uid = uid,
            totalLength = totalLen,
            ipLayerLength = ipLen,
            protocol = proto,
            isUplink = isUl,
            timestampSec = tsSec,
            timestampUsec = tsUsec,
            packetDrops = drops,
            interfaceId = ifId
        )

        val emitted = _packetHeaderFlow.tryEmit(headerInfo)
        if (!emitted) {
            if (System.currentTimeMillis() % 2000 < 50) {
                Log.w("JniCallback", "Packet header buffer full or no subscribers? Dropping oldest.")
            }
        }
    }

    external fun nativeStartSocketListener(socketPath: String): Boolean
    external fun nativeStopSocketListener()
    external fun nativeCleanup()

    init {
        try {
            System.loadLibrary("socketclient")
            Log.i("JniBridge", "Native library 'socketclient' loaded.")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("JniBridge", "FATAL: Failed to load 'socketclient'", e)
        } catch (t: Throwable) {
            Log.e("JniBridge", "FATAL: Unknown error loading 'socketclient'", t)
        }
    }

}