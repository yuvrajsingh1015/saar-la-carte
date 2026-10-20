package saarlacarte.loggers

import saarlacarte.enums.LogLevel

/**
 * LOGGING DETAILS:
 * INFO logs will also be output if a log level of DEBUG is specified,
 * IMPORTANT logs will be output no matter the log level
 */

/**
Per-restaurant logger
 **/
class RestaurantLogger(
    logLevel: LogLevel,
    outPath: String?,
    restaurantId: Int
) : Logger(logLevel, outPath, restaurantId) {
    /** Customer group decided a restaurant **/
    fun restaurantDecided(groupId: Int) {
        if (logLevel == LogLevel.DEBUG) {
            log(
                "[DEBUG] Restaurant Decision: Group $groupId decided on restaurant " +
                    "$restaurantId."
            )
        }
    }

    /** Restaurant simulates a tick **/
    fun restaurantStartsTick(restaurantId: Int) {
        if (logLevel == LogLevel.DEBUG) {
            log(
                "[DEBUG] Restaurant Start (R $restaurantId): Restaurant $restaurantId " +
                    "simulates a tick."
            )
        }
    }

    /** How many customer groups rated in this tick **/
    fun ratingStatus(customersNumber: Int) {
        if (logLevel == LogLevel.DEBUG) {
            log(
                "[DEBUG] Rating Status (R $restaurantId): $customersNumber groups " +
                    "performed ratings this tick."
            )
        }
    }

    /** A customer group leaves a rating on a restaurant **/
    fun receivedRating(groupId: Int, groupRating: String, positiveRatings: Int, negativeRatings: Int) {
        if (logLevel == LogLevel.DEBUG || logLevel == LogLevel.INFO) {
            log(
                "[INFO] Rating (R $restaurantId): Group $groupId rates " +
                    "the restaurant $restaurantId with $groupRating rating, " +
                    "leading to $positiveRatings positive ratings and " +
                    "$negativeRatings negative ratings."
            )
        }
    }

    /** Restaurant finished simulating the current tick **/
    fun restaurantEndTick() {
        if (logLevel == LogLevel.DEBUG) {
            log(
                "[DEBUG] Restaurant End (R $restaurantId): Restaurant $restaurantId " +
                    "finished simulating the tick."
            )
        }
    }

    /** Total number of cooked meals **/
    fun cookedTotal(mealsNumber: Int) {
        log(
            "[IMPORTANT] Simulation Statistics: Restaurant $restaurantId cooked " +
                "$mealsNumber meals."
        )
    }

    /** Total number of served customers **/
    fun servedTotal(customersNumber: Int) {
        log(
            "[IMPORTANT] Simulation Statistics: Restaurant $restaurantId served " +
                "$customersNumber customers."
        )
    }

    /** Total number of delivered meals and delivery customers **/
    fun deliveredTotal(customersNumber: Int) {
        log(
            "[IMPORTANT] Simulation Statistics: Restaurant $restaurantId delivered " +
                "meals to $customersNumber customers."
        )
    }

    /** Total number of received ratings **/
    fun ratingsTotal(ratingsNumber: Int) {
        log(
            "[IMPORTANT] Simulation Statistics: Restaurant $restaurantId received " +
                "$ratingsNumber ratings."
        )
    }
}
