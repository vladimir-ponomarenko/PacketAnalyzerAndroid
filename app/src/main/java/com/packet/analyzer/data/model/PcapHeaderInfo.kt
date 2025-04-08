package com.packet.analyzer.data.model

/**
 * Класс для хранения полной информации о заголовке пакета, полученной от pcapd.
 */
data class PcapHeaderInfo(
    val uid: Int,
    val totalLength: Long,       // Полный размер кадра (hdr.len)
    val ipLayerLength: Long,     // Размер IP-пакета (hdr.len - dlt_offset)
    val protocol: Int,           // Код транспортного протокола (6=TCP, 17=UDP, 1=ICMP, etc.)
    val isUplink: Boolean,       // true, если исходящий пакет (TX)
    val timestampSec: Long,      // Секунды временной метки
    val timestampUsec: Long,     // Микросекунды временной метки
    val packetDrops: Int,        // Количество отброшенных пакетов до этого
    val interfaceId: Int         // ID интерфейса (если pcapd запущен с несколькими -i)
)