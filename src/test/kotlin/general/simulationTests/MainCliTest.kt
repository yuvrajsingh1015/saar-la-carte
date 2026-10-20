package general.simulationTests

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import saarlacarte.main
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** F01 CLI: help, validation, and a maxTicks=0 successful parse. */
class MainCliTest {

    @TempDir
    lateinit var tmp: Path

    @Test
    fun emptyArgsAndHelpPrintUsage() {
        assertTrue(captureStdout { main(emptyArray()) }.contains("Usage: saar-la-carte"))
        assertTrue(captureStdout { main(arrayOf("--help")) }.contains("--maxTicks"))
    }

    @Test
    fun invalidLogLevelIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            main(args(logLevel = "TRACE"))
        }
    }

    @Test
    fun maxTicksMustBeInRange() {
        assertThrows(IllegalArgumentException::class.java) {
            main(args(maxTicks = "-1"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            main(args(maxTicks = "1001"))
        }
    }

    @Test
    fun missingFoodArgumentIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            main(
                arrayOf(
                    "--restaurants",
                    "r.json",
                    "--scenario",
                    "s.json",
                    "--maxTicks",
                    "0",
                    "--logLevel",
                    "DEBUG"
                )
            )
        }
    }

    @Test
    fun debugInfoAndImportantLogLevelsAreAccepted() {
        listOf("DEBUG", "info", "Important").forEach { level ->
            val out = emptyLog("out-$level.log")
            main(args(logLevel = level, out = out.pathString))
        }
        assertTrue(tmp.resolve("out-DEBUG.log").exists())
    }

    @Test
    fun validFixtureWithZeroTicksRuns() {
        val out = emptyLog("sim.log")
        main(args(out = out.pathString))
        assertTrue(out.readText().contains("Simulation statistics are calculated."))
    }

    @Test
    fun invalidFoodFileStopsWithoutRunning() {
        val badFood = tmp.resolve("bad-food.json")
        badFood.writeText("{")
        val out = emptyLog("invalid.log")
        main(args(food = badFood.pathString, out = out.pathString))
        assertTrue(out.readText().contains("is invalid."))
    }

    private fun args(
        logLevel: String = "IMPORTANT",
        maxTicks: String = "0",
        food: String = "${liveDir()}/food.json",
        out: String = emptyLog("out.log").pathString
    ): Array<String> = arrayOf(
        "--food",
        food,
        "--restaurants",
        "${liveDir()}/restaurants.json",
        "--scenario",
        "${liveDir()}/scenario.json",
        "--maxTicks",
        maxTicks,
        "--logLevel",
        logLevel,
        "--out",
        out
    )

    private fun liveDir(): String {
        return System.getProperty("user.dir") + "/scenarios/coverage/live"
    }

    private fun emptyLog(name: String): Path {
        val out = tmp.resolve(name)
        out.writeText("")
        return out
    }

    private fun captureStdout(block: () -> Unit): String {
        val buffer = ByteArrayOutputStream()
        val previous = System.out
        System.setOut(PrintStream(buffer))
        try {
            block()
        } finally {
            System.setOut(previous)
        }
        return buffer.toString()
    }
}
