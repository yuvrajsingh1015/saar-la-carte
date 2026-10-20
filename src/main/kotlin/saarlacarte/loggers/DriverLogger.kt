package saarlacarte.loggers

import saarlacarte.enums.LogLevel

/**
 * LOGGING DETAILS:
 * INFO logs will also be output if a log level of DEBUG is specified,
 * IMPORTANT logs will be output no matter the log level
 */

/**
Delivery driver logger
 **/
class DriverLogger(
    logLevel: LogLevel,
    outPath: String?,
    restaurantId: Int
) : Logger(logLevel, outPath, restaurantId) {
    // !!! ABSENT parameters from the class diagram: orderId, groupId !!!
    /** Drivers prepare driving **/
    fun deliveryPreparation(driverId: Int, orderId: Int, groupId: Int, deliveryTicks: Int) {
        if (logLevel == LogLevel.INFO || logLevel == LogLevel.DEBUG) {
            log(
                "[INFO] Delivery Preparation (R $restaurantId): Driver $driverId " +
                    "prepares driving order $orderId to group $groupId, which will take " +
                    "$deliveryTicks ticks."
            )
        }
    }

    /* !!! More parameters are passed wrt the original design !!!
    *  added: drivenDistance, ticksRemaining
    *  removed: order
    */
    /** Driver is on their way **/
    fun deliveryDriving(driverId: Int, drivenDistance: Int, ticksRemaining: Int) {
        if (logLevel == LogLevel.DEBUG) {
            log(
                "[DEBUG] Delivery Driving (R $restaurantId): Driver $driverId drove " +
                    "$drivenDistance km and needs $ticksRemaining more ticks."
            )
        }
    }

    /** Driver arrived at the customer group **/
    fun deliveryArrival(driverId: Int, groupId: Int, orderId: Int) {
        if (logLevel == LogLevel.INFO || logLevel == LogLevel.DEBUG) {
            log(
                "[INFO] Delivery Arrival (R $restaurantId): Driver $driverId arrived " +
                    "at group $groupId with order $orderId."
            )
        }
    }

    /** Delivery was successful **/
    fun deliveryFinished(driverId: Int, groupId: Int, orderId: Int) {
        log(
            "[IMPORTANT] Delivery Finished (R $restaurantId): Driver $driverId " +
                "gave delivery of order $orderId to group $groupId."
        )
    }

    /** Delivery failed **/
    fun deliveryFailed(driverId: Int, groupId: Int, orderId: Int) {
        log(
            "[IMPORTANT] Delivery Failed (R $restaurantId): Driver $driverId " +
                "failed to deliver order $orderId to group $groupId."
        )
    }

    /** Driver returned to the restaurant **/
    fun driverReturned(driverId: Int) {
        if (logLevel == LogLevel.INFO || logLevel == LogLevel.DEBUG) {
            log(
                "[INFO] Delivery Returned (R $restaurantId): Driver $driverId has " +
                    "returned."
            )
        }
    }
}
