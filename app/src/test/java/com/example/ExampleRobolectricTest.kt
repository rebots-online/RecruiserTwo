package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.ModelDao
import com.example.data.model.ModelItem
import com.example.data.model.SavedViewpoint
import com.example.data.repository.ModelRepository
import com.example.ui.viewer.ModelViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ModelDao
    private lateinit var repository: ModelRepository
    private lateinit var viewModel: ModelViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        
        // Setup in-memory Room DB
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        dao = db.modelDao()
        repository = ModelRepository(dao)
        viewModel = ModelViewModel(repository)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `read string from context verifies correct app name`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("3D Model Player & Navigator", appName)
    }

    @Test
    fun `viewmodel loads initial predefined samples`() = runTest(testDispatcher) {
        // Execute pending database init coroutines
        testScheduler.advanceUntilIdle()

        val models = viewModel.modelsList.value
        assertEquals(2, models.size)
        
        val firstModel = models.first { it.sampleAssetPath == "station" }
        assertEquals("Space Station Core Hub", firstModel.name)
        assertEquals("glb", firstModel.type)
        assertTrue(firstModel.isSample)
    }

    @Test
    fun `viewmodel selectModel updates activeModel state`() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()

        val models = viewModel.modelsList.value
        val plyModel = models.first { it.type == "ply" }

        viewModel.selectModel(plyModel)
        testScheduler.advanceUntilIdle()

        assertEquals(plyModel.id, viewModel.activeModel.value?.id)
        assertEquals("LIDAR Corridor Scan", viewModel.activeModel.value?.name)
    }

    @Test
    fun `viewmodel saveCurrentViewpoint persists camera coordinates`() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()

        // Sync camera coordinates
        viewModel.updateCameraPosition(2.5f, 3.5f, 4.5f, 0f, 1f, 0f)
        
        viewModel.saveCurrentViewpoint("Engine Deck Checkpoint")
        testScheduler.advanceUntilIdle()

        val viewpoints = viewModel.viewpointsList.first()
        val saved = viewpoints.firstOrNull { it.title == "Engine Deck Checkpoint" }
        
        assertNotNull(saved)
        assertEquals(2.5f, saved!!.posX, 0.01f)
        assertEquals(3.5f, saved.posY, 0.01f)
        assertEquals(4.5f, saved.posZ, 0.01f)
    }
}
