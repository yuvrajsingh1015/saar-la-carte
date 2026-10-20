package general.restaurantTests

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import saarlacarte.customer.CasualCustomerGroup
import saarlacarte.customer.DeliveryCustomerGroup
import saarlacarte.customer.EventCustomerGroup
import saarlacarte.customer.FoodPreferences
import saarlacarte.enums.CookType
import saarlacarte.enums.DishStatus
import saarlacarte.enums.MeasuringUnit
import saarlacarte.enums.RatingLikelihood
import saarlacarte.enums.RestaurantType
import saarlacarte.enums.TableType
import saarlacarte.enums.WaiterAction
import saarlacarte.food.Ingredient
import saarlacarte.food.Recipe
import saarlacarte.loggers.FOHSeatingLogger
import saarlacarte.orders.Dish
import saarlacarte.orders.Order
import saarlacarte.restaurant.FOHLogger
import saarlacarte.restaurant.FrontOfHouse
import saarlacarte.restaurant.Menu
import saarlacarte.restaurant.Table
import saarlacarte.restaurant.Waiter
import saarlacarte.simulation.Supplier
import saarlacarte.simulation.Ticks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FOHOrderingTest {
    private lateinit var mockSeatingLogger: FOHSeatingLogger
    private lateinit var mockFOHLogger: FOHLogger

    private lateinit var potato: Ingredient
    private lateinit var chicken: Ingredient
    private lateinit var rice: Ingredient
    private lateinit var onion: Ingredient

    private lateinit var chickenRice: Recipe
    private lateinit var potatoSoup: Recipe
    private lateinit var europeanBasicDish: Recipe

    @BeforeEach
    fun setUp() {
        Ticks.init(24)
        Ticks.advanceEvening()
        Ticks.advanceTick()

        mockSeatingLogger = mock()
        mockFOHLogger = mock()

        whenever(mockFOHLogger.loggerSeating).thenReturn(mockSeatingLogger)

        potato = Ingredient("Potato", MeasuringUnit.G, 1)
        onion = Ingredient("Onion", MeasuringUnit.X, 1)
        chicken = Ingredient("Chicken", MeasuringUnit.G, 1)
        rice = Ingredient("Rice", MeasuringUnit.G, 3)

        chickenRice = Recipe(
            1, "chickenRice", 30,
            mutableMapOf(chicken to 100, rice to 200),
            listOf(
                CookType.EXEC,
                CookType.SOUS,
                CookType.TOURNANT,
                CookType.ROAST
            ),
            RestaurantType.ASIAN
        )

        potatoSoup = Recipe(
            2,
            "potatoSoup",
            10,
            mutableMapOf(
                potato to 300,
                onion to 1,
            ),
            listOf(
                CookType.EXEC,
                CookType.SOUS,
                CookType.TOURNANT,
                CookType.VEGETABLE
            )
        )

        europeanBasicDish = Recipe(
            3,
            "europeanBasicDish",
            10,
            mutableMapOf(
                potato to 1,
                onion to 1,
            ),
            listOf(
                CookType.EXEC,
                CookType.SOUS,
                CookType.TOURNANT,
                CookType.VEGETABLE
            ),
            RestaurantType.EUROPEAN
        )
    }

    private fun createFOH(tables: List<Table>, waiters: MutableList<Waiter>, recipes: List<Recipe>): FrontOfHouse {
        val supplier = Supplier(
            listOf(chicken, rice, potato, onion),
            mutableMapOf("Chicken" to 1000, "Rice" to 1000, "Potato" to 1000, "Onion" to 10)
        )
        val menu = Menu(supplier, recipes, RestaurantType.ASIAN)
        menu.pantry.setSupplies(mutableMapOf(rice to 1000, onion to 20, chicken to 600, potato to 900))
        menu.setAvailableMenu(recipes)

        val foh = FrontOfHouse(tables, waiters, null, menu)
        foh.assignLoggers(mockFOHLogger)
        return foh
    }

    @Test
    fun `test if the assigned basic statuses are correct`() {
        val pref1 = FoodPreferences(1, favoriteDishes = listOf("chickenRice"))
        val pref2 = FoodPreferences(1, favoriteDishes = listOf("potatoSoup"))
        val pref3 = FoodPreferences(1, favoriteDishes = listOf("europeanBasicDish"))
        val group: CasualCustomerGroup = CasualCustomerGroup(
            1,
            3,
            1,
            listOf(pref1, pref2, pref3),
            TableType.COMMON,
            listOf(RestaurantType.ASIAN),
            listOf(1),
            RatingLikelihood.ALWAYS
        )
        val table = Table(1, TableType.COMMON, 3)
        val waiter = Waiter()
        waiter.assignId(1)
        val foh = createFOH(
            listOf(table),
            mutableListOf(waiter),
            listOf(chickenRice, potatoSoup, europeanBasicDish)
        )
        val orders = foh.seatAndOrder(listOf(group))
        assertEquals(1, orders.size, "too many orders returned")
        assertEquals(
            orders.first(),
            group.order,
            "returned order is not the same instance as the group's order"
        )
        val dishes = group.order?.getDishes() ?: emptyList()
        assertEquals(
            1,
            dishes.filter { it.getRecipe().dishName == "chickenRice" }.size,
            "ordered wrong amount of chicken rice"
        )
        assertTrue(
            dishes.find { it.getRecipe() == chickenRice }?.isBasic() ?: false,
            "chicken rice should be basic"
        )
        assertEquals(
            1,
            dishes.filter { it.getRecipe().dishName == "potatoSoup" }.size,
            "ordered wrong amount of potato soup"
        )
        assertFalse(
            dishes.find { it.getRecipe() == potatoSoup }?.isBasic() ?: true,
            "potato soup is not basic"
        )
        assertEquals(
            1,
            dishes.filter { it.getRecipe().dishName == "europeanBasicDish" }.size,
            "ordered wrong amount of europeanBasicDish"
        )
        assertTrue(
            dishes.find { it.getRecipe() == europeanBasicDish }?.isBasic() ?: false,
            "europeanBasicDish should be basic, even though it is basic for European"
        )
    }

    @Test
    fun `order placed by 1 group successfully`() {
        val table = Table(4, TableType.COMMON, 2)
        val waiter = Waiter()
        waiter.assignId(1)

        val foh = createFOH(listOf(table), mutableListOf(waiter), listOf(chickenRice, potatoSoup))

        val prefs = FoodPreferences(2, preferredIngredients = listOf("Rice"))
        val group: CasualCustomerGroup = CasualCustomerGroup(
            1,
            2,
            1,
            listOf(prefs),
            TableType.COMMON,
            listOf(RestaurantType.ASIAN),
            listOf(1),
            RatingLikelihood.ALWAYS
        )

        val orders = foh.seatAndOrder(listOf(group))
        assertEquals(1, orders.size)
        assertNotNull(orders)

        val order = orders.first()

        assertEquals("chickenRice", order.getDishes().first().getRecipe().dishName)
        assertEquals(2, order.getDishes().size)
        assertFalse(order.isDelivery())

        verify(mockSeatingLogger).orderPlaced(1, order.getId(), mapOf("chickenRice" to 2), listOf(1))
        verify(mockSeatingLogger).receivedOrders(2, 1)
    }

    @Test
    fun `order not placed by 1 group `() {
        val table = Table(4, TableType.COMMON, 2)
        val waiter = Waiter()
        waiter.assignId(1)

        val foh = createFOH(listOf(table), mutableListOf(waiter), listOf(chickenRice, potatoSoup))

        val prefs = FoodPreferences(2, excludedIngredients = listOf("Potato", "Onion", "Chicken", "Rice"))
        val group: CasualCustomerGroup = CasualCustomerGroup(
            1,
            2,
            1,
            listOf(prefs),
            TableType.COMMON,
            listOf(RestaurantType.ASIAN),
            listOf(1),
            RatingLikelihood.ALWAYS
        )

        val orders = foh.seatAndOrder(listOf(group))
        assertEquals(0, orders.size)
        assertTrue(orders.isEmpty())

        verify(mockSeatingLogger).orderNotPlaced(1, 2)
        verify(mockSeatingLogger, never()).orderPlaced(any(), any(), any(), any())
        verify(mockSeatingLogger).receivedOrders(0, 0)
    }

    @Test
    fun `order is not taken if they have already ordered`() {
        val table = Table(4, TableType.COMMON, 2)
        val waiter = Waiter()
        waiter.assignId(1)

        val foh = createFOH(listOf(table), mutableListOf(waiter), listOf(chickenRice, potatoSoup))

        val prefs = FoodPreferences(2, preferredIngredients = listOf("Rice"))
        val group = CasualCustomerGroup(
            1,
            2,
            1,
            listOf(prefs),
            TableType.COMMON,
            listOf(RestaurantType.ASIAN),
            listOf(1),
            RatingLikelihood.ALWAYS
        )

        val dish = Dish(chickenRice, DishStatus.INPROGRESS, true)

        val existingOrder = Order(22, mutableListOf(dish), false, 0, 2)
        group.order = existingOrder

        val orders = foh.seatAndOrder(listOf(group))
        assertTrue(orders.isEmpty())
        assertEquals(existingOrder, group.order)

        verify(mockSeatingLogger, never()).orderPlaced(any(), any(), any(), any())
    }

    @Test
    fun `take orders for delivery`() {
        val table = Table(4, TableType.COMMON, 2)
        val waiter = Waiter()
        waiter.assignId(1)

        val foh = createFOH(listOf(table), mutableListOf(waiter), listOf(chickenRice, potatoSoup))

        val prefs = FoodPreferences(2, preferredIngredients = listOf("Rice"))
        val group = DeliveryCustomerGroup(
            1,
            2,
            1,
            listOf(prefs),
            TableType.COMMON,
            listOf(RestaurantType.ASIAN),
            listOf(1),
            RatingLikelihood.ALWAYS,
            5
        )

        val orders = foh.seatAndOrder(listOf(group))
        val order = orders.first()
        assertEquals("chickenRice", order.getDishes().first().getRecipe().dishName)
        assertEquals(2, order.getDishes().size)
        assertTrue(order.isDelivery())

        verify(mockSeatingLogger).orderPlaced(1, order.getId(), mapOf("chickenRice" to 2), listOf())
    }

    @Test
    fun `do not seat,takeOrder for event when not enough waitstaff`() {
        val table = Table(4, TableType.COMMON, 27)
        val waiter = Waiter()
        waiter.assignId(1)

        waiter.performAction(WaiterAction.TAKING_ORDER, 8)

        val foh = createFOH(listOf(table), mutableListOf(waiter), listOf(chickenRice, potatoSoup))

        val prefs = FoodPreferences(2, preferredIngredients = listOf("Rice"))
        val group = EventCustomerGroup(
            1,
            4,
            1,
            listOf(prefs),
            TableType.COMMON,
            listOf(RestaurantType.ASIAN),
            1,
            mutableMapOf(RestaurantType.ASIAN to "chickenRice")
        )

        foh.managerAssignTables(listOf(group))
        foh.seatCustomers(listOf(group))
        val orders = foh.seatAndOrder(listOf(group))

        assertTrue(orders.isEmpty())
        assertNull(group.order)
        verify(mockSeatingLogger, never()).orderPlaced(any(), any(), any(), any())
    }

    @Test
    fun `take orders for event when enough waitstaff`() {
        val table = Table(4, TableType.COMMON, 27)
        val waiter1 = Waiter()
        waiter1.assignId(1)

        val waiter2 = Waiter()
        waiter2.assignId(2)

        // create special foh for event

        val supplier = Supplier(
            listOf(chicken, rice, potato, onion),
            mutableMapOf("Chicken" to 1000, "Rice" to 1000, "Potato" to 1000, "Onion" to 10)
        )
        val menu = Menu(supplier, listOf(chickenRice, potatoSoup), RestaurantType.ASIAN)
        menu.pantry.setSupplies(mutableMapOf(rice to 100000, onion to 2000, chicken to 60000, potato to 90000))
        menu.setAvailableMenu(listOf(chickenRice, potatoSoup))

        val foh = FrontOfHouse(listOf(table), mutableListOf(waiter1, waiter2), null, menu)
        foh.assignLoggers(mockFOHLogger)

        val prefs = FoodPreferences(14, preferredIngredients = listOf("Rice"))
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
        val order = orders.first()

        assertTrue(orders.isNotEmpty())
        assertNotNull(group.order)
        verify(mockSeatingLogger).orderPlaced(1, order.getId(), mapOf("chickenRice" to 14), listOf(1, 2))
    }
}
