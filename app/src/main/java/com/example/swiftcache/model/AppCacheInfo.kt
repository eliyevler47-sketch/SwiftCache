package com.example.swiftcache.model

import android.graphics.drawable.Drawable

data class AppCacheInfo(
    val packageName: String,
    val appName: String,
    val cacheSize: Long,
    val icon: Drawable?,
    var isSelected: Boolean = true
)
