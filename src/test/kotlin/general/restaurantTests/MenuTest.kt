package general.restaurantTests

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import saarlacarte.enums.CookType
import saarlacarte.enums.MeasuringUnit
import saarlacarte.enums.RestaurantType
import saarlacarte.food.Ingredient
import saarlacarte.food.Recipe
import saarlacarte.restaurant.Menu
import saarlacarte.simulation.Supplier
import saarlacarte.simulation.Ticks
import kotlin.test.Test

/**
 * Unit tests for F13 menu filtering and dish reservation.
 */
class MenuTest {
    private val rice = Ingredient("rice", MeasuringUnit.G, 3)
    private val chicken = Ingredient("chicken", MeasuringUnit.G, 2)
    private val riceBowl = Recipe(
        1,
        "Rice Bowl",
        10,
        mutableMapOf(rice to 1),
        listOf(CookType.SAUCE)
    )
    private val chickenRice = Recipe(
        2,
        "Chicken Rice",
        15,
        mutableMapOf(rice to 1, chicken to 1),
        listOf(CookType.SAUCE)
    )

    @BeforeEach
    fun resetTicks() {
        Ticks.init(1)
        Ticks.advanceEvening()
    }

    @AfterEach
    fun clearTicks() {
        Ticks.init(1)
    }

    @Test
    fun emptyPantryOffersNoDishes() {
        val menu = menuOf(riceBowl, chickenRice)
        assertTrue(menu.filterAvailableMenu().isEmpty())
    }

    @Test
    fun filterKeepsOnlyDishesThePantryCanMake() {
        val menu = menuOf(riceBowl, chickenRice)
        menu.pantry.restock(mapOf(rice to 1))
        val available = menu.filterAvailableMenu()
        assertEquals(listOf(riceBowl), available)
    }

    @Test
    fun setAvailableMenuRestrictsTheCatalog() {
        val menu = menuOf(riceBowl, chickenRice)
        menu.pantry.restock(mapOf(rice to 1, chicken to 1))
        menu.setAvailableMenu(listOf(riceBowl))
        assertEquals(listOf(riceBowl), menu.getAvailableMenu())
    }

    @Test
    fun reserveDishesRemovesStockAndDropsTheDish() {
        val menu = menuOf(riceBowl)
        menu.pantry.restock(mapOf(rice to 1))
        menu.filterAvailableMenu()
        assertTrue(menu.reserveDishes(mapOf(riceBowl to 1)))
        assertTrue(menu.filterAvailableMenu().isEmpty())
    }

    @Test
    fun reserveDishesFailsWhenStockIsShort() {
        val menu = menuOf(riceBowl)
        menu.pantry.restock(mapOf(rice to 1))
        menu.filterAvailableMenu()
        assertFalse(menu.reserveDishes(mapOf(riceBowl to 2)))
        assertEquals(1, menu.pantry.availableAmount(rice))
        assertEquals(listOf(riceBowl), menu.filterAvailableMenu())
    }

    @Test
    fun reserveMultipleDishesSumsIngredientsAcrossDishes() {
        val menu = menuOf(riceBowl, chickenRice)
        menu.pantry.restock(mapOf(rice to 2, chicken to 1))
        assertTrue(menu.reserveDishes(mapOf(riceBowl to 1, chickenRice to 1)))
        assertEquals(0, menu.pantry.availableAmount(rice))
        assertEquals(0, menu.pantry.availableAmount(chicken))
    }

    @Test
    fun reserveDishesMultipliesByPortionCount() {
        val menu = menuOf(riceBowl)
        menu.pantry.restock(mapOf(rice to 3))
        // 0 portions of a dish should not reserve anything for it
        assertTrue(menu.reserveDishes(mapOf(riceBowl to 3, chickenRice to 0)))
        assertEquals(0, menu.pantry.availableAmount(rice))
    }

    @Test
    fun reserveDishesAcrossMultipleDishesIsAtomic() {
        val menu = menuOf(riceBowl, chickenRice)
        // enough rice for both, but no chicken -> chickenRice can't be made
        menu.pantry.restock(mapOf(rice to 2))
        assertFalse(menu.reserveDishes(mapOf(riceBowl to 1, chickenRice to 1)))
        // nothing should have been reserved, riceBowl's rice is untouched
        assertEquals(2, menu.pantry.availableAmount(rice))
    }

    @Test
    fun emptyIngredientRecipeIsAlwaysAvailable() {
        val freeDish = Recipe(3, "Water", 1, mutableMapOf(), listOf(CookType.SAUCE))
        val menu = menuOf(freeDish)
        assertEquals(listOf(freeDish), menu.filterAvailableMenu())
    }

    @Test
    fun cookEligibleCatalogPersistsAcrossFilterCalls() {
        val menu = menuOf(riceBowl, chickenRice)
        menu.pantry.restock(mapOf(rice to 1, chicken to 1))
        menu.setAvailableMenu(listOf(riceBowl))
        // restocking more shouldn't bring chickenRice back, it's outside the catalog now
        menu.pantry.restock(mapOf(chicken to 5))
        assertEquals(listOf(riceBowl), menu.filterAvailableMenu())
    }

    @Test
    fun restockingMultipleTimesAccumulatesStock() {
        val menu = menuOf(riceBowl)
        menu.pantry.restock(mapOf(rice to 1))
        menu.pantry.restock(mapOf(rice to 1))
        menu.pantry.restock(mapOf(rice to 1))
        assertEquals(3, menu.pantry.availableAmount(rice))
        // stock actually usable, not just counted
        assertTrue(menu.reserveDishes(mapOf(riceBowl to 3)))
        assertEquals(0, menu.pantry.availableAmount(rice))
    }

    private fun menuOf(vararg dishes: Recipe): Menu {
        val supplier = Supplier(
            listOf(rice, chicken),
            mutableMapOf("rice" to 1, "chicken" to 1)
        )
        return Menu(supplier, dishes.toList(), RestaurantType.ASIAN)
    }
}
