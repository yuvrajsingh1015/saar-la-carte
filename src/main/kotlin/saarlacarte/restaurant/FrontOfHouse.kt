package saarlacarte.restaurant

import saarlacarte.customer.CasualCustomerGroup
import saarlacarte.customer.CustomerGroup
import saarlacarte.customer.DeliveryCustomerGroup
import saarlacarte.customer.EventCustomerGroup
import saarlacarte.customer.RegularCustomerGroup
import saarlacarte.delivery.Driver
import saarlacarte.enums.DishStatus
import saarlacarte.enums.TableStatus
import saarlacarte.enums.TableType
import saarlacarte.enums.WaiterAction
import saarlacarte.food.Recipe
import saarlacarte.loggers.FOHPreparationLogger
import saarlacarte.loggers.FOHSeatingLogger
import saarlacarte.loggers.FOHServingLogger
import saarlacarte.orders.Dish
import saarlacarte.orders.Order
import saarlacarte.simulation.Ticks
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.contains
import kotlin.collections.sortedWith
import kotlin.math.min

private const val CURRENTLOAD_TEN = 10
private const val REGULAR_PRIORITY = 1
private const val EVENT_PRIORITY = 2
private const val CASUAL_PRIORITY = 3
private const val DELIVERY_PRIORITY = 4

/**
 * Assigns [w] its id for the evening, the first time it performs an action.
 * Top-level (not a member of FrontOfHouse/DriverServing) so both can share it.
 */
internal fun assignWaiterIdIfNeeded(w: Waiter, waiters: List<Waiter>) {
    if (w.getId() != null) return
    // find the current highest id in waiters
    var highestId = 0
    for (waiter in waiters) {
        val waiterId = waiter.getId()
        if (waiterId != null && waiterId > highestId) {
            highestId = waiterId
        }
    }
    w.assignId(highestId + 1)
}

/** A customer stays with the waiter who seated them. A missed dish leaves that slot empty. */
private fun ordersBySeatingBlock(
    blocks: List<Pair<Waiter, Int>>,
    picks: List<Recipe?>
): Map<Waiter, Int>? {
    val taken = linkedMapOf<Waiter, Int>()
    var block = 0
    var left = blocks.getOrNull(0)?.second ?: 0
    for (pick in picks) {
        while (left == 0 && block < blocks.size) {
            block++
            left = blocks.getOrNull(block)?.second ?: 0
        }
        val owner = blocks.getOrNull(block)?.first
        if (owner == null) {
            if (pick != null) return null
            continue
        }
        if (pick != null) {
            val soFar = (taken[owner] ?: 0) + 1
            if (soFar > MAX_TICKLOAD - owner.getTickLoad(WaiterAction.TAKING_ORDER)) return null
            taken[owner] = soFar
        }
        left--
    }
    return taken
}

/**
A restaurant's front of house
 **/
