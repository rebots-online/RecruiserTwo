package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "model_items")
data class ModelItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String, // "glb" or "ply"
    val fileUri: String, // Local file path, content URI, or "sample"
    val isSample: Boolean,
    val sampleAssetPath: String = "", // Reference to preset assets
    val description: String = "",
    val addedTimestamp: Long = System.currentTimeMillis()
)
