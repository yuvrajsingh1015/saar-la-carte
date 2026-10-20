package general.simulationTests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import saarlacarte.enums.LogLevel
import saarlacarte.enums.RestaurantType
import saarlacarte.enums.StaffType
import saarlacarte.incidents.Incident
import saarlacarte.incidents.StaffIncident
import saarlacarte.loggers.SimulationLogger
import saarlacarte.restaurant.FrontOfHouse
import saarlacarte.restaurant.Kitchen
import saarlacarte.restaurant.Menu
import saarlacarte.restaurant.Restaurant
import saarlacarte.restaurant.Staff
import saarlacarte.simulation.Simulation
import saarlacarte.simulation.Supplier
import saarlacarte.simulation.Ticks
import java.nio.file.Path
import kotlin.io.path.createTempFile
import kotlin.io.path.readLines
import kotlin.test.Test

private const val TICKS_PER_EVENING = 24

/** Unit tests for Simulation **/
class SimulationUnitTest {

    private lateinit var simulation: Simulation
    private lateinit var logFile: Path

    @BeforeEach
    fun setUp() {
        logFile = createTempFile(prefix = "simulation-unitTest", suffix = ".log")
        simulation = Simulation(SimulationLogger(LogLevel.DEBUG, logFile.toString()))
        Ticks.init(TICKS_PER_EVENING) // default value, may be reinitialized inside other tests
    }

    @Test
    fun `test - maxTicks zero skips all evenings`() {
        Ticks.init(0)
        val log = runSimulation()
        assertTrue(log.any { it.contains("Simulation started.") })
        assertTrue(log.any { it.contains("Simulation statistics are calculated.") })
        assertFalse(log.any { it.contains("Preparation:") })
        assertFalse(log.any { it.contains("Incident:") })
    }

    @Test
    fun `test - applies this evening incidents in ascending id order`() {
        val executed = mutableListOf<Int>()
        simulation.incidents = listOf(
            IncidentStub(2, 1, executed),
            IncidentStub(1, 1, executed),
            IncidentStub(3, 2, executed)
        )
        val log = runSimulation()
        assertEquals(listOf(1, 2), executed)
        val incidentLines = log.filter { it.contains("Incident:") }
        assertEquals(2, incidentLines.size)
        assertTrue(incidentLines[0].contains("Incident 1"))
        assertTrue(incidentLines[1].contains("Incident 2"))
        assertFalse(incidentLines.any { it.contains("Incident 3") })
    }

    @Test
    fun `test - restaurants serve in ascending id order`() {
        val first = restaurant(3)
        val second = restaurant(1)
        simulation.restaurants = listOf(first, second)
        simulation.assignLoggers()
        val log = runSimulation()
        val start1 = log.indexOfFirst { it.startsWith("[DEBUG] Restaurant Start (R 1):") }
        val start3 = log.indexOfFirst { it.startsWith("[DEBUG] Restaurant Start (R 3):") }
        val end1 = log.indexOfFirst { it.startsWith("[DEBUG] Restaurant End (R 1):") }
        val end3 = log.indexOfFirst { it.startsWith("[DEBUG] Restaurant End (R 3):") }
        assertTrue(start1 in 0..<end1)
        assertTrue(start3 in (end1 + 1)..<end3)
    }

    @Test
    fun `test - ticks init to zero clears the limit`() {
        Ticks.init(5)
        Ticks.init(0)
        assertEquals(0, Ticks.getMaxTicks())
    }

    @Test
    fun `test - assign customers tick does not decide by default`() {
        simulation.restaurants = listOf(restaurant(1))
        simulation.assignLoggers()
        val log = runSimulation()
        assertFalse(log.any { it.contains("Restaurant Decision") })
        assertFalse(log.any { it.contains("Restaurant No Decision") })
    }

    @Test
    fun `test - assign loggers stores restaurant logger`() {
        val restaurant = restaurant(1)
        val sim = Simulation(SimulationLogger(LogLevel.IMPORTANT, null))
        sim.restaurants = listOf(restaurant)
        sim.assignLoggers()
        assertNotNull(restaurant.logger)
    }

    @Test
    fun `test - one tick does not log serving end`() {
        Ticks.init(1)
        val log = runSimulation()
        assertTrue(log.any { it.contains("Tick 1 (1) started.") })
        assertFalse(log.any { it.contains("Serving of evening 1 ends.") })
    }

