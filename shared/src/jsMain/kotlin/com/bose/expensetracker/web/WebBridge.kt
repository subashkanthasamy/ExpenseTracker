@file:OptIn(ExperimentalJsExport::class)

package com.bose.expensetracker.web

import com.bose.expensetracker.domain.model.Budget
import com.bose.expensetracker.domain.model.CategoryPresets
import com.bose.expensetracker.domain.model.Expense
import com.bose.expensetracker.domain.model.ExpenseScope
import com.bose.expensetracker.domain.model.Household
import com.bose.expensetracker.domain.model.PaymentMethod
import com.bose.expensetracker.domain.model.SavingsGoal
import com.bose.expensetracker.domain.usecase.access.HouseholdRole
import com.bose.expensetracker.domain.usecase.access.Permissions
import com.bose.expensetracker.domain.usecase.filter.ExpenseFilter
import com.bose.expensetracker.domain.usecase.insights.PaymentMethodSplitCalculator
import com.bose.expensetracker.domain.usecase.insights.SpendingSplitCalculator
import com.bose.expensetracker.domain.usecase.smsimport.SmsCategoryMatcher
import com.bose.expensetracker.domain.usecase.smsimport.SmsTransactionParser
import com.bose.expensetracker.ui.state.DateRangeFilter
import com.bose.expensetracker.ui.state.ExpenseFilterCriteria
import com.bose.expensetracker.util.CategoryIcons
import com.bose.expensetracker.util.formatAmount
import com.bose.expensetracker.util.formatCurrency

/**
 * The web front-end's view of the shared module — the counterpart of `SharedBridge.swift`.
 *
 * Kotlin/JS will not export three things the domain layer is built from, and the compiler
 * reports each as a *warning*, not an error:
 *
 *  1. `Long` — every date here is epoch millis, so the boundary uses `Double` instead. JS
 *     numbers hold integers exactly to 2^53, which is roughly ±285,000 years in millis.
 *  2. Kotlin `data class`es — `@JsExport` lives in `kotlin.js`, so it cannot be applied to
 *     anything in `commonMain`.
 *  3. `List` / `Map` — these become `Array` and plain objects.
 *
 * So nothing from `commonMain` crosses the boundary. Everything here takes and returns plain
 * JS objects, which is also exactly what the Firestore JS SDK hands back: a document's `data()`
 * can go straight in, and the results drop straight into React state.
 *
 * Because those three constraints are warnings, `shared/build.gradle.kts` turns warnings into
 * errors for this source set — otherwise a `Long` added to a signature here would compile
 * clean and silently reach TypeScript as an opaque object.
 */

// ---------------------------------------------------------------------------
// Reading plain JS objects
// ---------------------------------------------------------------------------

/** `Number(...)`, so a numeric string from a hand-edited document still coerces. */
private val jsNumber: (dynamic) -> Double = js("Number")
private val jsObjectKeys: (dynamic) -> Array<String> = js("Object.keys")

private fun newObject(): dynamic = js("({})")

private fun str(o: dynamic, key: String, default: String = ""): String {
    val v = o[key]
    return if (v == null || v == undefined) default else v.toString()
}

/** Blank and absent both become null, which is what the nullable filter fields mean. */
private fun strOrNull(o: dynamic, key: String): String? =
    str(o, key).ifBlank { null }

private fun num(o: dynamic, key: String, default: Double = 0.0): Double {
    val v = o[key]
    if (v == null || v == undefined) return default
    val d = jsNumber(v)
    return if (d.isNaN()) default else d
}

private fun bool(o: dynamic, key: String, default: Boolean = false): Boolean {
    val v = o[key]
    return if (v == null || v == undefined) default else v == true
}

private fun strList(o: dynamic, key: String): List<String> {
    val v = o[key]
    if (v == null || v == undefined) return emptyList()
    val length = jsNumber(v.length)
    if (length.isNaN()) return emptyList()
    val out = ArrayList<String>(length.toInt())
    for (i in 0 until length.toInt()) {
        val item = v[i]
        if (item != null && item != undefined) out.add(item.toString())
    }
    return out
}

private fun strMap(o: dynamic, key: String): Map<String, String> {
    val v = o[key]
    if (v == null || v == undefined) return emptyMap()
    val out = LinkedHashMap<String, String>()
    for (k in jsObjectKeys(v)) {
        val value = v[k]
        if (value != null && value != undefined) out[k] = value.toString()
    }
    return out
}

