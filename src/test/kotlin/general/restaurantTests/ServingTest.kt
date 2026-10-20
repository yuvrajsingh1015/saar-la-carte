package general.restaurantTests

import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import saarlacarte.customer.CasualCustomerGroup
import saarlacarte.enums.DishStatus
import saarlacarte.enums.TableType
import saarlacarte.food.Recipe
import saarlacarte.orders.Dish
import saarlacarte.orders.Order
import saarlacarte.restaurant.FrontOfHouse
import saarlacarte.restaurant.Table
import saarlacarte.restaurant.Waiter
import saarlacarte.simulation.Ticks
import kotlin.test.Test
import kotlin.test.assertEquals

class ServingTest {
    val waiter = Waiter()
    val table = Table(
        1,
        TableType.COMMON,
        7
    )
    val foh = FrontOfHouse(
        listOf(table),
        mutableListOf(waiter),
        null,
        mock()
    )
    val group: CasualCustomerGroup = mock()
    val dishes = mutableListOf<Dish>()
    val order = Order(1, dishes, false, null, 1)
    val recipe: Any = mock<Recipe>()

    @BeforeEach
    fun setUp() {
        Ticks.init(24)
        Ticks.advanceTick() // Current tick is 1
    }

    @Test
    fun testServe() {
        whenever(group.size).thenReturn(7)
        whenever(group.id).thenReturn(1)
        whenever(group.order).thenReturn(order)
        whenever(group.preferredTableType).thenReturn(TableType.COMMON)
        whenever(group.isArriving()).thenReturn(true)
        foh.seatCustomers(listOf(group))

        val recipe = mock<Recipe>()
        whenever(recipe.dishName).thenReturn("potato soup")
        whenever(recipe.id).thenReturn(1)

        repeat(7) {
            val dish = Dish(recipe = recipe, status = DishStatus.FINISHED, isBasic = true)
            dish.setFinishedTick(1)
            dishes.add(dish)
        }
        assertEquals(7, foh.serveTables(listOf(order)).first)
    }

    @Test
    fun testServeNotAllReadyHold() {
        whenever(group.size).thenReturn(2)
        whenever(group.id).thenReturn(1)
        whenever(group.order).thenReturn(order)
        whenever(group.preferredTableType).thenReturn(TableType.COMMON)
        whenever(group.isArriving()).thenReturn(true)
        foh.seatCustomers(listOf(group))

        val recipe = mock<Recipe>()
        whenever(recipe.dishName).thenReturn("potato soup")
        whenever(recipe.id).thenReturn(1)

        val dish1 = Dish(recipe = recipe, status = DishStatus.FINISHED, isBasic = true)
        dish1.setFinishedTick(1)
        val dish2 = Dish(recipe = recipe, status = DishStatus.INPROGRESS, isBasic = true)

        order.setDishes(mutableListOf(dish1, dish2))

        // Waiter should not serve because not all dishes are ready and wait time is not exceeded
        assertEquals(0, foh.serveTables(listOf(order)).first)
    }
}
