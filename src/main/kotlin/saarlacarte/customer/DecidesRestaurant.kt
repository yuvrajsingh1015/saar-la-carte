package saarlacarte.customer

import saarlacarte.restaurant.Restaurant

/**
 * Group decides restaurant
 */
interface DecidesRestaurant {
    /** Whether this group should pick a restaurant on the current tick. */
    fun isReadyToDecide(): Boolean

    /** Picks a restaurant, or null if none is suitable. */
    fun decideRestaurant(): Restaurant?

    /** Highest (positive − negative) rating; ties take the lowest restaurant id. */
    fun makeFinalChoice(eligible: List<Restaurant>): Restaurant {
        return eligible.minWith(
            compareByDescending<Restaurant> { it.positiveRatings - it.negativeRatings }
                .thenBy { it.id }
        )
    }
}