// ---------------------------------------------------------------------------
// Model conversion
// ---------------------------------------------------------------------------

/**
 * A Firestore expense document (plus its `id`) as an [Expense].
 *
 * `isSynced` is always true: it exists for Android's Room cache, and the web client reads
 * Firestore directly, so nothing here is ever pending.
 */
private fun toExpense(o: dynamic): Expense = Expense(
    id = str(o, "id"),
    householdId = str(o, "householdId"),
    amount = num(o, "amount"),
    categoryId = str(o, "categoryId"),
    categoryName = str(o, "categoryName"),
    date = num(o, "date").toLong(),
    notes = str(o, "notes"),
    addedBy = str(o, "addedBy"),
    addedByName = str(o, "addedByName"),
    createdAt = num(o, "createdAt").toLong(),
    updatedAt = num(o, "updatedAt").toLong(),
    paymentMethod = PaymentMethod.fromWire(str(o, "paymentMethod")),
    scope = ExpenseScope.fromWire(str(o, "scope")),
    isSynced = true
)

private fun fromExpense(e: Expense): dynamic {
    val o = newObject()
    o.id = e.id
    o.householdId = e.householdId
    o.amount = e.amount
    o.categoryId = e.categoryId
    o.categoryName = e.categoryName
    o.date = e.date.toDouble()
    o.notes = e.notes
    o.addedBy = e.addedBy
    o.addedByName = e.addedByName
    o.createdAt = e.createdAt.toDouble()
    o.updatedAt = e.updatedAt.toDouble()
    o.paymentMethod = e.paymentMethod.wire
    o.scope = e.scope.wire
    return o
}

private fun toExpenses(rows: Array<dynamic>): List<Expense> = rows.map { toExpense(it) }

private fun toHousehold(o: dynamic): Household = Household(
    id = str(o, "id"),
    name = str(o, "name"),
    memberUids = strList(o, "memberUids"),
    ownerUid = str(o, "ownerUid"),
    roles = strMap(o, "roles"),
    inviteCode = str(o, "inviteCode"),
    createdAt = num(o, "createdAt").toLong(),
    presetVersion = num(o, "presetVersion").toInt()
)

private fun roleFromWire(role: String): HouseholdRole = when (role) {
    "admin" -> HouseholdRole.ADMIN
    "owner" -> HouseholdRole.OWNER
    "member" -> HouseholdRole.MEMBER
    "guest" -> HouseholdRole.GUEST
    else -> HouseholdRole.NONE
}

private fun roleToWire(role: HouseholdRole): String = when (role) {
    HouseholdRole.ADMIN -> "admin"
    HouseholdRole.OWNER -> "owner"
    HouseholdRole.MEMBER -> "member"
    HouseholdRole.GUEST -> "guest"
    HouseholdRole.NONE -> "none"
}

private fun dateRangeFromWire(value: String): DateRangeFilter = when (value) {
    "this_month" -> DateRangeFilter.THIS_MONTH
    "last_month" -> DateRangeFilter.LAST_MONTH
    "this_year" -> DateRangeFilter.THIS_YEAR
    else -> DateRangeFilter.ALL
}

private fun toCriteria(o: dynamic): ExpenseFilterCriteria = ExpenseFilterCriteria(
    searchQuery = str(o, "searchQuery"),
    personFilter = strOrNull(o, "personFilter"),
    categoryFilter = strOrNull(o, "categoryFilter"),
    // Only a selectable method can be filtered on. UNSPECIFIED's wire value is the empty
    // string, which `strOrNull` turns into null — i.e. no filter — matching the mobile sheets,
    // which only offer UPI, Cash and Credit Card.
    paymentMethodFilter = strOrNull(o, "paymentMethodFilter")?.let { PaymentMethod.fromWire(it) },
    dateRange = dateRangeFromWire(str(o, "dateRange"))
)

// ---------------------------------------------------------------------------
// Permissions
//
// `firestore.rules` is the enforcement; these only keep the UI honest. Every capability is
// exposed by name so the React side cannot invent one that has no rules counterpart.
// ---------------------------------------------------------------------------

/** Role of [uid] in [household], as a wire string. [isAdmin] is the `admin` custom claim. */
@JsExport
fun roleOf(household: dynamic, uid: String, isAdmin: Boolean): String =
    roleToWire(Permissions.roleOf(toHousehold(household), uid, isAdmin))

/**
 * Whether [role] has [capability].
 *
 * An unknown capability returns false — a typo in the React layer hides a control rather than
 * exposing one that Firestore would reject anyway.
 */