class FrontOfHouse(
    private val tables: List<Table>,
    private val waiters: MutableList<Waiter>,
    private val driverList: MutableList<Driver>?,
    private val menu: Menu,
) {
    private var loggers: FOHLogger? = null
    val seatedGroups: MutableMap<CustomerGroup, List<Table>> = mutableMapOf()

    // seating status is logged after ordering actions (reference / MultifunctionalScenario)
    private val seatingWaiters: MutableSet<Waiter> = mutableSetOf()
    private val orderingWaiters: MutableSet<Waiter> = mutableSetOf()
    private var seatingStatusWaitstaff = 0
    private var seatingStatusCustomers = 0
    private var seatingStatusTables = 0

    // table lookup/reservation/merging (F14/F15) - kept FrontOfHouse under detekt's
    // LargeClass/TooManyFunctions limits; seatedGroups is shared by reference, not copied
    private val tablePreparation = TablePreparation(tables, seatedGroups)
    private val escortingCustomers = EscortingCustomers(waiters, seatedGroups)

    // waiter assigned, no table: leave and rate this tick (coverage-noTable-LeaveTick1)
    internal val sentAwayThisTick: MutableSet<CustomerGroup> = mutableSetOf()
    private val eventSeatingWaiters: MutableMap<EventCustomerGroup, List<Pair<Waiter, Int>>> = mutableMapOf()

    /** mirrors Restaurant.assignAllLoggers **/
    fun assignLoggers(fohLogger: FOHLogger) {
        loggers = fohLogger
        escortingCustomers.loggers = loggers
    }

    /**Serving phase: seat each arriving group, then take its order before the next group. **/
    fun seatAndOrder(groups: List<CustomerGroup>): List<Order> {
        var customersSeated = 0
        var tablesNumber = 0
        val orders: MutableList<Order> = mutableListOf()
        var customersNumber = 0

        // REGULAR, EVENT, CASUAL (includes both in-house & delivery), ascending by id
        val orderedGroups: List<CustomerGroup> =
            groups.filterIsInstance<RegularCustomerGroup>().sortedBy { it.id } +
                groups.filterIsInstance<EventCustomerGroup>().sortedBy { it.id } +
                groups.filterIsInstance<CasualCustomerGroup>().sortedBy { it.id }

        seatingWaiters.clear()
        orderingWaiters.clear()
        sentAwayThisTick.clear()
        eventSeatingWaiters.clear()

        orderedGroups.filter { isEligibleToSeatOrOrder(it) }.forEach { group ->
            // Delivery customers do NOT arrive or get seated; they only order
            if (group is DeliveryCustomerGroup) {
                val (co, _, newOrder) = takeOrder(group)
                customersNumber += co
                orders += newOrder
                return@forEach
            }

            // In-house customers arrive, get seated, and then immediately order
            val (_, cs, t) = seatArrivingGroup(group, group.isArriving())
            customersSeated += cs
            tablesNumber += min(t, 1) // Merged tables count as 1 in status log
            if (group.isSeated) {
                val (co, _, newOrder) = takeOrder(group)
                customersNumber += co
                orders += newOrder
            }
        }

        // Summary status logs
        seatingStatusWaitstaff = seatingWaiters.size
        seatingStatusCustomers = customersSeated
        seatingStatusTables = tablesNumber
        loggers?.loggerSeating?.seatedGroup(
            seatingStatusWaitstaff,
            seatingStatusCustomers,
            seatingStatusTables
        )
        loggers?.loggerSeating?.receivedOrders(customersNumber, orderingWaiters.size)
        return orders
    }

    /** Determines whether a group is due to arrive/seat or place an order this tick. */
    private fun isEligibleToSeatOrOrder(group: CustomerGroup): Boolean {
        if (group.hasLeftVisit) return false
        if (group is DeliveryCustomerGroup) {
            // Delivery customers order on the tick they decide on the restaurant (order not placed yet)
            return group.order == null
        }
        if (group.isSeated) return false
        val currentTick = Ticks.getTick()
        if (currentTick < group.visitingTick) return false
        return if (group is EventCustomerGroup) {
            group.isArriving()
        } else {
            currentTick <= group.visitingTick + 1
        }
    }

    /** @return (customersNumber, waitStaffNumber, orders) **/
    private fun takeOrder(group: CustomerGroup): Triple<Int, Int, List<Order>> {
        val orders: MutableList<Order> = mutableListOf()
        // logger parameters
        var customersNumber: Int = 0
        var waitstaffNumber: Int = 0
        // a group (delivery included) that already has an order was counted when it first ordered
        if (group.order != null) {
            return Triple(0, 0, listOf())
        }
        val (selectedRecipes, picks) = selectRecipes(group)
        val customersWithADish = selectedRecipes.values.sum()
        if (customersWithADish == 0) {
            loggers?.loggerSeating?.orderNotPlaced(group.id, group.size)
            releaseDepartedGroup(group)
            return Triple(0, 0, listOf())
        }
        group.startWaiting4Food()
        val leftFrustrated = group.size - customersWithADish
        if (leftFrustrated > 0) {
            group.peopleLeftFrustrated += leftFrustrated
            group.peopleWaiting -= leftFrustrated
            if (group !is EventCustomerGroup) {
                val targetWaiter = waiters.find { it.getCustomerGroups().contains(group) }
                targetWaiter?.reduceCurrentLoad(leftFrustrated)
            }
        }
        val (order, customersOrdered, waitstaffOrdered) = handleGroup(group, selectedRecipes, picks)
        if (order != null) orders.add(order)
        customersNumber += customersOrdered
        waitstaffNumber += waitstaffOrdered
        if (leftFrustrated > 0) {
            loggers?.loggerSeating?.orderNotPlaced(group.id, leftFrustrated)
        }
        return Triple(customersNumber, waitstaffNumber, orders)
    }

    /** **Serving phase: check if any customers arrive, if so assign waiters and seat them** **/
    fun seatCustomers(groups: List<CustomerGroup>) {
        var waitstaffSeated = 0
        var customersSeated = 0
        var tablesNumber = 0
        eventSeatingWaiters.clear()
        // spec order: REGULAR, EVENT, CASUAL, then ascending id within each type (page 32 line 24)
        val byType = listOf(
            groups.filterIsInstance<RegularCustomerGroup>(),
            groups.filterIsInstance<EventCustomerGroup>(),
            groups.filter { it is CasualCustomerGroup && it !is DeliveryCustomerGroup }
        )
        for (typeGroups in byType) {
            typeGroups.filter { group ->
                !group.hasLeftVisit && !group.isSeated && Ticks.getTick() >= group.visitingTick &&
                    if (group is EventCustomerGroup) {
                        group.isArriving()
                    } else {
                        Ticks.getTick() <= group.visitingTick + 1
                    }
            }.sortedBy { it.id }.forEach {
                val (w, c, t) = seatArrivingGroup(it, it.isArriving())
                waitstaffSeated += w
                customersSeated += c
                // we count merged table as 1 in logging
                tablesNumber += min(t, 1)
            }
        }
        seatingStatusWaitstaff = waitstaffSeated
        seatingStatusCustomers = customersSeated
        seatingStatusTables = tablesNumber
    }

    private fun seatArrivingGroup(group: CustomerGroup, logArrival: Boolean): Triple<Int, Int, Int> {
        if (logArrival) {
            loggers?.loggerSeating?.restaurantArrival(group.id)
        }
        when (group) {
            is EventCustomerGroup -> {
                return seatEventGroup(group)
            }

            else -> {
                val w = assignWaiter(group)
                val waitstaff = mutableListOf<Waiter>()
                if (w != null) {
                    assignWaiterIdIfNeeded(w, waiters)
                    waitstaff.add(w)
                }
                val res = seatGroup(group, waitstaff)
                if (res) {
                    val tablesNumber = seatedGroups[group]?.size ?: 0
                    return Triple(1, group.size, tablesNumber)
                }
            }
        }
        return Triple(0, 0, 0)
    }

    /** Serving phase: assign waitstaff to an EVENT group - P02 **/
    private fun seatEventGroup(cg: EventCustomerGroup): Triple<Int, Int, Int> {
        // no table was reserved for this group at the beginning of the evening (F14) - it
        // should already have been turned away then, so there is nothing left to do here
        if (seatedGroups[cg] == null) return Triple(0, 0, 0)

        val seatedWaiters = seatEventStep(cg.size)
        if (seatedWaiters == null) {
            loggers?.loggerSeating?.waitersUnavailable(cg.id)
            return Triple(0, 0, 0)
        }
        if (seatGroup(cg, seatedWaiters.map { it.first })) {
            // commitSeating (called from seatGroup) already logs the per-group groupSeated line
            eventSeatingWaiters[cg] = seatedWaiters
            val tablesNumber = seatedGroups[cg]?.size ?: 0
            return Triple(seatedWaiters.size, cg.size, tablesNumber)
        }
        return Triple(0, 0, 0)
    }

    /** choosing waiters for event seating; each pair is a waiter and how many they seated **/
    private fun seatEventStep(groupSize: Int): List<Pair<Waiter, Int>>? {
        val byCurrentLoadDesc = compareByDescending<Waiter> { w -> w.getCurrentLoad() }
            .thenBy { w -> w.getId() ?: Int.MAX_VALUE }
        val eligibleUnsorted = mutableListOf<Waiter>()
        for (w in waiters) {
            if (w.getTickLoad(WaiterAction.SEAT) < MAX_TICKLOAD) {
                eligibleUnsorted.add(w)
            }
        }
        val eligible = eligibleUnsorted.sortedWith(byCurrentLoadDesc)
        val participating = mutableListOf<Pair<Waiter, Int>>()
        var remaining = groupSize
        for (w in eligible) {
            if (remaining <= 0) break
            val take = minOf(MAX_TICKLOAD - w.getTickLoad(WaiterAction.SEAT), remaining)
            assignWaiterIdIfNeeded(w, waiters)
            w.performAction(WaiterAction.SEAT, take)
            participating.add(w to take)
            remaining -= take
        }
        return if (remaining <= 0) participating else null
    }

    /**
     * Serving phase: assign a waiter to a customer group - P15/P16.
     * Among all free waiters with currentLoad < 10, selects the one with the most customers.
     * If there are none, considers waiters whose SEATING tickLoad plus the group size stays
     * under [MAX_TICKLOAD] and selects the one with the lowest currentLoad.
     * Ties go to the lowest id.
     * @return a waiter who can perform SEATING, null otherwise
     **/
    fun assignWaiter(group: CustomerGroup): Waiter? {
        val freeWaiters = waiters
            .filter { it.canPerform(WaiterAction.SEAT, group.size) }
            .sortedBy { it.getId() ?: Int.MAX_VALUE } // ascending order of id, then nulls

        if (freeWaiters.isEmpty()) return null // there are no free waiters

        val lowLoadWaiters = freeWaiters.filter { it.getCurrentLoad() < CURRENTLOAD_TEN }

        return if (lowLoadWaiters.isNotEmpty()) { // select waiter with most customers <=> highest currentLoad
            lowLoadWaiters.maxByOrNull { it.getCurrentLoad() }
        } else { // select waiter with lowest currentLoad
            freeWaiters
                .filter { it.getTickLoad(WaiterAction.SEAT) + group.size <= MAX_TICKLOAD }
                .minByOrNull { it.getCurrentLoad() }
        }
    }

    /** Serving phase: attempt to seat a customer group **/
    private fun seatGroup(group: CustomerGroup, waiters: List<Waiter>): Boolean {
        if (waiters.isEmpty()) {
            loggers?.loggerSeating?.waitersUnavailable(group.id)
            return false
        } // no waiters available to seat the group

        // REGULAR and EVENT groups already had a table reserved for them at the beginning of
        // the evening (managerAssignTables) - that table is RESERVED, not FREE, so it has to
        // be looked up rather than searched for again. Only CASUAL groups get a table ad-hoc.
        val tables = if (group is RegularCustomerGroup || group is EventCustomerGroup) {
            seatedGroups[group] ?: emptyList()
        } else {
            findTables(group)
        }

        if (tables.isEmpty()) {
            val waiter = waiters.first()
            assignWaiterIdIfNeeded(waiter, waiters)
            waiter.getId()?.let { loggers?.loggerSeating?.tableUnavailable(it, group.id) }
            sentAwayThisTick.add(group)
            return false
        } // could not find any eligible tables for the group

        when (group) {
            is EventCustomerGroup -> {
                // SEAT tickLoad was already applied per-waiter in seatEventGroup
                commitSeating(group, tables, waiters)
                return true
            }

            else -> {
                waiters.forEach { w -> w.performAction(WaiterAction.SEAT, group.size) }
                commitSeating(group, tables, waiters)
                return true
            }
        }
    }

    /** Occupies tables, records waiters against group and logs the successful seating. **/
    private fun commitSeating(group: CustomerGroup, tables: List<Table>, waiters: List<Waiter>) {
        for (table in tables) {
            table.setStatus(TableStatus.OCCUPIED)
        }
        for (waiter in waiters) {
            waiter.addCustomerGroup(group)
            seatingWaiters.add(waiter)
        }
        group.isSeated = true
        seatedGroups[group] = tables
        val tableId = mutableListOf<Int>()
        for (table in tables) {
            tableId.add(table.getId())
        }
        // the merge log comes right before the seating log, and only when tables were merged
        if (tables.size > 1) {
            val mergedTable = tables.minWithOrNull(compareBy { it.getId() })
            if (mergedTable != null) {
                loggers?.loggerSeating?.tablesMerged(group.id, tables.map { it.getId() }, mergedTable.getId())
            }
        }
        val waitstaffIds = mutableListOf<Int>()
        for (waiter in waiters) {
            waitstaffIds.add(waiter.getId() ?: 0)
        }
        loggers?.loggerSeating?.groupSeated(group.id, tableId.min(), waitstaffIds)
    }

    /** Serving phase: find a suitable table for a customer group or merge them to make one **/
    fun findTables(group: CustomerGroup): List<Table> = tablePreparation.findTables(group)

    /** @return (order, customersNumber, waitStaffNumber) **/
    private fun handleGroup(
        group: CustomerGroup,
        selectedRecipes: Map<Recipe, Int>,
        picks: List<Recipe?>
    ): Triple<Order?, Int, Int> {
        var order: Order? = null
        when (group) {
            is DeliveryCustomerGroup -> {
                val tripleToReturn: Triple<Order?, Int, Int>
                if (group.order == null) {
                    order = takeOrder(group, selectedRecipes, emptyList())
                    // build parameters for logger and log
                    val dishNameToAmount = selectedRecipes.mapKeys { it.key.dishName }
                    val customersNumber = selectedRecipes.values.sum()
                    loggers?.loggerSeating?.orderPlaced(
                        group.id,
                        order.getId(),
                        dishNameToAmount,
                        listOf()
                    )
                    tripleToReturn = Triple(order, customersNumber, 0)
                } else {
                    // already ordered at arrival; count them in the status log, do not re-queue
                    tripleToReturn = Triple(null, group.size, 0)
                }
                return tripleToReturn
            }

            is EventCustomerGroup -> {
                // event: other actions
                return handleEventOrdering(group, selectedRecipes, picks)
            }

            // for regular/casual groups only one waiter takes their order
            else -> {
                val targetWaiter = waiters.find { it.getCustomerGroups().contains(group) }
                // the group is not yet seated/assigned to a waiter
                if (targetWaiter == null) return Triple(null, 0, 0)

                val customersOrdered = selectedRecipes.values.sum()
                if (targetWaiter.canPerform(WaiterAction.TAKING_ORDER, customersOrdered)) {
                    order = takeOrder(group, selectedRecipes, listOf(targetWaiter))
                    // build parameters for logger and log
                    val dishNameToAmount = selectedRecipes.mapKeys { it.key.dishName }
                    val customersNumber = selectedRecipes.values.sum()
                    val waiterId = targetWaiter.getId()
                    loggers?.loggerSeating?.orderPlaced(
                        group.id,
                        order.getId(),
                        dishNameToAmount,
                        listOf(waiterId ?: 0)
                    )
                    return Triple(order, customersNumber, 1)
                }
            }
        }
        return Triple(null, 0, 0)
    }

    private fun handleEventOrdering(
        group: CustomerGroup,
        selectedRecipes: Map<Recipe, Int>,
        picks: List<Recipe?>
    ): Triple<Order?, Int, Int> {
        val orderedCount = selectedRecipes.values.sum()
        if (orderedCount == 0) return Triple(null, 0, 0)
        val blocks = (group as? EventCustomerGroup)?.let { eventSeatingWaiters[it] }
            ?: return Triple(null, 0, 0)
        val taken = ordersBySeatingBlock(blocks, picks) ?: return Triple(null, 0, 0)
        val participating = taken.keys.toList()
        if (participating.isEmpty()) return Triple(null, 0, 0)
        participating.forEach { assignWaiterIdIfNeeded(it, waiters) }
        val order = takeOrder(group, selectedRecipes, participating, taken)
        loggers?.loggerSeating?.orderPlaced(
            group.id,
            order.getId(),
            selectedRecipes.mapKeys { it.key.dishName },
            participating.map { it.getId() ?: 0 }
        )
        return Triple(order, orderedCount, participating.size)
    }

    /** Take the order of the customer group according to their preferences, if it's delivery, etc. **/
    private fun takeOrder(
        group: CustomerGroup,
        recipes: Map<Recipe, Int>,
        orderWaiters: List<Waiter>,
        eventAmounts: Map<Waiter, Int>? = null
    ): Order {
        if (group is DeliveryCustomerGroup) {
            val order = constructOrder(recipes, true, group.deliveryDistance, Ticks.getTick())
            group.order = order
            return order
        }
        var remaining = recipes.values.sum()
        for (waiter in orderWaiters) {
            val capped = minOf(MAX_TICKLOAD - waiter.getTickLoad(WaiterAction.TAKING_ORDER), remaining)
            val amount = eventAmounts?.get(waiter) ?: capped
            if (amount <= 0) continue
            orderingWaiters.add(waiter)
            waiter.performAction(WaiterAction.TAKING_ORDER, amount)
            remaining -= amount
        }
        val order = constructOrder(recipes, false, null, Ticks.getTick())
        group.order = order
        return order
    }

    /**
     * Each of [group]'s customers picks a dish; a customer who can't find one they eat is
     * skipped rather than discarding everyone else's picks (spec: only that customer leaves
     * without ordering, the rest of the group still orders normally).
     */
    private fun selectRecipes(group: CustomerGroup): Pair<Map<Recipe, Int>, List<Recipe?>> {
        val selectedRecipes = mutableMapOf<Recipe, Int>()
        val picks = mutableListOf<Recipe?>()
        repeat(group.size) {
            val availableRecipes = menu.filterAvailableMenu()
            val recipe = group.selectDish(availableRecipes)
            picks.add(recipe)
            if (recipe == null) return@repeat
            menu.reserveDishes(mapOf(recipe to 1))
            selectedRecipes[recipe] = (selectedRecipes[recipe] ?: 0) + 1
        }
        return selectedRecipes to picks
    }

    private fun constructOrder(
        recipes: Map<Recipe, Int>,
        delivery: Boolean,
        deliveryDistance: Int?,
        orderedTick: Int
    ): Order {
        val dishes = mutableListOf<Dish>()
        recipes.forEach { (recipe, amount) ->
            // regardless of restaurant type
            val isBasicDish = recipe.basicDishFor != null
            repeat(amount) { dishes.add(Dish(recipe, DishStatus.NOTSTARTED, isBasicDish)) }
        }
        return Order(Order.nextId(), dishes, delivery, deliveryDistance, orderedTick)
    }

    private val inHouseServing = InHouseServing(waiters, seatedGroups)

    /** Serve ready dishes to their respective customers
     * @return Number of dishes served this tick **/
    fun serveTables(orders: List<Order>): Pair<Int, Int> {
        var servedDishes = 0

        // each table logs its serving lines and, if some meals stay, its no-serving line,
        // before the next table. Drivers are handed meals only after every table.
        val inHouseOrders = inHouseServing.ordersWithReadyDishes(orders.filter { !it.isDelivery() })
        servedDishes += inHouseServing.serveInOrder(inHouseOrders, loggers)

        val deliveryOrders =
            orders.filter { it.isDelivery() && it.getDishes().any { dish -> dish.getStatus() == DishStatus.FINISHED } }
        if (deliveryOrders.isNotEmpty()) {
            val (dd, _) = serveDrivers(deliveryOrders)
            servedDishes += dd
        }

        // a waiter serving several tables/drivers this tick must only be counted once
        val waitstaffServing = waiters.count { it.getTickLoad(WaiterAction.SERVING) > 0 }
        loggers?.loggerServing?.servingStatus(waitstaffServing, servedDishes)
        return Pair(servedDishes, waitstaffServing)
    }

    // driver-facing serving logic (give dishes to waiting drivers, forward the delivery tick)
    // lives in DriverServing - kept FrontOfHouse under detekt's LargeClass/TooManyFunctions limits
    private val driverServing = DriverServing(waiters, driverList)

    /** give out dishes to the drivers **/
    fun serveDrivers(orders: List<Order>): Pair<Int, Int> = driverServing.serveDrivers(orders, loggers)
    private fun processLeaving(group: CustomerGroup, leftCount: Int, tableId: Int) {
        if (leftCount > 0) {
            loggers?.loggerServing?.groupNotServedLeft(leftCount, group.id, tableId)
            if (group !is EventCustomerGroup) {
                waiters.find { group in it.getCustomerGroups() }?.reduceCurrentLoad(leftCount)
            }
        }
    }

    /**
     * processEating
     * Forum 335: No Eating first, then Finished Eating.
     * Delivery is a casual in that sort. Eating Status last.
     * @param groups all groups at the restaurant this tick
     * @return in-house groups that left due to not being served
     */
    fun processEating(groups: List<CustomerGroup> = emptyList()): List<CustomerGroup> {
        var totalStillEating = 0
        var totalFinishedEating = 0
        val departed = mutableListOf<CustomerGroup>()
        val byTypeThenId = compareBy<CustomerGroup> {
            when {
                it.isRegular() -> REGULAR_PRIORITY
                it.isEvent() -> EVENT_PRIORITY
                !it.isDelivery() -> CASUAL_PRIORITY
                else -> DELIVERY_PRIORITY
            }
        }.thenBy { it.id }

        val seatedCandidates = seatedGroups.keys.filter { it.isSeated }.sortedWith(byTypeThenId)

        for (group in seatedCandidates) {
            val tableId = seatedGroups[group]?.minOf { it.getId() } ?: 0
            val leftCount = group.waitAndGiveUp()
            processLeaving(group, leftCount, tableId)
            if (!group.isSeated) {
                departed.add(group)
            }
        }
        departed.forEach { releaseDepartedGroup(it) }

        val stillSeated = seatedGroups.keys.filter { it.isSeated }
        val delivery = groups.filter { it.isDelivery() && !it.hasLeftVisit }
        val eating = (stillSeated + delivery).distinct().sortedWith(byTypeThenId)
        for (group in eating) {
            if (group.isDelivery()) {
                group.eat()
                continue
            }
            val tableId = seatedGroups[group]?.minOf { it.getId() } ?: 0
            val beforeFinishedEating = group.peopleFinishedEating
            val (stillEating, finishedEating) = group.eat()
            val newlyFinished = finishedEating - beforeFinishedEating
            totalStillEating += stillEating
            totalFinishedEating += newlyFinished
            if (newlyFinished > 0) {
                loggers?.loggerServing?.finishedEating(newlyFinished, group.id, tableId)
            }
        }

        loggers?.loggerServing?.eatingStatus(totalStillEating, totalFinishedEating)
        return departed
    }

    /** Adj 6: drop a group that left without an escort action. */
    fun releaseDepartedGroup(group: CustomerGroup) {
        demergeTables(group)
        for (waiter in waiters) {
            waiter.releaseGroup(group)
        }
    }

    /****/
    fun escortCustomers(): List<CustomerGroup> = escortingCustomers.escortCustomers()

    /** DEBUG escorting status; call after escort actions and before Rating. */
    fun logEscortingStatus() = escortingCustomers.logEscortingStatus()

    /** Whether this group received a table during evening reservation. */
    fun hasReservedTable(group: CustomerGroup): Boolean = seatedGroups.containsKey(group)

    /** Opening end: remaining dine-in leave; regular/event tables stay reserved. */
    fun escortRemainingWithoutWaiters(): List<CustomerGroup> {
        val remaining = seatedGroups.keys.filter { !it.isDelivery() }.toList()
        val leftover = remaining.filter { it.isSeated }
        for (group in remaining) {
            demergeTables(group)
            if (group.isSeated) {
                group.applyEndOfOpeningExperience()
                group.resetVisit()
            }
            for (waiter in waiters) {
                waiter.releaseGroup(group)
            }
        }
        return leftover
    }

    /** Evening end: free every table and reset waiters and return updated rating. */
    fun endOfEvening() {
        seatedGroups.clear()
        eventSeatingWaiters.clear()
        for (table in tables) {
            table.setStatus(TableStatus.FREE)
        }
        for (waiter in waiters) {
            waiter.resetForNextEvening()
        }
    }

    /** Preparation phase: reserve tables for regular/event customers **/
    fun managerAssignTables(customers: List<CustomerGroup>) =
        tablePreparation.managerAssignTables(customers, loggers)

    /** Preparation phase: count the free seats left after reserving tables **/
    fun freeSeatCount(): Int = tablePreparation.freeSeatCount()

    /** Serving phase: count all available seats **/
    fun availableSeats(tableType: TableType): Int = tablePreparation.availableSeats(tableType)

    /** total tables count per type **/
    fun totalTablesCount(tableType: TableType): Int = tablePreparation.totalTablesCount(tableType)

    /** frees casual tables; regular/event tables stay reserved (i.e. no change) **/
    internal fun demergeTables(group: CustomerGroup) = tablePreparation.demergeTables(group)
}

/** Loggers for FrontOfHouse **/
data class FOHLogger(
    val loggerPreparation: FOHPreparationLogger,
    val loggerSeating: FOHSeatingLogger,
    val loggerServing: FOHServingLogger
)
