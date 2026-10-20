package general.loggerTests

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import saarlacarte.enums.LogLevel
import saarlacarte.enums.MeasuringUnit
import saarlacarte.food.Ingredient
import saarlacarte.incidents.UnavailableIncident
import saarlacarte.loggers.SimulationLogger
import saarlacarte.simulation.Supplier
import saarlacarte.simulation.Ticks
import kotlin.test.assertEquals

class SimulationLoggerTest : LoggerTest() {
    lateinit var debugLogger: SimulationLogger
    lateinit var infoLogger: SimulationLogger
    lateinit var importantLogger: SimulationLogger

    @BeforeEach
    fun initLoggers() {
        debugLogger = SimulationLogger(LogLevel.DEBUG, outputPathString)
        infoLogger = SimulationLogger(LogLevel.INFO, outputPathString)
        importantLogger = SimulationLogger(LogLevel.IMPORTANT, outputPathString)
    }

    @Test
    fun initializationDoneDebug() {
        debugLogger.initializationDone("food.json")
        assertEquals(
            "[INFO] Initialization Info: food.json successfully parsed and validated.",
            readOutput()
        )
    }

    @Test
    fun initializationDoneInfo() {
        infoLogger.initializationDone("food.json")
        assertEquals(
            "[INFO] Initialization Info: food.json successfully parsed and validated.",
            readOutput()
        )
    }

    @Test
    fun initializationDoneImportant() {
        importantLogger.initializationDone("food.json")
        expectNoOutput()
    }

    @Test
    fun initializationInvalidDebug() {
        debugLogger.initializationInvalid("food.json")
        assertEquals(
            "[IMPORTANT] Initialization Info: food.json is invalid.",
            readOutput()
        )
    }

    @Test
    fun initializationInvalidInfo() {
        infoLogger.initializationInvalid("food.json")
        assertEquals(
            "[IMPORTANT] Initialization Info: food.json is invalid.",
            readOutput()
        )
    }

    @Test
    fun initializationInvalidImportant() {
        importantLogger.initializationInvalid("food.json")
        assertEquals(
            "[IMPORTANT] Initialization Info: food.json is invalid.",
            readOutput()
        )
    }

    @Test
    fun simulationStartDebug() {
        debugLogger.simulationStart()
        assertEquals(
            "[INFO] Simulation Info: Simulation started.",
            readOutput()
        )
    }

    @Test
    fun simulationStartInfo() {
        infoLogger.simulationStart()
        assertEquals(
            "[INFO] Simulation Info: Simulation started.",
            readOutput()
        )
    }

    @Test
    fun simulationStartImportant() {
        importantLogger.simulationStart()
        expectNoOutput()
    }

    @Test
    fun incidentOccurredDebug() {
        Ticks.init(200)
        repeat(3) {
            Ticks.advanceEvening()
        }
        val ingredient = Ingredient("potato", MeasuringUnit.X, 2)
        val supplier = Supplier(listOf(ingredient), mutableMapOf("potato" to 200))
        val incident = UnavailableIncident(5, 3, supplier, "potato", 2)
        debugLogger.incidentOccurred(incident, "UNAVAILABLE")
        assertEquals(
            "[IMPORTANT] Incident: Incident 5 of type UNAVAILABLE occurred before evening 3.",
            readOutput()
        )
    }

    @Test
    fun incidentOccurredInfo() {
        Ticks.init(200)
        repeat(3) {
            Ticks.advanceEvening()
        }
        val ingredient = Ingredient("potato", MeasuringUnit.X, 2)
        val supplier = Supplier(listOf(ingredient), mutableMapOf("potato" to 200))
        val incident = UnavailableIncident(5, 3, supplier, "potato", 2)
        infoLogger.incidentOccurred(incident, "UNAVAILABLE")
        assertEquals(
            "[IMPORTANT] Incident: Incident 5 of type UNAVAILABLE occurred before evening 3.",
            readOutput()
        )
    }

    @Test
    fun incidentOccurredImportant() {
        Ticks.init(200)
        repeat(3) {
            Ticks.advanceEvening()
        }
        val ingredient = Ingredient("potato", MeasuringUnit.X, 2)
        val supplier = Supplier(listOf(ingredient), mutableMapOf("potato" to 200))
        val incident = UnavailableIncident(5, 3, supplier, "potato", 2)
        importantLogger.incidentOccurred(incident, "UNAVAILABLE")
        assertEquals(
            "[IMPORTANT] Incident: Incident 5 of type UNAVAILABLE occurred before evening 3.",
            readOutput()
        )
    }

    @Test
    fun preparationStartsDebug() {
        Ticks.init(200)
        repeat(3) {
            Ticks.advanceEvening()
        }
        debugLogger.preparationStarts()
        assertEquals(
            "[IMPORTANT] Preparation: Preparation for evening 3 starts.",
            readOutput()
        )
    }

