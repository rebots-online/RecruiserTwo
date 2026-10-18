package com.example.ui.viewer

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.ModelItem
import com.example.data.model.SavedViewpoint
import com.example.data.repository.ModelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class ModelViewModel(private val repository: ModelRepository) : ViewModel() {

    // UI States
    private val _activeModel = MutableStateFlow<ModelItem?>(null)
    val activeModel: StateFlow<ModelItem?> = _activeModel.asStateFlow()

    val modelsList: StateFlow<List<ModelItem>> = repository.allModels
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Viewpoints Flow for the active model
    val viewpointsList: StateFlow<List<SavedViewpoint>> = _activeModel
        .flatMapLatest { model ->
            if (model != null) {
                repository.getViewpointsForModel(model.id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Live Camera Parameters (synced from WebView)
    private val _cameraPosition = MutableStateFlow(floatArrayOf(6f, 4f, 8f))
    val cameraPosition: StateFlow<FloatArray> = _cameraPosition.asStateFlow()

    private val _cameraTarget = MutableStateFlow(floatArrayOf(0f, 0f, 0f))
    val cameraTarget: StateFlow<FloatArray> = _cameraTarget.asStateFlow()

    // Mesh stats
    private val _verticesCount = MutableStateFlow(0)
    val verticesCount: StateFlow<Int> = _verticesCount.asStateFlow()

    private val _polygonsCount = MutableStateFlow(0)
    val polygonsCount: StateFlow<Int> = _polygonsCount.asStateFlow()

    // Loading & Error feedback
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // WebGL Controls States
    private val _renderMode = MutableStateFlow("solid") // "solid", "wireframe", "points"
    val renderMode: StateFlow<String> = _renderMode.asStateFlow()

    private val _pointSize = MutableStateFlow(0.05f)
    val pointSize: StateFlow<Float> = _pointSize.asStateFlow()

    private val _lightIntensity = MutableStateFlow(1.2f)
    val lightIntensity: StateFlow<Float> = _lightIntensity.asStateFlow()

    private val _autoRotate = MutableStateFlow(false)
    val autoRotate: StateFlow<Boolean> = _autoRotate.asStateFlow()

    private val _gridVisible = MutableStateFlow(true)
    val gridVisible: StateFlow<Boolean> = _gridVisible.asStateFlow()

    private val _backgroundColor = MutableStateFlow("#0d0f14")
    val backgroundColor: StateFlow<String> = _backgroundColor.asStateFlow()

    // Selected Viewpoint to trigger smooth navigation
    private val _navigateEvent = MutableSharedFlow<SavedViewpoint>(replay = 0)
    val navigateEvent: SharedFlow<SavedViewpoint> = _navigateEvent.asSharedFlow()

    init {
        // Pre-insert default sample models if database is empty
        viewModelScope.launch {
            // Query database directly to bypass stateflow initial value
            val currentModels = repository.allModels.first()
            if (currentModels.isEmpty()) {
                insertPredefinedSamples()
            } else {
                // Auto-set the first model as active
                _activeModel.value = currentModels.first()
            }
        }
    }

    private suspend fun insertPredefinedSamples() {
        val sample1 = ModelItem(
            name = "Space Station Core Hub",
            type = "glb",
            fileUri = "sample:station",
            isSample = true,
            sampleAssetPath = "station",
            description = "A procedural 3D Sci-Fi Orbital Station. Excellent for testing translation, metallic reflections, and orbit limits in VR space."
        )
        val sample2 = ModelItem(
            name = "LIDAR Corridor Scan",
            type = "ply",
            fileUri = "sample:scan",
            isSample = true,
            sampleAssetPath = "scan",
            description = "A synthesized 3D point cloud scanner scan of a cave tunnel. Best for testing particle attenuation sizes, mesh wireframes, and camera navigation checkpoints."
        )
        val id1 = repository.insertModel(sample1)
        val id2 = repository.insertModel(sample2)

        // Insert some default navigation viewpoints to test immediately!
        repository.insertViewpoint(
            SavedViewpoint(
                modelId = id1,
                title = "Solar Panels Wing",
                posX = 7.0f, posY = 2.0f, posZ = 12.0f,
                targetX = 0f, targetY = 0f, targetZ = 0f
            )
        )
        repository.insertViewpoint(
            SavedViewpoint(
                modelId = id1,
                title = "Interior Hub Core",
                posX = 0.5f, posY = 0.5f, posZ = 1.0f,
                targetX = 0f, targetY = 0f, targetZ = 0f
            )
        )
        repository.insertViewpoint(
            SavedViewpoint(
                modelId = id2,
                title = "Scanned Entry Corridor",
                posX = 0.0f, posY = 3.0f, posZ = 15.0f,
                targetX = 0.0f, targetY = 1.0f, targetZ = 0.0f
            )
        )
        repository.insertViewpoint(
            SavedViewpoint(
                modelId = id2,
                title = "Subterranean Deep Chamber",
                posX = -2.0f, posY = -1.0f, posZ = -10.0f,
                targetX = 0.0f, targetY = 1.0f, targetZ = -5.0f
            )
        )

        _activeModel.value = repository.getModelById(id1)
    }

    fun selectModel(model: ModelItem) {
        _activeModel.value = model
        // Default PLY scans to points view and GLB models to solid mesh view
        _renderMode.value = if (model.type.lowercase() == "ply") "points" else "solid"
        _errorMessage.value = null
    }

    fun deleteModel(model: ModelItem) {
        viewModelScope.launch {
            repository.deleteModel(model)
            // Delete file if imported
            if (!model.isSample) {
                try {
                    val file = File(model.fileUri)
                    if (file.exists()) file.delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (_activeModel.value?.id == model.id) {
                _activeModel.value = modelsList.value.firstOrNull { it.id != model.id }
            }
        }
    }

    // Viewpoint Management
    fun saveCurrentViewpoint(title: String) {
        val active = _activeModel.value ?: return
        val pos = _cameraPosition.value
        val tgt = _cameraTarget.value

        viewModelScope.launch {
            val viewpoint = SavedViewpoint(
                modelId = active.id,
                title = title,
                posX = pos[0], posY = pos[1], posZ = pos[2],
                targetX = tgt[0], targetY = tgt[1], targetZ = tgt[2]
            )
            repository.insertViewpoint(viewpoint)
        }
    }

    fun deleteViewpoint(viewpoint: SavedViewpoint) {
        viewModelScope.launch {
            repository.deleteViewpoint(viewpoint)
        }
    }

    fun navigateToViewpoint(viewpoint: SavedViewpoint) {
        _cameraPosition.value = floatArrayOf(viewpoint.posX, viewpoint.posY, viewpoint.posZ)
        _cameraTarget.value = floatArrayOf(viewpoint.targetX, viewpoint.targetY, viewpoint.targetZ)
        viewModelScope.launch {
            _navigateEvent.emit(viewpoint)
        }
    }

    // Live WebGL property updates
    fun updateCameraPosition(posX: Float, posY: Float, posZ: Float, targetX: Float, targetY: Float, targetZ: Float) {
        _cameraPosition.value = floatArrayOf(posX, posY, posZ)
        _cameraTarget.value = floatArrayOf(targetX, targetY, targetZ)
    }

    fun updateStats(vertices: Int, polygons: Int) {
        _verticesCount.value = vertices
        _polygonsCount.value = polygons
    }

    fun setRenderMode(mode: String) {
        _renderMode.value = mode
    }

    fun setPointSize(size: Float) {
        _pointSize.value = size
    }

    fun setLightIntensity(intensity: Float) {
        _lightIntensity.value = intensity
    }

    fun setAutoRotate(rotate: Boolean) {
        _autoRotate.value = rotate
    }

    fun setGridVisible(visible: Boolean) {
        _gridVisible.value = visible
    }

    fun setBackgroundColor(colorHex: String) {
        _backgroundColor.value = colorHex
    }

    fun setModelLoading(loading: Boolean) {
        _isLoading.value = loading
    }

    fun setError(error: String?) {
        _errorMessage.value = error
        if (error != null) _isLoading.value = false
    }

    // Import external .glb or .ply from device
    fun importModel(context: Context, uri: Uri, name: String, extension: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val copiedFile = copyFileToInternalStorage(context, uri, name, extension)
                if (copiedFile != null) {
                    val newModel = ModelItem(
                        name = name.removeSuffix(".$extension"),
                        type = extension,
                        fileUri = copiedFile.absolutePath,
                        isSample = false,
                        description = "User-imported 3D model scanned or downloaded on-device. Size: ${(copiedFile.length() / 1024.0 / 1024.0).let { "%.2f".format(it) }} MB"
                    )
                    val insertedId = repository.insertModel(newModel)
                    val loadedModel = repository.getModelById(insertedId)
                    if (loadedModel != null) {
                        _activeModel.value = loadedModel
                    }
                } else {
                    _errorMessage.value = "Failed to open and copy select file."
                }
            } catch (e: Exception) {
                _errorMessage.value = "Import error: ${e.message}"
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun copyFileToInternalStorage(context: Context, uri: Uri, name: String, extension: String): File? {
        return withContext(Dispatchers.IO) {
            try {
                val contentResolver = context.contentResolver
                val inputStream = contentResolver.openInputStream(uri) ?: return@withContext null
                val modelsDir = File(context.filesDir, "models")
                if (!modelsDir.exists()) modelsDir.mkdirs()

                // Generate sanitized secure name
                val secureName = "${System.currentTimeMillis()}_${name.replace("[^a-zA-Z0-9._-]".toRegex(), "_")}"
                val targetFile = File(modelsDir, secureName)

                FileOutputStream(targetFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                targetFile
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    // Helper to load file content as Base64 for the WebView to load securely without CORS issues
    suspend fun getModelBase64(context: Context, model: ModelItem): String? {
        return withContext(Dispatchers.IO) {
            try {
                if (model.isSample) {
                    return@withContext null // Predefined models loaded procedurally in JS
                }
                val file = File(model.fileUri)
                if (!file.exists()) return@withContext null
                val bytes = file.readBytes()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}

class ModelViewModelFactory(private val repository: ModelRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ModelViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ModelViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
