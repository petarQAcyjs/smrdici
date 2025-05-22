package com.petar.smrdici.data.migration

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object FamilyMigration {
    private const val TAG = "FamilyMigration"
    private const val FAMILY_ID = "shared_family"
    
    suspend fun migrateToFamilyStructure() {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val firestore = FirebaseFirestore.getInstance()
        
        try {
            // Check if family already exists
            val familyDoc = firestore.collection("families").document(FAMILY_ID)
            val family = familyDoc.get().await()
            
            if (!family.exists()) {
                // Create family document
                familyDoc.set(mapOf(
                    "name" to "Smrdici Family",
                    "adminId" to currentUser.uid,
                    "createdAt" to com.google.firebase.Timestamp.now()
                )).await()
                
                Log.d(TAG, "Created family document")
            }
            
            // Add current user as member if not already
            val memberDoc = familyDoc.collection("members").document(currentUser.uid)
            if (!memberDoc.get().await().exists()) {
                memberDoc.set(mapOf(
                    "email" to currentUser.email,
                    "joinedAt" to com.google.firebase.Timestamp.now()
                )).await()
                
                Log.d(TAG, "Added current user as family member")
            }
            
            // Start migrating data
            migrateData(firestore, currentUser.uid)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during family migration", e)
        }
    }
    
    private suspend fun migrateData(firestore: FirebaseFirestore, userId: String) {
        try {
            // Migrate incomes
            val incomes = firestore.collection("users").document(userId)
                .collection("incomes").get().await()
            
            for (income in incomes.documents) {
                val data = income.data ?: continue
                firestore.collection("families").document(FAMILY_ID)
                    .collection("incomes").document(income.id)
                    .set(data).await()
            }
            
            // Migrate expenses
            val expenses = firestore.collection("users").document(userId)
                .collection("expenses").get().await()
            
            for (expense in expenses.documents) {
                val data = expense.data ?: continue
                firestore.collection("families").document(FAMILY_ID)
                    .collection("expenses").document(expense.id)
                    .set(data).await()
            }
            
            // Migrate accounts
            val accounts = firestore.collection("users").document(userId)
                .collection("accounts").get().await()
            
            for (account in accounts.documents) {
                val data = account.data ?: continue
                firestore.collection("families").document(FAMILY_ID)
                    .collection("accounts").document(account.id)
                    .set(data).await()
            }
            
            Log.d(TAG, "Successfully migrated user data to family structure")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error migrating data", e)
        }
    }
} 