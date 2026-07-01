package com.example.data.model

import androidx.annotation.Keep

@Keep
data class Channel(
    val id: String = "",
    val name: String = "",
    val streamUrl: String = "",
    val logoUrl: String = "",
    val category: String = "",
    val isFeatured: Boolean = false,
    val description: String = ""
)
