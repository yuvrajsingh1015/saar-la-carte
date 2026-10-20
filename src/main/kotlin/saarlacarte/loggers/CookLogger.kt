package saarlacarte.loggers

import saarlacarte.enums.LogLevel

/**
 * LOGGING DETAILS:
 * INFO logs will also be output if a log level of DEBUG is specified,
 * IMPORTANT logs will be output no matter the log level
 */

/**
Cook logger
 **/
class CookLogger(
    logLevel: LogLevel,
    outPath: String?,
    restaurantId: Int
) : Logger(logLevel, outPath, restaurantId) {
    // todo: also pass the cookType as well (to be defined in the class)
    // !!!
    //  changed parameters wrt design:
    //      - added cookType: String
    //      - order: Order -> orderName: String
    //      - added orderId: Int
    //      - orders: List<Order> -> orders: String
    // !!!
    /** Cook starts cooking some dishes **/
    fun cookStartCooking(
        cookId: Int,
        cookType: String,
        mealsNumber: Int,
        dishName: String,
        orderId: Int,
        orders: String
    ) {
        log(
            "[IMPORTANT] Kitchen Dish Assignment (R $restaurantId): Cook $cookId " +
                "of type $cookType starts cooking $mealsNumber meals of dish $dishName " +
                "based on order $orderId for orders $orders."
        )
    }

    /** Cook finished cooking a dish **/
    fun mealCooked(cookId: Int, mealsNumber: Int, dishName: String, ticksNumber: Int) {
        log(
            "[IMPORTANT] Kitchen Meal Cooked (R $restaurantId): Cook $cookId " +
                "finished cooking $mealsNumber meals of dish $dishName $ticksNumber " +
                "ticks after ordering."
        )
    }
}
