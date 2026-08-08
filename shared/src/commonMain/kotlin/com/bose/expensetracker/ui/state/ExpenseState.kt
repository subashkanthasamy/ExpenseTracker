package com.bose.expensetracker.ui.state

import com.bose.expensetracker.domain.model.Category
import com.bose.expensetracker.domain.model.Expense
import com.bose.expensetracker.domain.usecase.access.HouseholdRole
import kotlinx.datetime.Clock

/** Date windows the expense list can be narrowed to. `ALL` means no date constraint. */
enum class DateRangeFilter(val label: String) {
    ALL("All"),
    THIS_MONTH("This month"),
    LAST_MONTH("Last month"),
    THIS_YEAR("This year")
}

/**
 * Everything the user has chosen to narrow the expense list by.
 *
 * Kept as one value rather than loose fields so a screen has a single thing to write and a
 * single thing to read. The Android list previously held its filter state twice — three
 * flows feeding the filter plus mirror fields on [ExpenseListUiState] that nothing read —
 * and the obvious "fix" of writing the mirror produced a highlighted chip over an
 * unfiltered list.
 */
data class ExpenseFilterCriteria(
    val searchQuery: String = "",
    val personFilter: String? = null,
    val categoryFilter: String? = null,
    val dateRange: DateRangeFilter = DateRangeFilter.ALL
) {
    /** True when anything is actually narrowing the list — drives the empty-state copy. */
    val isActive: Boolean
        get() = searchQuery.isNotBlank() ||
                personFilter != null ||
                categoryFilter != null ||
                dateRange != DateRangeFilter.ALL
}

/** A selectable filter chip: [id] is matched against the expense, [label] is displayed. */
data class FilterOption(
    val id: String,
    val label: String
)

data class ExpenseListUiState(
    val expenses: List<Expense> = emptyList(),
    val isLoading: Boolean = true,
    val criteria: ExpenseFilterCriteria = ExpenseFilterCriteria(),
    val categoryOptions: List<FilterOption> = emptyList(),
    val personOptions: List<FilterOption> = emptyList(),
    /**
     * Size of the list before filtering. [expenses] is post-filter, so this is the only way
     * to tell "you have no expenses" apart from "your filters matched nothing" — showing the
     * former for the latter reads as data loss.
     */
    val totalCount: Int = 0,
    /** Signed-in account, so a row can tell whether it is the caller's to edit. */
    val currentUid: String = "",
    /** Caller's role in the household. Decides which controls render; not enforcement. */
    val role: HouseholdRole = HouseholdRole.NONE,
    val error: String? = null
)

data class AddEditExpenseUiState(
    val amount: String = "",
    val selectedCategory: Category? = null,
    val categories: List<Category> = emptyList(),
    val date: Long = Clock.System.now().toEpochMilliseconds(),
    val notes: String = "",
    val addedByName: String = "",
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)
