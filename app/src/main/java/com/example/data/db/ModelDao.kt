package com.example.data.db

import androidx.room.*
import com.example.data.model.ModelItem
import com.example.data.model.SavedViewpoint
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {
    @Query("SELECT * FROM model_items ORDER BY addedTimestamp DESC")
    fun getAllModels(): Flow<List<ModelItem>>

    @Query("SELECT * FROM model_items WHERE id = :id")
    suspend fun getModelById(id: Long): ModelItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: ModelItem): Long

    @Delete
    suspend fun deleteModel(model: ModelItem)

    @Query("SELECT * FROM saved_viewpoints WHERE modelId = :modelId ORDER BY timestamp ASC")
    fun getViewpointsForModel(modelId: Long): Flow<List<SavedViewpoint>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertViewpoint(viewpoint: SavedViewpoint): Long

    @Delete
    suspend fun deleteViewpoint(viewpoint: SavedViewpoint)
}