@JsExport
fun can(capability: String, role: String): Boolean {
    val r = roleFromWire(role)
    return when (capability) {
        "viewHousehold" -> Permissions.canViewHousehold(r)
        "deleteHousehold" -> Permissions.canDeleteHousehold(r)
        "renameHousehold" -> Permissions.canRenameHousehold(r)
        "manageMembers" -> Permissions.canManageMembers(r)
        "manageInviteCode" -> Permissions.canManageInviteCode(r)
        "manageSharedConfig" -> Permissions.canManageSharedConfig(r)
        "addExpense" -> Permissions.canAddExpense(r)
        "readAllExpenses" -> Permissions.canReadAllExpenses(r)
        "leaveHousehold" -> Permissions.canLeaveHousehold(r)
        else -> false
    }
}

/** Whether [role] may edit the expense authored by [expenseAddedBy]. */
@JsExport
fun canEditExpense(role: String, expenseAddedBy: String, uid: String): Boolean {
    // Only `addedBy` is consulted, so a stub carries all the information needed.
    val stub = Expense(
        id = "", householdId = "", amount = 0.0, categoryId = "", categoryName = "",
        date = 0L, notes = "", addedBy = expenseAddedBy, addedByName = "",
        createdAt = 0L, updatedAt = 0L
    )
    return Permissions.canEditExpense(roleFromWire(role), stub, uid)
}

@JsExport
fun roleLabel(role: String): String = Permissions.label(roleFromWire(role))

@JsExport
fun roleDescription(role: String): String = Permissions.description(roleFromWire(role))

/** Rows counting toward the household's shared figures; personal rows are dropped. */
@JsExport
fun sharedOnly(expenses: Array<dynamic>): Array<dynamic> =
    Permissions.sharedOnly(toExpenses(expenses)).map { fromExpense(it) }.toTypedArray()

// ---------------------------------------------------------------------------
// Filtering
// ---------------------------------------------------------------------------

@JsExport
fun filterExpenses(
    expenses: Array<dynamic>,
    criteria: dynamic,
    nowMillis: Double
): Array<dynamic> = ExpenseFilter
    .apply(toExpenses(expenses), toCriteria(criteria), nowMillis.toLong())
    .map { fromExpense(it) }
    .toTypedArray()

private fun optionsOut(options: List<com.bose.expensetracker.ui.state.FilterOption>): Array<dynamic> =
    options.map { option ->
        val o = newObject()
        o.id = option.id
        o.label = option.label
        o
    }.toTypedArray()

@JsExport
fun categoryOptions(expenses: Array<dynamic>): Array<dynamic> =
    optionsOut(ExpenseFilter.categoryOptions(toExpenses(expenses)))

@JsExport
fun personOptions(expenses: Array<dynamic>): Array<dynamic> =
    optionsOut(ExpenseFilter.personOptions(toExpenses(expenses)))

/** The date-range options, in display order, as `{ id, label }`. */
@JsExport
fun dateRangeOptions(): Array<dynamic> = DateRangeFilter.entries.map { range ->
    val o = newObject()
    o.id = when (range) {
        DateRangeFilter.ALL -> "all"
        DateRangeFilter.THIS_MONTH -> "this_month"
        DateRangeFilter.LAST_MONTH -> "last_month"
        DateRangeFilter.THIS_YEAR -> "this_year"
    }
    o.label = range.label
    o
}.toTypedArray()

// ---------------------------------------------------------------------------
// Insights
// ---------------------------------------------------------------------------

@JsExport
fun personSplit(expenses: Array<dynamic>): Array<dynamic> =
    SpendingSplitCalculator.split(toExpenses(expenses)).map { person ->
        val o = newObject()
        o.userId = person.userId
        o.name = person.name
        o.amount = person.amount
        o.share = person.share
        o
    }.toTypedArray()

@JsExport
fun paymentSplit(expenses: Array<dynamic>): Array<dynamic> =
    PaymentMethodSplitCalculator.split(toExpenses(expenses)).map { slice ->
        val o = newObject()
        o.id = slice.id
        o.label = slice.label
        o.amount = slice.amount
        o.share = slice.share
        o
    }.toTypedArray()

// ---------------------------------------------------------------------------
// Money — the Indian lakh/crore grouping, which Intl.NumberFormat does not reproduce
// for the abbreviated forms.
// ---------------------------------------------------------------------------

