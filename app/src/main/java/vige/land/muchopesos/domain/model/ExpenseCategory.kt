package vige.land.muchopesos.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class CategoryGroup(val displayName: String) {
    FOOD("Food"),
    HOUSING("Housing"),
    TRANSPORT("Transport"),
    UTILITIES("Utilities"),
    ENTERTAINMENT("Entertainment"),
    OTHER("Other"),
    SETTLEMENT("Settlement");

    companion object {
        val expenseGroups: List<CategoryGroup> = listOf(
            FOOD,
            HOUSING,
            TRANSPORT,
            UTILITIES,
            ENTERTAINMENT,
            OTHER,
        )

        fun parseOrNull(raw: String?): CategoryGroup? {
            if (raw.isNullOrBlank()) return null
            val trimmed = raw.trim()
            return entries.firstOrNull {
                it.name.equals(trimmed, ignoreCase = true) ||
                    it.displayName.equals(trimmed, ignoreCase = true)
            }
        }
    }
}

@Serializable
data class ExpenseCategory(
    val id: String,
    val name: String,
    val group: CategoryGroup,
    val iconKey: String,
)

object CategoryCatalog {
    // Food (3)
    val GROCERIES = ExpenseCategory(
        id = "groceries",
        name = "Groceries",
        group = CategoryGroup.FOOD,
        iconKey = "shopping_cart",
    )
    val DINING_OUT = ExpenseCategory(
        id = "dining_out",
        name = "Dining Out",
        group = CategoryGroup.FOOD,
        iconKey = "restaurant",
    )
    val COFFEE_SNACKS = ExpenseCategory(
        id = "coffee_snacks",
        name = "Coffee & Snacks",
        group = CategoryGroup.FOOD,
        iconKey = "coffee",
    )

    // Housing (3)
    val RENT_MORTGAGE = ExpenseCategory(
        id = "rent_mortgage",
        name = "Rent / Mortgage",
        group = CategoryGroup.HOUSING,
        iconKey = "home",
    )
    val HOUSEHOLD_SUPPLIES = ExpenseCategory(
        id = "household_supplies",
        name = "Household Supplies",
        group = CategoryGroup.HOUSING,
        iconKey = "cleaning_services",
    )
    val FURNITURE_MAINTENANCE = ExpenseCategory(
        id = "furniture_maintenance",
        name = "Furniture & Maintenance",
        group = CategoryGroup.HOUSING,
        iconKey = "chair",
    )

    // Transport (4)
    val CAR_MAINTENANCE = ExpenseCategory(
        id = "car_maintenance",
        name = "Car & Maintenance",
        group = CategoryGroup.TRANSPORT,
        iconKey = "directions_car",
    )
    val FUEL = ExpenseCategory(
        id = "fuel",
        name = "Fuel",
        group = CategoryGroup.TRANSPORT,
        iconKey = "local_gas_station",
    )
    val PUBLIC_TRANSIT = ExpenseCategory(
        id = "public_transit",
        name = "Public Transit",
        group = CategoryGroup.TRANSPORT,
        iconKey = "directions_transit",
    )
    val PARKING_TOLLS = ExpenseCategory(
        id = "parking_tolls",
        name = "Parking & Tolls",
        group = CategoryGroup.TRANSPORT,
        iconKey = "local_parking",
    )

    // Utilities (3)
    val ELECTRICITY_GAS = ExpenseCategory(
        id = "electricity_gas",
        name = "Electricity & Gas",
        group = CategoryGroup.UTILITIES,
        iconKey = "bolt",
    )
    val WATER_WASTE = ExpenseCategory(
        id = "water_waste",
        name = "Water & Waste",
        group = CategoryGroup.UTILITIES,
        iconKey = "water_drop",
    )
    val INTERNET_PHONE = ExpenseCategory(
        id = "internet_phone",
        name = "Internet & Phone",
        group = CategoryGroup.UTILITIES,
        iconKey = "wifi",
    )

    // Entertainment (3)
    val STREAMING_SUBSCRIPTIONS = ExpenseCategory(
        id = "streaming_subscriptions",
        name = "Streaming & Subscriptions",
        group = CategoryGroup.ENTERTAINMENT,
        iconKey = "subscriptions",
    )
    val MOVIES_EVENTS = ExpenseCategory(
        id = "movies_events",
        name = "Movies & Events",
        group = CategoryGroup.ENTERTAINMENT,
        iconKey = "movie",
    )
    val HOBBIES_LEISURE = ExpenseCategory(
        id = "hobbies_leisure",
        name = "Hobbies & Leisure",
        group = CategoryGroup.ENTERTAINMENT,
        iconKey = "sports_esports",
    )

    // Other (5)
    val HEALTHCARE_PHARMACY = ExpenseCategory(
        id = "healthcare_pharmacy",
        name = "Healthcare & Pharmacy",
        group = CategoryGroup.OTHER,
        iconKey = "medical_services",
    )
    val TRAVEL_VACATION = ExpenseCategory(
        id = "travel_vacation",
        name = "Travel & Vacation",
        group = CategoryGroup.OTHER,
        iconKey = "flight",
    )
    val PETS = ExpenseCategory(
        id = "pets",
        name = "Pets",
        group = CategoryGroup.OTHER,
        iconKey = "pets",
    )
    val GIFTS_DONATIONS = ExpenseCategory(
        id = "gifts_donations",
        name = "Gifts & Donations",
        group = CategoryGroup.OTHER,
        iconKey = "card_giftcard",
    )
    val OTHER = ExpenseCategory(
        id = "other",
        name = "Other",
        group = CategoryGroup.OTHER,
        iconKey = "category",
    )

