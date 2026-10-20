package saarlacarte.customer

import saarlacarte.enums.CustomerExperience
import saarlacarte.enums.RestaurantType
import saarlacarte.enums.TableType
import saarlacarte.food.Recipe
import saarlacarte.restaurant.Restaurant
import saarlacarte.simulation.BrowsingService
import saarlacarte.simulation.Ticks

private const val EVENT_RESERVE_LEAD_EVENINGS = 3
private const val EVENT_BROWSE_TICK = 1

// import saarlacarte.food.Recipe
/**
 * event customer group
 */
class EventCustomerGroup(
    id: Int,
    size: Int,
    visitingTick: Int,
    customerPreferences: List<FoodPreferences>,
    preferredTableType: TableType,
    val restaurantTypes: List<RestaurantType>,
    val eventEvening: Int,
    val favoriteBasicDish: MutableMap<RestaurantType, String>
) : CustomerGroup(id, size, visitingTick, customerPreferences, preferredTableType), DecidesRestaurant {
    private var browsing: BrowsingService? = null
    private var reservedRestaurant: Restaurant? = null

    /** Browsing service used for this tick's restaurant decision. */
    fun setBrowsingService(service: BrowsingService) {
        browsing = service
    }

    override fun isVisiting(): Boolean {
        return Ticks.getEvening() == eventEvening
    }

    override fun isEvent(): Boolean {
        return true
    }

    override fun isReadyToDecide(): Boolean {
        return Ticks.getTick() == EVENT_BROWSE_TICK &&
            Ticks.getEvening() == eventEvening - EVENT_RESERVE_LEAD_EVENINGS
    }

    override fun decideRestaurant(): Restaurant? {
        val browsingService = browsing ?: return null
        val eligible = browsingService.getEligibleRestaurants(
            restaurantTypes,
            preferredTableType,
            size,
            customerPreferences,
            false,
            true,
            visitingTick
        )
        if (eligible.isEmpty()) {
            return null
        }
        val chosen = makeFinalChoice(eligible)
        reservedRestaurant = chosen
        browsingService.addReservation(chosen, this)
        return chosen
    }

    /** Restaurant this event contacted, if any. */
    fun getReservedRestaurant(): Restaurant? {
        return reservedRestaurant
    }

    /** Event reservation three evenings ahead. */
    fun reserveRestaurant(): Restaurant? {
        return decideRestaurant()
    }

    override fun giveRating(): Int {
        return when (customerExperience) {
            CustomerExperience.NEGATIVE -> -1
            CustomerExperience.POSITIVE, CustomerExperience.NEUTRAL -> 1
        }
    }

    /**
     * Among dishes where they eat all ingredients, event customers prioritize the favorite dish
     * of the event over their own favorite dish or preferred ingredient.
     *
     */
    override fun selectDishForEach(recipes: List<Recipe>, preferences: FoodPreferences?): Recipe? {
        val excluded = preferences?.excludedIngredients ?: emptyList()
        val goodDishes = mutableListOf<Recipe>()

        for (recipe in recipes) {
            val usedIngredients = recipe.ingredients.keys.map { it.getName() }
            if (excluded.intersect(usedIngredients).isEmpty()) {
                goodDishes.add(recipe)
            }
        }

        if (goodDishes.isEmpty()) return null

        val visitedRestaurantType: RestaurantType? = reservedRestaurant?.type

        val eventFavoriteDish = if (visitedRestaurantType != null) {
            favoriteBasicDish[visitedRestaurantType]
        } else {
            null
        }

        if (eventFavoriteDish != null) {
            val eventDishMatch = goodDishes.find { it.dishName == eventFavoriteDish }
            if (eventDishMatch != null) {
                return eventDishMatch
            }
        }

        // fallback to the superclasses' implementation
        return super.selectDishForEach(recipes, preferences)
    }
}
