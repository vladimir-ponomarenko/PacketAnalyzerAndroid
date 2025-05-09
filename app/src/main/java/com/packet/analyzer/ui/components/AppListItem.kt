package com.packet.analyzer.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.packet.analyzer.data.model.AppInfo

@Composable
fun AppListItem(
    appInfo: AppInfo,
    onDetailsClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onDetailsClick(appInfo.packageName) },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            appInfo.icon?.let {
                Image(
                    painter = rememberAsyncImagePainter(model = it),
                    contentDescription = "${appInfo.appName} icon",
                    modifier = Modifier.size(48.dp)
                )
            } ?: Box(modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)))


            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appInfo.appName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = appInfo.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }


            IconButton(onClick = { onDetailsClick(appInfo.packageName) }) {
                Icon(
                    imageVector = Icons.Filled.Analytics,
                    contentDescription = "App Details",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}