package general.incidentTests

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import saarlacarte.enums.MeasuringUnit
import saarlacarte.food.Ingredient
import saarlacarte.incidents.UnavailableIncident
import saarlacarte.simulation.Supplier
import saarlacarte.simulation.Ticks

class UnavailableIncidentTest {

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
        Ticks.init(70)
        Ticks.advanceEvening()
    }

    @Test
    fun onlyPotatoUnavailable() {
        val incident = UnavailableIncident(
            1,
            1,
            supplier,
            "Potato",
            5
        )
        incident.execute()

        assertFalse(supplier.isAvailable("Potato"))
        assertTrue(supplier.isAvailable("Onion"))
    }

    @Test
    fun unknownIngredientUnavailable() {
        val incident = UnavailableIncident(
            1,
            1,
            supplier,
            "Ramen",
            5
        )
        incident.execute()

        assertTrue(supplier.isAvailable("Potato"))
        assertTrue(supplier.isAvailable("Onion"))
    }

    @Test
    fun potatoAvailableAfter2Evenings() {
        val incident = UnavailableIncident(
            1,
            1,
            supplier,
            "Potato",
            2
        )
        incident.execute()

        Ticks.advanceEvening()
        Ticks.advanceEvening()
        assertTrue(supplier.isAvailable("Potato"))
    }

    // checks the whole window evening by evening: unavailable on the first and last
    // evening of the incident, available again exactly one evening after
    @Test
    fun potatoUnavailableForWholeDurationThenAvailable() {
        val incident = UnavailableIncident(
            2,
            1,
            supplier,
            "Potato",
            3
        )
        incident.execute()

        // evening 1, the evening the incident happens
        assertFalse(supplier.isAvailable("Potato"))

        Ticks.advanceEvening()
        // evening 2, still inside the duration
        assertFalse(supplier.isAvailable("Potato"))

        Ticks.advanceEvening()
        // evening 3, last evening of the duration
        assertFalse(supplier.isAvailable("Potato"))

        Ticks.advanceEvening()
        // evening 4, duration is over
        assertTrue(supplier.isAvailable("Potato"))
    }

    // the incident's own evening decides the window, not the evening the simulation
    // happens to be on when execute() runs (incidents from earlier evenings get
    // caught up on simulation start, see spec p.30)
    @Test
    fun windowIsBasedOnIncidentEveningNotCurrentTick() {
        // move the simulation forward to evening 5
        Ticks.advanceEvening()
        Ticks.advanceEvening()
        Ticks.advanceEvening()
        Ticks.advanceEvening()

        // incident says evening 2, duration 2 -> window is evening 2 and 3 only
        val incident = UnavailableIncident(
            4,
            2,
            supplier,
            "Potato",
            2
        )
        incident.execute()

        // we are on evening 5 now, well after the window, so it should be available
        // (if the code wrongly used the current tick as the starting evening, this would fail)
        assertTrue(supplier.isAvailable("Potato"))
    }

    // two incidents on the same ingredient: the later execute() just overwrites the
    // unavailable-until value, it does not extend/combine the windows
    @Test
    fun secondIncidentOnSameIngredientOverwritesTheFirst() {
        val longIncident = UnavailableIncident(6, 1, supplier, "Potato", 5)
        longIncident.execute()

        val shortIncident = UnavailableIncident(7, 1, supplier, "Potato", 2)
        shortIncident.execute()

        Ticks.advanceEvening()
        Ticks.advanceEvening()
        // the short incident overwrote the long one, so potato is available again
        // by evening 3 even though the long incident alone would still block it
        assertTrue(supplier.isAvailable("Potato"))
    }

    // a few small edge cases bundled together: smallest legal duration, smallest legal
    // evening, and a wrong-case ingredient name
    @Test
    fun otherEdgeCasesForUnavailability() {
        // evening 0 is the lowest legal value (evening >= 0), reset ticks to get there
        Ticks.init(70)
        val evening0Incident = UnavailableIncident(8, 0, supplier, "Onion", 1)
        evening0Incident.execute()
        assertFalse(supplier.isAvailable("Onion"))
        Ticks.advanceEvening()
        assertTrue(supplier.isAvailable("Onion"))

        // smallest legal duration (duration > 0), so the window is just 1 evening
        val oneDayIncident = UnavailableIncident(9, 1, supplier, "Potato", 1)
        oneDayIncident.execute()
        assertFalse(supplier.isAvailable("Potato"))
        Ticks.advanceEvening()
        assertTrue(supplier.isAvailable("Potato"))

        // ingredient names are matched exactly, wrong case counts as unknown
        val wrongCaseIncident = UnavailableIncident(10, 2, supplier, "potato", 5)
        wrongCaseIncident.execute()
        assertTrue(supplier.isAvailable("Potato"))
    }

    // isAvailable() is just a check, procure() is what the pantry actually calls to
    // restock, so the unavailability needs to block that too, not just the check
    @Test
    fun procureReturnsZeroWhileUnavailableThenWorksAgain() {
        val incident = UnavailableIncident(11, 1, supplier, "Potato", 2)
        incident.execute()

        assertTrue(supplier.procure("Potato", 500) == 0)

        Ticks.advanceEvening()
        Ticks.advanceEvening()
        assertTrue(supplier.procure("Potato", 500) > 0)
    }
}
