package saarlacarte.restaurant

import saarlacarte.enums.CookType
import saarlacarte.enums.DishStatus
import saarlacarte.food.Recipe
import saarlacarte.loggers.CookLogger
import saarlacarte.orders.Order
import saarlacarte.simulation.Ticks

private const val TICK_LENGTH = 10

/**
A cook working in a restaurant's kitchen
 **/
class Cook(private val type: CookType) {
    private var id: Int? = null
    private var currentRecipe: Recipe? = null
    private var mealsNumber: Int = 0
    private var ordersBeingCooked: List<Order> = emptyList()
    private var logger: CookLogger? = null
    var ticksRemaining: Int = 0

    /** the kind of cook this is **/
    fun getType(): CookType = type

    /** null until this cook has been bound to its first job this evening **/
    fun getId(): Int? = id

    /** checks if a cook has a logger **/
    fun hasLogger(): Boolean = logger != null

    /**get meals currently being cooked**/
    fun getMealsNumber(): Int = mealsNumber

    /** removing an order from the list of orders**/
    fun clearOrder(order: Order) {
        if (isFree() || order !in ordersBeingCooked) {
            return
        }
        val recipe = currentRecipe ?: return
        val count = order.getDishes().count { dish ->
            dish.getRecipe().dishName == recipe.dishName
        }
        mealsNumber -= count
        ordersBeingCooked = ordersBeingCooked.filter { it != order }
        if (mealsNumber <= 0) {
            clearJob()
        }
    }

    /** sets the logger used to report this cook's activity **/
    fun setLogger(cookLogger: CookLogger) {
        logger = cookLogger
    }

    /** Assigns this cook's id for the evening **/
    fun assignId(newId: Int) {
        id = newId
    }

    /** A cook is free exactly when it isn't currently bound to a recipe **/
    fun isFree(): Boolean = currentRecipe == null

    /** Filters [recipes] down to the ones this cook's type is eligible to cook **/
    fun canCook(recipes: List<Recipe>): List<Recipe> {
        val eligibleRecipes = mutableListOf<Recipe>()
        for (recipe in recipes) {
            if (recipe.eligibleCookTypes.contains(type)) {
                eligibleRecipes.add(recipe)
            }
        }
        return eligibleRecipes
    }

    /**
     * Binds this cook to [recipe] for the rest of the evening: marks every NOTSTARTED
     * dish of this recipe within [orders] as INPROGRESS, and records how many meals
     * ([mealsNumber]) this job covers.
     */
    fun assignJob(recipe: Recipe, orders: List<Order>, mealsNumber: Int) {
        currentRecipe = recipe
        this.mealsNumber = mealsNumber
        ordersBeingCooked = orders
        // ceil(duration / TICK_LENGTH)
        ticksRemaining = (recipe.duration + TICK_LENGTH - 1) / TICK_LENGTH - 1
        for (order in orders) {
            order.setDishStatus(recipe, DishStatus.INPROGRESS)
        }
        // logger parameters
        val orderIds = orders.map { it.getId() }
        val dishName = recipe.dishName
        logger?.cookStartCooking(
            id ?: 0,
            this.type.toString(),
            mealsNumber,
            dishName,
            orderIds.min(),
            orderIds.distinct().sorted().joinToString(",")
        )
    }

    /**cooking meals and reducing the ticksRemaining or setting status to finished**/
    fun cooks() {
        if (isFree()) {
            return
        }
        if (ticksRemaining == 0) {
            val recipe = currentRecipe ?: return
            for (order in ordersBeingCooked) {
                order.setDishStatus(recipe, DishStatus.FINISHED)
            }
            val first = ordersBeingCooked.firstOrNull()
            if (first != null) {
                logger?.mealCooked(id ?: 0, mealsNumber, recipe.dishName, Ticks.getTick() - first.getOrderedTick())
            }
            clearJob()
        } else {
            ticksRemaining--
        }
    }

    /** Frees the cook and drops this evening's id. */
    fun resetForNextEvening() {
        clearJob()
        id = null
    }

    private fun clearJob() {
        currentRecipe = null
        ticksRemaining = 0
        mealsNumber = 0
        ordersBeingCooked = emptyList()
    }

    /**get the current Recipe being cooked**/
    fun getCurrentRecipe(): Recipe? = currentRecipe
}
