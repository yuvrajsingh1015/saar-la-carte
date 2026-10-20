package saarlacarte.loggers

import saarlacarte.enums.LogLevel

/**
 * LOGGING DETAILS:
 * INFO logs will also be output if a log level of DEBUG is specified,
 * IMPORTANT logs will be output no matter the log level
 */

/**
Front of house serving logger
 **/
class FOHServingLogger(
    logLevel: LogLevel,
    outPath: String?,
    restaurantId: Int
) : Logger(logLevel, outPath, restaurantId) {
    /****/
    fun groupNotServedLeft(customersNumber: Int, groupId: Int, tableId: Int) {
        if (logLevel == LogLevel.DEBUG || logLevel == LogLevel.INFO) {
            log(
                "[INFO] Restaurant No Eating (R $restaurantId): $customersNumber " +
                    "customers of group $groupId leave table $tableId due to not being " +
                    "served."
            )
        }
    }

    /** Summary of all SERVING actions done this tick **/
    fun servingStatus(waitstaffNumber: Int, mealsNumber: Int) {
        if (logLevel == LogLevel.DEBUG) {
            log(
                "[DEBUG] FOH Serving Status (R $restaurantId): $waitstaffNumber " +
                    "waitstaff served $mealsNumber meals."
            )
        }
    }

    /** Customers finished eating **/
    fun finishedEating(customersNumber: Int, groupId: Int, tableId: Int) {
        if (logLevel == LogLevel.DEBUG || logLevel == LogLevel.INFO) {
            log(
                "[INFO] FOH Finished Eating (R $restaurantId): $customersNumber " +
                    "customers of group $groupId have finished eating at table $tableId."
            )
        }
    }

    /** Waitstaff escorts customers outside the restaurant **/
    fun escorted(waitstaffId: Int, customersNumber: Int, groupId: Int, tableId: Int) {
        log(
            "[IMPORTANT] FOH Escorting (R $restaurantId): Waitstaff $waitstaffId " +
                "escorts $customersNumber customers of group $groupId from table " +
                "$tableId outside."
        )
    }

    /** Number of customers who are eating/finished eating **/
    fun eatingStatus(eatingNumber: Int, finishedEatingNumber: Int) {
        if (logLevel == LogLevel.DEBUG) {
            log(
                "[DEBUG] FOH Eating Status (R $restaurantId): $eatingNumber " +
                    "customers are eating and $finishedEatingNumber customers have finished " +
                    "eating this tick."
            )
        }
    }

    /** How many waitstaff were escorting and customers were escorted **/
    fun escortingStatus(waitstaffNumber: Int, customersNumber: Int) {
        if (logLevel == LogLevel.DEBUG) {
            log(
                "[DEBUG] FOH Escorting Status (R $restaurantId): $waitstaffNumber " +
                    "waitstaff escorted $customersNumber customers this tick."
            )
        }
    }

    /** Waitstaff serves some amount of dishes to a table. **/
    fun waitstaffServes(waitstaffId: Int, dishes: Map<String, Int>, tableId: Int, ticksNumber: Int) {
        log(
            "[IMPORTANT] FOH Serving (R $restaurantId): Waitstaff $waitstaffId " +
                "serves ${keyValueMappingString(dishes)} to table $tableId $ticksNumber ticks " +
                "after ordering."
        )
    }

    /** Waitstaff was not able to serve due to waiting for other meals or reaching the action limit **/
    fun notServing(waitstaffId: Int, mealsNumber: Int, tableId: Int) {
        if (logLevel == LogLevel.DEBUG) {
            log(
                "[DEBUG] FOH No Serving (R $restaurantId): Waitstaff $waitstaffId did " +
                    "not serve $mealsNumber meals to table $tableId."
            )
        }
    }

    // todo: dishName should actually be a Map from dishName: String to amount: Int
    /** Waitstaff serves meals to a driver **/
    fun serveToDriver(waitstaffId: Int, dishes: Map<String, Int>, driverId: Int, orderId: Int) {
        log(
            "[IMPORTANT] FOH Delivery (R $restaurantId): Waitstaff $waitstaffId " +
                "serves ${keyValueMappingString(dishes)} meals to driver $driverId for order " +
                "$orderId."
        )
    }
}
