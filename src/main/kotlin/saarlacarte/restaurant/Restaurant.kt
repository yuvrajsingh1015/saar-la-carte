package saarlacarte.restaurant

import saarlacarte.customer.CustomerGroup
import saarlacarte.customer.DeliveryCustomerGroup
import saarlacarte.customer.EventCustomerGroup
import saarlacarte.customer.RegularCustomerGroup
import saarlacarte.delivery.DeliveryService
import saarlacarte.enums.RestaurantType
import saarlacarte.loggers.CookLogger
import saarlacarte.loggers.DeliveryCustomerLogger
import saarlacarte.loggers.DriverLogger
import saarlacarte.loggers.KitchenLogger
import saarlacarte.loggers.RestaurantLogger
import saarlacarte.orders.Order
import saarlacarte.simulation.Ticks

private const val LAST_OPEN_ACCEPT = 3

/**
A restaurant taking part in the simulation
 **/
data class Restaurant(
    val id: Int,
    val name: String,
    val type: RestaurantType,
    val openingTickStart: Int,
    val openingTickEnd: Int,
    val canHostEvent: Boolean,
    val staff: Staff,
    var positiveRatings: Int,
    var negativeRatings: Int,
    val frontOfHouse: FrontOfHouse,
    val kitchen: Kitchen,
    val deliveryDriverCount: Int,
    val customerGroups: MutableList<CustomerGroup> = mutableListOf()
) {
    var availableDrivers = deliveryDriverCount
    val deliveryService: DeliveryService = DeliveryService(staff.getDrivers())
    var logger: RestaurantLogger? = null
    val statistics: RestaurantStatistics = RestaurantStatistics()
    private val pendingReserveFailRates = mutableListOf<CustomerGroup>()
    private val alreadyRated = mutableSetOf<Int>()
    private var openingAlreadyClosed = false

    /** Stores restaurant, FOH, kitchen, cook, and delivery-customer loggers. */
    fun assignAllLoggers(
        rl: RestaurantLogger,
        fohLogger: FOHLogger? = null,
        kitchenLogger: KitchenLogger? = null,
        cookLogger: CookLogger? = null,
        deliveryLogger: DeliveryCustomerLogger? = null,
        driverLog: DriverLogger? = null
    ) {
        logger = rl
        if (driverLog != null && deliveryLogger != null) {
            deliveryService.assignLogger(driverLog, deliveryLogger)
        }
        if (fohLogger != null) {
            frontOfHouse.assignLoggers(fohLogger)
        }
        if (kitchenLogger != null && cookLogger != null) {
            kitchen.assignLoggers(kitchenLogger, cookLogger)
        }
    }

    /** Preparation: reserve visiting groups, restock, then publish the cookable menu. */
    fun runPreparationPhase(reservations: List<CustomerGroup> = emptyList()) {
        openingAlreadyClosed = false
        customerGroups.forEach { it.clearVisitExperience() }
        frontOfHouse.managerAssignTables(reservations)
        val freeSeats = frontOfHouse.freeSeatCount()
        var firstTimeRegularSeats = 0
        for (customer in reservations) {
            if (customer is RegularCustomerGroup && customer.last3 == emptyList<Order>()) {
                val tables = frontOfHouse.seatedGroups[customer] ?: emptyList()
                firstTimeRegularSeats += tables.sumOf { it.getSize() }
            }
        }
        kitchen.estimateAndRestockFood(reservations, freeSeats + firstTimeRegularSeats)

        pendingReserveFailRates.clear()
        for (group in reservations) {
            if (!frontOfHouse.hasReservedTable(group)) {
                group.applyEndOfOpeningExperience()
                pendingReserveFailRates.add(group)
            }
        }

        // Set available menu - what our cook staff can cook tonight
        val catalog = kitchen.menu.recipes
        val cookable = kitchen.cooks.flatMap { it.canCook(catalog) }.distinct()
        kitchen.menu.setAvailableMenu(cookable)
        for (driver in staff.getDrivers()) {
            driver.setFree()
        }
        availableDrivers = staff.getDrivers().size
    }

    /** One serving-phase tick: seat through rate. No-op outside opening hours. */
    fun runServingPhaseTick() {
        staff.getWaiters().forEach { it.resetTickLoad() }
        val tick = Ticks.getTick()
        val open = tick in openingTickStart..openingTickEnd
        logger?.restaurantStartsTick(id)
        val unseatedLeavers: List<CustomerGroup>
        val noOrderLeavers: List<CustomerGroup>
        if (open) {
            unseatedLeavers = seat()
            noOrderLeavers = order()
            cook()
            serve()
        } else {
            unseatedLeavers = emptyList()
            noOrderLeavers = emptyList()
        }
        for (group in noOrderLeavers) {
            if (group is DeliveryCustomerGroup) availableDrivers++
        }
        if (tick >= openingTickStart) {
            availableDrivers += deliver()
            val eatLeavers = eat()
            val left = if (open) escort() else emptyList()
            val leftover = if (tick == openingTickEnd) leftoverLeavers() else emptyList()
            val toRate = left + unseatedLeavers + noOrderLeavers + eatLeavers + leftover +
                pendingReserveFailRates + customerGroups.filter { deliveryRatesThisTick(it) } +
                strandedLeavers()
            pendingReserveFailRates.clear()
            if (open) {
                frontOfHouse.logEscortingStatus()
            }
            rate(toRate)
        }
        logger?.restaurantEndTick()
    }

    /** Opening end: leftovers eaten; remaining dine-in leave without a waiter action. */
    fun endOfOpeningTime() {
        if (!openingAlreadyClosed) {
            rate(leftoverLeavers())
        }
        customerGroups.removeAll { !it.isDelivery() }
    }

    /** Still-inside dine-in groups leave with no waiter action, then rate in this tick. */
    private fun leftoverLeavers(): List<CustomerGroup> {
        kitchen.staffEatLeftovers()
        openingAlreadyClosed = true
        return frontOfHouse.escortRemainingWithoutWaiters()
    }

    /** Evening end: leftovers, abort outbound deliveries, free tables, drop tonight's groups. */
    fun endOfEvening() {
        kitchen.staffEatLeftovers()
        frontOfHouse.endOfEvening()
        kitchen.resetForNextEvening()
        for (group in customerGroups) {
            group.resetVisit()
        }
        deliveryService.clearGroups()
        customerGroups.clear()
        alreadyRated.clear()
        pendingReserveFailRates.clear()
        openingAlreadyClosed = false
    }

    /** Registers a group that decided on this restaurant. */
    fun addCustomerGroup(group: CustomerGroup) {
        if (group.hasLeftVisit) {
            group.resetVisit()
        }
        customerGroups.add(group)
        if (group is DeliveryCustomerGroup) {
            deliveryService.addGroup(group)
            availableDrivers--
        }
    }

    /** Writes the four end-of-simulation statistics lines. */
    fun logStatistics() {
        val restaurantLogger = logger ?: return
        restaurantLogger.cookedTotal(statistics.getMealsCooked())
        restaurantLogger.servedTotal(statistics.getCustomersServed())
        restaurantLogger.deliveredTotal(statistics.getCustomersDelivered())
        restaurantLogger.ratingsTotal(statistics.getRatingsReceived())
    }

    /** Seats eligible groups, then turns away anyone who could not be seated. */
    private fun seat(): List<CustomerGroup> {
        val candidates = customerGroups.filter { group ->
            !group.hasLeftVisit && group.id !in alreadyRated && group !in pendingReserveFailRates
        }
        val toProcess = if (Ticks.getTick() <= openingTickEnd - LAST_OPEN_ACCEPT) {
            candidates
        } else {
            // last 3: reserved regulars/events are not new.
            // deliveries and already-seated groups still need to order
            candidates.filter {
                frontOfHouse.hasReservedTable(it) || it.isDelivery() || it.isSeated
            }
        }
        kitchen.registerOrders(frontOfHouse.seatAndOrder(toProcess))
        return turnAwayUnseated()
    }

    /** Releases and returns groups whose seating attempt is exhausted this tick. */
    private fun turnAwayUnseated(): List<CustomerGroup> {
        val tick = Ticks.getTick()
        val leavers = customerGroups.filter { group ->
            !group.isDelivery() && !group.isSeated && !group.hasLeftVisit &&
                group.id !in alreadyRated && group !in pendingReserveFailRates &&
                tick >= group.visitingTick &&
                (
                    tick > openingTickEnd - LAST_OPEN_ACCEPT ||
                        seatingRetriesUsed(group, tick) ||
                        group in frontOfHouse.sentAwayThisTick
                    )
        }
        leavers.forEach { group ->
            group.abandonVisit()
            frontOfHouse.releaseDepartedGroup(group)
        }
        return leavers
    }

    /** Events try once. No-table sent-away leaves this tick; no-waiter still retries once. */
    private fun seatingRetriesUsed(group: CustomerGroup, tick: Int): Boolean {
        return if (group is EventCustomerGroup) {
            tick >= group.visitingTick
        } else {
            tick >= group.visitingTick + 1
        }
    }

    /** FOH already ordered in [seat]; leftover seated/delivery groups without an order leave. */
    private fun order(): List<CustomerGroup> {
        customerGroups.filter { it.peopleLeftFrustrated > 0 }.forEach { it.markNegativeExperience() }
        val failed = customerGroups.filter {
            it.order == null && it.id !in alreadyRated && (it.isDelivery() || it.isSeated)
        }
        failed.forEach { group ->
            group.abandonVisit()
            frontOfHouse.releaseDepartedGroup(group)
        }
        return failed
    }

    /** Assigns and cooks the tick's dishes, recording meals-cooked statistics. */
    private fun cook() {
        statistics.recordMealsCooked(kitchen.assignAndCook())
    }

    /** Serves cooked dine-in orders and records customers-served statistics. */
    private fun serve() {
        frontOfHouse.serveTables(customerGroups.mapNotNull { it.order })
        customerGroups.filter { !it.isDelivery() }.forEach { statistics.recordCustomersServed(it) }
    }

    /** F29 driver trip after serve. */
    private fun deliver(): Int {
        val returnedDrivers = deliveryService.deliver()
        customerGroups.filter { it.isDelivery() }.forEach { statistics.recordCustomersDelivered(it) }
        return returnedDrivers
    }

    /** Fully left this visit still rates if no named leave path caught them. */
    private fun strandedLeavers(): List<CustomerGroup> {
        return customerGroups.filter { it.hasLeftVisit && it.id !in alreadyRated }
    }

    /** Advances eating progress for all seated groups this tick. */
    private fun eat(): List<CustomerGroup> {
        return frontOfHouse.processEating(customerGroups)
    }

    /** Whether a delivery group is done eating or has given up, and so should rate now. */
    private fun deliveryRatesThisTick(group: CustomerGroup): Boolean {
        if (group !is DeliveryCustomerGroup) {
            return false
        }
        if (group.peopleFinishedEating >= group.size - group.peopleLeftFrustrated) {
            return true
        }
        // Still-eating / still-waiting leftovers despawn without rating.
        // Give-up is only "the food never came".
        return group.checkGaveUp() &&
            group.howManyEating() == 0 &&
            group.peopleFinishedEating == 0
    }

    /** Escorts customers who are done eating out of the restaurant. */
    private fun escort(): List<CustomerGroup> {
        return frontOfHouse.escortCustomers()
    }

    /** Rates each group once (regulars, then events, then others), updating counters and stats. */
    private fun rate(customers: List<CustomerGroup>) {
        val oldPositive = positiveRatings
        val oldNegative = negativeRatings
        val ordered = customers.distinctBy { it.id }.sortedWith(
            compareBy(
                {
                    when (it) {
                        is RegularCustomerGroup -> 1
                        is EventCustomerGroup -> 2
                        else -> 3
                    }
                },
                { it.id }
            )
        )
        ordered.forEach {
            if (it.id !in alreadyRated) {
                alreadyRated.add(it.id)
                val result = it.giveRating()
                if (result > 0) {
                    positiveRatings++
                    logger?.receivedRating(it.id, "POSITIVE", positiveRatings, negativeRatings)
                } else if (result < 0) {
                    negativeRatings++
                    logger?.receivedRating(it.id, "NEGATIVE", positiveRatings, negativeRatings)
                }
            }
        }
        val newRatings = positiveRatings - oldPositive + negativeRatings - oldNegative
        logger?.ratingStatus(newRatings)
        statistics.recordRatingsReceived(newRatings)
    }
}
