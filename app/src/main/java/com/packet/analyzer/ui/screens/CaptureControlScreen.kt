package com.packet.analyzer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.packet.analyzer.data.util.RootStatus
import com.packet.analyzer.ui.viewmodel.CaptureControlViewModel
import com.packet.analyzer.ui.viewmodel.CaptureUiState

@Composable
fun CaptureControlScreen(
    viewModel: CaptureControlViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    CaptureControlContent(
        uiState = uiState,
        onCaptureToggle = viewModel::toggleCaptureState,
        onClearError = viewModel::clearError
    )
}

@Composable
fun CaptureControlContent(
    uiState: CaptureUiState,
    onCaptureToggle: () -> Unit,
    onClearError: () -> Unit
) {
    val statusTextColor = when {
        uiState.error != null -> MaterialTheme.colorScheme.error
        uiState.rootStatus == RootStatus.DENIED || uiState.rootStatus == RootStatus.UNKNOWN -> Color(0xFFFFA726)
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
            onClick = onCaptureToggle,
            modifier = Modifier
                .size(200.dp),
            shape = CircleShape,
            enabled = uiState.isButtonEnabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (uiState.isCapturing) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(
                text = stringResource(id = uiState.buttonTextResId),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(id = uiState.statusTextResId),
            style = MaterialTheme.typography.titleMedium,
            color = statusTextColor,
            textAlign = TextAlign.Center
        )

        uiState.error?.let { errorMsg ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = errorMsg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}