package com.packet.analyzer.data.repository

import kotlinx.coroutines.flow.StateFlow

// Интерфейс для взаимодействия с данными трафика и управлением захватом
interface TrafficRepository {

    // Получить Flow с обновлениями статуса захвата (запущен/остановлен)
    fun getCaptureStatusUpdates(): StateFlow<Boolean>

    // TODO: Добавить Flow для получения данных о трафике
    // fun getTrafficUpdates(): Flow<List<AppTrafficInfo>>

    // Проверить наличие root-доступа
    suspend fun checkRootAccess(): Boolean

    // Начать захват трафика
    suspend fun startCapture()

    // Остановить захват трафика
    suspend fun stopCapture()

    // TODO: Добавить метод для получения списка приложений
    // suspend fun getAppList(): List<AppData>
}