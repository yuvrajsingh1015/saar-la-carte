package general.simulationTests

import org.junit.jupiter.api.Assertions.assertEquals
import saarlacarte.enums.MeasuringUnit
import saarlacarte.food.Ingredient
import saarlacarte.simulation.Supplier
import saarlacarte.simulation.Ticks
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/*
* Functions in supplier to test:
*
* -procure
* -isAvailable and its interaction with setUnavailable
* -interaction between procure and unavailable
*
*
* -changePackagingVolume
* -setUnavailable
* */
class SupplierTest {
    val chicken = Ingredient("chicken", MeasuringUnit.G, 2)
    val rice = Ingredient("rice", MeasuringUnit.G, 3)
    val potato = Ingredient("potato", MeasuringUnit.X, 5)
    val supplier = Supplier(
        ingredients = listOf(chicken, rice, potato),
        packagingVolumes = mutableMapOf("chicken" to 200, "potato" to 2, "rice" to 750)
    )

    @Test
    fun procureValidTest() {
        val obtained = supplier.procure("chicken", 50)
        assert(obtained == 200)
    }

    @Test
    fun isAvailableTest() {
        val supplierFresh = Supplier(
            ingredients = listOf(chicken, rice, potato),
            packagingVolumes = mutableMapOf("chicken" to 200, "potato" to 2, "rice" to 750)
        )
        assert(supplierFresh.isAvailable("chicken"))
    }

    @Test
    fun testChangePackagingVolume() {
        supplier.changePackagingVolume("chicken", 100)
        // check if *only* the chicken packaging volume changes
        assertEquals(100, supplier.packagingVolumes["chicken"])
        assertEquals(2, supplier.packagingVolumes["potato"])
        assertEquals(750, supplier.packagingVolumes["rice"])
    }

    @Test
    fun testChangePackagingVolumeInvalidName() {
        supplier.changePackagingVolume("what", 5)
        // check if the packaging volumes stay unchanged
        assertEquals(200, supplier.packagingVolumes["chicken"])
        assertEquals(2, supplier.packagingVolumes["potato"])
        assertEquals(750, supplier.packagingVolumes["rice"])
    }

    @Test
    fun testSetUnavailable() {
        supplier.setUnavailable("rice", 1, 3)
        // check that everything is still available except rice
        assertTrue(supplier.isAvailable("chicken"))
        assertTrue(supplier.isAvailable("potato"))
        assertFalse(supplier.isAvailable("rice"))
    }

    @Test
    fun testSetUnavailableInvalidName() {
        supplier.setUnavailable("what", 1, 3)
        // check that everything is still available
        assertTrue(supplier.isAvailable("chicken"))
        assertTrue(supplier.isAvailable("potato"))
        assertTrue(supplier.isAvailable("rice"))
    }

    @Test
    fun `interaction between isUnavailable and setUnavailable`() {
        val supplierFresh = Supplier(
            ingredients = listOf(chicken, rice, potato),
            packagingVolumes = mutableMapOf("chicken" to 200, "potato" to 2, "rice" to 750)
        )
        assert(supplierFresh.isAvailable("rice"))
        supplierFresh.setUnavailable(
            "rice",
            Ticks.getEvening(),
            1
        )
        // should become unavailable
        assert(!supplierFresh.isAvailable("rice"))
    }

    @Test
    fun `interaction between setUnavailable and procure`() {
        supplier.setUnavailable(
            "rice",
            Ticks.getEvening(),
            1
        )
        // should become unavailable
        assert(supplier.procure("rice", 20) == 0)
    }
}
