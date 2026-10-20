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
import saarlacarte.incidents.PackagingIncident
import saarlacarte.incidents.RecipeIncident
import saarlacarte.incidents.StaffIncident
import saarlacarte.incidents.UnavailableIncident
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

class IncidentParserTest {
    @TempDir
    lateinit var tmp: File

    private val parser = ScenarioParser()

    // necessary stuffs, will be initialized in the setup
    private lateinit var potato: Ingredient
    private lateinit var onion: Ingredient
    private lateinit var supplier: Supplier
    private lateinit var recipe: Recipe
    private lateinit var simulation: Simulation
    private lateinit var restaurant: Restaurant

    @BeforeEach
    fun setUp() {
        potato = Ingredient("Potato", MeasuringUnit.G, 1)
        onion = Ingredient("Onion", MeasuringUnit.X, 1)

        supplier = Supplier(listOf(potato, onion), mutableMapOf("Potato" to 1000, "Onion" to 10))
        recipe = Recipe(
            1, "potato soup", 10, mutableMapOf(potato to 300, onion to 1),
            listOf(CookType.EXEC, CookType.SOUS),
            RestaurantType.EUROPEAN
        )
        val staff = Staff(
            2,
            mapOf(
                CookType.EXEC to 1,
                CookType.SOUS to 1,
                CookType.TOURNANT to 0,
                CookType.SAUCE to 1,
                CookType.FISH to 0,
                CookType.ROAST to 1,
                CookType.VEGETABLE to 1,
                CookType.PASTRY to 0
            ),
            0
        )
        val menu = Menu(supplier, listOf(recipe), RestaurantType.EUROPEAN)
        val table = Table(1, TableType.COMMON, 9)
        val foh = FrontOfHouse(listOf(table), staff.getWaiters(), null, menu)
        val kitchen = Kitchen(staff.getCooks(), menu)
        restaurant = Restaurant(
            1, "Fire", RestaurantType.EUROPEAN, 1, 20,
            false, staff, 2, 2, foh, kitchen, 0
        )

        // instantiate the real simulation with mocked loggers
        simulation = Simulation(mock())
        simulation.restaurants = listOf(restaurant)
    }

    private val validScenarioJson = """
    {
      "customerGroups": [],
       "incidents":[
         {
           "id": 1,
           "type": "STAFF",
           "evening": 1,
           "restaurant": 1,
           "number": 1, 
           "staffType": "COOK",
           "cookType": "SOUS"
         },
         {
           "id": 2,
           "type": "RECIPE",
           "evening": 1,
           "ingredient": "Onion",
           "adaptation": 3
         },
         {
           "id": 3,
           "type": "PACKAGING",
           "evening": 1,
           "ingredient": "Potato",
           "packagingVolume": 500
         },
         {
           "id": 4,
           "type": "UNAVAILABLE",
           "evening": 1,
           "ingredient": "Onion",
           "duration": 2
         }
       ]
    }
    """.trimIndent()

    // write the above JSON scenario into a temp file named scenario.json and parse it
    // returns true if the scenario was parsed and validated successfully
    private fun parseScenario(json: String): Boolean {
        val file = File(tmp, "scenario.json")
        file.writeText(json)
        return parser.parse(file.path, simulation, listOf(recipe))
    }

    // creates and returns a fresh object of scenario above
    private fun validJsonObject(): JSONObject {
        return JSONObject(validScenarioJson)
    }

    // get the incident at the requested index from the scenarios incidents
    private fun getIncident(json: JSONObject, index: Int): JSONObject {
        return json.getJSONArray("incidents").getJSONObject(index)
    }

    @Test
    fun getIncidentsCorrectly() {
        assertTrue(parseScenario(validScenarioJson))
        assertEquals(4, simulation.incidents.size)

        // as is to make sure that we read it as a staff incident
        val staffIncident = simulation.incidents[0]
        assertTrue(staffIncident is StaffIncident)

        val recipeIncident = simulation.incidents[1]
        assertTrue(recipeIncident is RecipeIncident)

        val packagingIncident = simulation.incidents[2]
        assertTrue(packagingIncident is PackagingIncident)

        val unavailableIncident = simulation.incidents[3]
        assertTrue(unavailableIncident is UnavailableIncident)

        assertEquals(1, staffIncident.id)
        assertEquals(2, recipeIncident.id)
        assertEquals(3, packagingIncident.id)
        assertEquals(4, unavailableIncident.id)

        assertEquals(1, staffIncident.evening)
        assertEquals(1, recipeIncident.evening)
        assertEquals(1, packagingIncident.evening)
        assertEquals(1, unavailableIncident.evening)
    }

    // staff incidents as SI
    @Test
    fun avoidCookTypeExecSI() {
        val json = validJsonObject()
        getIncident(json, 0).put("cookType", "EXEC")
        assertFalse(parseScenario(json.toString()))
    }

