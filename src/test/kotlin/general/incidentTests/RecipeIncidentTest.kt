package general.incidentTests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import saarlacarte.enums.CookType
import saarlacarte.enums.MeasuringUnit
import saarlacarte.food.Ingredient
import saarlacarte.food.Recipe
import saarlacarte.incidents.RecipeIncident
import saarlacarte.simulation.Ticks
import kotlin.test.Test

class RecipeIncidentTest {
    private lateinit var rice: Ingredient
    private lateinit var chicken: Ingredient
    private lateinit var chickenRice: Recipe

    @BeforeEach
    fun setUp() {
        rice = Ingredient("rice", MeasuringUnit.G, 3)
        chicken = Ingredient("chicken", MeasuringUnit.G, 2)
        chickenRice = Recipe(
            1,
            "Chicken Rice",
            15,
            mutableMapOf(rice to 2, chicken to 1),
            listOf(CookType.SAUCE)
        )
        Ticks.init(70)
        Ticks.advanceEvening()
    }

    @Test
    fun testPositiveAdaptation() {
        val incident = RecipeIncident(
            2,
            1,
            listOf(chickenRice),
            "rice",
            100
        )

        assertEquals(2, chickenRice.ingredients[rice])
        assertEquals(1, chickenRice.ingredients[chicken])
        incident.execute()
        assertEquals(4, chickenRice.ingredients[rice])
        assertEquals(1, chickenRice.ingredients[chicken])
    }

    @Test
    fun testNegativeAdaptation() {
        val incident = RecipeIncident(
            2,
            1,
            listOf(chickenRice),
            "rice",
            -50
        )

        assertEquals(2, chickenRice.ingredients[rice])
        assertEquals(1, chickenRice.ingredients[chicken])
        incident.execute()
        assertEquals(1, chickenRice.ingredients[rice])
        assertEquals(1, chickenRice.ingredients[chicken])
    }

    @Test
    fun testMissingIngredient() {
        val incident = RecipeIncident(
            2,
            1,
            listOf(chickenRice),
            "soy sauce",
            30
        )

        assertEquals(2, chickenRice.ingredients[rice])
        assertEquals(1, chickenRice.ingredients[chicken])
        incident.execute()
        assertEquals(2, chickenRice.ingredients[rice])
        assertEquals(1, chickenRice.ingredients[chicken])
    }

    @Test
    fun testRemainPositive() {
        val incident = RecipeIncident(
            2,
            1,
            listOf(chickenRice),
            "rice",
            -100
        )
        assertEquals(2, chickenRice.ingredients[rice])
        assertEquals(1, chickenRice.ingredients[chicken])
        incident.execute()
        assertEquals(1, chickenRice.ingredients[rice])
        assertEquals(1, chickenRice.ingredients[chicken])
    }

    @Test
    fun testMultipleRecipesUpdatedWithRoundingDown() {
        val friedRice = Recipe(
            2,
            "Fried Rice",
            20,
            mutableMapOf(rice to 5),
            listOf(CookType.SOUS)
        )
        val chickenOnly = Recipe(
            3,
            "Roast Chicken",
            25,
            mutableMapOf(chicken to 3),
            listOf(CookType.ROAST)
        )
        val incident = RecipeIncident(
            3,
            1,
            listOf(chickenRice, friedRice, chickenOnly),
            "rice",
            33
        )

        assertEquals("rice", incident.getIngredientName())
        incident.execute()

        assertEquals(2, chickenRice.ingredients[rice])
        assertEquals(1, chickenRice.ingredients[chicken])
        assertEquals(6, friedRice.ingredients[rice])
        assertEquals(3, chickenOnly.ingredients[chicken])
    }
}