    @Test
    fun `test - twenty four ticks ends evening and does not start evening two`() {
        val log = runSimulation()
        assertTrue(log.any { it.contains("Tick 24 (1) started.") })
        assertTrue(log.any { it.contains("Serving of evening 1 ends.") })
        assertFalse(log.any { it.contains("Preparation for evening 2") })
        assertTrue(log.any { it.contains("Simulation statistics are calculated.") })
    }

    @Test
    fun `test - staffIncident executes and logs Staff type`() {
        val staff = Staff(0, emptyMap(), 0)
        simulation.incidents = listOf(StaffIncident(1, 1, 1, StaffType.WAITSTAFF, staff))
        val log = runSimulation()
        assertEquals(1, staff.getWaiters().size)
        assertTrue(log.any { it.contains("Incident 1 of type") })
    }

    @Test
    fun `test - empty restaurants and incidents don't crash`() {
        val log = runSimulation()
        assertTrue(log.any { it.contains("Tick 1 (1) started.") })
        assertTrue(log.any { it.contains("Simulation statistics are calculated.") })
    }

    @Test
    fun `test - applies only the current evening's incidents`() {
        Ticks.init(TICKS_PER_EVENING + 1)
        val executed = mutableListOf<Int>()
        simulation.incidents = listOf(
            IncidentStub(1, 1, executed),
            IncidentStub(2, 2, executed)
        )
        runSimulation()
        assertEquals(listOf(1, 2), executed)
    }

    @Test
    fun `test - multi-evening simulation completes evenings and terminates at multiple of 24 ticks`() {
        Ticks.init(48)
        simulation.restaurants = listOf(restaurant(1))
        simulation.assignLoggers()

        val log = runSimulation()

        assertTrue(log.any { it.contains("Preparation for evening 1 starts.") })
        assertTrue(log.any { it.contains("Serving of evening 1 starts.") })
        assertTrue(log.any { it.contains("Serving of evening 1 ends.") })
        assertTrue(log.any { it.contains("Preparation for evening 2 starts.") })
        assertTrue(log.any { it.contains("Serving of evening 2 starts.") })
        assertTrue(log.any { it.contains("Serving of evening 2 ends.") })
        assertFalse(log.any { it.contains("Preparation for evening 3 starts.") })
        assertTrue(log.any { it.contains("Simulation statistics are calculated.") })
    }

    @Test
    fun `test - simulation terminates mid-evening without serving end log when tick limit hit`() {
        Ticks.init(10)
        simulation.restaurants = listOf(restaurant(1))
        simulation.assignLoggers()

        val log = runSimulation()

        assertTrue(log.any { it.contains("Tick 10 (1) started.") })
        assertFalse(log.any { it.contains("Tick 11 (1) started.") })
        assertFalse(log.any { it.contains("Serving of evening 1 ends.") })
        assertTrue(log.any { it.contains("Simulation statistics are calculated.") })
    }

    @Test
    fun `test - prep phase executes in ascending restaurant id order`() {
        val firstRes = restaurant(3)
        val secondRes = restaurant(1)
        simulation.restaurants = listOf(firstRes, secondRes)
        simulation.assignLoggers()
        val log = runSimulation()

        // collect all logger messages fired in the prep phase
        val prepPhaseMessages = log.filter { message ->
            message.startsWith("[IMPORTANT] FOH No Reserving") ||
                message.startsWith("[DEBUG] Pantry") ||
                message.startsWith("[INFO] Pantry")
        }
        val loggedRestaurantIds = prepPhaseMessages.map { message ->
            extractRestaurantId(message)
        }

        assertEquals(listOf(1, 3), loggedRestaurantIds)
    }

    private fun runSimulation(): List<String> {
        simulation.run()
        return logFile.readLines()
    }

    private fun restaurant(id: Int): Restaurant {
        val staff = Staff(0, emptyMap(), 0)
        val menu = Menu(Supplier(emptyList(), mutableMapOf()), emptyList(), RestaurantType.ASIAN)
        return Restaurant(
            id,
            "R$id",
            RestaurantType.ASIAN,
            1,
            TICKS_PER_EVENING,
            false,
            staff,
            0,
            0,
            FrontOfHouse(emptyList(), staff.getWaiters(), null, menu),
            Kitchen(staff.getCooks(), menu),
            0
        )
    }

    private fun extractRestaurantId(str: String): Int {
        return Regex("\\d+").findAll(str).map { it.value.toInt() }.toList().first()
    }
}

private class IncidentStub(
    id: Int,
    evening: Int,
    private val executedIds: MutableList<Int>
) : Incident(id, evening) {
    override fun execute() {
        executedIds.add(id)
    }
}
