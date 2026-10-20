package general.integration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import saarlacarte.enums.CookType
import saarlacarte.enums.DishStatus
import saarlacarte.enums.RestaurantType
import saarlacarte.food.Recipe
import saarlacarte.orders.Dish
import saarlacarte.orders.Order
import saarlacarte.restaurant.Cook
import saarlacarte.restaurant.Kitchen
import saarlacarte.restaurant.Menu
import saarlacarte.simulation.Ticks
import kotlin.test.assertNull

/** tests for F10: Cooking - Order Queue **/
class CookingOrderQueueTest {
    private lateinit var kitchen: Kitchen

    private val basicRecipe1 = Recipe(
        1,
        "potato Soup",
        11, // 1 tick
        mutableMapOf(),
        listOf(CookType.TOURNANT, CookType.EXEC, CookType.VEGETABLE),
        RestaurantType.EUROPEAN
    )

    private val nonBasicRecipe2 = Recipe(
        2,
        "beef Pasta",
        11, // 1 tick
        mutableMapOf(),
        listOf(CookType.TOURNANT),
        null
    )

    private val nonBasicRecipe3 = Recipe(
        3,
        "chicken Rice",
        11, // 1 tick
        mutableMapOf(),
        listOf(CookType.TOURNANT),
        null
    )

    private val nonBasicRecipe4 = Recipe(
        4,
        "salad",
        11, // 1 tick
        mutableMapOf(),
        listOf(CookType.VEGETABLE, CookType.TOURNANT),
        null
    )

    private val menu = Menu(
        mock(),
        mutableListOf(basicRecipe1, nonBasicRecipe2, nonBasicRecipe3, nonBasicRecipe4),
        RestaurantType.EUROPEAN,
    )

    @BeforeEach
    fun setUp() {
        Ticks.init(24)
        Ticks.advanceEvening()
        Ticks.advanceTick()
    }

    @Test
    fun `test - oldest order has higher priority in the queue`() {
        val dish1 = Dish(nonBasicRecipe3, DishStatus.NOTSTARTED)
        val order1 = Order(
            1,
            mutableListOf(dish1),
            false,
            null,
            1
        )

        val dish2 = Dish(basicRecipe1, DishStatus.NOTSTARTED, true)
        val order2 = Order(
            2,
            mutableListOf(dish2),
            false,
            null,
            1
        )

        val cook = Cook(CookType.TOURNANT)
        kitchen = Kitchen(mutableListOf(cook), menu)

        kitchen.registerOrders(listOf(order1, order2))
        kitchen.assignAndCook()

        assertEquals(nonBasicRecipe3, cook.getCurrentRecipe())
        assertEquals(DishStatus.INPROGRESS, dish1.getStatus())
        assertEquals(DishStatus.NOTSTARTED, dish2.getStatus())
    }

    @Test
    fun `test - basic dish has higher priority in the order`() {
        val nonBasicDish = Dish(nonBasicRecipe2, DishStatus.NOTSTARTED)
        val basicDish = Dish(basicRecipe1, DishStatus.NOTSTARTED, true)
        val order = Order(
            1,
            mutableListOf(nonBasicDish, basicDish),
            false,
            null,
            1
        )

        val cook = Cook(CookType.TOURNANT)
        kitchen = Kitchen(mutableListOf(cook), menu)

        kitchen.registerOrders(listOf(order))
        kitchen.assignAndCook()

        assertEquals(basicRecipe1, cook.getCurrentRecipe())
        assertEquals(DishStatus.INPROGRESS, basicDish.getStatus())
        assertEquals(DishStatus.NOTSTARTED, nonBasicDish.getStatus())
    }

    @Test
    fun `test - order queue tiebreaker lowest id wins`() {
        val dishHighId = Dish(nonBasicRecipe3, DishStatus.NOTSTARTED)
        val dishLowId = Dish(nonBasicRecipe2, DishStatus.NOTSTARTED)
        val order = Order(
            1,
            mutableListOf(dishHighId, dishLowId),
            false,
            null,
            1
        )

        val cook = Cook(CookType.TOURNANT)
        kitchen = Kitchen(cooks = mutableListOf(cook), menu = menu)

        kitchen.registerOrders(listOf(order))
        kitchen.assignAndCook()

        assertEquals(nonBasicRecipe2, cook.getCurrentRecipe())
        assertEquals(DishStatus.INPROGRESS, dishLowId.getStatus())
        assertEquals(DishStatus.NOTSTARTED, dishHighId.getStatus())
    }

    @Test
    fun `cook prepares dish for all queued orders containing it`() {
        val dishOrder1 = Dish(basicRecipe1, DishStatus.NOTSTARTED, true)
        val order1 = Order(
            1,
            mutableListOf(dishOrder1),
            false,
            null,
            1
        )

        val dishOrder2 = Dish(basicRecipe1, DishStatus.NOTSTARTED, true)
        val order2 = Order(
            2,
            mutableListOf(dishOrder2),
            false,
            null,
            1
        )

        val cook = Cook(CookType.TOURNANT)
        kitchen = Kitchen(cooks = mutableListOf(cook), menu = menu)

        kitchen.registerOrders(listOf(order1, order2))
        kitchen.assignAndCook()

        assertEquals(DishStatus.INPROGRESS, dishOrder1.getStatus())
        assertEquals(DishStatus.INPROGRESS, dishOrder2.getStatus())
        assertEquals(2, cook.getMealsNumber())
    }

