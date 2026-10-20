package general.restaurantTests

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import saarlacarte.enums.MeasuringUnit
import saarlacarte.food.Ingredient
import saarlacarte.restaurant.Pantry
import saarlacarte.simulation.Supplier
import saarlacarte.simulation.Ticks
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * F09: Restaurant-Pantry.
 *
 * Pantry.restock(...) is only ever exercised indirectly through Kitchen.estimateAndRestockFood
 * in the existing PantryTest.kt, and none of those tests hit the three branches inside
 * restock() itself: skipping an ingredient whose requested amount is 0, skipping an ingredient
 * whose supplier.procure(...) came back with 0 (e.g. unavailable), and rejecting the whole call
 * up-front via validateAmounts when a negative amount is requested. This file calls
 * Pantry.restock(...) directly to close those branches.
 */
class PantryRestockBranchTest {
    private lateinit var pantry: Pantry
    private val mockSupplier = mock<Supplier>()

    private val rice = Ingredient("rice", MeasuringUnit.G, 5)
    private val chicken = Ingredient("chicken", MeasuringUnit.G, 2)

    @BeforeEach
    fun setUp() {
        pantry = Pantry(mockSupplier)
        Ticks.init(5)
        Ticks.advanceEvening()
    }

    @Test
    fun `restock rejects the whole call when an amount is negative`() {
        val result = pantry.restock(mapOf(rice to -1, chicken to 5))
        assertTrue(result.isEmpty())
        verify(mockSupplier, never()).procure(org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }

    @Test
    fun `restock skips ingredients that need zero amount`() {
        whenever(mockSupplier.procure(eq("chicken"), eq(5))).thenReturn(5)

        val result = pantry.restock(mapOf(rice to 0, chicken to 5))

        // rice was requested with amount 0 -> never asked from the supplier, not in the result
        verify(mockSupplier, never()).procure(eq("rice"), org.mockito.kotlin.any())
        assertFalse(result.containsKey(rice))
        assertEquals(5, result[chicken])
        assertEquals(5, pantry.getSupplies()[chicken])
    }

    @Test
    fun `restock does not record an ingredient the supplier could not procure`() {
        whenever(mockSupplier.procure(eq("chicken"), eq(5))).thenReturn(0)

        val result = pantry.restock(mapOf(chicken to 5))

        assertTrue(result.isEmpty())
        assertTrue(pantry.getPackages().isEmpty())
        // supplies map is untouched since nothing was actually procured
        assertEquals(0, pantry.getSupplies()[chicken] ?: 0)
    }

    @Test
    fun `restock records a package and updates supplies when procurement succeeds`() {
        whenever(mockSupplier.procure(eq("chicken"), eq(3))).thenReturn(4)

        val result = pantry.restock(mapOf(chicken to 3))

        assertEquals(4, result[chicken])
        assertEquals(4, pantry.getSupplies()[chicken])
        assertEquals(1, pantry.getPackages().size)
        assertEquals(4, pantry.getPackages().first().amount)
    }
}
