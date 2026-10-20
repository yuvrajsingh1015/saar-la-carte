package saarlacarte.loggers

import saarlacarte.enums.LogLevel
import kotlin.io.path.Path
import kotlin.io.path.appendText

/**
 Base class for all restaurant-scoped loggers
 **/
open class Logger(
    protected val logLevel: LogLevel,
    protected val outPath: String?,

    // !!! Was Int? in the original design, was changed to Int !!!
    protected val restaurantId: Int
) {
    /** Helper function: print message to outPath **/
    fun log(line: String) {
        val path = outPath ?: return
        if (path == "stdout" || path == "/dev/stdout") {
            println(line)
        } else {
            Path(path).appendText("$line\n")
        }
    }

    /** Helper function: build key-value mapping string to print in log messages **/
    fun <T1, T2> keyValueMappingString(map: Map<T1, T2>): String {
        return map.entries
            .sortedBy { it.key.toString() }
            .joinToString(",") { "${it.key}:${it.value}" }
    }
}
