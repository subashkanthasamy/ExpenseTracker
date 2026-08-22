package com.bose.expensetracker.domain.usecase.access

import com.bose.expensetracker.domain.model.Expense
import com.bose.expensetracker.domain.model.ExpenseScope
import com.bose.expensetracker.domain.model.Household

/**
 * What a signed-in account may do inside a household.
 *
 * ADMIN is global — it applies to every household — and is granted by a Firebase custom claim,
 * never by a field this account could write. OWNER is the creator of one specific household.
 */
enum class HouseholdRole {
    /**
     * Household co-manager, appointed by the owner. Everything the owner can do except delete
     * the household.
     *
     * Note this is NOT the global `admin` custom claim — that is support access and maps to
     * [OWNER], so granting it cannot *reduce* what an account may do.
     */
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
    const val ROLE_ADMIN = "admin"

    /**
     * Role of [uid] in [household].
     *
     * [isAdmin] comes from the `admin` custom claim on the ID token. Households written before
     * roles existed have a blank [Household.ownerUid]; those fall back to the first entry of
     * `memberUids`, which is the creator because the create rule admits exactly one initial
     * member and joins append. The backfill script writes that same value permanently.
     */
    fun roleOf(household: Household, uid: String, isAdmin: Boolean = false): HouseholdRole {
        // The global support claim is owner-equivalent, not the household ADMIN role. Mapping it
        // to ADMIN would mean granting support access took away the ability to delete.
        if (isAdmin) return HouseholdRole.OWNER

        val owner = household.ownerUid.ifBlank { household.memberUids.firstOrNull() ?: "" }
        if (uid.isNotBlank() && uid == owner) return HouseholdRole.OWNER

        if (uid !in household.memberUids) return HouseholdRole.NONE

        return when (household.roles[uid]) {
            ROLE_ADMIN -> HouseholdRole.ADMIN
            ROLE_GUEST -> HouseholdRole.GUEST
            // Absent means a member from before roles existed; treat as MEMBER so nobody
            // silently loses access between the rules deploy and the backfill.
            else -> HouseholdRole.MEMBER
        }
    }

    /** Owner and admin: everything that manages the household or its shared configuration. */
    private val MANAGER_LEVEL = setOf(HouseholdRole.ADMIN, HouseholdRole.OWNER)
    private val WRITE_LEVEL = setOf(HouseholdRole.ADMIN, HouseholdRole.OWNER, HouseholdRole.MEMBER)

    fun canViewHousehold(role: HouseholdRole): Boolean = role != HouseholdRole.NONE

    /**
     * Owner only. An admin manages the household; destroying it stays with the creator, and
     * there is no ownership transfer, so this is the one thing that cannot be delegated.
     */
    fun canDeleteHousehold(role: HouseholdRole): Boolean = role == HouseholdRole.OWNER

    fun canRenameHousehold(role: HouseholdRole): Boolean = role in MANAGER_LEVEL

    fun canManageMembers(role: HouseholdRole): Boolean = role in MANAGER_LEVEL

    /** Creating, rotating or revoking the invite code. */
    fun canManageInviteCode(role: HouseholdRole): Boolean = role in MANAGER_LEVEL

    /**
     * Categories, budgets, savings goals, recurring rules, assets and liabilities.
     *
     * Owner-level by decision: members contribute expenses, they do not reshape the household's
     * configuration.
     */
    fun canManageSharedConfig(role: HouseholdRole): Boolean = role in MANAGER_LEVEL

    fun canAddExpense(role: HouseholdRole): Boolean = role in WRITE_LEVEL

    /**
     * Whether this role may read every expense, personal ones included.
     *
     * This is not only a UI concern: it selects the Firestore query shape. A role that cannot
     * read all must query with constraints, because the rules reject an unfiltered list rather
     * than filtering it — get this wrong and the expense list goes empty, not partial.
     */
    fun canReadAllExpenses(role: HouseholdRole): Boolean = role in MANAGER_LEVEL

    /** Editing or deleting an existing expense: your own, or anyone's if you own the household. */
    fun canEditExpense(role: HouseholdRole, expense: Expense, uid: String): Boolean =
        role in MANAGER_LEVEL || (role == HouseholdRole.MEMBER && expense.addedBy == uid)

    /**
     * Anyone but the owner may leave. The owner walking away would orphan the household, and
     * there is no ownership transfer, so they delete it instead. An admin is not the owner, so
     * losing them costs the household nothing.
     */
    fun canLeaveHousehold(role: HouseholdRole): Boolean =
        role == HouseholdRole.ADMIN ||
            role == HouseholdRole.MEMBER ||
            role == HouseholdRole.GUEST

    /**
     * Rows that count toward the household's *shared* figures.
     *
     * Personal rows are excluded everywhere a number is presented as the household's, because a
     * member cannot see peers' personal rows and would otherwise get a different total from the
     * owner for the same label.
     */
    fun sharedOnly(expenses: List<Expense>): List<Expense> =
        expenses.filter { it.scope == ExpenseScope.SHARED }

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
        HouseholdRole.ADMIN -> "You manage this household's members and settings"
        HouseholdRole.OWNER -> "You created this household and manage its members and settings"
        HouseholdRole.MEMBER -> "You can add and edit your own expenses"
        HouseholdRole.GUEST -> "You can view this household but not change anything"
        HouseholdRole.NONE -> ""
    }
}
