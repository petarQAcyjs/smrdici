package com.petar.smrdici.data.repository

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.petar.smrdici.data.model.Budget
import com.petar.smrdici.data.model.BudgetType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * Repository za rad sa budžetima
 */
@Suppress("unused")
class BudgetRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val tag = "BudgetRepository"
    
    init {
        // Pokrenimo migraciju podataka ako je potrebno
        auth.currentUser?.let { user ->
            migrateBudgets(user.uid)
        }
    }
    
    companion object {
        @Volatile private var instance: BudgetRepository? = null
        
        /**
         * Singleton pristup repozitorijumu
         */
        fun getInstance(): BudgetRepository {
            return instance ?: synchronized(this) {
                instance ?: BudgetRepository(
                    FirebaseFirestore.getInstance(),
                    FirebaseAuth.getInstance()
                ).also { instance = it }
            }
        }
    }
    
    // Vraća kolekciju budžeta za trenutnog korisnika
    private fun getBudgetsCollection(): CollectionReference {
        val userId = auth.currentUser?.uid ?: throw Exception("Korisnik nije prijavljen")
        return firestore.collection("users").document(userId).collection("budgets")
    }
    
    /**
     * Migraciona metoda koja premešta budžete iz stare strukture u novu
     */
    private fun migrateBudgets(userId: String) {
        firestore.collection("budgets")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    Log.d(tag, "Nema budžeta za migraciju")
                    return@addOnSuccessListener
                }
                
                Log.d(tag, "Migracija ${snapshot.size()} budžeta za korisnika $userId")
                
                // Za svaki budžet u staroj kolekciji
                snapshot.documents.forEach { document ->
                    val budgetData = document.data
                    
                    if (budgetData != null) {
                        // Kopiramo u novu strukturu
                        val userBudgetsCollection = firestore.collection("users").document(userId).collection("budgets")
                        userBudgetsCollection.document(document.id)
                            .set(budgetData)
                            .addOnSuccessListener {
                                Log.d(tag, "Uspešno migriran budžet ${document.id}")
                                
                                // Brišemo iz stare kolekcije nakon uspešne migracije
                                firestore.collection("budgets").document(document.id)
                                    .delete()
                                    .addOnSuccessListener {
                                        Log.d(tag, "Uspešno obrisan stari budžet ${document.id}")
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e(tag, "Greška pri brisanju starog budžeta ${document.id}", e)
                                    }
                            }
                            .addOnFailureListener { e ->
                                Log.e(tag, "Greška pri migraciji budžeta ${document.id}", e)
                            }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(tag, "Greška pri dobavljanju budžeta za migraciju", e)
            }
    }
    
    /**
     * Pomoćna funkcija za konverziju DocumentSnapshot u Budget
     */
    private fun documentToBudget(doc: DocumentSnapshot): Budget? {
        return try {
            val id = doc.id
            val name = doc.getString("name") ?: ""
            val amount = doc.getDouble("amount") ?: 0.0
            @Suppress("UNCHECKED_CAST")
            val categoryIds = doc.get("categoryIds") as? List<String> ?: emptyList()
            val accountId = doc.getString("accountId") ?: ""
            val startDate = doc.getTimestamp("startDate") ?: Timestamp(Date())
            val endDate = doc.getTimestamp("endDate") ?: Timestamp(Date())
            val orderId = doc.getDouble("orderId") ?: 0.0
            val isActive = doc.getBoolean("isActive") != false
            val type = try {
                BudgetType.valueOf(doc.getString("type") ?: BudgetType.EXPENSE.name)
            } catch (e: Exception) {
                BudgetType.EXPENSE
            }
            
            Budget(
                id = id,
                name = name,
                amount = amount,
                categoryIds = categoryIds,
                accountId = accountId,
                startDate = startDate,
                endDate = endDate,
                orderId = orderId,
                isActive = isActive,
                type = type
            )
        } catch (e: Exception) {
            Log.e(tag, "Greška pri konvertovanju budžeta", e)
            null
        }
    }
    
    /**
     * Dodaje novi budžet u bazu
     */
    suspend fun addBudget(budget: Budget): Result<Budget> {
        return try {
            val user = auth.currentUser ?: throw Exception("Korisnik nije prijavljen")
            val budgetsCollection = getBudgetsCollection()
            
            // Kreiramo mapu sa podacima za Firestore
            val budgetData = hashMapOf(
                "id" to budget.id,
                "name" to budget.name,
                "amount" to budget.amount,
                "categoryIds" to budget.categoryIds,
                "accountId" to budget.accountId,
                "startDate" to budget.startDate,
                "endDate" to budget.endDate,
                "orderId" to budget.orderId,
                "isActive" to budget.isActive,
                "type" to budget.type.name,
                "userId" to user.uid,
                "createdAt" to Timestamp.now()
            )
            
            // Ako nema ID, koristimo automatski generisani
            val documentId = if (budget.id.isEmpty()) {
                val docRef = budgetsCollection.add(budgetData).await()
                docRef.id
            } else {
                val docRef = budgetsCollection.document(budget.id)
                docRef.set(budgetData).await()
                budget.id
            }
            
            // Vraćamo uspešno kreiran budžet sa ID-em
            Result.success(budget.copy(id = documentId))
        } catch (e: Exception) {
            Log.e(tag, "Greška pri dodavanju budžeta", e)
            Result.failure(e)
        }
    }
    
    /**
     * Ažurira postojeći budžet
     */
    suspend fun updateBudget(budget: Budget): Result<Budget> {
        return try {
            if (auth.currentUser === null) throw Exception("Korisnik nije prijavljen")
            val budgetsCollection = getBudgetsCollection()
            
            // Proveravamo da li budžet postoji
            if (budget.id.isEmpty()) {
                throw Exception("Budžet mora imati ID za ažuriranje")
            }
            
            // Kreiramo mapu sa podacima za ažuriranje
            val budgetData = hashMapOf(
                "name" to budget.name,
                "amount" to budget.amount,
                "categoryIds" to budget.categoryIds,
                "accountId" to budget.accountId,
                "startDate" to budget.startDate,
                "endDate" to budget.endDate,
                "orderId" to budget.orderId,
                "isActive" to budget.isActive,
                "type" to budget.type.name,
                "updatedAt" to Timestamp.now()
            )
            
            // Ažuriramo budžet
            val docRef = budgetsCollection.document(budget.id)
            docRef.update(budgetData).await()
            
            Result.success(budget)
        } catch (e: Exception) {
            Log.e(tag, "Greška pri ažuriranju budžeta", e)
            Result.failure(e)
        }
    }
    
    /**
     * Briše budžet iz baze
     */
    suspend fun deleteBudget(budgetId: String): Result<Unit> {
        return try {
            if (auth.currentUser === null) throw Exception("Korisnik nije prijavljen")
            val budgetsCollection = getBudgetsCollection()
            
            // Brišemo budžet
            val docRef = budgetsCollection.document(budgetId)
            docRef.delete().await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Greška pri brisanju budžeta", e)
            Result.failure(e)
        }
    }
    
    /**
     * Vraća sve budžete korisnika kao Flow
     */
    fun getAllBudgets(): Flow<List<Budget>> = callbackFlow {
        try {
            val user = auth.currentUser
            if (user === null) {
                trySend(emptyList())
                close()
                return@callbackFlow
            }
            
            val budgetsCollection = getBudgetsCollection()
            val query = budgetsCollection
                .whereEqualTo("isActive", true)
                .orderBy("orderId", Query.Direction.ASCENDING)
                
            val listener = query.addSnapshotListener { querySnapshot, exception ->
                if (exception != null) {
                    Log.e(tag, "Greška pri dobavljanju budžeta", exception)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val budgets = ArrayList<Budget>()
                querySnapshot?.forEach { documentSnapshot ->
                    documentToBudget(documentSnapshot)?.let { budget ->
                        budgets.add(budget)
                    }
                }
                
                trySend(budgets)
            }
            
            awaitClose {
                listener.remove()
            }
        } catch (e: Exception) {
            Log.e(tag, "Neočekivana greška u getAllBudgets", e)
            trySend(emptyList())
            close(e)
        }
    }
    
    /**
     * Vraća budžete određenog tipa (rashodi/prihodi)
     */
    fun getBudgetsByType(type: BudgetType): Flow<List<Budget>> = callbackFlow {
        try {
            val user = auth.currentUser
            if (user === null) {
                trySend(emptyList())
                close()
                return@callbackFlow
            }
            
            val budgetsCollection = getBudgetsCollection()
            val query = budgetsCollection
                .whereEqualTo("isActive", true)
                .whereEqualTo("type", type.name)
                .orderBy("orderId", Query.Direction.ASCENDING)
                
            val listener = query.addSnapshotListener { querySnapshot, exception ->
                if (exception != null) {
                    Log.e(tag, "Greška pri dobavljanju budžeta po tipu", exception)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val budgets = ArrayList<Budget>()
                querySnapshot?.forEach { documentSnapshot ->
                    documentToBudget(documentSnapshot)?.let { budget ->
                        // Osiguravamo da je tip postavljen
                        budgets.add(budget.copy(type = type))
                    }
                }
                
                Log.d(tag, "Dobavljeno ${budgets.size} budžeta tipa $type")
                
                trySend(budgets)
            }
            
            awaitClose { 
                Log.d(tag, "Zatvaranje listenera za budžete tipa $type")
                listener.remove() 
            }
        } catch (e: Exception) {
            Log.e(tag, "Neočekivana greška u getBudgetsByType", e)
            trySend(emptyList())
            close(e)
        }
    }
    
    /**
     * Vraća budžete za određeni račun
     */
    fun getBudgetsForAccount(accountId: String): Flow<List<Budget>> = callbackFlow {
        try {
            val user = auth.currentUser
            if (user === null) {
                trySend(emptyList())
                close()
                return@callbackFlow
            }
            
            val budgetsCollection = getBudgetsCollection()
            val query = budgetsCollection
                .whereEqualTo("isActive", true)
                .whereEqualTo("accountId", accountId)
                .orderBy("orderId", Query.Direction.ASCENDING)
                
            val listener = query.addSnapshotListener { querySnapshot, exception ->
                if (exception != null) {
                    Log.e(tag, "Greška pri dobavljanju budžeta za račun", exception)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val budgets = ArrayList<Budget>()
                querySnapshot?.forEach { documentSnapshot ->
                    documentToBudget(documentSnapshot)?.let { budget ->
                        budgets.add(budget)
                    }
                }
                
                Log.d(tag, "Dobavljeno ${budgets.size} budžeta za račun $accountId")
                
                trySend(budgets)
            }
            
            awaitClose { 
                Log.d(tag, "Zatvaranje listenera za budžete za račun")
                listener.remove() 
            }
        } catch (e: Exception) {
            Log.e(tag, "Neočekivana greška u getBudgetsForAccount", e)
            trySend(emptyList())
            close(e)
        }
    }
    
    /**
     * Vraća budžete za određenu kategoriju
     */
    fun getBudgetsForCategory(categoryId: String): Flow<List<Budget>> = callbackFlow {
        try {
            val user = auth.currentUser
            if (user === null) {
                trySend(emptyList())
                close()
                return@callbackFlow
            }
            
            val budgetsCollection = getBudgetsCollection()
            val query = budgetsCollection
                .whereEqualTo("isActive", true)
                .whereArrayContains("categoryIds", categoryId)
                .orderBy("orderId", Query.Direction.ASCENDING)
                
            val listener = query.addSnapshotListener { querySnapshot, exception ->
                if (exception != null) {
                    Log.e(tag, "Greška pri dobavljanju budžeta za kategoriju", exception)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val budgets = ArrayList<Budget>()
                querySnapshot?.forEach { documentSnapshot ->
                    documentToBudget(documentSnapshot)?.let { budget ->
                        budgets.add(budget)
                    }
                }
                
                Log.d(tag, "Dobavljeno ${budgets.size} budžeta za kategoriju $categoryId")
                
                trySend(budgets)
            }
            
            awaitClose {
                Log.d(tag, "Zatvaranje listenera za budžete za kategoriju")
                listener.remove() 
            }
        } catch (e: Exception) {
            Log.e(tag, "Neočekivana greška u getBudgetsForCategory", e)
            trySend(emptyList())
            close(e)
        }
    }
} 