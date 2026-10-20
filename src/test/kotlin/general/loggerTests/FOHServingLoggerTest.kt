package general.loggerTests

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import saarlacarte.enums.LogLevel
import saarlacarte.loggers.FOHServingLogger
import kotlin.test.assertEquals

class FOHServingLoggerTest : LoggerTest() {
    lateinit var debugLogger: FOHServingLogger
    lateinit var infoLogger: FOHServingLogger
    lateinit var importantLogger: FOHServingLogger

    @BeforeEach
    fun setUp() {
        debugLogger = FOHServingLogger(LogLevel.DEBUG, outputPathString, 5)
        infoLogger = FOHServingLogger(LogLevel.INFO, outputPathString, 5)
        importantLogger = FOHServingLogger(LogLevel.IMPORTANT, outputPathString, 5)
    }

    @Test
    fun groupNotServedLeftInfo() {
        infoLogger.groupNotServedLeft(2, 12, 4)
        assertEquals(
            "[INFO] Restaurant No Eating (R 5): 2 " +
                "customers of group 12 leave table 4 due to not being served.",
            readOutput()
        )
    }

    @Test
    fun groupNotServedLeftDebug() {
        debugLogger.groupNotServedLeft(2, 12, 4)
        assertEquals(
            "[INFO] Restaurant No Eating (R 5): 2 " +
                "customers of group 12 leave table 4 due to not being served.",
            readOutput()
        )
    }

    @Test
    fun groupNotServedLeftImportant() {
        importantLogger.groupNotServedLeft(2, 12, 4)
        expectNoOutput()
    }

    @Test
    fun servingStatusDebug() {
        debugLogger.servingStatus(2, 13)
        assertEquals(
            "[DEBUG] FOH Serving Status (R 5): 2 waitstaff served 13 meals.",
            readOutput()
        )
    }

    @Test
    fun servingStatusInfo() {
        infoLogger.servingStatus(2, 13)
        expectNoOutput()
    }

    @Test
    fun servingStatusImportant() {
        importantLogger.servingStatus(2, 13)
        expectNoOutput()
    }

    @Test
    fun finishedEatingInfo() {
        infoLogger.finishedEating(6, 12, 2)
        assertEquals(
            "[INFO] FOH Finished Eating (R 5): 6 customers of group" +
                " 12 have finished eating at table 2.",
            readOutput()
        )
    }

    @Test
    fun finishedEatingDebug() {
        debugLogger.finishedEating(6, 12, 2)
        assertEquals(
            "[INFO] FOH Finished Eating (R 5): 6 customers of group" +
                " 12 have finished eating at table 2.",
            readOutput()
        )
    }

    @Test
    fun finishedEatingImportant() {
        importantLogger.finishedEating(6, 12, 2)
        expectNoOutput()
    }

    @Test
    fun escortedImportant() {
        importantLogger.escorted(2, 6, 12, 4)
        assertEquals(
            "[IMPORTANT] FOH Escorting (R 5): Waitstaff 2 escorts 6 customers" +
                " of group 12 from table 4 outside.",
            readOutput()
        )
    }

    @Test
    fun escortedDebug() {
        debugLogger.escorted(2, 6, 12, 4)
        assertEquals(
            "[IMPORTANT] FOH Escorting (R 5): Waitstaff 2 escorts 6 customers" +
                " of group 12 from table 4 outside.",
            readOutput()
        )
    }

    @Test
    fun escortedInfo() {
        infoLogger.escorted(2, 6, 12, 4)
        assertEquals(
            "[IMPORTANT] FOH Escorting (R 5): Waitstaff 2 escorts 6 customers" +
                " of group 12 from table 4 outside.",
            readOutput()
        )
    }

    @Test
    fun eatingStatusDebug() {
        debugLogger.eatingStatus(2, 13)
        assertEquals(
            "[DEBUG] FOH Eating Status (R 5): 2 customers are eating and" +
                " 13 customers have finished eating this tick.",
            readOutput()
        )
    }

    @Test
    fun eatingStatusInfo() {
        infoLogger.eatingStatus(2, 13)
        expectNoOutput()
    }

    @Test
    fun eatingStatusImportant() {
        importantLogger.eatingStatus(2, 13)
        expectNoOutput()
    }

    @Test
    fun escortingStatusDebug() {
        debugLogger.escortingStatus(3, 27)
        assertEquals(
            "[DEBUG] FOH Escorting Status (R 5): 3 waitstaff" +
                " escorted 27 customers this tick.",
            readOutput()
        )
    }

    @Test
    fun escortingStatusInfo() {
        infoLogger.escortingStatus(3, 27)
        expectNoOutput()
    }

    @Test
    fun escortingStatusImportant() {
        importantLogger.escortingStatus(3, 27)
        expectNoOutput()
    }

    @Test
    fun waitstaffServesImportant() {
        importantLogger.waitstaffServes(2, mapOf("bla" to 3, "moreBla" to 4), 3, 8)
        assertEquals(
            "[IMPORTANT] FOH Serving (R 5): Waitstaff 2 serves bla:3,moreBla:4 to table 3 8 ticks after ordering.",
            readOutput()
        )
    }

    @Test
    fun waitstaffServesInfo() {
        infoLogger.waitstaffServes(2, mapOf("moreBla" to 4, "bla" to 3), 3, 8)
        assertEquals(
            "[IMPORTANT] FOH Serving (R 5): Waitstaff 2 serves bla:3,moreBla:4 to table 3 8 ticks after ordering.",
            readOutput()
        )
    }

    @Test
    fun waitstaffServesDebug() {
        debugLogger.waitstaffServes(2, mapOf("moreBla" to 4, "bla" to 3), 3, 8)
        assertEquals(
            "[IMPORTANT] FOH Serving (R 5): Waitstaff 2 serves bla:3,moreBla:4 to table 3 8 ticks after ordering.",
            readOutput()
        )
    }

    @Test
    fun notServingDebug() {
        debugLogger.notServing(6, 12, 4)
        assertEquals(
            "[DEBUG] FOH No Serving (R 5): Waitstaff 6 did not serve 12 meals to table 4.",
            readOutput()
        )
    }

    @Test
    fun serveToDriverImportant() {
        importantLogger.serveToDriver(2, mapOf("bla" to 3, "moreBla" to 4), 1, 14)
        assertEquals(
            "[IMPORTANT] FOH Delivery (R 5): Waitstaff 2 serves bla:3,moreBla:4 meals " +
                "to driver 1 for order 14.",
            readOutput()
        )
    }

    @Test
    fun serveToDriverInfo() {
        infoLogger.serveToDriver(2, mapOf("bla" to 3, "moreBla" to 4), 1, 14)
        assertEquals(
            "[IMPORTANT] FOH Delivery (R 5): Waitstaff 2 serves bla:3,moreBla:4 meals " +
                "to driver 1 for order 14.",
            readOutput()
        )
    }

    @Test
    fun serveToDriverDebug() {
        debugLogger.serveToDriver(2, mapOf("bla" to 3, "moreBla" to 4), 1, 14)
        assertEquals(
            "[IMPORTANT] FOH Delivery (R 5): Waitstaff 2 serves bla:3,moreBla:4 meals " +
                "to driver 1 for order 14.",
            readOutput()
        )
    }

    @Test
    fun notServingWithInfo() {
        infoLogger.notServing(6, 12, 4)
        expectNoOutput()
    }

    @Test
    fun notServingWithImportant() {
        importantLogger.notServing(6, 12, 4)
        expectNoOutput()
    }
}
