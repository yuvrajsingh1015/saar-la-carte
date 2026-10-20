package saarlacarte.simulation

import saarlacarte.customer.CasualCustomerGroup
import saarlacarte.customer.CustomerGroup
import saarlacarte.customer.DecidesRestaurant
import saarlacarte.customer.EventCustomerGroup
import saarlacarte.customer.RegularCustomerGroup
import saarlacarte.incidents.Incident
import saarlacarte.incidents.PackagingIncident
import saarlacarte.incidents.RecipeIncident
import saarlacarte.incidents.StaffIncident
import saarlacarte.incidents.UnavailableIncident
import saarlacarte.loggers.CookLogger
import saarlacarte.loggers.DeliveryCustomerLogger
import saarlacarte.loggers.DriverLogger
import saarlacarte.loggers.FOHPreparationLogger
import saarlacarte.loggers.FOHSeatingLogger
import saarlacarte.loggers.FOHServingLogger
import saarlacarte.loggers.KitchenLogger
import saarlacarte.loggers.RestaurantLogger
import saarlacarte.loggers.SimulationLogger
import saarlacarte.restaurant.FOHLogger
import saarlacarte.restaurant.Restaurant

private const val TICKS_PER_EVENING = 24
private const val TYPE_STAFF = "STAFF"
private const val TYPE_RECIPE = "RECIPE"
private const val TYPE_PACKAGING = "PACKAGING"
private const val TYPE_UNAVAILABLE = "UNAVAILABLE"

/**
 * Owns and runs the whole simulation.
 */
class Simulation(val logger: SimulationLogger) {
    var restaurants: List<Restaurant> = emptyList()
    var customers: List<CustomerGroup> = emptyList()
    var incidents: List<Incident> = emptyList()

    /** Runs the full simulation: evenings, serving ticks, then statistics. */
    fun run() {
        logger.simulationStart()
        runEvenings()
        endOfSimulation()
    }

    /** All evenings: prep and serving ticks until [Ticks] is exhausted. */
    private fun runEvenings() {
        // Evening by evening until maxTicks
        while (Ticks.canRunMoreTicks()) {
            Ticks.advanceEvening()
            applyIncidents()
            logger.preparationStarts()
            attachVisitingGroups()
            runRestaurantsPreparationPhases()
            logger.servingStart()

            // Evening began, run tick by tick now
            while (Ticks.advanceTick()) {
                logger.tickStart()
                assignCustomersTick()
                runRestaurantsServingPhases()
                endOpeningRestaurants()
                if (Ticks.getTick() == TICKS_PER_EVENING) {
                    endOfEvening()
                    break
                }
            }
        }
    }

    /** Regulars and tonight's events join the restaurant they already booked. */
    private fun attachVisitingGroups() {
        for (group in customers) {
            val restaurant = restaurantForVisit(group) ?: continue
            if (group !in restaurant.customerGroups) {
                restaurant.addCustomerGroup(group)
            }
        }
    }

    private fun restaurantForVisit(group: CustomerGroup): Restaurant? {
        return when (group) {
            is RegularCustomerGroup ->
                if (group.isVisiting()) restaurants.find { it.id == group.getRestaurant() } else null

            is EventCustomerGroup ->
                if (group.isVisiting()) group.getReservedRestaurant() else null

            else -> null
        }
    }

    private fun applyIncidents() {
        incidents
            .filter { it.evening == Ticks.getEvening() }
            .sortedBy { it.id }
            .forEach { incident ->
                incident.execute()
                logger.incidentOccurred(incident, incidentTypeName(incident))
            }
    }

    private fun runRestaurantsPreparationPhases() {
        restaurantsSortedById().forEach { restaurant ->
            restaurant.runPreparationPhase(reservationsFor(restaurant))
        }
    }

    private fun runRestaurantsServingPhases() {
        restaurantsSortedById().forEach { it.runServingPhaseTick() }
    }

    private fun assignCustomersTick() {
        val browsing = BrowsingService(restaurants)
        customers.sortedWith(
            compareBy<CustomerGroup> {
                when (it) {
                    is EventCustomerGroup -> 0
                    else -> 1
                }
            }.thenBy { it.id }
        ).forEach { group ->
            if (group !is DecidesRestaurant || !group.isReadyToDecide()) {
                return@forEach
            }
            group.clearVisitExperience()
            attachBrowsing(group, browsing)
            val restaurant = group.decideRestaurant()
            if (restaurant == null) {
                logger.restaurantNotDecided(group.id)
            } else {
                // Events reserve 3 evenings early; they are attached on the event evening.
                if (group !is EventCustomerGroup) {
                    restaurant.addCustomerGroup(group)
                }
                restaurant.logger?.restaurantDecided(group.id)
            }
        }
    }

    private fun attachBrowsing(group: CustomerGroup, browsing: BrowsingService) {
        when (group) {
            is CasualCustomerGroup -> group.setBrowsingService(browsing)
            is EventCustomerGroup -> group.setBrowsingService(browsing)
            else -> Unit
        }
    }

    private fun endOpeningRestaurants() {
        val tick = Ticks.getTick()
        restaurantsSortedById().forEach { restaurant ->
            if (tick == restaurant.openingTickEnd) {
                restaurant.endOfOpeningTime()
            }
        }
    }

    private fun reservationsFor(restaurant: Restaurant): List<CustomerGroup> {
        return customers.filter { group ->
            when (group) {
                is RegularCustomerGroup -> group.getRestaurant() == restaurant.id && group.isVisiting()
                is EventCustomerGroup -> group.isVisiting() && group.getReservedRestaurant() == restaurant
                else -> false
            }
        }
    }

    private fun restaurantsSortedById(): List<Restaurant> {
        return restaurants.sortedBy { it.id }
    }

    private fun incidentTypeName(incident: Incident): String {
        return when (incident) {
            is StaffIncident -> TYPE_STAFF
            is RecipeIncident -> TYPE_RECIPE
            is PackagingIncident -> TYPE_PACKAGING
            is UnavailableIncident -> TYPE_UNAVAILABLE
            else -> incident::class.simpleName.orEmpty()
        }
    }

    private fun endOfEvening() {
        restaurantsSortedById().forEach { it.endOfEvening() }
        logger.servingEnds()
    }

    private fun endOfSimulation() {
        logger.statisticsCalculated()
        restaurantsSortedById().forEach { it.logStatistics() }
    }

    /** Assigns restaurant / FOH / kitchen / delivery loggers from [logger]. */
    fun assignLoggers() {
        restaurants.forEach { restaurant ->
            val level = logger.logLevel
            val out = logger.outPath
            val id = restaurant.id
            restaurant.assignAllLoggers(
                RestaurantLogger(level, out, id),
                FOHLogger(
                    FOHPreparationLogger(level, out, id),
                    FOHSeatingLogger(level, out, id),
                    FOHServingLogger(level, out, id)
                ),
                KitchenLogger(level, out, id),
                CookLogger(level, out, id),
                DeliveryCustomerLogger(level, out, id),
                DriverLogger(level, out, id)
            )
        }
    }
}
