package general.loggerTests

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import saarlacarte.enums.LogLevel
import saarlacarte.loggers.RestaurantLogger
import kotlin.test.assertEquals

class RestaurantLoggerTest : LoggerTest() {
    lateinit var debugLogger: RestaurantLogger
    lateinit var infoLogger: RestaurantLogger
    lateinit var importantLogger: RestaurantLogger

    @BeforeEach
    fun setUp() {
        debugLogger = RestaurantLogger(LogLevel.DEBUG, outputPathString, 5)
        infoLogger = RestaurantLogger(LogLevel.INFO, outputPathString, 5)
        importantLogger = RestaurantLogger(LogLevel.IMPORTANT, outputPathString, 5)
    }

    @Test
    fun restaurantDecisionDebug() {
        debugLogger.restaurantDecided(12)
        assertEquals(
            "[DEBUG] Restaurant Decision: Group 12 decided on restaurant 5.",
            readOutput()
        )
    }

    @Test
    fun restaurantDecisionImportant() {
        importantLogger.restaurantDecided(12)
        expectNoOutput()
    }

    @Test
    fun restaurantStartsTickDebug() {
        debugLogger.restaurantStartsTick(5)
        assertEquals(
            "[DEBUG] Restaurant Start (R 5): Restaurant 5 simulates a tick.",
            readOutput()
        )
    }

    @Test
    fun restaurantStartsTickInfo() {
        infoLogger.restaurantStartsTick(5)
        expectNoOutput()
    }

    @Test
    fun restaurantStartsTickImportant() {
        importantLogger.restaurantStartsTick(5)
        expectNoOutput()
    }

    @Test
    fun ratingStatusDebug() {
        debugLogger.ratingStatus(3)
        assertEquals(
            "[DEBUG] Rating Status (R 5): 3 groups performed ratings this tick.",
            readOutput()
        )
    }

    @Test
    fun ratingStatusInfo() {
        infoLogger.ratingStatus(3)
        expectNoOutput()
    }

    @Test
    fun ratingStatusImportant() {
        importantLogger.ratingStatus(3)
        expectNoOutput()
    }

    @Test
    fun receivedRatingInfo() {
        infoLogger.receivedRating(12, "positive", 1, 3)
        assertEquals(
            "[INFO] Rating (R 5): Group 12 rates the restaurant 5 with positive rating," +
                " leading to 1 positive ratings and 3 negative ratings.",
            readOutput()
        )
    }

    @Test
    fun receivedRatingDebug() {
        debugLogger.receivedRating(12, "positive", 1, 3)
        assertEquals(
            "[INFO] Rating (R 5): Group 12 rates the restaurant 5 with positive rating," +
                " leading to 1 positive ratings and 3 negative ratings.",
            readOutput()
        )
    }

    @Test
    fun receivedRatingImportant() {
        importantLogger.receivedRating(12, "positive", 1, 3)
        expectNoOutput()
    }

    @Test
    fun restaurantEndTickDebug() {
        debugLogger.restaurantEndTick()
        assertEquals(
            "[DEBUG] Restaurant End (R 5): Restaurant 5 finished simulating the tick.",
            readOutput()
        )
    }

    @Test
    fun restaurantEndTickInfo() {
        infoLogger.restaurantEndTick()
        expectNoOutput()
    }

    @Test
    fun cookedTotalImportant() {
        importantLogger.cookedTotal(23)
        assertEquals(
            "[IMPORTANT] Simulation Statistics: Restaurant 5 cooked 23 meals.",
            readOutput()
        )
    }

    @Test
    fun cookedTotalInfo() {
        infoLogger.cookedTotal(23)
        assertEquals(
            "[IMPORTANT] Simulation Statistics: Restaurant 5 cooked 23 meals.",
            readOutput()
        )
    }

    @Test
    fun cookedTotalDebug() {
        debugLogger.cookedTotal(23)
        assertEquals(
            "[IMPORTANT] Simulation Statistics: Restaurant 5 cooked 23 meals.",
            readOutput()
        )
    }

    @Test
    fun servedTotalImportant() {
        importantLogger.servedTotal(23)
        assertEquals(
            "[IMPORTANT] Simulation Statistics: Restaurant 5 served 23 customers.",
            readOutput()
        )
    }

    @Test
    fun servedTotalInfo() {
        infoLogger.servedTotal(23)
        assertEquals(
            "[IMPORTANT] Simulation Statistics: Restaurant 5 served 23 customers.",
            readOutput()
        )
    }

    @Test
    fun servedTotalDebug() {
        debugLogger.servedTotal(23)
        assertEquals(
            "[IMPORTANT] Simulation Statistics: Restaurant 5 served 23 customers.",
            readOutput()
        )
    }

    @Test
    fun deliveredTotalImportant() {
        importantLogger.deliveredTotal(23)
        assertEquals(
            "[IMPORTANT] Simulation Statistics: Restaurant 5 delivered meals to 23 customers.",
            readOutput()
        )
    }

    @Test
    fun deliveredTotalInfo() {
        infoLogger.deliveredTotal(23)
        assertEquals(
            "[IMPORTANT] Simulation Statistics: Restaurant 5 delivered meals to 23 customers.",
            readOutput()
        )
    }

    @Test
    fun deliveredTotalDebug() {
        debugLogger.deliveredTotal(23)
        assertEquals(
            "[IMPORTANT] Simulation Statistics: Restaurant 5 delivered meals to 23 customers.",
            readOutput()
        )
    }

    @Test
    fun ratingsTotalImportant() {
        importantLogger.ratingsTotal(23)
        assertEquals(
            "[IMPORTANT] Simulation Statistics: Restaurant 5 received 23 ratings.",
            readOutput()
        )
    }

    @Test
    fun ratingsTotalInfo() {
        infoLogger.ratingsTotal(23)
        assertEquals(
            "[IMPORTANT] Simulation Statistics: Restaurant 5 received 23 ratings.",
            readOutput()
        )
    }

    @Test
    fun ratingsTotalDebug() {
        debugLogger.ratingsTotal(23)
        assertEquals(
            "[IMPORTANT] Simulation Statistics: Restaurant 5 received 23 ratings.",
            readOutput()
        )
    }

    @Test
    fun restaurantEndTickWithImportant() {
        importantLogger.restaurantEndTick()
        expectNoOutput()
    }

    @Test
    fun restaurantDecisonWithInfo() {
        infoLogger.restaurantDecided(12)
        expectNoOutput()
    }
}
