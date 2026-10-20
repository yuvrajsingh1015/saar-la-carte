package general.loggerTests

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import saarlacarte.enums.LogLevel
import saarlacarte.loggers.FOHSeatingLogger
import kotlin.test.assertEquals

class FOHSeatingLoggerTest : LoggerTest() {
    lateinit var debugLogger: FOHSeatingLogger
    lateinit var infoLogger: FOHSeatingLogger
    lateinit var importantLogger: FOHSeatingLogger

    @BeforeEach
    fun setUp() {
        debugLogger = FOHSeatingLogger(LogLevel.DEBUG, outputPathString, 5)
        infoLogger = FOHSeatingLogger(LogLevel.INFO, outputPathString, 5)
        importantLogger = FOHSeatingLogger(LogLevel.IMPORTANT, outputPathString, 5)
    }

    @Test
    fun restaurantArrivalInfo() {
        infoLogger.restaurantArrival(12)
        assertEquals(
            "[INFO] Restaurant Arrival (R 5): Group 12 arrived at restaurant 5.",
            readOutput()
        )
    }

    @Test
    fun restaurantArrivalDebug() {
        debugLogger.restaurantArrival(12)
        assertEquals(
            "[INFO] Restaurant Arrival (R 5): Group 12 arrived at restaurant 5.",
            readOutput()
        )
    }

    @Test
    fun restaurantArrivalImportant() {
        importantLogger.restaurantArrival(12)
        expectNoOutput()
    }

    @Test
    fun groupSeatedImportant() {
        importantLogger.groupSeated(12, 4, listOf(1, 2))
        assertEquals(
            "[IMPORTANT] FOH Seating (R 5): Group 12 seated at table 4 by waitstaff 1,2.",
            readOutput()
        )
    }

    @Test
    fun groupSeatedDebug() {
        debugLogger.groupSeated(12, 4, listOf(1, 2))
        assertEquals(
            "[IMPORTANT] FOH Seating (R 5): Group 12 seated at table 4 by waitstaff 1,2.",
            readOutput()
        )
    }

    @Test
    fun groupSeatedInfo() {
        infoLogger.groupSeated(12, 4, listOf(1, 2))
        assertEquals(
            "[IMPORTANT] FOH Seating (R 5): Group 12 seated at table 4 by waitstaff 1,2.",
            readOutput()
        )
    }

    @Test
    fun tablesMergedInfo() {
        infoLogger.tablesMerged(12, listOf(4, 5), 4)
        assertEquals(
            "[INFO] FOH Merging Tables (R 5): For group 12 the tables 4,5 were merged into 4.",
            readOutput()
        )
    }

    @Test
    fun waitersUnavailableInfo() {
        infoLogger.waitersUnavailable(12)
        assertEquals(
            "[INFO] FOH No Seating (R 5): No free waitstaff available for group 12.",
            readOutput()
        )
    }

    @Test
    fun waitersUnavailableDebug() {
        debugLogger.waitersUnavailable(12)
        assertEquals(
            "[INFO] FOH No Seating (R 5): No free waitstaff available for group 12.",
            readOutput()
        )
    }

    @Test
    fun waitersUnavailableImportant() {
        importantLogger.waitersUnavailable(12)
        expectNoOutput()
    }

    @Test
    fun tableUnavailableInfo() {
        infoLogger.tableUnavailable(2, 12)
        assertEquals(
            "[INFO] FOH No Seating (R 5): Assigned waitstaff 2 but no table available, group 12 is sent away.",
            readOutput()
        )
    }

    @Test
    fun tableUnavailableDebug() {
        debugLogger.tableUnavailable(2, 12)
        assertEquals(
            "[INFO] FOH No Seating (R 5): Assigned waitstaff 2 but no table available, group 12 is sent away.",
            readOutput()
        )
    }

    @Test
    fun seatedGroupDebug() {
        debugLogger.seatedGroup(2, 11, 4)
        assertEquals(
            "[DEBUG] FOH Seating Status (R 5): 2 waitstaff seated 11 customers on 4 tables.",
            readOutput()
        )
    }

    @Test
    fun seatedGroupImportant() {
        importantLogger.seatedGroup(2, 11, 4)
        expectNoOutput()
    }

    // run this one after logger gets fixed
    @Test
    fun orderPlacedImportant() {
        importantLogger.orderPlaced(12, 3, mapOf("potatoSoup" to 3, "mangoStickyRice" to 2), listOf(4))
        assertEquals(
            "[IMPORTANT] FOH Ordering (R 5):" +
                " Group 12 placed order 3 of mangoStickyRice:2,potatoSoup:3 with waitstaff 4.",
            readOutput()
        )
    }

    @Test
    fun orderPlacedInfo() {
        infoLogger.orderPlaced(12, 3, mapOf("potatoSoup" to 3, "mangoStickyRice" to 2), listOf(4))
        assertEquals(
            "[IMPORTANT] FOH Ordering (R 5):" +
                " Group 12 placed order 3 of mangoStickyRice:2,potatoSoup:3 with waitstaff 4.",
            readOutput()
        )
    }

    @Test
    fun orderPlacedDebug() {
        debugLogger.orderPlaced(12, 3, mapOf("potatoSoup" to 3, "mangoStickyRice" to 2), listOf(4))
        assertEquals(
            "[IMPORTANT] FOH Ordering (R 5):" +
                " Group 12 placed order 3 of mangoStickyRice:2,potatoSoup:3 with waitstaff 4.",
            readOutput()
        )
    }

    @Test
    fun orderNotPlacedImportant() {
        importantLogger.orderNotPlaced(12, 4)
        assertEquals(
            "[IMPORTANT] FOH No Ordering (R 5):" +
                " Group 12 could not place an order for 4 customers, they leave the restaurant.",
            readOutput()
        )
    }

    @Test
    fun orderNotPlacedInfo() {
        infoLogger.orderNotPlaced(12, 4)
        assertEquals(
            "[IMPORTANT] FOH No Ordering (R 5):" +
                " Group 12 could not place an order for 4 customers, they leave the restaurant.",
            readOutput()
        )
    }

    @Test
    fun orderNotPlacedDebug() {
        debugLogger.orderNotPlaced(12, 4)
        assertEquals(
            "[IMPORTANT] FOH No Ordering (R 5):" +
                " Group 12 could not place an order for 4 customers, they leave the restaurant.",
            readOutput()
        )
    }

    @Test
    fun receivedOrdersDebug() {
        debugLogger.receivedOrders(9, 2)
        assertEquals(
            "[DEBUG] FOH Ordering Status (R 5):" +
                " The restaurant received orders from 9 customers, 2 waitstaff took orders.",
            readOutput()
        )
    }

    @Test
    fun receivedOrdersInfo() {
        infoLogger.receivedOrders(9, 2)
        expectNoOutput()
    }

    @Test
    fun receivedOrdersImportant() {
        importantLogger.receivedOrders(9, 2)
        expectNoOutput()
    }

    // where we expect no output
    @Test
    fun seatedGroupWithInfo() {
        infoLogger.seatedGroup(2, 11, 4)
        expectNoOutput()
    }

    @Test
    fun tableUnavailableWithImportant() {
        importantLogger.tableUnavailable(2, 12)
        expectNoOutput()
    }
}
