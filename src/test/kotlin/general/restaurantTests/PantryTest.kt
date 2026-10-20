package general.restaurantTests

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import saarlacarte.customer.EventCustomerGroup
import saarlacarte.customer.RegularCustomerGroup
import saarlacarte.enums.CookType
import saarlacarte.enums.DishStatus
import saarlacarte.enums.MeasuringUnit
import saarlacarte.enums.RestaurantType
import saarlacarte.food.Ingredient
import saarlacarte.food.Package
import saarlacarte.food.Recipe
import saarlacarte.orders.Dish
import saarlacarte.orders.Order
import saarlacarte.restaurant.Cook
import saarlacarte.restaurant.Kitchen
import saarlacarte.restaurant.Menu
import saarlacarte.restaurant.Pantry
import saarlacarte.simulation.Supplier
import saarlacarte.simulation.Ticks
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** F09: Planning dishes, calculating ingredients, restocking **/
class PantryTest {
    private lateinit var pantry: Pantry

    private val mockSupplier = mock<Supplier>()

    private val rice = Ingredient("rice", MeasuringUnit.G, 5)
    private val chicken = Ingredient("chicken", MeasuringUnit.G, 2)

    private val ricePackage = Package(rice, 3, 3)
    private val chickenPackage = Package(chicken, 1, 1)
    // private val chickenPackage2 = Package(chicken, 1, 2)

    val supplies: MutableMap<Ingredient, Int> = linkedMapOf(rice to 5, chicken to 3)
    var packages = mutableListOf(ricePackage, chickenPackage)

    val friedChicken = Recipe(
        1,
        "fried chicken",
        20,
        mutableMapOf(chicken to 1),
        listOf(CookType.EXEC),
        RestaurantType.EUROPEAN
    )
    val boiledRice = Recipe(
        2,
        "boiled rice",
        30,
        mutableMapOf(rice to 4),
        listOf(CookType.EXEC),
        RestaurantType.ASIAN
    )
    val menu = Menu(
        Supplier(
            listOf(chicken, rice),
            mutableMapOf("chicken" to 1, "rice" to 4)
        ),
        listOf(friedChicken, boiledRice),
        RestaurantType.EUROPEAN
    )
    private val kitchen = Kitchen(mutableListOf(mock<Cook>()), menu)

    @BeforeEach
    fun setUp() {
        pantry = Pantry(mockSupplier)
        Ticks.init(5)
        Ticks.advanceEvening()
    }

    @Test
    fun testAvailableAmountEmpty() {
        assertEquals(0, pantry.availableAmount(rice))
    }

    @Test
    fun testAvailableAmount() {
        pantry.setSupplies(supplies)
        pantry.setPackages(packages)
        assertEquals(5, pantry.availableAmount(rice))
        assertEquals(3, pantry.availableAmount(chicken))
    }

    @Test
    fun `estimate food for regulars test`() {
        val order1 = createOrder(2, 2, 1)
        val order2 = createOrder(4, 0, 2)
        val order3 = createOrder(3, 1, 3)
        menu.pantry.setSupplies(mutableMapOf(chicken to 1, rice to 0))
        whenever(mockSupplier.procure(eq("chicken"), eq(8))).thenReturn(8)
        whenever(mockSupplier.procure(eq("rice"), eq(12))).thenReturn(12)

        val regulars = mock<RegularCustomerGroup>()
        whenever(regulars.isVisiting()).thenReturn(true)
        whenever(regulars.size).thenReturn(4)
        whenever(regulars.id).thenReturn(1)
        whenever(regulars.last3).thenReturn(listOf(order1, order2, order3))
        kitchen.estimateAndRestockFood(listOf(regulars), 0)
        val newSupplies = menu.pantry.getSupplies()
        assertEquals(9, newSupplies[chicken])
        assertEquals(12, newSupplies[rice])
    }

