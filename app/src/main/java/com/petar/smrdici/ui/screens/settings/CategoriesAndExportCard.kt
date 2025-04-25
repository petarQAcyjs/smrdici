package com.petar.smrdici.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun CategoriesCard(
    onIncomeClick: () -> Unit,
    onExpenseClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Категорије",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Опција за категорије прихода
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onIncomeClick)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Категорије прихода",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    
                    Text(
                        text = "Управљајте категоријама прихода",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
            
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
            )
            
            // Опција за категорије расхода
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onExpenseClick)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Категорије расхода",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    
                    Text(
                        text = "Управљајте категоријама расхода",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun ExportImportCard(onExportClick: () -> Unit, onImportClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Извоз/Увоз података",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Опција за извоз
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onExportClick)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = "Извоз",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Извоз података",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Сачувајте све ваше податке у JSON фајл",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            
            // Опција за увоз
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onImportClick)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = "Увоз",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Увоз података",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Учитајте ваше податке из претходно извезеног фајла",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

/**
 * Напредни дијалог за увоз података са прегледом и опцијама
 */
@Composable
fun ImportDialog(
    isVisible: Boolean,
    importPreview: ImportPreview?,
    importProgress: Float,
    importProgressText: String,
    isImporting: Boolean,
    selectedImportMode: ImportMode,
    onDismiss: () -> Unit,
    onSelectFile: () -> Unit,
    onImportModeChange: (ImportMode) -> Unit,
    onImport: () -> Unit,
    onCancel: () -> Unit
) {
    if (isVisible) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Увоз података") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    if (importPreview == null && !isImporting) {
                        // Почетни екран - кориснику се нуди да изабере датотеку
                        Text(
                            text = "Изаберите JSON датотеку која садржи податке за увоз",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        Button(
                            onClick = onSelectFile,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = "Изабери фајл"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Изабери JSON датотеку")
                        }
                    } else if (isImporting) {
                        // Приказ прогреса увоза
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = "Увоз података у току...",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            Text(
                                text = importProgressText,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            
                            LinearProgressIndicator(
                                progress = { importProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                            )
                        }
                    } else if (importPreview != null) {
                        // Приказ прегледа података за увоз
                        Text(
                            text = "Преглед података за увоз",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        // Детаљи фајла
                        Text(
                            text = "Верзија података: ${importPreview.version}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Датум извоза: ${importPreview.exportDate}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        // Број ставки за увоз
                        DataCountRow(
                            title = "Рачуни:",
                            count = importPreview.accountCount,
                            icon = Icons.Default.AccountBalance
                        )
                        
                        DataCountRow(
                            title = "Расходи:",
                            count = importPreview.expenseCount,
                            icon = Icons.Default.ShoppingCart
                        )
                        
                        DataCountRow(
                            title = "Приходи:",
                            count = importPreview.incomeCount,
                            icon = Icons.Default.Payments
                        )
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                        
                        // Опције увоза
                        Text(
                            text = "Начин увоза",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        // Радио група за избор начина увоза
                        Column(modifier = Modifier.selectableGroup()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .selectable(
                                        selected = selectedImportMode == ImportMode.REPLACE_ALL,
                                        onClick = { onImportModeChange(ImportMode.REPLACE_ALL) }
                                    )
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedImportMode == ImportMode.REPLACE_ALL,
                                    onClick = { onImportModeChange(ImportMode.REPLACE_ALL) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Замени све податке",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "Обрисаће се сви постојећи подаци",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .selectable(
                                        selected = selectedImportMode == ImportMode.ADD_NEW,
                                        onClick = { onImportModeChange(ImportMode.ADD_NEW) }
                                    )
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedImportMode == ImportMode.ADD_NEW,
                                    onClick = { onImportModeChange(ImportMode.ADD_NEW) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Додај нове податке",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "Постојећи подаци се задржавају",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (importPreview != null && !isImporting) {
                    Button(
                        onClick = onImport,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Увези податке")
                    }
                } else if (isImporting) {
                    // Током увоза, приказујемо само дугме за отказивање
                    TextButton(
                        onClick = onCancel,
                        enabled = false
                    ) {
                        Text("Увоз у току...")
                    }
                } else {
                    // Почетни екран - у овом случају немамо потврдно дугме
                    // јер већ имамо дугме за избор фајла у садржају дијалога
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onCancel,
                    enabled = !isImporting
                ) {
                    Text("Откажи")
                }
            }
        )
    }
}

/**
 * Помоћна компонента за приказ броја ставки одређеног типа
 */
@Composable
private fun DataCountRow(
    title: String,
    count: Int,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
} 