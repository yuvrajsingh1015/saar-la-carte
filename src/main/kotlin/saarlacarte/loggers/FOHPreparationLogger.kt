package saarlacarte.loggers

import saarlacarte.enums.LogLevel

/**
 * LOGGING DETAILS:
 * INFO logs will also be output if a log level of DEBUG is specified,
 * IMPORTANT logs will be output no matter the log level
 */

/**
Front of house preparation logger
 **/
class FOHPreparationLogger(
    logLevel: LogLevel,
    outPath: String?,
    restaurantId: Int
) : Logger(logLevel, outPath, restaurantId) {
    /** A table could not be reserved for a regular or event customer group **/
    fun tableNotReserved(groupId: Int) {
        log(
            "[IMPORTANT] FOH No Reserving (R $restaurantId): No table could be " +
                "reserved for group $groupId."
        )
    }

    /** Customer group decides on a restaurant **/
    fun restaurantDecided(groupId: Int) {
        if (logLevel == LogLevel.DEBUG) {
            log(
                "[DEBUG] Restaurant Decision: Group $groupId decided on restaurant " +
                    "$restaurantId."
            )
        }
    }
}
