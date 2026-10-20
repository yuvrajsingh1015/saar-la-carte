package general.incidentTests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import saarlacarte.enums.MeasuringUnit
import saarlacarte.food.Ingredient
import saarlacarte.incidents.PackagingIncident
import saarlacarte.restaurant.Pantry
import saarlacarte.simulation.Supplier
import saarlacarte.simulation.Ticks

/**
 * F33: Incident - Packaging Change.
 *
 * The existing PackagingIncidentTest.kt (by a teammate) only checks that
 * `execute()` writes the new volume into `supplier.packagingVolumes`. It never checks the
 * actual *point* of a packaging-change incident: that a later restock rounds up to a
 * different number of units because the package size changed (Supplier.procure rounds
 * `requiredAmount` up to the nearest multiple of `packagingVolumes[name]`). This test wires
 * PackagingIncident -> Supplier -> Pantry.restock together to cover that end-to-end effect,
 * which was not exercised by any existing test.
 */
class PackagingIncidentRestockIntegrationTest {
    private lateinit var rice: Ingredient
    private lateinit var supplier: Supplier
    private lateinit var pantry: Pantry

    @BeforeEach
    fun setUp() {
        rice = Ingredient("rice", MeasuringUnit.G, 5)
        supplier = Supplier(listOf(rice), mutableMapOf("rice" to 5))
        pantry = Pantry(supplier)
        Ticks.init(5)
        Ticks.advanceEvening()
    }

    @Test
    fun `changing the packaging volume changes how much a later restock actually procures`() {
        // before the incident: package size 5, need 12 -> rounds up to 3 packages = 15
        val before = pantry.restock(mapOf(rice to 12))
        assertEquals(15, before[rice])

        val incident = PackagingIncident(1, 1, supplier, "rice", 4)
        incident.execute()
        assertEquals(4, supplier.packagingVolumes["rice"])

        // after the incident: package size 4, need 12 -> rounds up to exactly 3 packages = 12
        val after = pantry.restock(mapOf(rice to 12))
        assertEquals(12, after[rice])
    }

    @Test
    fun `a packaging incident for an unknown ingredient does not disturb restocking of real ones`() {
        val incident = PackagingIncident(1, 1, supplier, "sugar", 999)
        incident.execute()

        // rice's packaging volume is untouched, so its rounding behaviour is unchanged
        assertEquals(5, supplier.packagingVolumes["rice"])
        val result = pantry.restock(mapOf(rice to 12))
        assertEquals(15, result[rice])
    }
}
