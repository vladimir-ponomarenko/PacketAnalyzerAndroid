package com.packet.analyzer.data.datasource.native

import kotlinx.coroutines.flow.SharedFlow

interface NativeTrafficSource {

    val packetFlow: SharedFlow<Pair<Int, Int>>

    fun startCapture(): Boolean

    fun stopCapture()

    fun requestStatsDump()

    fun cleanup()
}