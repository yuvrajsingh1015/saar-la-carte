package saarlacarte.loggers

import saarlacarte.enums.LogLevel
import saarlacarte.food.Ingredient

/**
 * LOGGING DETAILS:
 * INFO logs will also be output if a log level of DEBUG is specified,
 * IMPORTANT logs will be output no matter the log level
 */

/**
Kitchen logger
 **/
class KitchenLogger(
    logLevel: LogLevel,
    outPath: String?,
    restaurantId: Int
) : Logger(logLevel, outPath, restaurantId) {
    // todo: maybe make these two methods work in the same way (they have the same behaviour with different parameters)
    /** Pantry removed some expired ingredient(s) **/
    fun pantryRemovedIngredient(ingredient: Ingredient, amount: Int) {
        if (logLevel == LogLevel.DEBUG) {
            log(
                "[DEBUG] Pantry (R $restaurantId): Removed $amount " +
                    "${ingredient.getUnitString()} of ${ingredient.getName()} from the pantry."
            )
        }
    }

    // todo: maybe make these two methods work in the same way (they have the same behaviour with different parameters)
    /** Pantry procured some ingredient(s) from the supplier **/
    fun pantryProcuresIngredient(obtained: Map<Ingredient, Int>) {
        if (logLevel == LogLevel.DEBUG) {
            obtained.entries.sortedBy { it.key.getName() }.forEach { (ingredient, amount) ->
                log(
                    "[DEBUG] Pantry (R $restaurantId): Procured $amount " +
                        "${ingredient.getUnitString()} of ${ingredient.getName()} from the supplier."
                )
            }
        }
    }

    /** Pantry has been restocked **/
    fun pantryRestocked() {
        if (logLevel == LogLevel.INFO || logLevel == LogLevel.DEBUG) {
            log("[INFO] Pantry (R $restaurantId): Restocked ingredients.")
        }
    }

    /** Activity of all cooks in this tick **/
    fun kitchenStatus(cooksNumber: Int, mealsCooking: Int, mealsFinished: Int, mealsServable: Int) {
        if (logLevel == LogLevel.DEBUG) {
            log(
                "[DEBUG] Kitchen Status (R $restaurantId): $cooksNumber cooks were " +
                    "active cooking $mealsCooking and finishing $mealsFinished " +
                    "meals. $mealsServable meals can be served by the waitstaff."
            )
        }
    }
}
