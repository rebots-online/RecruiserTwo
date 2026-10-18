package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "saved_viewpoints",
    foreignKeys = [
        ForeignKey(
            entity = ModelItem::class,
            parentColumns = ["id"],
            childColumns = ["modelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["modelId"])]
)
data class SavedViewpoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val modelId: Long,
    val title: String,
    val posX: Float,
    val posY: Float,
    val posZ: Float,
    val targetX: Float,
    val targetY: Float,
    val targetZ: Float,
    val timestamp: Long = System.currentTimeMillis()
)
