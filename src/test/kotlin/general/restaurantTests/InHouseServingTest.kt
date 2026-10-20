package general.restaurantTests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import saarlacarte.customer.CustomerGroup
import saarlacarte.customer.RegularCustomerGroup
import saarlacarte.enums.CookType
import saarlacarte.enums.DishStatus
import saarlacarte.enums.RestaurantType
import saarlacarte.enums.TableType
import saarlacarte.enums.WaiterAction
import saarlacarte.food.Recipe
import saarlacarte.orders.Dish
import saarlacarte.orders.Order
import saarlacarte.restaurant.InHouseServing
import saarlacarte.restaurant.Table
import saarlacarte.restaurant.Waiter
import saarlacarte.simulation.Ticks

// F19: FOH serving meals from the kitchen (spec p.15-16, p.34)
class InHouseServingTest {

    @BeforeEach
    fun setUp() {
        Ticks.init(70)
        Ticks.advanceEvening()
        Ticks.advanceTick()
    }

    private fun recipe(id: Int, name: String, basicFor: RestaurantType? = null) =
        Recipe(id, name, 10, mutableMapOf(), listOf(CookType.EXEC), basicFor)

    private fun regularGroup(id: Int, size: Int = 2) =
        RegularCustomerGroup(id, size, 1, emptyList(), TableType.COMMON, 1, 1, 1) as CustomerGroup

    private fun setup(group: CustomerGroup, order: Order): Pair<Waiter, InHouseServing> {
        val waiter = Waiter()
        waiter.addCustomerGroup(group)
        group.order = order
        val table = Table(1, TableType.COMMON, 2)
        val seatedGroups = mutableMapOf(group to listOf(table))
        return Pair(waiter, InHouseServing(listOf(waiter), seatedGroups))
    }

    @Test
    fun allDishesReadyAreServedImmediately() {
        val group = regularGroup(1)
        val dish1 = Dish(recipe(1, "Sushi"), DishStatus.FINISHED).apply { setFinishedTick(Ticks.getTick()) }
        val dish2 = Dish(recipe(2, "Pizza"), DishStatus.FINISHED).apply { setFinishedTick(Ticks.getTick()) }
        val order = Order(Order.nextId(), mutableListOf(dish1, dish2), false, null, Ticks.getTick())
        val (_, service) = setup(group, order)

        val served = service.serveInOrder(listOf(order), null)
        assertEquals(2, served)
        assertEquals(DishStatus.SERVED, dish1.getStatus())
        assertEquals(DishStatus.SERVED, dish2.getStatus())
    }

    @Test
    fun partiallyReadyTableWaitsThisAndTheFollowingTick() {
        val group = regularGroup(1)
        val ready = Dish(recipe(1, "Sushi"), DishStatus.FINISHED).apply { setFinishedTick(Ticks.getTick()) }
        val cooking = Dish(recipe(2, "Pizza"), DishStatus.NOTSTARTED)
        val order = Order(Order.nextId(), mutableListOf(ready, cooking), false, null, Ticks.getTick())
        val (_, service) = setup(group, order)

        // same tick the meal finished: must not be served yet
        val servedNow = service.serveInOrder(listOf(order), null)
        assertEquals(0, servedNow)

        // one tick later (still "the following tick"): still must not be served
        Ticks.advanceTick()
        val servedNextTick = service.serveInOrder(listOf(order), null)
        assertEquals(0, servedNextTick)
        assertEquals(DishStatus.FINISHED, ready.getStatus())
    }

    @Test
    fun partiallyReadyTableServesReadyDishesAfterTwoTicks() {
        val group = regularGroup(1)
        val ready = Dish(recipe(1, "Sushi"), DishStatus.FINISHED).apply { setFinishedTick(Ticks.getTick()) }
        val cooking = Dish(recipe(2, "Pizza"), DishStatus.NOTSTARTED)
        val order = Order(Order.nextId(), mutableListOf(ready, cooking), false, null, Ticks.getTick())
        val (_, service) = setup(group, order)

        Ticks.advanceTick()
        Ticks.advanceTick() // two ticks after the first meal finished

        val served = service.serveInOrder(listOf(order), null)
        assertEquals(1, served)
        assertEquals(DishStatus.SERVED, ready.getStatus())
        assertEquals(DishStatus.NOTSTARTED, cooking.getStatus())
    }

