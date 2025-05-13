package com.packet.analyzer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.packet.analyzer.R
import com.packet.analyzer.ui.viewmodel.CaptureControlViewModel
import com.packet.analyzer.ui.viewmodel.CaptureControlScreenUiState
import com.packet.analyzer.util.FormatUtils

@Composable
fun CaptureControlScreen(
    viewModel: CaptureControlViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    CaptureControlContent(
        uiState = uiState,
        onToggleCapture = viewModel::toggleCaptureState,
        onCheckRoot = { viewModel.checkRootAccess(showLoading = true) },
        onClearError = viewModel::clearError
    )
}

@Composable
fun CaptureControlContent(
    uiState: CaptureControlScreenUiState,
    onToggleCapture: () -> Unit,
    onCheckRoot: () -> Unit,
    onClearError: () -> Unit
) {
    val statusTextColor = when {
        uiState.error != null -> MaterialTheme.colorScheme.error
        uiState.rootStatus == com.packet.analyzer.data.util.RootStatus.DENIED ||
                uiState.rootStatus == com.packet.analyzer.data.util.RootStatus.UNKNOWN -> Color(0xFFFFA726)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Button(
            onClick = onToggleCapture,
            modifier = Modifier.size(180.dp),
            shape = CircleShape,
            enabled = uiState.isButtonEnabled,
            colors = ButtonDefaults.buttonColors(

                containerColor = if (uiState.isCapturing) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                contentColor = if (uiState.isCapturing) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimary,

                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            ),
            contentPadding = PaddingValues(16.dp)
        ) {
            Text(
                text = stringResource(id = uiState.buttonTextResId),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                // maxLines = 2,
                // softWrap = true
            )
        }

        Spacer(modifier = Modifier.height(24.dp))


        Text(
            text = stringResource(id = uiState.statusTextResId),
            style = MaterialTheme.typography.titleMedium,
            color = statusTextColor,
            textAlign = TextAlign.Center
        )


        if (uiState.isCapturing || uiState.totalSessionPackets > 0) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Session: ${FormatUtils.formatBytes(uiState.totalSessionTraffic)} (${uiState.totalSessionPackets} packets)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }


        uiState.error?.let { errorMsg ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = errorMsg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.clickable(onClick = onClearError)
            )
        }


        if (uiState.rootStatus != com.packet.analyzer.data.util.RootStatus.GRANTED && !uiState.isOperationInProgress && !uiState.isCapturing) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onCheckRoot) {
                Text("Check Root Access") // TODO: Строковый ресурс
            }
        }
    }
}