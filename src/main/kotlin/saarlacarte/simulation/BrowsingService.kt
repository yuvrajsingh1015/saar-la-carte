package saarlacarte.simulation

import saarlacarte.customer.EventCustomerGroup
import saarlacarte.customer.FoodPreferences
import saarlacarte.enums.DriverState
import saarlacarte.enums.RestaurantType
import saarlacarte.enums.TableType
import saarlacarte.food.Recipe
import saarlacarte.restaurant.Restaurant

private const val OFFSET_BEFORE_CLOSING = 3

/**
 * Restaurant browsing and estimated availability.
 */
data class BrowsingService(private val restaurants: List<Restaurant>) {
    private val estimatedSeats = mutableMapOf<Int, MutableMap<TableType, Int>>()
    private val estimatedSeatsEvents = mutableMapOf<Int, MutableMap<TableType, Int>>()

    // Snapshot of currently free drivers; decremented as groups decide this tick.
    private val estimatedDrivers = mutableMapOf<Int, Int>()

    init {
        restaurants.forEach { restaurant ->
            estimatedSeats[restaurant.id] = mutableMapOf(
                TableType.BAR to restaurant.frontOfHouse.availableSeats(TableType.BAR),
                TableType.COMMON to restaurant.frontOfHouse.availableSeats(TableType.COMMON),
                TableType.SEPARATED to restaurant.frontOfHouse.availableSeats(TableType.SEPARATED)
            )
            if (Ticks.getTick() == 1) {
                estimatedSeatsEvents[restaurant.id] = mutableMapOf(
                    TableType.BAR to restaurant.frontOfHouse.totalTablesCount(TableType.BAR),
                    TableType.COMMON to restaurant.frontOfHouse.totalTablesCount(TableType.COMMON),
                    TableType.SEPARATED to restaurant.frontOfHouse.totalTablesCount(TableType.SEPARATED)
                )
            }

            estimatedDrivers[restaurant.id] = restaurant.staff.getDrivers()
                .count { driver -> driver.getState() == DriverState.FREE }
        }
    }

    /**
     * Restaurants that match the group's request.
     */
    fun getEligibleRestaurants(
        restaurantTypes: List<RestaurantType>,
        tableType: TableType? = null, // delivery doesn't need table type
        size: Int,
        prefs: List<FoodPreferences>,
        delivery: Boolean,
        isEvent: Boolean,
        visitingTick: Int,
    ): List<Restaurant> {
        var table = tableType
        if (table == null) { table = TableType.COMMON }
        val restaurantsCorrectTypes = restaurants.filter { it.type in restaurantTypes }
        val restaurantsOpen =
            restaurantsCorrectTypes.filter { isAcceptingAt(it, visitingTick) }
        val restaurantsGroupTypeSpecific: List<Restaurant>
        if (isEvent) {
            restaurantsGroupTypeSpecific = restaurantsOpen.filter {
                it.canHostEvent && size <= (estimatedSeatsEvents[it.id]?.get(table) ?: 0)
            }
        } else if (delivery) {
            restaurantsGroupTypeSpecific = restaurantsOpen.filter { restaurant ->
                (estimatedDrivers[restaurant.id] ?: 0) > 0
            }
        } else {
            restaurantsGroupTypeSpecific = restaurantsOpen.filter {
                size <= (estimatedSeats[it.id]?.get(table) ?: 0)
            }
        }
        // taking food preferences (excluded ingredients) into account
        return restaurantsGroupTypeSpecific.filter { restaurant ->
            prefs.all { pref -> existsEligibleRecipe(pref, restaurant.kitchen.menu.recipes) }
        }
    }

    /** Given preference and recipes, says if there is a recipe this subgroup can eat */
    private fun existsEligibleRecipe(pref: FoodPreferences, recipes: List<Recipe>): Boolean {
        val excludedIngredients = pref.excludedIngredients ?: listOf()
        for (recipe in recipes) {
            val usedIngredientNames = recipe.ingredients.keys.map { it.getName() }
            val isEligibleRecipe = excludedIngredients.none { usedIngredientNames.contains(it) }
            if (isEligibleRecipe) return true
        }
        return false
    }

    /** Reduces estimated seats or drivers after a decision. */
    fun updateAvailability(restaurant: Restaurant, size: Int, tableType: TableType, delivery: Boolean) {
        if (delivery) {
            val oldDriverNumber = estimatedDrivers[restaurant.id]
            checkNotNull(oldDriverNumber) { "Not in browsing service driver estimation" }
            estimatedDrivers.replace(restaurant.id, (oldDriverNumber - 1).coerceAtLeast(0))
        } else {
            val oldTableData = estimatedSeats[restaurant.id]
            checkNotNull(oldTableData) { "Not in browsing service seats estimation" }
            val oldFreeSeats = oldTableData[tableType]
            checkNotNull(oldFreeSeats) { "This type of table is not tracked" }
            oldTableData.replace(tableType, oldFreeSeats - size)
        }
    }

    /** Records an event reservation. */
    fun addReservation(restaurant: Restaurant, event: EventCustomerGroup) {
        val tableType = event.preferredTableType ?: TableType.COMMON
        val size = event.size
        val oldTableData = estimatedSeatsEvents[restaurant.id]
        checkNotNull(oldTableData) { "Not in browsing service seats estimation" }
        val oldFreeSeats = oldTableData[tableType]
        checkNotNull(oldFreeSeats) { "This type of table is not tracked" }
        oldTableData.replace(tableType, oldFreeSeats - size)
    }

    /** Spec: open and not in the last 3 ticks of opening time. */
    private fun isAcceptingAt(restaurant: Restaurant, visitingTick: Int): Boolean {
        return restaurant.openingTickStart <= visitingTick &&
            visitingTick <= restaurant.openingTickEnd - OFFSET_BEFORE_CLOSING
    }
}
