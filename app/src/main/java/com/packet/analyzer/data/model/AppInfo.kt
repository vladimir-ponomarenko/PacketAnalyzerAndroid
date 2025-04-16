package com.packet.analyzer.data.model

import android.graphics.drawable.Drawable

// Хранение информации о приложении
data class AppInfo(
    val appName: String,
    val packageName: String,
    val icon: Drawable
/*
*
* В будущем, когда добавится обработка пакетов, здесь будут ещё поля
*
*/

)