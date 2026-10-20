package saarlacarte.loggers

import saarlacarte.enums.LogLevel
import saarlacarte.incidents.Incident
import saarlacarte.simulation.Ticks
import kotlin.io.path.Path
import kotlin.io.path.appendText

/**
 * LOGGING DETAILS:
 * INFO logs will also be output if a log level of DEBUG is specified,
 * IMPORTANT logs will be output no matter the log level
 */

/**
Global simulation logger
 **/
data class SimulationLogger(val logLevel: LogLevel, val outPath: String?) {
    /** Validation and parsing successful **/
    fun initializationDone(filename: String) {
        if (logLevel == LogLevel.INFO || logLevel == LogLevel.DEBUG) {
            log("[INFO] Initialization Info: $filename successfully parsed and validated.")
        }
    }

    /** Invalid input file **/
    fun initializationInvalid(filename: String) {
        log("[IMPORTANT] Initialization Info: $filename is invalid.")
    }

    /** Simulation started **/
    fun simulationStart() {
        if (logLevel == LogLevel.INFO || logLevel == LogLevel.DEBUG) {
            log("[INFO] Simulation Info: Simulation started.")
        }
    }

    // !!! Different from design: also incidentType is passed !!!
    /** Incident occurred before the upcoming evening **/
    fun incidentOccurred(incident: Incident, incidentType: String) {
        log(
            "[IMPORTANT] Incident: Incident ${incident.id} of type " +
                "$incidentType occurred before evening ${Ticks.getEvening()}."
        )
    }

    /** Preparation phase starts for all restaurants **/
    fun preparationStarts() {
        log("[IMPORTANT] Preparation: Preparation for evening ${Ticks.getEvening()} starts.")
    }

    /** Serving phase starts for all restaurants **/
    fun servingStart() {
        log("[IMPORTANT] Serving: Serving of evening ${Ticks.getEvening()} starts.")
    }

    /** Tick starts **/
    fun tickStart() {
        log("[IMPORTANT] Simulation: Tick ${Ticks.getTick()} (${Ticks.getEvening()}) started.")
    }

    /** Customer group could not decide a suitable restaurant **/
    fun restaurantNotDecided(groupId: Int) {
        if (logLevel == LogLevel.DEBUG) {
            log(
                "[DEBUG] Restaurant No Decision: Group $groupId could not " +
                    "decide for a restaurant."
            )
        }
    }

    /** Serving phase ends for all restaurants **/
    fun servingEnds() {
        log("[IMPORTANT] Serving: Serving of evening ${Ticks.getEvening()} ends.")
    }

    /** Simulation statistics are calculated **/
    fun statisticsCalculated() {
        log("[IMPORTANT] Simulation Info: Simulation statistics are calculated.")
    }

    private fun log(line: String) {
        val path = outPath ?: return
        if (path == "stdout" || path == "/dev/stdout") {
            println(line)
        } else {
            Path(path).appendText("$line\n")
        }
    }
}
