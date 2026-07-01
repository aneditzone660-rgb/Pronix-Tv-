package com.example.data.model

import androidx.annotation.Keep

@Keep
data class Notice(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val createdAt: Long = 0L,
    val type: String = "info"
)
