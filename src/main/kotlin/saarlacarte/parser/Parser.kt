package saarlacarte.parser

import saarlacarte.enums.LogLevel
import saarlacarte.loggers.SimulationLogger
import saarlacarte.simulation.Simulation
import saarlacarte.simulation.Ticks
import java.io.File

/**
Drives initialization: parses and validates the food, restaurant and scenario
files in that order, logs each outcome, and stops at the first invalid file.
 */
class Parser(
    private val foodParser: FoodParser = FoodParser(),
    private val restaurantParser: RestaurantParser = RestaurantParser(),
    private val scenarioParser: ScenarioParser = ScenarioParser()
) {

    /** Returns the initialized [Simulation], or `null` if any config file is invalid. */
    fun parse(
        foodPath: String,
        restaurantPath: String,
        scenarioPath: String,
        maxTicks: Int,
        logLevel: LogLevel,
        outPath: String?
    ): Simulation? {
        Ticks.init(maxTicks)
        val logger = SimulationLogger(logLevel, outPath)
        val simulation = Simulation(logger)

        val food = logged(logger, foodPath, foodParser.parse(foodPath)) ?: return null
        val (supplier, recipes) = food

        val restaurants = logged(logger, restaurantPath, restaurantParser.parse(restaurantPath, supplier, recipes))
            ?: return null
        simulation.restaurants = restaurants

        if (!scenarioParser.parse(scenarioPath, simulation, recipes)) {
            logger.initializationInvalid(File(scenarioPath).name)
            return null
        }
        logger.initializationDone(File(scenarioPath).name)

        return simulation
    }

    private fun <T : Any> logged(logger: SimulationLogger, path: String, result: T?): T? {
        val name = File(path).name
        if (result == null) {
            logger.initializationInvalid(name)
        } else {
            logger.initializationDone(name)
        }
        return result
    }
}
