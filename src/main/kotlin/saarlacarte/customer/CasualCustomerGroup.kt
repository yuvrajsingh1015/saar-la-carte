package saarlacarte.customer

import saarlacarte.enums.CustomerExperience
import saarlacarte.enums.RatingLikelihood
import saarlacarte.enums.RestaurantType
import saarlacarte.enums.TableType
import saarlacarte.restaurant.Restaurant
import saarlacarte.simulation.BrowsingService
import saarlacarte.simulation.Ticks

/**
 * casual customer group
 */
open class CasualCustomerGroup(
    id: Int,
    size: Int,
    visitingTick: Int,
    customerPreferences: List<FoodPreferences>,
    preferredTableType: TableType?,
    val restaurantTypes: List<RestaurantType>,
    val visitingEvenings: List<Int>,
    val ratingLikelihood: RatingLikelihood
) : CustomerGroup(id, size, visitingTick, customerPreferences, preferredTableType), DecidesRestaurant {
    protected var browsing: BrowsingService? = null

    /** Browsing service used for this tick's restaurant decision. */
    fun setBrowsingService(service: BrowsingService) {
        browsing = service
    }

    override fun isVisiting(): Boolean {
        return Ticks.getEvening() in visitingEvenings
    }

    override fun giveRating(): Int {
        return when (ratingLikelihood) {
            RatingLikelihood.NEVER -> 0
            RatingLikelihood.SOME -> when (customerExperience) {
                CustomerExperience.POSITIVE -> 1
                CustomerExperience.NEUTRAL -> 0
                CustomerExperience.NEGATIVE -> -1
            }

            RatingLikelihood.ALWAYS -> when (customerExperience) {
                CustomerExperience.POSITIVE, CustomerExperience.NEUTRAL -> 1
                CustomerExperience.NEGATIVE -> -1
            }
        }
    }

    override fun isReadyToDecide(): Boolean {
        return isVisiting() && Ticks.getTick() == visitingTick
    }

    override fun decideRestaurant(): Restaurant? {
        val browsingService = browsing ?: return null
        val eligible = browsingService.getEligibleRestaurants(
            restaurantTypes,
            preferredTableType,
            size,
            customerPreferences,
            isDelivery(),
            false,
            visitingTick
        )
        if (eligible.isEmpty()) {
            return null
        }
        val chosen = makeFinalChoice(eligible)
        browsingService.updateAvailability(
            chosen,
            size,
            preferredTableType ?: TableType.COMMON,
            isDelivery()
        )
        return chosen
    }
}
