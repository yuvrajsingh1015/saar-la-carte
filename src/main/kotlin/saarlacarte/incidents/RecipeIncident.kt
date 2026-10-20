package saarlacarte.incidents

import saarlacarte.food.Recipe
import kotlin.math.max

const val PERCENTAGE = 100.0

/**
 Incident changing an ingredient amount across recipes
 **/
class RecipeIncident(
    id: Int,
    evening: Int,
    private val allRecipes: List<Recipe>,
    private val ingredientName: String,
    private val adaptation: Int
) : Incident(id, evening) {

    /**
     getter ingredient name
     **/
    fun getIngredientName() = ingredientName

    /**
     Applies the recipe change
     **/
    override fun execute() {
        allRecipes.forEach { recipe ->
            // find the pair (ingredient to amount) to replace if exists
            val entry = recipe.ingredients.entries.firstOrNull { it.key.getName() == ingredientName }
            if (entry != null) {
                // if recipe has this ingredient
                val newAmount = max((entry.value * (PERCENTAGE + adaptation) / PERCENTAGE).toInt(), 1)
                recipe.ingredients.remove(entry.key)
                recipe.ingredients[entry.key] = newAmount
            }
        }
    }
}
