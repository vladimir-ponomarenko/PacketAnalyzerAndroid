package com.packet.analyzer.data.datasource.app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.core.content.ContextCompat
import com.packet.analyzer.R
import com.packet.analyzer.data.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppInfoDataSource @Inject constructor(
    private val context: Context
) {
    private val packageManager: PackageManager = context.packageManager
    private val defaultIcon: Drawable by lazy {
        ContextCompat.getDrawable(context, R.mipmap.ic_launcher)!!
    }

    suspend fun getInstalledApps(includeSystemApps: Boolean): List<AppInfo> = withContext(Dispatchers.IO) {
        val apps = mutableListOf<AppInfo>()
        Log.d("AppInfoDataSource", "Fetching apps... includeSystemApps = $includeSystemApps")
        try {
            val applications = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            Log.d("AppInfoDataSource", "Found ${applications.size} total packages.")

            for (appInfo in applications) {
                val packageName = appInfo.packageName
                val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                Log.v("AppInfoDataSource", "Processing: $packageName, isSystem: $isSystemApp")

                if (!includeSystemApps && isSystemApp) {
                    Log.v("AppInfoDataSource", "Skipping system app: $packageName")
                    continue
                }

                try {
                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    val icon = try {
                        packageManager.getApplicationIcon(appInfo)
                    } catch (e: PackageManager.NameNotFoundException) {
                        Log.w("AppInfoDataSource", "Icon not found for package: $packageName")
                        defaultIcon
                    }
                    apps.add(AppInfo(appName = appName, packageName = packageName, icon = icon))
                    Log.v("AppInfoDataSource", "Added app: $appName ($packageName)")

                } catch (e: Exception) {
                    Log.e("AppInfoDataSource", "Error processing app info for: $packageName", e)
                }
            }
            apps.sortBy { it.appName.lowercase() }
            Log.d("AppInfoDataSource", "Finished processing. Returning ${apps.size} apps.")

        } catch (e: Exception) {
            Log.e("AppInfoDataSource", "Error getting installed applications list", e)
            return@withContext emptyList<AppInfo>()
        }
        return@withContext apps
    }
}