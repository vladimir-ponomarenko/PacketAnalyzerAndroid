package com.packet.analyzer.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.ui.graphics.vector.ImageVector
import com.packet.analyzer.R


sealed class Screen(
    val route: String,
    @StringRes val resourceId: Int,
    val icon: ImageVector
) {
    object CaptureControl : Screen("capture_control", R.string.nav_capture, Icons.Filled.TrackChanges)
    object AppList : Screen("app_list", R.string.nav_app_list, Icons.Filled.List)
    object Settings : Screen("settings", R.string.nav_settings, Icons.Filled.Settings)

    object AppDetails : Screen(
        route = "app_details/{appUid}",
        resourceId = R.string.app_details_title,
        icon = Icons.Filled.Info
    ) {
        fun createRoute(appUid: Int) = "app_details/$appUid"
    }
}

val bottomNavItems = listOf(
    Screen.Settings,
    Screen.CaptureControl,
    Screen.AppList
)