    @Test
    fun `lowest-ranking eligible cook type is chosen first`() {
        val dish = Dish(basicRecipe1, DishStatus.NOTSTARTED, true)
        val order = Order(
            1,
            mutableListOf(dish),
            false,
            null,
            1
        )

        val execCook = Cook(CookType.EXEC)
        val tournantCook = Cook(CookType.TOURNANT)
        val vegetableCook = Cook(CookType.VEGETABLE) // lowest rank among eligible

        kitchen = Kitchen(mutableListOf(execCook, tournantCook, vegetableCook), menu)

        kitchen.registerOrders(listOf(order))
        kitchen.assignAndCook()

        assertTrue(execCook.isFree())
        assertTrue(tournantCook.isFree())
        assertFalse(vegetableCook.isFree())
    }

    @Test
    fun `test - dishes remain NOTSTARTED when no eligible cook is available`() {
        // Recipe 2 can only be cooked by TOURNANT
        val dish = Dish(nonBasicRecipe2, DishStatus.NOTSTARTED)
        val order = Order(1, mutableListOf(dish), false, null, 1)

        // Only an EXEC cook is in the kitchen (not eligible for recipe 2)
        val execCook = Cook(CookType.EXEC)
        kitchen = Kitchen(mutableListOf(execCook), menu)

        kitchen.registerOrders(listOf(order))
        kitchen.assignAndCook()

        assertTrue(execCook.isFree())
        assertEquals(DishStatus.NOTSTARTED, dish.getStatus())
    }

    @Test
    fun `test - multiple cooks take distinct pending recipes in priority order`() {
        // Order 1 contains basicRecipe1 (basic) and nonBasicRecipe2 (non-basic)
        val dishBasic = Dish(basicRecipe1, DishStatus.NOTSTARTED, true)
        val dishNonBasic = Dish(nonBasicRecipe2, DishStatus.NOTSTARTED, false)
        val order = Order(1, mutableListOf(dishBasic, dishNonBasic), false, null, 1)

        val cook1 = Cook(CookType.TOURNANT)
        val cook2 = Cook(CookType.TOURNANT)
        kitchen = Kitchen(mutableListOf(cook1, cook2), menu)

        kitchen.registerOrders(listOf(order))
        kitchen.assignAndCook()

        // Both dishes should now be assigned and cooked
        assertEquals(DishStatus.INPROGRESS, dishBasic.getStatus())
        assertEquals(DishStatus.INPROGRESS, dishNonBasic.getStatus())
        assertFalse(cook1.isFree())
        assertFalse(cook2.isFree())

        // cook1 and cook2 must have picked different recipes
        val recipesAssigned = setOf(cook1.getCurrentRecipe(), cook2.getCurrentRecipe())
        assertTrue(recipesAssigned.contains(basicRecipe1))
        assertTrue(recipesAssigned.contains(nonBasicRecipe2))
    }

    @Test
    fun `test - registering the same order twice is ignored without duplicating meals`() {
        val dish = Dish(basicRecipe1, DishStatus.NOTSTARTED, true)
        val order = Order(1, mutableListOf(dish), false, null, 1)

        val cook = Cook(CookType.TOURNANT)
        kitchen = Kitchen(mutableListOf(cook), menu)

        kitchen.registerOrders(listOf(order))
        kitchen.registerOrders(listOf(order)) // duplicate registration

        kitchen.assignAndCook()

        // Only 1 meal should be cooked, not 2
        assertEquals(1, cook.getMealsNumber())
    }

    @Test
    fun `test - cooks remain free when all dishes are already in progress or finished`() {
        val dishInProgress = Dish(basicRecipe1, DishStatus.INPROGRESS, true)
        val dishFinished = Dish(nonBasicRecipe2, DishStatus.FINISHED, false)
        val order = Order(1, mutableListOf(dishInProgress, dishFinished), false, null, 1)

        val cook = Cook(CookType.TOURNANT)
        kitchen = Kitchen(mutableListOf(cook), menu)

        kitchen.registerOrders(listOf(order))
        kitchen.assignAndCook()

        // Cook should not be assigned anything since no dishes are NOTSTARTED
        assertTrue(cook.isFree())
        assertNull(cook.getCurrentRecipe())
    }

    @Test
    fun `test - oldest order takes precedence even if newer order has lower recipe id`() {
        // Order 1 contains higher recipe id (recipe 3)
        val dishOrder1 = Dish(nonBasicRecipe3, DishStatus.NOTSTARTED, false)
        val order1 = Order(1, mutableListOf(dishOrder1), false, null, 1)

        // Order 2 contains lower recipe id (recipe 2)
        val dishOrder2 = Dish(nonBasicRecipe2, DishStatus.NOTSTARTED, false)
        val order2 = Order(2, mutableListOf(dishOrder2), false, null, 1)

        val cook = Cook(CookType.TOURNANT)
        kitchen = Kitchen(mutableListOf(cook), menu)

        kitchen.registerOrders(listOf(order1, order2))
        kitchen.assignAndCook()

        // Order 1's dish must be cooked first despite having a higher recipe ID
        assertEquals(nonBasicRecipe3, cook.getCurrentRecipe())
        assertEquals(DishStatus.INPROGRESS, dishOrder1.getStatus())
        assertEquals(DishStatus.NOTSTARTED, dishOrder2.getStatus())
    }
}
