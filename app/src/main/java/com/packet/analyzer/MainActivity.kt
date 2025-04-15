package com.packet.analyzer

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import android.os.Bundle
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.packet.analyzer.ui.components.AppBottomNavigationBar
import com.packet.analyzer.ui.navigation.Screen
import com.packet.analyzer.ui.screens.AppListScreen
import com.packet.analyzer.ui.screens.CaptureControlScreen
import com.packet.analyzer.ui.screens.SettingsScreen
import com.packet.analyzer.ui.theme.PacketAnalyzerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PacketAnalyzerTheme {
                AppContent()
            }
        }
    }
}

@Composable
fun AppContent() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = { AppBottomNavigationBar(navController = navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.CaptureControl.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.CaptureControl.route) {
                CaptureControlScreen()
            }
            composable(Screen.AppList.route) {
                AppListScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }
}