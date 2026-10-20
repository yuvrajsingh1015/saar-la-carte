package saarlacarte.food

import saarlacarte.enums.CookType
import saarlacarte.enums.MeasuringUnit
import saarlacarte.enums.RestaurantType

/**
An ingredient used in recipes
 **/
data class Ingredient(
    private val name: String,
    private val unit: MeasuringUnit,
    private val bestBefore: Int
) {
    /** Getter for Ingredient name **/
    fun getName(): String {
        return name
    }

    /** Getter for Ingredient measuring unit **/
    fun getUnit(): MeasuringUnit {
        return unit
    }

    /** Getter for Ingredient measuring unit as a string **/
    fun getUnitString(): String {
        return when (unit) {
            MeasuringUnit.X -> "X"
            MeasuringUnit.G -> "g"
            MeasuringUnit.ML -> "mL"
        }
    }

    /** Getter for Ingredient bestBefore evening **/
    fun getBestBefore(): Int {
        return bestBefore
    }
}

/**
A recipe describing how to cook a dish
 **/
data class Recipe(
    val id: Int,
    val dishName: String,
    val duration: Int,
    val ingredients: MutableMap<Ingredient, Int>,
    val eligibleCookTypes: List<CookType>,
    val basicDishFor: RestaurantType? = null
)

/**
A package of the amount of an ingredient bought in an evening
 **/
data class Package(
    val ingredient: Ingredient,
    var amount: Int,
    val expirationDate: Int
)
