package com.packet.analyzer.data.util

import androidx.datastore.preferences.core.stringPreferencesKey

object PreferencesKeys {
    // Ключ для сохранения статуса root в DataStore
    val ROOT_STATUS = stringPreferencesKey("root_status")
}

enum class RootStatus {
    GRANTED, // Доступ предоставлен
    DENIED,  // Доступ не предоставлен или в запросе отказано
    UNKNOWN  // Статус еще не проверялся
}