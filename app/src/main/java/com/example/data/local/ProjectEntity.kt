package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "studio_projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val aspectRatio: String,
    val previewResolution: String,
    val thumbnailDrawable: String,
    val totalDurationMs: Long,
    val lastEditedAt: Long,
    val clipCount: Int,
    val payloadJson: String
)
