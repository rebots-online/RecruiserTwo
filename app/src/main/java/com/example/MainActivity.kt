package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.data.db.AppDatabase
import com.example.data.repository.ModelRepository
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewer.MainViewerScreen
import com.example.ui.viewer.ModelViewModel
import com.example.ui.viewer.ModelViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Initialize Edge-to-Edge window insets
        enableEdgeToEdge()

        // 2. Initialize Room Database & Dao
        val database = AppDatabase.getDatabase(applicationContext)
        val modelDao = database.modelDao()

        // 3. Initialize Repository & ViewModel using Factory
        val repository = ModelRepository(modelDao)
        val viewModelFactory = ModelViewModelFactory(repository)
        val viewModel = ViewModelProvider(this, viewModelFactory)[ModelViewModel::class.java]

        setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                MainViewerScreen(viewModel = viewModel)
            }
        }
    }
}
