package general.loggerTests

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import saarlacarte.enums.LogLevel
import saarlacarte.loggers.DeliveryCustomerLogger
import kotlin.test.assertEquals

class DeliveryCustomerLoggerTest : LoggerTest() {
    lateinit var debugLogger: DeliveryCustomerLogger
    lateinit var infoLogger: DeliveryCustomerLogger
    lateinit var importantLogger: DeliveryCustomerLogger

    @BeforeEach
    fun setUp() {
        debugLogger = DeliveryCustomerLogger(LogLevel.DEBUG, outputPathString, 5)
        infoLogger = DeliveryCustomerLogger(LogLevel.INFO, outputPathString, 5)
        importantLogger = DeliveryCustomerLogger(LogLevel.IMPORTANT, outputPathString, 5)
    }

    @Test
    fun deliveryGivenUpInfo() {
        infoLogger.deliveryGivenUp(12, 3)
        assertEquals(
            "[INFO] Delivery Given Up (R 5): Group 12 gave up on waiting for delivery of order 3.",
            readOutput()
        )
    }

    @Test
    fun deliveryGivenUpDebug() {
        debugLogger.deliveryGivenUp(12, 3)
        assertEquals(
            "[INFO] Delivery Given Up (R 5): Group 12 gave up on waiting for delivery of order 3.",
            readOutput()
        )
    }

    @Test
    fun deliveryGivenUpImportant() {
        importantLogger.deliveryGivenUp(12, 3)
        expectNoOutput()
    }

    @Test
    fun deliveryFinishedEatingInfo() {
        infoLogger.deliveryFinishedEating(12)
        assertEquals(
            "[INFO] Delivery Finished Eating (R 5): Group 12 has finished eating.",
            readOutput()
        )
    }

    @Test
    fun deliveryFinishedEatingWithDebug() {
        debugLogger.deliveryFinishedEating(12)
        assertEquals(
            "[INFO] Delivery Finished Eating (R 5): Group 12 has finished eating.",
            readOutput()
        )
    }

    @Test
    fun deliveryFinishedEatingWithImportant() {
        importantLogger.deliveryFinishedEating(12)
        expectNoOutput()
    }
}
