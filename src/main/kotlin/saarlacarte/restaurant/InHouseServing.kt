package saarlacarte.restaurant

import saarlacarte.customer.CustomerGroup
import saarlacarte.customer.EventCustomerGroup
import saarlacarte.enums.DishStatus
import saarlacarte.enums.WaiterAction
import saarlacarte.orders.Dish
import saarlacarte.orders.Order
import saarlacarte.simulation.Ticks

/**
 * Main logic and helper functions for serving in FrontOfHouse -
 * moved away from main FOH class file because of detekt issues
 **/
class InHouseServing(
    private val waiters: List<Waiter>,
    private val seatedGroups: MutableMap<CustomerGroup, List<Table>>,
) {
    /**
     * Serve the given in-house orders, regular then event then casual.
     * A table's serving lines and its no-serving line are logged together, before the next table.
     * @return dishes served this call
     **/
    fun serveInOrder(
        orders: List<Order>,
        loggers: FOHLogger?,
        notServedSink: MutableList<Triple<Int, Int, Int>>? = null
    ): Int {
        var servedDishes = 0
        prioritizeOrdersList(orders).forEach { order ->
            val targetGroup = seatedGroups.keys.find { it.order?.getId() == order.getId() }
            if (targetGroup == null) return@forEach
            servedDishes += serveGroup(order, targetGroup, loggers, notServedSink)
        }
        return servedDishes
    }

    /** Log a failed serving now, in table order, unless the caller is collecting the lines. **/
    private fun recordNotServed(
        notServedSink: MutableList<Triple<Int, Int, Int>>?,
        loggers: FOHLogger?,
        waiterId: Int,
        meals: Int,
        tableId: Int
    ) {
        if (meals == 0) return
        if (notServedSink != null) {
            notServedSink.add(Triple(waiterId, meals, tableId))
        } else {
            loggers?.loggerServing?.notServing(waiterId, meals, tableId)
        }
    }

    /** Serve an in-house order to its customer group **/
    fun serveGroup(
        order: Order,
        group: CustomerGroup,
        loggers: FOHLogger?,
        notServedSink: MutableList<Triple<Int, Int, Int>>? = null
    ): Int {
        var servedDishes = 0
        when (group) {
            is EventCustomerGroup -> {
                val currentServed = serveEvent(order, group, loggers, notServedSink)
                servedDishes += currentServed
            }

            else -> {
                val targetWaiter = waiters.find { it.getCustomerGroups().contains(group) }
                if (targetWaiter == null) return 0

                val targetTableId = seatedGroups[group]?.minOf { it.getId() } ?: 0
                val readyDishes = order.getDishes().filter { it.getStatus() == DishStatus.FINISHED }.size
                val remainingLoad = MAX_TICKLOAD - targetWaiter.getTickLoad(WaiterAction.SERVING)
                if (remainingLoad <= 0 || holdForWholeTable(order, remainingLoad)) {
                    recordNotServed(
                        notServedSink,
                        loggers,
                        targetWaiter.getId() ?: 0,
                        readyDishes,
                        targetTableId
                    )
                    return 0
                }
                val servedDishesToAmount = order.servePartially(group, remainingLoad)
                if (servedDishesToAmount.isEmpty()) {
                    recordNotServed(
                        notServedSink,
                        loggers,
                        targetWaiter.getId() ?: 0,
                        readyDishes,
                        targetTableId
                    )
                } else {
                    val justServed = servedDishesToAmount.values.sum()
                    servedDishes += justServed
                    targetWaiter.performAction(WaiterAction.SERVING, justServed)
                    // logger parameters
                    val ticksNumber = Ticks.getTick() - order.getOrderedTick()
                    loggers?.loggerServing?.waitstaffServes(
                        targetWaiter.getId() ?: 0,
                        servedDishesToAmount,
                        targetTableId,
                        ticksNumber
                    )
                    // ready meals that did not fit this waiter's serving limit
                    val stillReady = order.getDishes().count { it.getStatus() == DishStatus.FINISHED }
                    if (stillReady > 0) {
                        recordNotServed(
                            notServedSink,
                            loggers,
                            targetWaiter.getId() ?: 0,
                            stillReady,
                            targetTableId
                        )
                    }
                }
            }
        }
        return servedDishes
    }

    /**
     * Hold a table that is not being served yet.
     * A partial order waits the cook tick and the next one.
     * A complete order that does not fit waits the tick it became complete,
     * including when earlier dishes of that order finished on a previous tick.
     **/
    private fun holdForWholeTable(order: Order, remainingLoad: Int): Boolean {
        val dishes = order.getDishes()
        if (dishes.any { it.getStatus() == DishStatus.SERVED }) return false
        val finished = dishes.filter { it.getStatus() == DishStatus.FINISHED }
        if (finished.isEmpty()) return false
        val tick = Ticks.getTick()
        val oldest = finished.maxOf { tick - it.getFinishedTick() }
        val allCooked = dishes.all { it.getStatus() == DishStatus.FINISHED }
        if (!allCooked) return oldest < 2
        val justCompleted = finished.any { tick - it.getFinishedTick() == 0 }
        if (oldest >= 2 || (!justCompleted && oldest >= 1)) return false
        return finished.size > remainingLoad
    }

    /** @return all orders with at least one ready dish **/
    fun ordersWithReadyDishes(orders: List<Order>): List<Order> {
        return orders.filter { order ->
            order.getDishes().any { it.getStatus() == DishStatus.FINISHED }
        }
    }

    /** @return a list of orders prioritizing customers **/
    fun prioritizeOrdersList(orders: List<Order>): List<Order> {
        val customers = seatedGroups.keys

        return orders.sortedWith(
            compareBy<Order> { order ->
                val customer = customers.firstOrNull { it.order == order }
                when {
                    customer?.isRegular() == true -> 0
                    customer?.isEvent() == true -> 1
                    else -> 2 // CASUAL or fallback
                }
            }.thenBy { order ->
                val customer = customers.firstOrNull { it.order == order }
                customer?.id ?: 0
            }
        )
    }

    /** @return Pair(servedDishesCount)**/
    private fun serveEvent(
        order: Order,
        group: EventCustomerGroup,
        loggers: FOHLogger?,
        notServedSink: MutableList<Triple<Int, Int, Int>>?
    ): Int {
        val dishes = order.getDishes()
        val finishedDishes = dishes.filter { it.getStatus() == DishStatus.FINISHED }

        if (finishedDishes.isEmpty()) return 0

        val targetTableId = seatedGroups[group]?.minOf { it.getId() } ?: 0
        val totalDishes = dishes.size
        val alreadyServedCount = dishes.count { it.getStatus() == DishStatus.SERVED }

        val areAllDishesCooked = totalDishes == finishedDishes.size + alreadyServedCount

        val priority = eventWaiterPriority()

        val totalServingCapacity = waiters.sumOf { MAX_TICKLOAD - it.getTickLoad(WaiterAction.SERVING) }

        if (shouldHoldEventServing(areAllDishesCooked, alreadyServedCount, finishedDishes, totalServingCapacity)) {
            recordNotServed(notServedSink, loggers, holdWaiterId(priority), finishedDishes.size, targetTableId)
            return 0
        }
        return servingEvent(order, group, finishedDishes, priority, targetTableId, loggers, notServedSink)
    }

    /**
     * Hold while other meals of the table are still cooking.
     * When everything is cooked but the room cannot serve it all, hold the tick
     * the order became complete, even if some of its dishes finished earlier.
     * The following tick serves whatever still fits.
     */
    private fun shouldHoldEventServing(
        areAllDishesCooked: Boolean,
        alreadyServedCount: Int,
        finishedDishes: List<Dish>,
        totalServingCapacity: Int
    ): Boolean {
        if (alreadyServedCount > 0 || finishedDishes.isEmpty()) return false
        val tick = Ticks.getTick()
        val oldest = finishedDishes.maxOf { tick - it.getFinishedTick() }
        if (!areAllDishesCooked) return oldest < 2
        if (oldest >= 2) return false
        val justCompleted = finishedDishes.any { tick - it.getFinishedTick() == 0 }
        if (!justCompleted && oldest >= 1) return false
        return totalServingCapacity < finishedDishes.size
    }

    /**
     * First waiter in [priority] who can still serve. If every waiter is at the limit,
     * the first one is named anyway.
     */
    private fun holdWaiterId(priority: List<Waiter>): Int {
        val named = priority.firstOrNull { it.getTickLoad(WaiterAction.SERVING) < MAX_TICKLOAD }
            ?: priority.firstOrNull()
        return named?.getId() ?: 0
    }

    /** Most ready meals on the waiter's regular and casual tables, then lowest id. */
    private fun eventWaiterPriority(): List<Waiter> {
        return waiters.sortedWith(
            compareByDescending<Waiter> { getCookedMealsOfWaiter(it) }
                .thenBy { it.getId() ?: Int.MAX_VALUE }
        )
    }

    /** @return servedDishesCount**/
    private fun servingEvent(
        order: Order,
        group: EventCustomerGroup,
        finishedDishes: List<Dish>,
        eligibleWaiters: List<Waiter>,
        targetTableId: Int,
        loggers: FOHLogger?,
        notServedSink: MutableList<Triple<Int, Int, Int>>?
    ): Int {
        val dishesToServe = finishedDishes.sortedWith(
            compareBy<Dish> { !it.isBasic() }
                .thenBy { it.getRecipe().id }
        ).toMutableList()

        var servedDishesCount = 0
        var lastServingWaiterId = 0

        for (waiter in eligibleWaiters) {
            if (dishesToServe.isEmpty()) break

            val capacity = MAX_TICKLOAD - waiter.getTickLoad(WaiterAction.SERVING)
            if (capacity <= 0) continue

            val takeCount = minOf(capacity, dishesToServe.size)
            val serveByThisWaiter = dishesToServe.take(takeCount)

            assignWaiterIdIfNeeded(waiter, waiters)
            waiter.performAction(WaiterAction.SERVING, takeCount)

            val dishNameMap = dishLoop(serveByThisWaiter)

            repeat(takeCount) {
                dishesToServe.removeAt(0)
            }

            servedDishesCount += takeCount
            lastServingWaiterId = waiter.getId() ?: 0 // should never be 0 at all

            val ticksAfterOrdering = Ticks.getTick() - order.getOrderedTick()
            loggers?.loggerServing?.waitstaffServes(
                lastServingWaiterId,
                dishNameMap,
                targetTableId,
                ticksAfterOrdering
            )
        }
        if (servedDishesCount > 0) {
            group.receiveOrder()
        }
        if (dishesToServe.isNotEmpty()) {
            val loggingWaiterId = if (servedDishesCount > 0) {
                lastServingWaiterId
            } else {
                eligibleWaiters.firstOrNull()?.getId() ?: 0
            }
            recordNotServed(notServedSink, loggers, loggingWaiterId, dishesToServe.size, targetTableId)
        }
        return servedDishesCount
    }

    private fun dishLoop(serveByThisWaiter: List<Dish>): MutableMap<String, Int> {
        val dishNameMap = mutableMapOf<String, Int>()
        for (dish in serveByThisWaiter) {
            dish.setStatus(DishStatus.SERVED)
            val name = dish.getRecipe().dishName
            dishNameMap[name] = (dishNameMap[name] ?: 0) + 1
        }
        return dishNameMap
    }

    /** Ready meals at this waiter's own tables. An event table counts for nobody. */
    private fun getCookedMealsOfWaiter(waiter: Waiter): Int {
        var count = 0
        for (group in waiter.getCustomerGroups()) {
            if (group is EventCustomerGroup) continue
            count += group.order?.getDishes()?.count { it.getStatus() == DishStatus.FINISHED } ?: 0
        }
        return count
    }
}
