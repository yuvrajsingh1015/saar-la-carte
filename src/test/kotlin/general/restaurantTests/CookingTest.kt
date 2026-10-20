package general.restaurantTests

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import saarlacarte.enums.CookType
import saarlacarte.enums.DishStatus
import saarlacarte.enums.MeasuringUnit
import saarlacarte.enums.RestaurantType
import saarlacarte.food.Ingredient
import saarlacarte.food.Recipe
import saarlacarte.loggers.CookLogger
import saarlacarte.loggers.KitchenLogger
import saarlacarte.orders.Dish
import saarlacarte.orders.Order
import saarlacarte.restaurant.Cook
import saarlacarte.restaurant.Kitchen
import saarlacarte.restaurant.Menu
import saarlacarte.simulation.Supplier
import saarlacarte.simulation.Ticks
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CookingTest {
    // ingredients
    private lateinit var chicken: Ingredient
    private lateinit var rice: Ingredient
    private lateinit var beef: Ingredient
    private lateinit var pasta: Ingredient
    private lateinit var potato: Ingredient
    private lateinit var onion: Ingredient
    private lateinit var oil: Ingredient

    // recipes
    private lateinit var chickenRice: Recipe
    private lateinit var beefPasta: Recipe
    private lateinit var potatoSoup: Recipe

    // loggers
    private lateinit var mockCookLogger: CookLogger
    private lateinit var mockKitchenLogger: KitchenLogger

    @BeforeEach
    fun setUp() {
        Ticks.init(24)
        Ticks.advanceEvening()
        Ticks.advanceTick()

        chicken = Ingredient("chicken", MeasuringUnit.G, 1)
        rice = Ingredient("rice", MeasuringUnit.G, 3)
        beef = Ingredient("beef", MeasuringUnit.G, 1)
        pasta = Ingredient("pasta", MeasuringUnit.G, 3)
        potato = Ingredient("potato", MeasuringUnit.G, 1)
        onion = Ingredient("onion", MeasuringUnit.X, 1)
        oil = Ingredient("oil", MeasuringUnit.ML, 1)

        chickenRice = Recipe(
            1, "chickenRice", 30,
            mutableMapOf(chicken to 100, rice to 200, oil to 20),
            listOf(
                CookType.EXEC,
                CookType.SOUS,
                CookType.TOURNANT,
                CookType.ROAST
            ),
            RestaurantType.ASIAN
        )
        beefPasta = Recipe(
            2,
            "beefPasta",
            25,
            mutableMapOf(
                beef to 150,
                pasta to 200,
                oil to 20
            ),
            listOf(
                CookType.EXEC,
                CookType.SOUS,
                CookType.TOURNANT,
                CookType.ROAST
            )
        )
        potatoSoup = Recipe(
            3,
            "potatoSoup",
            10,
            mutableMapOf(
                potato to 300,
                onion to 1,
                oil to 20
            ),
            listOf(
                CookType.EXEC,
                CookType.SOUS,
                CookType.TOURNANT,
                CookType.VEGETABLE
            ),
            RestaurantType.EUROPEAN
        )

        mockCookLogger = mock()
        mockKitchenLogger = mock()
    }

    private fun createKitchen(
        cooks: MutableList<Cook>,
        recipes: MutableList<Recipe>,
        type: RestaurantType = RestaurantType.EUROPEAN
    ): Kitchen {
        val supplier = Supplier(
            listOf(chicken, rice, beef, pasta, potato, onion, oil),
            mutableMapOf(
                "chicken" to 1000,
                "rice" to 1000,
                "beef" to 1000,
                "pasta" to 1000,
                "potato" to 1000,
                "onion" to 100,
                "oil" to 500
            )
        )
        val menu = Menu(supplier, recipes, type)
        val kitchen = Kitchen(cooks, menu)
        kitchen.assignLoggers(mockKitchenLogger, mockCookLogger)
        return kitchen
    }

    // for all the cookType, just write the enum. do not want to write cook after each
    @Test
    fun randomCookCantCook() {
        val pastry = Cook(CookType.PASTRY)
        val kitchen = createKitchen(mutableListOf(pastry), mutableListOf(chickenRice), RestaurantType.ASIAN)

        val order = Order(
            1,
            mutableListOf(Dish(chickenRice, DishStatus.NOTSTARTED, true)),
            false,
            null,
            Ticks.getTick()
        )
        kitchen.registerOrders(listOf(order))
        kitchen.assignAndCook()

        val finishedMeals = kitchen.assignAndCook()

        assertEquals(0, finishedMeals)
        assertEquals(DishStatus.NOTSTARTED, order.getDishes().first().getStatus())
        assertTrue(pastry.isFree())
        verify(mockCookLogger, never()).cookStartCooking(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun lowRankCooks() {
        val exec = Cook(CookType.EXEC)
        val sous = Cook(CookType.SOUS)
        val tournant = Cook(CookType.TOURNANT)

        val kitchen = createKitchen(
            mutableListOf(exec, sous, tournant),
            mutableListOf(chickenRice),
            RestaurantType.ASIAN
        )

        val order = Order(
            1,
            mutableListOf(Dish(chickenRice, DishStatus.NOTSTARTED, true)),
            false,
            null,
            Ticks.getTick()
        )
        kitchen.registerOrders(listOf(order))
        kitchen.assignAndCook()

        assertEquals(1, tournant.getId())
        assertNull(sous.getId())
        assertNull(exec.getId())
        verify(mockCookLogger).cookStartCooking(1, "TOURNANT", 1, "chickenRice", 1, "1")
    }

    @Test
    fun cookSameDishesAcrossDifferentOrders() {
        val tournant = Cook(CookType.TOURNANT)

        val kitchen = createKitchen(
            mutableListOf(tournant),
            mutableListOf(chickenRice),
            RestaurantType.ASIAN
        )

        val order1 = Order(
            1,
            mutableListOf(Dish(chickenRice, DishStatus.NOTSTARTED, true)),
            false,
            null,
            Ticks.getTick()
        )
        val order2 = Order(
            2,
            mutableListOf(Dish(chickenRice, DishStatus.NOTSTARTED, true)),
            false,
            null,
            Ticks.getTick()
        )
        kitchen.registerOrders(listOf(order1, order2))
        kitchen.assignAndCook()

        assertEquals(1, tournant.getId())
        verify(mockCookLogger).cookStartCooking(1, "TOURNANT", 2, "chickenRice", 1, "1,2")
    }

    @Test
    fun cookTakesAppropriateTicksToCook() {
        val tournant = Cook(CookType.TOURNANT)

        val kitchen = createKitchen(
            mutableListOf(tournant),
            mutableListOf(chickenRice),
            RestaurantType.ASIAN
        )

        val order = Order(
            1,
            mutableListOf(Dish(chickenRice, DishStatus.NOTSTARTED, true)),
            false,
            null,
            Ticks.getTick()
        )
        // chickenRice takes 3 ticks to be done

        kitchen.registerOrders(listOf(order))

        val tick1Done = kitchen.assignAndCook()

        assertEquals(0, tick1Done)
        assertEquals(DishStatus.INPROGRESS, order.getDishes().first().getStatus())
        assertFalse(tournant.isFree())

        Ticks.advanceTick()
        val tick2Done = kitchen.assignAndCook()
        assertEquals(0, tick2Done)
        assertFalse(tournant.isFree())

        Ticks.advanceTick()
        val tick3Done = kitchen.assignAndCook()
        assertEquals(1, tick3Done)
        assertEquals(DishStatus.FINISHED, order.getDishes().first().getStatus())
        assertTrue(tournant.isFree())

        verify(mockCookLogger).mealCooked(1, 1, "chickenRice", 2)
    }

    @Test
    fun abortMealsOfOrder() {
        val exec = Cook(CookType.EXEC)
        val kitchen = createKitchen(mutableListOf(exec), mutableListOf(beefPasta))

        val order = Order(
            1,
            mutableListOf(Dish(beefPasta, DishStatus.NOTSTARTED, true)),
            false,
            null,
            Ticks.getTick()
        )
        kitchen.registerOrders(listOf(order))
        kitchen.assignAndCook()

        assertFalse(exec.isFree())

        kitchen.abortMealsOf(order)

        assertTrue(exec.isFree())
        assertEquals(0, exec.getMealsNumber())
    }

    @Test
    fun resetCooksForNextEvening() {
        val exec = Cook(CookType.EXEC)
        val kitchen = createKitchen(mutableListOf(exec), mutableListOf(beefPasta))

        val order = Order(
            1,
            mutableListOf(Dish(beefPasta, DishStatus.NOTSTARTED, true)),
            false,
            null,
            Ticks.getTick()
        )
        kitchen.registerOrders(listOf(order))
        kitchen.assignAndCook()

        assertEquals(1, exec.getId())

        kitchen.resetForNextEvening()

        assertNull(exec.getId())
        assertTrue(exec.isFree())
    }
}