    // Settlement
    val SETTLEMENT = ExpenseCategory(
        id = "settlement",
        name = "Settlement",
        group = CategoryGroup.SETTLEMENT,
        iconKey = "handshake",
    )

    val expenseCategories: List<ExpenseCategory> = listOf(
        GROCERIES,
        DINING_OUT,
        COFFEE_SNACKS,
        RENT_MORTGAGE,
        HOUSEHOLD_SUPPLIES,
        FURNITURE_MAINTENANCE,
        CAR_MAINTENANCE,
        FUEL,
        PUBLIC_TRANSIT,
        PARKING_TOLLS,
        ELECTRICITY_GAS,
        WATER_WASTE,
        INTERNET_PHONE,
        STREAMING_SUBSCRIPTIONS,
        MOVIES_EVENTS,
        HOBBIES_LEISURE,
        HEALTHCARE_PHARMACY,
        TRAVEL_VACATION,
        PETS,
        GIFTS_DONATIONS,
        OTHER,
    )

    val allCategories: List<ExpenseCategory> = expenseCategories + SETTLEMENT

    val groupedExpenseCategories: Map<CategoryGroup, List<ExpenseCategory>> =
        linkedMapOf<CategoryGroup, List<ExpenseCategory>>().apply {
            for (group in CategoryGroup.expenseGroups) {
                put(group, expenseCategories.filter { it.group == group })
            }
        }

    val defaultCategory: ExpenseCategory = OTHER

    private val byNormalizedKey: Map<String, ExpenseCategory> = buildMap {
        for (cat in allCategories) {
            put(normalizeKey(cat.id), cat)
            put(normalizeKey(cat.name), cat)
        }
        // Resilient short aliases for manually edited sheet cells
        put("dining", DINING_OUT)
        put("coffee", COFFEE_SNACKS)
        put("snacks", COFFEE_SNACKS)
        put("rent", RENT_MORTGAGE)
        put("mortgage", RENT_MORTGAGE)
        put("supplies", HOUSEHOLD_SUPPLIES)
        put("furniture", FURNITURE_MAINTENANCE)
        put("car", CAR_MAINTENANCE)
        put("gas", FUEL)
        put("transit", PUBLIC_TRANSIT)
        put("parking", PARKING_TOLLS)
        put("tolls", PARKING_TOLLS)
        put("electricity", ELECTRICITY_GAS)
        put("electric", ELECTRICITY_GAS)
        put("water", WATER_WASTE)
        put("internet", INTERNET_PHONE)
        put("phone", INTERNET_PHONE)
        put("streaming", STREAMING_SUBSCRIPTIONS)
        put("subscriptions", STREAMING_SUBSCRIPTIONS)
        put("movies", MOVIES_EVENTS)
        put("events", MOVIES_EVENTS)
        put("hobbies", HOBBIES_LEISURE)
        put("healthcare", HEALTHCARE_PHARMACY)
        put("pharmacy", HEALTHCARE_PHARMACY)
        put("travel", TRAVEL_VACATION)
        put("vacation", TRAVEL_VACATION)
        put("gifts", GIFTS_DONATIONS)
        put("donations", GIFTS_DONATIONS)
    }

    /**
     * Resolves a category by ID or display name (case-insensitive), falling back gracefully
     * if a spreadsheet row was manually edited with a custom or blank category label.
     */
    fun resolve(categoryNameOrId: String?, groupName: String? = null): ExpenseCategory {
        val parsedGroup = CategoryGroup.parseOrNull(groupName)
        if (parsedGroup == CategoryGroup.SETTLEMENT && categoryNameOrId.isNullOrBlank()) {
            return SETTLEMENT
        }

        val trimmed = categoryNameOrId?.trim().orEmpty()
        if (trimmed.isNotEmpty()) {
            val direct = byNormalizedKey[normalizeKey(trimmed)]
            if (direct != null) return direct

            val resolvedGroup = parsedGroup ?: CategoryGroup.OTHER
            if (resolvedGroup == CategoryGroup.SETTLEMENT) return SETTLEMENT
            val customSlug = trimmed.lowercase()
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .ifEmpty { "custom" }
            return ExpenseCategory(
                id = customSlug,
                name = trimmed,
                group = resolvedGroup,
                iconKey = fallbackIconFor(resolvedGroup),
            )
        }

        return when (parsedGroup) {
            CategoryGroup.SETTLEMENT -> SETTLEMENT
            null -> defaultCategory
            else -> groupedExpenseCategories[parsedGroup]?.firstOrNull() ?: defaultCategory
        }
    }

    private fun normalizeKey(raw: String): String =
        raw.trim().lowercase().replace(Regex("[^a-z0-9]"), "")

    private fun fallbackIconFor(group: CategoryGroup): String = when (group) {
        CategoryGroup.FOOD -> "restaurant"
        CategoryGroup.HOUSING -> "home"
        CategoryGroup.TRANSPORT -> "directions_car"
        CategoryGroup.UTILITIES -> "bolt"
        CategoryGroup.ENTERTAINMENT -> "movie"
        CategoryGroup.OTHER -> "category"
        CategoryGroup.SETTLEMENT -> "handshake"
    }
}
