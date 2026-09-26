package com.bose.expensetracker.data.remote

import com.bose.expensetracker.domain.usecase.access.Permissions
import com.bose.expensetracker.domain.model.Asset
import com.bose.expensetracker.domain.model.Budget
import com.bose.expensetracker.domain.model.RecurringExpense
import com.bose.expensetracker.domain.model.RecurringFrequency
import com.bose.expensetracker.domain.model.SavingsGoal
import com.bose.expensetracker.domain.model.Category
import com.bose.expensetracker.domain.model.Expense
import com.bose.expensetracker.domain.model.ExpenseScope
import com.bose.expensetracker.domain.model.PaymentMethod
import com.bose.expensetracker.domain.model.Household
import com.bose.expensetracker.domain.model.Liability
import com.bose.expensetracker.domain.model.User
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
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

    /**
     * Reads a time field as epoch millis.
     *
     * Epoch millis is the wire contract (it's what the shared domain model declares),
     * but the iOS app wrote these fields as Firestore [Timestamp]s, so documents
     * created there hold a Timestamp instead of a number. Reading those with
     * `getLong` throws "Field 'x' is not a java.lang.Number" and killed the app.
     * Accept either representation so mixed data already in Firestore still loads.
     */
    private fun DocumentSnapshot.getEpochMillis(field: String): Long? =
        when (val value = get(field)) {
            is Number -> value.toLong()
            is Timestamp -> value.toDate().time
            is java.util.Date -> value.time
            else -> null
        }

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
        } catch (e: CancellationException) {
            throw e
        } catch (e1: Exception) {
            android.util.Log.w("FirestoreDS", "getUser default failed for $uid: ${e1.message}")
            try {
                firestore.collection("users").document(uid).get(Source.CACHE).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e2: Exception) {
                android.util.Log.w("FirestoreDS", "getUser cache failed for $uid: ${e2.message}")
                try {
                    firestore.collection("users").document(uid).get(Source.SERVER).await()
                } catch (e: CancellationException) {
                    throw e
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
        } catch (e: CancellationException) {
            throw e
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
                "ownerUid" to household.ownerUid,
                "memberUids" to household.memberUids,
                // Empty at creation: the rules require the creator to grant nobody else a role.
                "roles" to household.roles,
                "inviteCode" to household.inviteCode,
                "createdAt" to household.createdAt
            )
        ).await()
        // Written second: the rules require the author to already be a member.
        publishInviteCode(household.id, household.name, household.inviteCode)
        return household.id
    }

    /**
     * Publishes the lookup document for [household] if it is missing.
     *
     * Households created before the invite-code change have no `inviteCodes/{code}` entry,
     * so they cannot be joined until one exists. Rather than requiring a one-off backfill
     * for every household, a member self-heals it — and the natural moment is when they
     * open the household screen, since that is the only place the code is shown to share.
     */
    suspend fun ensureInviteCodePublished(household: Household) {
        if (household.inviteCode.isBlank()) return
        try {
            val existing = firestore.collection("inviteCodes").document(household.inviteCode).get().await()
            val pointsHere = existing.exists() && existing.getString("householdId") == household.id
            if (pointsHere) return
            // Never repoint a code that already belongs to a different household.
            if (existing.exists()) {
                android.util.Log.w(
                    "FirestoreDS",
                    "inviteCode ${household.inviteCode} already maps to ${existing.getString("householdId")}"
                )
                return
            }
            publishInviteCode(household.id, household.name, household.inviteCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("FirestoreDS", "Could not publish invite code lookup", e)
        }
    }

    /** Publishes the code -> household lookup used by [resolveInviteCode]. */
    suspend fun publishInviteCode(householdId: String, householdName: String, inviteCode: String) {
        firestore.collection("inviteCodes").document(inviteCode).set(
            mapOf(
                "householdId" to householdId,
                "householdName" to householdName
            )
        ).await()
    }

    suspend fun getHousehold(householdId: String): Household? {
        val doc = try {
            val cached = firestore.collection("households").document(householdId).get(Source.CACHE).await()
            if (cached.exists()) cached
            else firestore.collection("households").document(householdId).get(Source.SERVER).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            try {
                firestore.collection("households").document(householdId).get(Source.SERVER).await()
            } catch (e: CancellationException) {
                throw e
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
            ownerUid = doc.getString("ownerUid") ?: "",
            roles = (doc.get("roles") as? Map<String, String>) ?: emptyMap(),
            inviteCode = doc.getString("inviteCode") ?: "",
            createdAt = doc.getEpochMillis("createdAt") ?: 0L,
            presetVersion = (doc.getLong("presetVersion") ?: 0L).toInt()
        )
    }

    /** What an invite code resolves to, without needing read access to the household. */
    data class InviteTarget(val householdId: String, val householdName: String)

    /**
     * Resolves an invite code via `inviteCodes/{code}`.
     *
     * The old implementation queried `households` by inviteCode, which required every
     * household to be readable by any signed-in user — that let anyone enumerate
     * households, read their codes and join them. This lookup document only discloses
     * anything to someone who already knows the code.
     */
    suspend fun resolveInviteCode(inviteCode: String): InviteTarget? {
        val doc = try {
            firestore.collection("inviteCodes").document(inviteCode).get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return null
        }
        if (!doc.exists()) return null
        val householdId = doc.getString("householdId") ?: return null
        return InviteTarget(householdId, doc.getString("householdName") ?: "")
    }

    /**
     * Adds [userId] to a household as a plain member.
     *
     * Both fields move together: `memberUids` is what the "my households" query reads, `roles`
     * is what the rules trust. The join branch of the security rules rejects an update that
     * touches only one of them, so this cannot be split.
     */
    suspend fun addMemberToHousehold(householdId: String, userId: String) {
        firestore.collection("households").document(householdId)
            .update(
                "memberUids", com.google.firebase.firestore.FieldValue.arrayUnion(userId),
                "roles.$userId", Permissions.ROLE_MEMBER
            )
            .await()
    }

    /** Owner-only. Only `roles.<uid>` changes; `memberUids` already contains them. */
    suspend fun updateMemberRole(householdId: String, userId: String, role: String) {
        firestore.collection("households").document(householdId)
            .update("roles.$userId", role)
            .await()
    }

    /**
     * Owner-only. Both fields move together — the rules reject an update that leaves
     * `memberUids` and `roles` disagreeing.
     */
    suspend fun removeMemberFromHousehold(householdId: String, userId: String) {
        firestore.collection("households").document(householdId)
            .update(
                "memberUids", com.google.firebase.firestore.FieldValue.arrayRemove(userId),
                "roles.$userId", com.google.firebase.firestore.FieldValue.delete()
            )
            .await()
    }

    /**
     * Records the catalogue version a household has been seeded up to.
     *
     * An update rather than part of create: the create rule uses `keys().hasOnly(...)` and would
     * reject the extra field, whereas the manager branch of update asserts specific fields and
     * lets an unasserted one through. Owner-only, like the seeding it accompanies.
     */
    suspend fun setHouseholdPresetVersion(householdId: String, version: Int) {
        firestore.collection("households").document(householdId)
            .update("presetVersion", version)
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
        snapshot.documents.chunked(400).forEach { chunk ->
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

    suspend fun deleteAllBudgets(householdId: String) {
        deleteAllInSubCollection(householdId, "budgets")
    }

    suspend fun deleteAllSavingsGoals(householdId: String) {
        deleteAllInSubCollection(householdId, "savingsGoals")
    }

    suspend fun deleteAllRecurring(householdId: String) {
        deleteAllInSubCollection(householdId, "recurring")
    }

    /**
     * Deletes the invite-code lookup, but only if it exists and points at this household.
     * Households predating the lookup have none, and the rules deny deleting a missing or
     * foreign code — so an unconditional delete would make those households undeletable.
     */
    suspend fun deleteInviteCodeIfOwned(inviteCode: String, householdId: String) {
        if (inviteCode.isBlank()) return
        val ref = firestore.collection("inviteCodes").document(inviteCode)
        val existing = ref.get().await()
        if (existing.exists() && existing.getString("householdId") == householdId) {
            ref.delete().await()
        }
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
                    "updatedAt" to expense.updatedAt,
                    "paymentMethod" to expense.paymentMethod.wire,
                    "scope" to expense.scope.wire
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
                    "updatedAt" to expense.updatedAt,
                    "paymentMethod" to expense.paymentMethod.wire,
                    "scope" to expense.scope.wire
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
        val batchSize = 400
        snapshot.documents.chunked(batchSize).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { doc -> batch.delete(doc.reference) }
            batch.commit().await()
        }
    }

    /**
     * Live expenses for a household, scoped to what the caller may read.
     *
     * [canReadAll] must be true only for the owner and admins. It decides the query shape, and
     * that is not cosmetic: the security rule lets a member read shared rows plus their own, and
     * Firestore authorises a query by proving every document it could return satisfies the rule.
     * An unfiltered query cannot be proven, so for a member it is **rejected outright** — the
     * listener errors and the list goes empty rather than returning a subset. Members therefore
     * get two constrained queries, merged here.
     *
     * Neither query carries an `orderBy`. That keeps them equality-only, which the automatic
     * single-field index already covers, so no composite index (and no firestore.indexes.json)
     * is needed. Both platforms already sort client-side.
     */
    fun observeExpenses(
        householdId: String,
        canReadAll: Boolean,
        uid: String
    ): Flow<List<Expense>> = callbackFlow {
        val collection = firestore.collection("households").document(householdId)
            .collection("expenses")

        // Merge by document id, not by appending: a member's own *shared* rows satisfy both
        // queries, and concatenating would list them twice.
        val bySource = mutableMapOf<String, Map<String, Expense>>()

        fun emitMerged() {
            trySend(bySource.values.flatMap { it.values })
        }

        fun decode(doc: com.google.firebase.firestore.DocumentSnapshot): Expense? =
            if (!doc.exists()) null else Expense(
                        id = doc.id,
                        householdId = householdId,
                        amount = doc.getDouble("amount") ?: 0.0,
                        categoryId = doc.getString("categoryId") ?: "",
                        categoryName = doc.getString("categoryName") ?: "",
                        date = doc.getEpochMillis("date") ?: 0L,
                        notes = doc.getString("notes") ?: "",
                        addedBy = doc.getString("addedBy") ?: "",
                        addedByName = doc.getString("addedByName") ?: "",
                        paymentMethod = PaymentMethod.fromWire(doc.getString("paymentMethod")),
                        scope = ExpenseScope.fromWire(doc.getString("scope")),
                        createdAt = doc.getEpochMillis("createdAt") ?: 0L,
                        updatedAt = doc.getEpochMillis("updatedAt") ?: 0L,
                        isSynced = true
                    )

        val listeners = if (canReadAll) {
            // Owner / admin: everything satisfies the rule, so one unfiltered listener is
            // provable and stays the cheapest option.
            listOf(
                "all" to collection.addSnapshotListener { snap, error ->
                    if (error != null) return@addSnapshotListener
                    bySource["all"] = snap?.documents?.mapNotNull(::decode)?.associateBy { it.id }
                        ?: emptyMap()
                    emitMerged()
                }
            )
        } else {
            listOf(
                "shared" to collection.whereEqualTo("scope", ExpenseScope.SHARED.wire)
                    .addSnapshotListener { snap, error ->
                        if (error != null) return@addSnapshotListener
                        bySource["shared"] =
                            snap?.documents?.mapNotNull(::decode)?.associateBy { it.id } ?: emptyMap()
                        emitMerged()
                    },
                "mine" to collection.whereEqualTo("addedBy", uid)
                    .addSnapshotListener { snap, error ->
                        if (error != null) return@addSnapshotListener
                        bySource["mine"] =
                            snap?.documents?.mapNotNull(::decode)?.associateBy { it.id } ?: emptyMap()
                        emitMerged()
                    }
            )
        }

        awaitClose { listeners.forEach { (_, registration) -> registration.remove() } }
    }

    // --- Budgets / Savings goals / Recurring expenses ---
    //
    // These three were Room-only, so they never synced between devices or household members
    // and were lost on reinstall. Document shapes match what the iOS app already reads and
    // writes, so the two platforms interoperate.

    private fun budgetsCollection(householdId: String) =
        firestore.collection("households").document(householdId).collection("budgets")

    private fun savingsCollection(householdId: String) =
        firestore.collection("households").document(householdId).collection("savingsGoals")

    private fun recurringCollection(householdId: String) =
        firestore.collection("households").document(householdId).collection("recurring")

    fun observeBudgets(householdId: String): Flow<List<Budget>> = callbackFlow {
        val listener = budgetsCollection(householdId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                trySend(
                    snapshot?.documents?.map { doc ->
                        Budget(
                            id = doc.id,
                            householdId = householdId,
                            categoryId = doc.getString("categoryId") ?: "",
                            categoryName = doc.getString("categoryName") ?: "",
                            monthlyLimit = doc.getDouble("monthlyLimit") ?: 0.0
                        )
                    } ?: emptyList()
                )
            }
        awaitClose { listener.remove() }
    }

    suspend fun upsertBudget(budget: Budget) {
        budgetsCollection(budget.householdId).document(budget.id).set(
            mapOf(
                "householdId" to budget.householdId,
                "categoryId" to budget.categoryId,
                "categoryName" to budget.categoryName,
                "monthlyLimit" to budget.monthlyLimit
            )
        ).await()
    }

    suspend fun deleteBudget(householdId: String, id: String) {
        budgetsCollection(householdId).document(id).delete().await()
    }

    fun observeSavingsGoals(householdId: String): Flow<List<SavingsGoal>> = callbackFlow {
        val listener = savingsCollection(householdId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                trySend(
                    snapshot?.documents?.map { doc ->
                        SavingsGoal(
                            id = doc.id,
                            householdId = householdId,
                            name = doc.getString("name") ?: "",
                            targetAmount = doc.getDouble("targetAmount") ?: 0.0,
                            currentAmount = doc.getDouble("currentAmount") ?: 0.0,
                            icon = doc.getString("icon") ?: "🎯",
                            targetDate = doc.getEpochMillis("targetDate"),
                            createdAt = doc.getEpochMillis("createdAt") ?: 0L
                        )
                    } ?: emptyList()
                )
            }
        awaitClose { listener.remove() }
    }

    suspend fun upsertSavingsGoal(goal: SavingsGoal) {
        val data = mutableMapOf<String, Any>(
            "householdId" to goal.householdId,
            "name" to goal.name,
            "targetAmount" to goal.targetAmount,
            "currentAmount" to goal.currentAmount,
            "icon" to goal.icon,
            "createdAt" to goal.createdAt
        )
        goal.targetDate?.let { data["targetDate"] = it }
        savingsCollection(goal.householdId).document(goal.id).set(data).await()
    }

    suspend fun deleteSavingsGoal(householdId: String, id: String) {
        savingsCollection(householdId).document(id).delete().await()
    }

    fun observeRecurringExpenses(householdId: String): Flow<List<RecurringExpense>> = callbackFlow {
        val listener = recurringCollection(householdId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                trySend(snapshot?.documents?.map { it.toRecurringExpense(householdId) } ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    suspend fun getActiveRecurringExpenses(householdId: String): List<RecurringExpense> =
        recurringCollection(householdId).get().await()
            .documents.map { it.toRecurringExpense(householdId) }
            .filter { it.isActive }

    private fun DocumentSnapshot.toRecurringExpense(householdId: String) = RecurringExpense(
        id = id,
        householdId = householdId,
        amount = getDouble("amount") ?: 0.0,
        categoryId = getString("categoryId") ?: "",
        categoryName = getString("categoryName") ?: "",
        notes = getString("notes") ?: "",
        addedBy = getString("addedBy") ?: "",
        addedByName = getString("addedByName") ?: "",
        paymentMethod = PaymentMethod.fromWire(getString("paymentMethod")),
        frequency = frequencyFromOrdinal((getLong("frequency") ?: 2L).toInt()),
        dayOfWeek = getLong("dayOfWeek")?.toInt(),
        dayOfMonth = getLong("dayOfMonth")?.toInt(),
        monthOfYear = getLong("monthOfYear")?.toInt(),
        startDate = getEpochMillis("startDate") ?: 0L,
        endDate = getEpochMillis("endDate"),
        lastGeneratedDate = getEpochMillis("lastGeneratedDate"),
        isActive = getBoolean("isActive") ?: true,
        createdAt = getEpochMillis("createdAt") ?: 0L
    )

    private fun frequencyFromOrdinal(ordinal: Int): RecurringFrequency =
        RecurringFrequency.entries.getOrElse(ordinal) { RecurringFrequency.MONTHLY }

    suspend fun upsertRecurringExpense(recurring: RecurringExpense) {
        val data = mutableMapOf<String, Any>(
            "householdId" to recurring.householdId,
            "amount" to recurring.amount,
            "categoryId" to recurring.categoryId,
            "categoryName" to recurring.categoryName,
            "notes" to recurring.notes,
            "addedBy" to recurring.addedBy,
            "addedByName" to recurring.addedByName,
            // Stored as the enum ordinal, which is what iOS reads and what Room used.
            "frequency" to recurring.frequency.ordinal,
            "startDate" to recurring.startDate,
            "isActive" to recurring.isActive,
            "paymentMethod" to recurring.paymentMethod.wire,
            "createdAt" to recurring.createdAt
        )
        recurring.dayOfWeek?.let { data["dayOfWeek"] = it }
        recurring.dayOfMonth?.let { data["dayOfMonth"] = it }
        recurring.monthOfYear?.let { data["monthOfYear"] = it }
        recurring.endDate?.let { data["endDate"] = it }
        recurring.lastGeneratedDate?.let { data["lastGeneratedDate"] = it }
        recurringCollection(recurring.householdId).document(recurring.id).set(data).await()
    }

    suspend fun updateRecurringLastGenerated(householdId: String, id: String, timestamp: Long) {
        recurringCollection(householdId).document(id)
            .set(mapOf("lastGeneratedDate" to timestamp), com.google.firebase.firestore.SetOptions.merge())
            .await()
    }

    suspend fun deleteRecurringExpense(householdId: String, id: String) {
        recurringCollection(householdId).document(id).delete().await()
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
                        date = doc.getEpochMillis("date") ?: 0L,
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
                        date = doc.getEpochMillis("date") ?: 0L,
                        addedBy = doc.getString("addedBy") ?: ""
                    )
                } ?: emptyList()
                trySend(liabilities)
            }
        awaitClose { listener.remove() }
    }
}
