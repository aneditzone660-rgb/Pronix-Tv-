package com.example.data.model

import androidx.annotation.Keep

@Keep
data class Category(
    val id: String = "",
    val name: String = "",
    val iconUrl: String = ""
)
