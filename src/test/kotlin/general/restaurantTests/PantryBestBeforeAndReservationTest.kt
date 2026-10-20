package general.restaurantTests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import saarlacarte.enums.CookType
import saarlacarte.enums.MeasuringUnit
import saarlacarte.enums.RestaurantType
import saarlacarte.food.Ingredient
import saarlacarte.food.Package
import saarlacarte.food.Recipe
import saarlacarte.restaurant.Menu
import saarlacarte.restaurant.Pantry
import saarlacarte.simulation.Supplier
import saarlacarte.simulation.Ticks

class PantryBestBeforeAndReservationTest {
    private lateinit var realPantry: Pantry

    private lateinit var potato: Ingredient
    private lateinit var onion: Ingredient

    private lateinit var potatoSoup: Recipe

    @BeforeEach
    fun setUp() {
        Ticks.init(24)
        Ticks.advanceEvening()

        potato = Ingredient("potato", MeasuringUnit.G, 1)
        onion = Ingredient("onion", MeasuringUnit.X, 1)

        potatoSoup = Recipe(
            1, "potato soup", 10, mutableMapOf(potato to 300, onion to 1),
            listOf(CookType.EXEC, CookType.SOUS),
            RestaurantType.EUROPEAN
        )

        realPantry = Pantry(mock())
        realPantry.setSupplies(mutableMapOf(potato to 5, onion to 5))
    }

    // fun ` `() {}

    @Test
    fun `clearOldPackages checks and removes the expired ingredients`() {
        realPantry.setPackages(
            mutableListOf(
                Package(potato, 5, 0), // expired
                Package(onion, 5, 100)
            )
        )

        val actualExpired = realPantry.clearOldPackages()

        assertEquals(mapOf(potato to 5), actualExpired)
        assertEquals(0, realPantry.availableAmount(potato))
        assertEquals(5, realPantry.availableAmount(onion))
    }

    @Test
    fun `clearOldPackages does nothing if all ingredients are fresh`() {
        realPantry.setPackages(
            mutableListOf(
                Package(potato, 5, 10),
                Package(onion, 5, 10)
            )
        )

        val actualExpired = realPantry.clearOldPackages()

        assertTrue(actualExpired.isEmpty())
    }

    @Test
    fun `reserve the ingredients when enough is available`() {
        val isReserved = realPantry.reserve(mapOf(potato to 2, onion to 5))

        assertTrue(isReserved)
        assertEquals(3, realPantry.availableAmount(potato))
        assertEquals(0, realPantry.availableAmount(onion))
    }

    @Test
    fun `do not reserve the ingredients when enough is NOT available`() {
        val isReserved = realPantry.reserve(mapOf(potato to 2, onion to 10))

        assertFalse(isReserved)
        assertEquals(5, realPantry.availableAmount(potato))
        assertEquals(5, realPantry.availableAmount(onion))
    }

    // ---------------------------------------------------------------------

    @Test
    fun `real reserve succeeds and reduces supplies when stock is enough`() {
        assertTrue(realPantry.reserve(mapOf(potato to 2)))
        assertEquals(3, realPantry.availableAmount(potato))
    }

    @Test
    fun `real reserve fails when stock is short, supplies stay unchanged`() {
        assertFalse(realPantry.reserve(mapOf(potato to 6)))
        assertEquals(5, realPantry.availableAmount(potato))
    }

    @Test
    fun `real reserve is atomic, one short ingredient blocks the whole reserve`() {
        // potato is enough, onion is not -> nothing should be reserved
        assertFalse(realPantry.reserve(mapOf(potato to 2, onion to 100)))
        assertEquals(5, realPantry.availableAmount(potato))
        assertEquals(5, realPantry.availableAmount(onion))
    }

    @Test
    fun `real reserve rejects a negative amount`() {
        assertFalse(realPantry.reserve(mapOf(potato to -1)))
        assertEquals(5, realPantry.availableAmount(potato))
    }

    @Test
    fun `real removeReserved consumes the reserved amount from the packages`() {
        realPantry.setPackages(mutableListOf(Package(potato, 5, 10)))
        realPantry.reserve(mapOf(potato to 2))

        realPantry.removeReserved() // should not throw, reserved gets cleared out of the packages

        // reserved stock is consumed, not given back to supplies
        assertEquals(3, realPantry.availableAmount(potato))
    }

    @Test
    fun `Menu reserveDishes talks to the real pantry`() {
        // more reserveDishes cases (multiple dishes, atomicity) already live in MenuTest.kt
        val menu = Menu(mock<Supplier>(), listOf(potatoSoup), RestaurantType.EUROPEAN)
        menu.pantry.setSupplies(mutableMapOf(potato to 300, onion to 1))

        assertTrue(menu.reserveDishes(mapOf(potatoSoup to 1)))
        assertEquals(0, menu.pantry.availableAmount(potato))
    }
}
