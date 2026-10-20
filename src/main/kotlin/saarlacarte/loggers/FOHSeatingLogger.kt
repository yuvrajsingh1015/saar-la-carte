package saarlacarte.loggers

import saarlacarte.enums.LogLevel

const val COMMA = ","

/**
 * LOGGING DETAILS:
 * INFO logs will also be output if a log level of DEBUG is specified,
 * IMPORTANT logs will be output no matter the log level
 */

/**
Front of house seating logger
 **/
class FOHSeatingLogger(
    logLevel: LogLevel,
    outPath: String?,
    restaurantId: Int
) : Logger(logLevel, outPath, restaurantId) {
    /** Customer group arrives at the restaurant **/
    fun restaurantArrival(groupId: Int) {
        if (logLevel == LogLevel.INFO || logLevel == LogLevel.DEBUG) {
            log(
                "[INFO] Restaurant Arrival (R $restaurantId): Group $groupId arrived " +
                    "at restaurant $restaurantId."
            )
        }
    }

    /** Customer group is seated by the waitstaff **/
    fun groupSeated(groupId: Int, tableId: Int, waitstaffIds: List<Int>) {
        val idsString = waitstaffIds.sorted().joinToString(separator = ",")
        log(
            "[IMPORTANT] FOH Seating (R $restaurantId): Group $groupId seated at " +
                "table $tableId by waitstaff $idsString."
        )
    }

    /** Tables have been merged **/
    fun tablesMerged(groupId: Int, tableIds: List<Int>, newTableId: Int) {
        val tablesString = tableIds.sorted().joinToString(separator = COMMA)
        if (logLevel == LogLevel.INFO || logLevel == LogLevel.DEBUG) {
            log(
                "[INFO] FOH Merging Tables (R $restaurantId): For group $groupId the " +
                    "tables $tablesString were merged into $newTableId."
            )
        }
    }

    /** No waiter was available for seating a customer group **/
    fun waitersUnavailable(groupId: Int) {
        if (logLevel == LogLevel.INFO || logLevel == LogLevel.DEBUG) {
            log(
                "[INFO] FOH No Seating (R $restaurantId): No free waitstaff available " +
                    "for group $groupId."
            )
        }
    }

    /** No seat was available for a customer group, so it leaves the restaurant **/
    fun tableUnavailable(waitstaffId: Int, groupId: Int) {
        if (logLevel == LogLevel.INFO || logLevel == LogLevel.DEBUG) {
            log(
                "[INFO] FOH No Seating (R $restaurantId): Assigned waitstaff " +
                    "$waitstaffId but no table available, group $groupId is sent away."
            )
        }
    }

    /** Seating attempt was successful **/
    fun seatedGroup(waitstaffNumber: Int, customersNumber: Int, tablesNumber: Int) {
        if (logLevel == LogLevel.DEBUG) {
            log(
                "[DEBUG] FOH Seating Status (R $restaurantId): $waitstaffNumber " +
                    "waitstaff seated $customersNumber customers on $tablesNumber tables."
            )
        }
    }

    /** An order was placed **/
    fun orderPlaced(groupId: Int, orderId: Int, dishNameToAmount: Map<String, Int>, waitstaffIds: List<Int>) {
        val waitersString = waitstaffIds.sorted().joinToString(separator = COMMA)
        val stringDishes = keyValueMappingString(dishNameToAmount)
        val logMessage =
            if (waitersString == "") { // for delivery orders
                "[IMPORTANT] FOH Ordering (R $restaurantId): Group $groupId placed " +
                    "order $orderId of $stringDishes."
            } else {
                "[IMPORTANT] FOH Ordering (R $restaurantId): Group $groupId placed " +
                    "order $orderId of $stringDishes with waitstaff $waitersString."
            }
        log(logMessage)
    }

    /** One or more customers couldn't find a dish to order and leave the restaurant **/
    fun orderNotPlaced(groupId: Int, groupSize: Int) {
        log(
            "[IMPORTANT] FOH No Ordering (R $restaurantId): Group $groupId could " +
                "not place an order for $groupSize customers, they leave the " +
                "restaurant."
        )
    }

    /** Ordering status: received orders, waitstaff taking orders **/
    fun receivedOrders(customersNumber: Int, waitstaffNumber: Int) {
        if (logLevel == LogLevel.DEBUG) {
            log(
                "[DEBUG] FOH Ordering Status (R $restaurantId): The restaurant received " +
                    "orders from $customersNumber customers, $waitstaffNumber waitstaff " +
                    "took orders."
            )
        }
    }
}
