package com.bose.expensetracker.ui.screen.expense

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bose.expensetracker.domain.model.Expense
import com.bose.expensetracker.ui.components.DateGroupHeader
import com.bose.expensetracker.ui.components.ScrollableFilterChipRow
import com.bose.expensetracker.ui.components.TimelineItem
import com.bose.expensetracker.ui.components.formatCurrency
import com.bose.expensetracker.ui.components.getCategoryEmoji
import com.bose.expensetracker.ui.state.DateRangeFilter
import com.bose.expensetracker.ui.state.ExpenseFilterCriteria
import com.bose.expensetracker.ui.state.FilterOption
import com.bose.expensetracker.ui.theme.AccentPurple
import com.bose.expensetracker.ui.theme.ExpenseRed
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun ExpenseListScreen(
    viewModel: ExpenseListViewModel,
    onAddExpense: () -> Unit,
    onEditExpense: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var expenseToDelete by remember { mutableStateOf<Expense?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AccentPurple.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = AccentPurple,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "EXPENSE TRACKER",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
            IconButton(onClick = { }) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = "Notifications",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Nothing recorded yet means nothing to narrow — don't show filters over an empty list.
        if (uiState.totalCount > 0) {
            Spacer(modifier = Modifier.height(16.dp))

            ExpenseFilters(
                criteria = uiState.criteria,
                categoryOptions = uiState.categoryOptions,
                personOptions = uiState.personOptions,
                onSearchChange = viewModel::setSearchQuery,
                onCategoryChange = viewModel::setCategoryFilter,
                onPersonChange = viewModel::setPersonFilter,
                onDateRangeChange = viewModel::setDateRange
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentPurple)
                }
            }

            uiState.expenses.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // totalCount distinguishes "nothing recorded" from "filters excluded
                        // everything" — showing the former for the latter reads as data loss.
                        val filteredOut = uiState.totalCount > 0
                        Text(
                            if (filteredOut) "No matching expenses" else "No expenses yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (filteredOut) {
                                "None of your ${uiState.totalCount} expenses match these filters"
                            } else {
                                "Tap + to add your first expense"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (filteredOut) {
                            Spacer(modifier = Modifier.height(12.dp))
                            TextButton(onClick = viewModel::clearFilters) {
                                Text("Clear filters", color = AccentPurple)
                            }
                        }
                    }
                }
            }

            else -> {
                val sorted = uiState.expenses.sortedByDescending { it.date }
                val grouped = sorted.groupBy { expense ->
                    getDateLabel(expense.date)
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    grouped.forEach { (dateLabel, expenses) ->
                        val groupTotal = expenses.sumOf { it.amount }

                        item(key = "header_$dateLabel") {
                            DateGroupHeader(
                                date = dateLabel,
                                total = "-${formatCurrency(groupTotal)}"
                            )
                        }

                        itemsIndexed(
                            expenses,
                            key = { _, expense -> expense.id }
                        ) { index, expense ->
                            val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                            val timeStr = timeFormat.format(Date(expense.date))

                            @OptIn(ExperimentalFoundationApi::class)
                            TimelineItem(
                                icon = getCategoryEmoji(expense.categoryName),
                                title = expense.notes.ifBlank { expense.categoryName },
                                subtitle = "${expense.categoryName} • $timeStr",
                                amount = -expense.amount,
                                isLast = index == expenses.lastIndex,
                                modifier = Modifier.combinedClickable(
                                    onClick = { onEditExpense(expense.id) },
                                    onLongClick = { expenseToDelete = expense }
                                )
                            )
                        }

                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }

                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }

    // Delete confirmation dialog
    expenseToDelete?.let { expense ->
        AlertDialog(
            onDismissRequest = { expenseToDelete = null },
            title = { Text("Delete Expense") },
            text = {
                Text(
                    "Are you sure you want to delete this ${expense.categoryName} expense of ${
                        formatCurrency(expense.amount)
                    }?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteExpense(expense.id)
                    expenseToDelete = null
                }) {
                    Text("Delete", color = ExpenseRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { expenseToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Search box plus the category / person / date-range chip rows.
 *
 * Every selection is hoisted out of [criteria] rather than kept in local `remember` state —
 * the previous version stored the selected tab locally, never read it, and rendered the
 * unfiltered list, so the chips highlighted but nothing changed.
 */
@Composable
private fun ExpenseFilters(
    criteria: ExpenseFilterCriteria,
    categoryOptions: List<FilterOption>,
    personOptions: List<FilterOption>,
    onSearchChange: (String) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onPersonChange: (String?) -> Unit,
    onDateRangeChange: (DateRangeFilter) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = criteria.searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            placeholder = { Text("Search notes, category, person or amount") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (criteria.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )

        // "All" is index 0 in each row, so a selected id maps to index + 1.
        ScrollableFilterChipRow(
            labels = listOf("All dates") + DateRangeFilter.entries.drop(1).map { it.label },
            selectedIndex = DateRangeFilter.entries.indexOf(criteria.dateRange),
            onSelected = { onDateRangeChange(DateRangeFilter.entries[it]) }
        )

        if (categoryOptions.isNotEmpty()) {
            ScrollableFilterChipRow(
                labels = listOf("All categories") +
                        categoryOptions.map { "${getCategoryEmoji(it.label)} ${it.label}" },
                selectedIndex = optionIndex(categoryOptions, criteria.categoryFilter),
                onSelected = { onCategoryChange(optionIdAt(categoryOptions, it)) }
            )
        }

        // One member means the filter can only ever be a no-op.
        if (personOptions.size > 1) {
            ScrollableFilterChipRow(
                labels = listOf("Everyone") + personOptions.map { it.label },
                selectedIndex = optionIndex(personOptions, criteria.personFilter),
                onSelected = { onPersonChange(optionIdAt(personOptions, it)) }
            )
        }
    }
}

/** Chip index for [selectedId], where index 0 is the "All" chip. */
private fun optionIndex(options: List<FilterOption>, selectedId: String?): Int {
    if (selectedId == null) return 0
    val index = options.indexOfFirst { it.id == selectedId }
    return if (index >= 0) index + 1 else 0
}

/** Id behind chip [index], or `null` for the "All" chip. */
private fun optionIdAt(options: List<FilterOption>, index: Int): String? =
    options.getOrNull(index - 1)?.id

private fun getDateLabel(timestamp: Long): String {
    val now = Calendar.getInstance()
    val date = Calendar.getInstance().apply { timeInMillis = timestamp }

    val diffDays = TimeUnit.MILLISECONDS.toDays(now.timeInMillis - date.timeInMillis)

    return when {
        isSameDay(now, date) -> "Today"
        diffDays == 1L -> "Yesterday"
        else -> {
            val format = SimpleDateFormat("MMM d", Locale.getDefault())
            format.format(Date(timestamp))
        }
    }
}

private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}
