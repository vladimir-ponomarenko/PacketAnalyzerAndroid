package com.packet.analyzer.ui.screens


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.packet.analyzer.ui.viewmodel.CaptureControlViewModel
import com.packet.analyzer.ui.viewmodel.CaptureUiState


@Composable
fun CaptureControlScreen(
    viewModel: CaptureControlViewModel = viewModel()
) {
    // Подписываемся на состояние UI из ViewModel
    val uiState by viewModel.uiState.collectAsState()

    CaptureControlContent(
        uiState = uiState,
        onCaptureToggle = { viewModel.toggleCaptureState() } // Передаем действие в ViewModel
    )
}

@Composable
fun CaptureControlContent(
    uiState: CaptureUiState,
    onCaptureToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(
            onClick = onCaptureToggle,
            modifier = Modifier
                .size(200.dp),
            shape = CircleShape,
            enabled = uiState.isButtonEnabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (uiState.isCapturing) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = stringResource(id = uiState.buttonTextResId),
                style = MaterialTheme.typography.headlineSmall
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(id = uiState.statusTextResId),
            style = MaterialTheme.typography.titleMedium
        )
    }
}