package com.example.data.model

import androidx.annotation.Keep

@Keep
data class AppUpdate(
    val latestVersionCode: Int = 1,
    val latestVersionName: String = "1.0.0",
    val isForceUpdate: Boolean = false,
    val updateUrl: String = "",
    val releaseNotes: String = ""
)
