package com.bose.expensetracker.domain.model

/** One entry in the seeded category catalogue. */
data class CategoryPreset(
    val name: String,
    /** A [com.bose.expensetracker.util.CategoryIcons] key, not a platform icon name. */
    val iconKey: String,
    val color: Long
)

/**
 * The categories a household is seeded with.
 *
 * Lives in `shared` because the two platforms previously kept their own hardcoded lists and had
 * drifted badly: Android seeded 8 and iOS 14, Android called it "Rent/Home Loan" where iOS said
 * "Rent", Android gave every category its own colour where iOS gave them all one purple, and the
 * two derived different document ids — so a household seeded by both ended up with duplicate
 * documents that iOS then hid by deduplicating on name at read time. One list is what stops that
 * happening again.
 *
 * The set leans Indian, and Tamil Nadu specifically: Aavin milk, the TNEB bill, a gas cylinder
 * and 20-litre water cans are recurring household costs here, and lumping them into "Bills" is
 * what pushed a third of one real household's expenses into "Misc".
 */
object CategoryPresets {

    /**
     * Bumped whenever entries are added.
     *
     * Households store the version they were last seeded at, so a top-up runs once per bump.
     * Without it, seeding by name alone would resurrect any preset the owner deleted on every
     * app open — seeding fighting the user, which is worse than a missing category.
     */
    const val VERSION = 2

    /**
     * Document id for a preset within a household.
     *
     * Scoped by household because that is the scheme Android already wrote, and changing it
     * would orphan every existing preset document into a duplicate.
     */
    fun idFor(householdId: String, name: String): String = "preset_${householdId}_${slug(name)}"

    fun slug(name: String): String = name
        .lowercase()
        .replace("&", "and")
        .replace("/", "_")
        .replace(" ", "_")

    /**
     * The catalogue.
     *
     * The first fifteen are the union of what the two platforms used to seed, so no existing iOS
     * household loses a category. Where the names disagreed, the one already present in real data
     * wins: "Rent/Home Loan" over iOS's "Rent", and "Medical" over iOS's "Health".
     */
    val all: List<CategoryPreset> = listOf(
        // --- Everyday ---
        CategoryPreset("Food", "restaurant", 0xFF4CAF50),
        CategoryPreset("Groceries", "shopping_cart", 0xFF8BC34A),
        CategoryPreset("Vegetables", "eco", 0xFF7CB342),
        CategoryPreset("Milk", "local_drink", 0xFF90CAF9),
        CategoryPreset("Water Can", "water_drop", 0xFF4FC3F7),

        // --- Getting around ---
        CategoryPreset("Transport", "directions_car", 0xFF2196F3),
        CategoryPreset("Fuel", "local_gas_station", 0xFF1565C0),
        CategoryPreset("Travel", "flight", 0xFF00ACC1),

        // --- Home and utilities ---
        CategoryPreset("Rent/Home Loan", "home", 0xFFFF9800),
        CategoryPreset("Electricity", "bolt", 0xFFFDD835),
        CategoryPreset("Gas Cylinder", "propane_tank", 0xFFEF6C00),
        CategoryPreset("Mobile & Internet", "wifi", 0xFF26A69A),
        CategoryPreset("Bills", "receipt_long", 0xFFF44336),

        // --- People and health ---
        CategoryPreset("Family", "family_restroom", 0xFFE91E63),
        CategoryPreset("Medical", "medical_services", 0xFFEF5350),
        CategoryPreset("Education", "school", 0xFF5C6BC0),
        CategoryPreset("Fitness", "fitness_center", 0xFF66BB6A),

        // --- Discretionary ---
        CategoryPreset("Shopping", "shopping_bag", 0xFFAB47BC),
        CategoryPreset("Entertainment", "movie", 0xFF9C27B0),
        CategoryPreset("Gifts", "card_giftcard", 0xFFEC407A),

        // --- Commitments ---
        CategoryPreset("Insurance", "shield", 0xFF546E7A),

        // Keep last: the fallback bucket.
        CategoryPreset("Misc", "more_horiz", 0xFF607D8B)
    )
}
