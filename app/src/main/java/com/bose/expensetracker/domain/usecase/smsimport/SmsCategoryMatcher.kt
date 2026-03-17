package com.bose.expensetracker.domain.usecase.smsimport

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsCategoryMatcher @Inject constructor() {

    companion object {
        private val MERCHANT_CATEGORY_MAP: Map<String, String> = mapOf(
            // Food & Dining
            "swiggy" to "Food",
            "zomato" to "Food",
            "uber eats" to "Food",
            "dominos" to "Food",
            "mcdonalds" to "Food",
            "kfc" to "Food",
            "pizza hut" to "Food",
            "starbucks" to "Food",
            "restaurant" to "Food",
            "cafe" to "Food",
            "food" to "Food",

            // Shopping
            "amazon" to "Shopping",
            "flipkart" to "Shopping",
            "myntra" to "Shopping",
            "ajio" to "Shopping",
            "meesho" to "Shopping",
            "nykaa" to "Shopping",
            "snapdeal" to "Shopping",
            "shopping" to "Shopping",
            "mall" to "Shopping",

            // Transport
            "uber" to "Transport",
            "ola" to "Transport",
            "rapido" to "Transport",
            "irctc" to "Transport",
            "makemytrip" to "Transport",
            "goibibo" to "Transport",
            "redbus" to "Transport",
            "petrol" to "Transport",
            "fuel" to "Transport",
            "parking" to "Transport",

            // Entertainment
            "netflix" to "Entertainment",
            "hotstar" to "Entertainment",
            "spotify" to "Entertainment",
            "bookmyshow" to "Entertainment",
            "prime video" to "Entertainment",
            "youtube" to "Entertainment",
            "disney" to "Entertainment",
            "inox" to "Entertainment",
            "pvr" to "Entertainment",

            // Groceries
            "bigbasket" to "Groceries",
            "blinkit" to "Groceries",
            "zepto" to "Groceries",
            "jiomart" to "Groceries",
            "dmart" to "Groceries",
            "swiggy instamart" to "Groceries",
            "grofers" to "Groceries",
            "grocery" to "Groceries",
            "supermarket" to "Groceries",

            // Bills & Utilities
            "airtel" to "Bills",
            "jio" to "Bills",
            "vodafone" to "Bills",
            "vi " to "Bills",
            "bescom" to "Bills",
            "electricity" to "Bills",
            "water bill" to "Bills",
            "gas bill" to "Bills",
            "broadband" to "Bills",
            "tata power" to "Bills",
            "bsnl" to "Bills",

            // Health
            "apollo" to "Health",
            "pharmeasy" to "Health",
            "1mg" to "Health",
            "netmeds" to "Health",
            "hospital" to "Health",
            "clinic" to "Health",
            "pharmacy" to "Health",
            "medical" to "Health",
            "doctor" to "Health",

            // Education
            "udemy" to "Education",
            "coursera" to "Education",
            "school" to "Education",
            "college" to "Education",
            "tuition" to "Education",
            "books" to "Education"
        )

        const val DEFAULT_CATEGORY = "Misc"
    }

    fun matchCategory(merchant: String?, smsBody: String): String {
        val searchText = "${merchant.orEmpty()} $smsBody".lowercase()

        // Check longer merchant names first for more specific matches
        val sortedEntries = MERCHANT_CATEGORY_MAP.entries.sortedByDescending { it.key.length }
        for ((keyword, category) in sortedEntries) {
            if (searchText.contains(keyword)) {
                return category
            }
        }

        return DEFAULT_CATEGORY
    }
}
