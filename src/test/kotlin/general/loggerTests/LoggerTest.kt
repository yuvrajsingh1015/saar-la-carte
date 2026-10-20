package general.loggerTests

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals

open class LoggerTest {
    var outputPathString: String = ""
    var tempFile: File? = null
    fun readOutput() = tempFile?.readText()?.trim() ?: "Couldn't read output"
    fun expectNoOutput() {
        assertEquals(
            "",
            readOutput()
        )
    }

    @TempDir
    @JvmField
    var tempDir: Path? = null

    @BeforeEach
    fun setup() {
        tempFile = tempDir!!.resolve("test_output.log").toFile()
        tempFile!!.createNewFile()
        outputPathString = tempFile!!.absolutePath
    }
}
