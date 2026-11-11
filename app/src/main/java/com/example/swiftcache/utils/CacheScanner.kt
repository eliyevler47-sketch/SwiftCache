package com.example.swiftcache.utils

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import com.example.swiftcache.model.AppCacheInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

object CacheScanner {

    suspend fun scanApps(context: Context): List<AppCacheInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        
        val storageVolume: StorageVolume? = storageManager.storageVolumes.firstOrNull { it.isPrimary }
        val uuid: UUID = if (storageVolume?.uuid != null) {
            UUID.fromString(storageVolume.uuid)
        } else {
            StorageManager.UUID_DEFAULT
        }

        val appsList = mutableListOf<AppCacheInfo>()
        val installedPackages = pm.getInstalledPackages(0)

        for (pkg in installedPackages) {
            val appInfo = pkg.applicationInfo ?: continue
            
            // Yalnız istifadəçinin sonradan yüklədiyi tətbiqləri yoxlayırıq
            if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue

            try {
                val stats = storageStatsManager.queryStatsForUid(uuid, appInfo.uid)
                val cacheBytes = stats.cacheBytes
                
                if (cacheBytes > 0) {
                    appsList.add(
                        AppCacheInfo(
                            packageName = pkg.packageName,
                            appName = pm.getApplicationLabel(appInfo).toString(),
                            cacheSize = cacheBytes,
                            icon = pm.getApplicationIcon(pkg.packageName)
                        )
                    )
                }
            } catch (e: Exception) {
                // Bəzi tətbiqlərin məlumatına giriş bağlı ola bilər
                continue
            }
        }
        
        appsList.sortByDescending { it.cacheSize }
        return@withContext appsList
    }
    
    fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
