package com.petar.smrdici.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.petar.smrdici.R
import kotlin.math.abs

/**
 * Komponenta koja prikazuje vizuelnu reprezentaciju stanja budžeta
 * u obliku baterije/progres bara. Inspirisana BudgetBattery komponentom iz Ivy aplikacije.
 */
@Composable
fun BudgetBattery(
    modifier: Modifier = Modifier,
    currency: String,
    expenses: Double,
    budget: Double,
    backgroundNotFilled: Color = Color(0xFF303436),
    onClick: (() -> Unit)? = null,
) {
    if (budget <= 0.0) return
    
    val percentSpent = expenses / budget
    
    // Boje za različite nivoe potrošnje
    val fillColor = when {
        percentSpent <= 0.25 -> Color(0xFF4CAF50) // Zelena
        percentSpent <= 0.50 -> Color(0xFF8BC34A) // Svetlo zelena
        percentSpent <= 0.75 -> Color(0xFFFFEB3B) // Žuta
        percentSpent <= 1.0 -> Color(0xFFFF9800)  // Narandžasta
        else -> Color(0xFFF44336)                 // Crvena
    }
    
    // Boje teksta
    val textColor = when {
        percentSpent >= 0.75 -> Color.White
        else -> Color(0xFFE0E0E0)
    }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundNotFilled)
            .drawBehind {
                drawRect(
                    color = fillColor,
                    size = size.copy(
                        width = (size.width * minOf(percentSpent, 1.0)).toFloat()
                    )
                )
            }
            .clickable(enabled = onClick != null) {
                onClick?.invoke()
            }
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(16.dp))
        
        Icon(
            imageVector = if (percentSpent > 1.0) 
                Icons.Default.Warning 
            else 
                Icons.Default.CheckCircle,
            contentDescription = null,
            tint = textColor
        )
        
        Spacer(Modifier.width(16.dp))
        
        Column {
            Text(
                text = if (percentSpent <= 1.0) {
                    stringResource(R.string.left_to_spend)
                } else {
                    stringResource(R.string.budget_exceeded_by)
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            
            Spacer(Modifier.height(4.dp))
            
            // Preostalo ili prekoračeno
            Text(
                text = "${formatAmount(abs(budget - expenses))} $currency",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.ExtraBold,
                color = textColor
            )
            
            Spacer(Modifier.height(2.dp))
            
            // Ukupno stanje
            Text(
                text = "${formatAmount(expenses)}/${formatAmount(budget)} $currency",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = textColor.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * Formatira iznos sa dve decimale
 */
private fun formatAmount(amount: Double): String {
    return "%.2f".format(amount)
}

@Preview
@Composable
fun PreviewBudgetBattery() {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .background(Color(0xFF1A1C1E))
    ) {
        Text(
            text = "Примери BudgetBattery компоненте",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Nizak nivo potrošnje - 20%
        BudgetBattery(
            expenses = 200.0,
            budget = 1000.0,
            currency = "RSD"
        )
        
        Spacer(Modifier.height(16.dp))
        
        // Srednji nivo potrošnje - 50%
        BudgetBattery(
            expenses = 500.0,
            budget = 1000.0,
            currency = "RSD"
        )
        
        Spacer(Modifier.height(16.dp))
        
        // Visok nivo potrošnje - 80%
        BudgetBattery(
            expenses = 800.0,
            budget = 1000.0,
            currency = "RSD"
        )
        
        Spacer(Modifier.height(16.dp))
        
        // Prekoračen budžet - 120%
        BudgetBattery(
            expenses = 1200.0,
            budget = 1000.0,
            currency = "RSD"
        )
    }
} 