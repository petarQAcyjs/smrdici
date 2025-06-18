package com.petar.smrdici.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Commute
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Copyright
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.House
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SmokingRooms
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Shared icon collections for expense and income categories
 */
object CategoryIcons {
    // Icons for expense categories
    val expenseIcons = listOf(
        Icons.Default.Receipt,
        Icons.Default.DirectionsCar,
        Icons.Default.ShoppingCart,
        Icons.Default.Pets,
        Icons.Default.Home,
        Icons.Default.LocalHospital,
        Icons.Default.Restaurant,
        Icons.Default.School,
        Icons.Default.Devices,
        Icons.Default.Fastfood,
        Icons.Default.ChildCare,
        Icons.Default.LocalCafe,
        Icons.Default.SmokingRooms,
        Icons.Default.LocalGroceryStore,
        Icons.Default.CreditCard,
        Icons.Default.Celebration,
        Icons.Default.SportsEsports,
        Icons.Default.CardGiftcard,
        Icons.Default.Apartment,
        Icons.Default.Checkroom,
        Icons.Default.Commute,
        Icons.Default.Face,
        Icons.Default.Favorite,
        Icons.Default.FitnessCenter,
        Icons.Default.Flight,
        Icons.Default.Grass,
        Icons.Default.Handyman,
        Icons.Default.LocalBar,
        Icons.Default.Movie,
        Icons.Default.MusicNote,
        Icons.Default.Security,
        Icons.Default.Smartphone,
        Icons.Default.SportsBasketball,
        Icons.Default.Subscriptions,
        Icons.Default.WaterDrop,
        Icons.Default.Wifi,
        Icons.AutoMirrored.Filled.MenuBook,
        Icons.AutoMirrored.Filled.TrendingUp
    )

    // Icons for income categories
    val incomeIcons = listOf(
        Icons.Default.AttachMoney,
        Icons.Default.Payments,
        Icons.Default.AccountBalance,
        Icons.Default.MoneyOff,
        Icons.Default.Work,
        Icons.Default.Savings,
        Icons.Default.LocalAtm,
        Icons.Default.CardGiftcard,
        Icons.Default.BusinessCenter,
        Icons.Default.Casino,
        Icons.Default.Copyright,
        Icons.Default.EmojiEvents,
        Icons.Default.EventSeat,
        Icons.Default.FamilyRestroom,
        Icons.Default.Gavel,
        Icons.Default.Handshake,
        Icons.Default.HealthAndSafety,
        Icons.Default.House,
        Icons.Default.PersonOff,
        Icons.Default.PieChart,
        Icons.Default.Receipt,
        Icons.Default.School,
        Icons.AutoMirrored.Filled.TrendingUp,
        Icons.Default.Computer,
        Icons.Default.AutoAwesome
    )
    
    /**
     * Find an icon by name in all icon collections
     */
    fun findIconByName(iconName: String): ImageVector? {
        // Try to find the icon by field name in Icons.Default
        try {
            val field = Icons.Default::class.java.getDeclaredField(iconName)
            field.isAccessible = true
            return field.get(Icons.Default) as? ImageVector
        } catch (e: Exception) {
            // Try next collection
        }
        
        // Try to find the icon in Icons.AutoMirrored.Filled
        try {
            val field = Icons.AutoMirrored.Filled::class.java.getDeclaredField(iconName)
            field.isAccessible = true
            return field.get(Icons.AutoMirrored.Filled) as? ImageVector
        } catch (e: Exception) {
            // Try next collection
        }
        
        // Try to find the icon in Icons.Filled
        try {
            val field = Icons.Filled::class.java.getDeclaredField(iconName)
            field.isAccessible = true
            return field.get(Icons.Filled) as? ImageVector
        } catch (e: Exception) {
            // Try indexed format
        }
        
        // Try to parse ExpenseIcon_X format
        if (iconName.startsWith("ExpenseIcon_")) {
            try {
                val index = iconName.removePrefix("ExpenseIcon_").toInt()
                if (index >= 0 && index < expenseIcons.size) {
                    return expenseIcons[index]
                }
            } catch (e: Exception) {
                // Try next format
            }
        }
        
        // Try to parse IncomeIcon_X format
        if (iconName.startsWith("IncomeIcon_")) {
            try {
                val index = iconName.removePrefix("IncomeIcon_").toInt()
                if (index >= 0 && index < incomeIcons.size) {
                    return incomeIcons[index]
                }
            } catch (e: Exception) {
                // Return default icon
            }
        }
        
        // Default icons as fallback
        return if (iconName == "ShoppingCart") {
            Icons.Default.ShoppingCart
        } else if (iconName == "AttachMoney") {
            Icons.Default.AttachMoney
        } else {
            null
        }
    }
} 