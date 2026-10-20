package saarlacarte.restaurant

import saarlacarte.enums.RestaurantType
import saarlacarte.food.Ingredient
import saarlacarte.food.Recipe
import saarlacarte.simulation.Supplier

/** A restaurant's catalog, pantry, and currently orderable dishes. */
data class Menu(val supplier: Supplier, val recipes: List<Recipe>, val type: RestaurantType) {
    val pantry: Pantry = Pantry(supplier)
    private val cookEligibleMenu: MutableList<Recipe> = recipes.toMutableList()
    private val availableMenu: MutableList<Recipe> = mutableListOf()

    /**
     * Stores cook ∩ catalog, then intersects with pantry stock.
     */
    fun setAvailableMenu(dishes: List<Recipe>) {
        cookEligibleMenu.clear()
        cookEligibleMenu.addAll(dishes)
        filterAvailableMenu()
    }

    /**
     * Recomputes [availableMenu] as cook-eligible dishes ∩ current pantry supplies.
     */
    fun filterAvailableMenu(): MutableList<Recipe> {
        val makeable = cookEligibleMenu.filter { pantryCanMake(it) }
        availableMenu.clear()
        availableMenu.addAll(makeable)
        return availableMenu.toMutableList()
    }

    /**
     * Reserves pantry stock for [dishes] (recipe to portion count).
     *
     * @return false if any ingredient is short; pantry is then unchanged
     */
    fun reserveDishes(dishes: Map<Recipe, Int>): Boolean {
        return pantry.reserve(ingredientTotals(dishes))
    }

    /** Last cook ∩ pantry snapshot. */
    fun getAvailableMenu(): List<Recipe> = availableMenu.toList()

    private fun pantryCanMake(recipe: Recipe): Boolean {
        return recipe.ingredients.all { (ingredient, amount) ->
            amount <= pantry.availableAmount(ingredient)
        }
    }

    private fun ingredientTotals(dishes: Map<Recipe, Int>): Map<Ingredient, Int> {
        val needed = mutableMapOf<Ingredient, Int>()
        for ((recipe, count) in dishes) {
            if (count <= 0) {
                continue
            }
            for ((ingredient, amount) in recipe.ingredients) {
                needed[ingredient] = (needed[ingredient] ?: 0) + amount * count
            }
        }
        return needed
    }
}
