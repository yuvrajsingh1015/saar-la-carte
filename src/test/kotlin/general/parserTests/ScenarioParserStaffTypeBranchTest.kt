package general.parserTests

import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import saarlacarte.enums.CookType
import saarlacarte.enums.MeasuringUnit
import saarlacarte.enums.RestaurantType
import saarlacarte.enums.TableType
import saarlacarte.food.Ingredient
import saarlacarte.food.Recipe
import saarlacarte.parser.ScenarioParser
import saarlacarte.restaurant.FrontOfHouse
import saarlacarte.restaurant.Kitchen
import saarlacarte.restaurant.Menu
import saarlacarte.restaurant.Restaurant
import saarlacarte.restaurant.Staff
import saarlacarte.restaurant.Table
import saarlacarte.simulation.Simulation
import saarlacarte.simulation.Supplier
import java.io.File

/**
 * F06: Parse Incidents.
 *
 * ScenarioParser.parseStaffIncident maps the "staffType" JSON field with
 * `when (staffTypeString) { "COOK" -> ...; "WAITSTAFF" -> ...; "DRIVER" -> ...; else -> return null }`.
 * The existing IncidentParserTest.kt only ever exercises the "COOK" branch (its shared
 * validScenarioJson fixture hard-codes staffType=COOK), so neither the WAITSTAFF/DRIVER
 * success branches nor the `else -> return null` (unknown staffType) failure branch were
 * ever exercised through the parser. This file closes exactly that gap.
 */
class ScenarioParserStaffTypeBranchTest {
    @TempDir
    lateinit var tmp: File

    private val parser = ScenarioParser()

    private lateinit var potato: Ingredient
    private lateinit var supplier: Supplier
    private lateinit var recipe: Recipe
    private lateinit var simulation: Simulation
    private lateinit var restaurant: Restaurant

    @BeforeEach
    fun setUp() {
        potato = Ingredient("Potato", MeasuringUnit.G, 1)
        supplier = Supplier(listOf(potato), mutableMapOf("Potato" to 1000))
        recipe = Recipe(
            1,
            "potato soup",
            10,
            mutableMapOf(potato to 300),
            listOf(CookType.EXEC),
            RestaurantType.EUROPEAN
        )
        val staff = Staff(
            2,
            mapOf(
                CookType.EXEC to 1,
                CookType.SOUS to 0,
                CookType.TOURNANT to 0,
                CookType.SAUCE to 0,
                CookType.FISH to 0,
                CookType.ROAST to 0,
                CookType.VEGETABLE to 0,
                CookType.PASTRY to 0
            ),
            0
        )
        val menu = Menu(supplier, listOf(recipe), RestaurantType.EUROPEAN)
        val table = Table(1, TableType.COMMON, 9)
        val foh = FrontOfHouse(listOf(table), staff.getWaiters(), null, menu)
        val kitchen = Kitchen(staff.getCooks(), menu)
        restaurant = Restaurant(
            1,
            "Fire",
            RestaurantType.EUROPEAN,
            1,
            20,
            false,
            staff,
            2,
            2,
            foh,
            kitchen,
            0
        )

        simulation = Simulation(mock())
        simulation.restaurants = listOf(restaurant)
    }

    private val validScenarioJson = """
    {
      "customerGroups": [],
      "incidents": [
        {
          "id": 1,
          "type": "STAFF",
          "evening": 1,
          "restaurant": 1,
          "number": 1,
          "staffType": "WAITSTAFF"
        }
      ]
    }
    """.trimIndent()

    private fun parseScenario(json: String): Boolean {
        val file = File(tmp, "scenario.json")
        file.writeText(json)
        return parser.parse(file.path, simulation, listOf(recipe))
    }

    private fun validJsonObject(): JSONObject = JSONObject(validScenarioJson)

    private fun getIncident(json: JSONObject): JSONObject =
        json.getJSONArray("incidents").getJSONObject(0)

    @Test
    fun `unknown staffType string is rejected`() {
        val json = validJsonObject()
        getIncident(json).put("staffType", "MANAGER")
        assertFalse(parseScenario(json.toString()))
    }

    @Test
    fun `waitstaff staff incidents are parsed correctly`() {
        assertTrue(parseScenario(validScenarioJson))
        assertEquals(1, simulation.incidents.size)
        val initialWaiters = restaurant.staff.getWaiters().size
        // one waiter hired via the incident, on top of whatever Staff(...) already started with
        simulation.incidents[0].execute()
        assertEquals(initialWaiters + 1, restaurant.staff.getWaiters().size)
    }

    @Test
    fun `driver staff incidents are parsed correctly`() {
        val json = validJsonObject()
        getIncident(json).put("staffType", "DRIVER")
        assertTrue(parseScenario(json.toString()))
        assertEquals(1, simulation.incidents.size)
        val initialDrivers = restaurant.staff.getDrivers().size
        simulation.incidents[0].execute()
        assertEquals(initialDrivers + 1, restaurant.staff.getDrivers().size)
    }
}
