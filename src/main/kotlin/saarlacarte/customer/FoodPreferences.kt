package saarlacarte.customer

/**
Food preferences of a subgroup within a customer group
 **/
data class FoodPreferences(
    val size: Int,
    val excludedIngredients: List<String>? = emptyList(),
    val preferredIngredients: List<String>? = emptyList(),
    val favoriteDishes: List<String>? = emptyList()
)
