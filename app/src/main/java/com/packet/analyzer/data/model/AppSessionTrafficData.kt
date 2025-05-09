package com.packet.analyzer.data.model

import java.util.LinkedList

const val MAX_GRAPH_PACKET_SAMPLES = 10000

data class AppSessionTrafficData(
    val uid: Int,
    val appInfo: AppInfo? = null,
    val totalBytes: Long = 0L,
    val uplinkBytes: Long = 0L,
    val downlinkBytes: Long = 0L,
    val totalPackets: Long = 0L,

    val packetWeights: LinkedList<Long> = LinkedList(),

    val weightDistributionForChart: Map<Float, Int> = emptyMap()
) {
    fun updatedWith(header: PcapHeaderInfo): AppSessionTrafficData {
        if (header.uid != this.uid) return this

        val newTotalBytes = this.totalBytes + header.ipLayerLength
        val newUplinkBytes = if (header.isUplink) this.uplinkBytes + header.ipLayerLength else this.uplinkBytes
        val newDownlinkBytes = if (!header.isUplink) this.downlinkBytes + header.ipLayerLength else this.downlinkBytes
        val newTotalPackets = this.totalPackets + 1


        val newPacketWeights = LinkedList(this.packetWeights)
        newPacketWeights.add(header.ipLayerLength)
        while (newPacketWeights.size > MAX_GRAPH_PACKET_SAMPLES) {
            newPacketWeights.removeFirst()
        }


        val newWeightDistribution = newPacketWeights
            .groupBy { it }
            .map { entry -> entry.key.toFloat() to entry.value.size }
            .toMap()

        return this.copy(
            totalBytes = newTotalBytes,
            uplinkBytes = newUplinkBytes,
            downlinkBytes = newDownlinkBytes,
            totalPackets = newTotalPackets,
            packetWeights = newPacketWeights,
            weightDistributionForChart = newWeightDistribution
        )
    }
}