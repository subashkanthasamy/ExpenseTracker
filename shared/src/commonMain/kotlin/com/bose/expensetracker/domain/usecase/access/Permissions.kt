package com.bose.expensetracker.domain.usecase.access

import com.bose.expensetracker.domain.model.Expense
import com.bose.expensetracker.domain.model.Household

/**
 * What a signed-in account may do inside a household.
 *
 * ADMIN is global — it applies to every household — and is granted by a Firebase custom claim,
 * never by a field this account could write. OWNER is the creator of one specific household.
 */
enum class HouseholdRole {
    ADMIN,
    OWNER,
    MEMBER,
    GUEST,
    /** Signed in but not part of this household. */
    NONE
}

/**
 * The single definition of the access rules, shared so Android and iOS grey out the same
 * buttons.
 *
 * **This is not the enforcement.** Both apps talk to Firestore directly with the user's own
 * credentials, so `firestore.rules` is the only thing standing between a user and the data —
 * these functions exist to keep the UI honest, not to keep it safe. Every rule below has a
 * counterpart in `firestore.rules`, and the two must be changed together.
 */
object Permissions {

    const val ROLE_MEMBER = "member"
    const val ROLE_GUEST = "guest"

    /**
     * Role of [uid] in [household].
     *
     * [isAdmin] comes from the `admin` custom claim on the ID token. Households written before
     * roles existed have a blank [Household.ownerUid]; those fall back to the first entry of
     * `memberUids`, which is the creator because the create rule admits exactly one initial
     * member and joins append. The backfill script writes that same value permanently.
     */
    fun roleOf(household: Household, uid: String, isAdmin: Boolean = false): HouseholdRole {
        if (isAdmin) return HouseholdRole.ADMIN

        val owner = household.ownerUid.ifBlank { household.memberUids.firstOrNull() ?: "" }
        if (uid.isNotBlank() && uid == owner) return HouseholdRole.OWNER

        if (uid !in household.memberUids) return HouseholdRole.NONE

        return when (household.roles[uid]) {
            ROLE_GUEST -> HouseholdRole.GUEST
            // Absent means a member from before roles existed; treat as MEMBER so nobody
            // silently loses access between the rules deploy and the backfill.
            else -> HouseholdRole.MEMBER
        }
    }

    private val OWNER_LEVEL = setOf(HouseholdRole.ADMIN, HouseholdRole.OWNER)
    private val WRITE_LEVEL = setOf(HouseholdRole.ADMIN, HouseholdRole.OWNER, HouseholdRole.MEMBER)

    fun canViewHousehold(role: HouseholdRole): Boolean = role != HouseholdRole.NONE

    fun canDeleteHousehold(role: HouseholdRole): Boolean = role in OWNER_LEVEL

    fun canRenameHousehold(role: HouseholdRole): Boolean = role in OWNER_LEVEL

    fun canManageMembers(role: HouseholdRole): Boolean = role in OWNER_LEVEL

    /** Creating, rotating or revoking the invite code. */
    fun canManageInviteCode(role: HouseholdRole): Boolean = role in OWNER_LEVEL

    /**
     * Categories, budgets, savings goals, recurring rules, assets and liabilities.
     *
     * Owner-level by decision: members contribute expenses, they do not reshape the household's
     * configuration.
     */
    fun canManageSharedConfig(role: HouseholdRole): Boolean = role in OWNER_LEVEL

    fun canAddExpense(role: HouseholdRole): Boolean = role in WRITE_LEVEL

    /** Editing or deleting an existing expense: your own, or anyone's if you own the household. */
    fun canEditExpense(role: HouseholdRole, expense: Expense, uid: String): Boolean =
        role in OWNER_LEVEL || (role == HouseholdRole.MEMBER && expense.addedBy == uid)

    /** The owner cannot walk away and orphan the household; they transfer or delete it. */
    fun canLeaveHousehold(role: HouseholdRole): Boolean =
        role == HouseholdRole.MEMBER || role == HouseholdRole.GUEST

    /** Short name for the role, shown on the household screen. */
    fun label(role: HouseholdRole): String = when (role) {
        HouseholdRole.ADMIN -> "Admin"
        HouseholdRole.OWNER -> "Owner"
        HouseholdRole.MEMBER -> "Member"
        HouseholdRole.GUEST -> "Guest"
        HouseholdRole.NONE -> ""
    }

    /**
     * What the role may do, in one line.
     *
     * Shown next to the label so a missing button reads as a permission rather than a bug —
     * without it, a member simply finds controls absent and assumes the app is broken.
     */
    fun description(role: HouseholdRole): String = when (role) {
        HouseholdRole.ADMIN -> "Full access to every household"
        HouseholdRole.OWNER -> "You created this household and manage its members and settings"
        HouseholdRole.MEMBER -> "You can add and edit your own expenses"
        HouseholdRole.GUEST -> "You can view this household but not change anything"
        HouseholdRole.NONE -> ""
    }
}
