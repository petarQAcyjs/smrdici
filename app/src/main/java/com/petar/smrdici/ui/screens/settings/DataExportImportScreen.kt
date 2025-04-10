package com.petar.smrdici.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataExportImportScreen(
    navController: NavController,
    viewModel: DataExportImportViewModel = viewModel(factory = DataExportImportViewModel.Factory(LocalContext.current))
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    val isExporting by viewModel.isExporting.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val exportSuccess by viewModel.exportSuccess.collectAsState()
    val importSuccess by viewModel.importSuccess.collectAsState()
    
    var showExportConfirmDialog by remember { mutableStateOf(false) }
    var showImportConfirmDialog by remember { mutableStateOf(false) }
    
    var selectedImportUri by remember { mutableStateOf<Uri?>(null) }
    
    // Observer za uspešan izvoz
    LaunchedEffect(exportSuccess) {
        exportSuccess?.let { success ->
            if (success) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Подаци су успешно извезени")
                }
            } else {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Грешка при извозу података")
                }
            }
            viewModel.resetExportStatus()
        }
    }
    
    // Observer za uspešan uvoz
    LaunchedEffect(importSuccess) {
        importSuccess?.let { success ->
            if (success) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Подаци су успешно увезени")
                }
            } else {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Грешка при увозу података")
                }
            }
            viewModel.resetImportStatus()
        }
    }
    
    // Launcher za biranje lokacije za čuvanje izvezenih podataka
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            viewModel.exportData(it)
        }
    }
    
    // Launcher za biranje lokacije za uvoz podataka
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedImportUri = uri
            showImportConfirmDialog = true
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Извоз и увоз података") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Kartica za izvoz podataka
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Upload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Text(
                        text = "Извоз података",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Извезите све ваше податке у JSON фајл за резервну копију или пренос на други уређај.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = { showExportConfirmDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isExporting && !isImporting
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 8.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        Text("Извези податке")
                    }
                }
            }
            
            // Kartica za uvoz podataka
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Text(
                        text = "Увоз података",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Увезите претходно извезене податке. Ово ће заменити све постојеће податке у апликацији!",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = { 
                            importLauncher.launch("application/json")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isExporting && !isImporting
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 8.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        Text("Увези податке")
                    }
                }
            }
        }
    }
    
    // Dijalog za potvrdu izvoza
    if (showExportConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExportConfirmDialog = false },
            title = { Text("Извоз података") },
            text = { Text("Да ли сте сигурни да желите да извезете све податке?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExportConfirmDialog = false
                        exportLauncher.launch(viewModel.getExportFilename())
                    }
                ) {
                    Text("Извези")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showExportConfirmDialog = false }
                ) {
                    Text("Откажи")
                }
            }
        )
    }
    
    // Dijalog za potvrdu uvoza
    if (showImportConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showImportConfirmDialog = false },
            title = { Text("Увоз података") },
            text = { 
                Text("Упозорење: Увоз ће заменити све постојеће податке! Да ли сте сигурни да желите да наставите?") 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportConfirmDialog = false
                        selectedImportUri?.let { uri ->
                            viewModel.importData(uri)
                        }
                    }
                ) {
                    Text("Увези")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showImportConfirmDialog = false }
                ) {
                    Text("Откажи")
                }
            }
        )
    }
} 