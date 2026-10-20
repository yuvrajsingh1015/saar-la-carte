package saarlacarte.parser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import saarlacarte.enums.CookType
import saarlacarte.enums.MeasuringUnit
import saarlacarte.enums.RestaurantType
import saarlacarte.food.Ingredient
import saarlacarte.food.Recipe
import saarlacarte.simulation.Supplier
import java.io.File

private const val SCHEMA = "classpath://schema/food.schema"

/**
 * parser for parsing ingredients and recipes
 */
class FoodParser {

    /** general foodParser */
    fun parse(filePath: String): Pair<Supplier, List<Recipe>>? {
        val fileContent = File(filePath).readText()
        if (!SchemaCheck.fits(fileContent, SCHEMA)) {
            return null
        }
        val rootJson = Json.parseToJsonElement(fileContent).jsonObject

        val (ingredients, packagingVolumes) = parseIngredientsWithVolumes(rootJson) ?: return null
        val recipes = parseRecipe(rootJson, ingredients)

        if (recipes == null ||
            !validateIngredient(ingredients)
        ) {
            return null
        }
        if (!validateRecipe(recipes) ||
            !crossValidateFood(recipes, ingredients)
        ) {
            return null
        }

        return Pair(Supplier(ingredients, packagingVolumes), recipes)
    }

    private fun parseIngredientItem(obj: JsonObject): Pair<Ingredient, Int>? {
        val name = obj["name"]?.jsonPrimitive?.content
        val unitStr = obj["unit"]?.jsonPrimitive?.content
        val packagingVolume = obj["packagingVolume"]?.jsonPrimitive?.int
        val bestBefore = obj["bestBefore"]?.jsonPrimitive?.int

        if (name == null || unitStr == null) {
            return null
        }
        if (packagingVolume == null || bestBefore == null) {
            return null
        }

        val unit = when (unitStr) {
            "g" -> MeasuringUnit.G
            "mL" -> MeasuringUnit.ML
            "X" -> MeasuringUnit.X
            else -> throw IllegalArgumentException("Invalid measuring unit: $unitStr")
        }

        return Pair(Ingredient(name, unit, bestBefore), packagingVolume)
    }

    private fun parseIngredientsWithVolumes(json: JsonObject): Pair<List<Ingredient>, MutableMap<String, Int>>? {
        val ingredientArray = json["ingredients"]?.jsonArray ?: return null
        val ingredients = mutableListOf<Ingredient>()
        val packagingVolumes = mutableMapOf<String, Int>()

        for (element in ingredientArray) {
            val (ingredient, volume) = parseIngredientItem(element.jsonObject) ?: return null
            ingredients.add(ingredient)
            packagingVolumes[ingredient.getName()] = volume
        }

        return Pair(ingredients, packagingVolumes)
    }

    private fun validateIngredient(ingredients: List<Ingredient>): Boolean {
        return ingredients.isNotEmpty() && ingredients.map { it.getName() }.toSet().size == ingredients.size
    }

    private fun parseRecipeIngredients(
        jsonArray: JsonArray?,
        ingredientMap: Map<String, Ingredient>
    ): MutableMap<Ingredient, Int>? {
        val array = jsonArray ?: return null
        val recipeIngredients = mutableMapOf<Ingredient, Int>()

        for (ingElem in array) {
            val ingObj = ingElem.jsonObject
            val ingName = ingObj["name"]?.jsonPrimitive?.content
            val amount = ingObj["amount"]?.jsonPrimitive?.int
            val ingredient = ingName?.let { ingredientMap[it] }

            if (ingredient == null || amount == null) {
                return null
            }
            recipeIngredients[ingredient] = amount
        }

        return recipeIngredients
    }

    private fun parseSingleRecipe(
        obj: JsonObject,
        ingredientMap: Map<String, Ingredient>
    ): Recipe? {
        val id = obj["id"]?.jsonPrimitive?.int
        val dishName = obj["dishName"]?.jsonPrimitive?.content
        val duration = obj["duration"]?.jsonPrimitive?.int
        val recipeIngredients = parseRecipeIngredients(obj["ingredients"]?.jsonArray, ingredientMap)

        if (id == null || dishName == null) {
            return null
        }
        if (duration == null || recipeIngredients == null) {
            return null
        }

        val cookTypes = obj["cookType"]?.jsonArray?.map {
            CookType.valueOf(it.jsonPrimitive.content.uppercase())
        } ?: emptyList()

        val basicDishForStr = obj["basicDishFor"]?.jsonPrimitive?.content
        val basicDishFor = basicDishForStr?.let { RestaurantType.valueOf(it.uppercase()) }

        return Recipe(
            id = id,
            dishName = dishName,
            duration = duration,
            ingredients = recipeIngredients,
            eligibleCookTypes = cookTypes,
            basicDishFor = basicDishFor
        )
    }

    private fun parseRecipe(json: JsonObject, ingredients: List<Ingredient>): List<Recipe>? {
        val recipeArray = json["recipes"]?.jsonArray ?: return emptyList()
        val ingredientMap = ingredients.associateBy { it.getName() }
        val recipes = mutableListOf<Recipe>()

        for (element in recipeArray) {
            val recipe = parseSingleRecipe(element.jsonObject, ingredientMap) ?: return null
            recipes.add(recipe)
        }

        return recipes
    }

    private fun validateRecipe(recipes: List<Recipe>): Boolean {
        if (recipes.isEmpty()) {
            return false
        }
        if (recipes.map { it.id }.toSet().size != recipes.size) {
            return false
        }
        val basicDishes = recipes.filter { it.basicDishFor != null }
        return basicDishes.map { it.dishName }.toSet().size == basicDishes.size
    }

    private fun crossValidateFood(recipes: List<Recipe>, ingredients: List<Ingredient>): Boolean {
        val ingredientNames = ingredients.map { it.getName() }.toSet()
        return recipes.all { recipe ->
            recipe.ingredients.keys.all { it.getName() in ingredientNames }
        }
    }
}
