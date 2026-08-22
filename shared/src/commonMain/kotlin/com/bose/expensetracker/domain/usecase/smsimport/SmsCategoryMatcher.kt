package com.bose.expensetracker.domain.usecase.smsimport

class SmsCategoryMatcher {

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
            "parking" to "Transport",
            "auto" to "Transport",
            "share auto" to "Transport",

            // Fuel — its own category now rather than folded into Transport. Existing expenses
            // keep saying Transport: categoryName is denormalised onto each row, so history is
            // not rewritten by a catalogue change.
            "petrol" to "Fuel",
            "diesel" to "Fuel",
            "fuel" to "Fuel",
            "indian oil" to "Fuel",
            "iocl" to "Fuel",
            "hpcl" to "Fuel",
            "bharat petroleum" to "Fuel",
            "bpcl" to "Fuel",
            "nayara" to "Fuel",

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

            // Milk — Aavin is the Tamil Nadu co-operative; delivery is a daily recurring cost.
            "aavin" to "Milk",
            "milk" to "Milk",
            "heritage" to "Milk",
            "arokya" to "Milk",

            // Electricity. TNEB is what a Tamil Nadu bill actually says.
            "tneb" to "Electricity",
            "tangedco" to "Electricity",
            "electricity board" to "Electricity",
            "electricity" to "Electricity",
            "bescom" to "Electricity",
            "tata power" to "Electricity",
            "current bill" to "Electricity",

            // Gas cylinder
            "indane" to "Gas Cylinder",
            "bharat gas" to "Gas Cylinder",
            "hp gas" to "Gas Cylinder",
            "gas cylinder" to "Gas Cylinder",
            "lpg" to "Gas Cylinder",
            "gas booking" to "Gas Cylinder",

            // Mobile & Internet
            "airtel" to "Mobile & Internet",
            "jio" to "Mobile & Internet",
            "vodafone" to "Mobile & Internet",
            "vi " to "Mobile & Internet",
            "bsnl" to "Mobile & Internet",
            "act fibernet" to "Mobile & Internet",
            "hathway" to "Mobile & Internet",
            "recharge" to "Mobile & Internet",
            "broadband" to "Mobile & Internet",
            "dth" to "Mobile & Internet",

            // Medical
            "apollo" to "Medical",
            "medplus" to "Medical",
            "pharmeasy" to "Medical",
            "netmeds" to "Medical",
            "pharmacy" to "Medical",
            "hospital" to "Medical",
            "clinic" to "Medical",
            "diagnostics" to "Medical",

            // Bills — whatever is left over
            "water bill" to "Bills",
            "gas bill" to "Bills",
            "1mg" to "Medical",
            "medical" to "Medical",
            "doctor" to "Medical",

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

        val sortedEntries = MERCHANT_CATEGORY_MAP.entries.sortedByDescending { it.key.length }
        for ((keyword, category) in sortedEntries) {
            if (searchText.contains(keyword)) {
                return category
            }
        }

        return DEFAULT_CATEGORY
    }
}
