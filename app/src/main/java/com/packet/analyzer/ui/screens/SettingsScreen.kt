package com.packet.analyzer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.packet.analyzer.R
import com.packet.analyzer.data.util.RootStatus
import com.packet.analyzer.ui.viewmodel.SettingsUiState
import com.packet.analyzer.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    SettingsContent(
        uiState = uiState,
        onRootToggle = { shouldRequest ->
            if (shouldRequest) {
                viewModel.checkOrRequestRoot()
            }
        }
    )
}

@Composable
fun SettingsContent(
    uiState: SettingsUiState,
    onRootToggle: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(id = R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 16.dp)
        )

        HorizontalDivider(
            modifier = Modifier.padding(bottom = 24.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )


        // Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = R.string.settings_root_access),
                    style = MaterialTheme.typography.titleLarge
                )
                val statusTextRes = when(uiState.rootStatus) {
                    RootStatus.GRANTED -> R.string.settings_root_status_granted
                    RootStatus.DENIED -> R.string.settings_root_status_denied
                    RootStatus.UNKNOWN -> R.string.settings_root_status_unknown
                }
                Text(
                    text = stringResource(id = statusTextRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            if (uiState.isCheckingRoot) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            } else {
                Switch(
                    checked = uiState.rootStatus == RootStatus.GRANTED,
                    onCheckedChange = onRootToggle
                )
            }
        }

/*
*
*
*       Другие настройки ...
*
*
*/


    }
}