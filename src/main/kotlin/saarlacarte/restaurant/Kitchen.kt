package saarlacarte.restaurant

import saarlacarte.customer.CustomerGroup
import saarlacarte.customer.EventCustomerGroup
import saarlacarte.customer.RegularCustomerGroup
import saarlacarte.enums.DishStatus
import saarlacarte.food.Ingredient
import saarlacarte.food.Recipe
import saarlacarte.loggers.CookLogger
import saarlacarte.loggers.KitchenLogger
import saarlacarte.orders.Order

private const val FOOD_ESTIMATE_FREE_SEATS_CONSTANT = 10

/**
A restaurant's kitchen
 **/

class Kitchen(val cooks: MutableList<Cook>, val menu: Menu) {
    private val orders: MutableList<Order> = mutableListOf()
    private var logger: KitchenLogger? = null
    private var cookLoggers: CookLogger? = null

    /** Assign kitchen's logger, and pass the cook logger to every cook. **/
    fun assignLoggers(kitchenLogger: KitchenLogger, cookLogger: CookLogger) {
        logger = kitchenLogger
        cookLoggers = cookLogger
        for (cook in cooks) {
            cook.setLogger(cookLogger)
        }
    }

    /** Estimates demand and restocks the pantry */
    fun estimateAndRestockFood(groups: List<CustomerGroup>, otherSeats: Int) {
        val pantry = menu.pantry
        pantry.removeReserved()
        val clearedPackages = pantry.clearOldPackages().toList().sortedBy { it.first.getName() }
        for ((ingredient, amount) in clearedPackages) {
            logger?.pantryRemovedIngredient(ingredient, amount)
        }
        val supplies = pantry.getSupplies()
        val neededIngredients = mutableMapOf<Ingredient, Int>()
        // needed for events and regulars
        for (customerGroup in groups) {
            collectGroupIngredients(customerGroup, neededIngredients)
        }
        for (ingredient in supplies.keys) {
            val needed = neededIngredients[ingredient]
            if (needed != null) {
                neededIngredients[ingredient] = needed.coerceAtLeast(0)
            }
        }
        // for remaining seats
        val recipes = menu.recipes
        recipes.forEach { recipe ->
            addIngredientsOf(
                neededIngredients,
                recipe.dishName,
                (otherSeats + FOOD_ESTIMATE_FREE_SEATS_CONSTANT - 1) / FOOD_ESTIMATE_FREE_SEATS_CONSTANT
            )
        }
        // NOW we deduct the amount that we already have for each ingredient
        neededIngredients.entries.forEach {
            neededIngredients[it.key] = (it.value - (supplies[it.key] ?: 0)).coerceAtLeast(0)
        }
        val obtained = pantry.restock(neededIngredients)
        logger?.pantryProcuresIngredient(obtained)
        logger?.pantryRestocked()
    }

    private fun collectGroupIngredients(
        group: CustomerGroup,
        needed: MutableMap<Ingredient, Int>
    ) {
        when (group) {
            is EventCustomerGroup -> collectEventIngredients(group, needed)
            is RegularCustomerGroup -> collectRegularIngredients(group, needed)
            else -> Unit
        }
    }

    // preparing the ingredients of the favorite dish of the group
    private fun collectEventIngredients(
        group: EventCustomerGroup,
        needed: MutableMap<Ingredient, Int>
    ) {
        val dish = group.favoriteBasicDish[menu.type] ?: return
        addIngredientsOf(needed, dish, count = group.size)
    }

    // preparing the ingredients ordered in the last 3 visits
    private fun collectRegularIngredients(
        group: RegularCustomerGroup,
        needed: MutableMap<Ingredient, Int>
    ) {
        for (order in group.last3) {
            for (dish in order.getDishes()) {
                addIngredientsOf(needed, dish.getRecipe().dishName, count = 1)
            }
        }
    }

    // adding the ingredients of a recipe
    private fun addIngredientsOf(neededIngredients: MutableMap<Ingredient, Int>, dish: String, count: Int) {
        val recipe = menu.recipes.find { it.dishName == dish } ?: return

        for ((ingredient, amount) in recipe.ingredients) {
            neededIngredients.merge(ingredient, amount * count, Int::plus)
        }
    }

    /** Queues newOrders for cooking; ingredients are already reserved by the time an order is placed. **/
    fun registerOrders(newOrders: List<Order>) {
        for (order in newOrders) {
            if (orders.none { it.getId() == order.getId() }) {
                orders.add(order)
            }
        }
    }

    /** One cooking tick: assign free cooks, then progress every bound cook. **/
    fun assignAndCook(): Int {
        // hand out cooks
        for (cook in cooks) {
            if (cook.hasLogger()) continue
            cookLoggers?.let { cook.setLogger(it) }
        }
        assignAllDishes()
        val (workingCooks, totalMeals, mealsFinishedThisTick) = cookAssignedDishes()
        logKitchenStatus(workingCooks, totalMeals, mealsFinishedThisTick)
        return mealsFinishedThisTick
    }

