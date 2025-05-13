package com.packet.analyzer.ui.screens

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.packet.analyzer.R
import com.packet.analyzer.ui.components.AppListItem
import com.packet.analyzer.ui.components.ScreenHeader
import com.packet.analyzer.ui.navigation.Screen
import com.packet.analyzer.ui.viewmodel.AppListViewModel
import com.packet.analyzer.ui.viewmodel.AppListScreenUiState

@Composable
fun AppListScreen(
    navController: NavController,
    viewModel: AppListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(id = R.string.app_list_title))
        // TODO: Добавить кнопку для viewModel.toggleIncludeSystemApps()

        Box(
            modifier = Modifier.weight(1f).fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when {
                uiState.isLoadingApps -> CircularProgressIndicator()
                uiState.error != null -> Text(
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
                uiState.appItems.isEmpty() && !uiState.isLoadingApps -> Text(
                    text = stringResource(R.string.app_list_no_apps_found),
                    modifier = Modifier.padding(16.dp)
                )
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                        items(items = uiState.appItems, key = { it.appInfo.packageName }) { itemData ->
                            AppListItem(
                                itemData = itemData,
                                onDetailsClick = { packageName ->
                                    val uid = try {
                                        context.packageManager.getPackageUid(packageName, 0)
                                    } catch (e: PackageManager.NameNotFoundException) {
                                        Log.w("AppListScreen", "UID not found for $packageName on click", e)
                                        -1
                                    } catch (e: Exception) {
                                        Log.e("AppListScreen", "Error getting UID for $packageName on click", e)
                                        -1
                                    }

                                    if (uid != -1) {
                                        navController.navigate(Screen.AppDetails.createRoute(uid))
                                    } else {
                                        Log.e("AppListScreen", "Could not navigate: Invalid UID for $packageName")

                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}