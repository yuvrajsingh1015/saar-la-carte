package saarlacarte.customer

import saarlacarte.enums.CustomerExperience
import saarlacarte.enums.RatingLikelihood
import saarlacarte.enums.RestaurantType
import saarlacarte.enums.TableType
import saarlacarte.loggers.DeliveryCustomerLogger
import saarlacarte.restaurant.Restaurant
import saarlacarte.simulation.Ticks
import kotlin.math.ceil

// distance covered per tick by the driver
private const val DELIVERY_TICKS_PER_DISTANCE = 5.0

// ticks to wait past visitingTick before giving up
private const val DELIVERY_GIVE_UP_TICKS = 3

/**
 * delivery customer group
 */
open class DeliveryCustomerGroup(
    id: Int,
    size: Int,
    visitingTick: Int,
    customerPreferences: List<FoodPreferences>,
    preferredTableType: TableType?,
    restaurantTypes: List<RestaurantType>,
    visitingEvenings: List<Int>,
    ratingLikelihood: RatingLikelihood,
    val deliveryDistance: Int
) : CasualCustomerGroup(
    id,
    size,
    visitingTick,
    customerPreferences,
    preferredTableType,
    restaurantTypes,
    visitingEvenings,
    ratingLikelihood
) {
    private var logger: DeliveryCustomerLogger? = null
    var hasGaveUp: Boolean = false

    /** sets the logger **/
    fun setLogger(deliveryCustomerLogger: DeliveryCustomerLogger) {
        logger = deliveryCustomerLogger
    }

    /** ready travelTicks + 3 ticks before visitingTick **/
    override fun isReadyToDecide(): Boolean {
        val travelTicks = ceil(deliveryDistance / DELIVERY_TICKS_PER_DISTANCE).toInt()
        return isVisiting() && Ticks.getTick() == visitingTick - 3 - travelTicks
    }

    override fun isDelivery(): Boolean {
        return true
    }

    /** rating based on experience and ratingLikelihood, spec **/
    override fun giveRating(): Int {
        return when (customerExperience) {
            CustomerExperience.NEGATIVE -> {
                if (ratingLikelihood == RatingLikelihood.SOME || ratingLikelihood == RatingLikelihood.ALWAYS) -1 else 0
            }

            CustomerExperience.NEUTRAL -> {
                if (ratingLikelihood == RatingLikelihood.ALWAYS) 1 else 0
            }

            CustomerExperience.POSITIVE -> {
                if (ratingLikelihood == RatingLikelihood.SOME || ratingLikelihood == RatingLikelihood.ALWAYS) 1 else 0
            }
        }
    }

    /** logs once everyone who actually got food has finished eating (some may never have ordered) **/
    override fun eat(): Pair<Int, Int> {
        val wasFinished = peopleFinishedEating
        val result = super.eat()
        val expectedToFinish = size - peopleLeftFrustrated
        if (wasFinished < expectedToFinish && peopleFinishedEating == expectedToFinish) {
            logger?.deliveryFinishedEating(id)
        }
        return result
    }

    /** sets the experience once the food actually arrives, based on the tick **/
    override fun receiveOrder() {
        val wasWaiting = peopleWaiting
        super.receiveOrder()
        if (wasWaiting > 0 && peopleWaiting == 0) {
            if (customerExperience == CustomerExperience.NEGATIVE) {
                return
            }
            val tick = Ticks.getTick()
            val experience = when {
                tick < visitingTick -> CustomerExperience.POSITIVE
                tick == visitingTick -> CustomerExperience.NEUTRAL
                else -> CustomerExperience.NEGATIVE
            }
            customerExperience = experience
        }
        peopleWaiting = 0
    }

    /**wtih new element*/
    override fun resetVisit() {
        super.resetVisit()
        hasGaveUp = false
    }

    /** true once the group waited too long for a delivery that never came **/
    fun checkGaveUp(): Boolean = hasGaveUp

    /**log that a group gives up this tick**/
    fun giveUpCaseCheck() {
        if (Ticks.getTick() == visitingTick + DELIVERY_GIVE_UP_TICKS && peopleWaiting > 0) {
            hasGaveUp = true
            val currentOrder = order ?: return
            customerExperience = CustomerExperience.NEGATIVE
            logger?.deliveryGivenUp(id, currentOrder.getId())
        }
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
            Ticks.getTick()
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