    @Test
    fun preparationStartsInfo() {
        Ticks.init(200)
        repeat(3) {
            Ticks.advanceEvening()
        }
        infoLogger.preparationStarts()
        assertEquals(
            "[IMPORTANT] Preparation: Preparation for evening 3 starts.",
            readOutput()
        )
    }

    @Test
    fun preparationStartsImportant() {
        Ticks.init(200)
        repeat(3) {
            Ticks.advanceEvening()
        }
        importantLogger.preparationStarts()
        assertEquals(
            "[IMPORTANT] Preparation: Preparation for evening 3 starts.",
            readOutput()
        )
    }

    @Test
    fun servingStartDebug() {
        Ticks.init(100)
        repeat(2) {
            Ticks.advanceEvening()
        }
        debugLogger.servingStart()
        assertEquals(
            "[IMPORTANT] Serving: Serving of evening 2 starts.",
            readOutput()
        )
    }

    @Test
    fun servingStartInfo() {
        Ticks.init(100)
        repeat(2) {
            Ticks.advanceEvening()
        }
        infoLogger.servingStart()
        assertEquals(
            "[IMPORTANT] Serving: Serving of evening 2 starts.",
            readOutput()
        )
    }

    @Test
    fun servingStartImportant() {
        Ticks.init(100)
        repeat(2) {
            Ticks.advanceEvening()
        }
        importantLogger.servingStart()
        assertEquals(
            "[IMPORTANT] Serving: Serving of evening 2 starts.",
            readOutput()
        )
    }

    @Test
    fun tickStartDebug() {
        Ticks.init(24)
        Ticks.advanceEvening()
        repeat(20) {
            Ticks.advanceTick()
        }
        debugLogger.tickStart()
        assertEquals(
            "[IMPORTANT] Simulation: Tick 20 (1) started.",
            readOutput()
        )
    }

    @Test
    fun tickStartInfo() {
        Ticks.init(24)
        Ticks.advanceEvening()
        repeat(20) {
            Ticks.advanceTick()
        }
        infoLogger.tickStart()
        assertEquals(
            "[IMPORTANT] Simulation: Tick 20 (1) started.",
            readOutput()
        )
    }

    @Test
    fun tickStartImportant() {
        Ticks.init(24)
        Ticks.advanceEvening()
        repeat(20) {
            Ticks.advanceTick()
        }
        importantLogger.tickStart()
        assertEquals(
            "[IMPORTANT] Simulation: Tick 20 (1) started.",
            readOutput()
        )
    }

    @Test
    fun restaurantNotDecidedDebug() {
        debugLogger.restaurantNotDecided(3)
        assertEquals(
            "[DEBUG] Restaurant No Decision: Group 3 could not decide for a restaurant.",
            readOutput()
        )
    }

    @Test
    fun restaurantNotDecidedInfo() {
        infoLogger.restaurantNotDecided(3)
        expectNoOutput()
    }

    @Test
    fun restaurantNotDecidedImportant() {
        importantLogger.restaurantNotDecided(3)
        expectNoOutput()
    }

    @Test
    fun servingEndsDebug() {
        Ticks.init(30)
        Ticks.advanceEvening()
        repeat(24) {
            Ticks.advanceTick()
        }
        debugLogger.servingEnds()
        assertEquals(
            "[IMPORTANT] Serving: Serving of evening 1 ends.",
            readOutput()
        )
    }

    @Test
    fun servingEndsInfo() {
        Ticks.init(30)
        Ticks.advanceEvening()
        repeat(24) {
            Ticks.advanceTick()
        }
        infoLogger.servingEnds()
        assertEquals(
            "[IMPORTANT] Serving: Serving of evening 1 ends.",
            readOutput()
        )
    }

    @Test
    fun servingEndsImportant() {
        Ticks.init(30)
        Ticks.advanceEvening()
        repeat(24) {
            Ticks.advanceTick()
        }
        importantLogger.servingEnds()
        assertEquals(
            "[IMPORTANT] Serving: Serving of evening 1 ends.",
            readOutput()
        )
    }

    @Test
    fun statisticsCalculatedDebug() {
        debugLogger.statisticsCalculated()
        assertEquals(
            "[IMPORTANT] Simulation Info: Simulation statistics are calculated.",
            readOutput()
        )
    }

    @Test
    fun statisticsCalculatedInfo() {
        infoLogger.statisticsCalculated()
        assertEquals(
            "[IMPORTANT] Simulation Info: Simulation statistics are calculated.",
            readOutput()
        )
    }

    @Test
    fun statisticsCalculatedImportant() {
        importantLogger.statisticsCalculated()
        assertEquals(
            "[IMPORTANT] Simulation Info: Simulation statistics are calculated.",
            readOutput()
        )
    }
}