    @Test
    fun zeroStaffAffectedSI() {
        val json = validJsonObject()
        getIncident(json, 0).put("number", 0)
        assertFalse(parseScenario(json.toString()))
    }

    @Test
    fun missingCookTypeSI() {
        val json = validJsonObject()
        getIncident(json, 0).remove("cookType")
        assertFalse(parseScenario(json.toString()))
    }

    @Test
    fun missingRestaurantSI() {
        val json = validJsonObject()
        getIncident(json, 0).put("restaurant", 5)
        assertFalse(parseScenario(json.toString()))
    }

    // recipe incidents as RI
    @Test
    fun unknownIngredientRI() {
        val json = validJsonObject()
        getIncident(json, 1).put("ingredient", "randomNameIDK")
        assertFalse(parseScenario(json.toString()))
    }

    @Test
    fun adaptationZeroRI() {
        val json = validJsonObject()
        getIncident(json, 1).put("adaptation", 0)
        assertFalse(parseScenario(json.toString()))
    }

    // packaging Incident as PI
    @Test
    fun zeroPackagingVolumePI() {
        val json = validJsonObject()
        getIncident(json, 2).put("packagingVolume", 0)
        assertFalse(parseScenario(json.toString()))
    }

    // unavailable Incident as UI
    @Test
    fun zeroDurationUI() {
        val json = validJsonObject()
        getIncident(json, 3).put("duration", 0)
        assertFalse(parseScenario(json.toString()))
    }

    @Test
    fun sameIngredientOverlapUI() {
        val json = validJsonObject()
        val incidents = json.getJSONArray("incidents")

        /**
         *        "id": 4,
         *        "type": "UNAVAILABLE",
         *        "evening": 1,
         *        "ingredient": "Onion",
         *        "duration": 2  -> 3
         */
        // from the valid json scenario get the ui
        getIncident(json, 3).put("duration", 3)
        // with duration 3, the ingredient is not available in evening 1, 2, 3

        // add a new ui incident
        val addedUI = JSONObject().apply {
            put("id", 5)
            put("type", "UNAVAILABLE")
            put("evening", 2)
            put("ingredient", "Onion")
            put("duration", 3)
        }
        incidents.put(addedUI)
        assertFalse(parseScenario(json.toString()))
    }

    @Test
    fun sameIngredientNOOverlapUI() {
        val json = validJsonObject()
        val incidents = json.getJSONArray("incidents")

        /**
         *        "id": 4,
         *        "type": "UNAVAILABLE",
         *        "evening": 1,
         *        "ingredient": "Onion",
         *        "duration": 2  -> 3
         */
        // from the valid json scenario get the ui
        getIncident(json, 3).put("duration", 3)
        // with duration 3, the ingredient is not available in evening 1, 2, 3

        // add a new ui incident
        val addedUI = JSONObject().apply {
            put("id", 5)
            put("type", "UNAVAILABLE")
            put("evening", 10)
            put("ingredient", "Onion")
            put("duration", 3)
        }
        incidents.put(addedUI)
        assertTrue(parseScenario(json.toString()))
    }

    @Test
    fun diffIngredientOverlapUI() {
        val json = validJsonObject()
        val incidents = json.getJSONArray("incidents")

        /**
         *        "id": 4,
         *        "type": "UNAVAILABLE",
         *        "evening": 1,
         *        "ingredient": "Onion",
         *        "duration": 2  -> 3
         */
        // from the valid json scenario get the ui
        getIncident(json, 3).put("duration", 3)
        // with duration 3, the ingredient is not available in evening 1, 2, 3

        // add a new ui incident
        val addedUI = JSONObject().apply {
            put("id", 5)
            put("type", "UNAVAILABLE")
            put("evening", 2)
            put("ingredient", "Potato")
            put("duration", 3)
        }
        incidents.put(addedUI)
        assertTrue(parseScenario(json.toString()))
    }

    @Test
    fun diffIngredientNOOverlapUI() {
        val json = validJsonObject()
        val incidents = json.getJSONArray("incidents")

        /**
         *        "id": 4,
         *        "type": "UNAVAILABLE",
         *        "evening": 1,
         *        "ingredient": "Onion",
         *        "duration": 2  -> 3
         */
        // from the valid json scenario get the ui
        getIncident(json, 3).put("duration", 3)
        // with duration 3, the ingredient is not available in evening 1, 2, 3

        // add a new ui incident
        val addedUI = JSONObject().apply {
            put("id", 5)
            put("type", "UNAVAILABLE")
            put("evening", 5)
            put("ingredient", "Potato")
            put("duration", 3)
        }
        incidents.put(addedUI)
        assertTrue(parseScenario(json.toString()))
    }

    // incidents should have unique ids
    @Test
    fun uniqueIdsIncidents() {
        val json = validJsonObject()
        getIncident(json, 1).put("id", 3)
        assertFalse(parseScenario(json.toString()))
    }
}
