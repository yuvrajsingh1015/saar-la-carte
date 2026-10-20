package general.loggerTests

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import saarlacarte.enums.LogLevel
import saarlacarte.loggers.DriverLogger
import kotlin.test.assertEquals

class DriverLoggerTest : LoggerTest() {
    lateinit var debugLogger: DriverLogger
    lateinit var infoLogger: DriverLogger
    lateinit var importantLogger: DriverLogger

    @BeforeEach
    fun initLoggers() {
        debugLogger = DriverLogger(LogLevel.DEBUG, outputPathString, 1)
        infoLogger = DriverLogger(LogLevel.INFO, outputPathString, 1)
        importantLogger = DriverLogger(LogLevel.IMPORTANT, outputPathString, 1)
    }

    @Test
    fun deliveryPreparationDebug() {
        debugLogger.deliveryPreparation(2, 3, 4, 5)
        assertEquals(
            "[INFO] Delivery Preparation (R 1): " +
                "Driver 2 prepares driving order 3 to group 4, which will take 5 ticks.",
            readOutput()
        )
    }

    @Test
    fun deliveryPreparationInfo() {
        infoLogger.deliveryPreparation(2, 3, 4, 5)
        assertEquals(
            "[INFO] Delivery Preparation (R 1): " +
                "Driver 2 prepares driving order 3 to group 4, which will take 5 ticks.",
            readOutput()
        )
    }

    @Test
    fun deliveryPreparationImportant() {
        importantLogger.deliveryPreparation(2, 3, 4, 5)
        expectNoOutput()
    }

    @Test
    fun deliveryDrivingDebug() {
        debugLogger.deliveryDriving(3, 5, 7)
        assertEquals(
            "[DEBUG] Delivery Driving (R 1): Driver 3 drove 5 km and needs 7 more ticks.",
            readOutput()
        )
    }

    @Test
    fun deliveryDrivingInfo() {
        infoLogger.deliveryDriving(3, 5, 7)
        expectNoOutput()
    }

    @Test
    fun deliveryDrivingImportant() {
        importantLogger.deliveryDriving(3, 5, 7)
        expectNoOutput()
    }

    @Test
    fun deliveryArrivalDebug() {
        debugLogger.deliveryArrival(2, 4, 6)
        assertEquals(
            "[INFO] Delivery Arrival (R 1): Driver 2 arrived at group 4 with order 6.",
            readOutput()
        )
    }

    @Test
    fun deliveryArrivalInfo() {
        infoLogger.deliveryArrival(2, 4, 6)
        assertEquals(
            "[INFO] Delivery Arrival (R 1): Driver 2 arrived at group 4 with order 6.",
            readOutput()
        )
    }

    @Test
    fun deliveryArrivalImportant() {
        importantLogger.deliveryArrival(2, 4, 6)
        expectNoOutput()
    }

    @Test
    fun deliveryFinishedDebug() {
        debugLogger.deliveryFinished(2, 4, 6)
        assertEquals(
            "[IMPORTANT] Delivery Finished (R 1): Driver 2 gave delivery of order 6 to group 4.",
            readOutput()
        )
    }

    @Test
    fun deliveryFinishedInfo() {
        infoLogger.deliveryFinished(2, 4, 6)
        assertEquals(
            "[IMPORTANT] Delivery Finished (R 1): Driver 2 gave delivery of order 6 to group 4.",
            readOutput()
        )
    }

    @Test
    fun deliveryFinishedImportant() {
        importantLogger.deliveryFinished(2, 4, 6)
        assertEquals(
            "[IMPORTANT] Delivery Finished (R 1): Driver 2 gave delivery of order 6 to group 4.",
            readOutput()
        )
    }

    @Test
    fun deliveryFailedDebug() {
        debugLogger.deliveryFailed(2, 4, 6)
        assertEquals(
            "[IMPORTANT] Delivery Failed (R 1): Driver 2 failed to deliver order 6 to group 4.",
            readOutput()
        )
    }

    @Test
    fun deliveryFailedInfo() {
        infoLogger.deliveryFailed(2, 4, 6)
        assertEquals(
            "[IMPORTANT] Delivery Failed (R 1): Driver 2 failed to deliver order 6 to group 4.",
            readOutput()
        )
    }

    @Test
    fun deliveryFailedImportant() {
        importantLogger.deliveryFailed(2, 4, 6)
        assertEquals(
            "[IMPORTANT] Delivery Failed (R 1): Driver 2 failed to deliver order 6 to group 4.",
            readOutput()
        )
    }

    @Test
    fun driverReturnedDebug() {
        debugLogger.driverReturned(3)
        assertEquals(
            "[INFO] Delivery Returned (R 1): Driver 3 has returned.",
            readOutput()
        )
    }

    @Test
    fun driverReturnedInfo() {
        infoLogger.driverReturned(3)
        assertEquals(
            "[INFO] Delivery Returned (R 1): Driver 3 has returned.",
            readOutput()
        )
    }

    @Test
    fun driverReturnedImportant() {
        importantLogger.driverReturned(3)
        expectNoOutput()
    }
}
