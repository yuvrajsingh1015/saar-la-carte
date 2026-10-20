package saarlacarte.restaurant

import saarlacarte.customer.CustomerGroup

/**
 * Running totals for one restaurant, printed at the end of the simulation.
 */
class RestaurantStatistics {
    private var mealsCooked: Int = 0
    private var customersServed: Int = 0
    private var customersDelivered: Int = 0
    private var ratingsReceived: Int = 0

    private val servedPeople = mutableMapOf<CustomerGroup, Receipt>()
    private val deliveredPeople = mutableMapOf<CustomerGroup, Receipt>()

    private data class Receipt(val visitGeneration: Int, val received: Int)

    /** Adds finished meals from the current cook phase. */
    fun recordMealsCooked(mealsNumber: Int) {
        mealsCooked += mealsNumber
    }

    /** Adds in-house people who newly received food. */
    fun recordCustomersServed(group: CustomerGroup) {
        customersServed += newlyReceived(group, servedPeople)
    }

    /** Adds delivery people who newly received food. */
    fun recordCustomersDelivered(group: CustomerGroup) {
        customersDelivered += newlyReceived(group, deliveredPeople)
    }

    private fun newlyReceived(group: CustomerGroup, already: MutableMap<CustomerGroup, Receipt>): Int {
        val received = (group.size - group.peopleWaiting - group.peopleLeftFrustrated).coerceAtLeast(0)
        val last = already[group]
        val delta = when {
            last == null || last.visitGeneration != group.visitGeneration -> received
            received > last.received -> received - last.received
            else -> 0
        }
        already[group] = Receipt(group.visitGeneration, received)
        return delta
    }

    /** Adds ratings produced during this tick. */
    fun recordRatingsReceived(ratingsNumber: Int) {
        ratingsReceived += ratingsNumber
    }

    /** Total meals that finished cooking. */
    fun getMealsCooked(): Int = mealsCooked

    /** Total in-house people recorded as served. */
    fun getCustomersServed(): Int = customersServed

    /** Total delivery people recorded as delivered. */
    fun getCustomersDelivered(): Int = customersDelivered

    /** Ratings recorded during the simulation. */
    fun getRatingsReceived(): Int = ratingsReceived
}
