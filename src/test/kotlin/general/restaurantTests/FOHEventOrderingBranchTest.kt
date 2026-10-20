package general.restaurantTests

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import saarlacarte.customer.EventCustomerGroup
import saarlacarte.customer.FoodPreferences
import saarlacarte.enums.CookType
import saarlacarte.enums.MeasuringUnit
import saarlacarte.enums.RestaurantType
import saarlacarte.enums.TableType
import saarlacarte.food.Ingredient
import saarlacarte.food.Recipe
import saarlacarte.loggers.FOHSeatingLogger
import saarlacarte.restaurant.FOHLogger
import saarlacarte.restaurant.FrontOfHouse
import saarlacarte.restaurant.Menu
import saarlacarte.restaurant.Table
import saarlacarte.restaurant.Waiter
import saarlacarte.simulation.Supplier
import saarlacarte.simulation.Ticks
import kotlin.test.Test
import kotlin.test.assertNull

/**
 * F18: FOH - Ordering.
 *
 * FrontOfHouse.handleEventOrdering starts with
 * `if (orderedCount == 0) return Triple(null, 0, 0)`: if none of an event group's customers
 * can find a dish they are willing to eat, nothing is ordered and nothing should be logged.
 * The existing FOHOrderingTest.kt only exercises event orderings where at least one dish is
 * picked, so this early-return branch (and the guarantee that it does not crash or log a
 * spurious order) was never covered.
 */
class FOHEventOrderingBranchTest {
    private lateinit var mockSeatingLogger: FOHSeatingLogger
    private lateinit var mockFOHLogger: FOHLogger

    private lateinit var chicken: Ingredient
    private lateinit var rice: Ingredient
    private lateinit var potato: Ingredient
    private lateinit var onion: Ingredient
    private lateinit var chickenRice: Recipe
    private lateinit var potatoSoup: Recipe

    @BeforeEach
    fun setUp() {
        Ticks.init(24)
        Ticks.advanceEvening()
        Ticks.advanceTick()

        mockSeatingLogger = mock(FOHSeatingLogger::class.java)
        mockFOHLogger = mock(FOHLogger::class.java)
        whenever(mockFOHLogger.loggerSeating).thenReturn(mockSeatingLogger)

        chicken = Ingredient("Chicken", MeasuringUnit.G, 1)
        rice = Ingredient("Rice", MeasuringUnit.G, 3)
        potato = Ingredient("Potato", MeasuringUnit.G, 1)
        onion = Ingredient("Onion", MeasuringUnit.X, 1)

        chickenRice = Recipe(
            1,
            "chickenRice",
            30,
            mutableMapOf(chicken to 100, rice to 200),
            listOf(CookType.EXEC, CookType.SOUS, CookType.TOURNANT, CookType.ROAST),
            RestaurantType.ASIAN
        )
        potatoSoup = Recipe(
            2,
            "potatoSoup",
            10,
            mutableMapOf(potato to 300, onion to 1),
            listOf(CookType.EXEC, CookType.SOUS, CookType.TOURNANT, CookType.VEGETABLE)
        )
    }

    @Test
    fun `event group where no customer can find an eligible dish places no order`() {
        val table = Table(4, TableType.COMMON, 27)
        val waiter1 = Waiter().also { it.assignId(1) }
        val waiter2 = Waiter().also { it.assignId(2) }

        val supplier = Supplier(
            listOf(chicken, rice, potato, onion),
            mutableMapOf("Chicken" to 1000, "Rice" to 1000, "Potato" to 1000, "Onion" to 10)
        )
        val menu = Menu(supplier, listOf(chickenRice, potatoSoup), RestaurantType.ASIAN)
        menu.pantry.setSupplies(mutableMapOf(rice to 100000, onion to 2000, chicken to 60000, potato to 90000))
        menu.setAvailableMenu(listOf(chickenRice, potatoSoup))

        val foh = FrontOfHouse(listOf(table), mutableListOf(waiter1, waiter2), null, menu)
        foh.assignLoggers(mockFOHLogger)

        // excludes every ingredient used by either recipe, so every customer's
        // selectDishForEach ends up with an empty goodDishes list -> null pick
        val prefs = FoodPreferences(14, excludedIngredients = listOf("Chicken", "Rice", "Potato", "Onion"))
        val group = EventCustomerGroup(
            1,
            14,
            1,
            listOf(prefs),
            TableType.COMMON,
            listOf(RestaurantType.ASIAN),
            1,
            mutableMapOf(RestaurantType.ASIAN to "chickenRice")
        )

        foh.managerAssignTables(listOf(group))
        val orders = foh.seatAndOrder(listOf(group))

        assertTrue(orders.isEmpty())
        assertNull(group.order)
        // the group was still successfully seated...
        assertTrue(group.isSeated)
        // ...but since nobody could pick a dish, no order-related logging happened
        verify(mockSeatingLogger, never()).orderPlaced(any(), any(), any(), any())
    }
}
