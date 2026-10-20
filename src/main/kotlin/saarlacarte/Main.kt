package saarlacarte

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import saarlacarte.enums.LogLevel
import saarlacarte.parser.Parser
import saarlacarte.simulation.Simulation

private const val MIN_MAX_TICKS = 0
private const val MAX_MAX_TICKS = 1000

/**
 * Entry point for the Saar la Carte simulation.
 */
fun main(args: Array<String>) {
    if (args.isEmpty() || args.contains("--help")) {
        printHelp()
        return
    }

    val sim = parse(args) ?: return
    runSimulation(sim)
}

/**
 * Parses CLI arguments and builds a fully initialized [Simulation], or `null` if invalid.
 */
private fun parse(args: Array<String>): Simulation? {
    val config = parseCommandLine(args)
    return parseSimulation(config)
}

/**
 * Runs the simulation after parsing and logger assignment.
 */
private fun runSimulation(sim: Simulation) {
    sim.assignLoggers()
    sim.run()
}

/** Delegates configuration parsing and validation to [Parser]. */
private fun parseSimulation(config: CliConfig): Simulation? {
    return Parser().parse(
        config.food,
        config.restaurants,
        config.scenario,
        config.maxTicks,
        config.logLevel,
        config.out
    )
}

private fun parseCommandLine(args: Array<String>): CliConfig {
    val parser = ArgParser("saar-la-carte")

    val food by parser.option(ArgType.String, fullName = "food")
    val restaurants by parser.option(ArgType.String, fullName = "restaurants")
    val scenario by parser.option(ArgType.String, fullName = "scenario")
    val maxTicks by parser.option(ArgType.Int, fullName = "maxTicks")
    val logLevel by parser.option(ArgType.String, fullName = "logLevel")
    val out by parser.option(ArgType.String, fullName = "out")

    parser.parse(args)

    return CliConfig(
        food = requireNotNull(food) { "Missing required argument: --food" },
        restaurants = requireNotNull(restaurants) { "Missing required argument: --restaurants" },
        scenario = requireNotNull(scenario) { "Missing required argument: --scenario" },
        maxTicks = requireNotNull(maxTicks) { "Missing required argument: --maxTicks" }
            .also {
                require(it in MIN_MAX_TICKS..MAX_MAX_TICKS) {
                    "--maxTicks must be between $MIN_MAX_TICKS and $MAX_MAX_TICKS"
                }
            },
        logLevel = parseLogLevel(requireNotNull(logLevel) { "Missing required argument: --logLevel" }),
        out = out ?: "stdout"
    )
}

private fun parseLogLevel(value: String): LogLevel =
    when (value.uppercase()) {
        "DEBUG" -> LogLevel.DEBUG
        "INFO" -> LogLevel.INFO
        "IMPORTANT" -> LogLevel.IMPORTANT
        else -> throw IllegalArgumentException("--logLevel must be DEBUG, INFO, or IMPORTANT")
    }

private fun printHelp() {
    println(
        """
        Usage: saar-la-carte --food <path> --restaurants <path> --scenario <path> \
            --maxTicks <int> --logLevel <level> [--out <path>]

        Required:
          --food         Path to the food configuration (ingredients and recipes)
          --restaurants  Path to the restaurants configuration
          --scenario     Path to the scenario configuration (customer groups and incidents)
          --maxTicks     Maximum simulation ticks (0–1000)
          --logLevel     DEBUG | INFO | IMPORTANT

        Optional:
          --out          Output destination (default: stdout)
          --help         Show this message
        """.trimIndent()
    )
}

private data class CliConfig(
    val food: String,
    val restaurants: String,
    val scenario: String,
    val maxTicks: Int,
    val logLevel: LogLevel,
    val out: String
)
