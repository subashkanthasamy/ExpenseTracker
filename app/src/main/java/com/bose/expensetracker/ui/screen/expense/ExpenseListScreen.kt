package com.bose.expensetracker.ui.screen.expense

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
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
                onDateRangeChange = viewModel::setDateRange,
                onClearAll = viewModel::clearFilters
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
 * One search row with a filter button, plus a chip per active filter.
 *
 * The date / category / person choices live in a sheet rather than as three stacked chip rows
 * above the list — that pushed the expenses themselves most of the way down the screen. What
 * stays inline is only what's currently on, so an unfiltered list costs a single row.
 *
 * Every selection is hoisted out of [criteria] rather than kept in local `remember` state; the
 * original screen stored its tab selection locally, never read it, and rendered the unfiltered
 * list, so chips highlighted while nothing changed.
 */
@Composable
private fun ExpenseFilters(
    criteria: ExpenseFilterCriteria,
    categoryOptions: List<FilterOption>,
    personOptions: List<FilterOption>,
    onSearchChange: (String) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onPersonChange: (String?) -> Unit,
    onDateRangeChange: (DateRangeFilter) -> Unit,
    onClearAll: () -> Unit
) {
    var showSheet by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = criteria.searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search") },
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

            val hasChipFilters = criteria.dateRange != DateRangeFilter.ALL ||
                    criteria.categoryFilter != null ||
                    criteria.personFilter != null
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (hasChipFilters) AccentPurple
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { showSheet = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = "Filters",
                    tint = if (hasChipFilters) Color.White
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        ActiveFilterChips(
            criteria = criteria,
            categoryOptions = categoryOptions,
            personOptions = personOptions,
            onCategoryChange = onCategoryChange,
            onPersonChange = onPersonChange,
            onDateRangeChange = onDateRangeChange
        )
    }

    if (showSheet) {
        ExpenseFilterSheet(
            criteria = criteria,
            categoryOptions = categoryOptions,
            personOptions = personOptions,
            onCategoryChange = onCategoryChange,
            onPersonChange = onPersonChange,
            onDateRangeChange = onDateRangeChange,
            onClearAll = onClearAll,
            onDismiss = { showSheet = false }
        )
    }
}

/** A removable chip per active filter, so what's on is visible without opening the sheet. */
@Composable
private fun ActiveFilterChips(
    criteria: ExpenseFilterCriteria,
    categoryOptions: List<FilterOption>,
    personOptions: List<FilterOption>,
    onCategoryChange: (String?) -> Unit,
    onPersonChange: (String?) -> Unit,
    onDateRangeChange: (DateRangeFilter) -> Unit
) {
    // The search box already shows its own text, so it doesn't get a chip.
    val active = buildList {
        if (criteria.dateRange != DateRangeFilter.ALL) {
            add(criteria.dateRange.label to { onDateRangeChange(DateRangeFilter.ALL) })
        }
        categoryOptions.firstOrNull { it.id == criteria.categoryFilter }?.let { option ->
            add("${getCategoryEmoji(option.label)} ${option.label}" to { onCategoryChange(null) })
        }
        personOptions.firstOrNull { it.id == criteria.personFilter }?.let { option ->
            add(option.label to { onPersonChange(null) })
        }
    }
    if (active.isEmpty()) return

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 20.dp)
    ) {
        items(active.size) { index ->
            val (label, onRemove) = active[index]
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(AccentPurple.copy(alpha = 0.15f))
                    .clickable(onClick = onRemove)
                    .padding(start = 14.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = AccentPurple,
                    maxLines = 1
                )
                Icon(
                    Icons.Default.Clear,
                    contentDescription = "Remove $label filter",
                    tint = AccentPurple,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseFilterSheet(
    criteria: ExpenseFilterCriteria,
    categoryOptions: List<FilterOption>,
    personOptions: List<FilterOption>,
    onCategoryChange: (String?) -> Unit,
    onPersonChange: (String?) -> Unit,
    onDateRangeChange: (DateRangeFilter) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Filters",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (criteria.isActive) {
                    TextButton(onClick = onClearAll) {
                        Text("Clear all", color = AccentPurple)
                    }
                }
            }

            FilterSection("Date")
            // "All" is index 0 in each row, so a selected id maps to index + 1.
            ScrollableFilterChipRow(
                labels = listOf("All dates") + DateRangeFilter.entries.drop(1).map { it.label },
                selectedIndex = DateRangeFilter.entries.indexOf(criteria.dateRange),
                onSelected = { onDateRangeChange(DateRangeFilter.entries[it]) }
            )

            if (categoryOptions.isNotEmpty()) {
                FilterSection("Category")
                ScrollableFilterChipRow(
                    labels = listOf("All categories") +
                            categoryOptions.map { "${getCategoryEmoji(it.label)} ${it.label}" },
                    selectedIndex = optionIndex(categoryOptions, criteria.categoryFilter),
                    onSelected = { onCategoryChange(optionIdAt(categoryOptions, it)) }
                )
            }

            // One member means the filter can only ever be a no-op.
            if (personOptions.size > 1) {
                FilterSection("Added by")
                ScrollableFilterChipRow(
                    labels = listOf("Everyone") + personOptions.map { it.label },
                    selectedIndex = optionIndex(personOptions, criteria.personFilter),
                    onSelected = { onPersonChange(optionIdAt(personOptions, it)) }
                )
            }
        }
    }
}

@Composable
private fun FilterSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 8.dp)
    )
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