@JsExport
fun formatMoney(amount: Double): String = formatCurrency(amount)

@JsExport
fun formatMoneyShort(amount: Double): String = formatAmount(amount)

// ---------------------------------------------------------------------------
// Categories and payment methods
// ---------------------------------------------------------------------------

/** The 22-entry preset catalogue, as `{ name, iconKey, color, colorHex }`. */
@JsExport
fun categoryPresets(): Array<dynamic> = CategoryPresets.all.map { preset ->
    val o = newObject()
    o.name = preset.name
    o.iconKey = preset.iconKey
    // Both forms: the ARGB number is what the Firestore document stores (and what the
    // mobile clients read), the hex string is for CSS. Returning only the hex would make a
    // web-seeded category unreadable to Android and iOS.
    o.color = preset.color.toDouble()
    o.colorHex = hexColor(preset.color)
    o
}.toTypedArray()

@JsExport
fun categoryPresetVersion(): Int = CategoryPresets.VERSION

@JsExport
fun categoryPresetId(householdId: String, name: String): String =
    CategoryPresets.idFor(householdId, name)

/**
 * Icon key for a category, from the stored value when it is canonical and the name otherwise.
 *
 * The key→glyph step stays in the web layer, as it does on each platform.
 */
@JsExport
fun categoryIconKey(storedIcon: String?, name: String): String =
    CategoryIcons.resolve(storedIcon, name)

/** ARGB `Long` from the shared palette as a CSS `#rrggbb`; the alpha byte is always opaque. */
private fun hexColor(argb: Long): String {
    val rgb = (argb and 0xFFFFFF).toString(16).padStart(6, '0')
    return "#$rgb"
}

@JsExport
fun categoryColor(argb: Double): String = hexColor(argb.toLong())

/** Selectable payment methods in picker order, as `{ wire, label, emoji }`. */
@JsExport
fun paymentMethods(): Array<dynamic> = PaymentMethod.selectable.map { method ->
    val o = newObject()
    o.wire = method.wire
    o.label = method.label
    o.emoji = method.emoji
    o
}.toTypedArray()

@JsExport
fun paymentMethodLabel(wire: String): String = PaymentMethod.fromWire(wire).label

/** Selectable scopes, as `{ wire, label }`. */
@JsExport
fun expenseScopes(): Array<dynamic> = ExpenseScope.selectable.map { scope ->
    val o = newObject()
    o.wire = scope.wire
    o.label = scope.label
    o
}.toTypedArray()

// ---------------------------------------------------------------------------
// Budgets and goals — computed properties live on the shared models, so the web
// client reads them rather than reimplementing the thresholds.
// ---------------------------------------------------------------------------

@JsExport
fun budgetProgress(spent: Double, monthlyLimit: Double): dynamic {
    val budget = Budget(
        id = "", householdId = "", categoryId = "", categoryName = "",
        monthlyLimit = monthlyLimit, spent = spent
    )
    val o = newObject()
    o.percentage = budget.percentage
    o.status = budget.status.name.lowercase()
    return o
}

@JsExport
fun goalProgress(currentAmount: Double, targetAmount: Double, targetDate: Double?): dynamic {
    val goal = SavingsGoal(
        id = "", householdId = "", name = "",
        targetAmount = targetAmount, currentAmount = currentAmount,
        targetDate = targetDate?.toLong()
    )
    val o = newObject()
    o.progress = goal.progress
    o.remaining = goal.remaining
    o.monthlyNeeded = goal.monthlyNeeded
    return o
}

// ---------------------------------------------------------------------------
// SMS paste-to-parse
//
// The parser is shared and works here; only automatic inbox access is unavailable, exactly as
// on iOS. Pasting a bank alert is the web substitute.
// ---------------------------------------------------------------------------

private val smsParser = SmsTransactionParser()
private val smsCategoryMatcher = SmsCategoryMatcher()

/** Parsed debit as `{ amount, merchant, paymentMethod, categoryName }`, or null. */
@JsExport
fun parseSms(sender: String, body: String, receivedMillis: Double): dynamic {
    val parsed = smsParser.parse(sender, body, receivedMillis.toLong()) ?: return null
    val o = newObject()
    o.amount = parsed.amount
    o.merchant = parsed.merchant
    o.paymentMethod = parsed.paymentMethod.wire
    o.categoryName = smsCategoryMatcher.matchCategory(parsed.merchant, body)
    o.rawMessage = parsed.rawMessage
    return o
}
