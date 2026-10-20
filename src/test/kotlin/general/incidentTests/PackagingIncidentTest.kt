package general.incidentTests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import saarlacarte.enums.MeasuringUnit
import saarlacarte.food.Ingredient
import saarlacarte.incidents.PackagingIncident
import saarlacarte.simulation.Supplier

class PackagingIncidentTest {

    private lateinit var potato: Ingredient
    private lateinit var onion: Ingredient

    private lateinit var supplier: Supplier

    @BeforeEach
    fun setUp() {
        potato = Ingredient("Potato", MeasuringUnit.G, 1)
        onion = Ingredient("Onion", MeasuringUnit.X, 1)

        supplier = Supplier(
            listOf(potato, onion),
            mutableMapOf("Potato" to 1000, "Onion" to 10)
        )
    }

    // pi for packaging incident
    @Test
    fun ingredientExistsPI() {
        val incident = PackagingIncident(
            1,
            1,
            supplier,
            "Potato",
            200
        )
        incident.execute()
        // affects only potato
        assertEquals(200, supplier.packagingVolumes["Potato"])
    }

    @Test
    fun noEffectToOtherIngredientPI() {
        val incident = PackagingIncident(
            1,
            1,
            supplier,
            "Potato",
            200
        )
        incident.execute()
        // affects only potato, doesn't affect onion
        assertEquals(10, supplier.packagingVolumes["Onion"])
    }

    @Test
    fun ingredientDoesNotExistsPI() {
        val incident = PackagingIncident(
            1,
            1,
            supplier,
            "Chilli",
            200
        )
        incident.execute()
        // affects neither potato nor onion
        assertEquals(1000, supplier.packagingVolumes["Potato"])
        assertEquals(10, supplier.packagingVolumes["Onion"])
    }
}
