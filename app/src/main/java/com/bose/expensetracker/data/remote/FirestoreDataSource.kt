package com.bose.expensetracker.data.remote

import com.bose.expensetracker.domain.model.Asset
import com.bose.expensetracker.domain.model.Category
import com.bose.expensetracker.domain.model.Expense
import com.bose.expensetracker.domain.model.Household
import com.bose.expensetracker.domain.model.Liability
import com.bose.expensetracker.domain.model.User
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    // --- Users ---

    suspend fun createUser(user: User) {
        firestore.collection("users").document(user.uid).set(
            mapOf(
                "email" to user.email,
                "displayName" to user.displayName,
                "householdId" to user.householdId,
                "createdAt" to System.currentTimeMillis()
            )
        ).await()
    }

    suspend fun getUser(uid: String): User? {
        val doc = try {
            // Try default source first (uses cache if available, server otherwise)
            firestore.collection("users").document(uid).get().await()
        } catch (e1: Exception) {
            android.util.Log.w("FirestoreDS", "getUser default failed for $uid: ${e1.message}")
            try {
                firestore.collection("users").document(uid).get(Source.CACHE).await()
            } catch (e2: Exception) {
                android.util.Log.w("FirestoreDS", "getUser cache failed for $uid: ${e2.message}")
                try {
                    firestore.collection("users").document(uid).get(Source.SERVER).await()
                } catch (e3: Exception) {
                    android.util.Log.e("FirestoreDS", "getUser all sources failed for $uid: ${e3.message}")
                    return null
                }
            }
        }
        if (!doc.exists()) {
            android.util.Log.w("FirestoreDS", "getUser doc does not exist for $uid")
            return null
        }
        val user = User(
            uid = uid,
            email = doc.getString("email") ?: "",
            displayName = doc.getString("displayName") ?: "",
            householdId = doc.getString("householdId")
        )
        android.util.Log.d("FirestoreDS", "getUser success: uid=$uid, householdId=${user.householdId}")
        return user
    }

    suspend fun updateUserHouseholdId(uid: String, householdId: String) {
        firestore.collection("users").document(uid)
            .set(mapOf("householdId" to householdId), com.google.firebase.firestore.SetOptions.merge())
            .await()
    }

    // --- Households ---

    suspend fun createHousehold(household: Household): String {
        firestore.collection("households").document(household.id).set(
            mapOf(
                "name" to household.name,
                "memberUids" to household.memberUids,
                "inviteCode" to household.inviteCode,
                "createdAt" to household.createdAt
            )
        ).await()
        return household.id
    }

    suspend fun getHousehold(householdId: String): Household? {
        val doc = try {
            val cached = firestore.collection("households").document(householdId).get(Source.CACHE).await()
            if (cached.exists()) cached
            else firestore.collection("households").document(householdId).get(Source.SERVER).await()
        } catch (_: Exception) {
            try {
                firestore.collection("households").document(householdId).get(Source.SERVER).await()
            } catch (_: Exception) {
                return null
            }
        }
        if (!doc.exists()) return null
        @Suppress("UNCHECKED_CAST")
        return Household(
            id = householdId,
            name = doc.getString("name") ?: "",
            memberUids = (doc.get("memberUids") as? List<String>) ?: emptyList(),
            inviteCode = doc.getString("inviteCode") ?: "",
            createdAt = doc.getLong("createdAt") ?: 0L
        )
    }

    suspend fun getHouseholdByInviteCode(inviteCode: String): Household? {
        val snapshot = try {
            firestore.collection("households")
                .whereEqualTo("inviteCode", inviteCode)
                .get(Source.SERVER).await()
        } catch (_: Exception) {
            try {
                firestore.collection("households")
                    .whereEqualTo("inviteCode", inviteCode)
                    .get(Source.CACHE).await()
            } catch (_: Exception) {
                return null
            }
        }
        val doc = snapshot.documents.firstOrNull() ?: return null
        @Suppress("UNCHECKED_CAST")
        return Household(
            id = doc.id,
            name = doc.getString("name") ?: "",
            memberUids = (doc.get("memberUids") as? List<String>) ?: emptyList(),
            inviteCode = doc.getString("inviteCode") ?: "",
            createdAt = doc.getLong("createdAt") ?: 0L
        )
    }

    suspend fun addMemberToHousehold(householdId: String, userId: String) {
        firestore.collection("households").document(householdId)
            .update("memberUids", com.google.firebase.firestore.FieldValue.arrayUnion(userId))
            .await()
    }

    // --- Expenses ---

    suspend fun addExpense(householdId: String, expense: Expense) {
        firestore.collection("households").document(householdId)
            .collection("expenses").document(expense.id).set(
                mapOf(
                    "amount" to expense.amount,
                    "categoryId" to expense.categoryId,
                    "categoryName" to expense.categoryName,
                    "date" to expense.date,
                    "notes" to expense.notes,
                    "addedBy" to expense.addedBy,
                    "addedByName" to expense.addedByName,
                    "createdAt" to expense.createdAt,
                    "updatedAt" to expense.updatedAt
                )
            ).await()
    }

    suspend fun updateExpense(householdId: String, expense: Expense) {
        firestore.collection("households").document(householdId)
            .collection("expenses").document(expense.id).set(
                mapOf(
                    "amount" to expense.amount,
                    "categoryId" to expense.categoryId,
                    "categoryName" to expense.categoryName,
                    "date" to expense.date,
                    "notes" to expense.notes,
                    "addedBy" to expense.addedBy,
                    "addedByName" to expense.addedByName,
                    "createdAt" to expense.createdAt,
                    "updatedAt" to expense.updatedAt
                )
            ).await()
    }

    suspend fun deleteExpense(householdId: String, expenseId: String) {
        firestore.collection("households").document(householdId)
            .collection("expenses").document(expenseId).delete().await()
    }

    fun observeExpenses(householdId: String): Flow<List<Expense>> = callbackFlow {
        val listener = firestore.collection("households").document(householdId)
            .collection("expenses")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                val expenses = snapshot?.documents?.mapNotNull { doc ->
                    Expense(
                        id = doc.id,
                        householdId = householdId,
                        amount = doc.getDouble("amount") ?: 0.0,
                        categoryId = doc.getString("categoryId") ?: "",
                        categoryName = doc.getString("categoryName") ?: "",
                        date = doc.getLong("date") ?: 0L,
                        notes = doc.getString("notes") ?: "",
                        addedBy = doc.getString("addedBy") ?: "",
                        addedByName = doc.getString("addedByName") ?: "",
                        createdAt = doc.getLong("createdAt") ?: 0L,
                        updatedAt = doc.getLong("updatedAt") ?: 0L,
                        isSynced = true
                    )
                } ?: emptyList()
                trySend(expenses)
            }
        awaitClose { listener.remove() }
    }

    // --- Categories ---

    suspend fun addCategory(householdId: String, category: Category) {
        firestore.collection("households").document(householdId)
            .collection("categories").document(category.id).set(
                mapOf(
                    "name" to category.name,
                    "icon" to category.icon,
                    "color" to category.color,
                    "isPreset" to category.isPreset,
                    "createdAt" to System.currentTimeMillis()
                )
            ).await()
    }

    suspend fun updateCategory(householdId: String, category: Category) {
        firestore.collection("households").document(householdId)
            .collection("categories").document(category.id).set(
                mapOf(
                    "name" to category.name,
                    "icon" to category.icon,
                    "color" to category.color,
                    "isPreset" to category.isPreset
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
    }

    suspend fun deleteCategory(householdId: String, categoryId: String) {
        firestore.collection("households").document(householdId)
            .collection("categories").document(categoryId).delete().await()
    }

    fun observeCategories(householdId: String): Flow<List<Category>> = callbackFlow {
        val listener = firestore.collection("households").document(householdId)
            .collection("categories")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                val categories = snapshot?.documents?.mapNotNull { doc ->
                    Category(
                        id = doc.id,
                        name = doc.getString("name") ?: "",
                        icon = doc.getString("icon") ?: "",
                        color = doc.getLong("color") ?: 0L,
                        isPreset = doc.getBoolean("isPreset") ?: false,
                        householdId = householdId
                    )
                } ?: emptyList()
                trySend(categories)
            }
        awaitClose { listener.remove() }
    }

    // --- Assets ---

    suspend fun addAsset(householdId: String, asset: Asset) {
        firestore.collection("households").document(householdId)
            .collection("assets").document(asset.id).set(
                mapOf(
                    "name" to asset.name,
                    "value" to asset.value,
                    "type" to asset.type,
                    "date" to asset.date,
                    "addedBy" to asset.addedBy
                )
            ).await()
    }

    suspend fun updateAsset(householdId: String, asset: Asset) {
        firestore.collection("households").document(householdId)
            .collection("assets").document(asset.id).set(
                mapOf(
                    "name" to asset.name,
                    "value" to asset.value,
                    "type" to asset.type,
                    "date" to asset.date,
                    "addedBy" to asset.addedBy
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
    }

    suspend fun deleteAsset(householdId: String, assetId: String) {
        firestore.collection("households").document(householdId)
            .collection("assets").document(assetId).delete().await()
    }

    fun observeAssets(householdId: String): Flow<List<Asset>> = callbackFlow {
        val listener = firestore.collection("households").document(householdId)
            .collection("assets")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                val assets = snapshot?.documents?.mapNotNull { doc ->
                    Asset(
                        id = doc.id,
                        householdId = householdId,
                        name = doc.getString("name") ?: "",
                        value = doc.getDouble("value") ?: 0.0,
                        type = doc.getString("type") ?: "",
                        date = doc.getLong("date") ?: 0L,
                        addedBy = doc.getString("addedBy") ?: ""
                    )
                } ?: emptyList()
                trySend(assets)
            }
        awaitClose { listener.remove() }
    }

    // --- Liabilities ---

    suspend fun addLiability(householdId: String, liability: Liability) {
        firestore.collection("households").document(householdId)
            .collection("liabilities").document(liability.id).set(
                mapOf(
                    "name" to liability.name,
                    "amount" to liability.amount,
                    "type" to liability.type,
                    "date" to liability.date,
                    "addedBy" to liability.addedBy
                )
            ).await()
    }

    suspend fun updateLiability(householdId: String, liability: Liability) {
        firestore.collection("households").document(householdId)
            .collection("liabilities").document(liability.id).set(
                mapOf(
                    "name" to liability.name,
                    "amount" to liability.amount,
                    "type" to liability.type,
                    "date" to liability.date,
                    "addedBy" to liability.addedBy
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
    }

    suspend fun deleteLiability(householdId: String, liabilityId: String) {
        firestore.collection("households").document(householdId)
            .collection("liabilities").document(liabilityId).delete().await()
    }

    fun observeLiabilities(householdId: String): Flow<List<Liability>> = callbackFlow {
        val listener = firestore.collection("households").document(householdId)
            .collection("liabilities")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                val liabilities = snapshot?.documents?.mapNotNull { doc ->
                    Liability(
                        id = doc.id,
                        householdId = householdId,
                        name = doc.getString("name") ?: "",
                        amount = doc.getDouble("amount") ?: 0.0,
                        type = doc.getString("type") ?: "",
                        date = doc.getLong("date") ?: 0L,
                        addedBy = doc.getString("addedBy") ?: ""
                    )
                } ?: emptyList()
                trySend(liabilities)
            }
        awaitClose { listener.remove() }
    }
}
