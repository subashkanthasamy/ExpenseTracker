package com.bose.expensetracker.data.remote

import com.bose.expensetracker.domain.model.Asset
import com.bose.expensetracker.domain.model.Category
import com.bose.expensetracker.domain.model.Expense
import com.bose.expensetracker.domain.model.Household
import com.bose.expensetracker.domain.model.Liability
import com.bose.expensetracker.domain.model.User
import com.google.firebase.firestore.FirebaseFirestore
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
                "householdIds" to user.householdIds,
                "activeHouseholdId" to user.activeHouseholdId,
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
        // Backward compat: read new fields, fall back to old householdId
        @Suppress("UNCHECKED_CAST")
        val householdIds = (doc.get("householdIds") as? List<String>)
        val activeHouseholdId = doc.getString("activeHouseholdId")
        val legacyHouseholdId = doc.getString("householdId")

        val resolvedIds = householdIds ?: listOfNotNull(legacyHouseholdId)
        val resolvedActive = activeHouseholdId ?: legacyHouseholdId

        val user = User(
            uid = uid,
            email = doc.getString("email") ?: "",
            displayName = doc.getString("displayName") ?: "",
            householdIds = resolvedIds,
            activeHouseholdId = resolvedActive
        )
        android.util.Log.d("FirestoreDS", "getUser success: uid=$uid, activeHouseholdId=${user.activeHouseholdId}, householdIds=${user.householdIds}")
        return user
    }

    suspend fun updateUserHouseholdId(uid: String, householdId: String) {
        addUserHouseholdId(uid, householdId)
        setActiveHouseholdId(uid, householdId)
    }

    suspend fun addUserHouseholdId(uid: String, householdId: String) {
        val docRef = firestore.collection("users").document(uid)
        // Ensure householdIds field exists first, then array union
        val doc = docRef.get(Source.SERVER).await()
        if (doc.get("householdIds") == null) {
            // Field doesn't exist (old user) — create it with current + new
            val existing = listOfNotNull(doc.getString("householdId"))
            val updated = (existing + householdId).distinct()
            docRef.set(mapOf("householdIds" to updated), com.google.firebase.firestore.SetOptions.merge()).await()
        } else {
            docRef.update("householdIds", com.google.firebase.firestore.FieldValue.arrayUnion(householdId)).await()
        }
    }

    suspend fun setActiveHouseholdId(uid: String, householdId: String) {
        firestore.collection("users").document(uid)
            .set(
                mapOf("activeHouseholdId" to householdId),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
    }

    suspend fun getUserFromServer(uid: String): User? {
        val doc = try {
            firestore.collection("users").document(uid).get(Source.SERVER).await()
        } catch (e: Exception) {
            android.util.Log.e("FirestoreDS", "getUserFromServer failed for $uid: ${e.message}")
            return null
        }
        if (!doc.exists()) return null
        @Suppress("UNCHECKED_CAST")
        val householdIds = (doc.get("householdIds") as? List<String>)
        val activeHouseholdId = doc.getString("activeHouseholdId")
        val legacyHouseholdId = doc.getString("householdId")
        return User(
            uid = uid,
            email = doc.getString("email") ?: "",
            displayName = doc.getString("displayName") ?: "",
            householdIds = householdIds ?: listOfNotNull(legacyHouseholdId),
            activeHouseholdId = activeHouseholdId ?: legacyHouseholdId
        )
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

    suspend fun deleteHousehold(householdId: String) {
        firestore.collection("households").document(householdId).delete().await()
    }

    suspend fun removeHouseholdFromUser(uid: String, householdId: String) {
        val docRef = firestore.collection("users").document(uid)
        docRef.update(
            "householdIds",
            com.google.firebase.firestore.FieldValue.arrayRemove(householdId)
        ).await()
        // If the deleted household was the active one, clear it
        val doc = docRef.get(Source.SERVER).await()
        if (doc.getString("activeHouseholdId") == householdId) {
            @Suppress("UNCHECKED_CAST")
            val remainingIds = (doc.get("householdIds") as? List<String>) ?: emptyList()
            val newActive = remainingIds.firstOrNull()
            docRef.update("activeHouseholdId", newActive).await()
        }
    }

    private suspend fun deleteAllInSubCollection(householdId: String, subCollection: String) {
        val collection = firestore.collection("households").document(householdId)
            .collection(subCollection)
        val snapshot = collection.get().await()
        snapshot.documents.chunked(500).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { doc -> batch.delete(doc.reference) }
            batch.commit().await()
        }
    }

    suspend fun deleteAllCategories(householdId: String) {
        deleteAllInSubCollection(householdId, "categories")
    }

    suspend fun deleteAllAssets(householdId: String) {
        deleteAllInSubCollection(householdId, "assets")
    }

    suspend fun deleteAllLiabilities(householdId: String) {
        deleteAllInSubCollection(householdId, "liabilities")
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

    suspend fun deleteAllExpenses(householdId: String) {
        val collection = firestore.collection("households").document(householdId)
            .collection("expenses")
        val snapshot = collection.get().await()
        val batchSize = 500
        snapshot.documents.chunked(batchSize).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { doc -> batch.delete(doc.reference) }
            batch.commit().await()
        }
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
