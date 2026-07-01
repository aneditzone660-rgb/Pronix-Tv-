package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteChannelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String,
    val category: String,
    val isFeatured: Boolean,
    val description: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "recent_history")
data class RecentChannelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String,
    val category: String,
    val isFeatured: Boolean,
    val description: String,
    val watchedAt: Long = System.currentTimeMillis(),
    val playbackPosition: Long = 0L
)
