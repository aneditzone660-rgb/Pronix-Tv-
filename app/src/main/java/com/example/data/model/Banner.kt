package com.example.data.model

import androidx.annotation.Keep

@Keep
data class Banner(
    val id: String = "",
    val title: String = "",
    val imageUrl: String = "",
    val actionUrl: String = "",
    val channelId: String = ""
)
