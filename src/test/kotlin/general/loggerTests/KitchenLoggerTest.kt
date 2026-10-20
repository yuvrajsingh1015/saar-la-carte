package general.loggerTests

import org.junit.jupiter.api.BeforeEach
import saarlacarte.enums.LogLevel
import saarlacarte.enums.MeasuringUnit
import saarlacarte.food.Ingredient
import saarlacarte.loggers.KitchenLogger
import kotlin.test.Test
import kotlin.test.assertEquals

class KitchenLoggerTest : LoggerTest() {

    lateinit var debugLogger: KitchenLogger
    lateinit var infoLogger: KitchenLogger
    lateinit var importantLogger: KitchenLogger

    @BeforeEach
    fun initLoggers() {
        debugLogger = KitchenLogger(LogLevel.DEBUG, outputPathString, 1)
        infoLogger = KitchenLogger(LogLevel.INFO, outputPathString, 1)
        importantLogger = KitchenLogger(LogLevel.IMPORTANT, outputPathString, 1)
    }

    @Test
    fun pantryRemovedIngredientDebugTest() {
        debugLogger.pantryRemovedIngredient(Ingredient("carrot", MeasuringUnit.X, 2), 10)
        assertEquals(
            "[DEBUG] Pantry (R 1): Removed 10 X of carrot from the pantry.",
            readOutput()
        )
    }

    @Test
    fun pantryRemovedIngredientInfoTest() {
        infoLogger.pantryRemovedIngredient(Ingredient("carrot", MeasuringUnit.X, 2), 10)
        assertEquals(
            "",
            readOutput()
        )
    }

    @Test
    fun pantryRemovedIngredientImportantTest() {
        importantLogger.pantryRemovedIngredient(Ingredient("carrot", MeasuringUnit.X, 2), 10)
        assertEquals(
            "",
            readOutput()
        )
    }

    @Test
    fun pantryProcuresIngredientDebug() {
        debugLogger.pantryProcuresIngredient(
            mapOf(
                Ingredient("Cherry", MeasuringUnit.X, 2) to 100,
                Ingredient("Apple", MeasuringUnit.X, 3) to 50
            )
        )
        assertEquals(
            "[DEBUG] Pantry (R 1): Procured 50 X of Apple from the supplier.\n" +
                "[DEBUG] Pantry (R 1): Procured 100 X of Cherry from the supplier.",
            readOutput()
        )
    }

    @Test
    fun pantryProcuresIngredientInfo() {
        infoLogger.pantryProcuresIngredient(
            mapOf(
                Ingredient("Cherry", MeasuringUnit.X, 2) to 100,
                Ingredient("Apple", MeasuringUnit.X, 3) to 50
            )
        )
        assertEquals(
            "",
            readOutput()
        )
    }

    @Test
    fun pantryProcuresIngredientImportant() {
        importantLogger.pantryProcuresIngredient(
            mapOf(
                Ingredient("Cherry", MeasuringUnit.X, 2) to 100,
                Ingredient("Apple", MeasuringUnit.X, 3) to 50
            )
        )
        assertEquals(
            "",
            readOutput()
        )
    }

    @Test
    fun pantryRestockedDebug() {
        debugLogger.pantryRestocked()
        assertEquals(
            "[INFO] Pantry (R 1): Restocked ingredients.",
            readOutput()
        )
    }

    @Test
    fun pantryRestockedInfo() {
        infoLogger.pantryRestocked()
        assertEquals(
            "[INFO] Pantry (R 1): Restocked ingredients.",
            readOutput()
        )
    }

    @Test
    fun pantryRestockedImportant() {
        importantLogger.pantryRestocked()
        assertEquals(
            "",
            readOutput()
        )
    }

    @Test
    fun kitchenStatusDebug() {
        debugLogger.kitchenStatus(3, 4, 5, 6)
        assertEquals(
            "[DEBUG] Kitchen Status (R 1): 3 cooks were active cooking 4 " +
                "and finishing 5 meals. 6 meals can be served by the waitstaff.",
            readOutput()
        )
    }

    @Test
    fun kitchenStatusInfo() {
        infoLogger.kitchenStatus(3, 4, 5, 6)
        assertEquals(
            "",
            readOutput()
        )
    }

    @Test
    fun kitchenStatusImportant() {
        importantLogger.kitchenStatus(3, 4, 5, 6)
        assertEquals(
            "",
            readOutput()
        )
    }
}
