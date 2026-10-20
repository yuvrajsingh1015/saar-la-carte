package saarlacarte.orders

import saarlacarte.enums.DishStatus
import saarlacarte.food.Recipe

/**
A single cooked or cooking dish within an order
 **/
class Dish(private val recipe: Recipe, private var status: DishStatus, private val isBasic: Boolean = false) {
    private var finishedTick: Int = 0

    /** the recipe this dish is cooked from **/
    fun getRecipe(): Recipe = recipe

    /** the current status of this dish **/
    fun getStatus(): DishStatus = status

    /** Tick when this dish finished cooking. */
    fun getFinishedTick(): Int = finishedTick

    /** Tick when this dish finished cooking */
    fun setFinishedTick(tick: Int) {
        finishedTick = tick
    }

    /** Updates the status of this dish. */
    fun setStatus(newStatus: DishStatus) {
        status = newStatus
    }

    /** whether this dish is the basic dish no matter restaurant type **/
    fun isBasic(): Boolean = isBasic
}
