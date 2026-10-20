package saarlacarte.restaurant

import saarlacarte.delivery.Driver
import saarlacarte.enums.DishStatus
import saarlacarte.enums.DriverState
import saarlacarte.enums.WaiterAction
import saarlacarte.orders.Dish
import saarlacarte.orders.Order

private const val MAX_SERVING_LOAD = 10

/**
 * Hands cooked dishes to waiting drivers, and forwards the delivery tick to DeliveryService.
 * Split out of FrontOfHouse to keep it under detekt's LargeClass/TooManyFunctions limits.
 */
class DriverServing(
    private val waiters: MutableList<Waiter>,
    private val driversList: MutableList<Driver>?
) {
    /** give out dishes to the drivers **/
    /** returns <meals handed, total involved waitstaff number> **/
    fun serveDrivers(orders: List<Order>, loggers: FOHLogger?): Pair<Int, Int> {
        var totalMealsHandedToDrivers = 0
        var totalWaitstaffInvolved = 0

        // spec: meals queue until every dish is cooked AND a driver is free
        val readyDeliveryOrders = orders.filter { order ->
            order.isDelivery() && isFullyCooked(order) && getUnservedDishesSorted(order).isNotEmpty()
        }.sortedBy { it.getId() }

        for (order in readyDeliveryOrders) {
            // if a driver has been partially served, get him
            // if not, assign a new driver
            var driver = driversList?.find { it.getOrder() == order }
            if (driver == null) {
                driver = driversList?.find {
                    it.getState() == DriverState.FREE && it.getOrder() == null
                } ?: continue
            }

            if (driver.getOrder() == null) {
                driver.setOrder(order)
            }

            val pendingDishes = getUnservedDishesSorted(order)
            if (pendingDishes.isNotEmpty()) {
                val (meals, waitersInvolvedNum) = serveOrderToDriver(
                    order,
                    driver,
                    pendingDishes,
                    driversList ?: emptyList(),
                    loggers
                )
                totalMealsHandedToDrivers += meals
                totalWaitstaffInvolved += waitersInvolvedNum
            }
        }
        return Pair(totalMealsHandedToDrivers, totalWaitstaffInvolved)
    }

    /**
     * hands as many of pending dishes of a particular order to driver
     * as this tick's waiter capacity allows
     * returns <meals handed, total involved waitstaff number for this particular order>
     */
    private fun serveOrderToDriver(
        order: Order,
        driver: Driver,
        pendingDishes: List<Dish>,
        driversList: List<Driver>,
        loggers: FOHLogger?
    ): Pair<Int, Int> {
        var mealsHanded = 0
        var involvedWaiters = 0
        // already-active waiters (lowest id) hand out first; unused null-id waiters go last
        val sortedWaiters = waiters.sortedBy { it.getId() ?: Int.MAX_VALUE }
        var dishIndex = 0

        for (waiter in sortedWaiters) {
            if (dishIndex >= pendingDishes.size) break

            val remainingLoad = MAX_SERVING_LOAD - waiter.getTickLoad(WaiterAction.SERVING)

            if (remainingLoad <= 0) continue

            val takeCount = minOf(remainingLoad, pendingDishes.size - dishIndex)

            assignWaiterIdIfNeeded(waiter, waiters)

            if (driver.id == null) {
                val highestDriverId = driversList.mapNotNull { it.id }.maxOrNull() ?: 0
                driver.id = highestDriverId + 1
            }

            waiter.performAction(WaiterAction.SERVING, takeCount)
            involvedWaiters++

            val dishesHandedByWaiter = mutableListOf<Dish>()
            for (i in 0 until takeCount) {
                val dish = pendingDishes[dishIndex + i]
                dish.setStatus(DishStatus.SERVED)
                dishesHandedByWaiter.add(dish)
            }

            val waiterId = waiter.getId() ?: 1
            val driverId = driver.id ?: 1

            val dishMap = formatDishMap(dishesHandedByWaiter)

            // the logger had to do marker
            loggers?.loggerServing?.serveToDriver(
                waiterId,
                dishMap,
                driverId,
                order.getId()
            )
            dishIndex += takeCount
            mealsHanded += takeCount
        }
        return Pair(mealsHanded, involvedWaiters)
    }

    /** True once every dish is cooked (FINISHED) or already handed (SERVED). */
    private fun isFullyCooked(order: Order): Boolean {
        val dishes = order.getDishes()
        return dishes.isNotEmpty() && dishes.all {
            it.getStatus() == DishStatus.FINISHED || it.getStatus() == DishStatus.SERVED
        }
    }

    /**
     * finished dishes that have not been handed to a driver yet
     * ordered by basic first, then by ascending id
     */
    private fun getUnservedDishesSorted(order: Order): List<Dish> {
        return order.getDishes().filter { it.getStatus() == DishStatus.FINISHED }.sortedWith(
            compareByDescending<Dish> { it.isBasic() }
                .thenBy { it.getRecipe().id }
        )
    }

    // potatoSoup:2,chickenRice:1
    /** counts the number of repeated dish in the dishes list, sort it by the dishName **/
    private fun formatDishMap(dishes: List<Dish>): MutableMap<String, Int> {
        val counts = mutableMapOf<String, Int>()
        for (dish in dishes) {
            val name = dish.getRecipe().dishName
            counts[name] = (counts[name] ?: 0) + 1
        }
        // sortedMap as name says sorts the map based on key
        return counts
    }
}
