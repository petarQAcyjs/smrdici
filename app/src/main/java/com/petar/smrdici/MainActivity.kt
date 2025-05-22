package com.petar.smrdici

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.petar.smrdici.data.migration.FamilyMigration
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Run family migration
        lifecycleScope.launch {
            FamilyMigration.migrateToFamilyStructure()
        }
        
        setContent {
// ... existing code ...
        }
    }
} 