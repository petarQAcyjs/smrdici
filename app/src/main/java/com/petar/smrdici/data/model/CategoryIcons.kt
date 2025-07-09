package com.petar.smrdici.data.model

import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Shared icon collections for expense and income categories
 */
object CategoryIcons {
    private const val TAG = "CategoryIcons"

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
        Icons.AutoMirrored.Filled.TrendingUp,
        // New icons - only include ones that are definitely available
        Icons.Default.LocalMall,
        Icons.Default.ShoppingBag,
        Icons.Default.LocalDining,
        Icons.Default.LocalPizza,
        Icons.Default.RiceBowl,
        Icons.Default.FoodBank,
        Icons.Default.Cookie,
        Icons.Default.Cake,
        Icons.Default.LocalPharmacy,
        Icons.Default.MedicalServices,
        Icons.Default.Medication,
        Icons.Default.Vaccines,
        Icons.Default.Bolt,
        Icons.Default.WaterDrop,
        Icons.Default.Lightbulb,
        Icons.Default.House,
        Icons.Default.BedroomParent,
        Icons.Default.Kitchen,
        Icons.Default.Chair,
        Icons.Default.Bathtub,
        Icons.Default.Iron,
        Icons.Default.CleaningServices,
        Icons.Default.Microwave,
        Icons.Default.Tv,
        Icons.Default.Laptop,
        Icons.Default.Computer,
        Icons.Default.Call,
        Icons.Default.CameraAlt,
        Icons.Default.Train,
        Icons.Default.Luggage,
        Icons.Default.FlightTakeoff,
        Icons.Default.AirplanemodeActive,
        Icons.Default.LocalShipping,
        Icons.Default.Garage,
        Icons.Default.SportsSoccer,
        Icons.Default.SportsBar,
        Icons.Default.Pool,
        Icons.Default.Spa,
        Icons.Default.SelfImprovement,
        Icons.Default.Brush,
        Icons.Default.Palette,
        Icons.Default.Nightlife,
        Icons.Default.Park,
        Icons.Default.Deck,
        Icons.Default.Yard,
        Icons.Default.Forest,
        Icons.Default.Toys,
        Icons.Default.ChildFriendly,
        Icons.Default.Backpack,
        Icons.Default.Build,
        Icons.Default.Construction,
        Icons.Default.Hardware,
        Icons.Default.Umbrella,
        Icons.Default.Dry,
        Icons.Default.CalendarMonth,
        Icons.Default.Cloud
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
        Icons.Default.AutoAwesome,
        // New icons
        Icons.Default.AccountBalanceWallet,
        Icons.Default.Wallet,
        Icons.Default.CreditScore,
        Icons.Default.Calculate,
        Icons.Default.Receipt,
        Icons.Default.Insights,
        Icons.Default.Diamond,
        Icons.Default.Agriculture,
        Icons.Default.Science,
        Icons.Default.Biotech,
        Icons.Default.Psychology,
        Icons.Default.Flood
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