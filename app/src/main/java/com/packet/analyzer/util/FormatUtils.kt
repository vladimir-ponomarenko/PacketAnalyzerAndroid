package com.packet.analyzer.util

import java.util.Locale

object FormatUtils {

    /**
     * Форматирует количество байт в читаемый вид (B, KB, MB, GB).
     * @param bytes Количество байт.
     * @return Форматированная строка.
     */
    fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "0 B"
        if (bytes < 1024) return "$bytes B"

        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)

        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)

        val gb = mb / 1024.0
        return String.format(Locale.US, "%.1f GB", gb)
    }
}