    /** Staff eat reserved stock and unfinished meals (opening end / evening end). */
    fun staffEatLeftovers() {
        val remaining = orders.toList()
        for (order in remaining) {
            abortMealsOf(order)
        }
        menu.pantry.removeReserved()
    }

    /** Cooks start the next evening free and without last evening's ids. */
    fun resetForNextEvening() {
        for (cook in cooks) {
            cook.resetForNextEvening()
        }
    }

    /**cancel the orders of a group in the kitchen**/
    fun abortMealsOf(order: Order) {
        val wasRemoved: Boolean = orders.remove(order)
        if (!wasRemoved) return
        for (cook in cooks) {
            cook.clearOrder(order)
        }
    }

    /** Binds a free, eligible cook to every recipe that still has NOT STARTED dishes queued. **/
    private fun assignAllDishes() {
        val pendingRecipes = collectPendingRecipes()
        // go through recipes in priority order and try to assign a cook to each
        for (recipe in pendingRecipes) {
            val relevantOrders = collectOrdersWithDish(recipe)
            if (relevantOrders.isNotEmpty()) {
                assignCookToDish(recipe, relevantOrders)
            }
        }
    }

    /**
     * Every recipe with a NOTSTARTED dish, sorted by priority: oldest order first, then basic
     * dishes, then lower recipe id.
     */
    private fun collectPendingRecipes(): MutableList<Recipe> {
        val pendingRecipes = mutableListOf<Recipe>()
        val earliestOrderId = mutableMapOf<Recipe, Int>()
        // go through every dish of every queued order
        for (order in orders) {
            for (dish in order.getDishes()) {
                if (dish.getStatus() == DishStatus.NOTSTARTED) {
                    recordPendingRecipe(dish.getRecipe(), order.getId(), pendingRecipes, earliestOrderId)
                }
            }
        }
        sortRecipesByPriority(pendingRecipes, earliestOrderId)
        return pendingRecipes
    }

    /** Adds [recipe] to [pendingRecipes] if new, keeping [earliestOrderId] at the lowest order id seen for it. **/
    private fun recordPendingRecipe(
        recipe: Recipe,
        orderId: Int,
        pendingRecipes: MutableList<Recipe>,
        earliestOrderId: MutableMap<Recipe, Int>
    ) {
        if (!pendingRecipes.contains(recipe)) {
            // new recipe
            pendingRecipes.add(recipe)
            earliestOrderId[recipe] = orderId
        } else if (orderId < earliestOrderId.getValue(recipe)) {
            // seen before, but this order is older
            earliestOrderId[recipe] = orderId
        }
    }

    /**
     * Sorts [recipes] in place: oldest pending order first (by [earliestOrderId]), then,
     * for recipes tied on that, basic dishes before non-basic ones, then lower recipe id.
     */
    private fun sortRecipesByPriority(recipes: MutableList<Recipe>, earliestOrderId: Map<Recipe, Int>) {
        for ((i, element) in recipes.withIndex()) {
            var bestIndex = i
            // find the best recipe in the rest of the list
            for (j in i + 1 until recipes.size) {
                if (hasHigherPriority(recipes[j], recipes[bestIndex], earliestOrderId)) {
                    bestIndex = j
                }
            }
            // swap
            val bestRecipe = recipes[bestIndex]
            recipes[bestIndex] = element
            recipes[i] = bestRecipe
        }
    }

    /** Whether [candidate] should be scheduled before [current]: see [sortRecipesByPriority]. **/
    private fun hasHigherPriority(candidate: Recipe, current: Recipe, earliestOrderId: Map<Recipe, Int>): Boolean {
        val candidateOrderId = earliestOrderId.getValue(candidate)
        val currentOrderId = earliestOrderId.getValue(current)
        if (candidateOrderId != currentOrderId) {
            return candidateOrderId < currentOrderId
        }
        // then basic dish
        val candidateIsBasic = candidate.basicDishFor != null
        val currentIsBasic = current.basicDishFor != null
        if (candidateIsBasic != currentIsBasic) {
            return candidateIsBasic
        }
        // at last dish id
        return candidate.id < current.id
    }

    /**
     * Assigns the highest-priority free, eligible cook to [recipe], binding it to every
     * NOTSTARTED dish of that recipe across [ordersForRecipe]. Does nothing if no eligible
     * cook is currently free; the recipe simply stays queued for the next tick.
     */
    private fun assignCookToDish(recipe: Recipe, ordersForRecipe: List<Order>) {
        // no free cook available, try again next tick
        val chosenCook = findFreeEligibleCook(recipe) ?: return

        // first job this evening, give it an id
        if (chosenCook.getId() == null) {
            chosenCook.assignId(nextCookId())
        }

        val mealsNumber = countNotStartedDishes(recipe, ordersForRecipe)
        chosenCook.assignJob(recipe, ordersForRecipe, mealsNumber)
    }

