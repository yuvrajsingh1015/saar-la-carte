package general.integration

import saarlacarte.customer.CasualCustomerGroup
import saarlacarte.enums.CookType
import saarlacarte.enums.MeasuringUnit
import saarlacarte.enums.RatingLikelihood
import saarlacarte.enums.RestaurantType
import saarlacarte.enums.TableType
import saarlacarte.food.Ingredient
import saarlacarte.food.Recipe
import saarlacarte.restaurant.FrontOfHouse
import saarlacarte.restaurant.Menu
import saarlacarte.restaurant.Table
import saarlacarte.restaurant.Waiter
import saarlacarte.simulation.Supplier
import kotlin.Int

class OrderingTest {
    private val potato = Ingredient("potato", MeasuringUnit.X, 2)
    private val water = Ingredient("water", MeasuringUnit.ML, 2)
    private val potatoSoup = Recipe(
        3,
        "potato soup",
        20,
        mutableMapOf<Ingredient, Int>(potato to 4, water to 900),
        listOf(CookType.SAUCE)
    )
    val group = CasualCustomerGroup(
        1,
        4,
        1,
        listOf(),
        TableType.COMMON,
        listOf(RestaurantType.EUROPEAN),
        listOf(1),
        RatingLikelihood.ALWAYS
    )

    val supplier = Supplier(
        listOf(potato, water),
        mutableMapOf("potato" to 10, "water" to 1000)
    )
    val menu = Menu(supplier, listOf(potatoSoup), RestaurantType.EUROPEAN)
    val waiter = Waiter()
    val foh = FrontOfHouse(
        listOf(Table(1, TableType.COMMON, 4)),
        mutableListOf(waiter),
        null,
        menu
    )

    /*
    @Test
    fun onlyAvailable3Order4times() {
        menu.pantry.setSupplies(mutableMapOf(potato to 20, water to 3000))
        waiter.addCustomerGroup(group)
        val order = foh.takeOrders(listOf(group)).first()
        assertTrue(
            order.getDishes().size == 3 && order.getDishes().all { it.getRecipe().dishName == "potato soup" }
        )
    }
    */
}
