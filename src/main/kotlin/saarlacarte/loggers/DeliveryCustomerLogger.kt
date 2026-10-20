package saarlacarte.loggers

import saarlacarte.enums.LogLevel

/**
 * LOGGING DETAILS:
 * INFO logs will also be output if a log level of DEBUG is specified,
 * IMPORTANT logs will be output no matter the log level
 */

/**
Delivery customer logger
 **/
class DeliveryCustomerLogger(
    logLevel: LogLevel,
    outPath: String?,
    restaurantId: Int
) : Logger(logLevel, outPath, restaurantId) {
    /** Customers waited too much and gave the delivery up **/
    fun deliveryGivenUp(groupId: Int, orderId: Int) {
        if (logLevel == LogLevel.INFO || logLevel == LogLevel.DEBUG) {
            log(
                "[INFO] Delivery Given Up (R $restaurantId): Group $groupId gave up on " +
                    "waiting for delivery of order $orderId."
            )
        }
    }

    /** Delivery customers finished eating **/
    fun deliveryFinishedEating(groupId: Int) {
        if (logLevel == LogLevel.INFO || logLevel == LogLevel.DEBUG) {
            log(
                "[INFO] Delivery Finished Eating (R $restaurantId): Group $groupId has " +
                    "finished eating."
            )
        }
    }
}