    @Test
    fun waiterAlreadyAtTheServingLimitCannotBeGivenMoreDishes() {
        val group = regularGroup(1)
        val dish = Dish(recipe(1, "Sushi"), DishStatus.FINISHED).apply { setFinishedTick(Ticks.getTick()) }
        val order = Order(Order.nextId(), mutableListOf(dish), false, null, Ticks.getTick())
        val (waiter, service) = setup(group, order)
        waiter.performAction(WaiterAction.SERVING, 10) // already maxed out this tick

        service.serveInOrder(listOf(order), null)

        // spec p.14: "Each waiter has a limit of 10 actions per type and tick" - a maxed-out
        // waiter must not be able to serve this dish this tick
        assertEquals(DishStatus.FINISHED, dish.getStatus())
    }

    @Test
    fun ordersWithReadyDishesFiltersOutOrdersWithNothingFinishedYet() {
        val readyOrder = Order(1, mutableListOf(Dish(recipe(1, "Sushi"), DishStatus.FINISHED)), false, null, 1)
        val notReadyOrder = Order(2, mutableListOf(Dish(recipe(2, "Pizza"), DishStatus.NOTSTARTED)), false, null, 1)
        val service = InHouseServing(emptyList(), mutableMapOf())

        val result = service.ordersWithReadyDishes(listOf(readyOrder, notReadyOrder))
        assertEquals(listOf(readyOrder), result)
    }

    @Test
    fun waiterAtPartialCapacityServesNoneIfTheTableDoesNotFit() {
        val group = regularGroup(1, size = 3)
        val dish1 = Dish(recipe(1, "Sushi"), DishStatus.FINISHED).apply { setFinishedTick(Ticks.getTick()) }
        val dish2 = Dish(recipe(2, "Pizza"), DishStatus.FINISHED).apply { setFinishedTick(Ticks.getTick()) }
        val dish3 = Dish(recipe(3, "Ramen"), DishStatus.FINISHED).apply { setFinishedTick(Ticks.getTick()) }
        val order = Order(Order.nextId(), mutableListOf(dish1, dish2, dish3), false, null, Ticks.getTick())
        val (waiter, service) = setup(group, order)
        waiter.performAction(WaiterAction.SERVING, 8)

        val served = service.serveInOrder(listOf(order), null)
        assertEquals(0, served)
        assertEquals(DishStatus.FINISHED, dish1.getStatus())
        assertEquals(DishStatus.FINISHED, dish2.getStatus())
        assertEquals(DishStatus.FINISHED, dish3.getStatus())

        // after holding for one tick, the waiter serves as many dishes as fit
        Ticks.advanceTick()
        waiter.resetTickLoad()
        waiter.performAction(WaiterAction.SERVING, 8)
        val servedAfterHold = service.serveInOrder(listOf(order), null)
        assertEquals(2, servedAfterHold)
    }

    @Test
    fun laterFinishedDishIsServedImmediatelyOnceServiceStarted() {
        val group = regularGroup(1)
        val first = Dish(recipe(1, "Sushi"), DishStatus.FINISHED).apply { setFinishedTick(1) }
        val later = Dish(recipe(2, "Pizza"), DishStatus.NOTSTARTED)
        val order = Order(Order.nextId(), mutableListOf(first, later), false, null, 1)
        val (_, service) = setup(group, order)

        Ticks.advanceTick()
        Ticks.advanceTick()
        val servedFirst = service.serveInOrder(listOf(order), null)
        assertEquals(1, servedFirst)

        later.setStatus(DishStatus.FINISHED)
        later.setFinishedTick(Ticks.getTick())
        val servedLater = service.serveInOrder(listOf(order), null)
        assertEquals(1, servedLater)
        assertEquals(DishStatus.SERVED, later.getStatus())
    }

    @Test
    fun noWaiterAssignedToGroupMeansNothingIsServed() {
        val group = regularGroup(1)
        val dish = Dish(recipe(1, "Sushi"), DishStatus.FINISHED).apply { setFinishedTick(Ticks.getTick()) }
        val order = Order(Order.nextId(), mutableListOf(dish), false, null, Ticks.getTick())
        group.order = order
        val table = Table(1, TableType.COMMON, 2)
        // group is seated but no waiter has it in their customer list
        val service = InHouseServing(listOf(Waiter()), mutableMapOf(group to listOf(table)))

        val served = service.serveInOrder(listOf(order), null)
        assertEquals(0, served)
        // assertEquals(0, waitstaff)
        assertTrue(dish.getStatus() != DishStatus.SERVED)
    }
}
