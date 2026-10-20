package general.loggerTests

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import saarlacarte.enums.CookType
import saarlacarte.enums.DishStatus
import saarlacarte.enums.LogLevel
import saarlacarte.enums.MeasuringUnit
import saarlacarte.enums.RestaurantType
import saarlacarte.food.Ingredient
import saarlacarte.food.Recipe
import saarlacarte.loggers.CookLogger
import saarlacarte.orders.Dish
import saarlacarte.orders.Order
import kotlin.test.assertEquals

class CookLoggerTest : LoggerTest() {
    lateinit var debugLogger: CookLogger
    lateinit var infoLogger: CookLogger
    lateinit var importantLogger: CookLogger

    private val rice = Ingredient("rice", MeasuringUnit.G, 5)
    private val chicken = Ingredient("chicken", MeasuringUnit.G, 2)
    private val potato = Ingredient("Potato", MeasuringUnit.G, 5)
    private val tomato = Ingredient("Tomato", MeasuringUnit.X, 4)
    private val chickenRiceRecipe = Recipe(
        1,
        "Chicken Rice",
        15,
        mutableMapOf(rice to 1, chicken to 1),
        listOf(CookType.TOURNANT, CookType.SOUS, CookType.EXEC),
        RestaurantType.EUROPEAN
    )
    val pizzaRecipe = Recipe(
        3,
        "Pizza",
        15,
        mutableMapOf(tomato to 2, potato to 100),
        listOf(CookType.EXEC, CookType.ROAST),
        RestaurantType.EUROPEAN
    )
    val cookingDish = Dish(pizzaRecipe, DishStatus.INPROGRESS)
    val anotherCookingDish = Dish(pizzaRecipe, DishStatus.INPROGRESS)
    val notStartedDish = Dish(chickenRiceRecipe, DishStatus.NOTSTARTED)
    val order3 = Order(3, mutableListOf(cookingDish, notStartedDish), false, null, 1)
    val order4 = Order(4, mutableListOf(anotherCookingDish), false, null, 2)

    @BeforeEach
    fun initLoggers() {
        debugLogger = CookLogger(LogLevel.DEBUG, outputPathString, 1)
        infoLogger = CookLogger(LogLevel.INFO, outputPathString, 1)
        importantLogger = CookLogger(LogLevel.IMPORTANT, outputPathString, 1)
    }

    @Test
    fun cookStartCookingDebug() {
        debugLogger.cookStartCooking(
            2,
            CookType.EXEC.toString(),
            2,
            "Pizza",
            3,
            listOf(order3, order4).map { it.getId() }.joinToString(separator = ",")
        )
        assertEquals(
            "[IMPORTANT] Kitchen Dish Assignment (R 1): Cook 2 of type EXEC " +
                "starts cooking 2 meals of dish Pizza based on order 3 for orders 3,4.",
            readOutput()
        )
    }

    @Test
    fun cookStartCookingInfo() {
        infoLogger.cookStartCooking(
            2,
            CookType.EXEC.toString(),
            2,
            "Pizza",
            3,
            listOf(order3, order4).map { it.getId() }.joinToString(separator = ",")
        )
        assertEquals(
            "[IMPORTANT] Kitchen Dish Assignment (R 1): Cook 2 of type EXEC " +
                "starts cooking 2 meals of dish Pizza based on order 3 for orders 3,4.",
            readOutput()
        )
    }

    @Test
    fun cookStartCookingImportant() {
        importantLogger.cookStartCooking(
            2,
            CookType.EXEC.toString(),
            2,
            "Pizza",
            3,
            listOf(order3, order4).map { it.getId() }.joinToString(separator = ",")
        )
        assertEquals(
            "[IMPORTANT] Kitchen Dish Assignment (R 1): Cook 2 of type EXEC " +
                "starts cooking 2 meals of dish Pizza based on order 3 for orders 3,4.",
            readOutput()
        )
    }

    @Test
    fun mealCookedDebug() {
        debugLogger.mealCooked(2, 4, "chicken rice", 5)
        assertEquals(
            "[IMPORTANT] Kitchen Meal Cooked (R 1): " +
                "Cook 2 finished cooking 4 meals of dish chicken rice 5 ticks after ordering.",
            readOutput()
        )
    }

    @Test
    fun mealCookedInfo() {
        infoLogger.mealCooked(2, 4, "chicken rice", 5)
        assertEquals(
            "[IMPORTANT] Kitchen Meal Cooked (R 1): " +
                "Cook 2 finished cooking 4 meals of dish chicken rice 5 ticks after ordering.",
            readOutput()
        )
    }

    @Test
    fun mealCookedImportant() {
        importantLogger.mealCooked(2, 4, "chicken rice", 5)
        assertEquals(
            "[IMPORTANT] Kitchen Meal Cooked (R 1): " +
                "Cook 2 finished cooking 4 meals of dish chicken rice 5 ticks after ordering.",
            readOutput()
        )
    }
}
