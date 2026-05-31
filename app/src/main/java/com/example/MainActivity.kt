package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.data.local.NoteDatabase
import com.example.data.repository.NoteRepository
import com.example.ui.MainAppContent
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.NoteViewModel
import com.example.viewmodel.NoteViewModelFactory

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    val db = NoteDatabase.getDatabase(applicationContext)
    val repository = NoteRepository(db.dao)
    val factory = NoteViewModelFactory(application, repository)
    val viewModel = androidx.lifecycle.ViewModelProvider(this, factory)[NoteViewModel::class.java]

    enableEdgeToEdge()
    setContent {
      val isDarkMode by viewModel.isDarkMode.collectAsState()
      
      MyApplicationTheme(darkTheme = isDarkMode) {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = androidx.compose.material3.MaterialTheme.colorScheme.background
        ) {
          MainAppContent(viewModel = viewModel)
        }
      }
    }
  }
}
