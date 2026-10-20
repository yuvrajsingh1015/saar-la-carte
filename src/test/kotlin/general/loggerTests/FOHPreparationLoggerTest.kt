package general.loggerTests

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import saarlacarte.enums.LogLevel
import saarlacarte.loggers.FOHPreparationLogger
import kotlin.test.assertEquals

class FOHPreparationLoggerTest : LoggerTest() {
    lateinit var debugLogger: FOHPreparationLogger
    lateinit var infoLogger: FOHPreparationLogger
    lateinit var importantLogger: FOHPreparationLogger

    @BeforeEach
    fun initLoggers() {
        debugLogger = FOHPreparationLogger(LogLevel.DEBUG, outputPathString, 100)
        infoLogger = FOHPreparationLogger(LogLevel.INFO, outputPathString, 100)
        importantLogger = FOHPreparationLogger(LogLevel.IMPORTANT, outputPathString, 100)
    }

    @Test
    fun tableNotReservedDebug() {
        debugLogger.tableNotReserved(2)
        assertEquals(
            "[IMPORTANT] FOH No Reserving (R 100): No table could be reserved for group 2.",
            readOutput()
        )
    }

    @Test
    fun tableNotReservedInfo() {
        infoLogger.tableNotReserved(2)
        assertEquals(
            "[IMPORTANT] FOH No Reserving (R 100): No table could be reserved for group 2.",
            readOutput()
        )
    }

    @Test
    fun tableNotReservedImportant() {
        importantLogger.tableNotReserved(2)
        assertEquals(
            "[IMPORTANT] FOH No Reserving (R 100): No table could be reserved for group 2.",
            readOutput()
        )
    }

    @Test
    fun restaurantDecidedDebug() {
        debugLogger.restaurantDecided(2)
        assertEquals(
            "[DEBUG] Restaurant Decision: Group 2 decided on restaurant 100.",
            readOutput()
        )
    }

    @Test
    fun restaurantDecidedInfo() {
        infoLogger.restaurantDecided(2)
        expectNoOutput()
    }

    @Test
    fun restaurantDecidedImportant() {
        importantLogger.restaurantDecided(2)
        expectNoOutput()
    }
}
