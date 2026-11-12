package com.saleh.alazzi.grocery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.saleh.alazzi.grocery.data.backup.GroceryBackupManager
import com.saleh.alazzi.grocery.data.local.GroceryDatabase
import com.saleh.alazzi.grocery.data.repository.GroceryRepository
import com.saleh.alazzi.grocery.ui.GroceryApp
import com.saleh.alazzi.grocery.ui.GroceryViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = GroceryDatabase.getInstance(applicationContext)
        val repository = GroceryRepository(database)
        val viewModel = ViewModelProvider(
            this,
            GroceryViewModel.Factory(database, repository)
        )[GroceryViewModel::class.java]

        setContent {
            val colors = lightColorScheme(
                primary = Color(0xFF155E9B),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFDCEBFA),
                onPrimaryContainer = Color(0xFF123B5A),
                secondary = Color(0xFF16804A),
                error = Color(0xFFB42318),
                background = Color(0xFFF3F5F7),
                surface = Color(0xFFFFFFFF),
                onSurface = Color(0xFF182230),
                onSurfaceVariant = Color(0xFF475467)
            )
            MaterialTheme(colorScheme = colors) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    GroceryApp(viewModel, GroceryBackupManager(applicationContext, database))
                }
            }
        }
    }
}
