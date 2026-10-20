package general.restaurantTests

import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import saarlacarte.delivery.Driver
import saarlacarte.enums.DishStatus
import saarlacarte.food.Recipe
import saarlacarte.orders.Dish
import saarlacarte.orders.Order
import saarlacarte.restaurant.FrontOfHouse
import saarlacarte.restaurant.Waiter
import saarlacarte.simulation.Ticks
import kotlin.test.Test
import kotlin.test.assertEquals

class FOHDelivery {
    private lateinit var foh: FrontOfHouse
    private lateinit var waiter1: Waiter
    private lateinit var waiter2: Waiter
    private lateinit var driver: Driver
    private lateinit var recipe: Recipe

    @BeforeEach
    fun setUp() {
        Ticks.init(24)
        Ticks.advanceTick() // Current tick is 1

        waiter1 = Waiter()
        waiter2 = Waiter()
        driver = Driver()

        foh = FrontOfHouse(
            tables = emptyList(),
            waiters = mutableListOf(waiter1, waiter2),
            driverList = mutableListOf(driver),
            menu = mock()
        )

        recipe = mock<Recipe>()
        whenever(recipe.dishName).thenReturn("burger")
        whenever(recipe.id).thenReturn(1)
        whenever(recipe.basicDishFor).thenReturn(null)
    }

    @Test
    fun testDeliverFullyReadyOrderToFreeDriver() {
        // Setup: Order with 5 finished dishes, driver is FREE
        val dishes = MutableList(5) {
            Dish(recipe, DishStatus.FINISHED).apply { setFinishedTick(1) }
        }
        val order = Order(id = 1, dishes = dishes, delivery = true, deliveryDistance = 10, orderedTick = 1)
        driver.setFree()

        val (servedCount, waitersInvolved) = foh.serveDrivers(listOf(order))

        // Assert: All 5 dishes served by 1 waiter
        assertEquals(5, servedCount)
        assertEquals(1, waitersInvolved)
        assertEquals(order, driver.getOrder())
    }

    @Test
    fun testDoNotDeliverIncompleteOrder() {
        // Setup: Order with 4 finished dishes and 1 NOTSTARTED dish
        val dishes = mutableListOf<Dish>()
        repeat(4) { dishes.add(Dish(recipe, DishStatus.FINISHED).apply { setFinishedTick(1) }) }
        dishes.add(Dish(recipe, DishStatus.NOTSTARTED))

        val order = Order(id = 2, dishes = dishes, delivery = true, deliveryDistance = 10, orderedTick = 1)
        driver.setFree()

        val (servedCount, waitersInvolved) = foh.serveDrivers(listOf(order))

        // Assert: 0 dishes served because the order is not fully ready
        assertEquals(0, servedCount)
        assertEquals(0, waitersInvolved)
    }

    @Test
    fun testDeliverSplitAcrossWaiters() {
        // Setup: Order with 15 finished dishes (exceeds single waiter capacity of 10)
        val dishes = MutableList(15) {
            Dish(recipe, DishStatus.FINISHED).apply { setFinishedTick(1) }
        }
        val order = Order(id = 3, dishes = dishes, delivery = true, deliveryDistance = 10, orderedTick = 1)
        driver.setFree()

        val (servedCount, waitersInvolved) = foh.serveDrivers(listOf(order))

        // Assert: All 15 dishes served, split across 2 waiters
        assertEquals(15, servedCount)
        assertEquals(2, waitersInvolved)
    }

    @Test
    fun testDeliverSplitAcrossTicks() {
        // Setup: Order with 15 finished dishes, but ONLY 1 waiter is available
        foh = FrontOfHouse(
            tables = emptyList(),
            waiters = mutableListOf(waiter1), // Only 1 waiter (max capacity 10)
            driverList = mutableListOf(driver),
            menu = mock(),
        )
        val dishes = MutableList(15) {
            Dish(recipe, DishStatus.FINISHED).apply { setFinishedTick(1) }
        }
        val order = Order(id = 4, dishes = dishes, delivery = true, deliveryDistance = 10, orderedTick = 1)
        driver.setFree()

        // Tick 1: Serve maximum capacity
        val (servedCount1, waitersInvolved1) = foh.serveDrivers(listOf(order))
        assertEquals(10, servedCount1)
        assertEquals(1, waitersInvolved1)

        // Advance to next tick, reset waiter loads
        Ticks.advanceTick()
        waiter1.resetTickLoad()

        // Tick 2: Serve the remaining dishes
        val (servedCount2, waitersInvolved2) = foh.serveDrivers(listOf(order))
        assertEquals(5, servedCount2)
        assertEquals(1, waitersInvolved2)
    }
}
