package com.packet.analyzer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.packet.analyzer.R
import com.packet.analyzer.data.model.MAX_GRAPH_PACKET_SAMPLES
import com.packet.analyzer.ui.components.AppTrafficChartView
import com.packet.analyzer.ui.viewmodel.AppDetailsScreenState
import com.packet.analyzer.ui.viewmodel.AppDetailsViewModel
import com.packet.analyzer.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailsScreen(
    appUid: Int,
    navController: NavController,
    viewModel: AppDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.appInfo?.appName ?: stringResource(R.string.app_details_loading_uid, appUid),
                        maxLines = 1,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        AppDetailsContent(
            uiState = uiState,
            appUid = appUid,
            modifier = Modifier.padding(paddingValues)
        )
    }
}

@Composable
fun AppDetailsContent(
    uiState: AppDetailsScreenState,
    appUid: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        if (uiState.isLoading && uiState.appInfo == null) {
            Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
                Text(stringResource(R.string.app_details_loading), modifier = Modifier.padding(top = 70.dp))
            }
        } else if (uiState.errorMessage != null) {
            Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                Text(text = uiState.errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 18.sp, textAlign = TextAlign.Center)
            }
        } else {
            uiState.appInfo?.let { info ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(info.appName, style = MaterialTheme.typography.headlineSmall)
                        Text(info.packageName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } ?: run {
                Text(
                    stringResource(R.string.app_details_app_not_found_uid, appUid),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                StatItem(stringResource(R.string.app_details_total_packets), uiState.totalPackets.toString())
                StatItem(stringResource(R.string.app_details_total_traffic), FormatUtils.formatBytes(uiState.totalTraffic))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                StatItem(stringResource(R.string.app_details_uplink), FormatUtils.formatBytes(uiState.uplinkTraffic))
                StatItem(stringResource(R.string.app_details_downlink), FormatUtils.formatBytes(uiState.downlinkTraffic))
            }
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                stringResource(R.string.app_details_graph_title, MAX_GRAPH_PACKET_SAMPLES),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                AppTrafficChartView(
                    entries = uiState.trafficChartEntries,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp)
                )
                Text(
                    text = stringResource(R.string.x_axis_label_packet_weight),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
            }

            if (uiState.trafficChartEntries.isEmpty() && uiState.totalPackets > 0 && !uiState.isLoading) {
                BoxWithCenteredText(stringResource(R.string.app_details_graph_processing))
            } else if (uiState.trafficChartEntries.isEmpty() && uiState.totalPackets == 0L && !uiState.isLoading) {
                BoxWithCenteredText(stringResource(R.string.app_details_graph_no_data_session))
            }

            Spacer(modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.app_details_coming_soon),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BoxWithCenteredText(text: String, height: Dp = 350.dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center
    ) {
        Text(text)
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}