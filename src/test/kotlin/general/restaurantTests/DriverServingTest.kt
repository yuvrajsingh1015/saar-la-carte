package general.restaurantTests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import saarlacarte.delivery.Driver
import saarlacarte.enums.CookType
import saarlacarte.enums.DishStatus
import saarlacarte.food.Recipe
import saarlacarte.orders.Dish
import saarlacarte.orders.Order
import saarlacarte.restaurant.DriverServing
import saarlacarte.restaurant.Waiter

class DriverServingTest {

    private fun recipe(id: Int, name: String) =
        Recipe(id, name, 10, mutableMapOf(), listOf(CookType.EXEC))

    @Test
    fun doesNotHandDishesUntilTheWholeOrderIsCooked() {
        val finished = Dish(recipe(1, "Pasta"), DishStatus.FINISHED)
        val cooking = Dish(recipe(2, "Stew"), DishStatus.INPROGRESS)
        val order = Order(1, mutableListOf(finished, cooking), true, 5, 1)
        val driver = Driver()
        val serving = DriverServing(mutableListOf(Waiter()), mutableListOf(driver))

        val (meals, _) = serving.serveDrivers(listOf(order), null)
        assertEquals(0, meals)
        assertEquals(DishStatus.FINISHED, finished.getStatus())
        assertNull(driver.getOrder())
    }

    @Test
    fun handsFinishedDishesOnceEveryMealIsCooked() {
        val pasta = Dish(recipe(1, "Pasta"), DishStatus.FINISHED)
        val stew = Dish(recipe(2, "Stew"), DishStatus.FINISHED)
        val order = Order(1, mutableListOf(pasta, stew), true, 5, 1)
        val driver = Driver()
        val serving = DriverServing(mutableListOf(Waiter()), mutableListOf(driver))

        val (meals, waiters) = serving.serveDrivers(listOf(order), null)
        assertEquals(2, meals)
        assertEquals(1, waiters)
        assertEquals(DishStatus.SERVED, pasta.getStatus())
        assertEquals(DishStatus.SERVED, stew.getStatus())
        assertEquals(order, driver.getOrder())
    }

    @Test
    fun laterOrderStillGetsItsDriverWhenAnEarlierOrderHasNoneFree() {
        val waiting = Dish(recipe(1, "Pasta"), DishStatus.FINISHED)
        val leftover = Dish(recipe(2, "Stew"), DishStatus.FINISHED)
        val orderNeedingDriver = Order(1, mutableListOf(waiting), true, 5, 1)
        val orderAlreadyAssigned = Order(2, mutableListOf(leftover), true, 5, 1)
        val busy = Driver().apply { setOrder(orderAlreadyAssigned) }
        val serving = DriverServing(mutableListOf(Waiter()), mutableListOf(busy))

        val (meals, _) = serving.serveDrivers(listOf(orderNeedingDriver, orderAlreadyAssigned), null)
        assertEquals(1, meals)
        assertEquals(DishStatus.SERVED, leftover.getStatus())
        assertEquals(DishStatus.FINISHED, waiting.getStatus())
    }
}