    /** The highest-priority free cook that is eligible to cook [recipe], or null if none is free. **/
    private fun findFreeEligibleCook(recipe: Recipe): Cook? {
        var chosenCook: Cook? = null
        for (cook in cooks) {
            // if this cook is not free find another one
            if (!cook.isFree()) {
                continue
            }
            // if this cook can not cook this recipe find another one
            if (cook.canCook(listOf(recipe)).isEmpty()) {
                continue
            }
            // update chosenCook if higher priority cook is found
            val currentChosen = chosenCook
            if (currentChosen == null || isHigherPriority(cook, currentChosen)) {
                chosenCook = cook
            }
        }
        return chosenCook
    }

    /** How many NOTSTARTED dishes of [recipe] are currently queued across [ordersForRecipe]. **/
    private fun countNotStartedDishes(recipe: Recipe, ordersForRecipe: List<Order>): Int {
        var mealsNumber = 0
        // count matching dishes across all the given orders
        for (order in ordersForRecipe) {
            for (dish in order.getDishes()) {
                if (dish.getRecipe() == recipe && dish.getStatus() == DishStatus.NOTSTARTED) {
                    mealsNumber++
                }
            }
        }
        return mealsNumber
    }

    /**
     * Whether candidate should be preferred over current for the next assignment: lowest
     * CookType rank wins first (spec p.13: "the dish is assigned to the lowest-ranking
     * eligible cook"), then lower (already-assigned) id; ties keep whichever cook was found
     * first, since current only changes when a strictly better cook shows up.
     */
    private fun isHigherPriority(candidate: Cook, current: Cook): Boolean {
        // lowest cook type rank wins first
        if (candidate.getType().ordinal != current.getType().ordinal) {
            return candidate.getType().ordinal > current.getType().ordinal
        }
        val candidateId = candidate.getId()
        val currentId = current.getId()
        // same type, lower id wins
        if (candidateId != null && currentId != null) {
            return candidateId < currentId
        }
        // an already-assigned cook beats one that has no id yet
        return candidateId != null
    }

    /** Orders that still have at least one NOTSTARTED dish of [recipe]. **/
    private fun collectOrdersWithDish(recipe: Recipe): List<Order> {
        val relevantOrders = mutableListOf<Order>()
        for (order in orders) {
            var hasNotStartedDish = false
            // does this order still have a waiting dish of this recipe
            for (dish in order.getDishes()) {
                if (dish.getRecipe() == recipe && dish.getStatus() == DishStatus.NOTSTARTED) {
                    hasNotStartedDish = true
                }
            }
            if (hasNotStartedDish) {
                relevantOrders.add(order)
            }
        }
        return relevantOrders
    }

    /**
     * Progresses bound cooks in id order.
     *
     * @return how many meals finished cooking this tick, for the Kitchen Status log
     */
    private fun cookAssignedDishes(): Triple<Int, Int, Int> {
        var totalDishes = 0
        var finishedDishes = 0
        var workingCooks = 0
        val cooksSorted = cooks.sortedBy { it.getId() }
        for (cook in cooksSorted) {
            val mealsCooking = cook.getMealsNumber()
            if (!cook.isFree()) workingCooks++

            cook.cooks()
            totalDishes += mealsCooking
            if (cook.isFree()) finishedDishes += mealsCooking
        }
        return Triple(workingCooks, totalDishes, finishedDishes)
    }

    /** Next id to hand a cook receiving its first job this evening. **/
    private fun nextCookId(): Int {
        var highestId = 0
        // find the highest id already given out
        for (cook in cooks) {
            val cookId = cook.getId()
            if (cookId != null && cookId > highestId) {
                highestId = cookId
            }
        }
        return highestId + 1
    }

    /**
     * Reports this tick's kitchen activity: active cooks, total meals worked on this tick
     * (still cooking plus the ones that just finished), meals finished this tick, and the
     * backlog of finished meals still waiting to be served.
     */
    private fun logKitchenStatus(cooksActive: Int, totalMeals: Int, mealsFinishedThisTick: Int) {
        var mealsInProgress = 0
        var mealsServable = 0
        // count dishes still cooking vs. done and waiting to be served
        for (order in orders) {
            for (dish in order.getDishes()) {
                if (dish.getStatus() == DishStatus.INPROGRESS) {
                    mealsInProgress++
                } else if (dish.getStatus() == DishStatus.FINISHED) {
                    mealsServable++
                }
            }
        }
        logger?.kitchenStatus(cooksActive, totalMeals, mealsFinishedThisTick, mealsServable)
    }
}
