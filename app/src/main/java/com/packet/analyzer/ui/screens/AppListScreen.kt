package com.packet.analyzer.ui.screens

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.github.mikephil.charting.data.Entry
import com.packet.analyzer.R
import com.packet.analyzer.ui.components.AppListItem
import com.packet.analyzer.ui.components.AppTrafficChartView
import com.packet.analyzer.ui.components.ScreenHeader
import com.packet.analyzer.ui.navigation.Screen
import com.packet.analyzer.ui.viewmodel.AppListScreenUiState
import com.packet.analyzer.ui.viewmodel.AppListViewModel
import com.packet.analyzer.util.FormatUtils

@Composable
fun AppListScreen(
    navController: NavController,
    viewModel: AppListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(id = R.string.app_list_title))

        OverallSettingsSection(
            isExpanded = uiState.isOverallSectionExpanded,
            onToggleExpand = viewModel::toggleOverallSectionExpanded,
            includeSystemApps = uiState.includeSystemApps,
            onToggleIncludeSystemApps = viewModel::toggleIncludeSystemApps,
            onNavigateToOverallStats = {
                navController.navigate(Screen.AppDetails.createRoute(Screen.AppDetails.OVERALL_STATS_UID))
            }
        )

        Box(
            modifier = Modifier.weight(1f).fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when {
                uiState.isLoadingApps && uiState.appItems.isEmpty() -> CircularProgressIndicator()
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

@Composable
fun OverallSettingsSection(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    includeSystemApps: Boolean,
    onToggleIncludeSystemApps: () -> Unit,
    onNavigateToOverallStats: () -> Unit
) {
    val rotationAngle by animateFloatAsState(targetValue = if (isExpanded) 0f else -180f, label = "expand_icon_rotation_settings")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.FilterList,
                    contentDescription = stringResource(R.string.overall_stats_title),
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.overall_stats_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onNavigateToOverallStats,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Analytics,
                        contentDescription = stringResource(R.string.overall_stats_details_desc),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (isExpanded) stringResource(R.string.action_collapse) else stringResource(R.string.action_expand),
                    modifier = Modifier.rotate(rotationAngle)
                )
            }
        }
        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onToggleIncludeSystemApps)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.setting_include_system_apps),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = includeSystemApps,
                        onCheckedChange = { onToggleIncludeSystemApps() }
                    )
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}
