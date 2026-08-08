package com.bose.expensetracker.util

/**
 * The one place that decides which icon a category gets.
 *
 * Categories used to carry three competing icon systems: Android seeded Material Symbol names
 * into `Category.icon` ("restaurant"), iOS seeded emoji into the same Firestore field ("🍔"),
 * and both UIs ignored the field entirely in favour of a name→emoji lookup — one per platform,
 * disagreeing with each other. Emoji cannot be reconciled anyway: they are drawn by the
 * platform font, so the same code renders different art on each OS, and they can't be tinted
 * to follow the theme.
 *
 * The canonical key is the **Material Symbol name**, because Android already seeds exactly
 * those strings, so existing Android-seeded households need no migration. Each platform maps
 * the key to its own native icon set — Material Symbols on Android, SF Symbols on iOS.
 */
object CategoryIcons {

    /** Used when nothing matches, and for the "Misc"/"Other" categories themselves. */
    const val FALLBACK = "more_horiz"

    /**
     * Every key a platform resolver is expected to handle. Keeping this explicit means an
     * unknown value in Firestore is treated as legacy data rather than trusted blindly.
     */
    val keys: Set<String> = setOf(
        "restaurant", "shopping_cart", "directions_car", "home", "receipt_long",
        "family_restroom", "movie", "shopping_bag", "medical_services", "school",
        "flight", "shield", "card_giftcard", "fitness_center", "payments",
        "trending_up", "autorenew", FALLBACK
    )

    /**
     * Exact category-name matches, checked first. Lowercased keys.
     *
     * Order matters only for [keywords] below; this map is unambiguous.
     */
    private val exactNames: Map<String, String> = mapOf(
        "food" to "restaurant",
        "food & dining" to "restaurant",
        "dining" to "restaurant",
        "restaurant" to "restaurant",
        "groceries" to "shopping_cart",
        "grocery" to "shopping_cart",
        "transport" to "directions_car",
        "transportation" to "directions_car",
        "cab" to "directions_car",
        "fuel" to "directions_car",
        "rent" to "home",
        "housing" to "home",
        "home" to "home",
        "bills" to "receipt_long",
        "utilities" to "receipt_long",
        "electricity" to "receipt_long",
        "water" to "receipt_long",
        "internet" to "receipt_long",
        "family" to "family_restroom",
        "kids" to "family_restroom",
        "entertainment" to "movie",
        "movies" to "movie",
        "games" to "movie",
        "gaming" to "movie",
        "shopping" to "shopping_bag",
        "clothing" to "shopping_bag",
        "fashion" to "shopping_bag",
        "health" to "medical_services",
        "medical" to "medical_services",
        "pharmacy" to "medical_services",
        "hospital" to "medical_services",
        "education" to "school",
        "books" to "school",
        "courses" to "school",
        "travel" to "flight",
        "insurance" to "shield",
        "gifts" to "card_giftcard",
        "gift" to "card_giftcard",
        "fitness" to "fitness_center",
        "gym" to "fitness_center",
        "income" to "payments",
        "salary" to "payments",
        "freelance" to "payments",
        "savings" to "trending_up",
        "investment" to "trending_up",
        "subscription" to "autorenew",
        "misc" to FALLBACK,
        "other" to FALLBACK
    )

    /**
     * Substring fallbacks for compound names the exact map can't cover.
     *
     * This is what "Rent/Home Loan" needed — it is a seeded preset on Android, yet matched
     * none of the exact keys, so it fell through to the generic icon on every screen.
     * Checked in order, so put the more specific keyword first where two could both match.
     */
    private val keywords: List<Pair<String, String>> = listOf(
        "grocer" to "shopping_cart",
        "rent" to "home",
        "home" to "home",
        "house" to "home",
        "food" to "restaurant",
        "dining" to "restaurant",
        "transport" to "directions_car",
        "travel" to "flight",
        "flight" to "flight",
        "bill" to "receipt_long",
        "utilit" to "receipt_long",
        "family" to "family_restroom",
        "entertain" to "movie",
        "shop" to "shopping_bag",
        "health" to "medical_services",
        "medic" to "medical_services",
        "educat" to "school",
        "school" to "school",
        "insur" to "shield",
        "gift" to "card_giftcard",
        "fitness" to "fitness_center",
        "salary" to "payments",
        "income" to "payments",
        "invest" to "trending_up",
        "saving" to "trending_up",
        "subscri" to "autorenew"
    )

    /** Icon key for a category [name], falling back to [FALLBACK] when nothing matches. */
    fun keyForName(name: String): String {
        val normalised = name.trim().lowercase()
        if (normalised.isEmpty()) return FALLBACK

        exactNames[normalised]?.let { return it }
        keywords.firstOrNull { (keyword, _) -> normalised.contains(keyword) }
            ?.let { (_, key) -> return key }
        return FALLBACK
    }

    /**
     * Icon key for a stored [icon] value, falling back to the category [name].
     *
     * [icon] is trusted only when it is already a canonical key. Anything else — an emoji from
     * an iOS-seeded household, a blank, a name from an older build — is ignored in favour of
     * deriving from the name, so mixed data across the two platforms still resolves.
     */
    fun resolve(icon: String?, name: String): String =
        if (icon != null && icon in keys) icon else keyForName(name)
}
