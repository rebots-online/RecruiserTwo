package com.example.data.repository

import com.example.data.db.ModelDao
import com.example.data.model.ModelItem
import com.example.data.model.SavedViewpoint
import kotlinx.coroutines.flow.Flow

class ModelRepository(private val modelDao: ModelDao) {
    val allModels: Flow<List<ModelItem>> = modelDao.getAllModels()

    fun getViewpointsForModel(modelId: Long): Flow<List<SavedViewpoint>> {
        return modelDao.getViewpointsForModel(modelId)
    }

    suspend fun getModelById(id: Long): ModelItem? {
        return modelDao.getModelById(id)
    }

    suspend fun insertModel(model: ModelItem): Long {
        return modelDao.insertModel(model)
    }

    suspend fun deleteModel(model: ModelItem) {
        modelDao.deleteModel(model)
    }

    suspend fun insertViewpoint(viewpoint: SavedViewpoint): Long {
        return modelDao.insertViewpoint(viewpoint)
    }

    suspend fun deleteViewpoint(viewpoint: SavedViewpoint) {
        modelDao.deleteViewpoint(viewpoint)
    }
}
