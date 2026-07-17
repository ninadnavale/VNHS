package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.data.AppDatabase
import com.example.data.AttendanceRepository
import com.example.ui.AttendanceDashboard
import com.example.ui.AttendanceViewModel
import com.example.ui.AttendanceViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Database, Dao and Repository
        val database = AppDatabase.getDatabase(applicationContext)
        val dao = database.attendanceDao()
        val repository = AttendanceRepository(dao)
        
        // Initialize ViewModel via custom factory
        val viewModel: AttendanceViewModel by viewModels {
            AttendanceViewModelFactory(repository)
        }

        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                AttendanceDashboard(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