    fun createOrder(chickens: Int, rices: Int, id: Int): Order {
        val dishes = mutableListOf<Dish>()
        repeat(chickens) {
            dishes.add(Dish(friedChicken, DishStatus.FINISHED))
        }
        repeat(rices) {
            dishes.add(Dish(boiledRice, DishStatus.FINISHED))
        }
        return Order(id, dishes, false, null, 2)
    }

    @Test
    fun `estimate food only free seats test`() {
        menu.pantry.setSupplies(mutableMapOf(chicken to 10, rice to 0))
        whenever(mockSupplier.procure(eq("chicken"), eq(0))).thenReturn(0)
        whenever(mockSupplier.procure(eq("rice"), eq(12))).thenReturn(12)

        kitchen.estimateAndRestockFood(listOf(), 21)
        val newSupplies = menu.pantry.getSupplies()
        assertEquals(10, newSupplies[chicken])
        assertEquals(12, newSupplies[rice])
    }

    @Test
    fun `estimate food for event test`() {
        menu.pantry.setSupplies(mutableMapOf(chicken to 0, rice to 0))
        whenever(mockSupplier.procure(eq("chicken"), eq(20))).thenReturn(20)
        whenever(mockSupplier.procure(eq("rice"), eq(0))).thenReturn(0)

        val event = mock<EventCustomerGroup>()
        whenever(event.isVisiting()).thenReturn(true)
        whenever(event.size).thenReturn(20)
        whenever(event.id).thenReturn(1)
        whenever(event.favoriteBasicDish).thenReturn(
            mutableMapOf(RestaurantType.EUROPEAN to "fried chicken", RestaurantType.ASIAN to "boiled rice")
        )

        kitchen.estimateAndRestockFood(listOf(event), 0)
        val newSupplies = menu.pantry.getSupplies()
        assertEquals(20, newSupplies[chicken])
        assertEquals(0, newSupplies[rice])
    }

    @Test
    fun `reserve ingredients with empty pantry`() {
        pantry.setSupplies(mutableMapOf(chicken to 0, rice to 0))
        val reserved = pantry.reserve(supplies) // supplies: rice to 5, chicken to 3
        assertFalse(reserved)
    }

    @Test
    fun `reserve with negative ingredients`() {
        pantry.setSupplies(mutableMapOf(chicken to 5, rice to 5))
        val reserved = pantry.reserve(mapOf(chicken to -1, rice to -1)) // supplies: rice to 5, chicken to 3
        assertFalse(reserved)
    }

    @Test
    fun `clearOldPackages with non-expired ingredients`() {
        pantry.setSupplies(supplies)
        pantry.setPackages(packages)
        assertEquals(emptyMap(), pantry.clearOldPackages())
    }

    @Test
    fun `clearOldPackages with an expired ingredient`() {
        pantry.setSupplies(supplies)
        pantry.setPackages(packages)
        Ticks.advanceEvening() // evening 2
        assertEquals(mapOf(chicken to 1), pantry.clearOldPackages())
    }

    @Test
    fun `release ingredients test`() {
        pantry.setPackages(packages) // rice to 3, chicken to 1
        pantry.setReserved(mutableMapOf(rice to 2, chicken to 1))
        pantry.removeReserved()
        assertEquals(
            mutableListOf(Package(rice, 1, 3)),
            pantry.getPackages()
        )
    }

    @Test
    fun `release negative ingredients test`() {
        pantry.setPackages(packages) // rice to 3, chicken to 1
        pantry.setReserved(mutableMapOf(chicken to -5, rice to -5))
        pantry.removeReserved()
        // packages should not have changed
        assertEquals(packages, pantry.getPackages())
    }

    // @Test
    // fun `clearOldPackages with only a subset of the packages expired`() {
    //     // use both chickenPackage and chickenPackage2
    // }

    /*
    * todo:
    *  reserve
    *  removeReserved -> release
    *  restock -> logged in ASCENDING ALPHABETIC ORDER
    */
}
