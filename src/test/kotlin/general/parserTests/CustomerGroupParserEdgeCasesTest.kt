package general.parserTests

import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import saarlacarte.enums.CookType
import saarlacarte.enums.LogLevel
import saarlacarte.enums.MeasuringUnit
import saarlacarte.enums.RestaurantType
import saarlacarte.enums.TableType
import saarlacarte.food.Ingredient
import saarlacarte.food.Recipe
import saarlacarte.loggers.SimulationLogger
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

// small extra cases split out of CustomerGroupParserTest to keep that class under
// the detekt size limit
class CustomerGroupParserEdgeCasesTest {

    @TempDir
    lateinit var tmp: File

    private val parser = ScenarioParser()

    private lateinit var supplier: Supplier
    private lateinit var recipes: List<Recipe>
    private lateinit var simulation: Simulation

    @BeforeEach
    fun setUp() {
        val potato = Ingredient("Potato", MeasuringUnit.G, 5)
        val tomato = Ingredient("Tomato", MeasuringUnit.X, 4)
        val rice = Ingredient("Rice", MeasuringUnit.G, 10)
        supplier = Supplier(
            listOf(potato, tomato, rice),
            mutableMapOf("Potato" to 1000, "Tomato" to 10, "Rice" to 500)
        )
        val pizzaRecipe = Recipe(
            1,
            "Pizza",
            15,
            mutableMapOf(tomato to 2, potato to 100),
            listOf(CookType.EXEC, CookType.ROAST),
            RestaurantType.EUROPEAN
        )
        val sushiRecipe = Recipe(
            2,
            "Sushi",
            10,
            mutableMapOf(rice to 150),
            listOf(CookType.EXEC, CookType.FISH),
            RestaurantType.ASIAN
        )
        recipes = listOf(pizzaRecipe, sushiRecipe)
        val restaurant = createRestaurant(1, RestaurantType.ASIAN)

        simulation = Simulation(SimulationLogger(LogLevel.INFO, null))
        simulation.restaurants = listOf(restaurant)
    }

    private fun createRestaurant(id: Int, type: RestaurantType): Restaurant {
        val staff = Staff(
            2,
            mapOf(
                CookType.EXEC to 1,
                CookType.SOUS to 1,
                CookType.TOURNANT to 0,
                CookType.SAUCE to 0,
                CookType.FISH to 0,
                CookType.ROAST to 1,
                CookType.VEGETABLE to 0,
                CookType.PASTRY to 0
            ),
            1
        )
        val menu = Menu(supplier, recipes, type)
        val table = Table(1, TableType.COMMON, 10)
        val foh = FrontOfHouse(listOf(table), staff.getWaiters(), null, menu)
        val kitchen = Kitchen(staff.getCooks(), menu)
        return Restaurant(
            id = id,
            name = "Restaurant $id",
            type = type,
            openingTickStart = 1,
            openingTickEnd = 24,
            canHostEvent = true,
            staff = staff,
            positiveRatings = 0,
            negativeRatings = 0,
            frontOfHouse = foh,
            kitchen = kitchen,
            deliveryDriverCount = 1
        )
    }

    private val validScenarioJson = """
    {
      "customerGroups": [
        {
          "id": 2,
          "type": "CASUAL",
          "size": 3,
          "visitingTick": 12,
          "tableType": "BAR",
          "foodPreferences": [
            {
              "size": 2,
              "preferredIngredients": ["Rice"]
            }
          ],
          "restaurantTypes": ["ASIAN"],
          "visitingEvenings": [1, 2],
          "ratingLikelihood": "ALWAYS"
        },
        {
          "id": 3,
          "type": "CASUAL",
          "size": 2,
          "visitingTick": 15,
          "foodPreferences": [
            {
              "size": 2,
              "favoriteDishes": ["Pizza"]
            }
          ],
          "restaurantTypes": ["ASIAN"],
          "visitingEvenings": [1],
          "deliveryDistance": 5,
          "ratingLikelihood": "SOME"
        },
        {
          "id": 4,
          "type": "EVENT",
          "size": 8,
          "visitingTick": 10,
          "tableType": "SEPARATED",
          "foodPreferences": [
            {
              "size": 8,
              "favoriteDishes": ["Pizza"]
            }
          ],
          "restaurantTypes": ["ASIAN"],
          "eventEvening": 4,
          "favoriteDishes": {
            "ASIAN": "Sushi"
          }
        }
      ],
      "incidents": []
    }
    """.trimIndent()

    private fun parseScenario(json: String): Boolean {
        val file = File(tmp, "scenario.json")
        file.writeText(json)
        return parser.parse(file.path, simulation, recipes)
    }

    private fun validJsonObject(): JSONObject = JSONObject(validScenarioJson)

    private fun getCustomerGroup(json: JSONObject, index: Int): JSONObject {
        return json.getJSONArray("customerGroups").getJSONObject(index)
    }

    private fun jsonArrayOf(vararg elements: Any): JSONArray {
        val array = JSONArray()
        for (element in elements) {
            array.put(element)
        }
        return array
    }

    @Test
    fun testDeliveryDistanceAsDecimalIsRejected() {
        // deliveryDistance is now typed as an integer, a decimal value fails schema
        val json = validJsonObject()
        val delivery = getCustomerGroup(json, 1)
        delivery.put("visitingTick", 6)
        delivery.put("deliveryDistance", 7.9)
        assertFalse(parseScenario(json.toString()))
    }

    @Test
    fun testUnknownRestaurantTypeRejected() {
        val json = validJsonObject()
        getCustomerGroup(json, 0).put("restaurantTypes", jsonArrayOf("SPACE"))
        assertFalse(parseScenario(json.toString()))
    }

    @Test
    fun testVisitingEveningZeroIsRejected() {
        // schema now requires visitingEvenings > 0, matching the spec
        val json = validJsonObject()
        getCustomerGroup(json, 0).put("visitingEvenings", jsonArrayOf(0))
        assertFalse(parseScenario(json.toString()))
    }

    @Test
    fun testEventExtraFavoriteDishForUnconsideredTypeAccepted() {
        val json = validJsonObject()
        val event = getCustomerGroup(json, 2)
        val fav = JSONObject().apply {
            put("ASIAN", "Sushi")
            put("AMERICAN", "Burger")
        }
        event.put("favoriteDishes", fav)
        assertTrue(parseScenario(json.toString()))
    }
}
