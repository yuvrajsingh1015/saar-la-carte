package general.integration

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
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

/**
 * Kitchen + Cook cook a real order; loggers are mocked.
 */
class KitchenCookingTest {
    private val rice = Ingredient("rice", MeasuringUnit.G, 3)
    private val potato = Ingredient("potato", MeasuringUnit.G, 3)
    private val riceBowl = Recipe(
        1,
        "Rice Bowl",
        10,
        mutableMapOf(rice to 50),
        listOf(CookType.EXEC),
        RestaurantType.ASIAN
    )
    private val potatoSoup = Recipe(
        2,
        "Potato Soup",
        10,
        mutableMapOf(potato to 40),
        listOf(CookType.EXEC, CookType.TOURNANT),
        RestaurantType.EUROPEAN
    )
    private val slowRice = Recipe(
        3,
        "Rice Bowl",
        30,
        mutableMapOf(rice to 50),
        listOf(CookType.ROAST, CookType.EXEC)
    )

    @BeforeEach
    fun startEveningTickOne() {
        Ticks.init(24)
        Ticks.advanceEvening()
        Ticks.advanceTick()
    }

    @AfterEach
    fun resetTicks() {
        Ticks.init(1)
    }

    @Test
    fun durationTenFinishesSameTickAndLogs() {
        val cookLogger = mock<CookLogger>()
        val kitchenLogger = mock<KitchenLogger>()
        val cook = Cook(CookType.EXEC)
        val kitchen = kitchenWith(mutableListOf(cook), cookLogger, kitchenLogger)
        val order = twoBowls()
        kitchen.registerOrders(listOf(order))

        val finished = kitchen.assignAndCook()

        assertEquals(2, finished)
        assertTrue(order.getDishes().all { it.getStatus() == DishStatus.FINISHED })
        assertTrue(cook.isFree())
        verify(
            cookLogger
        ).cookStartCooking(eq(1), eq("EXEC"), eq(2), eq("Rice Bowl"), eq(order.getId()), eq("1"))
        verify(cookLogger).mealCooked(eq(1), eq(2), eq("Rice Bowl"), eq(0))
        verify(kitchenLogger).kitchenStatus(eq(1), eq(2), eq(2), eq(2))
    }

    @Test
    fun ineligibleCookDoesNotStartTheJob() {
        val cookLogger = mock<CookLogger>()
        val kitchenLogger = mock<KitchenLogger>()
        val kitchen = kitchenWith(mutableListOf(Cook(CookType.PASTRY)), cookLogger, kitchenLogger)
        val order = twoBowls()
        kitchen.registerOrders(listOf(order))

        val finished = kitchen.assignAndCook()

        assertEquals(0, finished)
        assertTrue(order.getDishes().all { it.getStatus() == DishStatus.NOTSTARTED })
        verify(cookLogger, never()).cookStartCooking(any(), any(), any(), any(), any(), any())
        verify(cookLogger, never()).mealCooked(any(), any(), any(), any())
        verify(kitchenLogger).kitchenStatus(eq(0), eq(0), eq(0), eq(0))
    }

    @Test
    fun mixedOrderFinishesOnDifferentTicksWithTwoCookTypes() {
        val cookLogger = mock<CookLogger>()
        val kitchenLogger = mock<KitchenLogger>()
        val exec = Cook(CookType.EXEC)
        val roast = Cook(CookType.ROAST)
        val kitchen = kitchenWith(
            mutableListOf(exec, roast),
            cookLogger,
            kitchenLogger,
            listOf(potatoSoup, slowRice),
            listOf(potato, rice)
        )
        val order = Order(
            1,
            mutableListOf(
                Dish(potatoSoup, DishStatus.NOTSTARTED, true),
                Dish(potatoSoup, DishStatus.NOTSTARTED, true),
                Dish(potatoSoup, DishStatus.NOTSTARTED, true),
                Dish(slowRice, DishStatus.NOTSTARTED, false),
                Dish(slowRice, DishStatus.NOTSTARTED, false)
            ),
            false,
            null,
            Ticks.getTick()
        )
        kitchen.registerOrders(listOf(order))

        assertEquals(3, kitchen.assignAndCook())
        assertEquals(3, order.getDishes().count { it.getStatus() == DishStatus.FINISHED })
        assertEquals(2, order.getDishes().count { it.getStatus() == DishStatus.INPROGRESS })
        assertTrue(exec.isFree())
        assertTrue(!roast.isFree())
        verify(cookLogger).cookStartCooking(
            eq(1),
            eq("EXEC"),
            eq(3),
            eq("Potato Soup"),
            eq(1),
            eq("1")
        )
        verify(cookLogger).cookStartCooking(eq(2), eq("ROAST"), eq(2), eq("Rice Bowl"), eq(1), eq("1"))
        verify(cookLogger).mealCooked(eq(1), eq(3), eq("Potato Soup"), eq(0))

        Ticks.advanceTick()
        assertEquals(0, kitchen.assignAndCook())
        assertEquals(2, order.getDishes().count { it.getStatus() == DishStatus.INPROGRESS })

        Ticks.advanceTick()
        assertEquals(2, kitchen.assignAndCook())
        assertTrue(order.getDishes().all { it.getStatus() == DishStatus.FINISHED })
        assertTrue(roast.isFree())
        verify(cookLogger).mealCooked(eq(2), eq(2), eq("Rice Bowl"), eq(2))
        verify(kitchenLogger).kitchenStatus(eq(2), eq(5), eq(3), eq(3))
        verify(kitchenLogger).kitchenStatus(eq(1), eq(2), eq(2), eq(5))
    }

    private fun kitchenWith(
        cooks: MutableList<Cook>,
        cookLogger: CookLogger,
        kitchenLogger: KitchenLogger,
        recipes: List<Recipe> = listOf(riceBowl),
        ingredients: List<Ingredient> = listOf(rice)
    ): Kitchen {
        val volumes = ingredients.associate { it.getName() to 100 }.toMutableMap()
        val kitchen = Kitchen(cooks, Menu(Supplier(ingredients, volumes), recipes, RestaurantType.ASIAN))
        kitchen.assignLoggers(kitchenLogger, cookLogger)
        return kitchen
    }

    private fun twoBowls(): Order = orderOf(1, riceBowl, 2)

    private fun orderOf(id: Int, recipe: Recipe, meals: Int): Order {
        val dishes = MutableList(meals) { Dish(recipe, DishStatus.NOTSTARTED, true) }
        return Order(id, dishes, false, null, Ticks.getTick())
    }
}
