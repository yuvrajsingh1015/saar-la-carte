package general.customerTests

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import saarlacarte.customer.CasualCustomerGroup
import saarlacarte.customer.DeliveryCustomerGroup
import saarlacarte.customer.EventCustomerGroup
import saarlacarte.customer.FoodPreferences
import saarlacarte.delivery.Driver
import saarlacarte.enums.CookType
import saarlacarte.enums.MeasuringUnit
import saarlacarte.enums.RatingLikelihood
import saarlacarte.enums.RestaurantType
import saarlacarte.enums.TableType
import saarlacarte.food.Ingredient
import saarlacarte.food.Recipe
import saarlacarte.restaurant.FrontOfHouse
import saarlacarte.restaurant.Kitchen
import saarlacarte.restaurant.Menu
import saarlacarte.restaurant.Restaurant
import saarlacarte.restaurant.Staff
import saarlacarte.restaurant.Table
import saarlacarte.simulation.BrowsingService
import saarlacarte.simulation.Supplier
import saarlacarte.simulation.Ticks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CustomerRestaurantDecisionTest {

    private lateinit var potato: Ingredient
    private lateinit var chicken: Ingredient
    private lateinit var rice: Ingredient
    private lateinit var onion: Ingredient

    private lateinit var chickenRice: Recipe
    private lateinit var potatoSoup: Recipe

    @BeforeEach
    fun setup() {
        Ticks.init(24)
        Ticks.advanceEvening()
        Ticks.advanceTick()

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
    }

    private fun createRestaurant(
        id: Int,
        type: RestaurantType,
        canHostEvent: Boolean,
        positiveRatings: Int,
        negativeRatings: Int,
        recipes: List<Recipe>,
        tables: List<Table>,
        drivers: List<Driver> = emptyList(),
    ): Restaurant {
        val supplier = Supplier(
            listOf(chicken, rice, potato, onion),
            mutableMapOf("Chicken" to 1000, "Rice" to 1000, "Potato" to 1000, "Onion" to 10)
        )
        val menu = Menu(supplier, recipes, RestaurantType.ASIAN)
        menu.pantry.setSupplies(mutableMapOf(rice to 100000, onion to 2000, chicken to 60000, potato to 90000))
        menu.setAvailableMenu(listOf(chickenRice, potatoSoup))

        val kitchen = Kitchen(mutableListOf(), menu)
        val foh = FrontOfHouse(tables, mutableListOf(), null, menu)
        val staff = Staff(
            0,
            mapOf(
                CookType.EXEC to 0,
                CookType.SOUS to 0,
                CookType.TOURNANT to 0,
                CookType.VEGETABLE to 0,
                CookType.ROAST to 0,
                CookType.PASTRY to 0,
                CookType.FISH to 0,
                CookType.SAUCE to 0
            ),
            drivers.size
        )

        val openStart = 1
        val openEnd = 20

        return Restaurant(
            id = id,
            name = "Restaurant \${id}",
            type = type,
            openingTickStart = openStart,
            openingTickEnd = openEnd,
            canHostEvent = canHostEvent,
            staff = staff,
            positiveRatings = positiveRatings,
            negativeRatings = negativeRatings,
            frontOfHouse = foh,
            kitchen = kitchen,
            deliveryDriverCount = drivers.size,
        )
    }

    @Test
    fun `casuals select restaurant with highest rating`() {
        repeat(4) { Ticks.advanceTick() } // here the tick is 5 since we already did the advance tick up above

        val table1 = Table(4, TableType.COMMON, 4)
        val table2 = Table(4, TableType.COMMON, 4)

        val r1 = createRestaurant(1, RestaurantType.ASIAN, false, 5, 2, listOf(chickenRice), listOf(table1, table2))
        val r2 = createRestaurant(2, RestaurantType.ASIAN, false, 10, 2, listOf(chickenRice), listOf(table1, table2))

        val browsing = BrowsingService(listOf(r1, r2))

        val prefs = FoodPreferences(2, preferredIngredients = listOf("Rice"))
        val group = CasualCustomerGroup(
            1,
            2,
            5,
            listOf(prefs),
            TableType.COMMON,
            listOf(RestaurantType.ASIAN),
            listOf(1),
            RatingLikelihood.ALWAYS
        )

        group.setBrowsingService(browsing)

        assertTrue(group.isReadyToDecide())
        val chosen = group.decideRestaurant()

        assertEquals(r2, chosen)
    }

    @Test
    fun `casuals select restaurant low ID tie breaker`() {
        repeat(4) { Ticks.advanceTick() } // here the tick is 5 since we already did the advance tick up above

        val table1 = Table(4, TableType.COMMON, 4)
        val table2 = Table(4, TableType.COMMON, 4)

        val r1 = createRestaurant(1, RestaurantType.ASIAN, false, 10, 2, listOf(chickenRice), listOf(table1, table2))
        val r2 = createRestaurant(2, RestaurantType.ASIAN, false, 10, 2, listOf(chickenRice), listOf(table1, table2))

        val browsing = BrowsingService(listOf(r1, r2))

        val prefs = FoodPreferences(2, preferredIngredients = listOf("Rice"))
        val group = CasualCustomerGroup(
            1,
            2,
            5,
            listOf(prefs),
            TableType.COMMON,
            listOf(RestaurantType.ASIAN),
            listOf(1),
            RatingLikelihood.ALWAYS
        )

        group.setBrowsingService(browsing)

        assertTrue(group.isReadyToDecide())
        val chosen = group.decideRestaurant()

        assertEquals(r1, chosen)
    }

    @Test
    fun `delivery group don't choose restaurant with no driver`() {
        val table1 = Table(4, TableType.COMMON, 4)
        val table2 = Table(4, TableType.COMMON, 4)

        val r1 = createRestaurant(1, RestaurantType.ASIAN, false, 10, 2, listOf(chickenRice), listOf(table1, table2))

        val prefs = FoodPreferences(2, preferredIngredients = listOf("Rice"))
        val group = DeliveryCustomerGroup(
            1,
            2,
            10,
            listOf(prefs),
            TableType.COMMON,
            listOf(RestaurantType.ASIAN),
            listOf(1),
            RatingLikelihood.ALWAYS,
            15
        )
        // 15/5 = 3
        // visitingTick - 3 - 3 = 10 - 6 = 4
        repeat(3) { Ticks.advanceTick() }

        val browsing = BrowsingService(listOf(r1))

        group.setBrowsingService(browsing)

        // no driver so no booking
        assertTrue(group.isReadyToDecide())

        val chosen = group.decideRestaurant()
        assertNull(chosen)
    }

    @Test
    fun `event group don't choose restaurant with canHostEventFalse`() {
        val table1 = Table(4, TableType.COMMON, 4)
        val table2 = Table(4, TableType.COMMON, 4)

        val r1 = createRestaurant(1, RestaurantType.ASIAN, false, 10, 2, listOf(chickenRice), listOf(table1, table2))

        val prefs = FoodPreferences(2, preferredIngredients = listOf("Rice"))
        val group = EventCustomerGroup(
            1,
            4,
            4,
            listOf(prefs),
            TableType.COMMON,
            listOf(RestaurantType.ASIAN),
            4,
            mutableMapOf(RestaurantType.ASIAN to "chickenRice")
        )

        val browsing = BrowsingService(listOf(r1))

        group.setBrowsingService(browsing)

        assertTrue(group.isReadyToDecide())

        val chosen = group.decideRestaurant()
        assertNull(chosen)
    }

    @Test
    fun `groups don't choose restaurant where they cant eat anything`() {
        val table1 = Table(1, TableType.COMMON, 4)
        val table2 = Table(2, TableType.COMMON, 4)
        val table3 = Table(3, TableType.COMMON, 27)
        val table4 = Table(4, TableType.COMMON, 8)
        val table5 = Table(5, TableType.COMMON, 4)

        val r1 = createRestaurant(
            1,
            RestaurantType.ASIAN,
            true,
            10,
            2,
            listOf(chickenRice),
            listOf(table1, table2, table3, table4, table5)
        )
        val r2 = createRestaurant(
            2,
            RestaurantType.EUROPEAN,
            true,
            10,
            2,
            listOf(potatoSoup),
            listOf(table1, table2, table3, table4, table5)
        )

        val prefs = FoodPreferences(4, excludedIngredients = listOf("Rice"))
        val group = EventCustomerGroup(
            1,
            4,
            4,
            listOf(prefs),
            TableType.COMMON,
            listOf(RestaurantType.ASIAN, RestaurantType.EUROPEAN),
            4,
            mutableMapOf(RestaurantType.ASIAN to "chickenRice", RestaurantType.EUROPEAN to "potatoSoup")
        )

        val browsing = BrowsingService(listOf(r1, r2))

        group.setBrowsingService(browsing)

        assertTrue(group.isReadyToDecide())

        val chosen = group.decideRestaurant()

        assertEquals(r2, chosen)
    }

    @Test
    fun `event group reserves a host restaurant and keeps it`() {
        val table = Table(1, TableType.COMMON, 8)
        val r1 = createRestaurant(1, RestaurantType.ASIAN, true, 4, 1, listOf(chickenRice), listOf(table))
        val group = EventCustomerGroup(
            3,
            4,
            4,
            listOf(FoodPreferences(4, preferredIngredients = listOf("Rice"))),
            TableType.COMMON,
            listOf(RestaurantType.ASIAN),
            4,
            mutableMapOf(RestaurantType.ASIAN to "chickenRice")
        )
        group.setBrowsingService(BrowsingService(listOf(r1)))
        assertTrue(group.isReadyToDecide())
        assertEquals(r1, group.decideRestaurant())
        assertEquals(r1, group.getReservedRestaurant())
        assertEquals(r1, group.reserveRestaurant())
    }

    @Test
    fun `delivery group chooses a restaurant that has a free driver`() {
        val table = Table(1, TableType.COMMON, 4)
        val r1 = createRestaurant(
            1,
            RestaurantType.ASIAN,
            false,
            10,
            2,
            listOf(chickenRice),
            listOf(table),
            listOf(Driver())
        )
        val group = DeliveryCustomerGroup(
            4,
            2,
            10,
            listOf(FoodPreferences(2, preferredIngredients = listOf("Rice"))),
            TableType.COMMON,
            listOf(RestaurantType.ASIAN),
            listOf(1),
            RatingLikelihood.ALWAYS,
            15
        )
        repeat(3) { Ticks.advanceTick() }
        group.setBrowsingService(BrowsingService(listOf(r1)))
        assertTrue(group.isReadyToDecide())
        assertEquals(r1, group.decideRestaurant())
    }

    @Test
    fun `decideRestaurant is null when browsing was never attached`() {
        val event = EventCustomerGroup(
            5,
            4,
            4,
            emptyList(),
            TableType.COMMON,
            listOf(RestaurantType.ASIAN),
            4,
            mutableMapOf()
        )
        val delivery = DeliveryCustomerGroup(
            6,
            1,
            10,
            emptyList(),
            TableType.COMMON,
            listOf(RestaurantType.ASIAN),
            listOf(1),
            RatingLikelihood.ALWAYS,
            5
        )
        assertNull(event.decideRestaurant())
        assertNull(delivery.decideRestaurant())
    }
}
